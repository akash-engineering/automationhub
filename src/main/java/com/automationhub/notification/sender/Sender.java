package com.automationhub.notification.sender;

import com.automationhub.notification.dto.NotificationRequest;
import com.automationhub.notification.entity.NotificationChannel;

public interface Sender {

    boolean supports(NotificationChannel channel);

    void send(NotificationRequest request) throws SenderException;
}
