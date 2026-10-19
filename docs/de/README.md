# Printable-SDK für Java

Offizieller Java-Client für die Integrations-API des Dokumentendienstes
[Printable](https://printable.dev-cloud.space): Vorlagen, Druckformulare,
Stapelverarbeitung, Dokumententwürfe und Webhook-Prüfung. Die HMAC-Signatur der
Anfragen (einfacher und strenger kanonischer Modus) ist eingebaut und
byteweise identisch mit der Server-Implementierung.

> 🌐 [English](../../README.md) · **Deutsch** · [Русский](../ru/README.md) · [中文](../zh/README.md)

Java 17+. Einzige Abhängigkeit: Jackson databind. **Nur serverseitig** — der
geheime Schlüssel darf nie in eine nicht vertrauenswürdige Umgebung gelangen.

## Installation

```kotlin
// build.gradle.kts (after the artifact is published to Maven Central)
implementation("io.github.dskripchenko:printable-sdk:1.0.0")
```

Bis dahin — als Quell- oder Jar-Abhängigkeit aus diesem Repository einbinden
(`gradle jar` erzeugt `build/libs/printable-sdk-1.0.0.jar`).

## Schnellstart

```java
PrintableClient client = PrintableClient.builder()
    .baseUrl("https://printable.example.com")
    .token("erp-main-7f3a")
    .secretKey(System.getenv("PRINTABLE_SECRET"))
    .salt(System.getenv("PRINTABLE_SALT"))
    .strict(false) // true, wenn für den Zugang „gesamten Payload-Hash prüfen“ aktiv ist
    .build();

// Ein Dokument rendern
Map<String, Object> doc = client.printFormCreate(Map.of(
    "template", "invoice",
    "variables", Map.of("company", Map.of("name", "ACME Ltd")),
    "format", "link"));
System.out.println(doc.get("link"));

// Stapel mit Archiv und E-Mail-Versand
Map<String, Object> batch = client.printFormBatch(Map.of(
    "template", "certificate",
    "items", List.of(
        Map.of("variables", Map.of("name", "Alice")),
        Map.of("variables", Map.of("name", "Bob"))),
    "archive", true,
    "deliver", Map.of("email", "hr@example.com")));
Map<String, Object> status = client.printFormBatchGet((String) batch.get("uuid"));
```

## API-Oberfläche

`templateList` · `templateContract` · `templateExport` · `templateImport` ·
`templateImportDocx` · `templateImportXlsx` · `printFormCreate` · `printFormGet` ·
`printFormRules` · `printFormSignatureRequest` · `printFormSignatureComplete` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)`

Jede Methode gibt die dekodierte `payload`-Map aus dem Antwort-Umschlag zurück.
Fehler (nicht-2xx oder `success: false`) werfen `PrintableException` mit
`getStatus()`, `getErrorKey()` und dem rohen `getPayload()`.

## Webhooks

```java
boolean valid = client.verifyWebhook(
    rawBody, // raw request body string
    request.getHeader("X-Printable-Signature"),
    request.getHeader("X-Printable-Timestamp"));
```

## Integration testen

Injizieren Sie einen eigenen `Transport`, um HTTP zu stubben:

```java
PrintableClient client = PrintableClient.builder()
    /* … */
    .transport((method, url, body) ->
        new PrintableClient.Transport.Response(200, "{\"success\":true,\"payload\":{}}"))
    .build();
```

## Lizenz

MIT
