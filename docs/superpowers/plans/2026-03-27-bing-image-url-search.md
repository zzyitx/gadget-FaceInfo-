# Bing Image URL Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Bing 图片信源从文本关键词检索改为基于上传图片 URL 的检索。

**Architecture:** 在识别流程中，Bing 与 Google Lens、Yandex 一样直接消费 `tmpfiles` 返回的公开图片 URL，不再遍历 `seedQueries` 发起文本搜图。SerpAPI 客户端接口同步改为图片 URL 语义，并保持下游证据抽取结构不变。

**Tech Stack:** Java 17, Spring Boot 3.3, Maven, JUnit 5, Mockito

---

### Task 1: 锁定 Bing 请求语义

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
- Modify: `src/main/java/com/example/face2info/service/FaceRecognitionService.java`
- Modify: `src/main/java/com/example/face2info/client/SerpApiClient.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldUseUploadedImageUrlForBingSearch() {
    when(tmpfilesClient.uploadImage(image)).thenReturn("https://tempfile.org/abc/preview");
    when(serpApiClient.reverseImageSearchByUrl("https://tempfile.org/abc/preview")).thenReturn(emptyResponse());
    when(serpApiClient.reverseImageSearchByUrlYandex("https://tempfile.org/abc/preview", "about")).thenReturn(emptyResponse());
    when(serpApiClient.reverseImageSearchByUrlYandex("https://tempfile.org/abc/preview", "similar")).thenReturn(emptyResponse());
    when(serpApiClient.reverseImageSearchByUrlBing("https://tempfile.org/abc/preview")).thenReturn(emptyResponse());

    service.recognize(image);

    verify(serpApiClient).reverseImageSearchByUrlBing("https://tempfile.org/abc/preview");
    verify(serpApiClient, never()).searchBingImages(anyString());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn "-Dtest=FaceRecognitionServiceImplTest#shouldUseUploadedImageUrlForBingSearch" test`
Expected: FAIL，因为 `reverseImageSearchByUrlBing` 尚不存在，且代码仍在调用 `searchBingImages`

- [ ] **Step 3: Write minimal interface changes**

```java
SerpApiResponse reverseImageSearchByUrlBing(String imageUrl);
```

- [ ] **Step 4: Run test to verify it still fails for implementation missing**

Run: `mvn "-Dtest=FaceRecognitionServiceImplTest#shouldUseUploadedImageUrlForBingSearch" test`
Expected: FAIL，断言提示未调用新的 Bing URL 检索

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java src/main/java/com/example/face2info/client/SerpApiClient.java
git commit -m "test(service): 为Bing图片URL检索补充失败用例"
```

### Task 2: 实现 Bing 图片 URL 检索

**Files:**
- Modify: `src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java`
- Modify: `src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java`
- Test: `src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`

- [ ] **Step 1: Write the failing regression test for降级行为**

```java
@Test
void shouldContinueWhenBingImageUrlSearchFails() {
    when(tmpfilesClient.uploadImage(image)).thenReturn("https://tempfile.org/abc/preview");
    when(serpApiClient.reverseImageSearchByUrl("https://tempfile.org/abc/preview")).thenReturn(lensResponse());
    when(serpApiClient.reverseImageSearchByUrlYandex("https://tempfile.org/abc/preview", "about")).thenReturn(emptyResponse());
    when(serpApiClient.reverseImageSearchByUrlYandex("https://tempfile.org/abc/preview", "similar")).thenReturn(emptyResponse());
    when(serpApiClient.reverseImageSearchByUrlBing("https://tempfile.org/abc/preview")).thenThrow(new RuntimeException("timeout"));

    RecognitionEvidence result = service.recognize(image);

    assertThat(result.getErrors()).contains("bing_images: timeout");
    assertThat(result.getImageMatches()).isNotEmpty();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn "-Dtest=FaceRecognitionServiceImplTest" test`
Expected: FAIL，Bing 仍未按 URL 调用

- [ ] **Step 3: Implement minimal production changes**

```java
SerpApiResponse bingResponse = safeSearch("bing_images", imageUrl, evidence,
        () -> serpApiClient.reverseImageSearchByUrlBing(imageUrl));
if (bingResponse != null && bingResponse.getRoot() != null) {
    webEvidences.addAll(extractWebEvidence(bingResponse.getRoot(), "bing_images"));
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn "-Dtest=FaceRecognitionServiceImplTest" test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java
git commit -m "feat(service): 改为使用图片URL调用Bing图搜"
```

### Task 3: 全量验证

**Files:**
- Verify only

- [ ] **Step 1: Run targeted tests**

Run: `mvn "-Dtest=FaceRecognitionServiceImplTest,InformationAggregationServiceImplTest" test`
Expected: PASS

- [ ] **Step 2: Run full verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit if verification requires fixture updates**

```bash
git add .
git commit -m "test: 完成Bing图片URL检索验证"
```
