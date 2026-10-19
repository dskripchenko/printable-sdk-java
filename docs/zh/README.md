# Printable SDK for Java

面向文档生成服务 [Printable](https://printable.dev-cloud.space) 集成 API 的官方 Java 客户端：
模板、打印表单、批量渲染、文档草稿以及 webhook 校验。HMAC 请求签名
（简单模式与严格规范模式）已内置，并与服务端实现逐字节一致。

> 🌐 [English](../../README.md) · [Deutsch](../de/README.md) · [Русский](../ru/README.md) · **中文**

Java 17+。唯一依赖：Jackson databind。**仅限服务端**——密钥绝不能进入不可信环境。

## 安装

```kotlin
// build.gradle.kts (after the artifact is published to Maven Central)
implementation("io.github.dskripchenko:printable-sdk:1.0.0")
```

在此之前——以源码或 jar 依赖的形式从本仓库引入（`gradle jar` 会生成
`build/libs/printable-sdk-1.0.0.jar`）。

## 快速开始

```java
PrintableClient client = PrintableClient.builder()
    .baseUrl("https://printable.example.com")
    .token("erp-main-7f3a")
    .secretKey(System.getenv("PRINTABLE_SECRET"))
    .salt(System.getenv("PRINTABLE_SALT"))
    .strict(false) // 若该凭据开启了“校验完整载荷哈希”，则填 true
    .build();

// 渲染一份文档
Map<String, Object> doc = client.printFormCreate(Map.of(
    "template", "invoice",
    "variables", Map.of("company", Map.of("name", "ACME Ltd")),
    "format", "link"));
System.out.println(doc.get("link"));

// 批量生成，打包并邮件送达
Map<String, Object> batch = client.printFormBatch(Map.of(
    "template", "certificate",
    "items", List.of(
        Map.of("variables", Map.of("name", "Alice")),
        Map.of("variables", Map.of("name", "Bob"))),
    "archive", true,
    "deliver", Map.of("email", "hr@example.com")));
Map<String, Object> status = client.printFormBatchGet((String) batch.get("uuid"));
```

## API 一览

`templateList` · `templateContract` · `templateExport` · `templateImport` ·
`templateImportDocx` · `templateImportXlsx` · `printFormCreate` · `printFormGet` ·
`printFormRules` · `printFormSignatureRequest` · `printFormSignatureComplete` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)`

每个方法都返回响应信封中解码后的 `payload` map。
出错时（非 2xx 或 `success: false`）抛出 `PrintableException`，可通过
`getStatus()`、`getErrorKey()` 和原始的 `getPayload()` 取值。

## Webhook

```java
boolean valid = client.verifyWebhook(
    rawBody, // raw request body string
    request.getHeader("X-Printable-Signature"),
    request.getHeader("X-Printable-Timestamp"));
```

## 为集成打桩测试

注入自定义的 `Transport` 即可为 HTTP 打桩：

```java
PrintableClient client = PrintableClient.builder()
    /* … */
    .transport((method, url, body) ->
        new PrintableClient.Transport.Response(200, "{\"success\":true,\"payload\":{}}"))
    .build();
```

## 许可证

MIT
