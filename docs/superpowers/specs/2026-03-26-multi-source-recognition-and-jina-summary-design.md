# 多信源识别与 Jina 正文聚合设计

**日期**: 2026-03-26

**目标**: 在现有人脸图片检索链路上补足多图像信源，并新增“网页正文抓取 + 大模型总结”主流程，使服务端能先从网页正文中解析人物与简介，再补充 Google、NewsAPI 和社交账号信息。

## 1. 范围

本次设计仅覆盖两项能力：

1. 将现有单一 `google_lens` 图片检索扩展为至少三路图像信源：
   - `google_lens`
   - `bing_images`
   - `yandex_images`
2. 将识别后的详情生成改造为：
   - 先基于多信源结果收集网页证据
   - 通过 Jina 抓取最多 20 个网页正文
   - 将正文交给大模型总结，生成人物姓名和最终简介
   - 再使用总结得到的人名调用 Google 搜索、NewsAPI 和社交账号抓取做补充

本次明确不做：

- 不新增候选人物列表对外返回
- 不实现第二阶段的多候选排序能力
- 不绑定具体的大模型厂商实现，只保留抽象接口和可替换占位实现

## 2. 当前问题

当前主流程存在三个结构性缺口：

1. `FaceRecognitionServiceImpl` 仅依赖 `google_lens`，识别结果稳定性不足。
2. 识别层直接返回单一 `name/confidence/source`，没有把多信源网页证据沉淀下来。
3. `InformationAggregationServiceImpl` 只对一个名字做浅层搜索聚合，未抓取正文，也未经过大模型综合判断。

这导致当前服务更像“基于图片检索标题猜人名”，而不是“基于公开网页内容做人物解析与聚合”。

## 3. 目标流程

改造后的请求处理顺序如下：

1. 前端上传图片。
2. 服务端上传图片到临时 URL 服务。
3. 通过 `google_lens`、`bing_images`、`yandex_images` 三路并行收集图像检索结果。
4. 从三路结果中抽取网页链接、标题、来源和图片匹配信息，生成网页证据集合。
5. 对网页证据去重、排序，选取最多 20 个高价值网页 URL。
6. 调用 Jina 抓取这 20 个页面正文。
7. 将网页正文和基础证据交给大模型，生成：
   - 最可能的人物姓名
   - 人物简介
   - 关键事实摘要
   - 引用证据 URL
8. 使用模型确认的人名，继续调用：
   - Google 搜索：补充百科、官网、知识图谱信息
   - NewsAPI：补充相关新闻
   - 社交账号搜索：补充公开社交入口
9. 将模型生成的人物简介作为最终 `person.description` 主值返回；Google、News 和社交数据作为结构化补充字段返回。

## 4. 架构调整

### 4.1 client 层

现有 `SerpApiClient` 继续作为统一搜索入口，但需要扩展：

- `reverseImageSearchByUrl(String imageUrl)`
  - 继续用于 `google_lens`
- `searchBingImages(String query)`
  - 用于 `bing_images`
  - 该接口输入是 `q`，因此只能消费前序识图阶段提炼出的种子短语，不能直接消费上传图片 URL
- `reverseImageSearchByUrlYandex(String imageUrl, String tab)`
  - 用于 `yandex_images`
- `googleSearch(String query)`
  - 继续用于人物补充信息、社交账号搜索

新增两个 client 抽象：

- `JinaReaderClient`
  - 职责：输入多个 URL，返回正文提取结果
- `SummaryGenerationClient`
  - 职责：输入人物证据集合，返回模型整理后的人物解析结果
  - 本轮只定义接口和默认占位实现，不耦合具体模型供应商

### 4.2 service 层

`FaceRecognitionService` 的职责要从“直接输出名字”改为“输出多信源识别证据”。  
这层不再拥有最终姓名决定权，核心职责变成：

- 调用三路图像信源
- 提取网页证据
- 汇总图片匹配结果
- 生成供下游正文抓取使用的 URL 集合

`InformationAggregationService` 的职责要从“对名字做浅层聚合”改为“先通过正文和模型确定人，再补结构化信息”。  
这层新增主编排职责：

