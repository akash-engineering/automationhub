package com.automationhub.notification.dto;

import com.automationhub.notification.entity.DeliveryStatus;
import com.automationhub.notification.entity.NotificationChannel;
import com.automationhub.notification.entity.NotificationDelivery;

import java.time.Instant;
import java.util.UUID;

public record NotificationDeliveryResponse(
        UUID id,
        UUID executionId,
        UUID workflowId,
        NotificationChannel channel,
        DeliveryStatus status,
        String recipient,
        String message,
        Instant createdAt
) {
    public static NotificationDeliveryResponse from(NotificationDelivery delivery) {
        return new NotificationDeliveryResponse(
                delivery.getId(),
                delivery.getExecutionId(),
                delivery.getWorkflowId(),
                delivery.getChannel(),
                delivery.getStatus(),
                delivery.getRecipient(),
                delivery.getMessage(),
                delivery.getCreatedAt()
        );
    }
}
