package com.automationhub.notification.sender;

import com.automationhub.notification.dto.NotificationRequest;
import com.automationhub.notification.entity.NotificationChannel;
import com.fasterxml.jackson.core.JsonProcessingException;
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
public class SlackSender implements Sender {

    private static final Logger log = LoggerFactory.getLogger(SlackSender.class);

    private final String webhookUrl;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public SlackSender(@Value("${slack.webhook-url:}") String webhookUrl,
                       ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.SLACK;
    }

    @Override
    public void send(NotificationRequest request) throws SenderException {
        String text = request.subject() + "\n" + request.body();

        if (webhookUrl.isBlank()) {
            log.warn("[slack] webhook unconfigured — logging only. to={} subject={}",
                    request.recipient(), request.subject());
            return;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("text", text));
        } catch (JsonProcessingException ex) {
            throw new SenderException("slack: failed to serialize payload", ex);
        }

        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new SenderException("slack: webhook returned " + status.value()
                        + " body=" + response.getBody());
            }
            log.info("[slack] sent subject={} status={}", request.subject(), status.value());
        } catch (RestClientException ex) {
            throw new SenderException("slack: webhook call failed: " + ex.getMessage(), ex);
        }
    }
}
