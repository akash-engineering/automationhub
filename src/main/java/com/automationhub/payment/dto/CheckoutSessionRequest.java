package com.automationhub.payment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CheckoutSessionRequest(
        @NotNull UUID planId
) {
}
