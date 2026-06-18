package com.automationhub.workflow.controller;

import com.automationhub.infrastructure.security.CurrentUser;
import com.automationhub.shared.web.PageResponse;
import com.automationhub.workflow.dto.ExecutionLogResponse;
import com.automationhub.workflow.dto.ExecutionResponse;
import com.automationhub.workflow.service.ExecutionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/workflows/{workflowId}")
public class ExecutionController {

    private final ExecutionService executionService;
    private final CurrentUser currentUser;

    public ExecutionController(ExecutionService executionService, CurrentUser currentUser) {
        this.executionService = executionService;
        this.currentUser = currentUser;
    }

    @PostMapping("/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExecutionResponse execute(
            @PathVariable UUID workflowId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return executionService.execute(workflowId, currentUser.requireId(), idempotencyKey);
    }

    @GetMapping("/executions")
    public PageResponse<ExecutionResponse> listExecutions(
            @PathVariable UUID workflowId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return executionService.listExecutions(workflowId, currentUser.requireId(), pageable);
    }

    @GetMapping("/executions/{executionId}/logs")
    public List<ExecutionLogResponse> listLogs(
            @PathVariable UUID workflowId,
            @PathVariable UUID executionId
    ) {
        return executionService.listLogs(workflowId, executionId, currentUser.requireId());
    }
}
