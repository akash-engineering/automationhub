package com.automationhub.workflow.repository;

import com.automationhub.workflow.entity.ExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, UUID> {

    List<ExecutionLog> findByExecutionIdOrderByActionOrderAsc(UUID executionId);
}
