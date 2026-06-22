package com.automationhub.payment.repository;

import com.automationhub.payment.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Subscription> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Subscription> findFirstByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
