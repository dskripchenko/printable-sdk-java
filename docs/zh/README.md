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
`printFormSignatureInvite` ·
`printFormBatch` · `printFormBatchGet` ·
`documentDraftCreate` · `documentDraftGet` ·
`call(method, endpoint, params)`

每个方法都返回响应信封中解码后的 `payload` map。
出错时（非 2xx 或 `success: false`）抛出 `PrintableException`，可通过
`getStatus()`、`getErrorKey()` 和原始的 `getPayload()` 取值。

## 签名与保护

`print-form/create` 和 `print-form/batch` 接受以下可选参数，按原样放入 params 传递：

- `preset` —— 打印预设代码，用来代替 `template`（`document-draft/create` 也接受）；
- `signing` —— 每个签名栏的签名配置、可见或不可见签名、签署理由，以及 `recipients` —— 自行签署文档的对方；
- `protection` —— PDF 保护：`{"generate": true}` 由服务生成密码，或在 `password` 字段中给出自己的密码；可选的 `permissions` 为 `read_print`、`read_only` 或 `forms_sign`。

此时响应中带有 `protection` 块（`protected`、`permissions`、`password`、`owner_password`）。**密码只返回一次** —— 在签发文档的那次响应中；之后会被掩码（`••••••••`），请立即保存。`signature.slots[]` 的每一项可能带有 `method`（`cms`、`plugin`、`pdf` 或 `sig`）和 `signature_link` —— 接收方的分离式 `.sig`。

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
// 密码只在这次响应中返回 —— 请现在保存
Object protection = doc.get("protection");

// 自行转交链接，或让服务通过邮件发送
Map<String, Object> invite = client.printFormSignatureInvite(
    (String) doc.get("uuid"), "buyer@example.com", "buyer", "Buyer LLC");
System.out.println(invite.get("sign_url"));
```

`printFormSignatureInvite()` 邀请接收方签署：返回 `sign_url` —— 指向签名页面的个人链接（浏览器中的 CryptoPro 插件、上传分离式 `.sig`，或在其签名栏中签好的 PDF）—— 以及 `expires_at`。提供邮箱时，服务还会通过邮件发送该链接（`sent`）。

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
