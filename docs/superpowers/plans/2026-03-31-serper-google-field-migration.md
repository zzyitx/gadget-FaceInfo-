# Serper Google 字段迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Google Search 与 Google Lens 的结果消费逻辑切换到 `SerperAPI` 的 `organic` 字段，同时保持 Bing / Yandex 继续走现有 `SerpApiClient` 且行为不回归。

**Architecture:** 保留 `GoogleSearchClient` 作为 Google 接入边界，但服务层只消费 `SerperAPI` 的 `organic` 与可选 `knowledgeGraph`。`FaceRecognitionServiceImpl` 和 `InformationAggregationServiceImpl` 删除 Google 专用旧字段分支，不修改 `SerpApiClient` 的 Bing / Yandex 返回结构和测试。当前工作区已有未提交改动，执行时只在目标文件内做最小修改，不回退用户现有修改。

**Tech Stack:** Java 17, Spring Boot 3.3.5, Jackson, RestTemplate, JUnit 5, Mockito, MockRestServiceServer, Maven

---

### Task 1: 更新 GoogleSearchClient 的 Serper 响应测试

**Files:**
- Modify: `src/test/java/com/example/face2info/client/impl/GoogleSearchClientImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldReturnSerperOrganicResultsForGoogleSearch() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://google.serper.dev/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-API-KEY", "test-key"))
            .andExpect(content().json("""
                    {"q":"Lei Jun 抖音","hl":"zh-cn"}
                    """))
            .andRespond(withSuccess("""
                    {
                      "organic": [
                        {
                          "title": "雷军_抖音百科",
                          "link": "https://www.douyin.com/wiki/lei-jun",
                          "source": "抖音百科",
                          "snippet": "雷军，小米集团创始人。"
                        }
                      ]
                    }
                    """, MediaType.APPLICATION_JSON));

    GoogleSearchClientImpl client = new GoogleSearchClientImpl(restTemplate, new ObjectMapper(), createProperties());

    SerpApiResponse response = client.googleSearch("Lei%20Jun%20%E6%8A%96%E9%9F%B3");

    assertThat(response.getRoot().path("organic")).hasSize(1);
    assertThat(response.getRoot().path("organic").get(0).path("title").asText()).isEqualTo("雷军_抖音百科");
    server.verify();
}

@Test
void shouldReturnSerperOrganicResultsForGoogleLens() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://google.serper.dev/lens"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-API-KEY", "test-key"))
            .andExpect(content().json("""
                    {"url":"https://example.com/image.png","hl":"zh-cn"}
                    """))
            .andRespond(withSuccess("""
                    {
                      "organic": [
                        {
                          "title": "雷军",
                          "link": "https://commons.wikimedia.org/wiki/File:Lei_Jun.jpg",
                          "source": "Wikimedia.org",
                          "imageUrl": "https://upload.wikimedia.org/wikipedia/commons/0/0f/Lei_Jun.jpg",
                          "thumbnailUrl": "https://encrypted-tbn2.gstatic.com/images?q=tbn:test"
                        }
                      ]
                    }
                    """, MediaType.APPLICATION_JSON));

    GoogleSearchClientImpl client = new GoogleSearchClientImpl(restTemplate, new ObjectMapper(), createProperties());

    SerpApiResponse response = client.reverseImageSearchByUrl("https://example.com/image.png");

    assertThat(response.getRoot().path("organic")).hasSize(1);
    assertThat(response.getRoot().path("organic").get(0).path("imageUrl").asText())
            .isEqualTo("https://upload.wikimedia.org/wikipedia/commons/0/0f/Lei_Jun.jpg");
    server.verify();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=GoogleSearchClientImplTest test`
Expected: FAIL，现有断言仍围绕 `organic_results` 或 `visual_matches`

- [ ] **Step 3: Write minimal implementation**

```java
private SerpApiResponse execute(String name, String url, Map<String, Object> payload) {
    GoogleSearchProperties google = google();
    return RetryUtils.execute(name, google.getMaxRetries(), google.getBackoffInitialMs(), () -> {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-KEY", apiKey());
        ResponseEntity<String> response = restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), String.class);
        JsonNode root = objectMapper.readTree(response.getBody());
        return new SerpApiResponse().setRoot(root);
    });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=GoogleSearchClientImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/client/impl/GoogleSearchClientImplTest.java src/main/java/com/example/face2info/client/impl/GoogleSearchClientImpl.java
git commit -m "test(client): 调整 Google Serper organic 响应断言"
```

