package io.github.dskripchenko.printable;

import java.util.Collections;
import java.util.Map;

/**
 * API-level error: non-2xx response or {@code success: false} envelope.
 */
public class PrintableException extends RuntimeException {

    private final int status;
    private final String errorKey;
    private final Map<String, Object> payload;

    public PrintableException(String message, int status, String errorKey, Map<String, Object> payload) {
        super(message);
        this.status = status;
        this.errorKey = errorKey;
        this.payload = payload == null ? Collections.emptyMap() : payload;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
