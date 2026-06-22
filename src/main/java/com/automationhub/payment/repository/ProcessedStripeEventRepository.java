package com.automationhub.payment.repository;

import com.automationhub.payment.entity.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, UUID> {
}
