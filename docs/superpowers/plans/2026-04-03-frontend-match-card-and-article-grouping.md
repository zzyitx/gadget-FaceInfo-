# 前端图片匹配卡片与相关文章分组 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 移除首页左侧可见状态卡片，重构图片匹配卡片展示，并将相关文章改为按图片顺序分组展示且显示来源。

**Architecture:** 只修改静态页 [index.html](/D:/ideaProject/gadget/src/main/resources/static/index.html) 与静态页测试 [StaticPageTest.java](/D:/ideaProject/gadget/src/test/java/com/example/face2info/controller/StaticPageTest.java)。前端继续消费现有 `status`、`image_matches` 和 `news` 字段，但不再把 `status` 渲染成可见状态模块；相关文章区域改为由 `image_matches` 驱动分组渲染。

**Tech Stack:** Spring Boot 3.3.5, JUnit 5, MockMvc, 原生 HTML/CSS/JavaScript

---

## 文件结构

- 修改：[index.html](/D:/ideaProject/gadget/src/main/resources/static/index.html)
  - 删除左侧 `statusBox` DOM、相关样式和可见状态更新逻辑
  - 调整图片匹配卡片样式与渲染
  - 改造相关文章分组渲染函数
- 修改：[StaticPageTest.java](/D:/ideaProject/gadget/src/test/java/com/example/face2info/controller/StaticPageTest.java)
  - 先写失败测试锁定新页面结构
  - 再补充图片卡片与文章分组断言

## 任务 1：为移除状态卡片和文章分组渲染补测试

**Files:**
- Modify: `src/test/java/com/example/face2info/controller/StaticPageTest.java`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: 写失败测试，锁定状态卡片已移除且文章分组入口存在**

```java
@Test
void shouldHideVisibleStatusCardAndRenderImageDrivenArticleGroups() throws Exception {
    mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"statusBox\""))))
            .andExpect(content().string(not(containsString("class=\"status\""))))
            .andExpect(content().string(containsString("renderArticleGroups(imageMatches, newsList)")))
            .andExpect(content().string(containsString("article-group")))
            .andExpect(content().string(containsString("match-group-source")));
}
```

- [ ] **Step 2: 运行测试，确认它先失败**

Run: `mvn -Dtest=StaticPageTest#shouldHideVisibleStatusCardAndRenderImageDrivenArticleGroups test`

Expected: FAIL，原因是当前页面仍包含 `statusBox` 或还没有新的 `renderArticleGroups(imageMatches, newsList)` 结构。

- [ ] **Step 3: 再写失败测试，锁定图片卡片不再展示 title，但文章组展示匹配标题与来源**

```java
@Test
void shouldMoveMatchTitleFromImageCardToArticleGroupHeader() throws Exception {
    mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("image-info .title"))))
            .andExpect(content().string(not(containsString("class=\"title\""))))
            .andExpect(content().string(containsString("match-group-title")))
            .andExpect(content().string(containsString("item.title || \"未命名结果\"")))
            .andExpect(content().string(containsString("item.source || \"未知来源\"")));
}
```

- [ ] **Step 4: 运行测试，确认它先失败**

Run: `mvn -Dtest=StaticPageTest#shouldMoveMatchTitleFromImageCardToArticleGroupHeader test`

Expected: FAIL，原因是当前页面仍在图片卡片中输出 `title`，还没有文章组标题类名和对应渲染逻辑。

- [ ] **Step 5: 提交测试骨架**

```bash
git add src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "test(前端): 补充状态卡片移除与文章分组测试"
```

注意：如果实现与测试需要放在同一次提交，至少保持“先失败再实现”的执行顺序，不要跳过红灯阶段。

## 任务 2：删除可见状态卡片并清理状态展示逻辑

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: 写最小实现，删除状态卡片 DOM**

从页面结构中删除这一段：

```html
<section class="status" id="statusBox" aria-live="polite">
  <div class="status-label">状态</div>
  <div class="status-text" id="statusText">待开始</div>
  <div class="status-note" id="statusNote">
    检索结果会展示人物信息、图片匹配、公开账号和相关文章。
  </div>
</section>
```

- [ ] **Step 2: 删除状态样式和引用变量**

删除或改写这些前端片段：

```javascript
const statusBox = document.getElementById("statusBox");
const statusText = document.getElementById("statusText");
const statusNote = document.getElementById("statusNote");
```

并删除样式块：

```css
.status { ... }
.status-success { ... }
.status-partial { ... }
.status-failed { ... }
.status-label { ... }
.status-text { ... }
```

- [ ] **Step 3: 将可见状态更新改为仅保留内部分支判断**

删除 `setStatus()` 的调用和函数定义，保留 `data.status` 的逻辑判断，例如：

```javascript
if (!response.ok || data.status === "failed") {
  renderError(data);
  return;
}

renderPerson(data.person, data.status, data.error);
renderImageMatches(data.image_matches || [], data.status, data.error);
renderSocialAccounts(data.person && data.person.social_accounts ? data.person.social_accounts : []);
renderArticleGroups(data.image_matches || [], data.news || []);
```

- [ ] **Step 4: 运行已新增测试，确认这一部分转绿**

