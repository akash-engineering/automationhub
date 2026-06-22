package com.automationhub.payment.service;

import com.automationhub.payment.exception.StripeNotConfiguredException;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StripeClient {

    private static final Logger log = LoggerFactory.getLogger(StripeClient.class);
    private static final String META_OWNER_ID = "automationhub_owner_id";
    private static final String META_PLAN_ID = "automationhub_plan_id";

    private final String apiKey;
    private final String webhookSecret;

    public StripeClient(@Value("${stripe.api-key:}") String apiKey,
                        @Value("${stripe.webhook-secret:}") String webhookSecret) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        if (this.apiKey.isBlank()) {
            log.warn("Stripe API key not configured — live Stripe calls disabled. "
                    + "Set STRIPE_API_KEY to enable.");
        } else {
            Stripe.apiKey = this.apiKey;
            log.info("Stripe API key configured.");
        }
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public boolean isWebhookConfigured() {
        return !webhookSecret.isBlank();
    }

    public Customer createCustomer(UUID ownerId) throws StripeException {
        requireConfigured();
        CustomerCreateParams params = CustomerCreateParams.builder()
                .putMetadata(META_OWNER_ID, ownerId.toString())
                .build();
        return Customer.create(params);
    }

    public Session createCheckoutSession(String customerId,
                                         String priceId,
                                         UUID ownerId,
                                         UUID planId,
                                         String successUrl,
                                         String cancelUrl) throws StripeException {
        requireConfigured();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build())
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata(META_OWNER_ID, ownerId.toString())
                .putMetadata(META_PLAN_ID, planId.toString())
                .build();
        return Session.create(params);
    }

    public Event constructEvent(String payload, String sigHeader) throws SignatureVerificationException {
        if (webhookSecret.isBlank()) {
            throw new StripeNotConfiguredException("Stripe webhook secret not configured");
        }
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new StripeNotConfiguredException("Stripe API key not configured");
        }
    }
}
