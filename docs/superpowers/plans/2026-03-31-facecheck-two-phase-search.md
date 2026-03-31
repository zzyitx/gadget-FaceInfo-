# FaceCheck 两段式搜索修正 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FaceCheck 客户端从单次上传误判结果，修正为“上传拿 `id_search` 后轮询 `/api/search`”的两段式流程，并在超时场景下以 `partial + warning` 方式降级。

**Architecture:** 保持 `controller/service` 的对外语义不变，把第三方两段式协议完全封装在 `client` 内部。通过新增最小内部结果模型承载搜索结果与超时标记，并在 `service` 层把超时信号转换成现有响应中的 `warnings`。

**Tech Stack:** Java 17, Spring Boot 3.3.5, RestTemplate, Jackson, JUnit 5, Spring MockRestServiceServer, Mockito

---

## File Map

- Modify: `src/main/java/com/example/face2info/client/FaceCheckClient.java`
  - 调整客户端接口返回类型，使其表达“最终搜索结果”而不是“上传响应”
- Modify: `src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java`
  - 实现 `upload_pic -> search轮询`，处理远端错误、超时和结果映射
- Modify: `src/main/java/com/example/face2info/config/FaceCheckApiProperties.java`
  - 增加 `searchPath`、`pollIntervalMillis`、`searchTimeoutMillis`、`demo`
- Modify: `src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java`
  - 收缩为上传阶段模型，只保留 `idSearch` 和 `message`
- Create: `src/main/java/com/example/face2info/entity/internal/FaceCheckSearchResponse.java`
  - 承载最终匹配列表和超时标记
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
  - 消费新的客户端返回类型，并把超时转换为 `warnings`
- Modify: `src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java`
  - 按两段式流程重写客户端测试
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
  - 增加超时降级测试并适配新的客户端接口
- Modify: `src/main/resources/application-git.yml`
  - 同步脱敏配置结构
- Modify: `src/main/resources/application.yml`
  - 同步本地真实配置结构

### Task 1: 收紧模型和配置边界

**Files:**
- Modify: `src/main/java/com/example/face2info/config/FaceCheckApiProperties.java`
- Modify: `src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java`
- Create: `src/main/java/com/example/face2info/entity/internal/FaceCheckSearchResponse.java`
- Modify: `src/main/java/com/example/face2info/client/FaceCheckClient.java`

- [ ] **Step 1: 写失败测试，锁定新增配置和返回模型的最小契约**

在 `src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java` 先加一个最小测试，表达客户端最终返回的是“搜索结果 + 超时标记”而不是上传响应：

```java
@Test
void shouldExposeSearchResultContractForFinalMatches() {
    FaceCheckSearchResponse response = new FaceCheckSearchResponse()
            .setItems(java.util.List.of(new FaceCheckMatchCandidate().setSourceHost("instagram.com")))
            .setTimedOut(true);

    assertThat(response.getItems()).hasSize(1);
    assertThat(response.isTimedOut()).isTrue();
}
```

- [ ] **Step 2: 运行单测，确认因为类型不存在而失败**

Run: `mvn -Dtest=FaceCheckClientImplTest#shouldExposeSearchResultContractForFinalMatches test`

Expected: 编译失败，提示 `FaceCheckSearchResponse` 不存在或 `FaceCheckClient` 返回契约不匹配。

- [ ] **Step 3: 写最小实现，补齐模型和接口**

创建 `src/main/java/com/example/face2info/entity/internal/FaceCheckSearchResponse.java`：

```java
package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

public class FaceCheckSearchResponse {

    private List<FaceCheckMatchCandidate> items = new ArrayList<>();
    private boolean timedOut;

    public List<FaceCheckMatchCandidate> getItems() {
        return items;
    }

    public FaceCheckSearchResponse setItems(List<FaceCheckMatchCandidate> items) {
        this.items = items;
        return this;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public FaceCheckSearchResponse setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
        return this;
    }
}
```

更新 `src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java`：

```java
public class FaceCheckUploadResponse {

    private String idSearch;
    private String message;

    public String getIdSearch() {
        return idSearch;
    }

    public FaceCheckUploadResponse setIdSearch(String idSearch) {
        this.idSearch = idSearch;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public FaceCheckUploadResponse setMessage(String message) {
        this.message = message;
        return this;
    }
}
```

更新 `src/main/java/com/example/face2info/client/FaceCheckClient.java`：

```java
package com.example.face2info.client;

import com.example.face2info.entity.internal.FaceCheckSearchResponse;
import org.springframework.web.multipart.MultipartFile;

public interface FaceCheckClient {

    FaceCheckSearchResponse search(MultipartFile image);
}
```

