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
`templateImportDocx` · `templateImportXlsx` · `printFormCreate` · `printFormGet` ·
`printFormRules` · `printFormSignatureRequest` · `printFormSignatureComplete` ·
`printFormSignatureInvite` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)`

Каждый метод возвращает раскодированную карту `payload` из конверта ответа.
Ошибки (не-2xx или `success: false`) бросают `PrintableException` с
`getStatus()`, `getErrorKey()` и исходным `getPayload()`.

## Подпись и защита

`print-form/create` и `print-form/batch` принимают необязательные параметры — они передаются в params как есть:

- `preset` — код пресета печати вместо `template` (его принимает и `document-draft/create`);
- `signing` — профиль подписи для каждого поля, видимая или невидимая подпись, основание и `recipients` — контрагенты, которые подписывают документ сами;
- `protection` — защита PDF: `{"generate": true}` — пароль сгенерирует сервис, либо свой пароль в поле `password`; необязательное `permissions` — `read_print`, `read_only` или `forms_sign`.

В ответе тогда есть блок `protection` (`protected`, `permissions`, `password`, `owner_password`). **Пароли приходят только один раз** — в ответе, который выпускает документ; дальше они замаскированы (`••••••••`), так что сохраните их сразу. У каждого элемента `signature.slots[]` могут быть `method` (`cms`, `plugin`, `pdf` или `sig`) и `signature_link` — отделённая `.sig` получателя.

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
// Пароль приходит только в этом ответе — сохраните его сейчас
Object protection = doc.get("protection");

// Передайте ссылку сами или дайте сервису отправить её письмом
Map<String, Object> invite = client.printFormSignatureInvite(
    (String) doc.get("uuid"), "buyer@example.com", "buyer", "Buyer LLC");
System.out.println(invite.get("sign_url"));
```

`printFormSignatureInvite()` приглашает получателя подписать документ: возвращает `sign_url` — персональную ссылку на страницу подписи (плагин КриптоПро в браузере, загрузка отделённой `.sig` или PDF, подписанный в своём поле) — и `expires_at`. С адресом почты сервис ещё и отправит ссылку письмом (`sent`).

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
