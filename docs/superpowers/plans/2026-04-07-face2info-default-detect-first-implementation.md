# Face2Info 主入口默认先检测 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `POST /api/face2info` 默认先执行多人脸检测，再按 `0/1/多脸` 三种结果分流，单脸自动继续聚合，多脸返回 `selection_required` 供前端选脸。

**Architecture:** 保持 `FaceInfoResponse` 作为主入口统一响应，在 service 层新增检测分流，在响应模型中补一个 `selection` 工作流对象。检测能力继续由仓库内 `face-detector/` sidecar 提供，只参考 `CompreFace` 的检测思路，不直接调用 `CompreFace` 项目。

**Tech Stack:** Java 17、Spring Boot 3.3.5、JUnit 5、MockMvc、Python sidecar、FastAPI

---

## 文件结构与职责

- 修改 `src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
  扩展 `selection` 字段并允许 `selection_required` 状态。
- 新增 `src/main/java/com/example/face2info/entity/response/FaceSelectionPayload.java`
  承载 `detection_id`、`preview_image`、`faces[]`。
- 修改 `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
  主入口先检测再分流，复用现有聚合流程。
- 修改 `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
  覆盖 `0/1/多脸` 三种主入口路径。
- 修改 `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
  覆盖 `/api/face2info` 的 `selection_required` 响应。
- 修改 `src/main/resources/static/index.html`
  支持主入口多脸返回后的选脸交互。
- 修改 `src/main/resources/application-git.yml`
  同步 `face2info.api.face-detection` 配置结构。
- 修改 `README.md`
  说明主入口默认先检测与 sidecar 启动方式。

