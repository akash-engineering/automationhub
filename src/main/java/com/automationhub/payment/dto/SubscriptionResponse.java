package com.automationhub.payment.dto;

import com.automationhub.payment.entity.Subscription;
import com.automationhub.payment.entity.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        UUID planId,
        String stripeCustomerId,
        String stripeSubscriptionId,
        SubscriptionStatus status,
        Instant currentPeriodEnd,
        Instant createdAt
) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getPlanId(),
                subscription.getStripeCustomerId(),
                subscription.getStripeSubscriptionId(),
                subscription.getStatus(),
                subscription.getCurrentPeriodEnd(),
                subscription.getCreatedAt()
        );
    }
}
