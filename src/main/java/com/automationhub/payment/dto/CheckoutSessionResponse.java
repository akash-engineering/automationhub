package com.automationhub.payment.dto;

public record CheckoutSessionResponse(
        String sessionId,
        String url
) {
}
