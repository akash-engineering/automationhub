package com.automationhub.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConditionalOnProperty(
        name = "automationhub.document.storage.provider",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalFileStorageService implements StorageService {

    private static final String PROVIDER = "local";
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(
            @Value("${automationhub.document.storage.local.root:./var/documents}") String rootPath) {
        this.root = Paths.get(rootPath).toAbsolutePath().normalize();
        log.info("LocalFileStorageService root={}", this.root);
    }

    @Override
    public StorageLocation put(String key, byte[] bytes, String contentType) {
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(root);
            Files.write(target, bytes);
            log.info("stored document key={} bytes={} path={}", key, bytes.length, target);
            return new StorageLocation(key, PROVIDER);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to store document " + key, ex);
        }
    }

    @Override
    public byte[] get(String key) {
        Path target = resolveSafe(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to read document " + key, ex);
        }
    }

    private Path resolveSafe(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("storage key escapes root: " + key);
        }
        return target;
    }
}
