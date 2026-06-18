package com.automationhub.workflow.dto;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.Workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String name,
        UUID ownerId,
        List<ActionSpec> actions,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkflowResponse from(Workflow workflow, List<Action> actions) {
        List<ActionSpec> specs = actions.stream().map(ActionSpec::from).toList();
        return new WorkflowResponse(
                workflow.getId(),
                workflow.getName(),
                workflow.getOwnerId(),
                specs,
                workflow.getCreatedAt(),
                workflow.getUpdatedAt()
        );
    }
}