- 基于识别证据筛选高价值网页
- 调用 Jina 抓正文
- 调用摘要生成 client 得到最终人名和简介
- 使用最终人名继续执行 Google、NewsAPI、社交聚合
- 统一做降级、清洗、去重

### 4.3 entity.internal

需要新增内部模型，避免污染对外响应：

- `RecognitionEvidence`
  - 表示三路图像识别的总结果
  - 包含：`imageMatches`、`webEvidences`、`errors`
- `WebEvidence`
  - 表示单个网页证据
  - 包含：`url`、`title`、`source`、`sourceEngine`、`snippet`
- `PageContent`
  - 表示 Jina 返回的正文内容
  - 包含：`url`、`title`、`content`、`sourceEngine`
- `ResolvedPersonProfile`
  - 表示大模型的输出
  - 包含：`resolvedName`、`summary`、`keyFacts`、`evidenceUrls`

现有 `RecognitionCandidate` 可以保留并重命名，或直接由新的 `RecognitionEvidence` 替代；实现阶段按最小改动原则选择。

## 5. 数据流设计

### 5.1 多信源识别阶段

输入：图片 URL  
输出：网页证据集合 + 图片匹配结果

规则：

- `google_lens`
  - 继续解析 `knowledge_graph`、`visual_matches`、`image_results`、`organic_results`
- `bing_images`
  - 由于该接口要求 `q`，不直接拿上传图片调用
  - 从 `google_lens` 和 `yandex_images` 的标题结果中清洗出 1 到 3 个种子短语
  - 分别以这些种子短语请求 Bing Images，再汇总网页证据
  - 优先提取图片页落地链接、标题、来源
- `yandex_images`
  - 分别尝试 `about` 和 `similar`，优先使用能返回落地页和标题的结果

这一阶段只做“收集证据”，不把任一信源的标题直接当作最终人物名。
其中 `bing_images` 的职责是扩大线索覆盖面，而不是单独承担最终识别。

### 5.2 正文抓取阶段

从全部网页证据中筛选最多 20 个 URL，筛选规则如下：

- 优先保留出现频率高、跨信源重复出现的 URL 或域名
- 去掉明显无正文价值的页面：
  - 搜索结果跳转页
  - 纯图片页
  - 无正文的缩略图页
  - 重复 URL
- 控制单域名占比，避免 20 个页面都来自同一站点

然后调用 `JinaReaderClient` 读取正文，输出 `PageContent` 列表。

### 5.3 模型总结阶段

将以下内容输入 `SummaryGenerationClient`：

- 上传任务上下文
- 网页正文列表
- 网页标题和来源
- 多信源图像检索来源信息

模型输出应至少包含：

- `resolvedName`
- `summary`
- `keyFacts`
- `evidenceUrls`

其中：

- `resolvedName` 用于后续 Google、NewsAPI、社交账号查询
- `summary` 作为最终 `person.description`

### 5.4 结构化补全阶段

用 `resolvedName` 执行现有补全逻辑：

- `googleSearch(name)`：
  - 补 `wikipedia`
  - 补 `officialWebsite`
  - 作为 description 降级来源
- `newsApiClient.searchNews(name)`：
  - 生成新闻列表
- 社交账号搜索：
  - 继续用 Google 搜索平台关键词获取社交链接

## 6. 响应策略

本次优先保持现有对外接口结构不破坏：

- 继续返回 `person/news/image_matches/status/error`
- `person.name` 使用模型输出的人名
- `person.description` 使用模型输出的人物简介
- `image_matches` 继续用于前端展示图片匹配结果

这意味着：

- 不新增候选人物返回字段
- 大模型输出的 `keyFacts/evidenceUrls` 先只在内部保存，不强制对外暴露

如果后续需要将证据或事实点对外返回，再单独做兼容字段扩展。

## 7. 降级策略

### 7.1 图像信源降级

- 三路信源任意一条失败，只记录错误，不中断主流程
- 只要至少有一条信源产出可用网页证据，就继续 Jina 抓正文

### 7.2 Jina 抓取降级

- 20 个页面不要求全部成功
- 只要抓到部分正文，就继续模型总结

### 7.3 模型总结降级

- 若模型返回了 `summary` 但未明确给出名字：
  - 优先从正文中高频出现的人名线索回退
  - 再退回 Google Lens `knowledge_graph.title`
