package com.automationhub.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWorkflowRequest(
        @NotBlank @Size(max = 200) String name,
        @NotEmpty @Valid List<ActionSpec> actions
) {
}
