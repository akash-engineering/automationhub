package com.automationhub.workflow.repository;

import com.automationhub.workflow.entity.Execution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    Page<Execution> findAllByWorkflowIdOrderByCreatedAtDesc(UUID workflowId, Pageable pageable);
}
