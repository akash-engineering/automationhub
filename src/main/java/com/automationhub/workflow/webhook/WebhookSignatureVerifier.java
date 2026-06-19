package com.automationhub.workflow.webhook;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

@Component
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SCHEME_PREFIX = "sha256=";
    private static final Duration MAX_SKEW = Duration.ofMinutes(5);

    public boolean verify(String secret, String timestampHeader, String signatureHeader, String rawBody) {
        if (secret == null || secret.isBlank()) return false;
        if (timestampHeader == null || signatureHeader == null || rawBody == null) return false;
        if (!signatureHeader.startsWith(SCHEME_PREFIX)) return false;

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            return false;
        }
        Instant signedAt = Instant.ofEpochSecond(timestamp);
        Duration delta = Duration.between(signedAt, Instant.now()).abs();
        if (delta.compareTo(MAX_SKEW) > 0) return false;

        byte[] expected = hmac(secret, timestamp + "." + rawBody);
        byte[] provided = decodeHex(signatureHeader.substring(SCHEME_PREFIX.length()));
        if (provided == null) return false;
        return MessageDigest.isEqual(expected, provided);
    }

    private static byte[] hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC computation failed", ex);
        }
    }

    private static byte[] decodeHex(String hex) {
        if (hex.length() % 2 != 0) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
