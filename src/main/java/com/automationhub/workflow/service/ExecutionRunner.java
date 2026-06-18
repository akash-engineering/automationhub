package com.automationhub.workflow.service;

import com.automationhub.shared.exception.ResourceNotFoundException;
import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.Execution;
import com.automationhub.workflow.entity.ExecutionLog;
import com.automationhub.workflow.entity.ExecutionStatus;
import com.automationhub.workflow.event.WorkflowCompletedEvent;
import com.automationhub.workflow.event.WorkflowFailedEvent;
import com.automationhub.workflow.repository.ActionRepository;
import com.automationhub.workflow.repository.ExecutionLogRepository;
import com.automationhub.workflow.repository.ExecutionRepository;
import com.automationhub.workflow.service.action.ActionExecutor;
import com.automationhub.workflow.service.action.ActionExecutorRegistry;
import com.automationhub.workflow.service.action.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ExecutionRunner {

    private static final Logger log = LoggerFactory.getLogger(ExecutionRunner.class);

    private final ExecutionRepository executionRepository;
    private final ActionRepository actionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final ActionExecutorRegistry registry;
    private final ApplicationEventPublisher eventPublisher;

    public ExecutionRunner(ExecutionRepository executionRepository,
                           ActionRepository actionRepository,
                           ExecutionLogRepository executionLogRepository,
                           ActionExecutorRegistry registry,
                           ApplicationEventPublisher eventPublisher) {
        this.executionRepository = executionRepository;
        this.actionRepository = actionRepository;
        this.executionLogRepository = executionLogRepository;
        this.registry = registry;
        this.eventPublisher = eventPublisher;
    }

    @Async("automationHubTaskExecutor")
    @Transactional
    public void run(UUID executionId, UUID workflowId, UUID ownerId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        List<Action> actions = actionRepository.findByWorkflowIdOrderByOrderAsc(workflowId);

        boolean failed = false;
        String failureReason = null;

        for (Action action : actions) {
            ActionResult result;
            try {
                ActionExecutor executor = registry.resolve(action.getType());
                result = executor.execute(action);
            } catch (Exception ex) {
                log.warn("Executor threw for action {} (type={}): {}", action.getId(), action.getType(), ex.getMessage());
                result = ActionResult.failed(action.getType() + ": " + ex.getMessage());
            }

            ExecutionStatus stepStatus = result.success() ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED;
            executionLogRepository.save(ExecutionLog.builder()
                    .executionId(executionId)
                    .actionOrder(action.getOrder())
                    .status(stepStatus)
                    .message(result.message())
                    .build());

            if (!result.success()) {
                failed = true;
                failureReason = result.message();
                break;
            }
        }

        Instant now = Instant.now();
        if (failed) {
            execution.setStatus(ExecutionStatus.FAILED);
            executionRepository.save(execution);
            eventPublisher.publishEvent(new WorkflowFailedEvent(workflowId, executionId, ownerId, failureReason, now));
        } else {
            execution.setStatus(ExecutionStatus.COMPLETED);
            executionRepository.save(execution);
            eventPublisher.publishEvent(new WorkflowCompletedEvent(workflowId, executionId, ownerId, now));
        }
    }
}
