package com.automationhub.workflow.webhook;

import com.automationhub.workflow.dto.ExecutionResponse;
import com.automationhub.workflow.entity.Workflow;
import com.automationhub.workflow.repository.WorkflowRepository;
import com.automationhub.workflow.service.ExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WebhookTriggerService {

    private final WorkflowRepository workflowRepository;
    private final WebhookSignatureVerifier verifier;
    private final ExecutionService executionService;

    public WebhookTriggerService(WorkflowRepository workflowRepository,
                                 WebhookSignatureVerifier verifier,
                                 ExecutionService executionService) {
        this.workflowRepository = workflowRepository;
        this.verifier = verifier;
        this.executionService = executionService;
    }

    @Transactional
    public ExecutionResponse trigger(UUID workflowId,
                                     String timestampHeader,
                                     String signatureHeader,
                                     String idempotencyKey,
                                     String rawBody) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WebhookAuthenticationException("unknown workflow"));
        String secret = workflow.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new WebhookAuthenticationException("webhook disabled");
        }
        if (!verifier.verify(secret, timestampHeader, signatureHeader, rawBody)) {
            throw new WebhookAuthenticationException("signature check failed");
        }
        return executionService.execute(workflowId, workflow.getOwnerId(), idempotencyKey);
    }
}
