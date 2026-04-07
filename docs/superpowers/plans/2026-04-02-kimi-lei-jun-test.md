# Kimi 雷军文章测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Kimi 客户端补齐 `keyFacts` 解析，并新增一个基于雷军百科正文的真实手动测试。

**Architecture:** 在现有 `KimiSummaryGenerationClient` 中扩展 JSON 字段解析，不调整对外响应模型。测试保留在现有 `KimiSummaryGenerationClientTest` 中，新增一个 mock 场景验证 `keyFacts`，再新增一个 `@Disabled` 的真实 Kimi 手动测试承载指定正文输入与中等强度断言。

**Tech Stack:** Java 17, Spring Boot Test, JUnit 5, AssertJ, RestTemplate

---

### Task 1: 补齐测试覆盖

**Files:**
- Modify: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: 写失败测试**

新增一个 mock 响应，包含 `keyFacts` 数组，断言 `profile.getKeyFacts()` 返回去重后的事件简介；同时新增一个 `@Disabled` 的真实雷军文章测试骨架，断言 `resolvedName`、`summary`、`keyFacts` 非空且命中核心关键词。

- [ ] **Step 2: 运行定向测试确认失败**

Run: `mvn "-Dtest=KimiSummaryGenerationClientTest#shouldParseKeyFactsFromKimiResponse" test`
Expected: FAIL，原因是 `keyFacts` 目前未被客户端解析。

### Task 2: 实现 keyFacts 解析

**Files:**
- Modify: `src/main/java/com/example/face2info/client/impl/KimiSummaryGenerationClient.java`
- Test: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: 最小实现**

在 `parseProfile` 中读取 `keyFacts` JSON 数组，复用现有字符串列表读取逻辑去重写入 `ResolvedPersonProfile`。

- [ ] **Step 2: 运行定向测试确认通过**

Run: `mvn "-Dtest=KimiSummaryGenerationClientTest" test`
Expected: PASS，`@Disabled` 的真实测试被跳过，其余测试通过。
