# FaceCheck 接入与前端匹配结果改版设计

## 背景

当前项目已经具备图片上传、人物聚合、新闻补充和社交账号补充能力，但前端中的“识别线索 / 图片匹配”区域仍围绕旧的通用搜图结果展示。现需要接入 `facecheck.id` 的 `upload_pic` 接口，并将页面中的图片匹配区域替换为 `facecheck` 返回的相似结果展示，同时保持现有人物简介、新闻和社交账号聚合链路不变。

本次设计遵循项目既有约束：

- 第三方能力必须先经过 `client` 层封装
- 对外响应保持稳定，避免把第三方原始结构直接暴露给前端
- 出现部分失败时优先降级，尽量返回可用结果
- 配置项统一进入 `application.yml` / `application-git.yml`

## 目标

- 新增 `FaceCheck` 第三方客户端封装
- 首版只接入 `POST /api/upload_pic`
- 为后续“上传后轮询查询结果”预留扩展边界
- 顶层响应新增稳定字段 `facecheck_matches`
- 前端取消旧“识别线索”布局中围绕旧 `image_matches` 的展示，改为展示 `facecheck` 返回的相似图片、相似度和来源域名
- 保留现有 `person`、`news`、`social_accounts` 等聚合结果

## 非目标

- 本次不替换原有人物聚合主流程
- 本次不移除旧 `image_matches` 字段，仅前端停止展示它
- 本次不实现 `FaceCheck` 后续轮询接口
- 本次不将 `FaceCheck` 原始响应体直接透传给前端

## 方案对比

### 方案 A：新增独立 `FaceCheck` client 和独立响应模型

这是本次采用的方案。

优点：

- 分层清晰，符合现有项目约束
- 前端与第三方返回结构解耦
- 后续若切换为“上传 + 轮询”，只需调整 `client` 和少量 `service` 逻辑
- 可以保留现有聚合响应结构，不影响人物简介与新闻流程

代价：

- 需要新增内部模型、响应模型、配置项和测试

### 方案 B：复用现有 `image_matches`

不采用。

原因：

- `image_matches` 语义偏向通用搜图线索，不适合承载 `base64` 图片、`score`、来源域名等 `FaceCheck` 专属信息
- 后续扩展会导致字段语义混乱

### 方案 C：前端直接渲染 `FaceCheck` 原始 JSON

不采用。

原因：

- 破坏后端稳定响应边界
- 前端会直接依赖第三方字段命名，后续维护成本高

## 架构设计

### 分层调整

新增或调整以下职责：

- `client`
  - 新增 `FaceCheckClient`
  - 新增对应实现类，负责调用 `https://facecheck.id/api/upload_pic`
  - 负责请求构造、超时控制、异常包装和响应适配
- `entity.internal`
  - 新增 `FaceCheckUploadResponse`
  - 新增 `FaceCheckMatchCandidate`
  - 如有必要，新增 URL 子对象映射模型
- `entity.response`
  - 新增对前端稳定输出模型 `FaceCheckMatch`
- `service`
  - 在 `Face2InfoServiceImpl` 中增加 `FaceCheck` 查询编排
  - 保持人物聚合主链路不变
- `controller`
  - 不直接处理第三方调用细节，只返回扩展后的统一响应

### 数据流

请求流程：

1. 前端上传图片到现有接口
2. `controller` 完成入参校验后调用 `Face2InfoService`
3. `service` 继续执行原有人物聚合流程
4. `service` 同时调用 `FaceCheckClient.uploadPic(...)`
5. `client` 将上传图片转为 `FaceCheck` 所需格式并请求第三方
6. `client` 将第三方响应适配为内部模型
7. `service` 将内部模型映射为顶层响应中的 `facecheck_matches`
8. 前端改为优先展示 `facecheck_matches`

### 首版扩展边界

虽然首版只调用 `upload_pic`，但 `client` 接口设计应保留可扩展性。建议将 `FaceCheckClient` 抽象为“开始搜索”能力，而不是将服务层直接绑定到单一接口细节。后续若增加轮询接口，可在 `client` 内部演进为：

- `startSearch(...)`
- `fetchSearchResult(idSearch)`

首版实现中可仅落一个方法，但命名和内部结构应避免阻塞后续扩展。

## 响应模型设计

### 新增顶层字段

在现有统一响应模型中新增：

- `facecheck_matches`

该字段为数组，默认返回空数组，不返回 `null`。

### `FaceCheckMatch` 字段建议

每个匹配项对前端暴露以下字段：

- `image_data_url`
  - 由后端将第三方返回的 `base64` 包装为 `data:` URL
  - 目标是让前端可直接 `<img src="...">` 渲染
- `similarity_score`
  - 对应第三方 `score`
- `source_host`
  - 从 `item.url.value` 解析域名，例如 `instagram.com`
- `source_url`
  - 原始可点击链接
- `group`
  - 对应第三方 `group`
- `seen`
  - 对应第三方 `seen`
- `index`
  - 对应第三方 `index`

### 字段处理规则