Run: `mvn -Dtest=StaticPageTest#shouldHideVisibleStatusCardAndRenderImageDrivenArticleGroups test`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "refactor(前端): 移除首页可见状态卡片"
```

## 任务 3：重构图片匹配卡片布局并移除卡片标题

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: 写最小实现，调整图片卡片信息区字段**

将 `renderImageMatches()` 中的卡片信息部分从：

```javascript
'<div class="title">' + title + "</div>",
'<div class="score">相似度 ' + escapeHtml(confidence) + "%</div>",
```

改为不再输出标题，只保留序号、来源、相似度，例如：

```javascript
'<div class="source">#' + escapeHtml(item.position || index + 1) + " · " + source + "</div>",
'<div class="score">相似度 ' + escapeHtml(confidence) + "%</div>",
```

- [ ] **Step 2: 调整样式，让卡片更适配不同图片比例**

在样式中移除标题样式并增强图片区域适配，例如：

```css
.image-item {
  display: grid;
  grid-template-rows: minmax(240px, auto) auto;
  align-content: start;
}

.image-frame {
  min-height: 240px;
  max-height: 320px;
}

.image-item img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
}
```

- [ ] **Step 3: 运行第二个测试，确认卡片标题迁移需求转绿**

Run: `mvn -Dtest=StaticPageTest#shouldMoveMatchTitleFromImageCardToArticleGroupHeader test`

Expected: PASS

- [ ] **Step 4: 运行现有图片样式测试，防止回归**

Run: `mvn -Dtest=StaticPageTest#shouldRenderImageMatchesWithoutCroppingResultImages test`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "feat(前端): 调整图片匹配卡片并移除卡片标题"
```

## 任务 4：把相关文章改为按图片顺序分组渲染

**Files:**
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: 改造函数签名，让文章渲染以图片匹配为输入驱动**

把调用方从：

```javascript
renderArticleGroups(data.news || []);
```

改为：

```javascript
renderArticleGroups(data.image_matches || [], data.news || []);
```

并将函数签名改为：

```javascript
function renderArticleGroups(imageMatches, newsList) {
  // ...
}
```

- [ ] **Step 2: 写最小实现，按图片顺序生成文章组容器**

用 `imageMatches` 生成文章组，组头显示图片结果标题和来源，例如：

```javascript
function renderArticleGroups(imageMatches, newsList) {
  if (!imageMatches.length) {
    newsCard.innerHTML = renderNewsCard('<p class="empty">暂无相关文章。</p>');
    return;
  }

  const groupsHtml = imageMatches.slice(0, 20).map(function mapMatch(item) {
    return renderArticleSection(
      item.title || "未命名结果",
      item.source || "未知来源",
      renderNewsArticles(newsList)
    );
  }).join("");

  newsCard.innerHTML = renderNewsCard(groupsHtml);
}
```

注意：如果后续需要把 `newsList` 做更细的分配，先保证顺序和组头正确，不要虚构错误映射。

- [ ] **Step 3: 将文章分组结构改成显式组头和来源标签**

把原来的：

```javascript
function renderArticleSection(title, contentHtml) {
  return [
    '<section class="article-group">',
    '<div class="section-header compact">',
    '<div class="section-title"><h2>' + escapeHtml(title) + "</h2></div>",
    "</div>",
    contentHtml,
    "</section>"
  ].join("");
}
```

改为：

```javascript
function renderArticleSection(title, source, contentHtml) {
  return [
    '<section class="article-group">',
    '<div class="article-group-header">',
    '<div class="match-group-title">' + escapeHtml(title) + "</div>",
    '<div class="match-group-source">来源：' + escapeHtml(source) + "</div>",
    "</div>",
    contentHtml || '<p class="empty">该图片结果暂无相关文章。</p>',
    "</section>"
  ].join("");
}
```

- [ ] **Step 4: 为文章组样式补最小 CSS**

```css
.article-group {
  padding: 16px;
  border: 1px solid rgba(32, 23, 20, 0.12);
  border-radius: 20px;
  background: rgba(255, 250, 246, 0.9);
}

.article-group-header {
  display: grid;
  gap: 6px;
  margin-bottom: 12px;
}

.match-group-title {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.5;
}

.match-group-source {
  font-size: 13px;
  color: #64574f;
}
```

- [ ] **Step 5: 运行整组静态页测试**

Run: `mvn -Dtest=StaticPageTest test`

Expected: PASS

- [ ] **Step 6: 提交这一小步**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "feat(前端): 按图片顺序分组展示相关文章"
```

## 任务 5：做最终验证并清点风险

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/test/java/com/example/face2info/controller/StaticPageTest.java`

- [ ] **Step 1: 运行完整校验**

Run: `mvn clean verify`

Expected: BUILD SUCCESS

- [ ] **Step 2: 记录需要人工确认的页面行为**

人工检查重点：

```text
1. 首页左侧不再出现可见状态卡片
2. 图片匹配卡片点击仍能打开来源页面
3. 图片卡片不再显示 title，只显示序号、来源、相似度
4. 相关文章区按图片顺序生成分组
5. 每个分组显示匹配 title 和来源
6. 无新闻时，分组内空态文案正确
```

- [ ] **Step 3: 清点工作区风险**

在提交或合并前运行：

```bash
git status --short
```

确认本次实现没有覆盖工作区里与本任务无关的现有变更，尤其注意：

```text
src/main/java/com/example/face2info/client/impl/SerpApiClientImpl.java
docs/superpowers/plans/2026-04-02-jina-incremental-summary.md
docs/superpowers/plans/2026-04-02-kimi-lei-jun-test.md
docs/superpowers/specs/2026-04-02-frontend-copy-localization-design.md
```

- [ ] **Step 4: 最终提交**

```bash
git add src/main/resources/static/index.html src/test/java/com/example/face2info/controller/StaticPageTest.java
git commit -m "feat(前端): 重构图片匹配卡片与相关文章分组"
```
