# FaceCheck Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留现有人物聚合链路的前提下，接入 `facecheck.id` 的图片匹配能力，并将前端图片匹配区域改为展示 `facecheck_matches`。

**Architecture:** 后端新增独立的 `FaceCheckClient`、内部模型和响应模型，由 `Face2InfoServiceImpl` 在现有聚合流程之外补充一次 `FaceCheck` 查询，并把映射后的稳定字段挂到 `FaceInfoResponse.facecheckMatches`。前端静态页停止使用旧 `image_matches` 作为主展示来源，改为渲染新的 `facecheck_matches`，局部失败时按现有 `partial` 语义降级。

**Tech Stack:** Java 17, Spring Boot 3.3.5, Maven, JUnit 5, MockMvc, Mockito, static `index.html`

---

## File Structure

**Create:**

- `src/main/java/com/example/face2info/client/FaceCheckClient.java`
- `src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java`
- `src/main/java/com/example/face2info/config/FaceCheckApiProperties.java`
- `src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java`
- `src/main/java/com/example/face2info/entity/internal/FaceCheckMatchCandidate.java`
- `src/main/java/com/example/face2info/entity/response/FaceCheckMatch.java`
- `src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java`

**Modify:**

- `src/main/java/com/example/face2info/config/ApiProperties.java`
- `src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
- `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- `src/main/resources/application-git.yml`
- `src/main/resources/application.yml`
- `src/main/resources/static/index.html`
- `README.md`
- `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
- `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
- `src/test/java/com/example/face2info/controller/StaticPageTest.java`

**Why this structure:**

- `client` 只负责和 `facecheck.id` 交互，避免第三方协议泄漏到 `service`
- `entity.internal` 负责承接第三方响应适配
- `entity.response` 提供稳定输出字段，前端只依赖这里
- `Face2InfoServiceImpl` 继续承担总编排职责，不新增额外 orchestration service
- 测试按 client / service / controller / static page 四层补齐

### Task 1: 扩展响应模型与配置骨架

**Files:**

- Create: `src/main/java/com/example/face2info/config/FaceCheckApiProperties.java`
- Create: `src/main/java/com/example/face2info/entity/response/FaceCheckMatch.java`
- Modify: `src/main/java/com/example/face2info/config/ApiProperties.java`
- Modify: `src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
- Modify: `src/main/resources/application-git.yml`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`

- [ ] **Step 1: 先写控制器层失败测试，锁定新响应字段和配置段**

```java
@Test
void shouldExposeFaceCheckMatchesInResponseJson() throws Exception {
    when(face2InfoService.process(any())).thenReturn(new FaceInfoResponse()
            .setStatus("success")
            .setFacecheckMatches(java.util.List.of(
                    new FaceCheckMatch()
                            .setImageDataUrl("data:image/jpeg;base64,AAA")
                            .setSimilarityScore(97.2)
                            .setSourceHost("instagram.com")
                            .setSourceUrl("https://instagram.com/p/demo")
                            .setGroup(1)
                            .setSeen(3)
                            .setIndex(0)
            )));

    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    mockMvc.perform(multipart("/api/face2info").file(image))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.facecheck_matches[0].image_data_url").value("data:image/jpeg;base64,AAA"))
            .andExpect(jsonPath("$.facecheck_matches[0].similarity_score").value(97.2))
            .andExpect(jsonPath("$.facecheck_matches[0].source_host").value("instagram.com"));
}

@Test
void shouldContainFacecheckSectionInApplicationGitConfig() throws Exception {
    String content = Files.readString(Path.of("src/main/resources/application-git.yml"));

    assertThat(content).contains("facecheck:");
    assertThat(content).contains("upload-path:");
    assertThat(content).contains("reset-prev-images:");
}
```

- [ ] **Step 2: 运行测试，确认当前实现确实失败**

Run:

```bash
mvn -Dtest=FaceInfoControllerTest test
```

Expected:

- `jsonPath("$.facecheck_matches[0]...")` 失败，因为 `FaceInfoResponse` 还没有该字段
- 配置断言失败，因为 `application-git.yml` 还没有 `facecheck:` 段

- [ ] **Step 3: 用最小实现补齐响应模型与配置类**

```java
@Schema(description = "FaceCheck image match")
public class FaceCheckMatch {

    @JsonProperty("image_data_url")
    private String imageDataUrl;

    @JsonProperty("similarity_score")
    private double similarityScore;

    @JsonProperty("source_host")
    private String sourceHost;

    @JsonProperty("source_url")
    private String sourceUrl;

    private int group;
    private int seen;
    private int index;

    // getters / fluent setters
}
```

