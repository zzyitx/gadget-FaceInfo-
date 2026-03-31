# 2026-03-31 FaceCheck 两段式搜索修正规格

## 背景

当前仓库中的 `FaceCheckClient` 仅调用一次 `POST /api/upload_pic`，并直接把上传响应中的 `output.items` 当作最终匹配结果返回。

根据 FaceCheck 官方调用方式，正确流程应为：

1. 使用 `multipart/form-data` 上传图片到 `/api/upload_pic`
2. 从上传响应中获取 `id_search`
3. 使用 `id_search` 轮询 `POST /api/search`
4. 在搜索响应出现 `output.items` 后结束并解析结果

当前实现的问题在于把“上传阶段响应”误当成“搜索完成响应”，会导致结果时序、字段语义和稳定性都不符合官方协议。

## 目标

- 将 FaceCheck 调用改为官方两段式流程：`upload_pic -> search`
- 保持现有对外接口结构稳定，不把 `id_search`、轮询和第三方协议细节泄漏到 `service` 层
- 搜索超时时以降级方式返回，不阻断主聚合链路
- 保持现有 `facecheck_matches` 输出结构不变

## 非目标

- 不新增独立的对外 FaceCheck 控制器接口
- 不把 FaceCheck 改造成异步任务系统
- 不调整前端消费的 `facecheck_matches` 字段结构
- 不改造除 FaceCheck 之外的识别与聚合流程

## 方案选择

### 方案 A：在 `client` 内部完成两段式流程

由 `FaceCheckClient` 对 `service` 继续暴露“给一张图，返回匹配结果”的抽象，在 `client` 内部处理上传、轮询、超时和远端错误。

优点：

- 符合当前分层约束
- 改动面最小
- 不会把第三方协议细节扩散到业务层
- 能复用现有 `service` 降级逻辑

缺点：

- `client` 内部状态处理会比当前实现复杂一些

### 方案 B：把上传和查询拆成两个公开方法

由 `service` 显式控制 `upload` 与 `search` 的时序。

优点：

- 协议步骤更显式

缺点：

- 破坏 `client` 负责第三方协议封装的边界
- `service` 会感知 `id_search` 和轮询细节
- 后续测试和维护成本更高

### 方案 C：引入异步任务状态机

将 FaceCheck 查询状态持久化或缓存化，再异步轮询。

优点：

- 可扩展到超长轮询场景

缺点：

- 明显超出本次需求
- 需要额外状态存储和更多接口设计

### 最终选择

采用方案 A。

## 架构设计

### 分层职责

- `controller`
  - 不变
  - 继续只接收上传图片并返回统一聚合结果

- `service`
  - 不直接操作 `id_search`
  - 继续只消费 `FaceCheckClient` 的聚合结果
  - 在 FaceCheck 搜索超时时，将其降级为 `warnings`，并把整体状态置为 `partial`

- `client`
  - 负责上传图片、解析 `id_search`、轮询 `/api/search`、识别远端错误、判断超时
  - 负责把搜索结果映射为内部 `FaceCheckMatchCandidate`

## 数据流

### 1. 上传阶段

请求：

- 方法：`POST /api/upload_pic`
- `Content-Type`：`multipart/form-data`
- 表单字段：`images`
- 请求头：`Authorization: <token>`

响应处理：

- 若存在 `error` 且非空，立即抛出 `ApiCallException`
- 若 `id_search` 为空，视为协议异常并抛出 `ApiCallException`
- 否则进入搜索轮询阶段

### 2. 搜索轮询阶段

请求体固定包含：

- `id_search`
- `with_progress = true`
- `status_only = false`
- `demo = <配置值>`

轮询结束条件：

- 若返回存在 `output.items`，结束轮询并映射结果
- 若返回存在 `error` 且非空，立即抛出 `ApiCallException`
- 否则按固定间隔继续轮询，直到达到总超时时间

### 3. 超时降级

若在配置的超时时间内始终未拿到 `output.items``：

- `client` 返回空的匹配结果，并携带“FaceCheck 搜索超时”的降级信号
- `service` 将该信号写入顶层 `warnings`
- 总体响应状态改为 `partial`
- 不阻断既有的人物识别与信息聚合结果

## 数据模型调整

### `FaceCheckClient`

保持“面向业务结果”的接口，不新增对外暴露的分步 API。

### `FaceCheckUploadResponse`

需要只承载上传阶段的最小信息：

- `idSearch`
- 可选的 `message`

不再把它作为最终匹配结果载体。

### FaceCheck 搜索结果内部模型

新增一个仅供 `client/service` 内部使用的搜索结果模型，例如：

- `items`
- `timedOut`

若需要，也可以额外预留：

- `message`
- `progress`

但不强制暴露给 `service`

### `FaceInfoResponse`

对外结构不变：

- `facecheck_matches` 继续返回匹配列表
- 超时时通过 `warnings` 体现，不新增字段

## 配置设计

在 `face2info.api.facecheck` 下新增或补齐以下配置：

- `searchPath`
- `pollIntervalMillis`
- `searchTimeoutMillis`
- `demo`

约束：

- `application.yml` 与 `application-git.yml` 的结构必须同步
- 不在代码中硬编码这些参数

## 错误处理

### 远端显式错误

以下情况直接视为 FaceCheck 调用失败：

- 上传响应中存在 `error/code`
- 搜索响应中存在 `error/code`
- 上传响应缺少有效 `id_search`

处理方式：

- `client` 抛出 `ApiCallException`
- `service` 捕获后维持现有降级策略，不让整个请求直接失败

### 搜索超时

超时不是硬失败，而是可降级问题。

处理方式：

- 不抛出致命异常
- 返回空匹配列表
- 在响应顶层 `warnings` 添加 `FaceCheck 搜索超时`
- 总体状态为 `partial`

## 测试设计

### `client` 层

至少补充以下测试：

1. 上传成功后轮询成功
   - 第一次请求命中 `/api/upload_pic`
   - 后续请求命中 `/api/search`
   - 当搜索响应出现 `output.items` 时返回映射后的匹配结果

2. 搜索响应返回远端错误
   - 当 `/api/search` 返回 `error/code` 时抛出 `ApiCallException`

3. 搜索超时
   - 当多次轮询都没有 `output.items` 时返回空结果并标记超时

### `service` 层

至少补充以下测试：

1. FaceCheck 正常返回时，`facecheck_matches` 被正确写入响应
2. FaceCheck 超时时，响应状态为 `partial`，且 `warnings` 包含 `FaceCheck 搜索超时`

## 兼容性与风险

### 兼容性

- 前端响应字段不变，兼容现有消费方
- `controller` 和主聚合流程接口不变
- 调整集中在 `client` 内部与少量 `service` 降级逻辑

### 风险

- 若 FaceCheck 实际鉴权格式并非纯 token，而是特定前缀格式，需要按真实接口响应再微调
- 轮询耗时会拉长单次请求时间，需要依赖合理的超时配置
- 若远端返回结构存在字段波动，需要在解析时继续保持宽松兼容

## 实施范围

本次实现应修改：

- `client` 接口与实现
- FaceCheck 内部模型
- FaceCheck 配置类与配置文件
- `service` 中的超时降级逻辑
- `client/service` 相关测试

本次实现不应修改：

- 对外控制器接口路径
- 前端字段命名
- 其他第三方客户端逻辑
