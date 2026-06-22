package com.automationhub.payment.repository;

import com.automationhub.payment.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findAllByOrderByAmountAsc();

    Optional<Plan> findByStripePriceId(String stripePriceId);
}
