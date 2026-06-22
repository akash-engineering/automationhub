package com.automationhub.payment.controller;

import com.automationhub.infrastructure.security.CurrentUser;
import com.automationhub.payment.dto.CheckoutSessionRequest;
import com.automationhub.payment.dto.CheckoutSessionResponse;
import com.automationhub.payment.dto.PlanResponse;
import com.automationhub.payment.dto.SubscriptionResponse;
import com.automationhub.payment.service.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUser currentUser;

    public PaymentController(PaymentService paymentService, CurrentUser currentUser) {
        this.paymentService = paymentService;
        this.currentUser = currentUser;
    }

    @GetMapping("/plans")
    public List<PlanResponse> listPlans() {
        return paymentService.listPlans();
    }

    @PostMapping("/checkout-sessions")
    public CheckoutSessionResponse createCheckoutSession(@Valid @RequestBody CheckoutSessionRequest request)
            throws StripeException {
        return paymentService.createCheckoutSession(request.planId(), currentUser.requireId());
    }

    @GetMapping("/subscriptions")
    public List<SubscriptionResponse> listSubscriptions() {
        return paymentService.listSubscriptions(currentUser.requireId());
    }

    @GetMapping("/subscriptions/{id}")
    public SubscriptionResponse getSubscription(@PathVariable UUID id) {
        return paymentService.getSubscription(id, currentUser.requireId());
    }
}
