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
implementation("io.github.dskripchenko:printable-sdk:1.1.0")
```

Until then — include as a source/jar dependency from this repository
(`gradle jar` produces `build/libs/printable-sdk-1.1.0.jar`).

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
`printFormSignatureInvite` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)` (escape hatch).

Every method returns the decoded `payload` map of the response envelope.
Errors (non-2xx or `success: false`) throw `PrintableException` with
`getStatus()`, `getErrorKey()` and the raw error `getPayload()`.

## Signing and protection

`print-form/create` and `print-form/batch` accept optional parameters, passed as-is in the params:

- `preset` — a print-preset code used instead of `template` (`document-draft/create` accepts it too);
- `signing` — a signature profile per slot, a visible or invisible signature, the reason, and `recipients` — the counterparties who sign the document themselves;
- `protection` — PDF protection: `{"generate": true}` lets the service generate the password, or pass your own one under `password`; the optional `permissions` is `read_print`, `read_only` or `forms_sign`.

The response then carries a `protection` block (`protected`, `permissions`, `password`, `owner_password`). **Passwords are returned only once** — in the response that issues the document; afterwards they come back masked (`••••••••`), so store them right away. Each `signature.slots[]` item may carry `method` (`cms`, `plugin`, `pdf` or `sig`) and `signature_link` — the recipient's detached `.sig`.

```java
Map<String, Object> doc = client.printFormCreate(Map.of(
    "preset", "contract-signed",
    "variables", Map.of("contract", Map.of("number", 15)),
    "signing", Map.of(
        "slots", Map.of("company", Map.of("profile", "ceo")),
        "invisible", false,
        "reason", "Contract 15",
        "recipients", Map.of("buyer", Map.of("email", "buyer@example.com", "name", "Buyer LLC"))),
    "protection", Map.of("generate", true, "permissions", "read_print")));
// The password comes only in this response — store it now
Object protection = doc.get("protection");

// Hand the link over yourself, or let the service email it
Map<String, Object> invite = client.printFormSignatureInvite(
    (String) doc.get("uuid"), "buyer@example.com", "buyer", "Buyer LLC");
System.out.println(invite.get("sign_url"));
```

`printFormSignatureInvite()` invites the recipient to sign: it returns `sign_url` — a personal link to the signing page (CryptoPro browser plug-in, a detached `.sig` upload, or a PDF signed in its field) — and `expires_at`. With an email address the service also sends the link (`sent`).

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
