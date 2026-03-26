# package-info.java 回补实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为主业务包补回 `package-info.java`，恢复包级职责说明文档，并确保工程可正常编译。

**Architecture:** 本次改动只新增包级说明文件，不修改现有业务类与运行逻辑。所有新增文件统一采用中文 Javadoc 加 `package` 声明的最小形式，保持结构清晰、职责明确。

**Tech Stack:** Java 17、Spring Boot 3.3.5、Maven

---

## 文件结构

- Create: `src/main/java/com/example/face2info/client/package-info.java`
- Create: `src/main/java/com/example/face2info/config/package-info.java`
- Create: `src/main/java/com/example/face2info/controller/package-info.java`
- Create: `src/main/java/com/example/face2info/entity/package-info.java`
- Create: `src/main/java/com/example/face2info/entity/internal/package-info.java`
- Create: `src/main/java/com/example/face2info/entity/response/package-info.java`
- Create: `src/main/java/com/example/face2info/exception/package-info.java`
- Create: `src/main/java/com/example/face2info/service/package-info.java`
- Create: `src/main/java/com/example/face2info/util/package-info.java`

### Task 1: 补齐主业务包的 package-info.java

**Files:**
- Create: `src/main/java/com/example/face2info/client/package-info.java`
- Create: `src/main/java/com/example/face2info/config/package-info.java`
- Create: `src/main/java/com/example/face2info/controller/package-info.java`
- Create: `src/main/java/com/example/face2info/entity/package-info.java`
- Create: `src/main/java/com/example/face2info/entity/internal/package-info.java`
- Create: `src/main/java/com/example/face2info/entity/response/package-info.java`
- Create: `src/main/java/com/example/face2info/exception/package-info.java`
- Create: `src/main/java/com/example/face2info/service/package-info.java`
- Create: `src/main/java/com/example/face2info/util/package-info.java`

- [ ] **Step 1: 新增 `client` 包说明文件**

```java
/**
 * 第三方平台客户端抽象与访问入口。
 */
package com.example.face2info.client;
```

- [ ] **Step 2: 新增 `config` 包说明文件**

```java
/**
 * 应用配置与基础设施装配。
 */
package com.example.face2info.config;
```

- [ ] **Step 3: 新增 `controller` 包说明文件**

```java
/**
 * HTTP 接口入口与请求处理。
 */
package com.example.face2info.controller;
```

- [ ] **Step 4: 新增 `entity` 包说明文件**

```java
/**
 * 数据模型根包，包含内部流转模型与对外响应模型。
 */
package com.example.face2info.entity;
```

- [ ] **Step 5: 新增 `entity.internal` 包说明文件**

```java
/**
 * 服务内部聚合流程使用的中间模型。
 */
package com.example.face2info.entity.internal;
```

- [ ] **Step 6: 新增 `entity.response` 包说明文件**

```java
/**
 * 对外 HTTP API 响应模型。
 */
package com.example.face2info.entity.response;
```

- [ ] **Step 7: 新增 `exception` 包说明文件**

```java
/**
 * 业务异常定义与统一异常处理。
 */
package com.example.face2info.exception;
```

- [ ] **Step 8: 新增 `service` 包说明文件**

```java
/**
 * 核心业务服务抽象与流程编排入口。
 */
package com.example.face2info.service;
```

- [ ] **Step 9: 新增 `util` 包说明文件**

```java
/**
 * 通用工具与无状态辅助能力。
 */
package com.example.face2info.util;
```

### Task 2: 验证新增文件不影响编译

**Files:**
- Verify: `src/main/java/com/example/face2info/client/package-info.java`
- Verify: `src/main/java/com/example/face2info/config/package-info.java`
- Verify: `src/main/java/com/example/face2info/controller/package-info.java`
- Verify: `src/main/java/com/example/face2info/entity/package-info.java`
- Verify: `src/main/java/com/example/face2info/entity/internal/package-info.java`
- Verify: `src/main/java/com/example/face2info/entity/response/package-info.java`
- Verify: `src/main/java/com/example/face2info/exception/package-info.java`
- Verify: `src/main/java/com/example/face2info/service/package-info.java`
- Verify: `src/main/java/com/example/face2info/util/package-info.java`

- [ ] **Step 1: 运行最小编译验证**

运行：

```bash
mvn -DskipTests compile
```

预期：

- 编译成功
- 新增 `package-info.java` 不引入语法或包声明错误

- [ ] **Step 2: 复核新增文件内容**

检查点：

- 每个文件都只包含中文 Javadoc 和 `package` 声明
- 包名与目录结构完全一致
- 没有引入额外注解或业务代码

- [ ] **Step 3: 提交本任务**

```bash
git add src/main/java/com/example/face2info/client/package-info.java src/main/java/com/example/face2info/config/package-info.java src/main/java/com/example/face2info/controller/package-info.java src/main/java/com/example/face2info/entity/package-info.java src/main/java/com/example/face2info/entity/internal/package-info.java src/main/java/com/example/face2info/entity/response/package-info.java src/main/java/com/example/face2info/exception/package-info.java src/main/java/com/example/face2info/service/package-info.java src/main/java/com/example/face2info/util/package-info.java
git commit -m "docs(package): 补回主业务包 package-info 说明"
```
