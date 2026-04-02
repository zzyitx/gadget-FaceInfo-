# Remove Person Detail Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the standalone person-detail aggregation step and make the main aggregation flow produce structured person profile data directly from Jina page reads and Kimi summarization.

**Architecture:** Keep `InformationAggregationService` as the single aggregation entry, but delete its independent person-detail fetch stage. Extend the Kimi final-summary contract to return structured profile fields, map them into `PersonAggregate`/`PersonInfo`, and let the main flow degrade to a minimal person object when summarization fails.

**Tech Stack:** Java 17, Spring Boot 3.3.5, Maven, JUnit 5, Mockito, MockRestServiceServer

---

### Task 1: Add failing model and response-mapping tests

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Write the failing response mapping test**

```java
@Test
void shouldMapBasicInfoIntoResponse() {
    when(aggregationService.aggregate(evidence)).thenReturn(new AggregationResult()
            .setPerson(new PersonAggregate()
                    .setName("Jay Chou")
                    .setDescription("Short description")
                    .setSummary("Long summary")
                    .setWikipedia("https://example.com/wiki")
                    .setOfficialWebsite("https://example.com")
                    .setBasicInfo(new PersonBasicInfo()
                            .setBirthDate("1979-01-18")
                            .setEducation(List.of("Tamkang Senior High School"))
                            .setOccupations(List.of("Singer", "Producer"))
                            .setBiographies(List.of("Taiwanese Mandopop artist")))));

    FaceInfoResponse response = new Face2InfoServiceImpl(imageUtils, recognitionService, aggregationService).process(image);

    assertThat(response.getPerson().getBasicInfo().getBirthDate()).isEqualTo("1979-01-18");
    assertThat(response.getPerson().getBasicInfo().getEducation()).containsExactly("Tamkang Senior High School");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=Face2InfoServiceImplTest#shouldMapBasicInfoIntoResponse test`
Expected: FAIL because `basicInfo` does not exist on the current models.

- [ ] **Step 3: Write the failing aggregation flow test**

```java
@Test
void shouldPopulateStructuredProfileFieldsFromFinalSummaryWithoutGoogleDetailAggregation() {
    when(jinaReaderClient.readPages(List.of("https://example.com/a"))).thenReturn(List.of(
            new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body")));
    when(summaryGenerationClient.summarizePage("Jay Chou", new PageContent().setUrl("https://example.com/a").setTitle("A").setContent("body")))
            .thenReturn(new PageSummary().setSourceUrl("https://example.com/a").setSummary("page summary"));
    when(summaryGenerationClient.summarizePersonFromPageSummaries(anyString(), anyList()))
            .thenReturn(new ResolvedPersonProfile()
                    .setResolvedName("Jay Chou")
                    .setDescription("Short description")
                    .setSummary("Long summary")
                    .setWikipedia("https://example.com/wiki")
                    .setOfficialWebsite("https://example.com")
                    .setBasicInfo(new PersonBasicInfo().setBirthDate("1979-01-18")));

    AggregationResult result = service.aggregate(new RecognitionEvidence()
            .setSeedQueries(List.of("Jay Chou"))
            .setWebEvidences(List.of(new WebEvidence().setUrl("https://example.com/a"))));

    assertThat(result.getPerson().getWikipedia()).isEqualTo("https://example.com/wiki");
    verifyNoInteractions(googleSearchClient);
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn -Dtest=InformationAggregationServiceImplTest#shouldPopulateStructuredProfileFieldsFromFinalSummaryWithoutGoogleDetailAggregation test`
Expected: FAIL because the service still relies on the removed person-detail aggregation path and the new fields do not exist.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "test(服务): 为人物基础信息主流程补充失败测试"
```

### Task 2: Extend profile models and Kimi parsing

**Files:**
- Create: `src/main/java/com/example/face2info/entity/internal/PersonBasicInfo.java`
- Create: `src/main/java/com/example/face2info/entity/response/PersonBasicInfoResponse.java`
- Modify: `src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java`
- Modify: `src/main/java/com/example/face2info/entity/internal/PersonAggregate.java`
- Modify: `src/main/java/com/example/face2info/entity/response/PersonInfo.java`
- Modify: `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
- Modify: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: Write the failing Kimi parsing test**

```java
@Test
void shouldParseStructuredBasicInfoFromFinalProfile() {
    ResolvedPersonProfile profile = client.summarizePersonFromPageSummaries("Jay Chou", List.of(
            new PageSummary().setSourceUrl("https://example.com/a").setSummary("Summary A")
    ));

    assertThat(profile.getDescription()).isEqualTo("Short description");
    assertThat(profile.getBasicInfo().getOccupations()).containsExactly("Singer");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KimiSummaryGenerationClientTest#shouldParseStructuredBasicInfoFromFinalProfile test`
