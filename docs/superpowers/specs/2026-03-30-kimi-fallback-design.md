# Kimi 兜底总结设计

## 背景

当前聚合总流程中，`InformationAggregationServiceImpl.resolveProfileFromEvidence(...)` 会先调用 `JinaReaderClient` 读取正文，再把 `PageContent` 交给 `SummaryGenerationClient`。现有实现存在两个问题：

1. Jina 抛异常时直接提前返回，Kimi 总结不会被调用。
2. Jina 返回空列表时也直接跳过总结，没有利用已有网页证据做降级处理。

这与项目“部分失败优先降级返回可用结果”的约束不一致。

## 目标

当 `WebEvidence` 已经收集到候选网页证据时：

- Jina 成功读取正文，继续优先使用正文调用 Kimi。
- Jina 失败或返回空结果时，使用 `WebEvidence` 的 `url/title/snippet/source/sourceEngine` 构造兜底 `PageContent`，继续调用 Kimi。
- 只有在既没有 Jina 正文，也无法从 `WebEvidence` 构造有效页面时，才跳过 Kimi。
- Kimi 失败时仍保持现有降级行为，回退到 SerpAPI 聚合简介，并返回 warning。

## 方案

### 1. 统一总结输入

在 `InformationAggregationServiceImpl` 中引入“候选总结页面”概念：

- 首先选出参与总结的 URL 列表。
- 尝试通过 Jina 读取正文。
- 若 Jina 成功且结果非空，使用 Jina 返回的 `PageContent`。
- 若 Jina 失败或结果为空，则从 `WebEvidence` 构造兜底 `PageContent`。

兜底 `PageContent` 字段约定：

- `url`：`WebEvidence.url`
- `title`：`WebEvidence.title`
- `content`：按顺序拼接可用的 `snippet`、`source`，去除空白后形成可读文本
- `sourceEngine`：优先使用 `WebEvidence.sourceEngine`，没有则标记为 `evidence`

### 2. Warning 策略

- 不为 Jina 失败单独增加 warning。
- 只有 Kimi 调用失败时，继续返回现有 warning：`正文智能处理暂时不可用`。

原因：Jina 失败但 Kimi 已经通过兜底证据成功总结时，请求对外仍然是可用结果，不应误导调用方。

### 3. 测试范围

至少覆盖以下场景：

- Jina 抛异常时，仍使用 `WebEvidence` 构造输入并调用 Kimi。
- Jina 返回空列表时，仍使用 `WebEvidence` 构造输入并调用 Kimi。
- `WebEvidence` 无可用文本时，不调用 Kimi，保持现有回退。

## 影响范围

- `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

## 风险与约束

- 兜底证据的文本质量低于正文，Kimi 输出质量可能下降，但优先满足“不中断总结链路”的稳定性目标。
- 该调整不修改 `SummaryGenerationClient` 接口，避免扩大改动面。