```java
public class FaceCheckApiProperties {

    private String baseUrl = "https://facecheck.id";
    private String uploadPath = "/api/upload_pic";
    private String apiKey;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private boolean resetPrevImages = true;

    // getters / setters
}
```

```java
public static class Api {

    private SerpApiProperties serp = new SerpApiProperties();
    private NewsApiProperties news = new NewsApiProperties();
    private JinaApiProperties jina = new JinaApiProperties();
    private KimiApiProperties kimi = new KimiApiProperties();
    private SummaryApiProperties summary = new SummaryApiProperties();
    private FaceCheckApiProperties facecheck = new FaceCheckApiProperties();
    private Proxy proxy = new Proxy();
}
```

```java
public class FaceInfoResponse {

    private PersonInfo person;
    private List<NewsItem> news = new ArrayList<>();

    @JsonProperty("image_matches")
    private List<ImageMatch> imageMatches = new ArrayList<>();

    @JsonProperty("facecheck_matches")
    private List<FaceCheckMatch> facecheckMatches = new ArrayList<>();

    private List<String> warnings = new ArrayList<>();
    private String status;
    private String error;

    public List<FaceCheckMatch> getFacecheckMatches() {
        return facecheckMatches;
    }

    public FaceInfoResponse setFacecheckMatches(List<FaceCheckMatch> facecheckMatches) {
        this.facecheckMatches = facecheckMatches;
        return this;
    }
}
```

```yaml
face2info:
  api:
    facecheck:
      base-url: https://facecheck.id
      upload-path: /api/upload_pic
      api-key: ${FACECHECK_API_KEY:}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
      reset-prev-images: true
```

- [ ] **Step 4: 重新运行控制器测试，确认变绿**

Run:

```bash
mvn -Dtest=FaceInfoControllerTest test
```

Expected:

- `FaceInfoControllerTest` 全部通过

- [ ] **Step 5: 提交本任务**

```bash
git add src/main/java/com/example/face2info/config/FaceCheckApiProperties.java src/main/java/com/example/face2info/config/ApiProperties.java src/main/java/com/example/face2info/entity/response/FaceCheckMatch.java src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java src/main/resources/application-git.yml src/main/resources/application.yml src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git commit -m "feat(config): 新增 facecheck 响应模型与配置骨架"
```

### Task 2: 实现 FaceCheck client 及其适配逻辑

**Files:**

- Create: `src/main/java/com/example/face2info/client/FaceCheckClient.java`
- Create: `src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java`
- Create: `src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java`
- Create: `src/main/java/com/example/face2info/entity/internal/FaceCheckMatchCandidate.java`
- Test: `src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java`

- [ ] **Step 1: 先写 client 层失败测试，锁定请求和映射行为**

```java
@Test
void shouldMapUploadResponseItemsToMatchCandidates() throws Exception {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://facecheck.id/api/upload_pic"))
            .andRespond(withSuccess("""
                    {
                      "id_search":"req-1",
                      "output":{
                        "items":[
                          {
                            "score":97.2,
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

    FaceCheckUploadResponse response = client.upload(image);

    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getSourceHost()).isEqualTo("instagram.com");
    assertThat(response.getItems().get(0).getImageDataUrl()).isEqualTo("data:image/jpeg;base64,AAA");
}

@Test
void shouldReturnEmptyItemsWhenFacecheckOutputIsMissing() throws Exception {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://facecheck.id/api/upload_pic"))
            .andRespond(withSuccess("{\"id_search\":\"req-2\"}", MediaType.APPLICATION_JSON));

    FaceCheckUploadResponse response = client.upload(image);

    assertThat(response.getItems()).isEmpty();
}

@Test
void shouldWrapRemoteErrorsAsApiCallException() {
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://facecheck.id/api/upload_pic"))
            .andRespond(withServerError());

    assertThatThrownBy(() -> client.upload(image))
            .isInstanceOf(ApiCallException.class)
            .hasMessageContaining("facecheck");
}
```

- [ ] **Step 2: 运行单测，确认现在失败**

Run:

```bash
mvn -Dtest=FaceCheckClientImplTest test
```

Expected:

- 编译失败，因为 `FaceCheckClient`、`FaceCheckClientImpl`、内部模型尚不存在

- [ ] **Step 3: 按最小实现补齐 client 和内部模型**

```java
public interface FaceCheckClient {

    FaceCheckUploadResponse upload(MultipartFile image);
}
```