更新 `src/main/java/com/example/face2info/config/FaceCheckApiProperties.java`：

```java
@Getter
@Setter
public class FaceCheckApiProperties {

    private String baseUrl = "https://facecheck.id";
    private String uploadPath = "/api/upload_pic";
    private String searchPath = "/api/search";
    private String apiKey;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int pollIntervalMillis = 1000;
    private int searchTimeoutMillis = 15000;
    private boolean resetPrevImages = true;
    private boolean demo = false;
}
```

- [ ] **Step 4: 运行单测，确认模型契约测试通过**

Run: `mvn -Dtest=FaceCheckClientImplTest#shouldExposeSearchResultContractForFinalMatches test`

Expected: PASS

- [ ] **Step 5: 提交这个边界收紧改动**

```bash
git add src/main/java/com/example/face2info/config/FaceCheckApiProperties.java src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java src/main/java/com/example/face2info/entity/internal/FaceCheckSearchResponse.java src/main/java/com/example/face2info/client/FaceCheckClient.java src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java
git commit -m "feat(client): 收紧 FaceCheck 搜索结果模型与配置边界"
```

### Task 2: 用 TDD 把 FaceCheck 客户端改成两段式上传加轮询

**Files:**
- Modify: `src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java`
- Modify: `src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java`

- [ ] **Step 1: 写“上传后轮询成功”的失败测试**

把 `src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java` 改成先验证 `/api/upload_pic`，再验证 `/api/search`：

```java
@Test
void shouldUploadThenPollSearchUntilItemsAppear() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    server.expect(requestTo("https://facecheck.id/api/upload_pic"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "id_search":"req-1",
                      "message":"uploaded"
                    }
                    """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://facecheck.id/api/search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "message":"searching",
                      "progress":35
                    }
                    """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://facecheck.id/api/search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "output":{
                        "items":[
                          {
                            "score":97,
                            "group":1,
                            "base64":"AAA",
                            "url":{"value":"https://www.instagram.com/p/demo"},
                            "index":0,
                            "seen":3
                          }
                        ]
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

    FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

    FaceCheckSearchResponse response = client.search(
            new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

    assertThat(response.isTimedOut()).isFalse();
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getSourceHost()).isEqualTo("instagram.com");
    server.verify();
}
```

- [ ] **Step 2: 跑这条测试，确认它因为还在调用旧接口而失败**

Run: `mvn -Dtest=FaceCheckClientImplTest#shouldUploadThenPollSearchUntilItemsAppear test`

Expected: FAIL，原因应是 `search` 方法不存在、请求路径不匹配，或只发起了上传请求没有轮询搜索。

- [ ] **Step 3: 写“搜索返回远端错误”的失败测试**

```java
@Test
void shouldThrowWhenSearchReturnsRemoteError() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://facecheck.id/api/upload_pic"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "id_search":"req-err"
                    }
                    """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://facecheck.id/api/search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "error":"quota exceeded",
                      "code":"403"
                    }
                    """, MediaType.APPLICATION_JSON));

    FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), createProperties());

    assertThatThrownBy(() -> client.search(
            new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3})))
            .isInstanceOf(ApiCallException.class)
            .hasMessageContaining("facecheck");

    server.verify();
}
```

- [ ] **Step 4: 写“搜索超时”的失败测试**

```java
@Test
void shouldReturnTimedOutResponseWhenSearchDoesNotFinishInTime() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    server.expect(requestTo("https://facecheck.id/api/upload_pic"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "id_search":"req-timeout"
                    }
                    """, MediaType.APPLICATION_JSON));
    server.expect(requestTo("https://facecheck.id/api/search"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "message":"still searching",
                      "progress":10
                    }
                    """, MediaType.APPLICATION_JSON));

    ApiProperties properties = createProperties();
    properties.getApi().getFacecheck().setSearchTimeoutMillis(1);
    properties.getApi().getFacecheck().setPollIntervalMillis(0);

    FaceCheckClientImpl client = new FaceCheckClientImpl(restTemplate, new ObjectMapper(), properties);

    FaceCheckSearchResponse response = client.search(
            new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3}));

    assertThat(response.isTimedOut()).isTrue();
    assertThat(response.getItems()).isEmpty();
    server.verify();
}
```

- [ ] **Step 5: 运行客户端测试集，确认它们因为实现缺失而失败**

Run: `mvn -Dtest=FaceCheckClientImplTest test`

Expected: FAIL，至少包含成功轮询、远端错误或超时断言失败。

