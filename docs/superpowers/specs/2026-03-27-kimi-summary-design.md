# Kimi 摘要 Provider 设计文档

## 背景

当前项目会先通过 `JinaReaderClient` 抽取网页正文，再交给 `SummaryGenerationClient` 处理长文本内容。现有的 `noop` 实现只保留调用链路，不会真正调用大模型。本次需求是在现有扩展点上接入 Kimi，作为可切换的摘要 Provider，用于处理正文并生成面向前端展示的摘要与标签。

## 目标

- 在 `face2info.api.summary.provider=kimi` 时启用 Kimi 实现。
- 基于正文内容生成两类增强结果：
  - 人物摘要
  - 面向前端直接展示的人物标签
- 在 Kimi 未启用或调用失败时，保持现有聚合主流程可用。
- 在后端记录 Kimi 调用失败日志，但不能泄漏密钥、完整正文或第三方原始响应。
- 当 Kimi 增强失败时，在接口响应中增加通用提示信息。

## 非目标

- 不将聚合主流程改造成完全依赖大模型输出。
- 不向前端透出 Kimi 原始响应内容。
- 不因为 Kimi 失败而让整个请求直接失败。
- 本次不增加基于标签的内部排序或推荐逻辑。

## 现有上下文

- `InformationAggregationServiceImpl` 当前已经具备以下能力：
  - 通过 `JinaReaderClient` 拉取候选网页正文
  - 通过 `SummaryGenerationClient` 处理正文
  - 在摘要处理失败时回退到原始聚合链路
- `SummaryGenerationClient` 当前返回内部模型 `ResolvedPersonProfile`
- `NoopSummaryGenerationClient` 用于在不开启摘要能力时保留统一调用方式
- `FaceInfoResponse` 和 `PersonInfo` 是对外响应模型，必须保持兼容性

## 选定方案

在 `client` 层新增一个独立的 `KimiSummaryGenerationClient`，实现现有 `SummaryGenerationClient` 接口，并通过 Spring 条件装配按 `face2info.api.summary.provider` 切换具体实现。`NoopSummaryGenerationClient` 保留为默认降级实现。

该方案可以继续遵守项目“所有第三方能力都从 `client` 层进入”的约束，同时让 `service` 层只负责编排，不感知 Kimi 的具体 HTTP 细节。

## 备选方案对比

### 方案一：新增独立的 `KimiSummaryGenerationClient`，放在 `client.impl` 下

优点：

- 符合当前分层约束
- 易于单独测试
- 便于未来接入更多模型 Provider
- `InformationAggregationServiceImpl` 可以继续保持 Provider 无感知

缺点：

- 需要新增一套配置类和客户端实现

### 方案二：在现有 `NoopSummaryGenerationClient` 中增加 Provider 分支

优点：

- 文件数量更少

缺点：

- 会把空实现和真实 Provider 混在一起
- 未来继续扩展模型时职责会越来越混乱

### 方案三：在 `InformationAggregationServiceImpl` 中直接调用 Kimi

优点：

- 短期改动最少

缺点：

- 违反当前项目的 `client` 分层约束
- 会让测试和降级逻辑更难维护
- 聚合服务会直接耦合到具体厂商

最终采用方案一。

## 架构设计

### 配置层

新增 `face2info.api.kimi` 配置段，包含以下配置项：

- `base-url`
- `api-key`
- `model`
- `connect-timeout-ms`
- `read-timeout-ms`
- `max-retries`
- `backoff-initial-ms`
- `system-prompt`

现有的 `face2info.api.summary.provider` 继续作为 Provider 选择器。该字段在本次改造后支持的值包括：

- `noop`
- `kimi`

### Client 层

新增 `KimiSummaryGenerationClient`，主要职责如下：

1. 接收 `fallbackName` 和 `List<PageContent>`
2. 从正文内容中构造受控提示词
3. 调用 Kimi 接口
4. 将返回结果解析为稳定的内部结构
5. 返回填充后的 `ResolvedPersonProfile`

Client 层必须要求 Kimi 输出结构化 JSON，并对异常格式进行严格校验，不能把第三方原始响应直接向上传递。

### Service 层

`InformationAggregationServiceImpl` 继续只承担编排职责：

- 始终通过 `SummaryGenerationClient` 获取增强后的人物信息
- 成功时把 `summary` 和 `tags` 映射到对外响应
- 失败时继续走原有聚合结果，并附加一条通用 warning

### Response 层

