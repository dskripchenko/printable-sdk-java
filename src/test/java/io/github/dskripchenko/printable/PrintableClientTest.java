package io.github.dskripchenko.printable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class PrintableClientTest {

    record Captured(String method, String url, String body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PrintableClient makeClient(List<Captured> captured, boolean strict,
                                       int status, String response) {
        return PrintableClient.builder()
                .baseUrl("https://printable.test")
                .token("tok-1").secretKey("sec-1").salt("salt-123")
                .strict(strict)
                .transport((method, url, body) -> {
                    captured.add(new Captured(method, url, body));
                    return new PrintableClient.Transport.Response(status, response);
                })
                .build();
    }

    @Test
    void postSendsSignedBodyWithSimpleHash() throws Exception {
        List<Captured> captured = new ArrayList<>();
        PrintableClient client = makeClient(captured, false, 200,
                "{\"success\":true,\"payload\":{\"ok\":1}}");

        Map<String, Object> payload = client.printFormCreate(
                Map.of("template", "invoice", "variables", Map.of("a", 1)));

        assertEquals(Map.of("ok", 1), payload);
        Captured request = captured.get(0);
        assertEquals("POST", request.method());
        assertEquals("https://printable.test/api/integration/print-form/create", request.url());
        Map<String, Object> body = MAPPER.readValue(request.body(), new TypeReference<>() {
        });
        assertEquals("tok-1", body.get("token"));
        long timestamp = ((Number) body.get("timestamp")).longValue();
        assertEquals(new Signer("salt-123").signToken("tok-1", timestamp, "sec-1"), body.get("hash"));
    }

    @Test
    void strictModeSignsPayloadWithoutTokenAndHash() throws Exception {
        List<Captured> captured = new ArrayList<>();
        PrintableClient client = makeClient(captured, true, 200,
                "{\"success\":true,\"payload\":{}}");
        client.printFormCreate(Map.of("template", "invoice"));

        Map<String, Object> body = MAPPER.readValue(captured.get(0).body(), new TypeReference<>() {
        });
        String hash = (String) body.remove("hash");
        body.remove("token");
        // The timestamp decodes as an Integer/Long — equivalent for the canonicalization.
        Map<String, Object> signed = new LinkedHashMap<>(body);
        assertEquals(new Signer("salt-123").signPayload(signed, "sec-1"), hash);
    }

    @Test
    void getPutsAuthIntoQueryString() {
        List<Captured> captured = new ArrayList<>();
        PrintableClient client = makeClient(captured, false, 200,
                "{\"success\":true,\"payload\":{}}");
        client.printFormBatchGet("0f8fad5b-d9cb-469f-a165-70867728950e");

        Captured request = captured.get(0);
        assertEquals("GET", request.method());
        assertNull(request.body());
        assertTrue(request.url().contains("uuid=0f8fad5b-d9cb-469f-a165-70867728950e"));
        assertTrue(request.url().contains("token=tok-1"));
        assertTrue(request.url().contains("hash="));
        assertTrue(request.url().contains("timestamp="));
    }

    @Test
    void errorEnvelopeRaisesWithErrorKey() {
        PrintableClient client = makeClient(new ArrayList<>(), false, 401,
                "{\"success\":false,\"payload\":{\"errorKey\":\"auth-token-invalid\",\"message\":\"Bad token\"}}");

        PrintableException exception = assertThrows(PrintableException.class, client::templateList);
        assertEquals(401, exception.getStatus());
        assertEquals("auth-token-invalid", exception.getErrorKey());
        assertEquals("Bad token", exception.getMessage());
    }

    @Test
    void nonJsonResponseRaises() {
        PrintableClient client = makeClient(new ArrayList<>(), false, 502, "<html>Bad gateway</html>");
        PrintableException exception = assertThrows(PrintableException.class, client::templateList);
        assertTrue(exception.getMessage().contains("non-JSON"));
    }

    @Test
    void verifyWebhookAcceptsPrefixedHeader() throws Exception {
        PrintableClient client = makeClient(new ArrayList<>(), false, 200, "{}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("sec-1".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        StringBuilder hex = new StringBuilder();
        for (byte b : mac.doFinal("1753600000.{\"uuid\":\"abc\"}".getBytes(StandardCharsets.UTF_8))) {
            hex.append(String.format("%02x", b));
        }
        assertTrue(client.verifyWebhook("{\"uuid\":\"abc\"}", "sha256=" + hex, "1753600000"));
        assertFalse(client.verifyWebhook("{\"uuid\":\"abc\"}", "sha256=deadbeef", "1753600000"));
    }

    private static Map<String, Object> bodyOf(Captured request) throws Exception {
        return MAPPER.readValue(request.body(), new TypeReference<>() {
        });
    }

    @Test
    void templateImportXlsxTakesGroupIdOrPath() throws Exception {
        List<Captured> captured = new ArrayList<>();
        PrintableClient client = makeClient(captured, false, 200, "{\"success\":true,\"payload\":{}}");
        client.templateImportXlsx("UEsDBBQ=", 7, null);
        client.templateImportXlsx("UEsDBBQ=", "Реестры / Отчёты", "Отчёт");

        assertEquals("https://printable.test/api/integration/template/import-xlsx", captured.get(0).url());
        Map<String, Object> first = bodyOf(captured.get(0));
        assertEquals(7, ((Number) first.get("group_id")).intValue());
        assertFalse(first.containsKey("group"));
        assertFalse(first.containsKey("name"));
        Map<String, Object> second = bodyOf(captured.get(1));
        assertEquals("Реестры / Отчёты", second.get("group"));
        assertFalse(second.containsKey("group_id"));
        assertEquals("Отчёт", second.get("name"));
    }

    @Test
    void signatureRequestSendsCertificateAndSlotWhenGiven() throws Exception {
        List<Captured> captured = new ArrayList<>();
        PrintableClient client = makeClient(captured, false, 200, "{\"success\":true,\"payload\":{}}");
        client.printFormSignatureRequest("11111111-1111-1111-1111-111111111111", "MIIB", null);
        client.printFormSignatureRequest("11111111-1111-1111-1111-111111111111", "MIIB", "counterparty");

        assertEquals("POST", captured.get(0).method());
        assertEquals("https://printable.test/api/integration/print-form/signature-request", captured.get(0).url());
        Map<String, Object> first = bodyOf(captured.get(0));
        assertEquals("MIIB", first.get("certificate"));
        assertFalse(first.containsKey("slot"));
        assertEquals("counterparty", bodyOf(captured.get(1)).get("slot"));
    }

    @Test
    void signatureCompleteSendsCmsAndFormat() throws Exception {
        List<Captured> captured = new ArrayList<>();
        PrintableClient client = makeClient(captured, false, 200, "{\"success\":true,\"payload\":{}}");
        client.printFormSignatureComplete("11111111-1111-1111-1111-111111111111", "MIAG", null, "b64");

        assertEquals("https://printable.test/api/integration/print-form/signature-complete", captured.get(0).url());
        Map<String, Object> body = bodyOf(captured.get(0));
        assertEquals("MIAG", body.get("cms"));
        assertEquals("b64", body.get("format"));
        assertFalse(body.containsKey("slot"));
    }
}
