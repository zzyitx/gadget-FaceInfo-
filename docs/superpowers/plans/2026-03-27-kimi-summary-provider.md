# Kimi 摘要 Provider 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 为正文处理链路接入可切换的 Kimi Provider，产出人物摘要与前端标签，并在失败时记录后端日志且向响应追加通用 warning。

**架构：** 保持现有 `client -> service -> response` 分层不变，在 `SummaryGenerationClient` 下新增 Kimi 实现，通过 `face2info.api.summary.provider` 进行条件装配。聚合服务继续只做编排，Kimi 失败时自动降级回原有聚合结果，并通过响应顶层 `warnings` 提示前端。

**技术栈：** Java 17、Spring Boot 3.3.5、RestTemplate、JUnit 5、Mockito、spring-boot-starter-test

---

## 文件结构与职责

- `src/main/java/com/example/face2info/config/KimiApiProperties.java`
  负责承载 Kimi 独立配置，包括地址、密钥、模型、超时、重试和系统提示词。
- `src/main/java/com/example/face2info/config/ApiProperties.java`
  负责把 `KimiApiProperties` 纳入统一配置树。
- `src/main/java/com/example/face2info/config/RestTemplateConfig.java`
  负责把 Kimi 超时纳入全局 `RestTemplate` 最大超时计算。
- `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
  负责请求 Kimi、解析 JSON、记录受控错误日志，并返回 `ResolvedPersonProfile`。
- `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
  负责只在 `summary.provider=noop` 时注册为默认空实现。
- `src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`
  负责补充 Kimi 输出字段，承载 `summary`、`tags`、`evidenceUrls`。
- `src/main/java/com/example/face2info/entity/internal/PersonAggregate.java`
  负责补充最终聚合结果中的 `summary`、`tags`。
- `src/main/java/com/example/face2info/entity/internal/AggregationResult.java`
  负责补充 `warnings` 集合，承载面向前端的通用提示。
- `src/main/java/com/example/face2info/entity/response/PersonInfo.java`
  负责对外暴露 `summary`、`tags`。
- `src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
  负责对外暴露顶层 `warnings`。
- `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
  负责在 Kimi 成功时合并增强结果，在失败时降级并追加 warning。
- `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
  负责把内部聚合结果映射到最终响应字段。
- `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`
  负责验证 Kimi 客户端成功、超时、HTTP 错误、非法 JSON 等场景。
- `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
  负责验证聚合成功、Kimi 失败降级、warning 追加。
- `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
  负责验证摘要、标签、warning 的最终响应映射。
- `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
  负责验证控制层 JSON 响应中新增字段的兼容性。
- `src/main/resources/application.yml`
  负责补充 `face2info.api.kimi` 和 `summary.provider=kimi` 说明占位。
- `README.md`
  负责补充 Kimi 配置说明与响应字段说明。

### 任务 1：先补响应与内部模型的失败测试

**文件：**
- 修改：`src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
- 修改：`src/main/java/com/example/face2info/entity/internal/AggregationResult.java`
- 修改：`src/main/java/com/example/face2info/entity/internal/PersonAggregate.java`
- 修改：`src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`
- 修改：`src/main/java/com/example/face2info/entity/response/PersonInfo.java`
- 修改：`src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
- 修改：`src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`

- [ ] **步骤 1：先写 `Face2InfoServiceImpl` 的失败测试**

```java
@Test
void shouldMapSummaryTagsAndWarningsToResponse() {
    ImageUtils imageUtils = mock(ImageUtils.class);
    FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
    InformationAggregationService aggregationService = mock(InformationAggregationService.class);
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    RecognitionEvidence evidence = new RecognitionEvidence();

    doNothing().when(imageUtils).validateImage(image);
    when(recognitionService.recognize(image)).thenReturn(evidence);
    when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
            .setPerson(new PersonAggregate()
                    .setName("周杰伦")
                    .setDescription("华语流行男歌手")
                    .setSummary("周杰伦是华语流行乐代表人物。")
                    .setTags(java.util.List.of("歌手", "音乐制作人")))
            .setWarnings(java.util.List.of("正文智能处理暂时不可用")));

    Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService);

    FaceInfoResponse response = service.process(image);

    assertThat(response.getPerson().getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。");
    assertThat(response.getPerson().getTags()).containsExactly("歌手", "音乐制作人");
    assertThat(response.getWarnings()).containsExactly("正文智能处理暂时不可用");
}
```

