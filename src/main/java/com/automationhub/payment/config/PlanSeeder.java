package com.automationhub.payment.config;

import com.automationhub.payment.entity.Plan;
import com.automationhub.payment.entity.PlanInterval;
import com.automationhub.payment.repository.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanSeeder.class);

    private final PlanRepository planRepository;

    public PlanSeeder(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (planRepository.count() > 0) {
            return;
        }
        planRepository.saveAll(List.of(
                Plan.builder()
                        .name("Starter")
                        .stripePriceId("price_starter_placeholder")
                        .amount(new BigDecimal("9.00"))
                        .interval(PlanInterval.MONTH)
                        .build(),
                Plan.builder()
                        .name("Pro")
                        .stripePriceId("price_pro_placeholder")
                        .amount(new BigDecimal("29.00"))
                        .interval(PlanInterval.MONTH)
                        .build()
        ));
        log.info("Seeded default plans: Starter ($9/mo), Pro ($29/mo)");
    }
}
