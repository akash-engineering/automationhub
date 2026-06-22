package com.automationhub.payment.event;

import com.automationhub.shared.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentSucceededEvent(
        UUID ownerId,
        UUID paymentId,
        UUID subscriptionId,
        BigDecimal amount,
        Instant occurredAt
) implements DomainEvent {
}
