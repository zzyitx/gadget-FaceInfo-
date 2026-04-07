# 人脸检测与选脸流程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `gadget` 增加本地 Python 人脸检测能力，支持多人脸框选、用户选脸、裁剪目标脸，并将裁剪结果接入现有识别与信息聚合主流程。

**Architecture:** 本方案采用“Java 主编排 + 本地 Python sidecar 检测服务”的双阶段流程。第一阶段由 Java 调用 Python 完成多人脸检测与会话缓存，第二阶段由 Java 根据用户选择的人脸从会话中取出裁剪图并继续走现有 `FaceRecognitionService` 与 `InformationAggregationService`。

**Tech Stack:** Spring Boot 3.3、JUnit 5、MockMvc、FastAPI、facenet-pytorch MTCNN、Pillow、ConcurrentHashMap 会话缓存

---

## 文件结构规划

### Java 侧新增文件
- `src/main/java/com/example/face2info/client/FaceDetectionClient.java`
  Python 检测服务客户端接口。
- `src/main/java/com/example/face2info/client/impl/FaceDetectionClientImpl.java`
  调用本地 Python HTTP 服务，负责请求组装与响应解析。
- `src/main/java/com/example/face2info/config/FaceDetectionProperties.java`
  检测服务地址、超时、会话 TTL 配置。
- `src/main/java/com/example/face2info/entity/internal/FaceBoundingBox.java`
  人脸框模型。
- `src/main/java/com/example/face2info/entity/internal/DetectedFace.java`
  检测结果中的单张人脸模型。
- `src/main/java/com/example/face2info/entity/internal/DetectionSession.java`
  检测会话模型。
- `src/main/java/com/example/face2info/entity/internal/SelectedFaceCrop.java`
  选中人脸后的裁剪载荷模型。
- `src/main/java/com/example/face2info/entity/response/FaceDetectionResponse.java`
  `/detect` 对外响应。
- `src/main/java/com/example/face2info/entity/response/DetectedFaceResponse.java`
  单张人脸响应项。
- `src/main/java/com/example/face2info/entity/response/FaceSelectionRequest.java`
  `/process-selected` 请求体。
- `src/main/java/com/example/face2info/exception/FaceDetectionException.java`
  检测与选脸业务异常。
- `src/main/java/com/example/face2info/service/FaceDetectionService.java`
  检测编排接口。
- `src/main/java/com/example/face2info/service/impl/FaceDetectionServiceImpl.java`
  检测流程、会话缓存、选脸取图实现。
- `src/main/java/com/example/face2info/util/InMemoryMultipartFile.java`
  将裁剪图字节包装成可继续传给现有识别流程的 `MultipartFile`。

### Java 侧修改文件
- `src/main/java/com/example/face2info/config/ApiProperties.java`
  挂载 `face-detection` 配置。
- `src/main/java/com/example/face2info/controller/FaceInfoController.java`
  增加 `/face2info/detect` 与 `/face2info/process-selected`。
- `src/main/java/com/example/face2info/exception/GlobalExceptionHandler.java`
  增加检测相关异常映射。
- `src/main/resources/application-git.yml`
  增加检测服务配置样例。
- `README.md`
  补充检测服务启动方式与新接口说明。

### Python 侧新增文件
- `face-detector/app.py`
  FastAPI 入口。
- `face-detector/detector.py`
  MTCNN 检测、标框、裁剪实现。
- `face-detector/schemas.py`
  请求与响应模型。
- `face-detector/requirements.txt`
  Python 依赖。
- `face-detector/tests/test_detector.py`
  检测核心测试。
- `face-detector/tests/test_app.py`
  HTTP 接口测试。
- `face-detector/README.md`
  本地运行说明。

### 测试文件
- `src/test/java/com/example/face2info/service/impl/FaceDetectionServiceImplTest.java`
- `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
- `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

---

### Task 1：定义检测接口与对外契约