对外响应新增以下字段：

- `PersonInfo.summary: String`
- `PersonInfo.tags: List<String>`
- `FaceInfoResponse.warnings: List<String>`

兼容性要求：

- 现有字段不能删除或重命名
- Kimi 未启用或失败时，仍返回原有的人物、新闻和社交账号数据
- `warnings` 只用于展示通用提示，不返回第三方原始报错

## 数据流

1. 人脸识别阶段产出 `RecognitionEvidence`
2. `InformationAggregationServiceImpl` 从证据中选择候选网页 URL
3. `JinaReaderClient` 读取网页正文
4. 根据 Provider 选择 `SummaryGenerationClient` 实现：
   - `noop`：仅返回回退信息
   - `kimi`：调用 Kimi 并生成增强内容
5. 聚合服务将摘要和标签映射到 `PersonInfo`
6. 如果 Kimi 失败，则保留原有人物聚合结果，并在顶层响应中追加通用 warning

## Prompt 与输出协议

Kimi 必须被要求输出固定结构的 JSON，目标格式如下：

```json
{
  "resolvedName": "string",
  "summary": "string",
  "tags": ["string"],
  "evidenceUrls": ["string"]
}
```

约束规则：

- `summary` 必须是简洁、可读、适合前端直接展示的人物摘要
- `tags` 必须是短标签，适合前端直接渲染
- `tags` 需要去重，并限制在较小数量范围内
- 如果 Kimi 无法优化姓名，可直接回退使用 `fallbackName`
- `evidenceUrls` 只能来自输入页面，不能凭空生成

## 失败处理

Kimi 调用失败时，不能让整个请求失败，必须回退到原始聚合结果。

建议在后端日志中统一归类以下失败类型：

- `CONFIG_MISSING`
- `HTTP_ERROR`
- `TIMEOUT`
- `EMPTY_RESPONSE`
- `INVALID_RESPONSE`

日志要求：

- 尽可能带上请求链路信息
- 必须包含失败类别
- 可以记录轻量上下文，例如回退姓名、URL 数量
- 不得打印 API Key
- 不得打印完整正文
- 不得原样打印第三方完整响应体

面向前端的提示要求：

- 在 `FaceInfoResponse.warnings` 中增加通用提示
- 推荐提示文案：`正文智能处理暂时不可用`
- 响应中不得暴露厂商细节或原始错误信息

## 测试策略

### Client 层测试

需要覆盖以下场景：

- Kimi 成功返回并正确解析出摘要和标签
- HTTP 4xx / 5xx 被转换成受控失败
- 超时被转换成受控失败
- 非法 JSON 或字段缺失被识别为解析失败
- Provider 未启用时不触发 Kimi 调用

### Service 层测试

需要覆盖以下场景：

- Kimi 成功时，`PersonInfo.summary` 与 `PersonInfo.tags` 被正确映射
- Kimi 失败时，原始聚合结果保持可用
- Kimi 失败时，`FaceInfoResponse.warnings` 中增加通用提示
- `noop` Provider 下保持兼容行为，不产生 Kimi 增强结果

### Controller 层测试

需要补充对以下响应字段的断言：

- `person.summary`
- `person.tags`
- `warnings`

同时要验证增强成功和增强降级两类响应路径。

## 预期改动文件

配置相关：

- `src/main/java/com/example/face2info/config/KimiApiProperties.java`
- `src/main/java/com/example/face2info/config/ApiProperties.java`
- `src/main/resources/application.yml`

Client 相关：

- `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
- `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
- `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`

内部模型：

- `src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`
- 如有必要，可增加一个内部错误或结果辅助模型

响应模型：

- `src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
- `src/main/java/com/example/face2info/entity/response/PersonInfo.java`

服务层：

- `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`

测试：

- `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
- `src/test/java/com/example/face2info/client/impl/` 下新增 Kimi 客户端测试

## 本次已确定的设计决策

- Provider 模式为可切换，不直接替换现有摘要逻辑
- 输出内容为“摘要 + 前端展示标签”
- Kimi 失败时继续返回原始聚合结果
- Kimi 失败时既要写后端日志，也要返回通用 warning

## 实现注意事项

- 优先使用条件装配，而不是在 `service` 中写 Provider 分支
- `warnings` 应保持通用，便于未来复用到其他大模型 Provider
- 尽量保留现有摘要调用入口，缩小改动范围
- 实现时必须遵守测试先行，尤其要先覆盖降级路径和失败路径