Expected: FAIL because final profile JSON parsing does not expose the new fields.

- [ ] **Step 3: Implement minimal model and parser changes**

```java
public class PersonBasicInfo {
    private String birthDate;
    private List<String> education = new ArrayList<>();
    private List<String> occupations = new ArrayList<>();
    private List<String> biographies = new ArrayList<>();
}
```

```java
return new ResolvedPersonProfile()
        .setResolvedName(firstNonBlank(json.path("resolvedName").asText(null), fallbackName))
        .setDescription(json.path("description").asText(null))
        .setSummary(json.path("summary").asText(null))
        .setWikipedia(json.path("wikipedia").asText(null))
        .setOfficialWebsite(json.path("officialWebsite").asText(null))
        .setBasicInfo(readBasicInfo(json.path("basicInfo")))
        .setKeyFacts(readStringList(json.path("keyFacts")))
        .setTags(readStringList(json.path("tags")))
        .setEvidenceUrls(evidenceUrls);
```

- [ ] **Step 4: Run focused tests to verify they pass**

Run: `mvn -Dtest=KimiSummaryGenerationClientTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/entity/internal/PersonBasicInfo.java src/main/java/com/example/face2info/entity/response/PersonBasicInfoResponse.java src/main/java/com/example/face2info/entity/internal/ResolvedPersonProfile.java src/main/java/com/example/face2info/entity/internal/PersonAggregate.java src/main/java/com/example/face2info/entity/response/PersonInfo.java src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java
git commit -m "feat(人物模型): 扩展Kimi结构化人物信息"
```

### Task 3: Remove standalone person-detail aggregation from the main flow

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: Delete the old detail aggregation dependency from the flow**

```java
PersonAggregate person = buildPersonFromProfile(profile, resolvedName);
result.setPerson(person);
```

```java
private PersonAggregate buildPersonFromProfile(ResolvedPersonProfile profile, String resolvedName) {
    return new PersonAggregate()
            .setName(resolvedName)
            .setDescription(formatDescription(cleanDescription(profile.getDescription()), cleanDescription(profile.getSummary())))
            .setSummary(appendSuffix(cleanDescription(profile.getSummary()), KIMI_SUFFIX))
            .setWikipedia(profile.getWikipedia())
            .setOfficialWebsite(profile.getOfficialWebsite())
            .setTags(profile.getTags() == null ? List.of() : profile.getTags())
            .setBasicInfo(profile.getBasicInfo())
            .setEvidenceUrls(profile.getEvidenceUrls());
}
```

- [ ] **Step 2: Run focused aggregation tests to verify the expected failure/passing cycle**

Run: `mvn -Dtest=InformationAggregationServiceImplTest test`
Expected: PASS after removing the Google detail aggregation coupling and mapping the structured fields from the summary pipeline.

- [ ] **Step 3: Keep fallback behavior minimal**

```java
return new ResolvedPersonProfile()
        .setResolvedName(fallbackName)
        .setEvidenceUrls(urls);
```

Expected behavior: when final summary fails, `person.name` remains available while `description`, `summary`, `wikipedia`, `officialWebsite`, and `basicInfo` stay empty and warnings include the summary warning.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java
git commit -m "refactor(聚合服务): 删除独立人物详情聚合环节"
```

### Task 4: Map new fields to API output and refresh docs/tests

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java`
- Modify: `src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java`
- Modify: `README.md`

- [ ] **Step 1: Implement response mapping**

```java
PersonInfo person = new PersonInfo()
        .setName(aggregationResult.getPerson().getName())
        .setDescription(aggregationResult.getPerson().getDescription())
        .setSummary(aggregationResult.getPerson().getSummary())
        .setWikipedia(aggregationResult.getPerson().getWikipedia())
        .setOfficialWebsite(aggregationResult.getPerson().getOfficialWebsite())
        .setBasicInfo(PersonBasicInfoResponse.from(aggregationResult.getPerson().getBasicInfo()))
        .setTags(aggregationResult.getPerson().getTags())
        .setSocialAccounts(aggregationResult.getSocialAccounts());
```

- [ ] **Step 2: Run focused response tests**

Run: `mvn -Dtest=Face2InfoServiceImplTest test`
Expected: PASS

- [ ] **Step 3: Document the new response shape in Chinese**

```md
- `person.basic_info.birth_date`
- `person.basic_info.education`
- `person.basic_info.occupations`
- `person.basic_info.biographies`
```

- [ ] **Step 4: Run project verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/service/impl/Face2InfoServiceImpl.java src/test/java/com/example/face2info/service/impl/Face2InfoServiceImplTest.java README.md
git commit -m "docs(接口): 补充人物基础信息响应字段"
```