```java
public class FaceCheckMatchCandidate {

    private String imageDataUrl;
    private double similarityScore;
    private String sourceHost;
    private String sourceUrl;
    private int group;
    private int seen;
    private int index;

    // getters / fluent setters
}
```

```java
public class FaceCheckUploadResponse {

    private String idSearch;
    private List<FaceCheckMatchCandidate> items = new ArrayList<>();

    public String getIdSearch() {
        return idSearch;
    }

    public FaceCheckUploadResponse setIdSearch(String idSearch) {
        this.idSearch = idSearch;
        return this;
    }

    public List<FaceCheckMatchCandidate> getItems() {
        return items;
    }

    public FaceCheckUploadResponse setItems(List<FaceCheckMatchCandidate> items) {
        this.items = items;
        return this;
    }
}
```

```java
@Service
public class FaceCheckClientImpl implements FaceCheckClient {

    private final RestTemplate restTemplate;
    private final ApiProperties apiProperties;

    public FaceCheckClientImpl(RestTemplate restTemplate, ApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    @Override
    public FaceCheckUploadResponse upload(MultipartFile image) {
        String endpoint = apiProperties.getApi().getFacecheck().getBaseUrl()
                + apiProperties.getApi().getFacecheck().getUploadPath();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("images", List.of(Base64.getEncoder().encodeToString(image.getBytes())));
        body.put("id_search", UUID.randomUUID().toString());
        body.put("reset_prev_images", apiProperties.getApi().getFacecheck().isResetPrevImages());

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(endpoint, body, JsonNode.class);
        return mapResponse(response.getBody());
    }

    FaceCheckUploadResponse mapResponse(JsonNode body) {
        List<FaceCheckMatchCandidate> items = Optional.ofNullable(body.path("output").path("items"))
                .filter(JsonNode::isArray)
                .stream()
                .flatMap(node -> StreamSupport.stream(node.spliterator(), false))
                .map(this::mapItem)
                .filter(item -> StringUtils.hasText(item.getImageDataUrl()))
                .toList();

        return new FaceCheckUploadResponse()
                .setIdSearch(body.path("id_search").asText(""))
                .setItems(items);
    }

    private FaceCheckMatchCandidate mapItem(JsonNode item) {
        String sourceUrl = item.path("url").path("value").asText("");
        return new FaceCheckMatchCandidate()
                .setImageDataUrl(toDataUrl(item.path("base64").asText("")))
                .setSimilarityScore(item.path("score").asDouble(0))
                .setSourceHost(extractHost(sourceUrl))
                .setSourceUrl(sourceUrl)
                .setGroup(item.path("group").asInt(0))
                .setSeen(item.path("seen").asInt(0))
                .setIndex(item.path("index").asInt(0));
    }
}
```

- [ ] **Step 4: 运行 client 单测，确认通过**

Run:

```bash
mvn -Dtest=FaceCheckClientImplTest test
```

Expected:

- `FaceCheckClientImplTest` 通过

- [ ] **Step 5: 提交本任务**

```bash
git add src/main/java/com/example/face2info/client/FaceCheckClient.java src/main/java/com/example/face2info/client/impl/FaceCheckClientImpl.java src/main/java/com/example/face2info/entity/internal/FaceCheckUploadResponse.java src/main/java/com/example/face2info/entity/internal/FaceCheckMatchCandidate.java src/test/java/com/example/face2info/client/impl/FaceCheckClientImplTest.java
git commit -m "feat(client): 新增 facecheck 上传查询客户端"
```

### Task 3: 将 FaceCheck 结果接入总编排服务

**Files:**

- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

- [ ] **Step 1: 先写服务层失败测试，锁定成功、空结果和局部失败行为**

```java
@Test
void shouldMapFacecheckMatchesIntoResponse() {
    FaceCheckClient faceCheckClient = mock(FaceCheckClient.class);
    when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse()
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

    Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService, faceCheckClient);
    FaceInfoResponse response = service.process(image);

    assertThat(response.getFacecheckMatches()).hasSize(1);
    assertThat(response.getFacecheckMatches().get(0).getSourceHost()).isEqualTo("x.com");
}

@Test
void shouldKeepSuccessWhenFacecheckReturnsEmptyMatches() {
    when(faceCheckClient.upload(image)).thenReturn(new FaceCheckUploadResponse());

    FaceInfoResponse response = service.process(image);

    assertThat(response.getStatus()).isEqualTo("success");
    assertThat(response.getFacecheckMatches()).isEmpty();
}

@Test
void shouldDowngradeToPartialWhenFacecheckFailsButPersonExists() {
    when(faceCheckClient.upload(image)).thenThrow(new ApiCallException("facecheck timeout"));

    FaceInfoResponse response = service.process(image);

    assertThat(response.getStatus()).isEqualTo("partial");
    assertThat(response.getFacecheckMatches()).isEmpty();
    assertThat(response.getError()).contains("facecheck");
}
```

