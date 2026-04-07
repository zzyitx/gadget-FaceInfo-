# Face2Info 主入口默认先检测设计

## 目标

将 `POST /api/face2info` 调整为默认先执行人脸检测，再根据检测结果进入后续流程，保证“用户上传图片后立即进行人脸检测”成为系统默认行为。

本次设计需要同时满足以下目标：

- 保持 `POST /api/face2info` 作为对外主入口，不新增新的主入口版本。
- 主入口在单脸场景下自动继续既有人物识别与信息聚合流程。
- 主入口在多脸场景下返回可供前端继续选择的人脸候选信息，而不是误进入识别链路。
- 保留现有 `POST /api/face2info/detect` 和 `POST /api/face2info/process-selected`，供显式两步流、调试与后续扩展复用。
- 尽量复用现有 `FaceDetectionService`、`Face2InfoService`、`FaceInfoResponse` 和页面结果渲染区，避免重复模型与重复逻辑。

## 背景

仓库中已经具备以下能力：

- Java 侧已有 `FaceDetectionClient`、`FaceDetectionService`、检测会话存储和 `/api/face2info/detect`、`/api/face2info/process-selected` 两个接口。
- 仓库内已有本地 Python sidecar `face-detector/`，负责检测、预览与 crop 生成。
- 当前默认页面 [src/main/resources/static/index.html](/D:/ideaProject/gadget/src/main/resources/static/index.html) 仍直接调用 `POST /api/face2info`。
- 当前 `Face2InfoServiceImpl.process()` 直接进入识别与聚合流程，没有先调用检测逻辑。

因此当前真实行为是：

- 检测能力存在；
- 默认页面没有走检测接口；
- 主入口也没有接入检测分流；
- 用户感知为“上传后人脸检测根本没运行”。

本次设计的核心就是把已有检测能力真正接到默认主流程上。

## CompreFace 参考边界

本次实现需要参考 `D:\ideaProject\CompreFace` 中的人脸检测实现思路，但不能把 `CompreFace` 当成运行时依赖接入本项目。

明确约束如下：

- 可以参考 `CompreFace` 的检测链路、bbox 处理方式、检测阈值思路以及“检测先于识别”的流程拆分方式。
- 不直接调用 `CompreFace` 的 HTTP 接口。
- 不把 `CompreFace` 作为额外服务进程加入 `gadget` 运行链路。
- 不复制其整套 Java API、管理后台、数据库或训练相关模块。
- `gadget` 运行时只依赖本仓库内的 `face-detector/` sidecar 和本项目自身的 Java 服务。

也就是说，本次目标是“在 `gadget` 内部重现多人脸检测核心能力”，而不是“把 `CompreFace` 项目接进来”。

## 范围

### 范围内

- 修改 `Face2InfoServiceImpl.process()` 的主流程，先检测后分流。
- 在 `FaceInfoResponse` 上做增量扩展，支持多脸待选择状态。
- 修改默认静态页面，使其围绕主入口的三种状态进行交互。
- 补充主入口分流相关测试。
- 同步 `application-git.yml` 与 `README.md`。

### 范围外

- 替换当前 Python sidecar 为完整 `CompreFace` 服务。
- 直接依赖或调用 `D:\ideaProject\CompreFace` 项目的接口或进程。
- 引入数据库保存检测会话。
- 改造当前人物识别、搜索、聚合的第三方提供方。
- 新增新的 API 主版本路径，如 `/api/face2info/v2`。
- 大规模重构现有前端结构或引入新的前端框架。

## 设计原则

- 主入口行为必须和用户预期一致：上传即检测。
- 检测能力在本项目内闭环实现，只参考 `CompreFace`，不运行或调用 `CompreFace`。
- 聚合主流程与选脸工作流分层清晰，避免把检测态和平铺到聚合结果顶层。
- 多脸不是失败，而是需要下一步用户操作。
- 单脸应尽量无感继续，避免用户多一步操作。
- 保持兼容优先，优先扩展已有模型，而不是新建并行响应结构。

## 主入口分流设计

### 入口

保留现有主入口：

- `POST /api/face2info`

该接口调整为：

1. 接收上传图片。
2. 先执行图片校验。
3. 调用 `FaceDetectionService.detect(image)`。
4. 根据检测结果进入不同分支。

### 分支一：未检测到人脸

当检测结果为 `0` 张人脸时：

