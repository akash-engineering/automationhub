package com.automationhub.payment.service;

import com.automationhub.payment.entity.Payment;
import com.automationhub.payment.entity.PaymentStatus;
import com.automationhub.payment.entity.ProcessedStripeEvent;
import com.automationhub.payment.entity.Subscription;
import com.automationhub.payment.entity.SubscriptionStatus;
import com.automationhub.payment.event.PaymentSucceededEvent;
import com.automationhub.payment.repository.PaymentRepository;
import com.automationhub.payment.repository.ProcessedStripeEventRepository;
import com.automationhub.payment.repository.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);
    private static final String META_OWNER_ID = "automationhub_owner_id";
    private static final String META_PLAN_ID = "automationhub_plan_id";

    private final StripeClient stripeClient;
    private final ProcessedStripeEventRepository processedRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StripeWebhookService(StripeClient stripeClient,
                                ProcessedStripeEventRepository processedRepository,
                                SubscriptionRepository subscriptionRepository,
                                PaymentRepository paymentRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.stripeClient = stripeClient;
        this.processedRepository = processedRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void process(String payload, String sigHeader) throws SignatureVerificationException {
        Event event = stripeClient.constructEvent(payload, sigHeader);

        try {
            processedRepository.saveAndFlush(ProcessedStripeEvent.builder()
                    .stripeEventId(event.getId())
                    .eventType(event.getType())
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Stripe event {} already processed — skipping", event.getId());
            return;
        }

        log.info("processing Stripe event id={} type={}", event.getId(), event.getType());
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "invoice.payment_succeeded" -> handleInvoicePaid(event);
            case "invoice.payment_failed" -> handleInvoiceFailed(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            default -> log.info("ignoring unhandled Stripe event type: {}", event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) deserialize(event);
        if (session == null) return;

        UUID ownerId = readUuid(session.getMetadata().get(META_OWNER_ID));
        UUID planId = readUuid(session.getMetadata().get(META_PLAN_ID));
        if (ownerId == null || planId == null) {
            log.warn("checkout.session.completed missing owner/plan metadata: session={}", session.getId());
            return;
        }
        if (session.getSubscription() == null) {
            log.warn("checkout.session.completed has no subscription: session={}", session.getId());
            return;
        }

        Subscription subscription = subscriptionRepository.findByStripeSubscriptionId(session.getSubscription())
                .orElseGet(Subscription::new);
        subscription.setOwnerId(ownerId);
        subscription.setPlanId(planId);
        subscription.setStripeCustomerId(session.getCustomer());
        subscription.setStripeSubscriptionId(session.getSubscription());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
        log.info("activated subscription stripeId={} ownerId={}", session.getSubscription(), ownerId);
    }

    private void handleInvoicePaid(Event event) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice == null) return;

        Optional<Subscription> subOpt = invoice.getSubscription() == null
                ? Optional.empty()
                : subscriptionRepository.findByStripeSubscriptionId(invoice.getSubscription());

        UUID ownerId = subOpt.map(Subscription::getOwnerId).orElse(null);
        if (ownerId == null) {
            log.warn("invoice.payment_succeeded for unknown subscription: {}", invoice.getSubscription());
            return;
        }

        BigDecimal amount = BigDecimal.valueOf(invoice.getAmountPaid())
                .movePointLeft(2);
        Instant paidAt = Instant.now();

        Payment saved = paymentRepository.save(Payment.builder()
                .ownerId(ownerId)
                .subscriptionId(subOpt.get().getId())
                .stripePaymentIntentId(invoice.getPaymentIntent() != null ? invoice.getPaymentIntent() : invoice.getId())
                .amount(amount)
                .status(PaymentStatus.SUCCEEDED)
                .paidAt(paidAt)
                .build());

        subOpt.ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.ACTIVE);
            if (invoice.getPeriodEnd() != null) {
                sub.setCurrentPeriodEnd(Instant.ofEpochSecond(invoice.getPeriodEnd()));
            }
            subscriptionRepository.save(sub);
        });

        eventPublisher.publishEvent(new PaymentSucceededEvent(
                ownerId, saved.getId(), subOpt.get().getId(), amount, paidAt));
        log.info("recorded payment id={} amount={} subscriptionId={}",
                saved.getId(), amount, subOpt.get().getId());
    }

    private void handleInvoiceFailed(Event event) {
        Invoice invoice = (Invoice) deserialize(event);
        if (invoice == null || invoice.getSubscription() == null) return;

        subscriptionRepository.findByStripeSubscriptionId(invoice.getSubscription()).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            subscriptionRepository.save(sub);
            log.warn("subscription {} marked PAST_DUE (invoice {} failed)", sub.getId(), invoice.getId());
        });

        if (invoice.getSubscription() != null) {
            Optional<Subscription> subOpt = subscriptionRepository.findByStripeSubscriptionId(invoice.getSubscription());
            subOpt.ifPresent(sub -> paymentRepository.save(Payment.builder()
                    .ownerId(sub.getOwnerId())
                    .subscriptionId(sub.getId())
                    .stripePaymentIntentId(invoice.getPaymentIntent() != null ? invoice.getPaymentIntent() : invoice.getId())
                    .amount(BigDecimal.valueOf(invoice.getAmountDue()).movePointLeft(2))
                    .status(PaymentStatus.FAILED)
                    .paidAt(Instant.now())
                    .build()));
        }
    }

    private void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub = (com.stripe.model.Subscription) deserialize(event);
        if (stripeSub == null) return;
        subscriptionRepository.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.CANCELED);
            subscriptionRepository.save(sub);
            log.info("subscription {} marked CANCELED", sub.getId());
        });
    }

    private static StripeObject deserialize(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> obj = deserializer.getObject();
        if (obj.isPresent()) return obj.get();
        try {
            return deserializer.deserializeUnsafe();
        } catch (Exception ex) {
            log.warn("failed to deserialize Stripe event {} ({}): {}",
                    event.getId(), event.getType(), ex.getMessage());
            return null;
        }
    }

    private static UUID readUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
