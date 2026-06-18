package com.automationhub.workflow.dto;

import com.automationhub.workflow.entity.Execution;
import com.automationhub.workflow.entity.ExecutionStatus;

import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID id,
        UUID workflowId,
        ExecutionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExecutionResponse from(Execution execution) {
        return new ExecutionResponse(
                execution.getId(),
                execution.getWorkflowId(),
                execution.getStatus(),
                execution.getCreatedAt(),
                execution.getUpdatedAt()
        );
    }
}
