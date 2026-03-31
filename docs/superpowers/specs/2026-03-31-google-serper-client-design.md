# Google Serper Client Design

**目标**

将项目中的 Google 搜索与 Google Lens 识图从 `SerpAPI` 切换到 `Serper`，同时保持 `Yandex/Bing` 继续走现有 `SerpApiClient`，避免影响非 Google 识别链路。

**设计**

- 新增 `GoogleSearchClient`，只负责两个能力：
  - `googleSearch(String query)` 调用 `POST https://google.serper.dev/search`
  - `reverseImageSearchByUrl(String imageUrl)` 调用 `POST https://google.serper.dev/lens`
- 保留 `SerpApiClient`，只负责：
  - `reverseImageSearchByUrlYandex(String imageUrl, String tab)`
  - `reverseImageSearchByUrlBing(String imageUrl)`
  - `searchBingImages(String query)`
- `FaceRecognitionServiceImpl` 同时注入 `GoogleSearchClient` 和 `SerpApiClient`
  - Google Lens 走 `GoogleSearchClient`
  - Yandex/Bing 继续走 `SerpApiClient`
- `InformationAggregationServiceImpl` 注入 `GoogleSearchClient`
  - 人物详情聚合的 `googleSearch(...)` 迁移到 `GoogleSearchClient`

**配置**

- 在 `face2info.api.google` 下新增：
  - `search-url`
  - `lens-url`
  - `api-key`
  - `hl`
  - `connect-timeout-ms`
  - `read-timeout-ms`
  - `max-retries`
  - `backoff-initial-ms`
- `application.yml` 与 `application-git.yml` 同步维护结构。
- `face2info.api.serp` 的 `base-url`、`api-key` 等配置继续服务于 `Yandex/Bing`。

**兼容策略**

- 新 client 继续返回 `SerpApiResponse`，只把它当作原始 JSON 包装对象使用，不在本次变更中重命名。
- 如 `Serper` 返回字段与现有解析链路存在差异，在 `GoogleSearchClientImpl` 内做最小字段兼容映射，避免把第三方差异泄漏到 `service`。

**错误处理**

- 沿用 `RetryUtils` 执行重试。
- 沿用统一异常体系，配置缺失时抛业务异常，不将第三方错误原文直接暴露给前端。
- 日志保留必要定位信息，但不打印 API Key。

**测试**

- 新增 `GoogleSearchClientImplTest`
  - 覆盖 search POST、lens POST、header、body、query 规范化
- 调整 `FaceRecognitionServiceImplTest`
  - Google Lens mock 切到 `GoogleSearchClient`
  - Yandex/Bing mock 保持在 `SerpApiClient`
- 调整 `InformationAggregationServiceImplTest`
  - `googleSearch(...)` mock 切到 `GoogleSearchClient`
- 保留 `SerpApiClientImplTest`，只验证 Yandex/Bing 行为未回归
