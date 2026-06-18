package com.automationhub.notification.dto;

import com.automationhub.notification.entity.NotificationChannel;

import java.util.UUID;

public record NotificationRequest(
        NotificationChannel channel,
        String recipient,
        String subject,
        String body,
        UUID executionId,
        UUID workflowId,
        UUID ownerId
) {
}
