# Jina 逐篇总结再总汇总 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有“整批正文一次性送 AI 总结”改造成“正文逐篇 AI 总结，再基于篇级摘要统一做最终人物汇总”的两阶段流程。

**Architecture:** 保持 `InformationAggregationServiceImpl` 负责流程编排，新增内部模型 `PageSummary` 作为篇级摘要载体，并将 `SummaryGenerationClient` 扩展为“单篇总结”和“最终汇总”两个能力。服务层显式控制“单篇失败跳过”和“零成功篇级摘要时整体失败”，客户端层继续封装 Kimi prompt、HTTP 与 JSON 解析。

**Tech Stack:** Java 17, Spring Boot 3.3.5, JUnit 5, Mockito, MockRestServiceServer, Maven

---

## File Map

- Create: `src/main/java/com/example/face2info/entity/internal/PageSummary.java`
  - 篇级结构化摘要模型，承载 `sourceUrl/title/resolvedNameCandidate/summary/keyFacts/tags`
- Modify: `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
  - 将单一 `summarizePerson(...)` 拆成 `summarizePage(...)` 和 `summarizePersonFromPageSummaries(...)`
- Modify: `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`
  - 同步适配新接口，提供无副作用的默认实现
- Modify: `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
  - 增加篇级 prompt 与最终汇总 prompt，分别解析 `PageSummary` 和 `ResolvedPersonProfile`
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
  - 将正文增强主链路改成逐篇总结后再总汇总
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`
  - 覆盖逐篇调用、单篇失败跳过、零成功整体失败、总汇总失败等行为
- Modify: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`
  - 覆盖篇级总结与总汇总解析

## Task 1: 锁定服务层目标行为

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: 写服务层失败测试，约束“逐篇总结后总汇总”调用链**

```java
@Test
void shouldSummarizePagesOneByOneBeforeFinalProfileAggregation() {
    JinaReaderClient jinaReaderClient = mock(JinaReaderClient.class);
    SummaryGenerationClient summaryGenerationClient = mock(SummaryGenerationClient.class);

    List<PageContent> pages = List.of(
            new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("Page A"),
            new PageContent().setUrl("https://example.com/b").setTitle("B").setContent("Page B")
    );
    when(jinaReaderClient.readPages(List.of("https://example.com/a", "https://example.com/b"))).thenReturn(pages);
    when(summaryGenerationClient.summarizePage("unknown", pages.get(0)))
            .thenReturn(new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A"));
    when(summaryGenerationClient.summarizePage("unknown", pages.get(1)))
            .thenReturn(new PageSummary().setSourceUrl("https://example.com/b").setSummary("Summary B"));
    when(summaryGenerationClient.summarizePersonFromPageSummaries(
            "unknown",
            List.of(
                    new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A"),
                    new PageSummary().setSourceUrl("https://example.com/b").setSummary("Summary B")
            )))
            .thenReturn(new ResolvedPersonProfile().setResolvedName("Jay Chou").setSummary("Final Summary"));
}
```

- [ ] **Step 2: 运行单测，确认因接口和模型尚不存在而失败**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldSummarizePagesOneByOneBeforeFinalProfileAggregation test`

Expected: FAIL，提示 `PageSummary` 或 `summarizePage/summarizePersonFromPageSummaries` 未定义。

- [ ] **Step 3: 在同一个测试类里补两个边界失败测试**

```java
@Test
void shouldSkipFailedPageSummariesAndStillBuildFinalProfileWhenAtLeastOnePageSucceeds() {
    when(summaryGenerationClient.summarizePage("unknown", pages.get(0)))
            .thenThrow(new RuntimeException("INVALID_RESPONSE"));
    when(summaryGenerationClient.summarizePage("unknown", pages.get(1)))
            .thenReturn(new PageSummary().setSourceUrl("https://example.com/b").setSummary("Summary B"));

    // 断言最终仍会调用 summarizePersonFromPageSummaries(...)
}

