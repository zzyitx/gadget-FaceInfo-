# gadget

`gadget` 是一个基于 Spring Boot 的人物公开信息聚合服务。系统接收用户上传的人脸图片，先通过反向搜图识别候选人物，再聚合正文证据、相关新闻和公开社交账号，并以统一的 HTTP API 结构返回结果。

## 核心能力

- 接收前端上传的人脸图片
- 基于反向搜图识别人名候选
- 通过 Jina 抓取正文并交给 Kimi 做篇级总结和最终总结
- 聚合人物简介、基础信息、相关新闻和公开社交账号
- 对外提供统一的 HTTP API 响应结构
- 通过配置管理第三方 API、代理和异步线程池

## 接口说明

- `POST /api`
  - 请求方式：`multipart/form-data`
  - 表单字段：`image`
  - 用途：上传图片并聚合人物公开信息

## 外部依赖

- `Serper`：用于 Google Lens 反向搜图和 Google 搜索
- `SerpAPI`：当前用于 Yandex 和 Bing 图片搜索
- `NewsAPI`：用于查询相关新闻
- `Jina Reader`：用于抓取网页正文
- `Kimi`：用于正文篇级总结和最终人物总结
- `tempfile.org`：用于把上传图片转换成可公开访问的临时 URL

相关配置位于 [application.yml](/D:/ideaProject/gadget/src/main/resources/application.yml)。

## 安全配置

所有第三方密钥都必须通过环境变量注入，禁止把真实值写入仓库中的 `application.yml`。

- `SERP_API_KEY`
- `SERPER_API_KEY`
- `NEWS_API_KEY`
- `JINA_API_KEY`
- `KIMI_API_KEY`

本地调试时，可通过以下方式注入敏感配置：

- IDE 运行配置或系统环境变量
- 被 `.gitignore` 忽略的 `src/main/resources/application-local.yml`

## 配置文件策略

- 运行时默认读取 `src/main/resources/application.yml`
- `application.yml` 仅作为本地真实配置文件，允许保留本地 key，不提交、不推送
- `src/main/resources/application-git.yml` 是仓库中的脱敏配置副本，专门用于 Git 提交
- 每次修改配置时，先更新本地 `application.yml`，再同步更新 `application-git.yml`
- Git 提交只提交 `application-git.yml`，不要为了提交而删除本地 `application.yml` 中的真实 key

## 包结构说明

- `com.example.face2info.client`
  - 外部平台客户端模块，负责封装第三方访问逻辑
- `com.example.face2info.config`
  - 应用配置模块，负责线程池、过滤器和 HTTP 客户端等基础设施
- `com.example.face2info.controller`
  - HTTP 接口入口
- `com.example.face2info.entity.internal`
  - 服务内部聚合中间模型
- `com.example.face2info.entity.response`
  - 对外响应模型
- `com.example.face2info.exception`
  - 业务异常与统一异常响应
- `com.example.face2info.service`
  - 识别、聚合和总流程编排逻辑
- `com.example.face2info.util`
  - 图片校验、重试和日志脱敏等通用工具

## 运行方式

```bash
mvn spring-boot:run
```

启动后默认访问地址：

- `http://localhost:8080`

## 测试

```bash
mvn clean test
```

## 协作约定

- 代理或自动化助手在按仓库约定读取相关技能说明和项目实现时，必须同时读取 `AGENTS.md`，并严格遵守其中规则。

## Kimi 正文增强

- 通过 `face2info.api.summary.provider=kimi` 启用 Kimi 正文增强
- 需要配置环境变量 `KIMI_API_KEY`，如有必要可额外配置 `KIMI_API_BASE_URL`、`KIMI_MODEL` 和 `KIMI_SYSTEM_PROMPT`
- Kimi 处理成功时，接口会补充：
  - `person.description`
  - `person.summary`
  - `person.tags`
  - `person.wikipedia`
  - `person.official_website`
  - `person.basic_info`
- `person.basic_info` 包含：
  - `birth_date`
  - `education`
  - `occupations`
  - `biographies`
- Kimi 调用失败时，接口仍返回最小可用聚合结果，并在顶层 `warnings` 中返回 `正文智能处理暂时不可用`

## FaceCheck 图片匹配

- 通过 `face2info.api.facecheck` 配置接入 `FaceCheck`
- 需要配置环境变量 `FACECHECK_API_KEY`
- 页面中的图片匹配区域优先展示 `facecheck_matches`
- `FaceCheck` 调用失败时不会阻断现有人物聚合流程，接口可能返回 `partial`
