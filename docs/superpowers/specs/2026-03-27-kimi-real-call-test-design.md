# Kimi 真实调用测试设计

## 背景

当前项目已经具备 `KimiSummaryGenerationClient` 及其基于 `MockRestServiceServer` 的单元测试，但缺少一个可由开发者手动触发、面向真实 Kimi 接口的测试入口。用户希望通过测试命令直接验证 Kimi 是否能正常调用，并将成功返回的信息打印到控制台。

## 目标

提供一个手动执行的测试方法，满足以下要求：

- 通过 `mvn -Dtest=... test` 单独触发
- 真实调用 Kimi API，而不是 mock
- 成功后在控制台打印关键信息
- 不修改现有生产代码调用链
- 不在常规测试流程中默认执行

## 方案

在现有测试类 [src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java](/D:/ideaProject/gadget/src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java) 中新增一个“手动真实调用测试”方法。

该方法直接复用现有客户端构造方式：

- 手动构造 `ApiProperties`
- 从环境变量读取 `KIMI_API_KEY`
- 使用真实 `RestTemplate`
- 实例化 `KimiSummaryGenerationClient`
- 传入固定的 `fallbackName` 与少量示例正文
- 调用 `summarizePerson(...)`
- 使用 `System.out.println(...)` 在控制台打印 `resolvedName`、`summary`、`tags` 和 `evidenceUrls`

## 约束与边界

- 测试方法默认使用 `@Disabled`，避免普通 `mvn test` 或 CI 误触发真实外部调用
- 测试失败时应给出明确原因，例如缺少 `KIMI_API_KEY`
- 控制台输出只包含模型返回结果，不包含 API Key、请求头或其他敏感信息
- 不新增 `controller`、`service` 或生产环境专用入口
- 不引入 `@SpringBootTest`，避免为单次外部连通性测试拉起完整 Spring 上下文

## 影响文件

- 修改 [src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java](/D:/ideaProject/gadget/src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java)

## 验证方式

开发者手动启用该测试后，使用如下命令单独执行：

```bash
mvn -Dtest=KimiSummaryGenerationClientTest#shouldPrintRealKimiResponse test
```

预期结果：

- Kimi 接口调用成功
- Maven 测试通过
- 控制台可见结构化输出内容

## 风险

- 本测试依赖本地网络环境和真实 Kimi 凭据
- 第三方接口限流、超时或返回格式变化会导致测试失败
- 由于是真实外部调用，该测试不适合作为 CI 稳定测试的一部分
