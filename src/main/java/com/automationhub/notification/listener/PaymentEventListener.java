package com.automationhub.notification.listener;

import com.automationhub.notification.dto.NotificationRequest;
import com.automationhub.notification.entity.NotificationChannel;
import com.automationhub.notification.service.NotificationService;
import com.automationhub.payment.event.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final NotificationService notificationService;

    public PaymentEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("automationHubTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        MDC.put("paymentId", event.paymentId().toString());
        try {
            log.info("received PaymentSucceededEvent ownerId={} paymentId={} amount={} occurredAt={}",
                    event.ownerId(), event.paymentId(), event.amount(), event.occurredAt());
            String subject = "Payment received: " + event.amount();
            String body = "Payment " + event.paymentId() + " of " + event.amount()
                    + " succeeded for subscription " + event.subscriptionId()
                    + " at " + event.occurredAt();
            String recipient = "owner:" + event.ownerId();
            for (NotificationChannel channel : NotificationChannel.values()) {
                notificationService.send(new NotificationRequest(
                        channel, recipient, subject, body,
                        null, null, event.ownerId()));
            }
        } finally {
            MDC.remove("paymentId");
        }
    }
}
