# Google Serper Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Google 搜索与 Google Lens 调用从 `SerpAPI` 切换到 `Serper`，同时保持现有 Yandex/Bing 能力与上层聚合输出稳定。

**Architecture:** 新增独立 `GoogleSearchClient` 承接 Google search/lens；`SerpApiClient` 只保留 Yandex/Bing；service 层通过依赖拆分选择不同来源。新 Google client 继续返回 `SerpApiResponse` 以降低对聚合解析链路的影响。

**Tech Stack:** Java 17, Spring Boot 3.3.5, RestTemplate, Jackson, JUnit 5, MockRestServiceServer, Mockito

---

### Task 1: 为 Google client 写失败测试

**Files:**
- Create: `src/test/java/com/example/face2info/client/impl/GoogleSearchClientImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldPostGoogleSearchRequestToSerper() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://google.serper.dev/search"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-API-KEY", "test-key"))
            .andExpect(content().json("""
                    {"q":"Lei Jun 抖音","hl":"zh-cn"}
                    """))
            .andRespond(withSuccess("{\"organic_results\":[]}", MediaType.APPLICATION_JSON));

    GoogleSearchClientImpl client = new GoogleSearchClientImpl(restTemplate, new ObjectMapper(), createProperties());

    client.googleSearch("Lei%20Jun%20%E6%8A%96%E9%9F%B3");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=GoogleSearchClientImplTest test`
Expected: FAIL，提示 `GoogleSearchClientImpl` 或 `GoogleSearchProperties` 不存在

- [ ] **Step 3: Write minimal implementation**

```java
public class GoogleSearchClientImpl implements GoogleSearchClient {
    @Override
    public SerpApiResponse googleSearch(String query) {
        throw new UnsupportedOperationException("not implemented");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=GoogleSearchClientImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/client/impl/GoogleSearchClientImplTest.java
git commit -m "test(client): 补充 Google Serper 客户端测试"
```

### Task 2: 迁移 service 依赖测试

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
private final GoogleSearchClient googleSearchClient = mock(GoogleSearchClient.class);

when(googleSearchClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse().setRoot(...));
when(googleSearchClient.googleSearch("JayChou")).thenReturn(new SerpApiResponse().setRoot(...));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest test`
Expected: FAIL，构造函数参数或 mock 依赖不匹配

- [ ] **Step 3: Write minimal implementation**

```java
public FaceRecognitionServiceImpl(GoogleSearchClient googleSearchClient,
                                  SerpApiClient serpApiClient,
                                  NameExtractor nameExtractor,
                                  TmpfilesClient tmpfilesClient) {
    ...
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "test(service): 迁移 Google client 依赖测试"
```

### Task 3: 实现 GoogleSearchClient 与配置

**Files:**
- Create: `src/main/java/com/example/face2info/client/GoogleSearchClient.java`
- Create: `src/main/java/com/example/face2info/client/impl/GoogleSearchClientImpl.java`
- Create: `src/main/java/com/example/face2info/config/GoogleSearchProperties.java`
- Modify: `src/main/java/com/example/face2info/config/ApiProperties.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-git.yml`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldPostGoogleLensRequestToSerper() {
    ...
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=GoogleSearchClientImplTest test`
Expected: FAIL，POST body、header 或 URL 不匹配

- [ ] **Step 3: Write minimal implementation**

```java
Map<String, Object> payload = Map.of("q", normalizeQuery(query), "hl", googleProperties.getHl());
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("X-API-KEY", apiKey());
ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=GoogleSearchClientImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/client/GoogleSearchClient.java src/main/java/com/example/face2info/client/impl/GoogleSearchClientImpl.java src/main/java/com/example/face2info/config/GoogleSearchProperties.java src/main/java/com/example/face2info/config/ApiProperties.java src/main/resources/application.yml src/main/resources/application-git.yml
git commit -m "feat(client): 新增 Google Serper 客户端与配置"
```

### Task 4: 收敛旧 SerpApiClient 到 Yandex/Bing

**Files:**
- Modify: `src/main/java/com/example/face2info/client/SerpApiClient.java`
- Modify: `src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java`
- Modify: `src/test/java/com/example/face2info/client/impl/SerpApiClientImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldUseBingReverseImageEngineAndImageUrlParameter() {
    ...
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SerpApiClientImplTest test`
Expected: FAIL，旧 Google 断言已移除但实现未同步

- [ ] **Step 3: Write minimal implementation**

```java
public interface SerpApiClient {
    SerpApiResponse reverseImageSearchByUrlYandex(String imageUrl, String tab);
    SerpApiResponse reverseImageSearchByUrlBing(String imageUrl);
    SerpApiResponse searchBingImages(String query);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SerpApiClientImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/client/SerpApiClient.java src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java src/test/java/com/example/face2info/client/impl/SerpApiClientImplTest.java
git commit -m "refactor(client): 收敛 SerpApiClient 到 Yandex 与 Bing"
```

### Task 5: 更新文档并完成验证

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Write the failing test**

```text
无代码测试；此任务以构建验证替代。
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn clean verify`
Expected: 若代码或文档配置不完整则失败

- [ ] **Step 3: Write minimal implementation**

```markdown
- `Serper`：用于 Google Lens 反向搜图和 Google 搜索
- `SerpAPI`：当前仅用于 Yandex/Bing 图像搜索
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn clean verify`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: 更新 Google Serper 客户端说明"
```
