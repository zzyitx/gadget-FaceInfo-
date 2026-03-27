# Skip NewsAPI In Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the `news` response field stable while stopping the aggregation flow from calling NewsAPI.

**Architecture:** Adjust only the orchestration path in `InformationAggregationServiceImpl`. Preserve the existing NewsAPI client, config, entities, and parsing code so the capability can be restored later without re-adding infrastructure.

**Tech Stack:** Java 17, Spring Boot 3.3.5, JUnit 5, Mockito, AssertJ

---

### Task 1: Lock Down The New Flow Contract

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldSkipNewsAggregationAndReturnEmptyNews() throws Exception {
    SerpApiClient serpApiClient = mock(SerpApiClient.class);
    NewsApiClient newsApiClient = mock(NewsApiClient.class);
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    List<PageContent> pages = List.of(new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Jay Chou is a singer"));
    when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(pages);
    when(summaryGenerationClient.summarizePerson("Jay Chou", pages))
            .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("Jay Chou is a singer"));
    when(serpApiClient.googleSearch("Jay Chou")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"knowledge_graph\":{\"description\":\"Mandopop singer\"}}")));
    when(serpApiClient.googleSearch("Jay Chou 抖音")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(serpApiClient.googleSearch("Jay Chou 微博")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));

    InformationAggregationServiceImpl service =
            new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

    AggregationResult result = service.aggregate(new RecognitionEvidence()
            .setSeedQueries(List.of("Jay Chou"))
            .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getNews()).isEmpty();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldSkipNewsAggregationAndReturnEmptyNews test`
Expected: FAIL because the service still executes the news branch.

- [ ] **Step 3: Write minimal implementation**

```java
result.setPerson(person);
result.setSocialAccounts(deduplicateSocialAccounts(joinTask("社交账号", socialFuture, List.of(), result.getErrors())));
result.setNews(List.of());
return result;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldSkipNewsAggregationAndReturnEmptyNews test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-03-27-skip-newsapi-in-flow.md src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
git commit -m "fix(service): 暂时跳过NewsAPI聚合流程"
```

### Task 2: Remove Flow-Level Regression Risk

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`

- [ ] **Step 1: Write the failing verification**

```java
verifyNoInteractions(newsApiClient);
assertThat(result.getErrors()).isEmpty();
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldSkipNewsAggregationAndReturnEmptyNews test`
Expected: FAIL because the current flow still calls `newsApiClient.searchNews(...)`.

- [ ] **Step 3: Write minimal implementation**

```java
// Remove newsFuture creation from aggregate(...)
// Keep NewsApiClient field and collectNews(...) for future re-enablement.
```

- [ ] **Step 4: Run targeted tests**

Run: `mvn -Dtest=InformationAggregationServiceImplTest test`
Expected: PASS

- [ ] **Step 5: Run broader verification**

Run: `mvn -Dtest=Face2InfoServiceImplTest,InformationAggregationServiceImplTest test`
Expected: PASS
