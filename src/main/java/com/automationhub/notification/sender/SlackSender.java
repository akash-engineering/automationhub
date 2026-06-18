package com.automationhub.notification.sender;

import com.automationhub.notification.dto.NotificationRequest;
import com.automationhub.notification.entity.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlackSender implements Sender {

    private static final Logger log = LoggerFactory.getLogger(SlackSender.class);

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.SLACK;
    }

    @Override
    public void send(NotificationRequest request) {
        log.info("[slack] to={} subject={} body={}", request.recipient(), request.subject(), request.body());
    }
}