- 终止后续识别与聚合流程。
- 返回统一业务失败响应。
- 沿用现有异常体系或失败响应风格。

期望语义：

- `status = "failed"`
- `error` 给出明确可恢复提示，例如“未检测到人脸，请更换更清晰的人脸图片”。

该分支不应继续调用：

- `FaceRecognitionService.recognize()`
- `InformationAggregationService.aggregate()`

### 分支二：检测到单张人脸

当检测结果为 `1` 张人脸时：

- 直接取该人脸对应的 `SelectedFaceCrop`。
- 将 crop 转换为可继续处理的 `MultipartFile` 或等价内部对象。
- 进入现有 `FaceRecognitionService.recognize()`。
- 继续进入现有 `InformationAggregationService.aggregate()`。
- 返回既有 `FaceInfoResponse` 聚合结果。

该分支的用户感知应当是：

- 上传后自动检测；
- 检测成功后自动继续识别与聚合；
- 最终页面直接展示人物结果，无需额外选脸。

### 分支三：检测到多张人脸

当检测结果大于 `1` 张时：

- 不继续执行人物识别与公开信息聚合。
- 将检测结果包装成“待选择”响应返回给前端。
- 前端基于返回的 `detection_id` 和 `faces[]` 展示选脸界面。
- 用户选中后，再调用 `POST /api/face2info/process-selected` 进入既有后半段流程。

该分支的关键约束：

- 多脸不是失败；
- 多脸时主入口不能误进入任意一张脸的识别；
- 多脸时必须保留检测会话，以支持后续选择。

建议状态值：

- `status = "selection_required"`

## 响应模型设计

### 保持 `FaceInfoResponse` 为主入口统一响应

本次不新建新的主入口响应模型，继续使用 `FaceInfoResponse`。

原因：

- 主入口仍然是同一个接口；
- 单脸和多脸只是流程阶段不同；
- 复用已有响应可减少 controller、异常处理和前端解析分叉；
- 兼容性优于额外引入并行结构。

### 新增状态值

`FaceInfoResponse.status` 增加一个新语义：

- `selection_required`

完整状态集合为：

- `success`
- `partial`
- `failed`
- `selection_required`

语义定义：

- `success`：完整成功。
- `partial`：部分成功，但仍返回可用聚合结果。
- `failed`：请求失败，无法继续。
- `selection_required`：检测到多张人脸，需要用户继续选择。

### 新增 `selection` 字段

在 `FaceInfoResponse` 中新增可选字段：

- `selection`

该字段仅在 `status = "selection_required"` 时返回，结构为：

- `detection_id`
- `preview_image`
- `faces`

其中 `faces[]` 单项建议复用现有检测响应中的字段：

- `face_id`
- `bbox`
- `confidence`
- `crop_preview`

推荐结构示例：

```json
{
  "status": "selection_required",
  "error": null,
  "warnings": [],
  "person": null,
  "image_matches": [],
  "news": [],
  "selection": {
    "detection_id": "det-123",
    "preview_image": "data:image/png;base64,...",
    "faces": [
      {
        "face_id": "face-1",
        "bbox": {
          "x": 12,
          "y": 24,
          "width": 100,
          "height": 120
        },
        "confidence": 0.98,
        "crop_preview": "data:image/png;base64,..."
      }
    ]
  }
}
```

### 不采用顶层平铺字段

不建议把以下字段直接平铺到 `FaceInfoResponse` 顶层：

- `detection_id`
- `preview_image`
- `faces`

原因：

- 这些字段属于“选脸工作流状态”，不是“聚合结果”本身；
- 平铺会污染主响应语义；
- 后续维护时更难区分结果态与工作流态；
- 容易让现有前端把多脸响应误当成聚合完成态。

## 接口行为设计

### `POST /api/face2info`

#### 输入

- `multipart/form-data`
- 字段：`image`

#### 输出

按检测结果分三种情况：

- `failed`：无脸或检测失败
- `success` / `partial`：单脸直接完成聚合
- `selection_required`：多脸待选择

### `POST /api/face2info/detect`

继续保留现有能力，不改变其定位：

- 给显式两步流使用；
- 给调试和问题排查使用；
- 给未来需要先独立展示检测结果的页面或客户端使用。

本次不将默认页面切回“先调 detect 再调 process-selected”的旧设计，而是让主入口承担默认先检测职责。

### `POST /api/face2info/process-selected`