@Test
void shouldReturnFallbackProfileAndWarningWhenNoPageSummarySucceeds() {
    when(summaryGenerationClient.summarizePage("unknown", pages.get(0)))
            .thenThrow(new RuntimeException("INVALID_RESPONSE"));
    when(summaryGenerationClient.summarizePage("unknown", pages.get(1)))
            .thenThrow(new RuntimeException("TIMEOUT"));

    // 断言不会调用 summarizePersonFromPageSummaries(...)
    // 断言 aggregate(...) 走 warning + SerpAPI 描述降级
}
```

- [ ] **Step 4: 再跑一次服务层单测，确认仍是红灯且失败原因正确**

Run: `mvn -Dtest=InformationAggregationServiceImplTest test`

Expected: FAIL，且失败集中在新行为对应的未实现接口，不应出现环境性错误。

- [ ] **Step 5: 提交测试约束**

```bash
git add src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "test(service): 补充逐篇总结与零成功失败约束"
```

## Task 2: 引入篇级模型并扩展摘要接口

**Files:**
- Create: `src/main/java/com/example/face2info/entity/internal/PageSummary.java`
- Modify: `src/main/java/com/example/face2info/client/SummaryGenerationClient.java`
- Modify: `src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java`

- [ ] **Step 1: 新建 `PageSummary` 模型**

```java
package com.example.face2info.entity.internal;

import java.util.ArrayList;
import java.util.List;

public class PageSummary {

    private String sourceUrl;
    private String title;
    private String resolvedNameCandidate;
    private String summary;
    private List<String> keyFacts = new ArrayList<>();
    private List<String> tags = new ArrayList<>();

    public String getSourceUrl() { return sourceUrl; }
    public PageSummary setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }
    public String getTitle() { return title; }
    public PageSummary setTitle(String title) { this.title = title; return this; }
    public String getResolvedNameCandidate() { return resolvedNameCandidate; }
    public PageSummary setResolvedNameCandidate(String resolvedNameCandidate) { this.resolvedNameCandidate = resolvedNameCandidate; return this; }
    public String getSummary() { return summary; }
    public PageSummary setSummary(String summary) { this.summary = summary; return this; }
    public List<String> getKeyFacts() { return keyFacts; }
    public PageSummary setKeyFacts(List<String> keyFacts) { this.keyFacts = keyFacts; return this; }
    public List<String> getTags() { return tags; }
    public PageSummary setTags(List<String> tags) { this.tags = tags; return this; }
}
```

- [ ] **Step 2: 扩展 `SummaryGenerationClient` 接口**

```java
public interface SummaryGenerationClient {

    PageSummary summarizePage(String fallbackName, PageContent page);

    ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries);
}
```

- [ ] **Step 3: 适配 `NoopSummaryGenerationClient`**

```java
@Override
public PageSummary summarizePage(String fallbackName, PageContent page) {
    if (page == null) {
        return null;
    }
    return new PageSummary()
            .setSourceUrl(page.getUrl())
            .setTitle(page.getTitle());
}