- `base64` 为空时，不生成对应图片卡片，可过滤该条或保留占位，首版推荐过滤
- `url.value` 为空时：
  - `source_host` 返回空字符串或约定默认值
  - `source_url` 返回空字符串
- 域名解析失败时，不抛出异常，降级为原始链接或默认值
- `similarity_score` 直接返回数值，由前端控制展示格式

## 第三方请求设计

### 请求参数

首版请求 `upload_pic` 时包含：

- `images`
  - 使用上传图片内容，按第三方要求传输
- `id_search`
  - 首版由服务端生成请求级唯一值，便于后续轮询扩展
- `reset_prev_images`
  - 通过配置提供默认值

说明：

- 若第三方实际要求 `images` 为 base64 数组，则在 `client` 层完成文件到 base64 的转换
- 若第三方要求 multipart 或其他格式，也只允许在 `client` 层适配，不泄漏到 `service`

### 配置项

在 `application.yml` 和 `application-git.yml` 中新增 `facecheck` 配置段，建议包含：

- `base-url`
- `upload-path`
- `api-key`
- `timeout`
- `reset-prev-images`

同时更新 `README.md` 说明配置用途和接入方式。仓库提交仅提交脱敏版 `application-git.yml` 结构，不提交本地真实密钥。

## 错误处理与降级

### 失败策略

本次接入遵循“局部失败不拖垮整体”的策略：

- 原人物聚合成功，`FaceCheck` 成功：
  - 顶层状态为 `success`
- 原人物聚合成功，`FaceCheck` 返回空结果：
  - 顶层状态保持原有成功状态
  - `facecheck_matches` 返回空数组
- 原人物聚合成功，`FaceCheck` 调用失败：
  - 顶层状态为 `partial` 或沿用现有局部失败策略
  - 错误信息进入统一 warning / error 文案
  - `facecheck_matches` 返回空数组
- 原人物聚合本身失败：
  - 保持现有失败处理逻辑，不因为 `FaceCheck` 成功而伪造成功响应

### 日志要求

- 记录第三方调用耗时、状态码和错误摘要
- 不记录 `api-key`
- 不打印完整原始图片内容
- 不打印完整 `base64` 响应正文

## 前端改版设计

### 展示原则

前端保留当前页面整体结构，但调整结果区域职责：

- 保留人物简介卡片
- 保留社交账号卡片
- 保留新闻卡片
- 保留调试 JSON 卡片
- 取消原“识别线索”中围绕旧 `image_matches` 的摘要展示
- 将“图片匹配”区域改为 `FaceCheck` 专用结果列表

### 图片匹配卡片展示

每张卡片展示：

- 相似图片
- 相似度
- 来源域名
- “打开来源”链接

来源展示规则：

- 卡片正文展示 `source_host`
- 点击链接使用 `source_url`
- 不在卡片正文中直接铺开完整 URL

### 前端状态

- 有结果时：
  - 展示图片卡片列表
- 返回空数组时：
  - 显示“未返回相似结果”
- `FaceCheck` 失败但主聚合成功时：
  - 图片区域显示失败说明
  - 人物简介、新闻、社交账号继续展示

### 与旧字段兼容

- 后端保留 `image_matches`
- 前端停止渲染 `image_matches`
- 调试 JSON 可以继续保留原字段，便于开发期排查

## 测试设计

### `client` 层

至少覆盖：

- 正常请求组装与响应解析
- `base64` 转 `data:` URL
- 来源 URL 域名提取
- 空 URL / 空 base64 / 空 items 分支
- 第三方超时或异常时的异常包装

### `service` 层

至少覆盖：

- `FaceCheck` 成功时响应中包含 `facecheck_matches`
- `FaceCheck` 返回空结果时顶层状态和空数组行为
- `FaceCheck` 失败但主聚合成功时的降级行为

### 接口层或序列化测试

至少覆盖：

- 顶层响应新增字段 `facecheck_matches`
- 旧字段兼容未破坏

### 前端静态页逻辑

至少覆盖：

- 有相似图片时的卡片渲染
- 无结果时的空态渲染
- 局部失败时的说明渲染

## 实施边界

本次实现应限制在以下范围内：

- 新增 `FaceCheck` client、内部模型、响应模型和配置
- 修改服务编排将 `facecheck_matches` 挂到现有响应
- 调整静态页仅展示新的 `FaceCheck` 匹配结果
- 补齐成功、失败、边界测试

不做以下扩展：

- 不新增数据库持久化
- 不修改对外主接口路径
- 不做多张图片批量上传
- 不实现 `FaceCheck` 轮询接口

## 验收标准

- 上传单张图片后，现有人物聚合结果仍可正常返回
- 响应顶层新增 `facecheck_matches`
- 前端图片区域展示 `FaceCheck` 图片、相似度和来源域名
- `FaceCheck` 失败时，系统仍能按降级策略返回可用聚合结果
- 配置结构同步更新到 `application.yml` / `application-git.yml`
- 新增功能具备成功、失败和边界测试
