package com.automationhub.document.listener;

import com.automationhub.document.entity.Document;
import com.automationhub.document.service.DocumentService;
import com.automationhub.document.service.InvoiceData;
import com.automationhub.document.service.InvoiceLine;
import com.automationhub.workflow.event.WorkflowCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;

@Component
public class WorkflowCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowCompletedListener.class);

    private final DocumentService documentService;
    private final boolean enabled;

    public WorkflowCompletedListener(
            DocumentService documentService,
            @Value("${automationhub.document.auto-summary.enabled:false}") boolean enabled) {
        this.documentService = documentService;
        this.enabled = enabled;
    }

    @Async("automationHubTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(WorkflowCompletedEvent event) {
        if (!enabled) return;
        MDC.put("executionId", event.executionId().toString());
        try {
            InvoiceData data = new InvoiceData(
                    "Workflow Completion Summary",
                    "owner:" + event.ownerId(),
                    "USD",
                    List.of(
                            new InvoiceLine("Workflow " + event.workflowId(), BigDecimal.ZERO),
                            new InvoiceLine("Execution " + event.executionId(), BigDecimal.ZERO)
                    )
            );
            Document doc = documentService.generate(data, event.ownerId(), event.executionId());
            log.info("auto-summary doc id={} executionId={}", doc.getId(), event.executionId());
        } catch (Exception ex) {
            log.warn("auto-summary failed for executionId={}: {}", event.executionId(), ex.getMessage());
        } finally {
            MDC.remove("executionId");
        }
    }
}