@Override
public ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries) {
    ResolvedPersonProfile profile = new ResolvedPersonProfile().setResolvedName(fallbackName);
    if (pageSummaries != null) {
        profile.setEvidenceUrls(pageSummaries.stream()
                .map(PageSummary::getSourceUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList());
    }
    return profile;
}
```

- [ ] **Step 4: 运行服务层单测，确认编译问题已收敛到实现未完成**

Run: `mvn -Dtest=InformationAggregationServiceImplTest test`

Expected: FAIL，报错集中在 `InformationAggregationServiceImpl` 仍调用旧接口。

- [ ] **Step 5: 提交接口与模型改动**

```bash
git add src/main/java/com/example/face2info/entity/internal/PageSummary.java src/main/java/com/example/face2info/client/SummaryGenerationClient.java src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java
git commit -m "refactor(client): 扩展篇级摘要接口与默认实现"
```

## Task 3: 先让服务层流程通过测试

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- Test: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: 在 `resolveProfileFromEvidence(...)` 中引入篇级摘要收集**

```java
List<PageSummary> pageSummaries = new ArrayList<>();
for (PageContent page : pages) {
    if (page == null || !StringUtils.hasText(page.getContent())) {
        continue;
    }
    try {
        PageSummary pageSummary = summaryGenerationClient.summarizePage(fallbackName, page);
        if (pageSummary != null && StringUtils.hasText(pageSummary.getSummary())) {
            if (!StringUtils.hasText(pageSummary.getSourceUrl())) {
                pageSummary.setSourceUrl(page.getUrl());
            }
            if (!StringUtils.hasText(pageSummary.getTitle())) {
                pageSummary.setTitle(page.getTitle());
            }
            pageSummaries.add(pageSummary);
        }
    } catch (RuntimeException ex) {
        log.warn("篇级总结失败 fallbackName={} url={} error={}", fallbackName, page.getUrl(), ex.getMessage(), ex);
    }
}
```

- [ ] **Step 2: 用“零成功篇级摘要即整体失败”替换旧的整批总结调用**

```java
if (pageSummaries.isEmpty()) {
    warnings.add(SUMMARY_WARNING);
    return new ResolvedPersonProfile()
            .setResolvedName(fallbackName)
            .setEvidenceUrls(urls);
}

ResolvedPersonProfile profile = summaryGenerationClient
        .summarizePersonFromPageSummaries(fallbackName, pageSummaries);
if (profile.getEvidenceUrls() == null || profile.getEvidenceUrls().isEmpty()) {
    profile.setEvidenceUrls(pageSummaries.stream()
            .map(PageSummary::getSourceUrl)
            .filter(StringUtils::hasText)
            .distinct()
            .toList());
}
return profile;
```

- [ ] **Step 3: 保留现有 Jina 失败后的 `WebEvidence -> PageContent` 兜底逻辑，不做额外扩面**

```java
if (pages == null || pages.isEmpty()) {
    pages = buildFallbackPages(evidences, urls);
}
```

- [ ] **Step 4: 运行服务层单测，确认转绿**

Run: `mvn -Dtest=InformationAggregationServiceImplTest test`

Expected: PASS，新增逐篇行为测试通过，旧的 URL 选择和降级测试一并通过。

- [ ] **Step 5: 提交服务层主流程**

```bash
git add src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "feat(service): 重构jina逐篇总结与最终汇总流程"
```

## Task 4: 锁定 Kimi 客户端两阶段行为

**Files:**
- Modify: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: 写单篇总结的失败测试**

```java
@Test
void shouldParseStructuredPageSummaryFromKimiResponse() {
    server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
            .andRespond(withSuccess("""
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "{\\"resolvedNameCandidate\\":\\"Jay Chou\\",\\"summary\\":\\"Singer\\",\\"keyFacts\\":[\\"Fact A\\"],\\"tags\\":[\\"music\\"],\\"sourceUrl\\":\\"https://example.com/a\\"}"
                          }
                        }
                      ]
                    }
                    """, MediaType.APPLICATION_JSON));

    PageSummary summary = client.summarizePage("Jay Chou", new PageContent()
            .setUrl("https://example.com/a")
            .setTitle("Article A")
            .setContent("Page content A"));

    assertThat(summary.getResolvedNameCandidate()).isEqualTo("Jay Chou");
    assertThat(summary.getSummary()).isEqualTo("Singer");
    assertThat(summary.getTags()).containsExactly("music");
}
```

- [ ] **Step 2: 写单篇空摘要测试和最终汇总成功测试**

```java
@Test
void shouldThrowControlledExceptionWhenPageSummaryIsBlank() {
    server.expect(requestTo("https://www.sophnet.com/api/open-apis/v1/chat/completions"))
            .andRespond(withSuccess("""
                    {"choices":[{"message":{"content":"{\\"summary\\":\\"   \\"}"}}]}
                    """, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.summarizePage("Jay Chou", page))
            .isInstanceOf(ApiCallException.class)
            .hasMessageContaining("EMPTY_RESPONSE");
}

@Test
void shouldParseFinalProfileFromPageSummaries() {
    ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
            new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A"),
            new PageSummary().setSourceUrl("https://example.com/b").setSummary("Summary B")
    ));

    assertThat(profile.getResolvedName()).isEqualTo("Jay Chou");
    assertThat(profile.getEvidenceUrls()).containsExactly("https://example.com/a", "https://example.com/b");
}
```

- [ ] **Step 3: 跑客户端单测，确认红灯原因是客户端尚未实现新接口**

Run: `mvn -Dtest=KimiSummaryGenerationClientTest test`

Expected: FAIL，且报错集中在 `summarizePage` / `summarizePersonFromPageSummaries` 未实现。

- [ ] **Step 4: 清理或改名旧的 `summarizePerson(...)` 相关测试，避免计划执行者继续沿用旧接口**

```java
// 将 shouldParseSummaryAndTagsFromKimiResponse 改成 shouldParseFinalProfileFromPageSummaries
// 将所有 client.summarizePerson(...) 调用切换到新接口
```

- [ ] **Step 5: 提交客户端测试约束**

```bash
git add src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java
git commit -m "test(client): 补充两阶段kimi摘要解析测试"
```

## Task 5: 实现 Kimi 客户端两阶段摘要

**Files:**
- Modify: `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
- Test: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: 新增单篇 prompt 构建与解析方法**

```java
@Override
public PageSummary summarizePage(String fallbackName, PageContent page) {
    KimiApiProperties kimi = properties.getApi().getKimi();
    validateKimiConfig(kimi);

    return RetryUtils.execute("Kimi summarize page", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
        JsonNode response = callKimi(kimi, buildPagePrompt(fallbackName, page));
        return parsePageSummary(page, response);
    });
}

private String buildPagePrompt(String fallbackName, PageContent page) {
    return """
            请基于以下单篇正文抽取人物信息，只输出 JSON。
            字段固定为 resolvedNameCandidate、summary、keyFacts、tags。
            fallbackName: %s
            title: %s
            url: %s
            正文:
            %s
            """.formatted(fallbackName, page.getTitle(), page.getUrl(), page.getContent());
}
```

