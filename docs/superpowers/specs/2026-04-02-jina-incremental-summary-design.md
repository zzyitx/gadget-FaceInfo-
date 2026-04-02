# Jina 逐篇总结再总汇总设计

## 背景

当前 `InformationAggregationServiceImpl.resolveProfileFromEvidence(...)` 的正文增强流程是：

1. 从 `WebEvidence` 里挑选候选 URL
2. 调用 `JinaReaderClient.readPages(...)` 一次性抓取全部正文
3. 将整批 `PageContent` 一次性交给 `SummaryGenerationClient.summarizePerson(...)`
4. 直接生成最终 `ResolvedPersonProfile`

这个流程的问题是：

- AI 处理只能在全部正文抓完后才开始，无法做到逐篇消费
- 单篇正文的提炼结果没有独立结构，后续无法复用或精细降级
- “抓取阶段”和“总结阶段”耦合过紧，不利于控制篇级失败与最终失败边界

本次需求要求将流程改为两阶段：

1. `jina` 每抓到一篇正文，就立刻交给 AI 生成篇级结构化摘要
2. 等全部正文处理结束后，再把篇级摘要集合统一交给 AI 做最终人物汇总

## 目标

- 将当前“整批正文一次性总结”改为“逐篇总结 + 最终总汇总”的两阶段流程
- 篇级输出使用结构化对象，而不是纯文本
- 单篇正文的 AI 总结失败时跳过该篇，不阻断其他篇处理
- 当全部正文处理结束后，如果没有任何一篇成功产出篇级摘要，则最终人物总结整体失败
- 保持第三方 AI 调用仍通过 `client` 层封装，不在 `service` 层直接拼装 HTTP 逻辑
- 为新流程补充成功、失败、边界测试

## 非目标

- 不修改对外响应字段结构
- 不引入新的外部 Provider 切换机制
- 不在本次需求中改变 `JinaReaderClient` 的抓取协议
- 不新增“抓到一篇就向前端流式返回”的接口能力

## 已确认的业务决策

- 篇级 AI 输出采用结构化对象，而不是纯文本
- 单篇 AI 总结失败时，跳过该篇，继续处理其他篇
- 如果最终没有任何成功的篇级摘要，则最终人物总结整体失败，不走温和降级总汇总

## 方案对比

### 方案 A：在现有聚合服务中编排两阶段流程

做法：

- `InformationAggregationServiceImpl` 继续负责流程编排
- `SummaryGenerationClient` 扩展为“篇级总结”和“最终汇总”两个方法
- 新增篇级内部模型，例如 `PageSummary`

优点：

- 改动集中在现有主链路
- 分层清晰，符合当前项目结构
- 便于在服务层显式控制“单篇失败跳过”和“零成功即整体失败”
- 测试可以按服务层和客户端层拆开补充

缺点：

- 需要扩展 `SummaryGenerationClient` 接口
- 需要新增一个内部模型和配套解析逻辑

### 方案 B：把两阶段流水线塞进 `SummaryGenerationClient`

做法：

- 服务层仍只传入 `List<PageContent>`
- 客户端内部自己先逐篇总结，再做最终汇总

优点：

- 服务层表面改动较小

缺点：

- 客户端职责膨胀
- 服务层失去对失败边界和日志语义的控制
- 不利于后续做篇级统计、监控和测试

### 方案 C：新增独立“正文总结编排服务”

做法：

- 在 `service` 层引入新的专用编排服务，负责 `PageContent -> PageSummary -> ResolvedPersonProfile`

优点：

- 长期扩展性最好

缺点：

- 本次需求改动面偏大
- 对现有代码体量来说有过度设计风险

结论：采用方案 A。

## 架构设计

### 1. 新增篇级内部模型

新增内部模型 `PageSummary`，建议字段如下：

- `sourceUrl`
- `title`
- `resolvedNameCandidate`
- `summary`
- `keyFacts`
- `tags`

约束：

- `sourceUrl` 必须来自输入 `PageContent.url`
- `resolvedNameCandidate` 允许为空，用于给最终总汇总提供名称候选
- `keyFacts` 与 `tags` 要在客户端解析阶段完成去重和空值过滤

### 2. 扩展 SummaryGenerationClient

将接口从单一的最终总结能力扩展为两阶段能力：

```java
PageSummary summarizePage(String fallbackName, PageContent page);

ResolvedPersonProfile summarizePersonFromPageSummaries(
        String fallbackName,
        List<PageSummary> pageSummaries
);
```

保留现有 `ResolvedPersonProfile` 作为最终汇总结果模型。

接口职责约束：

- `summarizePage(...)` 只负责单篇正文的结构化提炼
- `summarizePersonFromPageSummaries(...)` 只负责基于篇级摘要集合生成最终人物总结
- `service` 层不处理具体 prompt、HTTP、JSON 解析细节

### 3. 调整 InformationAggregationServiceImpl 主流程

`resolveProfileFromEvidence(...)` 调整后的流程：

1. 从 `WebEvidence` 里选出参与正文增强的 URL
2. 调用 `jinaReaderClient.readPages(urls)` 获取正文列表
3. 遍历 `PageContent` 列表，逐篇调用 `summaryGenerationClient.summarizePage(...)`
4. 收集成功的 `PageSummary`
5. 单篇失败时记录日志并跳过，不向外暴露第三方错误原文
6. 全部遍历完成后：
   - 如果 `PageSummary` 集合为空，则视为最终人物总结整体失败
   - 否则调用 `summaryGenerationClient.summarizePersonFromPageSummaries(...)`
7. 生成最终 `ResolvedPersonProfile`

