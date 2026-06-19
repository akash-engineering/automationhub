package com.automationhub.workflow.webhook;

public class WebhookAuthenticationException extends RuntimeException {

    public WebhookAuthenticationException(String reason) {
        super(reason);
    }
}
