package com.automationhub.notification.controller;

import com.automationhub.infrastructure.security.CurrentUser;
import com.automationhub.notification.dto.NotificationDeliveryResponse;
import com.automationhub.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping("/executions/{executionId}")
    public List<NotificationDeliveryResponse> listForExecution(@PathVariable UUID executionId) {
        return notificationService.listForExecution(executionId, currentUser.requireId());
    }
}
