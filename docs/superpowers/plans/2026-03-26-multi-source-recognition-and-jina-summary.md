# Multi-Source Recognition And Jina Summary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a multi-source face evidence pipeline that uses Google Lens, Bing Images, and Yandex via SerpApi, fetches up to 20 page bodies via Jina, resolves the person through an abstract summary client, and then enriches the final response with Google, NewsAPI, and social results.

**Architecture:** Keep the existing `client -> service -> response` layering. Move face recognition from “return a single guessed name” to “return recognition evidence”, then let `InformationAggregationService` orchestrate page selection, Jina page reading, summary generation, and structured enrichment with downgrade paths. Preserve the external response shape and keep vendor-specific HTTP logic inside `client`.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Maven, Jackson, RestTemplate, Spring Test, Mockito, JUnit 5.

---

## File Structure

**Create:**
- `src/main/java/com/example/face2info/client/JinaReaderClient.java`
- `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
- `src/main/java/com/example/face2info/client/impl/JinaReaderClientImpl.java`
- `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
- `src/main/java/com/example/face2info/config/JinaApiProperties.java`
- `src/main/java/com/example/face2info/config/SummaryApiProperties.java`
- `src/main/java/com/example/face2info/entity/internal/RecognitionEvidence.java`
- `src/main/java/com/example/face2info/entity/internal/WebEvidence.java`
- `src/main/java/com/example/face2info/entity/internal/PageContent.java`
- `src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`

**Modify:**
- `src/main/java/com/example/face2info/client/SerpApiClient.java`
- `src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java`
- `src/main/java/com/example/face2info/config/ApiProperties.java`
- `src/main/java/com/example/face2info/config/SerpApiProperties.java`
- `src/main/resources/application.yml`
- `src/main/java/com/example/face2info/service/FaceRecognitionService.java`
- `src/main/java/com/example/face2info/service/InformationAggregationService.java`
- `src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java`
- `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- `src/main/java/com/example/face2info/entity/internal/AggregationResult.java`
- `src/main/java/com/example/face2info/entity/internal/PersonAggregate.java`

**Test:**
- `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
- `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

### Task 1: Add Config Models For Jina And Summary

**Files:**
- Create: `src/main/java/com/example/face2info/config/JinaApiProperties.java`
- Create: `src/main/java/com/example/face2info/config/SummaryApiProperties.java`
- Modify: `src/main/java/com/example/face2info/config/ApiProperties.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add a constructor wiring assertion to `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`:

```java
@Test
void shouldCreateServiceWithSummaryAndJinaDependencies() {
    ThreadPoolTaskExecutor executor = executor();
    try {
        InformationAggregationServiceImpl service = new InformationAggregationServiceImpl(
                mock(SerpApiClient.class),
                mock(NewsApiClient.class),
                mock(JinaReaderClient.class),
                mock(SummaryGenerationClient.class),
                executor
        );

        assertThat(service).isNotNull();
    } finally {
        executor.shutdown();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldCreateServiceWithSummaryAndJinaDependencies test`

Expected: FAIL with constructor mismatch because `InformationAggregationServiceImpl` does not yet accept `JinaReaderClient` and `SummaryGenerationClient`.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/example/face2info/config/JinaApiProperties.java`:

```java
package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JinaApiProperties {

    private String baseUrl = "https://r.jina.ai/http://";
    private String apiKey;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 2;
    private int backoffInitialMs = 300;
}
```

Create `src/main/java/com/example/face2info/config/SummaryApiProperties.java`:

```java
package com.example.face2info.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SummaryApiProperties {

    private boolean enabled = false;
    private String provider = "noop";
    private String baseUrl;
    private String apiKey;
    private String model;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
}
```

Update `src/main/java/com/example/face2info/config/ApiProperties.java` inside `ApiProperties.Api`:

```java
private SerpApiProperties serp = new SerpApiProperties();
private NewsApiProperties news = new NewsApiProperties();
private JinaApiProperties jina = new JinaApiProperties();
private SummaryApiProperties summary = new SummaryApiProperties();
private Proxy proxy = new Proxy();
```

Update `src/main/resources/application.yml`:

```yml
    jina:
      base-url: https://r.jina.ai/http://
      api-key: ${JINA_API_KEY:}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
      max-retries: 2
      backoff-initial-ms: 300
    summary:
      enabled: false
      provider: noop
      base-url: ${SUMMARY_API_BASE_URL:}
      api-key: ${SUMMARY_API_KEY:}
      model: ${SUMMARY_MODEL:}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldCreateServiceWithSummaryAndJinaDependencies test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/config/JinaApiProperties.java src/main/java/com/example/face2info/config/SummaryApiProperties.java src/main/java/com/example/face2info/config/ApiProperties.java src/main/resources/application.yml src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "feat(config): 新增jina与摘要配置模型"