- [ ] **步骤 2：运行测试，确认因字段不存在而失败**

运行：`mvn -Dtest=Face2InfoServiceImplTest#shouldMapSummaryTagsAndWarningsToResponse test`

预期：FAIL，报错集中在 `setSummary`、`setTags`、`setWarnings`、`getWarnings` 等方法不存在。

- [ ] **步骤 3：最小实现内部模型与响应模型字段**

```java
// src/main/java/com/example/face2info/entity/internal/PersonAggregate.java
private String summary;
private List<String> tags = new ArrayList<>();

public String getSummary() {
    return summary;
}

public PersonAggregate setSummary(String summary) {
    this.summary = summary;
    return this;
}

public List<String> getTags() {
    return tags;
}

public PersonAggregate setTags(List<String> tags) {
    this.tags = tags;
    return this;
}
```

```java
// src/main/java/com/example/face2info/entity/internal/AggregationResult.java
private List<String> warnings = new ArrayList<>();

public List<String> getWarnings() {
    return warnings;
}

public AggregationResult setWarnings(List<String> warnings) {
    this.warnings = warnings;
    return this;
}
```

```java
// src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java
private List<String> tags = new ArrayList<>();

public List<String> getTags() {
    return tags;
}

public ResolvedPersonProfile setTags(List<String> tags) {
    this.tags = tags;
    return this;
}
```

```java
// src/main/java/com/example/face2info/entity/response/PersonInfo.java
private String summary;
private List<String> tags = new ArrayList<>();

public String getSummary() {
    return summary;
}

public PersonInfo setSummary(String summary) {
    this.summary = summary;
    return this;
}

public List<String> getTags() {
    return tags;
}

public PersonInfo setTags(List<String> tags) {
    this.tags = tags;
    return this;
}
```

```java
// src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java
private List<String> warnings = new ArrayList<>();

public List<String> getWarnings() {
    return warnings;
}

public FaceInfoResponse setWarnings(List<String> warnings) {
    this.warnings = warnings;
    return this;
}
```

```java
// src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java
PersonInfo person = new PersonInfo()
        .setName(aggregationResult.getPerson().getName())
        .setDescription(aggregationResult.getPerson().getDescription())
        .setSummary(aggregationResult.getPerson().getSummary())
        .setTags(aggregationResult.getPerson().getTags())
        .setWikipedia(aggregationResult.getPerson().getWikipedia())
        .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
        .setSocialAccounts(aggregationResult.getSocialAccounts());

return new FaceInfoResponse()
        .setPerson(person)
        .setNews(aggregationResult.getNews())
        .setWarnings(aggregationResult.getWarnings())
        .setImageMatches(evidence.getImageMatches())
        .setStatus(aggregationResult.getErrors().isEmpty() ? "success" : "partial")
        .setError(aggregationResult.getErrors().isEmpty() ? null : String.join("; ", aggregationResult.getErrors()));
```

- [ ] **步骤 4：运行测试，确认通过**

运行：`mvn -Dtest=Face2InfoServiceImplTest#shouldMapSummaryTagsAndWarningsToResponse test`

预期：PASS

- [ ] **步骤 5：补充一个失败响应不带 warning 的保护测试**

```java
@Test
void shouldKeepWarningsEmptyWhenAggregationDoesNotProvideWarnings() {
    ImageUtils imageUtils = mock(ImageUtils.class);
    FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
    InformationAggregationService aggregationService = mock(InformationAggregationService.class);
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    RecognitionEvidence evidence = new RecognitionEvidence();

    doNothing().when(imageUtils).validateImage(image);
    when(recognitionService.recognize(image)).thenReturn(evidence);
    when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
            .setPerson(new PersonAggregate().setName("周杰伦")));

    FaceInfoResponse response = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService).process(image);

    assertThat(response.getWarnings()).isEmpty();
}
```

- [ ] **步骤 6：运行 `Face2InfoServiceImplTest` 全量测试**

运行：`mvn -Dtest=Face2InfoServiceImplTest test`

