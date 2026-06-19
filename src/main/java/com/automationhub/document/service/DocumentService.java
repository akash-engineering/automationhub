package com.automationhub.document.service;

import com.automationhub.document.dto.DocumentResponse;
import com.automationhub.document.entity.Document;
import com.automationhub.document.repository.DocumentRepository;
import com.automationhub.document.storage.StorageLocation;
import com.automationhub.document.storage.StorageService;
import com.automationhub.shared.exception.ResourceNotFoundException;
import com.automationhub.shared.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final PdfInvoiceRenderer renderer;

    public DocumentService(DocumentRepository documentRepository,
                           StorageService storageService,
                           PdfInvoiceRenderer renderer) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.renderer = renderer;
    }

    @Transactional
    public Document generate(InvoiceData data, UUID ownerId, UUID executionId) {
        byte[] bytes = renderer.render(data);
        String storageKey = UUID.randomUUID() + ".pdf";
        String filename = sanitize(data.title()) + ".pdf";
        StorageLocation location = storageService.put(storageKey, bytes, PDF_CONTENT_TYPE);
        Document saved = documentRepository.save(Document.builder()
                .ownerId(ownerId)
                .executionId(executionId)
                .filename(filename)
                .contentType(PDF_CONTENT_TYPE)
                .storageKey(location.key())
                .sizeBytes(bytes.length)
                .build());
        log.info("generated document id={} owner={} executionId={} key={} bytes={}",
                saved.getId(), ownerId, executionId, location.key(), bytes.length);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(UUID ownerId, Pageable pageable) {
        Page<Document> page = documentRepository.findAllByOwnerId(ownerId, pageable);
        List<DocumentResponse> content = page.getContent().stream()
                .map(DocumentResponse::from)
                .toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID id, UUID ownerId) {
        return DocumentResponse.from(require(id, ownerId));
    }

    @Transactional(readOnly = true)
    public DownloadResult download(UUID id, UUID ownerId) {
        Document doc = require(id, ownerId);
        byte[] bytes = storageService.get(doc.getStorageKey());
        return new DownloadResult(doc, bytes);
    }

    private Document require(UUID id, UUID ownerId) {
        return documentRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "document";
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public record DownloadResult(Document document, byte[] bytes) {
    }
}