- 若模型不可用、未配置或失败：
  - 回退到现有 Google 搜索 + `knowledge_graph/snippet` 生成 description
  - 接口 `status` 标记为 `partial`

### 7.4 补充信息降级

- Google、NewsAPI、社交账号任一失败都不影响主简介返回
- 失败信息统一进入 `errors`

## 8. 配置设计

`application.yml` 预计新增两类配置：

### 8.1 Jina 配置

- `face2info.api.jina.base-url`
- `face2info.api.jina.api-key`
- `face2info.api.jina.connect-timeout-ms`
- `face2info.api.jina.read-timeout-ms`
- `face2info.api.jina.max-retries`
- `face2info.api.jina.backoff-initial-ms`

### 8.2 summary 配置

本轮不绑定具体供应商，但需要预留：

- `face2info.api.summary.enabled`
- `face2info.api.summary.provider`
- `face2info.api.summary.base-url`
- `face2info.api.summary.api-key`
- `face2info.api.summary.model`
- `face2info.api.summary.connect-timeout-ms`
- `face2info.api.summary.read-timeout-ms`

默认可将 `summary.enabled` 置为 `false`，由占位实现触发降级逻辑。

## 9. 代码改动边界

预计会触达以下模块：

- `client`
  - 扩展 `SerpApiClient` 及实现
  - 新增 `JinaReaderClient`
  - 新增 `SummaryGenerationClient`
- `config`
  - 扩展 `ApiProperties`
  - 新增 Jina 和 summary 配置属性类
- `entity.internal`
  - 新增识别证据、网页正文、模型输出等内部模型
- `service.impl`
  - 改造 `FaceRecognitionServiceImpl`
  - 改造 `InformationAggregationServiceImpl`
  - 按需要微调 `Face2InfoServiceImpl`
- `test`
  - 补充识别层与聚合层测试

`controller` 和对外响应模型本轮原则上不做破坏性调整。

## 10. 测试设计

本轮测试至少覆盖以下路径：

### 10.1 识别层

- 成功路径：
  - `google_lens + bing_images + yandex_images` 能汇总网页证据
- 失败路径：
  - 单一信源失败时，其它信源仍能继续
- 边界路径：
  - 重复网页证据去重后仍能保留有效 URL

### 10.2 Jina + 模型聚合层

- 成功路径：
  - 20 个页面正文能被送入 `SummaryGenerationClient`，模型返回人名和简介
- 失败路径：
  - `SummaryGenerationClient` 不可用时回退到 Google description
- 边界路径：
  - Jina 只返回部分页面正文时仍能完成聚合

### 10.3 主流程

- 成功路径：
  - 模型返回人名后，继续补全 Google、NewsAPI、社交账号
- 失败路径：
  - NewsAPI 或社交账号失败时整体返回 `partial`
- 边界路径：
  - 无法从多信源拿到足够网页证据时，仍能用原有 Google 路径兜底

## 11. 风险与约束

### 11.1 供应商耦合

Jina 和未来的大模型提供方都属于外部依赖，因此必须坚持：

- 所有 HTTP 调用只走 `client`
- 不在 `service` 中拼接 URL
- 通过配置而不是硬编码管理接口地址和密钥

### 11.2 结果可信度

模型基于网页正文做总结时，可能把同名人物混淆。因此实现时需要在 prompt 中明确约束：

- 只总结与上传图片对应人物相关的信息
- 优先参考跨站点重复出现的信息
- 无法确认时输出保守结论，不捏造事实

### 11.3 性能

一次请求将包含：

- 3 路图像信源
- 最多 20 页正文抓取
- 1 次模型总结
- 额外 Google / News / 社交查询

实现时需要继续利用线程池并发，同时为正文抓取和模型总结设置严格超时，避免接口阻塞过长。

## 12. 结论

本次改造的核心不是“增加更多搜索接口”，而是把当前链路从“看图猜名字”升级为“多信源取证 -> 网页正文抓取 -> 模型确认人物与简介 -> 结构化补全”的聚合流程。

按该设计实现后，服务端将具备更强的识别稳定性和更完整的人物详情生成能力，同时保留现有 API 结构的兼容性，为后续接入新的候选人排序逻辑和其它模型接口保留扩展位。