预期：PASS

- [ ] **步骤 7：提交**

```bash
git add src/main/java/com/example/face2info/entity/internal/AggregationResult.java src/main/java/com/example/face2info/entity/internal/PersonAggregate.java src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java src/main/java/com/example/face2info/entity/response/PersonInfo.java src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java
git commit -m "feat(响应模型): 补充Kimi摘要标签与warning映射"
```

### 任务 2：先写 Kimi 客户端失败测试，再实现 Provider

**文件：**
- 新建：`src/main/java/com/example/face2info/config/KimiApiProperties.java`
- 修改：`src/main/java/com/example/face2info/config/ApiProperties.java`
- 修改：`src/main/java/com/example/face2info/config/RestTemplateConfig.java`
- 修改：`src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
- 新建：`src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
- 新建：`src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **步骤 1：先写 Kimi 客户端成功解析测试**

```java
@Test
void shouldParseSummaryAndTagsFromKimiResponse() {
    ApiProperties properties = new ApiProperties();
    properties.getApi().getSummary().setEnabled(true);
    properties.getApi().getSummary().setProvider("kimi");
    properties.getApi().setKimi(new KimiApiProperties());
    properties.getApi().getKimi().setBaseUrl("https://api.moonshot.cn/v1/chat/completions");
    properties.getApi().getKimi().setApiKey("test-key");
    properties.getApi().getKimi().setModel("moonshot-v1-8k");

    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "{\\"resolvedName\\":\\"周杰伦\\",\\"summary\\":\\"周杰伦是华语流行乐代表人物。\\",\\"tags\\":[\\"歌手\\",\\"音乐制作人\\"],\\"evidenceUrls\\":[\\"https://example.com/a\\"]}"
                          }
                        }
                      ]
                    }
                    """, MediaType.APPLICATION_JSON));

    KimiSummaryGenerationClient client = new KimiSummaryGenerationClient(restTemplate, properties, new ObjectMapper());

    ResolvedPersonProfile profile = client.summarizePerson("周杰伦", List.of(
            new PageContent().setUrl("https://example.com/a").setContent("正文A")
    ));

    assertThat(profile.getResolvedName()).isEqualTo("周杰伦");
    assertThat(profile.getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。");
    assertThat(profile.getTags()).containsExactly("歌手", "音乐制作人");
}
```

- [ ] **步骤 2：补写 Kimi 客户端失败测试**

```java
@Test
void shouldThrowControlledExceptionWhenKimiReturnsInvalidJson() {
    ApiProperties properties = new ApiProperties();
    properties.getApi().getSummary().setEnabled(true);
    properties.getApi().getSummary().setProvider("kimi");
    properties.getApi().setKimi(new KimiApiProperties());
    properties.getApi().getKimi().setBaseUrl("https://api.moonshot.cn/v1/chat/completions");
    properties.getApi().getKimi().setApiKey("test-key");
    properties.getApi().getKimi().setModel("moonshot-v1-8k");

    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://api.moonshot.cn/v1/chat/completions"))
            .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}", MediaType.APPLICATION_JSON));

    KimiSummaryGenerationClient client = new KimiSummaryGenerationClient(restTemplate, properties, new ObjectMapper());

    assertThatThrownBy(() -> client.summarizePerson("周杰伦", List.of(new PageContent().setUrl("https://example.com/a").setContent("正文A"))))
            .isInstanceOf(ApiCallException.class)
            .hasMessageContaining("INVALID_RESPONSE");
}
```

- [ ] **步骤 3：运行测试，确认失败**

运行：`mvn -Dtest=KimiSummaryGenerationClientTest test`

预期：FAIL，报错为 `KimiSummaryGenerationClient`、`KimiApiProperties` 或 `setKimi` 不存在。

- [ ] **步骤 4：最小实现配置模型与条件装配**

```java
// src/main/java/com/example/face2info/config/KimiApiProperties.java
@Getter
@Setter
public class KimiApiProperties {
    private String baseUrl = "https://api.moonshot.cn/v1/chat/completions";
    private String apiKey;
    private String model = "moonshot-v1-8k";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 2;
    private long backoffInitialMs = 300L;
    private String systemPrompt = "你是一个人物信息抽取助手，只能输出JSON。";
}
```

```java
// src/main/java/com/example/face2info/config/ApiProperties.java
private KimiApiProperties kimi = new KimiApiProperties();

public KimiApiProperties getKimi() {
    return kimi;
}

public void setKimi(KimiApiProperties kimi) {
    this.kimi = kimi;
}
```

```java
// src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java
@Component
@ConditionalOnProperty(prefix = "face2info.api.summary", name = "provider", havingValue = "noop", matchIfMissing = true)
public class NoopSummaryGenerationClient implements SummaryGenerationClient {
```

```java
// src/main/java/com/example/face2info/config/RestTemplateConfig.java
int connectTimeout = max(
        properties.getApi().getSerp().getConnectTimeoutMs(),
        properties.getApi().getNews().getConnectTimeoutMs(),
        properties.getApi().getJina().getConnectTimeoutMs(),
        properties.getApi().getSummary().getConnectTimeoutMs(),
        properties.getApi().getKimi().getConnectTimeoutMs()
);
int readTimeout = max(
        properties.getApi().getSerp().getReadTimeoutMs(),
        properties.getApi().getNews().getReadTimeoutMs(),
        properties.getApi().getJina().getReadTimeoutMs(),
        properties.getApi().getSummary().getReadTimeoutMs(),
        properties.getApi().getKimi().getReadTimeoutMs()
);
```

- [ ] **步骤 5：最小实现 Kimi 客户端**

```java
@Slf4j
@Component
@ConditionalOnProperty(prefix = "face2info.api.summary", name = "provider", havingValue = "kimi")
public class KimiSummaryGenerationClient implements SummaryGenerationClient {

    private final RestTemplate restTemplate;
    private final ApiProperties properties;
    private final ObjectMapper objectMapper;

    public KimiSummaryGenerationClient(RestTemplate restTemplate, ApiProperties properties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResolvedPersonProfile summarizePerson(String fallbackName, List<PageContent> pages) {
        KimiApiProperties kimi = properties.getApi().getKimi();
        if (!StringUtils.hasText(kimi.getApiKey()) || !StringUtils.hasText(kimi.getBaseUrl()) || !StringUtils.hasText(kimi.getModel())) {
            throw new ApiCallException("CONFIG_MISSING: kimi config is incomplete");
        }
        return RetryUtils.execute("Kimi summarize", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(kimi.getApiKey());
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of(
                    "model", kimi.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", kimi.getSystemPrompt()),
                            Map.of("role", "user", "content", buildUserPrompt(fallbackName, pages))
                    )
            );
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    kimi.getBaseUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
            return parseProfile(fallbackName, pages, response.getBody());
        });
    }
}
```

- [ ] **步骤 6：运行客户端测试，确认通过**

运行：`mvn -Dtest=KimiSummaryGenerationClientTest test`

预期：PASS

- [ ] **步骤 7：提交**

```bash
git add src/main/java/com/example/face2info/config/KimiApiProperties.java src/main/java/com/example/face2info/config/ApiProperties.java src/main/java/com/example/face2info/config/RestTemplateConfig.java src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java
git commit -m "feat(client): 新增Kimi摘要provider与条件装配"
```

### 任务 3：先写聚合降级测试，再实现 Kimi 失败 warning

**文件：**
- 修改：`src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- 修改：`src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`

- [ ] **步骤 1：先写 Kimi 成功映射测试**

```java
@Test
void shouldMapSummaryAndTagsIntoAggregationResult() throws Exception {
    SerpApiClient serpApiClient = mock(SerpApiClient.class);
    NewsApiClient newsApiClient = mock(NewsApiClient.class);
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("正文A"));
    when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
    when(summaryGenerationClient.summarizePerson("周杰伦", pages)).thenReturn(new ResolvedPersonProfile()
            .setResolvedName("周杰伦")
            .setSummary("周杰伦是华语流行乐代表人物。")
            .setTags(List.of("歌手", "音乐制作人"))
            .setEvidenceUrls(List.of("https://example.com/a")));
    when(serpApiClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"后备简介\"}}")));
    when(serpApiClient.googleSearch("周杰伦 鎶栭煶")).thenReturn(new SerpApiResponse().setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(serpApiClient.googleSearch("周杰伦 寰崥")).thenReturn(new SerpApiResponse().setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(newsApiClient.searchNews("周杰伦")).thenReturn(new NewsApiResponse().setRoot(objectMapper.readTree("{\"articles\":[]}")));

    AggregationResult result = new InformationAggregationServiceImpl(
            serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
    ).aggregate(new RecognitionEvidence().setSeedQueries(List.of("周杰伦")).setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getPerson().getSummary()).isEqualTo("周杰伦是华语流行乐代表人物。");
    assertThat(result.getPerson().getTags()).containsExactly("歌手", "音乐制作人");
    assertThat(result.getWarnings()).isEmpty();
}
```

- [ ] **步骤 2：再写 Kimi 失败降级测试**

```java
@Test
void shouldAppendWarningWhenSummaryGenerationFails() throws Exception {
    SerpApiClient serpApiClient = mock(SerpApiClient.class);
    NewsApiClient newsApiClient = mock(NewsApiClient.class);
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("正文A"));
    when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
    when(summaryGenerationClient.summarizePerson("周杰伦", pages)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
    when(serpApiClient.googleSearch("周杰伦")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"后备简介\"}}")));
    when(serpApiClient.googleSearch("周杰伦 鎶栭煶")).thenReturn(new SerpApiResponse().setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(serpApiClient.googleSearch("周杰伦 寰崥")).thenReturn(new SerpApiResponse().setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(newsApiClient.searchNews("周杰伦")).thenReturn(new NewsApiResponse().setRoot(objectMapper.readTree("{\"articles\":[]}")));

    AggregationResult result = new InformationAggregationServiceImpl(
            serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
    ).aggregate(new RecognitionEvidence().setSeedQueries(List.of("周杰伦")).setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getPerson().getDescription()).isEqualTo("后备简介");
    assertThat(result.getPerson().getSummary()).isNull();
    assertThat(result.getPerson().getTags()).isEmpty();
    assertThat(result.getWarnings()).containsExactly("正文智能处理暂时不可用");
}
```

- [ ] **步骤 3：运行测试，确认失败**

运行：`mvn -Dtest=InformationAggregationServiceImplTest test`

预期：FAIL，报错集中在 `getSummary`、`getTags`、`getWarnings` 断言不满足。

- [ ] **步骤 4：最小实现聚合成功路径与降级 warning**

```java
// src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
ResolvedPersonProfile profile = resolveProfileFromEvidence(
        evidence.getWebEvidences(),
        firstSeedQuery(evidence),
        result.getWarnings()
);

PersonAggregate person = joinTask("实体信息", personFuture, new PersonAggregate().setName(resolvedName), result.getErrors());
person.setName(resolvedName);
person.setDescription(cleanDescription(firstNonBlank(profile.getSummary(), person.getDescription())));
person.setSummary(cleanDescription(profile.getSummary()));
person.setTags(profile.getTags() == null ? List.of() : profile.getTags());
person.setEvidenceUrls(profile.getEvidenceUrls());
```

```java
// src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences, String fallbackName, List<String> warnings) {
    List<String> urls = selectTopUrls(evidences);
    if (urls.isEmpty()) {
        return new ResolvedPersonProfile().setResolvedName(fallbackName);
    }
    List<PageContent> pages = jinaReaderClient.readPages(urls);
    try {
        ResolvedPersonProfile profile = summaryGenerationClient.summarizePerson(fallbackName, pages);
        if (profile == null) {
            return new ResolvedPersonProfile().setResolvedName(fallbackName).setEvidenceUrls(urls);
        }
        if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
            profile.setEvidenceUrls(urls);
        }
        return profile;
    } catch (RuntimeException ex) {
        log.error("Kimi summary generation failed, fallbackName={}, urlCount={}, category={}",
                fallbackName, urls.size(), classifySummaryFailure(ex), ex);
        warnings.add("正文智能处理暂时不可用");
        return new ResolvedPersonProfile()
                .setResolvedName(fallbackName)
                .setEvidenceUrls(urls);
    }
}
```

```java
// src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
try {
    ResolvedPersonProfile profile = summaryGenerationClient.summarizePerson(fallbackName, pages);
    if (profile == null) {
        return new ResolvedPersonProfile().setResolvedName(fallbackName).setEvidenceUrls(urls);
    }
    if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
        profile.setEvidenceUrls(urls);
    }
    return profile;
} catch (RuntimeException ex) {
    log.error("Kimi summary generation failed, fallbackName={}, urlCount={}, category={}",
            fallbackName, urls.size(), classifySummaryFailure(ex), ex);
    warnings.add("正文智能处理暂时不可用");
    return new ResolvedPersonProfile()
            .setResolvedName(fallbackName)
            .setEvidenceUrls(urls);
}
```

```java
// src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
private String classifySummaryFailure(RuntimeException ex) {
    String message = ex.getMessage() == null ? "" : ex.getMessage();
    if (message.contains("CONFIG_MISSING")) {
        return "CONFIG_MISSING";
    }
    if (message.contains("INVALID_RESPONSE")) {
        return "INVALID_RESPONSE";
    }
    if (message.contains("empty")) {
        return "EMPTY_RESPONSE";
    }
    return "HTTP_ERROR";
}
```

- [ ] **步骤 5：运行聚合测试**

运行：`mvn -Dtest=InformationAggregationServiceImplTest test`

预期：PASS

- [ ] **步骤 6：提交**

```bash
git add src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "feat(service): 接入Kimi增强结果并补充降级warning"
```

### 任务 4：控制层、配置文档和全量校验

**文件：**
- 修改：`src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
- 修改：`src/main/resources/application.yml`
- 修改：`README.md`

- [ ] **步骤 1：先写控制层 JSON 失败测试**

```java
@Test
void shouldExposeSummaryTagsAndWarningsInResponseJson() throws Exception {
    when(face2InfoService.process(any())).thenReturn(new FaceInfoResponse()
            .setStatus("partial")
            .setWarnings(java.util.List.of("正文智能处理暂时不可用"))
            .setPerson(new com.example.face2info.entity.response.PersonInfo()
                    .setName("周杰伦")
                    .setSummary("周杰伦是华语流行乐代表人物。")
                    .setTags(java.util.List.of("歌手", "音乐制作人"))));

    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    mockMvc.perform(multipart("/api/face2info").file(image))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("partial"))
            .andExpect(jsonPath("$.person.summary").value("周杰伦是华语流行乐代表人物。"))
            .andExpect(jsonPath("$.person.tags[0]").value("歌手"))
            .andExpect(jsonPath("$.warnings[0]").value("正文智能处理暂时不可用"));
}
```

- [ ] **步骤 2：运行控制层测试，确认失败**

运行：`mvn -Dtest=FaceInfoControllerTest#shouldExposeSummaryTagsAndWarningsInResponseJson test`

预期：FAIL，如果任务 1 已完成则这里大概率直接 PASS；若失败，应为序列化字段未映射。

- [ ] **步骤 3：补充配置与文档**

```yaml
# src/main/resources/application.yml
face2info:
  api:
    summary:
      enabled: false
      provider: noop
    kimi:
      base-url: ${KIMI_API_BASE_URL:https://api.moonshot.cn/v1/chat/completions}
      api-key: ${KIMI_API_KEY:}
      model: ${KIMI_MODEL:moonshot-v1-8k}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
      max-retries: 2
      backoff-initial-ms: 300
      system-prompt: ${KIMI_SYSTEM_PROMPT:你是一个人物信息抽取助手，只能输出JSON。}
```

```markdown
<!-- README.md -->
## Kimi 正文增强

- 通过 `face2info.api.summary.provider=kimi` 启用 Kimi 增强。
- 需要配置环境变量：`KIMI_API_KEY`。
- 成功时会在响应中补充 `person.summary` 与 `person.tags`。
- 调用失败时接口继续返回原始聚合结果，并在 `warnings` 中返回 `正文智能处理暂时不可用`。
```

- [ ] **步骤 4：运行控制层测试**

运行：`mvn -Dtest=FaceInfoControllerTest test`

预期：PASS

- [ ] **步骤 5：运行完整校验**

运行：`mvn clean verify`

预期：PASS

- [ ] **步骤 6：提交**

```bash
git add src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java src/main/resources/application.yml README.md
git commit -m "docs(config): 补充Kimi配置说明与响应字段验证"
```