### Task 1: 锁定主入口 service 分流行为

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`

- [ ] **Step 1: 写失败测试，覆盖主入口多脸返回 `selection_required`**

```java
@Test
void shouldReturnSelectionRequiredWhenMultipleFacesDetected() {
    DetectionSession session = new DetectionSession()
            .setDetectionId("det-1")
            .setPreviewImage("data:image/png;base64,preview")
            .setFaces(List.of(buildDetectedFace("face-1"), buildDetectedFace("face-2")));
    when(faceDetectionService.detect(image)).thenReturn(session);

    FaceInfoResponse response = service.process(image);

    assertThat(response.getStatus()).isEqualTo("selection_required");
    assertThat(response.getSelection()).isNotNull();
    verify(faceRecognitionService, never()).recognize(any());
    verify(informationAggregationService, never()).aggregate(any());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: FAIL，提示 `getSelection()`、`selection_required` 分支或主入口行为尚未实现。

- [ ] **Step 3: 再写失败测试，覆盖单脸自动继续聚合**

```java
@Test
void shouldProcessRecognizedImageWhenExactlyOneFaceDetected() {
    DetectionSession session = new DetectionSession()
            .setDetectionId("det-1")
            .setFaces(List.of(buildDetectedFace("face-1")));
    when(faceDetectionService.detect(image)).thenReturn(session);
    when(faceRecognitionService.recognize(any())).thenReturn(evidence);
    when(informationAggregationService.aggregate(evidence)).thenReturn(aggregationResult);

    FaceInfoResponse response = service.process(image);

    assertThat(response.getStatus()).isEqualTo("success");
    verify(faceRecognitionService).recognize(any());
    verify(informationAggregationService).aggregate(evidence);
}
```

- [ ] **Step 4: 再写失败测试，覆盖 `0` 张脸失败**

```java
@Test
void shouldFailWhenNoFaceDetected() {
    DetectionSession session = new DetectionSession()
            .setDetectionId("det-1")
            .setFaces(List.of());
    when(faceDetectionService.detect(image)).thenReturn(session);

    FaceInfoResponse response = service.process(image);

    assertThat(response.getStatus()).isEqualTo("failed");
    assertThat(response.getError()).contains("未检测到人脸");
    verify(faceRecognitionService, never()).recognize(any());
}
```

- [ ] **Step 5: 用最小实现让 service 测试通过**

```java
@Override
public FaceInfoResponse process(MultipartFile image) {
    imageUtils.validateImage(image);
    DetectionSession session = faceDetectionService.detect(image);
    int faceCount = session.getFaces() == null ? 0 : session.getFaces().size();
    if (faceCount == 0) {
        return new FaceInfoResponse()
                .setStatus("failed")
                .setError("未检测到人脸，请更换更清晰的人脸图片。");
    }
    if (faceCount > 1) {
        return buildSelectionRequiredResponse(session);
    }
    SelectedFaceCrop crop = session.getFaces().get(0).getSelectedFaceCrop();
    MultipartFile selectedFace = new InMemoryMultipartFile(crop.getFilename(), crop.getContentType(), crop.getBytes());
    return processRecognizedImage(selectedFace);
}
```

- [ ] **Step 6: 运行 service 测试确认通过**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 7: 提交这一小步**

```bash
git add src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java
git commit -m "test(service): 补充主入口检测分流测试并接入默认先检测"
```

### Task 2: 扩展主入口响应模型

**Files:**
- Create: `src/main/java/com/example/face2info/entity/response/FaceSelectionPayload.java`
- Modify: `src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`

- [ ] **Step 1: 写失败测试，断言 `FaceInfoResponse` 可承载 selection**

```java
@Test
void shouldExposeSelectionPayload() {
    FaceSelectionPayload selection = new FaceSelectionPayload().setDetectionId("det-1");
    FaceInfoResponse response = new FaceInfoResponse().setSelection(selection);

    assertThat(response.getSelection()).isNotNull();
    assertThat(response.getSelection().getDetectionId()).isEqualTo("det-1");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: FAIL，提示 `FaceSelectionPayload` 或 `setSelection/getSelection` 不存在。

- [ ] **Step 3: 写最小响应模型**

```java
public class FaceSelectionPayload {
    @JsonProperty("detection_id")
    private String detectionId;
    @JsonProperty("preview_image")
    private String previewImage;
    private List<DetectedFaceResponse> faces = new ArrayList<>();
}
```

```java
public class FaceInfoResponse {
    private String status;
    private String error;
    private List<String> warnings = new ArrayList<>();
    private PersonInfo person;
    @JsonProperty("image_matches")
    private List<ImageMatch> imageMatches = new ArrayList<>();
    private List<NewsArticle> news = new ArrayList<>();
    private FaceSelectionPayload selection;
}
```

- [ ] **Step 4: 运行相关测试确认通过**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add src/main/java/com/example/face2info/entity/response/FaceSelectionPayload.java src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java
git commit -m "feat(response): 扩展主入口选脸响应模型"
```

### Task 3: 把多脸检测结果映射进主入口响应

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`

- [ ] **Step 1: 写失败测试，断言 selection 字段内容完整**

```java
assertThat(response.getSelection().getDetectionId()).isEqualTo("det-1");
assertThat(response.getSelection().getPreviewImage()).isEqualTo("data:image/png;base64,preview");
assertThat(response.getSelection().getFaces()).hasSize(2);
assertThat(response.getSelection().getFaces().get(0).getFaceId()).isEqualTo("face-1");
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: FAIL，提示 `buildSelectionRequiredResponse` 未正确映射字段。

- [ ] **Step 3: 在 service 中补最小组装方法**

```java
private FaceInfoResponse buildSelectionRequiredResponse(DetectionSession session) {
    FaceSelectionPayload selection = new FaceSelectionPayload()
            .setDetectionId(session.getDetectionId())
            .setPreviewImage(session.getPreviewImage());
    if (session.getFaces() != null) {
        for (DetectedFace detectedFace : session.getFaces()) {
            selection.getFaces().add(new DetectedFaceResponse()
                    .setFaceId(detectedFace.getFaceId())
                    .setConfidence(detectedFace.getConfidence())
                    .setBbox(detectedFace.getFaceBoundingBox())
                    .setCropPreview(toDataUrl(detectedFace.getSelectedFaceCrop())));
        }
    }
    return new FaceInfoResponse()
            .setStatus("selection_required")
            .setSelection(selection);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java
git commit -m "feat(service): 返回多脸待选择主入口响应"
```

### Task 4: 锁定 controller 层主入口返回结构

**Files:**
- Modify: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`

- [ ] **Step 1: 写失败测试，覆盖 `/api/face2info` 的 `selection_required` JSON**

```java
@Test
void shouldReturnSelectionRequiredPayloadForMainEndpoint() throws Exception {
    face2InfoService.setProcessResponse(new FaceInfoResponse()
            .setStatus("selection_required")
            .setSelection(new FaceSelectionPayload()
                    .setDetectionId("det-1")
                    .setPreviewImage("data:image/png;base64,preview")
                    .setFaces(List.of(new DetectedFaceResponse().setFaceId("face-1")))));

    MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1});
    mockMvc.perform(multipart("/api/face2info").file(image))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("selection_required"))
            .andExpect(jsonPath("$.selection.detection_id").value("det-1"))
            .andExpect(jsonPath("$.selection.faces[0].face_id").value("face-1"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: FAIL，提示响应序列化中缺少 `selection` 字段或命名不匹配。

- [ ] **Step 3: 修正 JSON 命名和 getter/setter，保证 controller 测试通过**

```java
@JsonProperty("detection_id")
public String getDetectionId() { ... }
```

```java
@JsonProperty("preview_image")
public String getPreviewImage() { ... }
```

- [ ] **Step 4: 运行 controller 测试确认通过**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java src/main/java/com/example/face2info/entity/response/FaceSelectionPayload.java
git commit -m "test(controller): 锁定主入口多脸待选择响应结构"
```

### Task 5: 修改默认页面支持多脸选脸

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: 在页面中增加选脸容器和状态区占位**

```html
<section class="card" id="selectionCard" hidden>
  <div class="section-header compact">
    <div class="section-title">
      <span class="eyebrow">人脸选择</span>
      <h2>请选择要继续分析的人脸</h2>
    </div>
    <span class="pill" id="selectionStatus">等待选择</span>
  </div>
  <div id="selectionPreview"></div>
  <div class="match-list" id="selectionFaces"></div>
</section>
```

- [ ] **Step 2: 修改主提交流程，优先处理 `selection_required`**

```javascript
if (data.status === "selection_required" && data.selection) {
  renderSelection(data.selection);
  renderPendingSelectionState();
  return;
}
```

- [ ] **Step 3: 增加选脸提交逻辑**

```javascript
async function handleSelectionClick(faceId) {
  const response = await fetch("/api/face2info/process-selected", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      detection_id: currentSelection.detection_id,
      face_id: faceId
    })
  });
  const data = await response.json();
  if (!response.ok || data.status === "failed") {
    renderSelectionError(data.error || "选脸后处理失败。");
    return;
  }
  hideSelection();
  renderPerson(data.person, data.status, data.error);
  renderImageMatches(data.image_matches || [], data.status, data.error);
  renderSocialAccounts(data.person && data.person.social_accounts ? data.person.social_accounts : []);
  renderArticleGroups(data.image_matches || [], data.news || []);
}
```

- [ ] **Step 4: 让单脸路径维持现有渲染，不额外弹出选择区**

```javascript
hideSelection();
renderPerson(data.person, data.status, data.error);
```

- [ ] **Step 5: 本地手工验证页面三种情况**

Run:
- `mvn spring-boot:run`
- `cd face-detector && uvicorn app:app --host 127.0.0.1 --port 8091 --reload`

Expected:
- 单脸图片：直接出聚合结果
- 多脸图片：进入选脸卡片
- 无脸图片：显示失败提示

- [ ] **Step 6: 提交这一小步**

```bash
git add src/main/resources/static/index.html
git commit -m "feat(frontend): 支持主入口多脸选脸交互"
```

### Task 6: 同步配置与文档

**Files:**
- Modify: `src/main/resources/application-git.yml`
- Modify: `README.md`

- [ ] **Step 1: 补脱敏配置结构**

```yaml
face2info:
  api:
    face-detection:
      base-url: http://localhost:8091
      detect-path: /detect
      connect-timeout-ms: 3000
      read-timeout-ms: 10000
      session-ttl-seconds: 600
