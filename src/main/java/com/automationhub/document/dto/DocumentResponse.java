package com.automationhub.document.dto;

import com.automationhub.document.entity.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID executionId,
        String filename,
        String contentType,
        long sizeBytes,
        Instant createdAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getExecutionId(),
                d.getFilename(),
                d.getContentType(),
                d.getSizeBytes(),
                d.getCreatedAt()
        );
    }
}