继续保留现有行为：

- 输入 `detection_id` 和 `face_id`
- 从检测会话中取出对应 crop
- 进入既有人物识别与信息聚合流程
- 返回 `FaceInfoResponse`

## 服务层设计

### `Face2InfoServiceImpl`

本次改造重点放在 `Face2InfoServiceImpl.process()`。

建议把现有流程拆成更清晰的几个步骤：

1. `validateOriginalImage(image)`
2. `detectFaces(image)`
3. `routeByDetectionResult(...)`
4. `processRecognizedImage(selectedImage)`
5. `buildSelectionRequiredResponse(session)`

这样做的好处：

- 主入口分流逻辑和聚合逻辑职责分开；
- 单脸与多脸路径更容易测试；
- `processSelectedFace()` 可以继续复用 `processRecognizedImage(...)`；
- 后续若要增加“自动选择最大人脸”等策略，也有明确落点。

### `FaceDetectionService`

当前 `FaceDetectionService` 已具备以下职责：

- 调用本地 detector；
- 建立短期检测会话；
- 按 `detection_id` 和 `face_id` 取回 `SelectedFaceCrop`。

本次不扩大其职责边界，只要求：

- `detect(image)` 返回的 `DetectionSession` 能稳定表达 `0/1/多脸`；
- 多脸路径下会话正确存储；
- 单脸路径也可以直接复用其返回的 crop；
- 过期或缺失会话继续通过统一业务异常返回。

### `face-detector/` 实现约束

`face-detector/` 继续作为 `gadget` 仓库内的本地 sidecar 模块存在。

它的职责仅限于：

- 接收上传图片；
- 执行多人脸检测；
- 返回每张脸的 `bbox` 与 `confidence`；
- 生成带框预览图；
- 生成每张脸的 crop 预览。

它不负责：

- 人物识别；
- 搜索与聚合；
- 检测会话持久化；
- 调用 `CompreFace` 项目本身。

实现时可以参考 `CompreFace` 在检测端“先检测出所有人脸，再把单个人脸交给后续流程”的结构，但最终代码必须落在 `gadget/face-detector/` 自身文件中。

### 复用既有聚合流程

`processRecognizedImage(...)` 应继续负责：

- 图片校验；
- `FaceRecognitionService.recognize()`；
- `InformationAggregationService.aggregate()`；
- 聚合结果组装；
- `success` / `partial` / `failed` 的现有语义。

本次不应把检测流程直接混入聚合结果组装细节中。

## 默认页面交互设计

### 提交入口

默认页面仍从用户视角只做一次“上传并开始分析”。

前端统一调用：

- `POST /api/face2info`

不再让默认页面先显式调用 `/api/face2info/detect` 作为入口。

### 页面分流

前端根据主入口返回的 `status` 分三路处理：

- `success` / `partial`
  直接渲染现有人物、图片匹配、社交账号、相关文章区域。

- `selection_required`
  进入选脸模式，显示 `selection.preview_image` 和 `selection.faces[]`。

- `failed`
  按现有错误区渲染。

### 选脸模式

当返回 `selection_required` 时，页面新增一个“人脸选择”展示区，建议放在结果区顶部。

该区域包含：

- 总览预览图；
- 每张人脸的 crop 卡片；
- 置信度或简单标识；
- 提示文案，例如“检测到多张人脸，请选择要继续分析的一张”。

用户点击某张人脸后：

1. 前端调用 `POST /api/face2info/process-selected`；
2. 传入 `detection_id` 与 `face_id`；
3. 页面进入“正在聚合信息”状态；
4. 成功后隐藏或折叠选脸区域，显示最终聚合结果。

### 单脸无感继续

当主入口返回 `success` 或 `partial` 时：

- 页面不额外暴露检测阶段；
- 用户感知仍然是“一次上传后自动完成”。

### 失败恢复

当选脸后聚合失败时：

- 页面应尽量保留选脸区域，不立即清空候选；
- 在选脸区域内或错误区域显示失败提示；
- 允许用户重新选择或重新上传。

## 错误处理设计

### 检测阶段错误

应统一包装为业务可读错误，不暴露 Python sidecar 细节。

重点覆盖：

- 未检测到人脸；
- 图片格式不支持；
- 图片内容损坏；
- detector 服务不可用；
- detector 超时；
- detector 返回结构缺失。

### 选脸阶段错误

