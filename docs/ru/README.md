# Printable SDK для Java

Официальный клиент на Java к интеграционному API сервиса генерации документов
[Printable](https://printable.dev-cloud.space): шаблоны, печатные формы, пакетная
печать, черновики документов и проверка вебхуков. Подпись запросов HMAC
(простой и строгий канонический режимы) встроена и побайтово совпадает с
серверной реализацией.

> 🌐 [English](../../README.md) · [Deutsch](../de/README.md) · **Русский** · [中文](../zh/README.md)

Java 17+. Единственная зависимость — Jackson databind. **Только на сервере** —
секретный ключ не должен попадать в недоверенное окружение.

## Установка

```kotlin
// build.gradle.kts (after the artifact is published to Maven Central)
implementation("io.github.dskripchenko:printable-sdk:1.0.0")
```

А пока — подключайте как зависимость исходниками или jar-файлом из этого
репозитория (`gradle jar` кладёт `build/libs/printable-sdk-1.0.0.jar`).

## Быстрый старт

```java
PrintableClient client = PrintableClient.builder()
    .baseUrl("https://printable.example.com")
    .token("erp-main-7f3a")
    .secretKey(System.getenv("PRINTABLE_SECRET"))
    .salt(System.getenv("PRINTABLE_SALT"))
    .strict(false) // true, если у ключа включена «проверка хеша всего тела»
    .build();

// Печать документа
Map<String, Object> doc = client.printFormCreate(Map.of(
    "template", "invoice",
    "variables", Map.of("company", Map.of("name", "ACME Ltd")),
    "format", "link"));
System.out.println(doc.get("link"));

// Пакет с архивом и отправкой на почту
Map<String, Object> batch = client.printFormBatch(Map.of(
    "template", "certificate",
    "items", List.of(
        Map.of("variables", Map.of("name", "Alice")),
        Map.of("variables", Map.of("name", "Bob"))),
    "archive", true,
    "deliver", Map.of("email", "hr@example.com")));
Map<String, Object> status = client.printFormBatchGet((String) batch.get("uuid"));
```

## Поверхность API

`templateList` · `templateContract` · `templateExport` · `templateImport` ·
`templateImportDocx` · `printFormCreate` · `printFormGet` ·
`printFormRules` · `printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)`

Каждый метод возвращает раскодированную карту `payload` из конверта ответа.
Ошибки (не-2xx или `success: false`) бросают `PrintableException` с
`getStatus()`, `getErrorKey()` и исходным `getPayload()`.

## Вебхуки

```java
boolean valid = client.verifyWebhook(
    rawBody, // raw request body string
    request.getHeader("X-Printable-Signature"),
    request.getHeader("X-Printable-Timestamp"));
```

## Проверка интеграции

Подставьте свой `Transport`, чтобы заглушить HTTP:

```java
PrintableClient client = PrintableClient.builder()
    /* … */
    .transport((method, url, body) ->
        new PrintableClient.Transport.Response(200, "{\"success\":true,\"payload\":{}}"))
    .build();
```

## Лицензия

MIT
