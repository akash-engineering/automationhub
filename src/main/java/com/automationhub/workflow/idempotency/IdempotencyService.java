package com.automationhub.workflow.idempotency;

import com.automationhub.workflow.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    // REQUIRES_NEW so a unique-constraint violation rolls back ONLY this insert,
    // leaving the caller's transaction (which already holds an uncommitted Execution
    // row) usable. The caller catches DataIntegrityViolationException, looks up the
    // winner, and discards its orphan execution.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID record(String key, UUID ownerId, UUID workflowId, UUID executionId) {
        IdempotencyKey record = IdempotencyKey.builder()
                .key(key)
                .ownerId(ownerId)
                .workflowId(workflowId)
                .executionId(executionId)
                .build();
        repository.saveAndFlush(record);
        return executionId;
    }
}
