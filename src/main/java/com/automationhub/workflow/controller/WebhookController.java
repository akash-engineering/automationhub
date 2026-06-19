package com.automationhub.workflow.controller;

import com.automationhub.workflow.dto.ExecutionResponse;
import com.automationhub.workflow.webhook.WebhookTriggerService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookTriggerService triggerService;

    public WebhookController(WebhookTriggerService triggerService) {
        this.triggerService = triggerService;
    }

    @PostMapping(value = "/workflows/{workflowId}", consumes = "*/*")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionResponse trigger(
            @PathVariable UUID workflowId,
            @RequestHeader(value = "X-Webhook-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) String body
    ) {
        return triggerService.trigger(workflowId, timestamp, signature, idempotencyKey, body == null ? "" : body);
    }
}
