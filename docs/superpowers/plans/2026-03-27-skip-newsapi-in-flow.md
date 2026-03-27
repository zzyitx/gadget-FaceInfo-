# 暂停 NewsAPI 流程实施计划

> **供代理执行参考：** 实施本计划时，建议使用 `superpowers:subagent-driven-development`，也可使用 `superpowers:executing-plans` 按任务逐步执行。步骤使用复选框 `- [ ]` 语法跟踪。

**目标：** 在停止聚合流程调用 NewsAPI 的同时，保持接口中的 `news` 响应字段结构不变。

**方案：** 仅调整 `InformationAggregationServiceImpl` 中的流程编排路径，不删除现有 NewsAPI 客户端、配置、实体模型和解析逻辑，确保后续恢复时无需重新补基础设施。

**技术栈：** Java 17、Spring Boot 3.3.5、JUnit 5、Mockito、AssertJ

---

### 任务 1：固定新的流程契约

**涉及文件：**
- 修改：`src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **步骤 1：先写失败测试**

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
    when(serpApiClient.googleSearch("Jay Chou 鎶栭煶")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));
    when(serpApiClient.googleSearch("Jay Chou 寰崥")).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("{\"organic_results\":[]}")));

    InformationAggregationServiceImpl service =
            new InformationAggregationServiceImpl(serpApiClient, newsApiClient, jinaReaderClient, summaryGenerationClient, executor);

    AggregationResult result = service.aggregate(new RecognitionEvidence()
            .setSeedQueries(List.of("Jay Chou"))
            .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getNews()).isEmpty();
}
```

- [ ] **步骤 2：运行测试并确认失败**

运行：`mvn -Dtest=InformationAggregationServiceImplTest#shouldSkipNewsAggregationAndReturnEmptyNews test`  
预期：`FAIL`，因为服务当前仍会执行新闻聚合分支。

- [ ] **步骤 3：编写最小实现**

```java
result.setPerson(person);
result.setSocialAccounts(deduplicateSocialAccounts(joinTask("绀句氦璐﹀彿", socialFuture, List.of(), result.getErrors())));
result.setNews(List.of());
return result;
```

- [ ] **步骤 4：再次运行测试并确认通过**

运行：`mvn -Dtest=InformationAggregationServiceImplTest#shouldSkipNewsAggregationAndReturnEmptyNews test`  
预期：`PASS`

- [ ] **步骤 5：提交代码**

```bash
git add docs/superpowers/plans/2026-03-27-skip-newsapi-in-flow.md src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java
git commit -m "fix(service): 暂时跳过NewsAPI聚合流程"
```

### 任务 2：消除流程层回归风险

**涉及文件：**
- 修改：`src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
- 修改：`src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`

- [ ] **步骤 1：先写失败校验**

```java
verifyNoInteractions(newsApiClient);
assertThat(result.getErrors()).isEmpty();
```

- [ ] **步骤 2：运行测试并确认失败**

运行：`mvn -Dtest=InformationAggregationServiceImplTest#shouldSkipNewsAggregationAndReturnEmptyNews test`  
预期：`FAIL`，因为当前流程仍会调用 `newsApiClient.searchNews(...)`。

- [ ] **步骤 3：编写最小实现**

```java
// 从 aggregate(...) 中移除 newsFuture 的创建
// 保留 NewsApiClient 字段和 collectNews(...)，便于后续重新启用
```

- [ ] **步骤 4：运行定向测试**

运行：`mvn -Dtest=InformationAggregationServiceImplTest test`  
预期：`PASS`

- [ ] **步骤 5：运行更大范围验证**

运行：`mvn "-Dtest=Face2InfoServiceImplTest,InformationAggregationServiceImplTest" test`  
预期：`PASS`
