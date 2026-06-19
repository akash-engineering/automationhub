package com.automationhub.workflow.service;

import com.automationhub.shared.exception.ResourceNotFoundException;
import com.automationhub.shared.web.PageResponse;
import com.automationhub.workflow.dto.ExecutionLogResponse;
import com.automationhub.workflow.dto.ExecutionResponse;
import com.automationhub.workflow.entity.Execution;
import com.automationhub.workflow.entity.ExecutionStatus;
import com.automationhub.workflow.entity.Workflow;
import com.automationhub.workflow.idempotency.IdempotencyService;
import com.automationhub.workflow.repository.ExecutionLogRepository;
import com.automationhub.workflow.repository.ExecutionRepository;
import com.automationhub.workflow.repository.WorkflowRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExecutionService {

    private final WorkflowRepository workflowRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final IdempotencyService idempotencyService;
    private final ExecutionRunner runner;

    public ExecutionService(WorkflowRepository workflowRepository,
                            ExecutionRepository executionRepository,
                            ExecutionLogRepository executionLogRepository,
                            IdempotencyService idempotencyService,
                            ExecutionRunner runner) {
        this.workflowRepository = workflowRepository;
        this.executionRepository = executionRepository;
        this.executionLogRepository = executionLogRepository;
        this.idempotencyService = idempotencyService;
        this.runner = runner;
    }

    @Transactional
    public ExecutionResponse execute(UUID workflowId, UUID ownerId, String idempotencyKey) {
        Workflow workflow = workflowRepository.findByIdAndOwnerId(workflowId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + workflowId));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<UUID> existing = idempotencyService.findExecutionId(idempotencyKey);
            if (existing.isPresent()) {
                Execution prior = executionRepository.findById(existing.get())
                        .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + existing.get()));
                return ExecutionResponse.from(prior);
            }
        }

        Execution execution = executionRepository.save(Execution.builder()
                .workflowId(workflow.getId())
                .status(ExecutionStatus.RUNNING)
                .build());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                idempotencyService.record(idempotencyKey, ownerId, workflow.getId(), execution.getId());
            } catch (DataIntegrityViolationException raceLost) {
                executionRepository.delete(execution);
                UUID priorId = idempotencyService.findExecutionId(idempotencyKey)
                        .orElseThrow(() -> raceLost);
                Execution prior = executionRepository.findById(priorId)
                        .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + priorId));
                return ExecutionResponse.from(prior);
            }
        }

        UUID executionId = execution.getId();
        UUID workflowIdValue = workflow.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runner.run(executionId, workflowIdValue, ownerId);
            }
        });

        return ExecutionResponse.from(execution);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExecutionResponse> listExecutions(UUID workflowId, UUID ownerId, Pageable pageable) {
        if (!workflowRepository.existsByIdAndOwnerId(workflowId, ownerId)) {
            throw new ResourceNotFoundException("Workflow not found: " + workflowId);
        }
        Page<Execution> page = executionRepository.findAllByWorkflowIdOrderByCreatedAtDesc(workflowId, pageable);
        List<ExecutionResponse> content = page.getContent().stream().map(ExecutionResponse::from).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<ExecutionLogResponse> listLogs(UUID workflowId, UUID executionId, UUID ownerId) {
        if (!workflowRepository.existsByIdAndOwnerId(workflowId, ownerId)) {
            throw new ResourceNotFoundException("Workflow not found: " + workflowId);
        }
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        if (!execution.getWorkflowId().equals(workflowId)) {
            throw new ResourceNotFoundException("Execution not found: " + executionId);
        }
        return executionLogRepository.findByExecutionIdOrderByActionOrderAsc(executionId).stream()
                .map(ExecutionLogResponse::from)
                .toList();
    }
}
