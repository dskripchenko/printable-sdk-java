package io.github.dskripchenko.printable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Client for the Printable integration API ({@code /api/integration}).
 *
 * <pre>{@code
 * PrintableClient client = PrintableClient.builder()
 *     .baseUrl("https://printable.example.com")
 *     .token("erp-main-...")
 *     .secretKey(System.getenv("PRINTABLE_SECRET"))
 *     .salt(System.getenv("PRINTABLE_SALT"))
 *     .build();
 *
 * Map<String, Object> doc = client.printFormCreate(Map.of(
 *     "template", "invoice",
 *     "variables", Map.of("company", Map.of("name", "ACME Ltd"))));
 * System.out.println(doc.get("link"));
 * }</pre>
 *
 * Every call returns the decoded {@code payload} map of the response
 * envelope; errors raise {@link PrintableException}. Server-side only:
 * the secret key must never reach untrusted environments.
 */
public final class PrintableClient {

    /** Transport: (method, url, jsonBodyOrNull) → (status, rawBody). */
    @FunctionalInterface
    public interface Transport {
        Response send(String method, String url, String jsonBody);

        record Response(int status, String body) {
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String token;
    private final String secretKey;
    private final boolean strict;
    private final Duration timeout;
    private final Signer signer;
    private final Transport transport;

    private PrintableClient(Builder builder) {
        this.baseUrl = builder.baseUrl.replaceAll("/+$", "");
        this.token = builder.token;
        this.secretKey = builder.secretKey;
        this.strict = builder.strict;
        this.timeout = builder.timeout;
        this.signer = new Signer(builder.salt);
        this.transport = builder.transport != null ? builder.transport : httpTransport();
    }

    public static Builder builder() {
        return new Builder();
    }

    // -- Templates ------------------------------------------------------

    public Map<String, Object> templateList() {
        return call("GET", "template/list", Map.of());
    }

    public Map<String, Object> templateContract(String template) {
        return call("GET", "template/contract", Map.of("template", template));
    }

    public Map<String, Object> templateExport(String template) {
        return call("GET", "template/export", Map.of("template", template));
    }

    public Map<String, Object> templateImport(String packageB64, long groupId) {
        return call("POST", "template/import", Map.of("package_b64", packageB64, "group_id", groupId));
    }

    public Map<String, Object> templateImportDocx(String docxB64, long groupId, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("docx_b64", docxB64);
        params.put("group_id", groupId);
        if (name != null) {
            params.put("name", name);
        }
        return call("POST", "template/import-docx", params);
    }

    /**
     * Imports an Excel workbook (.xlsx) as a template draft into an existing
     * group: one section per non-empty sheet, printed at the sheet's own print
     * scale.
     */
    public Map<String, Object> templateImportXlsx(String xlsxB64, long groupId, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xlsx_b64", xlsxB64);
        params.put("group_id", groupId);
        if (name != null) {
            params.put("name", name);
        }
        return call("POST", "template/import-xlsx", params);
    }

    /**
     * Imports an Excel workbook (.xlsx) as a template draft into the group at
     * a path ("Реестры / Отчёты"), which the service creates when it is
     * missing.
     */
    public Map<String, Object> templateImportXlsx(String xlsxB64, String groupPath, String name) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("xlsx_b64", xlsxB64);
        params.put("group", groupPath);
        if (name != null) {
            params.put("name", name);
        }
        return call("POST", "template/import-xlsx", params);
    }

    // -- Print forms ----------------------------------------------------

    public Map<String, Object> printFormCreate(Map<String, ?> params) {
        return call("POST", "print-form/create", params);
    }

    public Map<String, Object> printFormGet(String uuid) {
        return call("GET", "print-form/get", Map.of("uuid", uuid, "format", "link"));
    }

    /**
     * Asks for the digest an external signer signs: for a document in
     * {@code awaiting_signature}, with the signer's certificate (PEM, or DER
     * in base64 as CryptoPro exports it). Sign the digest into a detached CMS
     * and pass it to {@link #printFormSignatureComplete} before
     * {@code expires_at}. A null {@code slot} means the signature field the
     * document waits for.
     */
    public Map<String, Object> printFormSignatureRequest(String uuid, String certificate, String slot) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uuid", uuid);
        params.put("certificate", certificate);
        if (slot != null) {
            params.put("slot", slot);
        }
        return call("POST", "print-form/signature-request", params);
    }

