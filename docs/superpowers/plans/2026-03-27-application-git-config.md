# application-git 配置策略实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 `application-git.yml` 作为可提交脱敏配置副本，保留 `application.yml` 作为本地真实配置，并把提交规则、忽略规则和测试全部切换到 git 版配置文件。

**Architecture:** 运行时继续只读取 `application.yml`，不改 Spring Boot 配置加载行为。仓库内新增 `application-git.yml` 作为结构同步、内容脱敏的提交模板，同时把 `.gitignore`、文档和测试统一切到“双配置文件策略”。

**Tech Stack:** Java 17、Spring Boot 3.3.5、Maven、JUnit 5、Spring Boot Test、YAML 配置文件

---

## 文件结构与职责

- `src/main/resources/application.yml`
  继续作为本地运行时真实配置文件，允许保留真实 key，不提交到远端。
- `src/main/resources/application-git.yml`
  新增仓库脱敏配置副本，结构与 `application.yml` 同步，所有敏感值必须脱敏，可提交。
- `.gitignore`
  继续忽略 `application.yml`，明确允许 `application-git.yml` 进入版本控制。
- `README.md`
  说明运行时读取 `application.yml`，提交时只提交 `application-git.yml`。
- `AGENTS.md`
  写入配置双文件策略，约束后续代理和协作者的配置修改流程。
- `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
  把现有配置安全校验从 `application.yml` 切换为 `application-git.yml`。

### Task 1: 建立 git 版配置文件并补安全测试

**Files:**
- Create: `src/main/resources/application-git.yml`
- Modify: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`

- [ ] **Step 1: 先写校验 `application-git.yml` 的失败测试**

```java
@Test
void shouldNotCommitFallbackSecretsInApplicationGitConfig() throws Exception {
    Path path = Path.of("src/main/resources/application-git.yml");

    assertThat(path).exists();
    assertThat(Files.readAllLines(path))
            .filteredOn(line -> line.contains("api-key:"))
            .allMatch(line -> line.matches(".*\\$\\{[A-Z0-9_]+:}\\s*$"),
                    "Git version config must not contain committed fallback secrets");
}
```

- [ ] **Step 2: 运行测试，确认因文件不存在而失败**

Run: `mvn -Dtest=FaceInfoControllerTest#shouldNotCommitFallbackSecretsInApplicationGitConfig test`
Expected: FAIL，报错为 `src/main/resources/application-git.yml` 不存在。

- [ ] **Step 3: 最小实现脱敏配置副本**

```yaml
# src/main/resources/application-git.yml
server:
  port: 8080

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

face2info:
  api:
    serp:
      base-url: https://serpapi.com/search.json
      api-key: ${SERP_API_KEY:}
      bing-market: en-US
      connect-timeout-ms: 5000
      read-timeout-ms: 10000
      max-retries: 3
      backoff-initial-ms: 300
    news:
      base-url: https://newsapi.org/v2/everything
      api-key: ${NEWS_API_KEY:}
      language: zh
      sort-by: relevancy
      page-size: 10
      connect-timeout-ms: 5000
      read-timeout-ms: 10000
      max-retries: 3
      backoff-initial-ms: 300
    jina:
      base-url: https://r.jina.ai/http://
      api-key: ${JINA_API_KEY:}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
      max-retries: 2
      backoff-initial-ms: 300
    kimi:
      base-url: ${KIMI_API_BASE_URL:https://api.moonshot.cn/v1/chat/completions}
      api-key: ${KIMI_API_KEY:}
      model: ${KIMI_MODEL:moonshot-v1-8k}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
      max-retries: 2
      backoff-initial-ms: 300
      system-prompt: ${KIMI_SYSTEM_PROMPT:你是一个人物信息抽取助手，只能输出JSON。}
    summary:
      enabled: false
      provider: noop
      base-url: ${SUMMARY_API_BASE_URL:}
      api-key: ${SUMMARY_API_KEY:}
      model: ${SUMMARY_MODEL:}
      connect-timeout-ms: 5000
      read-timeout-ms: 15000
    proxy:
      enabled: false
      host:
      port:
  async:
    core-pool-size: 8
    max-pool-size: 16
    queue-capacity: 100
    keep-alive-seconds: 60
    thread-name-prefix: face2info-

springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html

logging:
  pattern:
    level: "%5p [%X{requestId:-}]"
```

