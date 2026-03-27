# AGENTS.md

## 项目概述

`gadget` 是一个基于 **Spring Boot** 的网站后端项目，核心能力是接收用户上传的人脸图像，调用反向搜图与公开信息检索服务，对人物相关资料进行搜索、清洗与聚合，并以统一的 HTTP API 结构返回结果。

### 项目类型
- Web 项目
- 后端聚合服务
- 面向图片上传与信息检索的 Spring Boot API

### 核心功能
- 接收前端上传的人脸图片
- 通过反向搜图识别候选人物
- 聚合人物简介、相关新闻、公开社交账号等信息
- 输出统一结构的接口响应
- 通过配置管理外部 API、代理与异步线程池

### 技术栈
- 语言：`Java 17`
- 框架：`Spring Boot 3.3.5`
- 构建工具：`Maven`
- API 文档：`springdoc-openapi`
- 校验：`spring-boot-starter-validation`
- 重试：`spring-retry`
- HTTP 客户端：`Apache HttpClient 5`
- 数据库：`MySQL`（项目需求中指定；当前 `pom.xml` 未显式声明 MySQL 驱动，接入时需补充依赖）

### 架构说明
本项目采用标准 Spring Boot 分层结构，核心调用链如下：

1. 前端上传图片到接口
2. `controller` 接收请求并校验
3. `service` 编排识别、搜索、聚合流程
4. `client` 访问外部平台，如 `SerpAPI`、`NewsAPI` 和临时文件 URL 服务
5. `entity.internal` 承载中间结果
6. `entity.response` 统一封装返回给前端

**关键**：本项目不是单一搜索接口，而是“多来源公开信息聚合服务”。新增能力时，优先保证接口稳定、第三方依赖解耦、错误处理一致。

---

## 开发命令

### 环境要求
- `JDK 17`
- `Maven 3.9+`
- `MySQL 8.x`（如启用数据库能力）
- 可用的第三方 API Key

### 安装与依赖解析
```bash
mvn clean install
```

### 本地启动
```bash
mvn spring-boot:run
```

### 指定环境启动
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 打包构建
```bash
mvn clean package
```

### 跳过测试快速构建
```bash
mvn clean package -DskipTests
```

### 运行全部测试
```bash
mvn test
```

### 运行完整校验
```bash
mvn clean verify
```

### 运行单个测试类
```bash
mvn -Dtest=FaceSearchServiceTest test
```

### 查看依赖树
```bash
mvn dependency:tree
```

### 清理构建产物
```bash
mvn clean
```

**关键**：提交代码前至少运行一次：

```bash
mvn clean verify
```

---

## 项目结构

```text
gadget/
├─ .mvn/                              # Maven Wrapper 配置
├─ src/
│  ├─ main/
│  │  ├─ java/com/example/face2info/
│  │  │  ├─ client/                   # 第三方平台客户端封装
│  │  │  ├─ config/                   # Spring 配置、线程池、HTTP 客户端等
│  │  │  ├─ controller/               # HTTP 接口入口
│  │  │  ├─ entity/
│  │  │  │  ├─ internal/              # 服务内部中间模型
│  │  │  │  └─ response/              # 对外响应模型
│  │  │  ├─ exception/                # 业务异常与统一异常处理
│  │  │  ├─ service/                  # 核心聚合与编排逻辑
│  │  │  └─ util/                     # 图片校验、名称提取、重试等工具
│  │  └─ resources/
│  │     ├─ application.yml           # 主配置文件
│  │     └─ static/index.html         # 静态页面入口
│  └─ test/
│     └─ java/                        # 单元测试与集成测试
├─ target/                            # Maven 构建输出目录
├─ pom.xml                            # Maven 项目配置
├─ README.md                          # 项目说明
└─ AGENTS.md                          # 代理执行约定
```

### 核心模块职责
- `client`
  - 封装所有第三方 API 访问逻辑。
  - 禁止在 `service` 或 `controller` 中直接拼接第三方 HTTP 请求。
- `config`
  - 管理外部服务配置、线程池、HTTP 客户端、过滤器等基础设施。
- `controller`
  - 只负责入参接收、校验和响应返回。
  - 不承载复杂业务编排逻辑。
- `entity.internal`
  - 承载内部聚合中间结果，避免对外响应模型污染业务流程。
- `entity.response`
  - 定义统一对外响应结构，优先保持字段稳定。
- `exception`
  - 统一错误模型、异常处理与对外错误响应。
- `service`
  - 项目业务核心，负责识别、搜索、聚合、降级与编排。
- `util`
  - 放置无状态、可复用的通用能力，避免重复逻辑散落。

---

## 代码规范

### 命名约定
- 类名使用 `PascalCase`
  - 例如：`FaceSearchService`、`SerpApiClient`
- 方法名与变量名使用 `camelCase`
  - 例如：`searchByImage()`、`candidateNames`
- 常量使用 `UPPER_SNAKE_CASE`
  - 例如：`DEFAULT_TIMEOUT_SECONDS`
- 包名统一小写，按职责分层
  - 例如：`com.example.face2info.service`