- [ ] **Step 2: 运行服务测试，确认先失败**

Run:

```bash
mvn -Dtest=Face2InfoServiceImplTest test
```

Expected:

- 编译失败或断言失败，因为构造器还没有 `FaceCheckClient`，响应中也没有映射逻辑

- [ ] **Step 3: 最小改动实现服务编排和降级**

```java
@Service
public class Face2InfoServiceImpl implements Face2InfoService {

    private final ImageUtils imageUtils;
    private final FaceRecognitionService faceRecognitionService;
    private final InformationAggregationService informationAggregationService;
    private final FaceCheckClient faceCheckClient;

    public Face2InfoServiceImpl(ImageUtils imageUtils,
                                FaceRecognitionService faceRecognitionService,
                                InformationAggregationService informationAggregationService,
                                FaceCheckClient faceCheckClient) {
        this.imageUtils = imageUtils;
        this.faceRecognitionService = faceRecognitionService;
        this.informationAggregationService = informationAggregationService;
        this.faceCheckClient = faceCheckClient;
    }

    @Override
    public FaceInfoResponse process(MultipartFile image) {
        RecognitionEvidence evidence = faceRecognitionService.recognize(image);
        AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);

        List<String> errors = new ArrayList<>(aggregationResult.getErrors());
        List<FaceCheckMatch> facecheckMatches = new ArrayList<>();
        try {
            facecheckMatches = faceCheckClient.upload(image).getItems().stream()
                    .map(item -> new FaceCheckMatch()
                            .setImageDataUrl(item.getImageDataUrl())
                            .setSimilarityScore(item.getSimilarityScore())
                            .setSourceHost(item.getSourceHost())
                            .setSourceUrl(item.getSourceUrl())
                            .setGroup(item.getGroup())
                            .setSeen(item.getSeen())
                            .setIndex(item.getIndex()))
                    .toList();
        } catch (ApiCallException ex) {
            errors.add(ex.getMessage());
        }

        String status = errors.isEmpty() ? "success" : "partial";
        return new FaceInfoResponse()
                .setPerson(person)
                .setNews(aggregationResult.getNews())
                .setWarnings(aggregationResult.getWarnings())
                .setImageMatches(evidence.getImageMatches())
                .setFacecheckMatches(facecheckMatches)
                .setStatus(person == null ? "failed" : status)
                .setError(errors.isEmpty() ? null : String.join("; ", errors));
    }
}
```

- [ ] **Step 4: 运行服务测试确认通过**

Run:

```bash
mvn -Dtest=Face2InfoServiceImplTest test
```

Expected:

- `Face2InfoServiceImplTest` 通过

- [ ] **Step 5: 提交本任务**

```bash
git add src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java
git commit -m "feat(service): 接入 facecheck 匹配结果并支持降级"
```

### Task 4: 改造静态页展示 FaceCheck 匹配结果

**Files:**

- Modify: `src/main/resources/static/index.html`
- Modify: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: 先写静态页失败测试，锁定页面结构和脚本入口**

```java
@Test
void shouldRenderFacecheckMatchSectionOnIndexPage() throws Exception {
    mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("facecheck_matches")))
            .andExpect(content().string(containsString("renderFacecheckMatches")))
            .andExpect(content().string(containsString("打开来源")))
            .andExpect(content().string(containsString("相似度")));
}
```

- [ ] **Step 2: 运行测试，确认当前页面还没有这些内容**

Run:

```bash
mvn -Dtest=StaticPageTest test
```

Expected:

- `containsString("renderFacecheckMatches")` 失败

- [ ] **Step 3: 以最小前端改动替换旧展示逻辑**

```html
<section class="card" id="matchesCard">
    <div class="eyebrow">FaceCheck 匹配结果</div>
    <div class="empty">暂无 FaceCheck 相似图片结果。</div>
</section>
```