继续沿用现有错误模型，覆盖：

- `detection_id` 不存在；
- 检测会话过期；
- `face_id` 不存在；
- crop 缺失或为空。

### 多脸不是错误

这是本次设计的关键约束：

- 多脸必须返回 `selection_required`；
- 不应映射成 `failed`；
- 不应把“需要选脸”当作服务异常。

## 测试设计

### `Face2InfoServiceImpl` 测试

新增或调整以下测试：

- 检测到 `0` 张人脸时，主入口返回失败或抛出业务异常。
- 检测到 `1` 张人脸时，会取该 crop 并继续调用 `FaceRecognitionService`。
- 检测到多张人脸时，返回 `selection_required`。
- 多脸时不会调用 `FaceRecognitionService`。
- 多脸时不会调用 `InformationAggregationService`。
- `processSelectedFace()` 仍可通过 `detection_id + face_id` 正常进入主聚合流程。

### `FaceInfoControllerTest` 测试

为 `POST /api/face2info` 增加以下覆盖：

- 单脸成功路径；
- 多脸返回 `selection_required`；
- `selection.detection_id`、`selection.preview_image`、`selection.faces[0].face_id` 字段完整；
- 检测失败路径。

现有以下测试继续保留：

- `/api/face2info/detect`
- `/api/face2info/process-selected`

### 前端验证策略

当前仓库没有明确的前端测试栈，本次不为了这次改动额外引入新的前端测试框架。

前端部分采用：

- Java 侧接口测试锁定分流语义；
- 本地手工联调验证页面三种状态；
- 必要时记录手工验证步骤。

## 配置与文档设计

### 配置

需要保证 `application-git.yml` 同步包含 `face2info.api.face-detection` 结构。

至少包括：

- `base-url`
- `detect-path`
- `connect-timeout-ms`
- `read-timeout-ms`
- `session-ttl-seconds`

如后续需要补充 detector 阈值、最大图片边长等参数，也应首先落在 `gadget` 自身配置中，而不是复用 `CompreFace` 项目配置。

`application.yml` 本地结构也必须保持一致，但遵守仓库规则，不提交真实本地敏感配置。

### 文档

需要更新：

- `README.md`

说明内容包括：

- 主入口现在默认先做人脸检测；
- 多脸时会返回 `selection_required`；
- 本地运行需要同时启动 `face-detector` sidecar；
- 默认页面支持多脸选择后继续聚合。

## 风险与缓解

### 风险一：主入口响应新增状态后，旧前端无法正确处理

缓解：

- 使用清晰的 `selection_required` 状态而不是模糊错误；
- 默认页面同步升级；
- 返回结构仍以 `FaceInfoResponse` 为基础，降低解析分叉。

### 风险二：多脸时误进入识别链，导致识别对象不确定

缓解：

- 在 service 层明确禁止多脸自动进入聚合；
- 用测试锁住“多脸不调用识别与聚合”的约束。

### 风险三：检测 sidecar 未启动时，主入口整体不可用

缓解：

- 返回明确业务错误，提示检测服务不可用；
- 在 `README.md` 中补充本地启动说明；
- 保留 `/detect` 便于独立排查 detector 问题。

### 风险四：检测会话过期导致多脸选择失败

缓解：

- 保持现有 TTL 策略；
- 返回清晰的 session expired 错误；
- 页面收到此类错误后提示重新上传图片。

## 实施顺序建议

1. 先补 `Face2InfoServiceImpl` 主入口分流测试，确保能覆盖 `0/1/多脸` 三种路径。
2. 扩展 `FaceInfoResponse` 和相关响应模型，支持 `selection_required` 与 `selection`。
3. 改造 `Face2InfoServiceImpl.process()`，接入检测分流。
4. 补 controller 测试，锁住主入口响应结构。
5. 修改默认页面，支持多脸选择流程。
6. 同步 `application-git.yml` 和 `README.md`。
7. 执行 `mvn clean verify`，并做一次本地手工联调。

## 结论

本次设计选择在现有能力之上完成主入口升级，而不是新增第二个主入口或回退成前端显式两步流。

最终效果应当是：

- 用户上传图片后默认立即执行人脸检测；
- 单脸自动继续识别与聚合；
- 多脸明确进入选脸流程；
- 主入口、显式检测接口和选脸继续接口三者职责清晰；
- 默认页面体验与后端语义一致。