```

### Task 2: Extend SerpApiClient For Bing And Yandex

**Files:**
- Modify: `src/main/java/com/example/face2info/client/SerpApiClient.java`
- Modify: `src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java`
- Modify: `src/main/java/com/example/face2info/config/SerpApiProperties.java`
- Test: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add this test to `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`:

```java
@Test
void shouldUseBingAndYandexSourcesWhenCollectingEvidence() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
    when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "knowledge_graph": { "title": "Jay Chou" },
                      "visual_matches": [{ "title": "Jay Chou profile", "link": "https://example.com/a", "source": "Example" }]
                    }
                    """)));
    when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "image_results": [{ "title": "Jay Chou singer", "link": "https://example.com/b", "source": "Yandex" }]
                    }
                    """)));
    when(serpApiClient.searchBingImages("Jay Chou")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "image_results": [{ "title": "Jay Chou concert", "link": "https://example.com/c", "source": "Bing" }]
                    }
                    """)));

    FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

    RecognitionEvidence result = service.recognize(image);

    assertThat(result.getWebEvidences()).extracting(WebEvidence::getUrl)
            .contains("https://example.com/a", "https://example.com/b", "https://example.com/c");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest#shouldUseBingAndYandexSourcesWhenCollectingEvidence test`

Expected: FAIL because `SerpApiClient` and `FaceRecognitionServiceImpl` do not yet expose Bing/Yandex evidence methods or `RecognitionEvidence`.

- [ ] **Step 3: Write minimal implementation**

Update `src/main/java/com/example/face2info/client/SerpApiClient.java`:

```java
SerpApiResponse reverseImageSearchByUrl(String imageUrl);
SerpApiResponse reverseImageSearchByUrlYandex(String imageUrl, String tab);
SerpApiResponse searchBingImages(String query);
SerpApiResponse googleSearch(String query);
```

Update `src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java` by adding `reverseImageSearchByUrlYandex(...)` and `searchBingImages(...)`, each building a SerpApi URL with `engine=yandex_images` and `engine=bing_images` respectively.

Add to `src/main/java/com/example/face2info/config/SerpApiProperties.java`:

```java
private String bingMarket = "en-US";
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest#shouldUseBingAndYandexSourcesWhenCollectingEvidence test`

Expected: PASS or move the failure to missing `RecognitionEvidence`, which Task 3 addresses.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/client/SerpApiClient.java src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java src/main/java/com/example/face2info/config/SerpApiProperties.java src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java
git commit -m "feat(client): 扩展serpapi支持bing与yandex图像信源"
```

### Task 3: Replace Single Recognition Candidate With Recognition Evidence

**Files:**
- Create: `src/main/java/com/example/face2info/entity/internal/RecognitionEvidence.java`
- Create: `src/main/java/com/example/face2info/entity/internal/WebEvidence.java`
- Modify: `src/main/java/com/example/face2info/service/FaceRecognitionService.java`
- Modify: `src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java`
- Test: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add this test:

```java
@Test
void shouldDeduplicateEvidenceUrlsAcrossSources() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
    when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "knowledge_graph": { "title": "Jay Chou" },
                      "visual_matches": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Lens" }]
                    }
                    """)));
    when(serpApiClient.reverseImageSearchByUrlYandex(PREVIEW_URL, "about")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    {
                      "image_results": [{ "title": "Jay Chou", "link": "https://example.com/a", "source": "Yandex" }]
                    }
                    """)));
    when(serpApiClient.searchBingImages("Jay Chou")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"image_results\": []}")));

    FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

    RecognitionEvidence result = service.recognize(image);

    assertThat(result.getWebEvidences()).hasSize(1);
    assertThat(result.getSeedQueries()).contains("Jay Chou");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest#shouldDeduplicateEvidenceUrlsAcrossSources test`

Expected: FAIL because `RecognitionEvidence`, `getSeedQueries`, and URL deduplication do not exist yet.

- [ ] **Step 3: Write minimal implementation**

Create `src/main/java/com/example/face2info/entity/internal/WebEvidence.java` and `RecognitionEvidence.java` with plain getters/setters for:
- `WebEvidence`: `url`, `title`, `source`, `sourceEngine`, `snippet`
- `RecognitionEvidence`: `imageMatches`, `webEvidences`, `seedQueries`, `errors`

Update `src/main/java/com/example/face2info/service/FaceRecognitionService.java`:

```java
RecognitionEvidence recognize(MultipartFile image);
```

Update `src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java` to:
- call Lens first
- derive 1 to 3 seed queries from cleaned titles
- call Yandex `about`
- call Bing for each seed query
- collect `WebEvidence`
- deduplicate by URL
- return `RecognitionEvidence`

Use these helper signatures:

```java
private List<String> extractSeedQueries(SerpApiResponse lensResponse, SerpApiResponse yandexResponse)
private List<WebEvidence> extractWebEvidence(JsonNode root, String sourceEngine)
private List<WebEvidence> deduplicateWebEvidence(List<WebEvidence> evidences)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest#shouldDeduplicateEvidenceUrlsAcrossSources test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/entity/internal/RecognitionEvidence.java src/main/java/com/example/face2info/entity/internal/WebEvidence.java src/main/java/com/example/face2info/service/FaceRecognitionService.java src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java
git commit -m "feat(service): 输出多信源识别证据"
```

### Task 4: Add Jina Reader And Summary Abstraction

**Files:**
- Create: `src/main/java/com/example/face2info/client/JinaReaderClient.java`
- Create: `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
- Create: `src/main/java/com/example/face2info/client/impl/JinaReaderClientImpl.java`
- Create: `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
- Create: `src/main/java/com/example/face2info/entity/internal/PageContent.java`
- Create: `src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`
- Test: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add:

```java
@Test
void shouldUseJinaPagesAsSummaryInput() throws Exception {
    SerpApiClient serpApiClient = mock(SerpApiClient.class);
    NewsApiClient newsApiClient = mock(NewsApiClient.class);
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    List<PageContent> pages = List.of(
            new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"),
            new PageContent().setUrl("https://example.com/b").setTitle("B").setContent("Jay Chou released an album")
    );
    when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b"))).thenReturn(pages);
    when(summaryGenerationClient.summarizePerson("unknown", pages))
            .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("Jay Chou is a Mandopop singer."));

    InformationAggregationServiceImpl service =
            new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

    ResolvedPersonProfile profile = service.resolveProfileFromEvidence(List.of(
            new WebEvidence().setUrl("https://example.com/a"),
            new WebEvidence().setUrl("https://example.com/b")
    ));

    assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
    assertThat(profile.getSummary()).contains("Mandopop singer");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldUseJinaPagesAsSummaryInput test`

Expected: FAIL because the clients, models, and `resolveProfileFromEvidence` method do not exist.

- [ ] **Step 3: Write minimal implementation**

Create:
- `src/main/java/com/example/face2info/entity/internal/PageContent.java`
- `src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`
- `src/main/java/com/example/face2info/client/JinaReaderClient.java`
- `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
- `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`

With these signatures:

```java
public interface JinaReaderClient {
    List<PageContent> readPages(List<String> urls);
}

public interface SummaryGenerationClient {
    ResolvedPersonProfile summarizePerson(String fallbackName, List<PageContent> pages);
}
```

Create `src/main/java/com/example/face2info/client/impl/JinaReaderClientImpl.java` with a minimal RestTemplate-backed `readPages` implementation that iterates over URLs and returns `PageContent` with raw body text.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldUseJinaPagesAsSummaryInput test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/client/JinaReaderClient.java src/main/java/com/example/face2info/client/SummaryGenerationClient.java src/main/java/com/example/face2info/client/impl/JinaReaderClientImpl.java src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java src/main/java/com/example/face2info/entity/internal/PageContent.java src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "feat(client): 新增jina正文读取与摘要抽象"
```

### Task 5: Rebuild InformationAggregationService Around Evidence Resolution

**Files:**
- Modify: `src/main/java/com/example/face2info/service/InformationAggregationService.java`
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- Modify: `src/main/java/com/example/face2info/entity/internal/AggregationResult.java`
- Modify: `src/main/java/com/example/face2info/entity/internal/PersonAggregate.java`
- Test: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Add:

```java
@Test
void shouldUseResolvedNameForGoogleNewsAndSocialAggregation() throws Exception {
    SerpApiClient serpApiClient = mock(SerpApiClient.class);
    NewsApiClient newsApiClient = mock(NewsApiClient.class);
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    when(jinaReaderClient.readPages(List.of("https://example.com/a")))
            .thenReturn(List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer")));
    when(summaryGenerationClient.summarizePerson("unknown", List.of(
            new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer")
    ))).thenReturn(new ResolvedPersonProfile()
            .setResolvedName("Jay Chou")
            .setSummary("Jay Chou is a Mandopop singer.")
            .setEvidenceUrls(List.of("https://example.com/a")));
    when(serpApiClient.googleSearch("Jay Chou")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"Fallback\",\"website\":\"https://jay.example.com\"}}")));
    when(serpApiClient.googleSearch("Jay Chou 抖音")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(serpApiClient.googleSearch("Jay Chou 微博")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(newsApiClient.searchNews("Jay Chou")).thenReturn(new NewsApiResponse()
            .setRoot(objectMapper.readTree("{\"articles\":[]}")));

    InformationAggregationServiceImpl service =
            new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

    AggregationResult result = service.aggregate(new RecognitionEvidence()
            .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getPerson().getName()).isEqualTo("Jay Chou");
    assertThat(result.getPerson().getDescription()).isEqualTo("Jay Chou is a Mandopop singer.");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldUseResolvedNameForGoogleNewsAndSocialAggregation test`

Expected: FAIL because `aggregate` still accepts `String` and does not resolve a profile from evidence.

- [ ] **Step 3: Write minimal implementation**

Change `src/main/java/com/example/face2info/service/InformationAggregationService.java`:

```java
AggregationResult aggregate(RecognitionEvidence evidence);
```

Update `src/main/java/com/example/face2info/entity/internal/PersonAggregate.java` by adding `evidenceUrls` with getters and setters.

Update `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java` to:
- accept `RecognitionEvidence`
- choose up to 20 URLs
- call `resolveProfileFromEvidence`
- use `resolvedName` for Google search, news, and social search
- keep `summary` as primary description
- fallback to Google description when summary is blank

Use these helper signatures:

```java
ResolvedPersonProfile resolveProfileFromEvidence(List<WebEvidence> evidences)
private List<String> selectTopUrls(List<WebEvidence> evidences)
private String resolveNameOrFallback(ResolvedPersonProfile profile, RecognitionEvidence evidence)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldUseResolvedNameForGoogleNewsAndSocialAggregation test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/service/InformationAggregationService.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/main/java/com/example/face2info/entity/internal/AggregationResult.java src/main/java/com/example/face2info/entity/internal/PersonAggregate.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "feat(service): 基于正文总结结果重建人物聚合流程"
```

### Task 6: Wire The Main Service And Preserve Response Compatibility

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- Create: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`:

```java
package com.example.face2info.service.impl;

import com.example.face2info.entity.internal.AggregationResult;
import com.example.face2info.entity.internal.PersonAggregate;
import com.example.face2info.entity.internal.RecognitionEvidence;
import com.example.face2info.entity.response.FaceInfoResponse;
import com.example.face2info.service.FaceRecognitionService;
import com.example.face2info.service.InformationAggregationService;
import com.example.face2info.util.ImageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Face2InfoServiceImplTest {

    @Test
    void shouldMapRecognitionEvidenceAndAggregationResultToExistingResponseShape() {
        ImageUtils imageUtils = mock(ImageUtils.class);
        FaceRecognitionService recognitionService = mock(FaceRecognitionService.class);
        InformationAggregationService aggregationService = mock(InformationAggregationService.class);
        MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RecognitionEvidence evidence = new RecognitionEvidence();

        when(recognitionService.recognize(image)).thenReturn(evidence);
        when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
                .setPerson(new PersonAggregate().setName("Jay Chou").setDescription("Jay Chou is a singer.")));

        Face2InfoServiceImpl service = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService);

        FaceInfoResponse response = service.process(image);

        assertThat(response.getPerson().getName()).isEqualTo("Jay Chou");
        assertThat(response.getPerson().getDescription()).isEqualTo("Jay Chou is a singer.");
        assertThat(response.getStatus()).isEqualTo("success");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`

Expected: FAIL because `Face2InfoServiceImpl` still expects single-candidate recognition semantics.

- [ ] **Step 3: Write minimal implementation**

Update `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`:

```java
RecognitionEvidence evidence = faceRecognitionService.recognize(image);
AggregationResult aggregationResult = informationAggregationService.aggregate(evidence);
```

Map `person.name`, `person.description`, `person.wikipedia`, `person.officialWebsite`, `socialAccounts`, `news`, `imageMatches`, `status`, and `error` into the existing `FaceInfoResponse`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java
git commit -m "feat(service): 贯通主流程并保持响应结构兼容"
```

### Task 7: Add Downgrade Coverage And Full Verification

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`

- [ ] **Step 1: Write the failing tests**

Add concrete tests for:
- Yandex failure while Lens still provides evidence
- blank summary result falling back to Google description
- NewsAPI failure after profile resolution returning `partial`

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest,Face2InfoServiceImplTest test`

Expected: FAIL because the downgrade branches are not fully implemented yet.

- [ ] **Step 3: Write minimal implementation**

Implement the missing downgrade logic:
- in `FaceRecognitionServiceImpl`, wrap each external source call independently and append per-source errors into `RecognitionEvidence.errors`
- in `InformationAggregationServiceImpl`, if `summary` is blank, keep Google description
- in `Face2InfoServiceImpl`, set status to `partial` when aggregation errors are non-empty but person data exists

- [ ] **Step 4: Run targeted tests**

Run: `mvn -Dtest=FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest,Face2InfoServiceImplTest test`

Expected: PASS

- [ ] **Step 5: Run full verification**

Run: `mvn clean verify`

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java
git commit -m "test(service): 补齐多信源与降级链路验证"
```

## Self-Review

### Spec Coverage

- Multi-source image recognition: covered by Tasks 2, 3, and 7.
- Jina page reading: covered by Tasks 1 and 4.
- Summary abstraction with no concrete provider: covered by Tasks 1 and 4.
- Use summary result to resolve final person name before Google/News/social: covered by Task 5.
- Preserve response compatibility: covered by Task 6.
- Downgrade paths and verification: covered by Task 7.

No spec gaps found.

### Placeholder Scan

- Each task includes exact files, concrete test direction, and exact commands.
- The future real summary provider remains abstract by design, but this plan still defines the noop implementation required for this phase.

### Type Consistency

- `RecognitionEvidence` is the cross-service input model.
- `WebEvidence` feeds `selectTopUrls`.
- `PageContent` feeds `SummaryGenerationClient.summarizePerson`.
- `ResolvedPersonProfile` feeds `AggregationResult.person`.

Type names and method names are consistent across all tasks.
