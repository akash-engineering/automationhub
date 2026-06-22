package com.automationhub.payment.service;

import com.automationhub.payment.entity.ProcessedStripeEvent;
import com.automationhub.payment.repository.PaymentRepository;
import com.automationhub.payment.repository.ProcessedStripeEventRepository;
import com.automationhub.payment.repository.SubscriptionRepository;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests";

    @Mock private ProcessedStripeEventRepository processedRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        StripeClient stripeClient = new StripeClient("", WEBHOOK_SECRET);
        service = new StripeWebhookService(
                stripeClient, processedRepository, subscriptionRepository,
                paymentRepository, eventPublisher);
    }

    @Test
    void validSignature_freshEvent_recordsAndDispatches() throws Exception {
        String payload = subscriptionDeletedPayload("evt_test_signature_ok", "sub_test_xyz");
        String signature = sign(payload, WEBHOOK_SECRET, Instant.now().getEpochSecond());

        service.process(payload, signature);

        ArgumentCaptor<ProcessedStripeEvent> captor = ArgumentCaptor.forClass(ProcessedStripeEvent.class);
        verify(processedRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStripeEventId()).isEqualTo("evt_test_signature_ok");
        assertThat(captor.getValue().getEventType()).isEqualTo("customer.subscription.deleted");
        // Dispatcher reached: handler looked up our subscription by Stripe id
        verify(subscriptionRepository).findByStripeSubscriptionId("sub_test_xyz");
    }

    @Test
    void duplicateEvent_skipsDispatch() throws Exception {
        String payload = subscriptionDeletedPayload("evt_test_duplicate", "sub_test_xyz");
        String signature = sign(payload, WEBHOOK_SECRET, Instant.now().getEpochSecond());
        when(processedRepository.saveAndFlush(any(ProcessedStripeEvent.class)))
                .thenThrow(new DataIntegrityViolationException("dup"));

        service.process(payload, signature);

        verify(processedRepository).saveAndFlush(any(ProcessedStripeEvent.class));
        verify(subscriptionRepository, never()).findByStripeSubscriptionId(any());
    }

    @Test
    void invalidSignature_throws() {
        String payload = subscriptionDeletedPayload("evt_test_bad_sig", "sub_test_xyz");
        // Sign with the wrong secret
        String signature = sign(payload, "whsec_wrong", Instant.now().getEpochSecond());

        assertThatThrownBy(() -> service.process(payload, signature))
                .isInstanceOf(SignatureVerificationException.class);
        verify(processedRepository, never()).saveAndFlush(any(ProcessedStripeEvent.class));
    }

    private static String sign(String payload, String secret, long timestamp) {
        try {
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) hex.append(String.format("%02x", b));
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC failed", ex);
        }
    }

    private static String subscriptionDeletedPayload(String eventId, String stripeSubscriptionId) {
        return "{"
                + "\"id\":\"" + eventId + "\","
                + "\"object\":\"event\","
                + "\"api_version\":\"" + Stripe.API_VERSION + "\","
                + "\"type\":\"customer.subscription.deleted\","
                + "\"created\":1700000000,"
                + "\"data\":{\"object\":{"
                + "\"id\":\"" + stripeSubscriptionId + "\","
                + "\"object\":\"subscription\","
                + "\"customer\":\"cus_test_abc\","
                + "\"status\":\"canceled\""
                + "}}"
                + "}";
    }
}
