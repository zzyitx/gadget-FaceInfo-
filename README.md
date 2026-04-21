# gadget

`gadget` 是一个基于 Spring Boot 的人物公开信息聚合服务。系统接收用户上传的人脸图片，先通过 `CompreFace` 执行人脸检测，再按检测结果进入识别、搜索和公开信息聚合流程，并以统一的 HTTP API 结构返回结果。

## 核心能力

- 接收前端上传的人脸图片
- 通过 `CompreFace` 执行本地多人脸检测
- 单脸场景自动继续进入识别与聚合流程
- 多脸场景返回 `selection_required`，供前端展示总览图和候选人脸卡片
- 聚合人物简介、基础信息和公开社交账号
- 对外提供统一的 HTTP API 响应结构

## 接口说明

### 主入口

- `POST /api/face2info`
  - 请求方式：`multipart/form-data`
  - 请求体字段：`image`
  - 用途：上传图片后，系统会先转换为图床 URL，再默认做人脸检测并按检测结果分流

主入口返回语义：

- `status=success` 或 `status=partial`
  - 单脸场景直接返回最终聚合结果
- `status=selection_required`
  - 检测到多张人脸，返回 `selection` 对象
  - `selection` 中包含 `detection_id`、`preview_image` 和 `faces[]`
- `status=failed`
  - 返回统一错误信息

### 选脸继续

- `POST /api/face2info/process-selected`
  - 请求方式：`application/json`
  - 请求体字段：
    - `detection_id`
    - `face_id`
  - 用途：基于前一次检测会话中选中的人脸 crop，继续进入既有识别与聚合流程

### 检测调试接口

- `POST /api/face2info/detect`
  - 请求方式：`multipart/form-data`
  - 请求体字段：`image`
  - 用途：上传图片后，系统会先转换为图床 URL，再仅返回检测会话、带框总览图和检测到的人脸列表，便于独立调试检测链路

## 外部依赖

- `Serper`：用于 Google Lens 反向搜图和 Google 搜索
- `SerpAPI`：当前用于 Yandex 和 Bing 图片搜索
- `Jina Reader`：用于抓取网页正文
- `Kimi`：用于正文篇级总结和最终人物总结
- `GFPGAN` 本地仓库：用于人脸图像高清化（修复老照片/低清人脸）
- `tempfile.org`：用于把上传原图、内部裁剪图等图片转换成可公开访问的临时 URL

检测相关说明：

- 本项目运行时依赖本仓库内 `infra/compreface/compose.yaml` 启动的 `CompreFace`
- 多脸阶段只负责检测与选脸，不做搜索结果图片相似度比对
- 搜索结果图片回流后，会调用 `CompreFace verification` 做同脸去重和相似度打分

相关配置位于 [application.yml](/D:/ideaProject/gadget/src/main/resources/application.yml)。

## 安全配置

所有第三方密钥都必须通过环境变量注入，禁止把真实值写入仓库中的 `application.yml`。

- `SERP_API_KEY`
- `SERPER_API_KEY`
- `JINA_API_KEY`
- `KIMI_API_KEY`
- `SOPHNET_API_KEY`
- `DEEPSEEK_MODEL`

本地调试时，可通过以下方式注入敏感配置：

- IDE 运行配置或系统环境变量
- 被 `.gitignore` 忽略的 `src/main/resources/application-local.yml`

## 配置文件策略

- 运行时默认读取 `src/main/resources/application.yml`
- `application.yml` 仅作为本地真实配置文件，允许保留本地 key，不提交、不推送
- `src/main/resources/application-git.yml` 是仓库中的脱敏配置副本，专门用于 Git 提交
- 每次修改配置时，先更新本地 `application.yml`，再同步更新 `application-git.yml`
- Git 提交只提交 `application-git.yml`，不要为了提交而删除本地 `application.yml` 中的真实 key

### CompreFace 配置

脱敏配置文件中需要保留 `face2info.api.compreface` 结构，至少包含：

- `base-url`
- `connect-timeout-ms`
- `read-timeout-ms`
- `session-ttl-seconds`
- `detection.api-key`
- `detection.path`
- `verification.api-key`
- `verification.path`

示例：

```yaml
face2info:
  api:
    compreface:
      base-url: http://127.0.0.1:8000
      connect-timeout-ms: 5000
      read-timeout-ms: 30000
      session-ttl-seconds: 600
      detection:
        api-key: ${COMPREFACE_DETECTION_API_KEY:}
        path: /api/v1/detection/detect
      verification:
        api-key: ${COMPREFACE_VERIFICATION_API_KEY:}
        path: /api/v1/verification/verify
```

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

## 本地启动

### 启动后端

```bash
mvn spring-boot:run
```

启动后默认访问地址：

- `http://localhost:8080`

### 准备 CompreFace