- [ ] **Step 2: 新增最终汇总 prompt 构建与解析方法**

```java
@Override
public ResolvedPersonProfile summarizePersonFromPageSummaries(String fallbackName, List<PageSummary> pageSummaries) {
    KimiApiProperties kimi = properties.getApi().getKimi();
    validateKimiConfig(kimi);

    return RetryUtils.execute("Kimi summarize person", kimi.getMaxRetries(), kimi.getBackoffInitialMs(), () -> {
        JsonNode response = callKimi(kimi, buildPersonSummaryPrompt(fallbackName, pageSummaries));
        return parseProfileFromPageSummaries(fallbackName, pageSummaries, response);
    });
}
```

- [ ] **Step 3: 提取公共 HTTP 调用和配置校验，避免复制逻辑**

```java
private void validateKimiConfig(KimiApiProperties kimi) {
    if (!StringUtils.hasText(kimi.getApiKey())
            || !StringUtils.hasText(kimi.getBaseUrl())
            || !StringUtils.hasText(kimi.getModel())) {
        throw new ApiCallException("CONFIG_MISSING: kimi config is incomplete");
    }
}

private JsonNode callKimi(KimiApiProperties kimi, String prompt) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(kimi.getApiKey());
    headers.setContentType(MediaType.APPLICATION_JSON);

    Map<String, Object> requestBody = Map.of(
            "model", kimi.getModel(),
            "messages", List.of(
                    Map.of("role", "system", "content", kimi.getSystemPrompt()),
                    Map.of("role", "user", "content", prompt)
            )
    );

    return restTemplate.exchange(
            kimi.getBaseUrl(),
            HttpMethod.POST,
            new HttpEntity<>(requestBody, headers),
            JsonNode.class
    ).getBody();
}
```

- [ ] **Step 4: 运行客户端单测，确认转绿**

Run: `mvn -Dtest=KimiSummaryGenerationClientTest test`

Expected: PASS，包含单篇总结与最终汇总两个入口。

- [ ] **Step 5: 提交客户端实现**

```bash
git add src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java
git commit -m "feat(client): 支持kimi逐篇摘要与最终汇总"
```

## Task 6: 全量验证并收尾

**Files:**
- Verify only

- [ ] **Step 1: 运行服务层与客户端聚焦测试**

Run: `mvn -Dtest=InformationAggregationServiceImplTest,KimiSummaryGenerationClientTest test`

Expected: PASS。

- [ ] **Step 2: 运行完整校验**

Run: `mvn clean verify`

Expected: BUILD SUCCESS。

- [ ] **Step 3: 检查工作区，确认没有误带入无关改动**

Run: `git status --short`

Expected: 仅包含本次任务相关文件；如果看到既有未提交改动，必须与本次改动分开处理，不得覆盖。

- [ ] **Step 4: 进行计划自检备注**

```text
核对项：
- 服务层是否逐篇调用 summarizePage(...)
- 单篇失败是否被跳过
- 零成功篇级摘要时是否不再调用最终汇总
- 最终汇总失败时是否仍返回 warning + 基础人物信息
- Kimi 客户端是否对单篇与总汇总都做了固定 JSON 解析
```

- [ ] **Step 5: 提交最终收尾**

```bash
git add src/main/java/com/example/face2info/entity/internal/PageSummary.java src/main/java/com/example/face2info/client/SummaryGenerationClient.java src/main/java/com/example/face2info/client/impl/NoopSummaryGenerationClient.java src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java
git commit -m "feat(summary): 完成jina逐篇总结再总汇总改造"
```

## Self-Review

- 规格覆盖：
  - 两阶段流程：Task 2、Task 3、Task 5
  - 单篇失败跳过：Task 1、Task 3
  - 零成功整体失败：Task 1、Task 3
  - 客户端 JSON 协议与解析：Task 4、Task 5
  - 测试覆盖：Task 1、Task 4、Task 6
- 占位符检查：计划中未使用 `TODO/TBD/implement later` 等占位描述。
- 类型一致性：
  - 新增模型统一命名为 `PageSummary`
  - 客户端接口统一命名为 `summarizePage(...)` 与 `summarizePersonFromPageSummaries(...)`
  - 最终结果模型保持 `ResolvedPersonProfile`