**Files:**
- Create: `src/main/java/com/example/face2info/entity/internal/FaceBoundingBox.java`
- Create: `src/main/java/com/example/face2info/entity/internal/DetectedFace.java`
- Create: `src/main/java/com/example/face2info/entity/internal/DetectionSession.java`
- Create: `src/main/java/com/example/face2info/entity/internal/SelectedFaceCrop.java`
- Create: `src/main/java/com/example/face2info/entity/response/FaceDetectionResponse.java`
- Create: `src/main/java/com/example/face2info/entity/response/DetectedFaceResponse.java`
- Create: `src/main/java/com/example/face2info/entity/response/FaceSelectionRequest.java`
- Test: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`

- [ ] **Step 1: 先在控制器层写失败测试，约束新接口响应结构**

```java
@Test
void shouldReturnDetectionPayloadForDetectEndpoint() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
    FaceDetectionResponse response = new FaceDetectionResponse()
            .setDetectionId("det-1")
            .setPreviewImage("data:image/jpeg;base64,preview")
            .setFaces(List.of(
                    new DetectedFaceResponse()
                            .setFaceId("face-1")
                            .setConfidence(0.98)
                            .setCropPreview("data:image/jpeg;base64,crop")
            ));

    when(faceDetectionService.detect(image)).thenReturn(response);

    mockMvc.perform(multipart("/api/face2info/detect").file(image))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.detection_id").value("det-1"))
            .andExpect(jsonPath("$.faces[0].face_id").value("face-1"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: FAIL，提示 `faceDetectionService`、`/api/face2info/detect` 或响应模型不存在。

- [ ] **Step 3: 新建内部模型与响应模型最小实现**

```java
public class FaceBoundingBox {
    private int x;
    private int y;
    private int width;
    private int height;
}
```

```java
public class DetectedFaceResponse {
    @JsonProperty("face_id")
    private String faceId;
    private double confidence;
    @JsonProperty("crop_preview")
    private String cropPreview;
    private FaceBoundingBox bbox;
}
```

```java
public class FaceDetectionResponse {
    @JsonProperty("detection_id")
    private String detectionId;
    @JsonProperty("preview_image")
    private String previewImage;
    private List<DetectedFaceResponse> faces = new ArrayList<>();
}
```

- [ ] **Step 4: 重新运行控制器测试，确认编译层问题收敛**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: 仍然 FAIL，但失败点前进到控制器中尚未新增接口或依赖未注入。

- [ ] **Step 5: 提交本任务**

```bash
git add src/main/java/com/example/face2info/entity/internal src/main/java/com/example/face2info/entity/response src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git commit -m "feat: add face detection api models"
```

### Task 2：先用测试定义 Java 检测服务与会话缓存行为

**Files:**
- Create: `src/main/java/com/example/face2info/service/FaceDetectionService.java`
- Create: `src/main/java/com/example/face2info/exception/FaceDetectionException.java`
- Create: `src/test/java/com/example/face2info/service/impl/FaceDetectionServiceImplTest.java`
- Modify: `src/main/java/com/example/face2info/entity/internal/DetectionSession.java`

- [ ] **Step 1: 写失败测试覆盖检测成功、会话过期、非法 face_id**

```java
@Test
void shouldStoreDetectionSessionAndReturnResponse() {
    MockMultipartFile image = new MockMultipartFile("image", "group.jpg", "image/jpeg", new byte[]{1, 2, 3});
    FaceDetectionClient client = mock(FaceDetectionClient.class);
    when(client.detect(image)).thenReturn(new DetectionSession()
            .setDetectionId("det-1")
            .setFaces(List.of(new DetectedFace().setFaceId("face-1"))));

    FaceDetectionServiceImpl service = new FaceDetectionServiceImpl(client, Clock.systemUTC(), Duration.ofMinutes(10));

    FaceDetectionResponse response = service.detect(image);

    assertThat(response.getDetectionId()).isEqualTo("det-1");
}

@Test
void shouldFailWhenSelectedFaceDoesNotExist() {
    FaceDetectionServiceImpl service = preparedServiceWithOneFace();

    assertThatThrownBy(() -> service.getSelectedFaceCrop("det-1", "face-x"))
            .isInstanceOf(FaceDetectionException.class)
            .hasMessageContaining("face_id");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=FaceDetectionServiceImplTest test`
Expected: FAIL，提示 `FaceDetectionClient`、`FaceDetectionServiceImpl` 或 `getSelectedFaceCrop` 不存在。

- [ ] **Step 3: 写最小接口、异常与会话模型**

```java
public interface FaceDetectionService {
    FaceDetectionResponse detect(MultipartFile image);
    SelectedFaceCrop getSelectedFaceCrop(String detectionId, String faceId);
}
```

```java
public class FaceDetectionException extends RuntimeException {
    public FaceDetectionException(String message) {
        super(message);
    }
}
```

```java
public class DetectionSession {
    private String detectionId;
    private Instant expiresAt;
    private List<DetectedFace> faces = new ArrayList<>();
}
```

- [ ] **Step 4: 实现最小可运行的内存会话缓存服务**

```java
@Service
public class FaceDetectionServiceImpl implements FaceDetectionService {
    private final FaceDetectionClient faceDetectionClient;
    private final Map<String, DetectionSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    @Override
    public FaceDetectionResponse detect(MultipartFile image) {
        DetectionSession session = faceDetectionClient.detect(image);
        session.setExpiresAt(clock.instant().plus(ttl));
        sessions.put(session.getDetectionId(), session);
        return mapToResponse(session);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -Dtest=FaceDetectionServiceImplTest test`
Expected: PASS

- [ ] **Step 6: 提交本任务**

```bash
git add src/main/java/com/example/face2info/service src/main/java/com/example/face2info/exception src/test/java/com/example/face2info/service/impl/FaceDetectionServiceImplTest.java
git commit -m "feat: add face detection session service"
```

### Task 3：实现 Java 控制器双阶段接口

**Files:**
- Modify: `src/main/java/com/example/face2info/controller/FaceInfoController.java`
- Modify: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
- Modify: `src/main/java/com/example/face2info/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: 为选脸处理接口写失败测试**

```java
@Test
void shouldProcessSelectedFace() throws Exception {
    FaceInfoResponse response = new FaceInfoResponse().setStatus("success");
    when(face2InfoService.processSelectedFace("det-1", "face-1")).thenReturn(response);

    mockMvc.perform(post("/api/face2info/process-selected")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"detection_id":"det-1","face_id":"face-1"}
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: FAIL，提示 `processSelectedFace` 或新接口不存在。

- [ ] **Step 3: 修改控制器，新增检测与选脸两个接口**

```java
@PostMapping(value = "/face2info/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public FaceDetectionResponse detect(@RequestPart("image") MultipartFile image) {
    return faceDetectionService.detect(image);
}

@PostMapping(value = "/face2info/process-selected", consumes = MediaType.APPLICATION_JSON_VALUE)
public FaceInfoResponse processSelected(@RequestBody @Valid FaceSelectionRequest request) {
    return face2InfoService.processSelectedFace(request.getDetectionId(), request.getFaceId());
}
```

- [ ] **Step 4: 修改统一异常处理，增加检测类异常映射**

```java
@ExceptionHandler(FaceDetectionException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ErrorResponse handleFaceDetectionException(FaceDetectionException ex) {
    return new ErrorResponse("BAD_REQUEST", ex.getMessage());
}
```

- [ ] **Step 5: 运行控制器测试确认通过**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: PASS

- [ ] **Step 6: 提交本任务**

```bash
git add src/main/java/com/example/face2info/controller/FaceInfoController.java src/main/java/com/example/face2info/exception/GlobalExceptionHandler.java src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git commit -m "feat: add detect and select face endpoints"
```

### Task 4：实现 Python 检测 sidecar 与单元测试

**Files:**
- Create: `face-detector/app.py`
- Create: `face-detector/detector.py`
- Create: `face-detector/schemas.py`
- Create: `face-detector/requirements.txt`
- Create: `face-detector/tests/test_detector.py`
- Create: `face-detector/tests/test_app.py`
- Create: `face-detector/README.md`

- [ ] **Step 1: 先写 detector 层失败测试**

```python
def test_detect_faces_returns_multiple_faces(sample_group_image_bytes):
    detector = FaceDetector()
    result = detector.detect(sample_group_image_bytes)
    assert len(result.faces) >= 2
    assert result.preview_image.startswith("data:image/jpeg;base64,")
```

```python
def test_detect_faces_raises_when_no_face(sample_blank_image_bytes):
    detector = FaceDetector()
    with pytest.raises(NoFaceDetectedError):
        detector.detect(sample_blank_image_bytes)
```

- [ ] **Step 2: 运行 Python 测试确认失败**

Run: `pytest face-detector/tests/test_detector.py -q`
Expected: FAIL，提示 `FaceDetector` 不存在。

- [ ] **Step 3: 写最小检测实现**

```python
class FaceDetector:
    def __init__(self) -> None:
        self.mtcnn = MTCNN(keep_all=True)

    def detect(self, image_bytes: bytes) -> DetectionResult:
        image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        boxes, probs = self.mtcnn.detect(image)
        if boxes is None or len(boxes) == 0:
            raise NoFaceDetectedError("no face detected")
        return build_detection_result(image, boxes, probs)
```

- [ ] **Step 4: 再写 FastAPI 接口测试与最小 HTTP 入口**

```python
@app.post("/detect")
def detect(file: UploadFile = File(...)) -> DetectionResponse:
    image_bytes = file.file.read()
    return detector.detect(image_bytes)
```

- [ ] **Step 5: 运行 Python 全部测试确认通过**

Run: `pytest face-detector/tests -q`
Expected: PASS

- [ ] **Step 6: 提交本任务**

```bash
git add face-detector
git commit -m "feat: add python face detector sidecar"
```

### Task 5：实现 Java 检测客户端与配置接入

**Files:**
- Create: `src/main/java/com/example/face2info/client/FaceDetectionClient.java`
- Create: `src/main/java/com/example/face2info/client/impl/FaceDetectionClientImpl.java`
- Create: `src/main/java/com/example/face2info/config/FaceDetectionProperties.java`
- Modify: `src/main/java/com/example/face2info/config/ApiProperties.java`
- Modify: `src/main/resources/application-git.yml`

- [ ] **Step 1: 为客户端写失败测试，约束请求路径与返回解析**

```java
@Test
void shouldCallLocalDetectorAndParseResponse() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("http://localhost:8091/detect"))
            .andRespond(withSuccess("""
                    {"detection_id":"det-1","preview_image":"data:image/jpeg;base64,p","faces":[]}
                    """, MediaType.APPLICATION_JSON));

    FaceDetectionClientImpl client = new FaceDetectionClientImpl(restTemplate, properties, objectMapper);
    DetectionSession session = client.detect(new MockMultipartFile("image", "a.jpg", "image/jpeg", new byte[]{1}));

    assertThat(session.getDetectionId()).isEqualTo("det-1");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=FaceDetectionClientImplTest test`
Expected: FAIL，提示客户端实现不存在。

- [ ] **Step 3: 加入配置类与 `application-git.yml` 配置项**

```java
public class FaceDetectionProperties {
    private String baseUrl = "http://localhost:8091";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 10000;
    private long sessionTtlSeconds = 600;
}
```

```yaml
face2info:
  api:
    face-detection:
      base-url: http://localhost:8091
      connect-timeout-ms: 3000
      read-timeout-ms: 10000
      session-ttl-seconds: 600
```

- [ ] **Step 4: 实现客户端最小调用逻辑**

```java
public DetectionSession detect(MultipartFile image) {
    HttpEntity<MultiValueMap<String, Object>> request = buildMultipartRequest(image);
    ResponseEntity<FaceDetectionResponse> response = restTemplate.postForEntity(
            properties.getBaseUrl() + "/detect", request, FaceDetectionResponse.class);
    return map(response.getBody());
}
```

- [ ] **Step 5: 运行客户端测试确认通过**

Run: `mvn -Dtest=FaceDetectionClientImplTest test`
Expected: PASS

- [ ] **Step 6: 提交本任务**

```bash
git add src/main/java/com/example/face2info/client src/main/java/com/example/face2info/config src/main/resources/application-git.yml
git commit -m "feat: add local face detector client"
```

### Task 6：把选中人脸裁剪图接入现有主流程

**Files:**
- Modify: `src/main/java/com/example/face2info/service/Face2InfoService.java`
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- Create: `src/main/java/com/example/face2info/util/InMemoryMultipartFile.java`
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

- [ ] **Step 1: 写失败测试，约束选中人脸后继续走既有识别与聚合链路**

```java
@Test
void shouldRecognizeSelectedFaceCropInsteadOfOriginalImage() {
    ImageUtils imageUtils = mock(ImageUtils.class);
    FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
    InformationAggregationService aggregationService = mock(InformationAggregationService.class);
    FaceDetectionService detectionService = mock(FaceDetectionService.class);
    SelectedFaceCrop crop = new SelectedFaceCrop()
            .setFilename("face-1.jpg")
            .setContentType("image/jpeg")
            .setBytes(new byte[]{9, 9, 9});

    when(detectionService.getSelectedFaceCrop("det-1", "face-1")).thenReturn(crop);
    when(recognitionService.recognize(any(MultipartFile.class))).thenReturn(new RecognitionEvidence());
    when(aggregationService.aggregate(any())).thenReturn(new AggregationResult().setPerson(new PersonAggregate().setName("Jay Chou")));

    FaceInfoResponse response = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService, detectionService)
            .processSelectedFace("det-1", "face-1");

    assertThat(response.getStatus()).isEqualTo("success");
    verify(recognitionService).recognize(argThat(file -> "face-1.jpg".equals(file.getOriginalFilename())));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: FAIL，提示新构造器、新方法或 `InMemoryMultipartFile` 不存在。

- [ ] **Step 3: 扩展服务接口与实现，增加 `processSelectedFace`**

```java
public interface Face2InfoService {
    FaceInfoResponse process(MultipartFile image);
    FaceInfoResponse processSelectedFace(String detectionId, String faceId);
}
```

```java
@Override
public FaceInfoResponse processSelectedFace(String detectionId, String faceId) {
    SelectedFaceCrop crop = faceDetectionService.getSelectedFaceCrop(detectionId, faceId);
    MultipartFile selectedFace = new InMemoryMultipartFile(crop.getFilename(), crop.getContentType(), crop.getBytes());
    return processRecognizedImage(selectedFace);
}
```

- [ ] **Step 4: 抽取公共主流程方法，避免重复逻辑**

```java
private FaceInfoResponse processRecognizedImage(MultipartFile image) {
    RecognitionEvidence evidence = faceRecognitionService.recognize(image);
    AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);
    return buildResponse(evidence, aggregationResult);
}
```

- [ ] **Step 5: 运行服务测试确认通过**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 6: 提交本任务**

```bash
git add src/main/java/com/example/face2info/service src/main/java/com/example/face2info/util/InMemoryMultipartFile.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java
git commit -m "feat: process selected face crop through main flow"
```

### Task 7：补全文档与完整验证

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`（仅当需要补充运行约定时）
- Test: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
- Test: `src/test/java/com/example/face2info/service/impl/FaceDetectionServiceImplTest.java`
- Test: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
- Test: `face-detector/tests/test_detector.py`
- Test: `face-detector/tests/test_app.py`

- [ ] **Step 1: 更新 README，补充双阶段接口与 Python 服务启动方式**

```md
## 本地人脸检测与选脸流程

1. 启动 Python 检测服务：`uvicorn app:app --host 0.0.0.0 --port 8091`
2. 调用 `POST /api/face2info/detect` 上传多人脸图片
3. 从返回的 `faces[].face_id` 中选择目标脸
4. 调用 `POST /api/face2info/process-selected` 继续主流程
```

- [ ] **Step 2: 运行 Java 定向测试**

Run: `mvn -Dtest=FaceInfoControllerTest,FaceDetectionServiceImplTest,Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 3: 运行 Python 测试**

Run: `pytest face-detector/tests -q`
Expected: PASS

- [ ] **Step 4: 运行完整后端验证**

Run: `mvn clean verify`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交本任务**

```bash
git add README.md docs/superpowers/plans/2026-04-03-face-detection-selection-implementation.md
git commit -m "docs: add face detection implementation plan and setup notes"
```

---

## 自检结果

### Spec 覆盖检查
- 多人脸检测：Task 4、Task 5
- 用户选脸：Task 2、Task 3、Task 6
- 裁剪图接入现有主流程：Task 6
- 会话缓存与过期：Task 2
- 新接口与错误处理：Task 3
- 配置与文档：Task 5、Task 7
- Java / Python 测试：Task 2、Task 3、Task 4、Task 6、Task 7

### 占位项检查
- 本计划未使用 `TODO`、`TBD` 或“后续补充”类占位语。
- 每个代码步骤均给出最小示例代码或命令。

### 类型一致性检查
- 检测会话统一使用 `DetectionSession`
- 选脸结果统一使用 `SelectedFaceCrop`
- 检测响应统一使用 `FaceDetectionResponse`
- 第二阶段处理方法统一命名为 `processSelectedFace`