```javascript
function buildSimplifiedJson(data) {
    return {
        status: data.status || null,
        error: data.error || null,
        person: data.person || null,
        facecheck_matches: (data.facecheck_matches || []).slice(0, 20),
        image_matches: (data.image_matches || []).slice(0, 20),
        news: (data.news || []).slice(0, 20)
    };
}

function renderFacecheckMatches(matches, status, error) {
    if (!matches.length) {
        const message = status === "partial" && error
                ? "FaceCheck 调用失败，本次未返回相似图片。"
                : "暂无 FaceCheck 相似图片结果。";
        matchesCard.innerHTML =
            '<div class="eyebrow">FaceCheck 匹配结果</div>' +
            '<div class="empty">' + escapeHtml(message) + '</div>';
        return;
    }

    const html = matches.map(function (item) {
        const sourceLink = item.source_url
            ? '<a href="' + escapeHtml(item.source_url) + '" target="_blank" rel="noreferrer">打开来源</a>'
            : '<span>无来源链接</span>';

        return '<div class="serp-entry">' +
            '<img src="' + escapeHtml(item.image_data_url) + '" alt="FaceCheck match preview">' +
            '<div class="pill">相似度 ' + escapeHtml(item.similarity_score) + '</div>' +
            '<strong>' + escapeHtml(item.source_host || "未知来源") + '</strong>' +
            '<div class="serp-link">' + sourceLink + '</div>' +
            '</div>';
    }).join("");

    matchesCard.innerHTML =
        '<div class="eyebrow">FaceCheck 匹配结果</div>' +
        '<div class="list">' + html + '</div>';
}
```

```javascript
renderFacecheckMatches(data.facecheck_matches || [], data.status, data.error);
```

- [ ] **Step 4: 运行静态页测试确认通过**

Run:

```bash
mvn -Dtest=StaticPageTest test
```

Expected:

- `StaticPageTest` 通过

- [ ] **Step 5: 提交本任务**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "feat(frontend): 使用 facecheck 结果替换图片匹配展示"
```

### Task 5: 更新 README 并执行回归验证

**Files:**

- Modify: `README.md`

- [ ] **Step 1: 先补一个配置文档断言，避免 README 漏掉 facecheck**

在 `FaceInfoControllerTest` 中追加：

```java
@Test
void shouldMentionFacecheckConfigInReadme() throws Exception {
    String content = Files.readString(Path.of("README.md"));

    assertThat(content).contains("FaceCheck");
    assertThat(content).contains("FACECHECK_API_KEY");
}
```

- [ ] **Step 2: 运行该测试，确认当前 README 未覆盖新能力**

Run:

```bash
mvn -Dtest=FaceInfoControllerTest test
```

Expected:

- `contains("FaceCheck")` 失败

- [ ] **Step 3: 更新 README 并同步测试**

README 增补的最小内容：

```md
## FaceCheck 图片匹配

- 通过 `face2info.api.facecheck` 配置接入 `facecheck.id`
- 需要环境变量 `FACECHECK_API_KEY`
- 页面中的图片匹配区域优先展示 `facecheck_matches`
- `FaceCheck` 失败时不会阻断现有人物聚合流程，接口可能返回 `partial`
```

- [ ] **Step 4: 运行针对性测试与完整校验**

Run:

```bash
mvn -Dtest=FaceInfoControllerTest,FaceCheckClientImplTest,Face2InfoServiceImplTest,StaticPageTest test
mvn clean verify
```

Expected:

- 指定测试全部通过
- `mvn clean verify` 通过，无新增失败

- [ ] **Step 5: 提交本任务**

```bash
git add README.md src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git add src/main/java/com/example/face2info/client src/main/java/com/example/face2info/config src/main/java/com/example/face2info/entity src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java src/main/resources/static/index.html src/test/java/com/example/face2info
git commit -m "docs(readme): 补充 facecheck 接入说明与回归验证"
```

## Self-Review

**Spec coverage:**

- `FaceCheck` 独立 client：Task 2
- 顶层 `facecheck_matches`：Task 1, Task 3
- 保留现有人物聚合：Task 3
- 前端显示图片、相似度、来源域名：Task 4
- 降级与 `partial`：Task 3, Task 4
- 配置同步到 `application.yml` / `application-git.yml`：Task 1
- README 更新：Task 5
- 成功 / 失败 / 边界测试：Task 1, Task 2, Task 3, Task 4

**Placeholder scan:**

- 已避免使用 `TODO`、`TBD`、`适当处理` 这类占位表达
- 每个任务都给出了实际文件、测试命令和最小代码骨架

**Type consistency:**

- 新增顶层字段统一命名为 `facecheckMatches` / `facecheck_matches`
- client 返回 `FaceCheckUploadResponse`
- service 对外映射 `FaceCheckMatch`