- [ ] **Step 6: 写最小实现，让客户端完成两段式流程**

将 `src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java` 调整为以下结构：

```java
@Slf4j
@Component
public class FaceCheckClientImpl implements FaceCheckClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiProperties properties;

    public FaceCheckClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper, ApiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public FaceCheckSearchResponse search(MultipartFile image) {
        FaceCheckUploadResponse uploadResponse = upload(image);
        return pollSearch(uploadResponse.getIdSearch());
    }

    FaceCheckUploadResponse upload(MultipartFile image) {
        ApiProperties.Api api = properties.getApi();
        String endpoint = api.getFacecheck().getBaseUrl() + api.getFacecheck().getUploadPath();

        try {
            org.springframework.util.LinkedMultiValueMap<String, Object> body =
                    new org.springframework.util.LinkedMultiValueMap<>();
            body.add("images", new org.springframework.core.io.ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return image.getOriginalFilename();
                }
            });

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint,
                    new HttpEntity<>(body, createMultipartHeaders()),
                    String.class);

            return mapUploadResponse(objectMapper.readTree(response.getBody()));
        } catch (IOException | RestClientException ex) {
            log.warn("FaceCheck upload failed endpoint={} message={}", endpoint, ex.getMessage());
            throw new ApiCallException("facecheck upload failed", ex);
        }
    }

    FaceCheckSearchResponse pollSearch(String idSearch) {
        ApiProperties.Api api = properties.getApi();
        String endpoint = api.getFacecheck().getBaseUrl() + api.getFacecheck().getSearchPath();
        long deadline = System.currentTimeMillis() + api.getFacecheck().getSearchTimeoutMillis();

        while (System.currentTimeMillis() <= deadline) {
            JsonNode body = doSearchRequest(endpoint, idSearch);
            if (hasRemoteError(body)) {
                throw new ApiCallException("facecheck search failed: " + body.path("error").asText(""));
            }
            JsonNode itemsNode = body.path("output").path("items");
            if (itemsNode.isArray()) {
                return new FaceCheckSearchResponse().setItems(mapItems(itemsNode));
            }
            sleep(api.getFacecheck().getPollIntervalMillis());
        }

        return new FaceCheckSearchResponse().setTimedOut(true);
    }
}
```

并补齐以下私有方法，保持命名一致：

```java
private HttpHeaders createMultipartHeaders() { ... }
private HttpHeaders createJsonHeaders() { ... }
private JsonNode doSearchRequest(String endpoint, String idSearch) { ... }
private FaceCheckUploadResponse mapUploadResponse(JsonNode body) { ... }
private boolean hasRemoteError(JsonNode body) { ... }
private List<FaceCheckMatchCandidate> mapItems(JsonNode itemNodes) { ... }
private void sleep(int pollIntervalMillis) { ... }
```

`doSearchRequest` 请求体固定为：

```java
Map<String, Object> body = new LinkedHashMap<>();
body.put("id_search", idSearch);
body.put("with_progress", true);
body.put("status_only", false);
body.put("demo", properties.getApi().getFacecheck().isDemo());
```

`createMultipartHeaders` 与 `createJsonHeaders` 都用：

```java
headers.setAccept(List.of(MediaType.APPLICATION_JSON));
headers.set("Authorization", properties.getApi().getFacecheck().getApiKey());
```

不要调用 `setBearerAuth`。

- [ ] **Step 7: 运行客户端测试，确认全部变绿**

Run: `mvn -Dtest=FaceCheckClientImplTest test`

Expected: PASS

- [ ] **Step 8: 提交两段式客户端改动**

```bash
git add src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java
git commit -m "fix(client): 改为 FaceCheck 两段式上传与轮询搜索"
```

### Task 3: 把超时信号映射为 `partial + warning`

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

- [ ] **Step 1: 写“FaceCheck 超时降级”的失败测试**

在 `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java` 新增：

```java
@Test
void shouldReturnPartialAndWarningWhenFacecheckSearchTimesOut() {
    ImageUtils imageUtils = mock(ImageUtils.class);
    FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
    InformationAggregationService aggregationService = mock(InformationAggregationService.class);
    FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    RecognitionEvidence evidence = new RecognitionEvidence();

    doNothing().when(imageUtils).validateImage(image);
    when(recognitionService.recognize(image)).thenReturn(evidence);
    when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
            .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer."))
            .setWarnings(new java.util.ArrayList<>()));
    when(faceCheckClient.search(image)).thenReturn(new FaceCheckSearchResponse().setTimedOut(true));

    FaceInfoResponse response = new Face2InfoServiceImpl(
            imageUtils, recognitionService, aggregationService, faceCheckClient).process(image);

    assertThat(response.getStatus()).isEqualTo("partial");
    assertThat(response.getWarnings()).contains("FaceCheck 搜索超时");
    assertThat(response.getFacecheckMatches()).isEmpty();
}
```