```

- [ ] **Step 2: 更新 README 说明主入口新语义**

```md
- `POST /api/face2info` 默认先执行人脸检测
- 检测到单张人脸时自动继续聚合
- 检测到多张人脸时返回 `selection_required`
- 本地联调需要同时启动 `face-detector`
```

- [ ] **Step 3: 运行文档与配置相关检查**

Run: `mvn -Dtest=FaceInfoControllerTest,Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 4: 提交这一小步**

```bash
git add src/main/resources/application-git.yml README.md
git commit -m "docs(config): 同步默认先检测配置与使用说明"
```

### Task 7: 整体验证

**Files:**
- Verify only

- [ ] **Step 1: 运行定向测试**

Run: `mvn -Dtest=Face2InfoServiceImplTest,FaceInfoControllerTest test`
Expected: PASS

- [ ] **Step 2: 运行完整校验**

Run: `mvn clean verify`
Expected: BUILD SUCCESS

- [ ] **Step 3: 记录手工联调结果**

确认：
- 单脸图片主入口自动继续聚合
- 多脸图片主入口返回选脸
- 选中后能继续聚合
- detector 未启动时返回清晰错误

- [ ] **Step 4: 最终提交**

```bash
git add src/main/java src/main/resources src/test README.md
git commit -m "feat(face2info): 主入口默认先检测并支持多脸选择"
```

## 自查结果

- Spec coverage：已覆盖主入口分流、`selection_required`、页面选脸、配置同步、README 更新和验证步骤。
- Placeholder scan：未使用 `TODO/TBD`，每个任务都给出了文件、命令和目标代码片段。
- Type consistency：统一使用 `selection_required`、`selection`、`detection_id`、`preview_image`、`face_id` 命名，与 spec 保持一致。
