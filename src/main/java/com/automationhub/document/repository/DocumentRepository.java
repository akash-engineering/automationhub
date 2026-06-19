package com.automationhub.document.repository;

import com.automationhub.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findAllByOwnerId(UUID ownerId, Pageable pageable);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);
}
