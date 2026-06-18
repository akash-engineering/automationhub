package com.automationhub.notification.service;

import com.automationhub.notification.dto.NotificationDeliveryResponse;
import com.automationhub.notification.dto.NotificationRequest;
import com.automationhub.notification.entity.DeliveryStatus;
import com.automationhub.notification.entity.NotificationChannel;
import com.automationhub.notification.entity.NotificationDelivery;
import com.automationhub.notification.repository.NotificationDeliveryRepository;
import com.automationhub.notification.sender.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Map<NotificationChannel, Sender> senders;
    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationService(List<Sender> senders, NotificationDeliveryRepository deliveryRepository) {
        Map<NotificationChannel, Sender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannel channel : NotificationChannel.values()) {
            List<Sender> matches = senders.stream().filter(s -> s.supports(channel)).toList();
            if (matches.size() > 1) {
                throw new IllegalStateException("Multiple Sender beans support " + channel + ": " + matches);
            }
            if (matches.size() == 1) {
                map.put(channel, matches.get(0));
            }
        }
        this.senders = Map.copyOf(map);
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public NotificationDeliveryResponse send(NotificationRequest request) {
        Sender sender = senders.get(request.channel());
        DeliveryStatus status;
        String message;

        if (sender == null) {
            status = DeliveryStatus.FAILED;
            message = "no sender registered for channel " + request.channel();
            log.warn("notification skipped: {}", message);
        } else {
            try {
                sender.send(request);
                status = DeliveryStatus.SENT;
                message = request.subject();
            } catch (Exception ex) {
                status = DeliveryStatus.FAILED;
                message = ex.getMessage();
                log.warn("notification send failed: channel={} executionId={} reason={}",
                        request.channel(), request.executionId(), ex.getMessage());
            }
        }

        NotificationDelivery saved = deliveryRepository.save(NotificationDelivery.builder()
                .executionId(request.executionId())
                .workflowId(request.workflowId())
                .ownerId(request.ownerId())
                .channel(request.channel())
                .status(status)
                .recipient(request.recipient())
                .message(message)
                .build());
        return NotificationDeliveryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryResponse> listForExecution(UUID executionId, UUID ownerId) {
        return deliveryRepository.findByOwnerIdAndExecutionIdOrderByCreatedAtAsc(ownerId, executionId).stream()
                .map(NotificationDeliveryResponse::from)
                .toList();
    }
}
