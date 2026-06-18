package com.automationhub.workflow.event;

import com.automationhub.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record WorkflowFailedEvent(
        UUID workflowId,
        UUID executionId,
        UUID ownerId,
        String reason,
        Instant occurredAt
) implements DomainEvent {
}
