# Serper Google 字段迁移设计

**目标**

将项目中 Google 搜索与 Google Lens 的结果消费逻辑完全切换到 `SerperAPI` 返回字段，停止继续依赖旧有 `SerpAPI` Google 结果字段；同时明确保留 `SerpApiClient` 在 Bing 和 Yandex 场景中的职责，避免影响非 Google 链路。

**范围**

- 本次只调整 Google 相关链路：
  - `GoogleSearchClientImpl`
  - `FaceRecognitionServiceImpl` 中 `google_lens` 识别分支
  - `InformationAggregationServiceImpl` 中 `googleSearchClient.googleSearch(...)` 聚合分支
- 不调整 Bing / Yandex 的调用入口与字段解析方式：
  - `SerpApiClient`
  - `SerpApiClientImpl`
  - 依赖 Bing / Yandex 的现有测试

**设计原则**

- `client` 层负责吸收第三方返回结构差异，`service` 层只依赖当前已确认稳定的 `SerperAPI` 字段。
- Google 链路不再继续为旧 `SerpAPI` Google 字段保留兼容分支。
- Bing / Yandex 继续沿用现有 `SerpApiClient` 行为，不进行顺手重构。
- 出现部分来源失败时继续保持降级返回，而不是让整个聚合流程失败。

**返回字段策略**

- Google Lens：
  - 主要读取 `organic`
  - 单条结果可用字段包括：
    - `title`
    - `source`
    - `link`
    - `imageUrl`
    - `thumbnailUrl`
- Google Search：
  - 主要读取 `organic`
  - 单条结果至少消费：
    - `title`
    - `source`
    - `link`
    - `snippet`（如返回中存在）
- `knowledgeGraph` 若 `SerperAPI` 返回则继续使用；若未返回，不再依赖旧 Google/SerpAPI 专用字段兜底。

**服务层改动**

- `FaceRecognitionServiceImpl`
  - `extractSeedQueries(...)` 改为从 Google `organic` 中提取候选名。
  - `extractWebEvidence(...)` 的 Google 分支改为从 `organic` 提取网页证据。
  - `extractImageMatches(...)` 改为从 Google Lens 的 `organic` 中提取图片匹配结果。
  - 删除仅服务于旧 Google/SerpAPI 结构的字段读取：
    - `visual_matches`
    - `image_results`
    - `organic_results`（仅限 Google 链路）
- `InformationAggregationServiceImpl`
  - `collectPersonInfo(...)` 改为优先读取 `knowledgeGraph.description`，否则从 `organic[].snippet` 中提取描述。
  - `parseSocialResults(...)` 改为只读取 `organic`。
  - 删除 Google 聚合分支对 `organic_results` 的依赖。

**客户端改动**

- 保留 `GoogleSearchClient` 接口名，避免外层依赖大面积调整。
- `GoogleSearchClientImpl` 继续直接返回 `SerpApiResponse`，但其含义变为“承载 `SerperAPI` 原始 JSON 的统一包装对象”。
- `GoogleSearchClientImpl` 不负责把 `organic` 伪造为旧的 `organic_results`、`visual_matches` 或 `image_results`。
- `GoogleSearchClientImpl` 只负责：
  - 正确发起 `SerperAPI /search` 与 `/lens` 请求
  - 校验 API key
  - 解析响应 JSON
  - 保持统一重试与异常包装

**兼容边界**

- Google 链路不再兼容旧 `SerpAPI` Google 字段名称。
- Bing / Yandex 链路完全不变：
  - 现有接口不改名
  - 现有字段解析不改写
  - 现有测试继续作为回归保护

**错误处理**

- Google `SerperAPI` 访问继续沿用 `RetryUtils`。
- 配置缺失、HTTP 调用失败、返回体无法解析时，继续抛出统一业务异常，不向前端暴露第三方原始错误。
- `FaceRecognitionServiceImpl` 中 Google 识别失败时仍记录 `errors` 并允许主流程降级继续。

**测试策略**

- `GoogleSearchClientImplTest`
  - 使用 `SerperAPI` 的 `/search`、`/lens` 返回样例
  - 验证请求头、请求体和 URL
  - 验证客户端能解析并返回 `organic` 结果
- `FaceRecognitionServiceImplTest`
  - 使用 Google Lens `organic` 样例验证：
    - 候选名提取
    - 网页证据提取
    - 图片匹配提取
  - 保留 Bing / Yandex 相关测试断言不变
- `InformationAggregationServiceImplTest`
  - 使用 Google Search `organic` 样例验证：
    - 人物描述提取
    - 社交账号过滤
  - 不再使用 `organic_results` 作为 Google mock 字段
- `SerpApiClientImplTest`
  - 不改写现有 Bing / Yandex 断言，确保行为未回归

**验收标准**

- Google Search 与 Google Lens 的主流程仅依赖 `SerperAPI` 字段。
- Google 相关测试不再引用旧 `SerpAPI` Google 字段名。
- Bing / Yandex 相关测试保持通过，证明未受影响。
- `mvn clean verify` 可通过。

**非目标**

- 本次不重命名 `SerpApiResponse` 类型。
- 本次不重构 Bing / Yandex 返回结构。
- 本次不扩展新的第三方信息源。
