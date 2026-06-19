package com.automationhub.workflow.service;

import com.automationhub.shared.exception.ResourceNotFoundException;
import com.automationhub.shared.web.PageResponse;
import com.automationhub.workflow.dto.ActionSpec;
import com.automationhub.workflow.dto.CreateWorkflowRequest;
import com.automationhub.workflow.dto.WorkflowResponse;
import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.Workflow;
import com.automationhub.workflow.repository.ActionRepository;
import com.automationhub.workflow.repository.WorkflowRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final ActionRepository actionRepository;

    public WorkflowService(WorkflowRepository workflowRepository, ActionRepository actionRepository) {
        this.workflowRepository = workflowRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional
    public WorkflowResponse create(CreateWorkflowRequest request, UUID ownerId) {
        Workflow workflow = workflowRepository.save(Workflow.builder()
                .name(request.name())
                .ownerId(ownerId)
                .build());
        List<Action> actions = request.actions().stream()
                .map(spec -> Action.builder()
                        .workflowId(workflow.getId())
                        .type(spec.type())
                        .order(spec.order())
                        .config(spec.config())
                        .build())
                .toList();
        List<Action> saved = actionRepository.saveAll(actions);
        return WorkflowResponse.from(workflow, sortByOrder(saved));
    }

    @Transactional(readOnly = true)
    public PageResponse<WorkflowResponse> list(UUID ownerId, Pageable pageable) {
        Page<Workflow> page = workflowRepository.findAllByOwnerId(ownerId, pageable);
        List<WorkflowResponse> content = page.getContent().stream()
                .map(w -> WorkflowResponse.from(w, actionRepository.findByWorkflowIdOrderByOrderAsc(w.getId())))
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(UUID id, UUID ownerId) {
        Workflow workflow = requireOwned(id, ownerId);
        return WorkflowResponse.from(workflow, actionRepository.findByWorkflowIdOrderByOrderAsc(id));
    }

    @Transactional
    public void delete(UUID id, UUID ownerId) {
        Workflow workflow = requireOwned(id, ownerId);
        actionRepository.deleteByWorkflowId(workflow.getId());
        workflowRepository.delete(workflow);
    }

    @Transactional
    public String rotateWebhookSecret(UUID workflowId, UUID ownerId) {
        Workflow workflow = requireOwned(workflowId, ownerId);
        String secret = generateSecret();
        workflow.setWebhookSecret(secret);
        workflowRepository.save(workflow);
        return secret;
    }

    @Transactional
    public void disableWebhook(UUID workflowId, UUID ownerId) {
        Workflow workflow = requireOwned(workflowId, ownerId);
        workflow.setWebhookSecret(null);
        workflowRepository.save(workflow);
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private Workflow requireOwned(UUID id, UUID ownerId) {
        return workflowRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found: " + id));
    }

    private static List<Action> sortByOrder(List<Action> actions) {
        return actions.stream().sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder())).toList();
    }
}
