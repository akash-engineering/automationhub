package com.automationhub.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "automationhub.document.storage.provider",
        havingValue = "s3"
)
public class S3StorageService implements StorageService {

    private static final String PROVIDER = "s3";
    private static final String STUB_MESSAGE = "S3 storage stub — real AWS integration arrives in Increment 4";
    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final String bucket;
    private final String region;

    public S3StorageService(
            @Value("${automationhub.document.storage.s3.bucket:}") String bucket,
            @Value("${automationhub.document.storage.s3.region:}") String region) {
        this.bucket = bucket;
        this.region = region;
        log.info("S3StorageService wired (inactive stub) bucket={} region={}", bucket, region);
    }

    @Override
    public StorageLocation put(String key, byte[] bytes, String contentType) {
        log.warn("S3 put attempted (stub): key={} bytes={} contentType={} bucket={} region={}",
                key, bytes.length, contentType, bucket, region);
        throw new UnsupportedOperationException(STUB_MESSAGE);
    }

    @Override
    public byte[] get(String key) {
        log.warn("S3 get attempted (stub): key={} bucket={} region={}", key, bucket, region);
        throw new UnsupportedOperationException(STUB_MESSAGE);
    }
}
