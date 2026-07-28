package io.github.dskripchenko.printable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Request/webhook signing, byte-compatible with the Printable server
 * ({@code App\Auth\HashSigner}).
 */
public final class Signer {

    private final String salt;

    public Signer(String salt) {
        if (salt == null || salt.isEmpty()) {
            throw new IllegalArgumentException("Printable salt must not be empty.");
        }
        this.salt = salt;
    }

    /** simple mode: sha256(token + timestamp + salt + secret). */
    public String signToken(String token, long timestamp, String secretKey) {
        return sha256Hex(token + timestamp + salt + secretKey);
    }

    /** strict mode: HMAC-SHA256(canonical(payload), secret + salt). */
    public String signPayload(Map<String, ?> payload, String secretKey) {
        return hmacHex(Canonical.canonicalize(payload), secretKey + salt);
    }

    /** Verify an incoming webhook signature (value without the "sha256=" prefix). */
    public boolean verifyWebhook(String signature, long timestamp, String rawBody, String secretKey) {
        String expected = hmacHex(timestamp + "." + rawBody, secretKey);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hmacHex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
