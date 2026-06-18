package com.automationhub.workflow.service.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.automationhub.workflow.entity.Action;
import com.automationhub.workflow.entity.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpActionExecutor implements ActionExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpActionExecutor.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpActionExecutor(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(ActionType type) {
        return type == ActionType.HTTP;
    }

    @Override
    public ActionResult execute(Action action) {
        String config = action.getConfig();
        if (config == null || config.isBlank()) {
            return ActionResult.failed("http: missing config");
        }

        String url;
        HttpMethod method;
        String body;
        try {
            JsonNode root = objectMapper.readTree(config);
            JsonNode urlNode = root.get("url");
            if (urlNode == null || urlNode.asText().isBlank()) {
                return ActionResult.failed("http: config.url is required");
            }
            url = urlNode.asText();
            method = HttpMethod.valueOf(root.path("method").asText("GET").toUpperCase());
            JsonNode bodyNode = root.get("body");
            body = bodyNode == null || bodyNode.isNull() ? null : bodyNode.toString();
        } catch (Exception ex) {
            return ActionResult.failed("http: invalid config json: " + ex.getMessage());
        }

        try {
            RestClient.RequestBodySpec spec = restClient.method(method).uri(url);
            ResponseEntity<String> response = (body == null ? spec : spec.body(body))
                    .retrieve()
                    .toEntity(String.class);
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                return ActionResult.ok("http: " + method + " " + url + " -> " + status.value());
            }
            return ActionResult.failed("http: " + method + " " + url + " -> " + status.value());
        } catch (RestClientException ex) {
            log.warn("HTTP action failed: {} {} : {}", method, url, ex.getMessage());
            return ActionResult.failed("http: " + method + " " + url + " -> " + ex.getMessage());
        }
    }
}
