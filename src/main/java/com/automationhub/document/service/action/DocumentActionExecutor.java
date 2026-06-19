package com.automationhub.document.service.action;

import com.automationhub.document.entity.Document;
import com.automationhub.document.service.DocumentService;
import com.automationhub.document.service.InvoiceData;
import com.automationhub.document.service.InvoiceLine;
import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import com.automationhub.workflow.repository.WorkflowRepository;
import com.automationhub.workflow.service.action.ActionExecutor;
import com.automationhub.workflow.service.action.ActionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DocumentActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(DocumentActionExecutor.class);

    private final ObjectMapper objectMapper;
    private final DocumentService documentService;
    private final WorkflowRepository workflowRepository;

    public DocumentActionExecutor(ObjectMapper objectMapper,
                                  DocumentService documentService,
                                  WorkflowRepository workflowRepository) {
        this.objectMapper = objectMapper;
        this.documentService = documentService;
        this.workflowRepository = workflowRepository;
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.DOCUMENT;
    }

    @Override
    public ActionResult execute(Action action) {
        String config = action.getConfig();
        if (config == null || config.isBlank()) {
            return ActionResult.failed("document: missing config");
        }

        InvoiceData data;
        try {
            data = parse(config);
        } catch (Exception ex) {
            return ActionResult.failed("document: invalid config json: " + ex.getMessage());
        }

        UUID ownerId = workflowRepository.findById(action.getWorkflowId())
                .map(w -> w.getOwnerId())
                .orElse(null);
        if (ownerId == null) {
            return ActionResult.failed("document: workflow owner not found for workflowId=" + action.getWorkflowId());
        }

        try {
            Document doc = documentService.generate(data, ownerId, null);
            return ActionResult.ok("document: generated id=" + doc.getId()
                    + " key=" + doc.getStorageKey()
                    + " bytes=" + doc.getSizeBytes());
        } catch (Exception ex) {
            log.warn("document action failed: workflowId={} reason={}", action.getWorkflowId(), ex.getMessage());
            return ActionResult.failed("document: " + ex.getMessage());
        }
    }

    private InvoiceData parse(String config) throws Exception {
        JsonNode root = objectMapper.readTree(config);
        String title = root.path("title").asText("Invoice");
        String recipient = root.path("recipient").asText("");
        String currency = root.path("currency").asText("USD");
        List<InvoiceLine> lines = new ArrayList<>();
        JsonNode linesNode = root.get("lines");
        if (linesNode != null && linesNode.isArray()) {
            for (JsonNode line : linesNode) {
                String desc = line.path("description").asText("");
                BigDecimal amount = new BigDecimal(line.path("amount").asText("0"));
                lines.add(new InvoiceLine(desc, amount));
            }
        }
        return new InvoiceData(title, recipient, currency, lines);
    }
}
