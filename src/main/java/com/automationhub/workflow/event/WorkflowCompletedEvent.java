package com.automationhub.workflow.event;

import com.automationhub.shared.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record WorkflowCompletedEvent(
        UUID workflowId,
        UUID executionId,
        UUID ownerId,
        Instant occurredAt
) implements DomainEvent {
}
