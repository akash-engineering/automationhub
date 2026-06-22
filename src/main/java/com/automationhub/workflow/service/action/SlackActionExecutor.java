package com.automationhub.workflow.service.action;

import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class SlackActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(SlackActionExecutor.class);

    private final String webhookUrl;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SlackActionExecutor(@Value("${slack.webhook-url:}") String webhookUrl,
                               ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.SLACK;
    }

    @Override
    public ActionResult execute(Action action) {
        String config = action.getConfig();
        String message;
        try {
            if (config == null || config.isBlank()) {
                return ActionResult.failed("slack: missing config");
            }
            JsonNode root = objectMapper.readTree(config);
            JsonNode messageNode = root.get("message");
            if (messageNode == null || messageNode.asText().isBlank()) {
                return ActionResult.failed("slack: config.message is required");
            }
            message = messageNode.asText();
        } catch (Exception ex) {
            return ActionResult.failed("slack: invalid config json: " + ex.getMessage());
        }

        if (webhookUrl.isBlank()) {
            log.warn("Slack action: webhook unconfigured — simulated send. actionId={}", action.getId());
            return ActionResult.ok("slack: simulated send (webhook unconfigured)");
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("text", message));
        } catch (Exception ex) {
            return ActionResult.failed("slack: failed to serialize payload: " + ex.getMessage());
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                return ActionResult.ok("slack: posted -> " + status.value());
            }
            return ActionResult.failed("slack: webhook returned " + status.value()
                    + " body=" + response.getBody());
        } catch (RestClientException ex) {
            log.warn("Slack action failed: {}", ex.getMessage());
            return ActionResult.failed("slack: webhook call failed: " + ex.getMessage());
        }
    }
}