```java
// src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
@Test
void shouldNotCommitFallbackSecretsInApplicationGitConfig() throws Exception {
    Path path = Path.of("src/main/resources/application-git.yml");

    assertThat(path).exists();
    assertThat(Files.readAllLines(path))
            .filteredOn(line -> line.contains("api-key:"))
            .allMatch(line -> line.matches(".*\\$\\{[A-Z0-9_]+:}\\s*$"),
                    "Git version config must not contain committed fallback secrets");
}
```

- [ ] **Step 4: 运行目标测试，确认通过**

Run: `mvn -Dtest=FaceInfoControllerTest#shouldNotCommitFallbackSecretsInApplicationGitConfig test`
Expected: PASS

- [ ] **Step 5: 补一个配置段存在性测试**

```java
@Test
void shouldContainPrimaryApiSectionsInApplicationGitConfig() throws Exception {
    String content = Files.readString(Path.of("src/main/resources/application-git.yml"));

    assertThat(content).contains("serp:");
    assertThat(content).contains("news:");
    assertThat(content).contains("jina:");
    assertThat(content).contains("kimi:");
    assertThat(content).contains("summary:");
}
```

- [ ] **Step 6: 运行控制层测试类**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/application-git.yml src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git commit -m "test(config): 新增application-git脱敏配置校验"
```

### Task 2: 固化忽略规则与双文件配置文档

**Files:**
- Modify: `.gitignore`
- Modify: `README.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: 先写失败测试，检查 `.gitignore` 允许 git 版配置提交**

```java
@Test
void shouldTrackApplicationGitConfigAndIgnoreLocalApplicationConfig() throws Exception {
    String ignoreContent = Files.readString(Path.of(".gitignore"));

    assertThat(ignoreContent).contains("src/main/resources/application.yml");
    assertThat(ignoreContent).contains("!src/main/resources/application-git.yml");
}
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `mvn -Dtest=FaceInfoControllerTest#shouldTrackApplicationGitConfigAndIgnoreLocalApplicationConfig test`
Expected: FAIL，报错为 `.gitignore` 尚未包含 `!src/main/resources/application-git.yml`。

- [ ] **Step 3: 最小实现忽略规则**

```gitignore
# .gitignore
src/main/resources/application-local.yml
src/main/resources/application.yml
!src/main/resources/application-git.yml
```

- [ ] **Step 4: 更新 README 双文件策略说明**

```md
## 配置文件策略

- 运行时默认读取 `src/main/resources/application.yml`
- `application.yml` 只作为本地真实配置，允许保留本地 key，不提交到远端
- `src/main/resources/application-git.yml` 是仓库中的脱敏配置副本
- 每次配置调整时，必须先修改本地 `application.yml`，再同步更新 `application-git.yml`
- Git 提交时只提交 `application-git.yml`，不得提交本地真实配置
```

- [ ] **Step 5: 更新 AGENTS 双文件协作规则**

```md
## 配置文件双轨规则

- `src/main/resources/application.yml` 为本地真实配置文件，允许保留真实 key，不提交到 Git。
- `src/main/resources/application-git.yml` 为仓库脱敏配置文件，结构必须与本地版保持同步。
- 任何配置变更都必须同时更新这两个文件中的结构，提交时只提交 `application-git.yml`。
- 代理和协作者禁止为了提交而删除本地 `application.yml` 中的真实 key。
```

- [ ] **Step 6: 运行新增测试**

Run: `mvn -Dtest=FaceInfoControllerTest#shouldTrackApplicationGitConfigAndIgnoreLocalApplicationConfig test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add .gitignore README.md AGENTS.md src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git commit -m "docs(config): 固化application双文件配置规则"
```

### Task 3: 最终联调与完整校验

**Files:**
- Modify: `src/main/resources/application-git.yml`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`

- [ ] **Step 1: 手工对齐 git 版配置结构**

```yaml
# 检查并对齐这几个配置段的字段顺序和键名
face2info:
  api:
    serp:
    news:
    jina:
    kimi:
    summary:
    proxy:
  async:
```

- [ ] **Step 2: 运行控制层测试**

Run: `mvn -Dtest=FaceInfoControllerTest test`
Expected: PASS

- [ ] **Step 3: 运行完整校验**

Run: `mvn clean verify`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application-git.yml README.md AGENTS.md src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java
git commit -m "feat(config): 新增application-git脱敏配置副本"
```