### 分层约束
- `controller` 只处理 HTTP 层职责，不直接访问第三方平台。
- `service` 负责流程编排，不直接返回第三方原始响应。
- `client` 负责第三方调用与结果适配，不承担接口返回组装职责。
- `entity.response` 只用于 API 输出，禁止混入内部状态字段。
- `entity.internal` 只用于服务内部流转，避免直接暴露给前端。

### 编码要求
- 优先使用注解注入、构造器注入，避免字段注入。
- 新增外部调用时必须考虑超时、重试、异常包装和日志追踪。
- 上传图片校验逻辑应集中在入口层或工具层，不要在多个服务中重复实现。
- 保持方法职责单一，聚合流程优先拆分为清晰的小步骤。
- 新增配置项时，统一归档到 `application.yml`，不要把密钥或地址写死在代码中。

### 项目特有约定
- 本项目的核心对象是“聚合结果”，不是第三方 API 的原始返回。
- 所有第三方接入都必须先经过 `client` 层封装。
- 对外响应结构一旦被前端消费，非必要不要随意改字段名或删除字段。
- 聚合流程出现部分失败时，优先降级返回可用结果，而不是整个请求直接失败。

### 日志与异常
- 所有外部调用失败都应带可定位日志，但不要记录敏感配置。
- 统一使用业务异常体系向上抛出，不直接把第三方错误原文返回给前端。
- ⚠️ 不要打印 API Key、代理认证信息、完整用户敏感图片内容。
- ⚠️ 不要将第三方响应体原样暴露给调用方。

---

## 测试策略

### 测试框架
- `spring-boot-starter-test`

### 测试命令
```bash
mvn test
```

```bash
mvn clean verify
```

### 测试重点
- 图片上传参数校验是否正确
- 第三方 API 正常响应、空响应、超时、异常分支
- 聚合逻辑在部分信息源失败时的降级行为
- 统一异常返回结构是否稳定
- 响应模型字段是否完整且兼容前端

### 覆盖要求
- 新增功能必须至少包含：
  - 1 条成功路径测试
  - 1 条失败路径测试
  - 1 条边界条件测试
- 核心 `service` 层必须优先补测试。
- 任何涉及响应结构调整的变更都必须补充接口层测试。

**关键**：修改聚合主流程、外部客户端适配或异常处理时，必须补测试，不接受“只改实现不补验证”。

---

## 接口与配置约定

### 当前核心接口
```http
POST /api
Content-Type: multipart/form-data
Field: image
```

### 外部依赖
当前项目说明中已集成或依赖以下外部能力：
- `SerpAPI`
- `NewsAPI`
- 临时文件 URL 服务

### 配置要求
- 所有第三方地址、密钥、代理、线程池参数统一维护在 `src/main/resources/application.yml`
- 新增配置时同步更新 `README.md`
- 涉及环境差异的配置优先通过 `profile` 或外部环境变量注入

**关键**：配置变更必须同时考虑本地开发、测试环境和生产环境的兼容性。

---

## 提交与变更规则

### 推荐提交信息
```text
feat(service): 新增人物信息聚合逻辑
fix(client): 修复 NewsAPI 超时处理
refactor(controller): 简化上传接口参数校验
test(service): 补充候选人物合并测试
docs: 更新 README 与 AGENTS 说明
```

### 提交前检查
1. 确认修改位于正确分层
2. 确认没有把第三方调用散落到 `service` 之外的错误位置
3. 确认配置项与日志没有泄漏敏感信息
4. 运行：

```bash
mvn clean verify
```

5. 确认测试覆盖核心变更路径

### 严格禁止
- ⚠️ 禁止在 `controller` 中直接写复杂聚合逻辑
- ⚠️ 禁止在 `service` 中直接硬编码第三方接口 URL 与密钥
- ⚠️ 禁止为了临时联调修改对外响应结构而不通知前端
- ⚠️ 禁止跳过测试直接提交核心逻辑变更

---

## 代理执行建议

当代理、自动化助手或协作者在本仓库工作时，请遵循以下顺序：

1. 先阅读 `README.md`、`pom.xml` 和本文件
2. 再定位改动属于 `controller`、`service`、`client`、`config` 还是 `entity`
3. 优先复用现有异常模型、响应模型和工具类
4. 所有第三方能力新增或改造都从 `client` 层进入
5. 修改完成后运行：

```bash
mvn clean verify
```

6. 只有在测试通过、配置完整、日志安全、响应稳定后才允许结束任务

**关键**：本项目最重要的工程目标不是“尽快搜到数据”，而是“稳定、可控地聚合公开信息并输出一致结果”。

## 提交补充要求

- 每次提交 Git 时，都需要按模块拆分提交内容，避免把无关改动混在同一次提交中。
- 每次提交信息都需使用中文说明，并明确写出涉及模块、主要代码改动和核心逻辑调整。

## 文档语言要求

- 新增或修改项目文档时，默认全部使用中文编写。
- 项目文档包括但不限于 `README.md`、`AGENTS.md`、`docs/` 下的设计文档、实施计划、操作说明和变更说明。
- 如确有必要保留英文术语，应优先使用“中文说明 + 英文术语”的写法，避免整段仅使用英文。
- 生成给项目协作者、代理或自动化助手使用的规格文档和计划文档时，除代码、命令、路径、配置键和协议字段外，正文说明统一使用中文。
