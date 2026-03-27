# Kimi 真实调用测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增一个手动触发的真实 Kimi 调用测试，通过 Maven 单测入口执行并将返回信息打印到控制台。

**Architecture:** 保持生产代码不变，只修改现有 `KimiSummaryGenerationClientTest`。先补一个默认禁用的真实调用测试并验证其在缺少环境变量时按预期失败，再补最小实现让测试在提供 `KIMI_API_KEY` 时可真实请求并输出结构化结果。

**Tech Stack:** Java 17, JUnit 5, Spring `RestTemplate`, Maven Surefire

---

### Task 1: 在测试类中定义手动真实调用场景

**Files:**
- Modify: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: 写出新的失败测试**

在现有测试类中新增一个默认禁用的方法，名称为 `shouldPrintRealKimiResponse`，先约束入口行为：

```java
@Disabled("手动测试：需要本地 KIMI_API_KEY 并会真实调用外部 API")
@Test
void shouldPrintRealKimiResponse() {
    String apiKey = System.getenv("KIMI_API_KEY");

    assertThat(apiKey)
            .as("运行该测试前需要配置环境变量 KIMI_API_KEY")
            .isNotBlank();

    fail("待补充真实调用实现");
}
```

- [ ] **Step 2: 运行目标测试并确认先红灯**

Run:

```bash
mvn "-Dtest=KimiSummaryGenerationClientTest#shouldPrintRealKimiResponse" test
```

Expected:

- 如果保留 `@Disabled`，Surefire 显示该测试被跳过，说明默认不会误跑
- 临时去掉 `@Disabled` 后再跑一次，若本地未配置 `KIMI_API_KEY`，应因 `isNotBlank()` 断言失败

- [ ] **Step 3: 写入最小实现**

将该测试扩展为真实调用测试，直接构造 `ApiProperties` 与客户端：

```java
String apiKey = System.getenv("KIMI_API_KEY");

assertThat(apiKey)
        .as("运行该测试前需要配置环境变量 KIMI_API_KEY")
        .isNotBlank();

ApiProperties properties = new ApiProperties();
properties.getApi().getSummary().setEnabled(true);
properties.getApi().getSummary().setProvider("kimi");
properties.getApi().getKimi().setApiKey(apiKey);
properties.getApi().getKimi().setMaxRetries(1);

RestTemplate restTemplate = new RestTemplate();
KimiSummaryGenerationClient client =
        new KimiSummaryGenerationClient(restTemplate, properties, new ObjectMapper());

ResolvedPersonProfile profile = client.summarizePerson("周杰伦", List.of(
        new PageContent()
                .setUrl("https://example.com/profile")
                .setContent("周杰伦，华语流行男歌手、音乐人、导演、演员。代表作包括《晴天》《七里香》。")
));

System.out.println("resolvedName=" + profile.getResolvedName());
System.out.println("summary=" + profile.getSummary());
System.out.println("tags=" + profile.getTags());
System.out.println("evidenceUrls=" + profile.getEvidenceUrls());

assertThat(profile.getResolvedName()).isNotBlank();
assertThat(profile.getSummary()).isNotBlank();
```

- [ ] **Step 4: 运行目标测试并确认转绿**

Run:

```bash
mvn "-Dtest=KimiSummaryGenerationClientTest#shouldPrintRealKimiResponse" test
```

Expected:

- 提供有效 `KIMI_API_KEY` 时测试通过
- Maven 控制台打印 `resolvedName`、`summary`、`tags`、`evidenceUrls`
- 未打印敏感信息

- [ ] **Step 5: 提交**

```bash
git add src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java docs/superpowers/specs/2026-03-27-kimi-real-call-test-design.md docs/superpowers/plans/2026-03-27-kimi-real-call-test.md
git commit -m "test(client): 新增 Kimi 真实调用手动测试"
```

### Task 2: 校正文档与执行说明

**Files:**
- Modify: `src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java`

- [ ] **Step 1: 补充测试注释与执行指引**

在真实调用测试方法前补一段简短注释，说明这是手动测试、默认禁用、需单独执行：

```java
/**
 * 手动真实调用测试：
 * 1. 本地配置 KIMI_API_KEY
 * 2. 按需临时去掉 @Disabled
 * 3. 使用 mvn -Dtest=KimiSummaryGenerationClientTest#shouldPrintRealKimiResponse test 单独执行
 */
```

- [ ] **Step 2: 运行相关测试类做回归确认**

Run:

```bash
mvn "-Dtest=KimiSummaryGenerationClientTest" test
```

Expected:

- 原有 mock 测试继续通过
- 新增真实调用测试默认被跳过

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/example/face2info/client/impl/KimiSummaryGenerationClientTest.java
git commit -m "test(client): 补充 Kimi 手动测试说明"
```
