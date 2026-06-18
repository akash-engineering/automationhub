package com.automationhub.notification.listener;

import com.automationhub.notification.dto.NotificationRequest;
import com.automationhub.notification.entity.NotificationChannel;
import com.automationhub.notification.service.NotificationService;
import com.automationhub.workflow.event.WorkflowCompletedEvent;
import com.automationhub.workflow.event.WorkflowFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class WorkflowEventListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEventListener.class);

    private final NotificationService notificationService;

    public WorkflowEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("automationHubTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(WorkflowCompletedEvent event) {
        withMdc(event.executionId(), () -> {
            log.info("received WorkflowCompletedEvent on thread={} workflowId={} executionId={} ownerId={} occurredAt={}",
                    Thread.currentThread().getName(),
                    event.workflowId(), event.executionId(), event.ownerId(), event.occurredAt());

            String subject = "Workflow " + event.workflowId() + " completed";
            String body = "Execution " + event.executionId() + " finished successfully at " + event.occurredAt();
            dispatchAll(event.executionId(), event.workflowId(), event.ownerId(), subject, body);
        });
    }

    @Async("automationHubTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFailed(WorkflowFailedEvent event) {
        withMdc(event.executionId(), () -> {
            log.info("received WorkflowFailedEvent on thread={} workflowId={} executionId={} ownerId={} reason={} occurredAt={}",
                    Thread.currentThread().getName(),
                    event.workflowId(), event.executionId(), event.ownerId(), event.reason(), event.occurredAt());

            String subject = "Workflow " + event.workflowId() + " FAILED";
            String body = "Execution " + event.executionId() + " failed at " + event.occurredAt() + ": " + event.reason();
            dispatchAll(event.executionId(), event.workflowId(), event.ownerId(), subject, body);
        });
    }

    private void dispatchAll(UUID executionId, UUID workflowId, UUID ownerId, String subject, String body) {
        String recipient = "owner:" + ownerId;
        for (NotificationChannel channel : NotificationChannel.values()) {
            NotificationRequest request = new NotificationRequest(
                    channel, recipient, subject, body, executionId, workflowId, ownerId);
            notificationService.send(request);
        }
    }

    private void withMdc(UUID executionId, Runnable work) {
        MDC.put("executionId", executionId.toString());
        try {
            work.run();
        } finally {
            MDC.remove("executionId");
        }
    }
}
