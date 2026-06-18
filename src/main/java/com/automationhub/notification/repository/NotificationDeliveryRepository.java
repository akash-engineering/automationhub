package com.automationhub.notification.repository;

import com.automationhub.notification.entity.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByExecutionIdOrderByCreatedAtAsc(UUID executionId);

    List<NotificationDelivery> findByOwnerIdAndExecutionIdOrderByCreatedAtAsc(UUID ownerId, UUID executionId);
}