- [ ] **Step 2: 写“FaceCheck 正常匹配”的适配测试**

把现有匹配测试改成使用 `search(image)` 和 `FaceCheckSearchResponse`：

```java
when(faceCheckClient.search(image)).thenReturn(new FaceCheckSearchResponse()
        .setItems(java.util.List.of(
                new FaceCheckMatchCandidate()
                        .setImageDataUrl("data:image/jpeg;base64,AAA")
                        .setSimilarityScore(91.3)
                        .setSourceHost("x.com")
                        .setSourceUrl("https://x.com/demo")
                        .setGroup(2)
                        .setSeen(9)
                        .setIndex(1)
        )));
```

- [ ] **Step 3: 运行服务测试，确认因为实现仍使用旧接口而失败**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`

Expected: FAIL，原因应是 `upload(image)` 已不存在，或 `warnings/status` 尚未处理超时。

- [ ] **Step 4: 写最小实现，把超时信号转成现有响应**

更新 `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java` 的 FaceCheck 处理段：

```java
List<String> combinedErrors = new ArrayList<>(aggregationResult.getErrors());
List<String> warnings = new ArrayList<>(aggregationResult.getWarnings());
List<FaceCheckMatch> facecheckMatches = new ArrayList<>();

try {
    FaceCheckSearchResponse faceCheckResponse = faceCheckClient.search(image);
    if (faceCheckResponse.isTimedOut()) {
        warnings.add("FaceCheck 搜索超时");
    }
    facecheckMatches = faceCheckResponse.getItems().stream()
            .map(this::toFacecheckMatch)
            .toList();
} catch (ApiCallException ex) {
    log.warn("FaceCheck 匹配阶段失败 message={}", ex.getMessage());
    combinedErrors.add(ex.getMessage());
}
```

并确保成功返回分支使用新的 `warnings` 变量：

```java
String status = (!combinedErrors.isEmpty() || !warnings.isEmpty()) ? "partial" : "success";

return new FaceInfoResponse()
        .setPerson(person)
        .setNews(aggregationResult.getNews())
        .setWarnings(warnings)
        .setImageMatches(evidence.getImageMatches())
        .setFacecheckMatches(facecheckMatches)
        .setStatus(status)
        .setError(combinedErrors.isEmpty() ? null : String.join("; ", combinedErrors));
```

- [ ] **Step 5: 运行服务测试，确认通过**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`

Expected: PASS

- [ ] **Step 6: 提交服务降级改动**

```bash
git add src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java
git commit -m "fix(service): 将 FaceCheck 超时降级为 partial warning"
```

### Task 4: 同步配置文件并做回归验证

**Files:**
- Modify: `src/main/resources/application-git.yml`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 写配置结构同步改动**

在两个配置文件的 `face2info.api.facecheck` 下补齐：

```yaml
    facecheck:
      base-url: https://facecheck.id
      upload-path: /api/upload_pic
      search-path: /api/search
      api-key: ${FACECHECK_API_KEY:}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
      poll-interval-ms: 1000
      search-timeout-ms: 15000
      reset-prev-images: true
      demo: false
```

如果 `application.yml` 中已有真实 key，仅同步结构，不覆盖真实值。

- [ ] **Step 2: 跑聚合相关回归测试**

Run: `mvn -Dtest=FaceCheckClientImplTest,Face2InfoServiceImplTest test`

Expected: PASS

- [ ] **Step 3: 跑完整验证命令**

Run: `mvn clean verify`

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交配置与验证结果**

```bash
git add src/main/resources/application-git.yml src/main/resources/application.yml
git commit -m "chore(config): 同步 FaceCheck 两段式搜索配置"
```

## Self-Review

- Spec coverage:
  - 两段式 `upload_pic -> search`：Task 2
  - 保持 `service` 不感知 `id_search`：Task 1, Task 3
  - 超时降级为 `partial + warning`：Task 3
  - 配置双轨同步：Task 4
  - 测试补齐：Task 2, Task 3, Task 4
- Placeholder scan:
  - 计划中无 `TODO/TBD/implement later`
  - 每个代码步骤都给出明确类名、方法名、命令和预期
- Type consistency:
  - 客户端统一使用 `FaceCheckSearchResponse`
  - 服务层统一调用 `faceCheckClient.search(image)`
  - 超时标记统一为 `timedOut`
