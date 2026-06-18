package com.automationhub.workflow.repository;

import com.automationhub.workflow.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Workflow> findAllByOwnerId(UUID ownerId, Pageable pageable);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);
}
