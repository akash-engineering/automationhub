package com.automationhub.workflow.service;

import com.automationhub.testsupport.PostgresTestBase;
import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import com.automationhub.workflow.entity.Execution;
import com.automationhub.workflow.entity.ExecutionLog;
import com.automationhub.workflow.entity.ExecutionStatus;
import com.automationhub.workflow.entity.Workflow;
import com.automationhub.workflow.repository.ActionRepository;
import com.automationhub.workflow.repository.ExecutionLogRepository;
import com.automationhub.workflow.repository.ExecutionRepository;
import com.automationhub.workflow.repository.IdempotencyKeyRepository;
import com.automationhub.workflow.repository.WorkflowRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutionIntegrationTest extends PostgresTestBase {

    @Autowired ExecutionService executionService;
    @Autowired WorkflowRepository workflowRepository;
    @Autowired ActionRepository actionRepository;
    @Autowired ExecutionRepository executionRepository;
    @Autowired ExecutionLogRepository executionLogRepository;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;

    MockWebServer server;
    UUID ownerId;
    UUID workflowId;

    @BeforeEach
    void setUp() throws IOException {
        executionLogRepository.deleteAllInBatch();
        idempotencyKeyRepository.deleteAllInBatch();
        executionRepository.deleteAllInBatch();
        actionRepository.deleteAllInBatch();
        workflowRepository.deleteAllInBatch();

        server = new MockWebServer();
        server.start();

        ownerId = UUID.randomUUID();
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .name("e2e-wf")
                .ownerId(ownerId)
                .build());
        workflowId = workflow.getId();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void workflow_with_successful_http_action_reaches_completed_with_one_log_row() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        saveHttpAction(server.url("/hook").toString());

        UUID executionId = executionService.execute(workflowId, ownerId, null).id();

        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Execution e = executionRepository.findById(executionId).orElseThrow();
                    assertThat(e.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
                });

        List<ExecutionLog> logs = executionLogRepository.findByExecutionIdOrderByActionOrderAsc(executionId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(logs.get(0).getMessage()).contains("200");
    }

    @Test
    void workflow_with_failing_http_action_reaches_failed_and_captures_reason() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        saveHttpAction(server.url("/fail").toString());

        UUID executionId = executionService.execute(workflowId, ownerId, null).id();

        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    Execution e = executionRepository.findById(executionId).orElseThrow();
                    assertThat(e.getStatus()).isEqualTo(ExecutionStatus.FAILED);
                });

        List<ExecutionLog> logs = executionLogRepository.findByExecutionIdOrderByActionOrderAsc(executionId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(logs.get(0).getMessage()).contains("500");
    }

    private Action saveHttpAction(String url) {
        String config = "{\"url\":\"" + url + "\",\"method\":\"GET\"}";
        return actionRepository.save(Action.builder()
                .workflowId(workflowId)
                .type(ActionType.HTTP)
                .order(1)
                .config(config)
                .build());
    }
}