当前脚本默认连接一个“已经启动好的 CompreFace 服务”，不要求必须从本仓库的 `infra/compreface` 启动。

例如，你可以像现在这样，直接在独立目录里启动：

```bash
cd D:/ideaProject/CompreFace
docker-compose up -d
```

只要 `CompreFace` 管理端最终可通过 `http://127.0.0.1:8000` 访问，下面的初始化脚本就可以直接复用。

### 首次配置 CompreFace

在 `CompreFace` 管理端可访问后，执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/compreface/init-compreface.ps1 -AdminEmail <管理员邮箱> -AdminPassword <管理员密码>
```

脚本会：

- 登录 `CompreFace` 管理端
- 首次缺失时自动创建 `gadget` 应用
- 在缺少 `DETECTION` 模型时自动补建
- 在缺少 `VERIFY` 模型时自动补建
- 输出 `COMPREFACE_DETECTION_API_KEY` 和 `COMPREFACE_VERIFICATION_API_KEY`

脚本支持重复执行：已存在的应用或模型会直接复用，缺失项才会补建。

脚本不会自动改写本地配置文件。执行后，把输出的 key 手动写入本地 `application.yml` 或环境变量。

## 默认页面行为

默认静态页围绕 `POST /api/face2info` 工作：

- 先上传图片，再调用检测接口生成候选人脸
- 单脸时：直接展示人物、图片匹配文章来源和社交账号
- 多脸时：展示带框总览图和候选人脸卡片
- 选中某张脸后：调用 `POST /api/face2info/process-selected` 继续后半段流程
- 搜索结果图片阶段：同一目标脸只保留相似度最高的一张图，其余不同人脸图片继续展示相似度

## 测试

```bash
mvn clean test
```

提交前建议至少运行：

```bash
mvn clean verify
```

## DeepSeek 与 Kimi 摘要分流

- 当前聚合链路采用 `DeepSeek + Kimi` 双模型分流
- 长文和普通网页优先由 `DeepSeek-V3.2-Fast` 处理
- 结构化特征明显的页面可分流给 `Kimi`
- 主题摘要采用 `DeepSeek -> Kimi` 的降级顺序
- 最终人物总结与综合判断采用 `DeepSeek -> Kimi` 的降级顺序
- 两个模型都失败时，接口返回 `大模型提取人物信息失败`
- 需要配置环境变量：
  - `SOPHNET_API_KEY`
  - `DEEPSEEK_MODEL`
  - `KIMI_API_KEY`
  - 如有必要可额外配置 `DEEPSEEK_API_BASE_URL`、`DEEPSEEK_SYSTEM_PROMPT`、`KIMI_API_BASE_URL`、`KIMI_MODEL` 和 `KIMI_SYSTEM_PROMPT`
- 大模型处理成功时，接口会补充：
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
- 页面级模型失败会在服务内部自动做分流回退，不直接中断总流程

## GFPGAN 本地人脸高清化

- 检测流程默认会先尝试调用本地 `GFPGAN` 仓库执行 `inference_gfpgan.py`，再进入 `CompreFace detection`
- 当 GFPGAN 推理超时、脚本不可用或输出缺失时，会自动降级为原图继续检测，不阻断主流程
- 关键配置位于 `face2info.api.face-enhance`
  - `provider=gfpgan`
  - `gfpgan.project-path` 指向本地 GFPGAN 项目目录，默认示例为 `D:/ideaProject/GFPGAN`
  - `gfpgan.python-command` 用于指定 Python 可执行文件；如使用虚拟环境，可改为 `D:/ideaProject/GFPGAN/venv/Scripts/python.exe`
  - `gfpgan.model-version`、`gfpgan.upscale`、`gfpgan.output-extension` 分别对应官方脚本的 `-v`、`-s`、`--ext`
- 按 GFPGAN 官方 README，首次使用前至少需要：
  - 在 GFPGAN 项目目录安装 `basicsr`、`facexlib` 和 `requirements.txt`
  - 执行 `python setup.py develop`
  - 下载权重文件到 `experiments/pretrained_models` 或 `gfpgan/weights`
- 当前默认关闭 `Real-ESRGAN` 背景增强，配置项为 `gfpgan.background-upsampler=none`，这样可以减少本地依赖和启动成本
- 如需保留旧的 Replicate 方案，仍可把 `provider` 切回 `replicate`

## FaceCheck 图片匹配

- 通过 `face2info.api.facecheck` 配置接入 `FaceCheck`
- 需要配置环境变量 `FACECHECK_API_KEY`
- 页面中的图片匹配区域优先展示 `facecheck_matches`
- `FaceCheck` 调用失败时不会阻断现有人物聚合流程，接口可能返回 `partial`

## 协作约定

- 代理或自动化助手在按仓库约定读取相关技能说明和项目实现时，必须同时读取 `AGENTS.md`，并严格遵守其中规则。
