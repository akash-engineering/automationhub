package com.automationhub.document.controller;

import com.automationhub.document.dto.DocumentResponse;
import com.automationhub.document.entity.Document;
import com.automationhub.document.service.DocumentService;
import com.automationhub.infrastructure.security.CurrentUser;
import com.automationhub.shared.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final CurrentUser currentUser;

    public DocumentController(DocumentService documentService, CurrentUser currentUser) {
        this.documentService = documentService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return documentService.list(currentUser.requireId(), pageable);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return documentService.get(id, currentUser.requireId());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        DocumentService.DownloadResult result = documentService.download(id, currentUser.requireId());
        Document doc = result.document();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, doc.getContentType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(doc.getSizeBytes()))
                .body(result.bytes());
    }
}
