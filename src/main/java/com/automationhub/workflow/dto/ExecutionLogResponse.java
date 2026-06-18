package com.automationhub.workflow.dto;

import com.automationhub.workflow.entity.ExecutionLog;
import com.automationhub.workflow.entity.ExecutionStatus;

import java.time.Instant;
import java.util.UUID;

public record ExecutionLogResponse(
        UUID id,
        UUID executionId,
        int actionOrder,
        ExecutionStatus status,
        String message,
        Instant createdAt
) {
    public static ExecutionLogResponse from(ExecutionLog log) {
        return new ExecutionLogResponse(
                log.getId(),
                log.getExecutionId(),
                log.getActionOrder(),
                log.getStatus(),
                log.getMessage(),
                log.getCreatedAt()
        );
    }
}
