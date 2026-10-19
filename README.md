# Printable SDK for Java

Official Java client for the Printable document-generation service
integration API: templates, print-forms, batch rendering, document drafts
and webhook verification — with HMAC request signing (simple and strict
canonical modes) built in and byte-compatible with the server implementation.

> 🌐 **English** · [Deutsch](docs/de/README.md) · [Русский](docs/ru/README.md) · [中文](docs/zh/README.md)

Java 17+. Single dependency: Jackson databind. **Server-side only** —
the secret key must never reach untrusted environments.

## Install

```kotlin
// build.gradle.kts (after the artifact is published to Maven Central)
implementation("io.github.dskripchenko:printable-sdk:1.0.0")
```

Until then — include as a source/jar dependency from this repository
(`gradle jar` produces `build/libs/printable-sdk-1.0.0.jar`).

## Quick start

```java
PrintableClient client = PrintableClient.builder()
    .baseUrl("https://printable.example.com")
    .token("erp-main-7f3a")
    .secretKey(System.getenv("PRINTABLE_SECRET"))
    .salt(System.getenv("PRINTABLE_SALT"))
    .strict(false) // true if the credential has "verify full payload hash" enabled
    .build();

// Render a document
Map<String, Object> doc = client.printFormCreate(Map.of(
    "template", "invoice",
    "variables", Map.of("company", Map.of("name", "ACME Ltd")),
    "format", "link"));
System.out.println(doc.get("link"));

// Batch with archive + email delivery
Map<String, Object> batch = client.printFormBatch(Map.of(
    "template", "certificate",
    "items", List.of(
        Map.of("variables", Map.of("name", "Alice")),
        Map.of("variables", Map.of("name", "Bob"))),
    "archive", true,
    "deliver", Map.of("email", "hr@example.com")));
Map<String, Object> status = client.printFormBatchGet((String) batch.get("uuid"));
```

## API surface

`templateList` · `templateContract` · `templateExport` · `templateImport` ·
`templateImportDocx` · `templateImportXlsx` · `printFormCreate` · `printFormGet` ·
`printFormRules` · `printFormSignatureRequest` · `printFormSignatureComplete` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)` (escape hatch).

Every method returns the decoded `payload` map of the response envelope.
Errors (non-2xx or `success: false`) throw `PrintableException` with
`getStatus()`, `getErrorKey()` and the raw error `getPayload()`.

## Webhooks

```java
boolean valid = client.verifyWebhook(
    rawBody, // raw request body string
    request.getHeader("X-Printable-Signature"),
    request.getHeader("X-Printable-Timestamp"));
```

## Testing your integration

Inject a custom `Transport` to stub HTTP:

```java
PrintableClient client = PrintableClient.builder()
    /* … */
    .transport((method, url, body) ->
        new PrintableClient.Transport.Response(200, "{\"success\":true,\"payload\":{}}"))
    .build();
```

## License

MIT
