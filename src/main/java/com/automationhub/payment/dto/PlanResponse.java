package com.automationhub.payment.dto;

import com.automationhub.payment.entity.Plan;
import com.automationhub.payment.entity.PlanInterval;

import java.math.BigDecimal;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String name,
        String stripePriceId,
        BigDecimal amount,
        PlanInterval interval
) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getStripePriceId(),
                plan.getAmount(),
                plan.getInterval()
        );
    }
}
