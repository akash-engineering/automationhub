package com.automationhub.payment.service;

import com.automationhub.payment.dto.CheckoutSessionResponse;
import com.automationhub.payment.dto.PlanResponse;
import com.automationhub.payment.dto.SubscriptionResponse;
import com.automationhub.payment.entity.Plan;
import com.automationhub.payment.entity.Subscription;
import com.automationhub.payment.repository.PlanRepository;
import com.automationhub.payment.repository.SubscriptionRepository;
import com.automationhub.shared.exception.ResourceNotFoundException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StripeClient stripeClient;
    private final String successUrl;
    private final String cancelUrl;

    public PaymentService(PlanRepository planRepository,
                          SubscriptionRepository subscriptionRepository,
                          StripeClient stripeClient,
                          @Value("${stripe.checkout.success-url:http://localhost:8080/checkout/success}") String successUrl,
                          @Value("${stripe.checkout.cancel-url:http://localhost:8080/checkout/cancel}") String cancelUrl) {
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.stripeClient = stripeClient;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findAllByOrderByAmountAsc().stream()
                .map(PlanResponse::from)
                .toList();
    }

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(UUID planId, UUID ownerId) throws StripeException {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));

        String customerId = subscriptionRepository.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId)
                .map(Subscription::getStripeCustomerId)
                .orElseGet(() -> {
                    try {
                        Customer customer = stripeClient.createCustomer(ownerId);
                        log.info("created Stripe customer id={} ownerId={}", customer.getId(), ownerId);
                        return customer.getId();
                    } catch (StripeException ex) {
                        throw new IllegalStateException("Failed to create Stripe customer: " + ex.getMessage(), ex);
                    }
                });

        com.stripe.model.checkout.Session session = stripeClient.createCheckoutSession(
                customerId,
                plan.getStripePriceId(),
                ownerId,
                plan.getId(),
                successUrl,
                cancelUrl
        );
        log.info("created Stripe checkout session id={} ownerId={} planId={}",
                session.getId(), ownerId, planId);
        return new CheckoutSessionResponse(session.getId(), session.getUrl());
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> listSubscriptions(UUID ownerId) {
        return subscriptionRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID id, UUID ownerId) {
        return subscriptionRepository.findByIdAndOwnerId(id, ownerId)
                .map(SubscriptionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found: " + id));
    }
}