    /**
     * Hands in the external signature: the detached CMS (base64 or PEM). The
     * answer is the document — {@code done} when every signature is in, else
     * still {@code awaiting_signature} for the next signer. {@code format} is
     * {@code link} or {@code b64}.
     */
    public Map<String, Object> printFormSignatureComplete(String uuid, String cms, String slot, String format) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uuid", uuid);
        params.put("cms", cms);
        if (slot != null) {
            params.put("slot", slot);
        }
        params.put("format", format != null ? format : "link");
        return call("POST", "print-form/signature-complete", params);
    }

    public Map<String, Object> printFormRules(String template) {
        return call("GET", "print-form/rules", Map.of("template", template));
    }

    public Map<String, Object> printFormBatch(Map<String, ?> params) {
        return call("POST", "print-form/batch", params);
    }

    public Map<String, Object> printFormBatchGet(String uuid) {
        return call("GET", "print-form/batch-get", Map.of("uuid", uuid));
    }

    // -- Document drafts ------------------------------------------------

    public Map<String, Object> documentDraftCreate(Map<String, ?> params) {
        return call("POST", "document-draft/create", params);
    }

    public Map<String, Object> documentDraftGet(String uuid) {
        return call("GET", "document-draft/get", Map.of("uuid", uuid));
    }

    // -- Webhooks -------------------------------------------------------

    /** Verify an incoming Printable webhook against its signature headers. */
    public boolean verifyWebhook(String rawBody, String signatureHeader, String timestampHeader) {
        String signature = signatureHeader.startsWith("sha256=")
                ? signatureHeader.substring("sha256=".length())
                : signatureHeader;
        return signer.verifyWebhook(signature, Long.parseLong(timestampHeader.trim()), rawBody, secretKey);
    }

    // -- Core -----------------------------------------------------------

    /** Signed call to any {@code {controller}/{action}}; returns envelope payload. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> call(String method, String endpoint, Map<String, ?> params) {
        long timestamp = System.currentTimeMillis() / 1000L;
        Map<String, Object> payload = new LinkedHashMap<>(params);
        payload.put("timestamp", timestamp);

        String hash = strict
                ? signer.signPayload(payload, secretKey)
                : signer.signToken(token, timestamp, secretKey);

        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.put("token", token);
        body.put("hash", hash);

        String url = baseUrl + "/api/integration/" + endpoint.replaceAll("^/+", "");
        Transport.Response response;
        if ("GET".equalsIgnoreCase(method)) {
            StringJoiner query = new StringJoiner("&");
            body.forEach((key, value) -> query.add(
                    URLEncoder.encode(key, StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8)));
            response = transport.send("GET", url + "?" + query, null);
        } else {
            response = transport.send(method.toUpperCase(), url, toJson(body));
        }

        Map<String, Object> decoded;
        try {
            decoded = MAPPER.readValue(response.body(), new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException e) {
            throw new PrintableException(
                    "Printable API returned a non-JSON response (HTTP " + response.status() + ")",
                    response.status(), null, null);
        }

        Map<String, Object> payloadOut = decoded.get("payload") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        if (response.status() >= 400 || !Boolean.TRUE.equals(decoded.get("success"))) {
            String message = payloadOut.get("message") instanceof String text
                    ? text
                    : "Printable API error (HTTP " + response.status() + ")";
            String errorKey = payloadOut.get("errorKey") instanceof String key ? key : null;
            throw new PrintableException(message, response.status(), errorKey, payloadOut);
        }
        return payloadOut;
    }

    private static String toJson(Map<String, Object> body) {
        try {
            return MAPPER.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize request body", e);
        }
    }

    private Transport httpTransport() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return (method, url, jsonBody) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json");
            if (jsonBody == null) {
                request.GET();
            } else {
                request.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            }
            try {
                HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
                return new Transport.Response(response.statusCode(), response.body());
            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PrintableException("Printable API request failed: " + e.getMessage(), 0, null, null);
            }
        };
    }

    public static final class Builder {
        private String baseUrl;
        private String token;
        private String secretKey;
        private String salt;
        private boolean strict;
        private Duration timeout = Duration.ofSeconds(30);
        private Transport transport;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder salt(String salt) {
            this.salt = salt;
            return this;
        }

        /** true when the credential requires full-payload HMAC verification. */
        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /** Custom transport (testing / instrumentation). */
        public Builder transport(Transport transport) {
            this.transport = transport;
            return this;
        }

        public PrintableClient build() {
            return new PrintableClient(this);
        }
    }
}
