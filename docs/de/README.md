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
implementation("io.github.dskripchenko:printable-sdk:1.1.0")
```

Bis dahin — als Quell- oder Jar-Abhängigkeit aus diesem Repository einbinden
(`gradle jar` erzeugt `build/libs/printable-sdk-1.1.0.jar`).

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
`printFormSignatureInvite` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)`

Jede Methode gibt die dekodierte `payload`-Map aus dem Antwort-Umschlag zurück.
Fehler (nicht-2xx oder `success: false`) werfen `PrintableException` mit
`getStatus()`, `getErrorKey()` und dem rohen `getPayload()`.

## Signatur und Schutz

`print-form/create` und `print-form/batch` akzeptieren optionale Parameter, die unverändert in den params übergeben werden:

- `preset` — der Code einer Druckvorgabe anstelle von `template` (auch `document-draft/create` akzeptiert ihn);
- `signing` — ein Signaturprofil je Feld, eine sichtbare oder unsichtbare Signatur, der Grund und `recipients` — die Gegenparteien, die das Dokument selbst signieren;
- `protection` — PDF-Schutz: `{"generate": true}` lässt den Dienst das Passwort erzeugen, alternativ ein eigenes im Feld `password`; das optionale `permissions` ist `read_print`, `read_only` oder `forms_sign`.

Die Antwort enthält dann einen Block `protection` (`protected`, `permissions`, `password`, `owner_password`). **Passwörter werden nur einmal geliefert** — in der Antwort, die das Dokument ausstellt; danach kommen sie maskiert (`••••••••`), also sofort speichern. Jedes Element von `signature.slots[]` kann `method` (`cms`, `plugin`, `pdf` oder `sig`) und `signature_link` tragen — die abgetrennte `.sig` des Empfängers.

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
// Das Passwort kommt nur in dieser Antwort — jetzt speichern
Object protection = doc.get("protection");

// Den Link selbst weitergeben oder vom Dienst per E-Mail senden lassen
Map<String, Object> invite = client.printFormSignatureInvite(
    (String) doc.get("uuid"), "buyer@example.com", "buyer", "Buyer LLC");
System.out.println(invite.get("sign_url"));
```

`printFormSignatureInvite()` lädt den Empfänger zur Signatur ein: Es liefert `sign_url` — einen persönlichen Link zur Signaturseite (CryptoPro-Browser-Plug-in, Upload einer abgetrennten `.sig` oder ein im eigenen Feld signiertes PDF) — und `expires_at`. Mit einer E-Mail-Adresse verschickt der Dienst den Link auch per E-Mail (`sent`).

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
