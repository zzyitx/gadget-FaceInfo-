# Independent Upload And Collapsible Panels Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页改成独立上传区布局，并把相关文章、社交账号、调试信息拆成默认折叠的独立模块。

**Architecture:** 仅调整静态首页 `index.html` 与对应静态页测试，不改后端接口。页面使用双栏布局承载上传区与结果区，折叠模块统一使用原生 `details/summary`，相关文章继续在模块内部组合 `image_matches` 和 `news` 两类数据。

**Tech Stack:** Spring Boot static page, vanilla HTML/CSS/JavaScript, MockMvc static page test

---

### Task 1: 锁定布局与折叠模块行为

**Files:**
- Modify: `src/test/java/com/example/face2info/controller/StaticPageTest.java`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: Write the failing test**

```java
@Test
void shouldRenderIndependentUploadPanelAndCollapsedInfoPanels() throws Exception {
    mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("uploadPanel")))
            .andExpect(content().string(containsString("socialCard")))
            .andExpect(content().string(containsString("debugPanel")))
            .andExpect(content().string(containsString("renderArticleGroups")));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=StaticPageTest test`
Expected: FAIL because上传区仍在顶部 hero 中，社交账号与调试信息还不是统一折叠模块。

- [ ] **Step 3: Write minimal implementation**

```java
.andExpect(content().string(not(containsString("id=\"socialCard\" open"))))
.andExpect(content().string(not(containsString("id=\"debugPanel\" open"))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=StaticPageTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "test(frontend): 补充独立上传区与折叠模块静态页校验"
```

### Task 2: 调整首页主布局

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: Write the failing test**

```java
.andExpect(content().string(containsString("id=\"uploadPanel\"")))
.andExpect(content().string(containsString("main-layout")))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=StaticPageTest test`
Expected: FAIL because页面仍使用 hero 双栏承载上传区。

- [ ] **Step 3: Write minimal implementation**

```html
<section class="hero">...</section>
<div class="main-layout">
  <aside class="upload-panel" id="uploadPanel">...</aside>
  <main class="results">...</main>
</div>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=StaticPageTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat(frontend): 调整首页为独立上传区与结果双栏布局"
```

### Task 3: 拆分折叠信息模块

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: Write the failing test**

```java
.andExpect(content().string(containsString("id=\"socialCard\"")))
.andExpect(content().string(containsString("id=\"debugPanel\"")))
.andExpect(content().string(containsString("details class=\"card social-card\"")))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=StaticPageTest test`
Expected: FAIL because社交账号和调试信息还不是独立默认折叠模块。

- [ ] **Step 3: Write minimal implementation**

```html
<details class="card social-card" id="socialCard">...</details>
<details class="card debug-card" id="debugPanel">...</details>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=StaticPageTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "feat(frontend): 拆分并折叠相关文章社交账号与调试模块"
```
