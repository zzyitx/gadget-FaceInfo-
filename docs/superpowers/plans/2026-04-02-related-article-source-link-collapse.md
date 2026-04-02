# Related Article Source Link Collapse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让前端《相关文章》模块默认折叠，并在每条文章内展示来源原文链接。

**Architecture:** 保持后端响应结构不变，只调整静态首页 `index.html`。使用原生 `details/summary` 作为折叠容器，避免额外状态管理；在现有 `renderNews` 流程中补充统一的卡片外壳函数和原文链接行。

**Tech Stack:** Spring Boot static page, vanilla HTML/CSS/JavaScript, MockMvc static page test

---

### Task 1: 锁定静态页行为

**Files:**
- Modify: `src/test/java/com/example/face2info/controller/StaticPageTest.java`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldRenderCollapsedNewsSectionWithArticleSourceLink() throws Exception {
    mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"newsCard\"")))
            .andExpect(content().string(containsString("renderNewsCard")))
            .andExpect(content().string(containsString("打开原文")));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=StaticPageTest test`
Expected: FAIL because `newsCard` 仍是普通 `section`，且页面脚本里没有原文链接文案。

- [ ] **Step 3: Write minimal implementation**

```java
.andExpect(content().string(containsString("news-card")))
.andExpect(content().string(not(containsString("id=\"newsCard\" open"))))
.andExpect(content().string(containsString("news-toggle")))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=StaticPageTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "test(frontend): 补充相关文章折叠与原文链接校验"
```

### Task 2: 调整相关文章模块结构

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: Write the failing test**

```java
.andExpect(content().string(containsString("id=\"newsCard\"")))
.andExpect(content().string(not(containsString("id=\"newsCard\" open"))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=StaticPageTest test`
Expected: FAIL because `newsCard` 不是默认关闭的 `details`。

- [ ] **Step 3: Write minimal implementation**

```html
<details class="card news-card" id="newsCard">
    <summary>...</summary>
    <div class="news-card-body">...</div>
</details>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=StaticPageTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat(frontend): 将相关文章模块调整为默认折叠"
```

### Task 3: 补充原文链接渲染

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: Write the failing test**

```java
.andExpect(content().string(containsString("打开原文")))
.andExpect(content().string(containsString("item.url")));
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=StaticPageTest test`
Expected: FAIL because `renderNews` 尚未输出原文链接行。

- [ ] **Step 3: Write minimal implementation**

```javascript
const sourceLink = item.url
    ? '<p class="news-source-link"><a href="' + escapeHtml(item.url) + '" target="_blank" rel="noreferrer">打开原文</a></p>'
    : '<p class="news-source-link">暂无原文链接</p>';
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=StaticPageTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "feat(frontend): 在相关文章中展示原文来源链接"
```
