package com.automationhub.workflow.service;

import com.automationhub.workflow.dto.ExecutionResponse;
import com.automationhub.workflow.entity.Execution;
import com.automationhub.workflow.entity.ExecutionStatus;
import com.automationhub.workflow.entity.Workflow;
import com.automationhub.workflow.idempotency.IdempotencyService;
import com.automationhub.workflow.repository.ExecutionLogRepository;
import com.automationhub.workflow.repository.ExecutionRepository;
import com.automationhub.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceIdempotencyTest {

    @Mock WorkflowRepository workflowRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository executionLogRepository;
    @Mock IdempotencyService idempotencyService;
    @Mock ExecutionRunner runner;

    ExecutionService service;

    UUID ownerId;
    UUID workflowId;
    Workflow workflow;

    @BeforeEach
    void setUp() {
        service = new ExecutionService(workflowRepository, executionRepository, executionLogRepository,
                idempotencyService, runner);
        ownerId = UUID.randomUUID();
        workflowId = UUID.randomUUID();
        workflow = Workflow.builder().name("wf").ownerId(ownerId).build();
        setId(workflow, workflowId);
        when(workflowRepository.findByIdAndOwnerId(workflowId, ownerId)).thenReturn(Optional.of(workflow));
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void first_call_with_key_creates_new_execution_and_records_idempotency() {
        String key = "k-1";
        when(idempotencyService.findExecutionId(key)).thenReturn(Optional.empty());
        Execution saved = newRunningExecution();
        when(executionRepository.save(any(Execution.class))).thenReturn(saved);
        when(idempotencyService.record(key, ownerId, workflowId, saved.getId())).thenReturn(saved.getId());

        ExecutionResponse response = service.execute(workflowId, ownerId, key);

        assertThat(response.id()).isEqualTo(saved.getId());
        assertThat(response.status()).isEqualTo(ExecutionStatus.RUNNING);
        verify(executionRepository).save(any(Execution.class));
        verify(idempotencyService).record(key, ownerId, workflowId, saved.getId());
    }

    @Test
    void second_call_with_same_key_returns_existing_execution_without_creating_a_new_one() {
        String key = "k-2";
        Execution existing = newRunningExecution();
        when(idempotencyService.findExecutionId(key)).thenReturn(Optional.of(existing.getId()));
        when(executionRepository.findById(existing.getId())).thenReturn(Optional.of(existing));

        ExecutionResponse response = service.execute(workflowId, ownerId, key);

        assertThat(response.id()).isEqualTo(existing.getId());
        verify(executionRepository, never()).save(any(Execution.class));
        verify(idempotencyService, never()).record(any(), any(), any(), any());
    }

    private Execution newRunningExecution() {
        Execution e = Execution.builder()
                .workflowId(workflowId)
                .status(ExecutionStatus.RUNNING)
                .build();
        setId(e, UUID.randomUUID());
        e.setCreatedAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        return e;
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field f = entity.getClass().getSuperclass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }
}