说明：

- 本次需求只要求“抓到每篇正文后立即处理”，不强制要求 `JinaReaderClient` 提供真正的流式 API
- 如果 `readPages(...)` 仍然一次性返回列表，则服务层可在拿到列表后立即逐篇处理，每篇不再等“全部 AI 处理准备好”后再统一送入模型
- 若后续 `JinaReaderClient` 升级为流式抓取，本设计仍可复用

## 数据流

### 阶段一：篇级正文总结

输入：

- `fallbackName`
- 单篇 `PageContent`

输出：

- 单篇 `PageSummary`

行为要求：

- 每篇正文单独调用 AI
- AI 返回固定 JSON 结构
- 解析失败、空响应、超时、HTTP 异常都归类为单篇失败
- 单篇失败不会终止整批处理

### 阶段二：人物总汇总

输入：

- `fallbackName`
- `List<PageSummary>`

输出：

- `ResolvedPersonProfile`

行为要求：

- 总汇总只消费成功的篇级摘要
- 如果输入集合为空，不调用总汇总 AI，直接判定为整体失败
- `ResolvedPersonProfile.evidenceUrls` 应优先来自成功篇级摘要的 `sourceUrl` 集合

## Prompt 与输出协议

### 篇级总结输出协议

单篇总结要求 AI 只输出 JSON，建议结构：

```json
{
  "resolvedNameCandidate": "string",
  "summary": "string",
  "keyFacts": ["string"],
  "tags": ["string"]
}
```

解析约束：

- `summary` 为空时视为无效篇级摘要
- `resolvedNameCandidate` 可为空
- `keyFacts` 和 `tags` 做去重、去空白

### 最终总汇总输出协议

最终人物总结要求 AI 只输出 JSON，建议结构保持与现有最终模型一致：

```json
{
  "resolvedName": "string",
  "summary": "string",
  "keyFacts": ["string"],
  "tags": ["string"],
  "evidenceUrls": ["string"]
}
```

约束：

- `resolvedName` 为空时回退到 `fallbackName`
- `evidenceUrls` 只能来自输入的篇级摘要来源 URL
- 不允许模型虚构额外证据链接

## 失败处理

### 单篇失败

以下情况都按单篇失败处理：

- 篇级 AI 配置缺失
- 篇级 AI HTTP 调用失败
- 超时
- 空响应
- 非法 JSON
- 解析后缺失必要字段

处理方式：

- 记录日志
- 跳过该篇
- 不把原始第三方错误直接透传到前端

### 总体失败

以下情况按最终人物总结整体失败处理：

- `jina` 抓取后没有任何可处理正文
- 所有篇级 AI 总结全部失败
- 成功篇级摘要集合为空
- 最终总汇总 AI 调用失败

整体失败时的行为：

- `resolveProfileFromEvidence(...)` 返回失败态或空摘要态的 `ResolvedPersonProfile`
- 保持聚合主流程可继续返回基础人物信息
- warning 文案沿用现有正文智能处理不可用提示

备注：

- 用户已明确要求“零成功篇级摘要时整体失败”，因此这里不再退回到“直接用原始正文做一次最终 AI 汇总”

## 日志要求

- 记录正文 URL 数量、成功篇级摘要数量、失败篇级摘要数量
- 区分“单篇失败”和“最终汇总失败”
- 不打印 API Key
- 不打印完整正文
- 不打印完整第三方响应体

建议日志节点：

- `篇级总结开始`
- `篇级总结成功`
- `篇级总结失败`
- `篇级总结阶段完成`
- `人物总汇总开始`
- `人物总汇总成功`
- `人物总汇总失败`

## 测试策略

### 服务层测试

至少覆盖以下场景：

- `jina` 返回多篇正文时，逐篇调用 `summarizePage(...)`，最后调用一次 `summarizePersonFromPageSummaries(...)`
- 某一篇 `summarizePage(...)` 失败时，其他篇仍继续处理
- 至少有一篇成功时，总汇总正常执行
- 所有篇级总结都失败时，不调用总汇总，返回整体失败路径
- 最终总汇总失败时，返回 warning 并走整体失败路径

### 客户端测试

至少覆盖以下场景：

- 单篇总结成功并正确解析结构化字段
- 单篇总结返回非法 JSON 时抛出受控异常
- 单篇总结返回空摘要时识别为失败
- 总汇总成功并正确解析 `ResolvedPersonProfile`
- 总汇总返回非法字段或空响应时抛出受控异常

### 边界测试

- 输入页面为空列表
- 输入页面只有一篇
- 多篇摘要中标签和事实存在重复值
- 模型返回的 `evidenceUrls` 缺失时使用输入来源 URL 补齐

## 影响文件

预计会涉及以下文件：

- `src/main/java/com/example/face2info/entity/internal/PageSummary.java`
- `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
- `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
- `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
- `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

## 风险与约束

- 两阶段 AI 调用会增加总调用次数，延迟和成本会比当前单次总结更高
- 如果篇级 prompt 设计不好，最终总汇总可能会丢失跨文章关联信息
- `NoopSummaryGenerationClient` 需要同步更新接口，否则会破坏现有装配
- 当前工作区已存在与 `KimiSummaryGenerationClient` 相关的未提交改动，实现时必须避免误覆盖

## 实施建议

- 先补服务层失败测试，验证“逐篇调用”和“零成功不汇总”的目标行为
- 再扩展 `SummaryGenerationClient` 接口与 `Noop` 实现
- 然后实现 `KimiSummaryGenerationClient` 的两阶段 prompt 和解析
- 最后收敛日志和 warning 行为，跑完整验证
