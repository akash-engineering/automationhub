package com.automationhub.workflow.idempotency;

import com.automationhub.workflow.repository.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;

    public IdempotencyService(IdempotencyKeyRepository repository) {
        this.repository = repository;
    }

    public Optional<UUID> findExecutionId(String key) {
        return repository.findByKey(key).map(IdempotencyKey::getExecutionId);
    }

    public UUID record(String key, UUID ownerId, UUID workflowId, UUID executionId) {
        IdempotencyKey record = IdempotencyKey.builder()
                .key(key)
                .ownerId(ownerId)
                .workflowId(workflowId)
                .executionId(executionId)
                .build();
        try {
            repository.saveAndFlush(record);
            return executionId;
        } catch (DataIntegrityViolationException ex) {
            return repository.findByKey(key)
                    .map(IdempotencyKey::getExecutionId)
                    .orElseThrow(() -> ex);
        }
    }
}
