# Kimi 兜底总结 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Jina 读取失败或空结果时，仍基于已有 `WebEvidence` 构造输入并调用 Kimi 完成总结。

**Architecture:** 保持现有 `SummaryGenerationClient` 接口不变，只调整 `InformationAggregationServiceImpl` 的总结输入准备逻辑。优先使用 Jina 正文，失败时降级为证据页 `PageContent`，并保留现有 Kimi 失败 warning 策略。

**Tech Stack:** Java 17, Spring Boot 3.3, JUnit 5, Mockito

---

### Task 1: 补充失败测试

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: 写出 Jina 异常场景的失败测试**
- [ ] **Step 2: 运行单测并确认因未调用 Kimi 或参数不符而失败**
- [ ] **Step 3: 写出 Jina 空结果场景的失败测试**
- [ ] **Step 4: 运行单测并确认失败**

### Task 2: 调整总结输入准备逻辑

**Files:**
- Modify: `src/main/java/com/example/face2info/service/impl/InformationAggregationServiceImpl.java`

- [ ] **Step 1: 提取“选择总结页面”逻辑，优先使用 Jina 结果**
- [ ] **Step 2: 新增从 `WebEvidence` 构造兜底 `PageContent` 的最小实现**
- [ ] **Step 3: 保持 Kimi 失败 warning 行为不变**
- [ ] **Step 4: 运行新增测试并确认通过**

### Task 3: 回归验证

**Files:**
- Modify: `src/test/java/com/example/face2info/service/impl/InformationAggregationServiceImplTest.java`

- [ ] **Step 1: 运行 `InformationAggregationServiceImplTest` 全量测试**
- [ ] **Step 2: 如有回归，最小修正实现或测试数据**
- [ ] **Step 3: 记录无法在当前环境完成的验证项**