### Task 2: 用 Serper organic 重写 Google Lens 识别测试

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldExtractSeedQueriesAndImageMatchesFromSerperOrganic() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
    when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "organic": [
                        {
                          "title": "File:Lei Jun.jpg - Wikimedia Commons",
                          "source": "Wikimedia.org",
                          "link": "https://commons.wikimedia.org/wiki/File:Lei_Jun.jpg",
                          "imageUrl": "https://upload.wikimedia.org/wikipedia/commons/0/0f/Lei_Jun.jpg",
                          "thumbnailUrl": "https://encrypted-tbn2.gstatic.com/images?q=tbn:test"
                        },
                        {
                          "title": "全国人大代表、小米集团董事长雷军",
                          "source": "新浪网",
                          "link": "https://finance.sina.com.cn/example",
                          "snippet": "雷军相关报道"
                        }
                      ]
                    }
                    """)));
    when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"image_results\": []}")));
    when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "similar")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"image_results\": []}")));
    when(serpApiClient.reverseImageSearchByUrlBing(PREVIEW_URL)).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"image_results\": []}")));

    FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient);

    RecognitionEvidence result = service.recognize(image);

    assertThat(result.getSeedQueries()).isNotEmpty();
    assertThat(result.getImageMatches()).hasSize(2);
    assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl)
            .contains("https://commons.wikimedia.org/wiki/File:Lei_Jun.jpg", "https://finance.sina.com.cn/example");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest#shouldExtractSeedQueriesAndImageMatchesFromSerperOrganic test`
Expected: FAIL，现有实现仍从 `visual_matches`、`organic_results`、`image_results` 读取 Google 结果

- [ ] **Step 3: Write minimal implementation**

```java
private List<String> extractSeedQueries(SerpApiResponse... responses) {
    Set<String> queries = new LinkedHashSet<>();
    for (SerpApiResponse response : responses) {
        if (response == null || response.getRoot() == null) {
            continue;
        }
        collectSeedQuery(queries, response.getRoot().path("knowledgeGraph").path("title").asText(null));
        collectSeedQueriesFromArray(queries, response.getRoot().path("organic"));
        if (queries.size() >= MAX_SEED_QUERIES) {
            break;
        }
    }
    return new ArrayList<>(queries).subList(0, Math.min(queries.size(), MAX_SEED_QUERIES));
}

private List<WebEvidence> extractWebEvidence(JsonNode root, String sourceEngine) {
    List<WebEvidence> evidences = new ArrayList<>();
    collectWebEvidence(evidences, root.path("organic"), sourceEngine);
    return evidences;
}

private List<ImageMatch> extractImageMatches(JsonNode root) {
    JsonNode organic = root.path("organic");
    List<ImageMatch> matches = new ArrayList<>();
    if (!organic.isArray()) {
        return matches;
    }
    for (JsonNode node : organic) {
        if (matches.size() >= MAX_IMAGE_MATCHES) {
            break;
        }
        String title = node.path("title").asText(null);
        String link = node.path("link").asText(null);
        String source = node.path("source").asText(null);
        if (!StringUtils.hasText(title) && !StringUtils.hasText(link)) {
            continue;
        }
        matches.add(new ImageMatch()
                .setPosition(matches.size() + 1)
                .setTitle(title)
                .setLink(link)
                .setSource(source));
    }
    return matches;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest test`
Expected: PASS，且现有 Bing / Yandex 相关断言保持通过

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java
git commit -m "refactor(service): 切换 Google Lens 识别到 Serper organic"
```

### Task 3: 用 Serper organic 重写 Google 搜索聚合测试

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldUseSerperOrganicSnippetAsFallbackDescription() throws Exception {
    GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);
    SerpApiClient serpApiClient = mock(SerpApiClient.class);
    NewsApiClient newsApiClient = mock(NewsApiClient.class);
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setContent("body"));
    when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
    when(summaryGenerationClient.summarizePerson("Lei Jun", pages)).thenThrow(new RuntimeException("INVALID_RESPONSE"));
    when(googleSearchClient.googleSearch("LeiJun")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "organic": [
                        {
                          "title": "雷军_抖音百科",
                          "link": "https://www.douyin.com/wiki/lei-jun",
                          "source": "抖音百科",
                          "snippet": "雷军，小米集团创始人、董事长。"
                        }
                      ]
                    }
                    """)));

    AggregationResult result = new InformationAggregationServiceImpl(
            googleSearchClient, serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor
    ).aggregate(new RecognitionEvidence()
            .setSeedQueries(List.of("Lei Jun"))
            .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getPerson().getSummary()).isNull();
    assertThat(result.getPerson().getDescription()).isEqualTo("雷军，小米集团创始人、董事长。 (由 SerpAPI 聚合)");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldUseSerperOrganicSnippetAsFallbackDescription test`
Expected: FAIL，现有实现仍从 `organic_results` 提取 Google 搜索摘要

- [ ] **Step 3: Write minimal implementation**

```java
private PersonAggregate collectPersonInfo(String name) {
    String normalizedName = normalizeName(name);
    SerpApiResponse response = googleSearchClient.googleSearch(normalizedName);
    if (response == null || response.getRoot() == null) {
        return new PersonAggregate().setName(name);
    }
    JsonNode root = response.getRoot();
    JsonNode knowledgeGraph = firstPresent(root, "knowledgeGraph", "knowledge_graph");
    PersonAggregate aggregate = new PersonAggregate().setName(name);
    if (knowledgeGraph != null && !knowledgeGraph.isMissingNode() && !knowledgeGraph.isNull()) {
        aggregate.setDescription(knowledgeGraph.path("description").asText(null));
        aggregate.setOfficialWebsite(firstText(knowledgeGraph, "website", "official_website"));
        aggregate.setWikipedia(firstText(knowledgeGraph, "wikipedia", "wikipedia_url"));
    }
    if (!StringUtils.hasText(aggregate.getDescription())) {
        JsonNode organic = root.path("organic");
        if (organic.isArray()) {
            for (JsonNode item : organic) {
                String snippet = item.path("snippet").asText(null);
                if (StringUtils.hasText(snippet)) {
                    aggregate.setDescription(snippet);
                    break;
                }
            }
        }
    }
    return aggregate;
}

