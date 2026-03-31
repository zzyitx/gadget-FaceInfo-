# Jina Max Page Reads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Jina 正文读取的候选页面数量限制为默认 5 个，并支持通过配置覆盖。

**Architecture:** 在聚合服务层对证据 URL 做截断，继续沿用现有证据顺序作为“最相关”排序依据。配置项放入 `JinaApiProperties` 与 `application*.yml`，测试覆盖默认值和覆盖值两条路径。

**Tech Stack:** Java 17, Spring Boot 3.3.5, JUnit 5, Mockito, AssertJ

---

### Task 1: 锁定读取上限行为

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] 新增默认只选择前 5 个 URL 的失败测试
- [ ] 新增配置覆盖时按配置值选择 URL 的失败测试
- [ ] 运行对应测试并确认在实现前失败

### Task 2: 增加配置并实现截断逻辑

**Files:**
- Modify: `src/main/java/com/example/face2info/config/JinaApiProperties.java`
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`

- [ ] 在 `JinaApiProperties` 增加 `maxPageReads`，默认值为 `5`
- [ ] 在 `InformationAggregationServiceImpl` 注入 `ApiProperties`
- [ ] 让 `selectTopUrls()` 按配置上限截断并保持原有去重顺序

### Task 3: 同步配置结构

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-git.yml`

- [ ] 在两个配置文件中同步新增 `face2info.api.jina.max-page-reads`

### Task 4: 验证

**Files:**
- Test: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] 运行 `mvn -Dtest=InformationAggregationServiceImplTest test`
- [ ] 确认新增行为通过且无回归
