# Serp 查询编码与 README 规则补充 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 SerpAPI Google 查询的重复编码问题，并在 README 中补充读取 `AGENTS.md` 的协作规则。

**Architecture:** 保持编码边界在 `client` 层，所有 Google/Bing/Lens 请求继续由 `SerpApiClientImpl` 统一构造 URL。对疑似已编码查询做一次规范化后再交给 `UriComponentsBuilder`，同时补充 README 协作说明，不改动服务层调用约束。

**Tech Stack:** Java 17, Spring Boot 3.3.5, RestTemplate, MockRestServiceServer, JUnit 5, Mockito

---

### Task 1: 锁定 Serp 查询重复编码回归

**Files:**
- Create: `src/test/java/com/example/face2info/client/impl/SerpApiClientImplTest.java`
- Modify: `src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldNormalizeEncodedGoogleQueryBeforeRequest() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    server.expect(requestTo("https://serpapi.com/search.json?engine=google&q=Lei%20Jun%20%E6%8A%96%E9%9F%B3&api_key=test-key"))
            .andRespond(withSuccess("{\"organic_results\":[]}", MediaType.APPLICATION_JSON));

    SerpApiClientImpl client = new SerpApiClientImpl(restTemplate, new ObjectMapper(), createProperties());

    client.googleSearch("Lei%20Jun%20%E6%8A%96%E9%9F%B3");

    server.verify();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SerpApiClientImplTest test`
Expected: FAIL，因为当前实现会把 `%` 再次编码成 `%25`。

- [ ] **Step 3: Write minimal implementation**

```java
private String normalizeQuery(String query) {
    if (!StringUtils.hasText(query)) {
        return query;
    }
    if (!query.contains("%")) {
        return query;
    }
    try {
        return UriUtils.decode(query, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
        return query;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=SerpApiClientImplTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/client/impl/SerpApiClientImplTest.java src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java
git commit -m "fix(client): 修复 Serp 查询重复编码问题"
```

### Task 2: 补充 README 协作规则

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Write the documentation change**

```md
## 协作约定

- 代理在按仓库约定读取相关技能说明和项目实现时，必须同时读取 `AGENTS.md` 并遵守其中规则。
```

- [ ] **Step 2: Verify README includes the rule**

Run: `Select-String -Path README.md -Pattern "AGENTS.md"`
Expected: 输出新增规则所在行。

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: 补充 AGENTS 读取约定"
```

### Task 3: 完整验证

**Files:**
- Verify only

- [ ] **Step 1: Run targeted tests**

Run: `mvn -Dtest=SerpApiClientImplTest,InformationAggregationServiceImplTest test`
Expected: PASS

- [ ] **Step 2: Run broader verification**

Run: `mvn clean verify`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add .
git commit -m "test: 验证编码修复与文档更新"
```
