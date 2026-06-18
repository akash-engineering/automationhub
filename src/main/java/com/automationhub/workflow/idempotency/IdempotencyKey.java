package com.automationhub.workflow.idempotency;

import com.automationhub.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = "key_value")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey extends BaseEntity {

    @Column(name = "key_value", nullable = false)
    private String key;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID workflowId;

    @Column(nullable = false)
    private UUID executionId;
}
