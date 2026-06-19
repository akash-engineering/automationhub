package com.automationhub.workflow.service;

import com.automationhub.testsupport.PostgresTestBase;
import com.automationhub.workflow.entity.Workflow;
import com.automationhub.workflow.repository.ActionRepository;
import com.automationhub.workflow.repository.ExecutionLogRepository;
import com.automationhub.workflow.repository.ExecutionRepository;
import com.automationhub.workflow.repository.IdempotencyKeyRepository;
import com.automationhub.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionServiceRaceIntegrationTest extends PostgresTestBase {

    @Autowired ExecutionService executionService;
    @Autowired WorkflowRepository workflowRepository;
    @Autowired ExecutionRepository executionRepository;
    @Autowired ExecutionLogRepository executionLogRepository;
    @Autowired ActionRepository actionRepository;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;

    UUID ownerId;
    UUID workflowId;

    @BeforeEach
    void resetState() {
        executionLogRepository.deleteAllInBatch();
        idempotencyKeyRepository.deleteAllInBatch();
        executionRepository.deleteAllInBatch();
        actionRepository.deleteAllInBatch();
        workflowRepository.deleteAllInBatch();

        ownerId = UUID.randomUUID();
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .name("race-wf")
                .ownerId(ownerId)
                .build());
        workflowId = workflow.getId();
    }

    @Test
    void two_concurrent_calls_with_the_same_key_produce_exactly_one_execution_row() throws Exception {
        String key = "race-" + UUID.randomUUID();
        int parallelism = 2;
        CountDownLatch ready = new CountDownLatch(parallelism);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            Callable<Outcome> call = () -> {
                ready.countDown();
                go.await();
                try {
                    UUID id = executionService.execute(workflowId, ownerId, key).id();
                    return Outcome.success(id);
                } catch (Throwable t) {
                    return Outcome.failure(t);
                }
            };

            Future<Outcome> f1 = pool.submit(call);
            Future<Outcome> f2 = pool.submit(call);
            ready.await();
            go.countDown();
            Outcome o1 = f1.get(10, TimeUnit.SECONDS);
            Outcome o2 = f2.get(10, TimeUnit.SECONDS);

            long executions = executionRepository.count();
            long keys = idempotencyKeyRepository.count();

            assertThat(executions)
                    .as("exactly one execution row should exist for the workflow")
                    .isEqualTo(1L);
            assertThat(keys)
                    .as("exactly one idempotency_keys row should exist")
                    .isEqualTo(1L);

            List<Outcome> outcomes = List.of(o1, o2);
            assertThat(outcomes).allSatisfy(o -> assertThat(o.error)
                    .as("both callers should return the existing execution id, not an exception")
                    .isNull());

            UUID winner = executionRepository.findAll().get(0).getId();
            assertThat(o1.executionId).isEqualTo(winner);
            assertThat(o2.executionId).isEqualTo(winner);
        } finally {
            pool.shutdownNow();
        }
    }

    private record Outcome(UUID executionId, Throwable error) {
        static Outcome success(UUID id) { return new Outcome(id, null); }
        static Outcome failure(Throwable t) { return new Outcome(null, t); }
    }
}
