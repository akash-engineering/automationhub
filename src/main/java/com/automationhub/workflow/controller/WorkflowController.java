package com.automationhub.workflow.controller;

import com.automationhub.infrastructure.security.CurrentUser;
import com.automationhub.shared.web.PageResponse;
import com.automationhub.workflow.dto.CreateWorkflowRequest;
import com.automationhub.workflow.dto.WorkflowResponse;
import com.automationhub.workflow.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final CurrentUser currentUser;

    public WorkflowController(WorkflowService workflowService, CurrentUser currentUser) {
        this.workflowService = workflowService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse create(@Valid @RequestBody CreateWorkflowRequest request) {
        return workflowService.create(request, currentUser.requireId());
    }

    @GetMapping
    public PageResponse<WorkflowResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return workflowService.list(currentUser.requireId(), pageable);
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable UUID id) {
        return workflowService.get(id, currentUser.requireId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        workflowService.delete(id, currentUser.requireId());
    }
}
