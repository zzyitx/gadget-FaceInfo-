# Frontend Single Page Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构静态前端单页，删除“识别线索”板块，在不改动后端接口的前提下重排信息层级并优化整体视觉。

**Architecture:** 继续使用单文件 `index.html` 承载 HTML、CSS 和原生 JavaScript。通过重组首屏上传区、结果卡片布局和渲染函数，让页面结构调整保持在同一文件内完成，同时避免引入新的构建链路。

**Tech Stack:** HTML5, CSS3, Vanilla JavaScript, Spring Boot static resources

---

## 文件结构

- Modify: `src/main/resources/static/index.html`
- Verify: 浏览器打开首页并手动上传图片验证

### Task 1: 重构页面骨架与首屏布局

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: 调整页面骨架**

把左侧侧栏式表单改为 Hero 内上传区，并把结果区改为主结果、辅助比对、扩展信息、调试区四段结构。

- [ ] **Step 2: 重写核心样式**

为 Hero、上传卡、主结果卡、次级卡、双列扩展区和底部调试区建立统一样式，确保桌面和移动端都能正常显示。

- [ ] **Step 3: 清理无效视觉元素**

删除围绕“识别线索”设计的布局、网格和说明文案样式，避免保留死代码。

- [ ] **Step 4: 手动检查 DOM 引用**

确认所有新旧 `id` 与后续 JavaScript 查询一致，不保留失效节点引用。

### Task 2: 收敛结果内容与渲染逻辑

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: 删除“识别线索”渲染逻辑**

移除 `signalsCard` 相关 DOM、变量、重置逻辑和 `renderSignals` 函数。

- [ ] **Step 2: 修改人物简介渲染**

调整 `renderPerson`，仅展示姓名、状态、简介和可选标签；删除 `wikipedia` 与 `official_website` 的展示逻辑。

- [ ] **Step 3: 调整图片匹配文案**

保留 `renderFacecheckMatches` 作为独立板块，但把标题和空状态文案统一改为“辅助比对”语义。

- [ ] **Step 4: 调整空状态和失败状态**

让 `resetResultCards`、`renderError`、`setStatus` 与新的页面结构保持一致，避免残留已删除模块的文案。

### Task 3: 优化状态反馈与结果呈现

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: 收敛状态文案**

把等待、加载、成功、部分成功、失败五种状态文案改成更短、更直接的表达。

- [ ] **Step 2: 优化调试区层级**

保留 `rawResponse` 输出，但将其视觉降级为页面底部折叠调试区。

- [ ] **Step 3: 兼容标签字段降级**

如果接口返回 `person.tags`，在人物简介主卡中显示；如果没有，页面必须正常渲染，不增加错误。

- [ ] **Step 4: 检查 HTML 转义覆盖**

确认新增文本渲染仍通过 `escapeHtml` 处理，避免字符串直接注入页面。

### Task 4: 手动验证

**Files:**
- Verify: `src/main/resources/static/index.html`

- [ ] **Step 1: 检查文件语法**

运行：

```powershell
mvn -q -DskipTests package
```

Expected: 构建成功，静态资源打包通过。

- [ ] **Step 2: 手动验证主要场景**

至少检查：

```text
1. 页面首屏上传区是否可见
2. 上传预览是否正常
3. 成功结果是否按“人物简介 -> 图片匹配 -> 社交账号 -> 新闻”显示
4. 人物简介是否不再展示维基百科和官网
5. 调试 JSON 是否仍可展开
```

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/static/index.html docs/superpowers/specs/2026-04-02-frontend-single-page-refresh-design.md docs/superpowers/plans/2026-04-02-frontend-single-page-refresh.md
git commit -m "feat(frontend): 重构单页前端展示层级"
```
