package com.automationhub.workflow.dto;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ActionSpec(
        @NotNull ActionType type,
        @PositiveOrZero int order,
        String config
) {
    public static ActionSpec from(Action action) {
        return new ActionSpec(action.getType(), action.getOrder(), action.getConfig());
    }
}
