package com.automationhub.workflow.repository;

import com.automationhub.workflow.entity.Action;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActionRepository extends JpaRepository<Action, UUID> {

    List<Action> findByWorkflowIdOrderByOrderAsc(UUID workflowId);

    void deleteByWorkflowId(UUID workflowId);
}