private JsonNode firstPresent(JsonNode root, String... fields) {
    for (String field : fields) {
        JsonNode node = root.path(field);
        if (!node.isMissingNode() && !node.isNull()) {
            return node;
        }
    }
    return null;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=InformationAggregationServiceImplTest test`
Expected: PASS，且 Google 搜索相关测试不再依赖 `organic_results`

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
git commit -m "refactor(service): 切换 Google 搜索聚合到 Serper organic"
```

### Task 4: 清理 Google 链路中的旧字段依赖并做回归验证

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java`
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- Test: `src/test/java/com/example/face2info/client/impl/SerpApiClientImplTest.java`
- Test: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
- Test: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldNotRequireGoogleOrganicResultsOrVisualMatches() throws Exception {
    when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "organic": [
                        { "title": "Lei Jun", "link": "https://example.com/lei", "source": "Example" }
                      ]
                    }
                    """)));

    FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(googleSearchClient, serpApiClient, nameExtractor, tmpfilesClient);

    RecognitionEvidence result = service.recognize(new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1}));

    assertThat(result.getImageMatches()).hasSize(1);
    assertThat(result.getErrors()).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest#shouldNotRequireGoogleOrganicResultsOrVisualMatches test`
Expected: FAIL，说明 Google 链路仍残留旧字段依赖

- [ ] **Step 3: Write minimal implementation**

```java
// FaceRecognitionServiceImpl
private List<WebEvidence> extractWebEvidence(JsonNode root, String sourceEngine) {
    List<WebEvidence> evidences = new ArrayList<>();
    if ("google_lens".equals(sourceEngine)) {
        collectWebEvidence(evidences, root.path("organic"), sourceEngine);
        return evidences;
    }
    collectWebEvidence(evidences, root.path("visual_matches"), sourceEngine);
    collectWebEvidence(evidences, root.path("image_results"), sourceEngine);
    collectWebEvidence(evidences, root.path("organic_results"), sourceEngine);
    return evidences;
}

// InformationAggregationServiceImpl
private List<SocialAccount> parseSocialResults(String platform, SerpApiResponse response) {
    List<SocialAccount> accounts = new ArrayList<>();
    if (response == null || response.getRoot() == null) {
        return accounts;
    }
    JsonNode organic = response.getRoot().path("organic");
    if (!organic.isArray()) {
        return accounts;
    }
    for (JsonNode item : organic) {
        String link = item.path("link").asText("");
        if (!isPlatformLink(platform, link)) {
            continue;
        }
        accounts.add(new SocialAccount()
                .setPlatform(platform)
                .setUrl(link)
                .setUsername(extractUsername(item.path("title").asText(null))));
    }
    return accounts;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=GoogleSearchClientImplTest,FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest,SerpApiClientImplTest test`
Expected: PASS，证明 Google 迁移完成且 Bing / Yandex 未受影响

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/test/java/com/example/face2info/client/impl/GoogleSearchClientImplTest.java src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "test(service): 清理 Google 旧字段依赖并完成回归"
```

### Task 5: 运行完整验证

**Files:**
- No file changes

- [ ] **Step 1: Run targeted verification**

Run: `mvn -Dtest=GoogleSearchClientImplTest,FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest,SerpApiClientImplTest test`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run full verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS

- [ ] **Step 3: Inspect worktree before final handoff**

Run: `git status --short`
Expected: 只包含本次迁移相关文件；不回退用户既有改动

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/face2info/client/impl/GoogleSearchClientImpl.java src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/test/java/com/example/face2info/client/impl/GoogleSearchClientImplTest.java src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "feat(service): 完成 Google Serper 字段迁移"
```
