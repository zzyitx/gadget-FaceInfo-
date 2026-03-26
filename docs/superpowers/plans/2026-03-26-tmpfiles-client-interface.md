# TmpfilesClient 接口化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 将 `TmpfilesClient` 重构为接口加实现类，并保持现有识别流程行为不变，同时明确内部模型和响应模型不引入数据库实体注解。

**架构：** 保持现有 `client` 分层模式，在 `client` 包中定义接口，在 `client.impl` 包中承载第三方调用实现。调用方继续只依赖抽象接口，避免直接依赖具体实现类。

**技术栈：** Java 17、Spring Boot 3.3.5、JUnit 5、Mockito、Maven

---

## 文件结构

- 修改：`src/main/java/com/example/face2info/client/TmpfilesClient.java`
  - 由具体类调整为接口，保留上传能力方法签名。
- 新建：`src/main/java/com/example/face2info/client/impl/TmpfilesClientImpl.java`
  - 承载 tempfile 上传实现、响应解析、异常处理。
- 修改：`src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java`
  - 继续依赖 `TmpfilesClient` 接口，必要时只调整导入和构造器注入兼容性。
- 修改：`src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
  - 保持接口 mock 的测试方式，补充接口化后的回归验证。
- 可选验证：`src/main/java/com/example/face2info/entity/internal/*`
  - 仅确认不做数据库实体化改动，不实际修改。
- 可选验证：`src/main/java/com/example/face2info/entity/response/*`
  - 仅确认不做数据库实体化改动，不实际修改。

### 任务 1：先写失败测试，锁定接口化后的协作方式

**文件：**
- 修改：`src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`
- 测试：`src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java`

- [ ] **步骤 1：补一条聚焦接口协作的失败测试**

```java
@Test
void shouldUploadImageThroughTmpfilesClientInterface() throws Exception {
    MockMultipartFile image = new MockMultipartFile("image", "face.jpg", "image/jpeg", new byte[]{1, 2, 3});
    when(tmpfilesClient.uploadImage(image)).thenReturn(PREVIEW_URL);
    when(serpApiClient.reverseImageSearchByUrl(PREVIEW_URL)).thenReturn(new SerpApiResponse()
            .setRoot(objectMapper.readTree("""
                    { "knowledge_graph": { "title": "Lei Jun" } }
                    """)));

    FaceRecognitionServiceImpl service = new FaceRecognitionServiceImpl(serpApiClient, nameExtractor, tmpfilesClient);

    assertThat(service.recognize(image).getName()).isEqualTo("Lei Jun");
}
```

- [ ] **步骤 2：运行单测并确认当前失败原因正确**

运行：

```bash
mvn -Dtest=FaceRecognitionServiceImplTest test
```

预期：

- 如果当前生产代码没有显式构造器，会出现编译失败或测试失败
- 失败点应当与 `FaceRecognitionServiceImplTest` 中通过构造器注入创建服务有关

- [ ] **步骤 3：确认失败不是测试书写错误**

检查点：

- `tmpfilesClient` 使用接口类型 mock
- `serpApiClient` 返回的 JSON 能命中知识图谱分支
- 断言聚焦识别结果而不是实现细节

### 任务 2：最小实现 TmpfilesClient 接口化

**文件：**
- 修改：`src/main/java/com/example/face2info/client/TmpfilesClient.java`
- 新建：`src/main/java/com/example/face2info/client/impl/TmpfilesClientImpl.java`
- 修改：`src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java`

- [ ] **步骤 1：把 `TmpfilesClient` 改为接口**

```java
package com.example.face2info.client;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;

public interface TmpfilesClient {

    String uploadImage(File image);

    String uploadImage(MultipartFile image);
}
```

- [ ] **步骤 2：新增 `TmpfilesClientImpl` 承载原有实现**

```java
package com.example.face2info.client.impl;

import com.example.face2info.client.TmpfilesClient;
import com.example.face2info.exception.ApiCallException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class TmpfilesClientImpl implements TmpfilesClient {
    // 保留原有上传与解析逻辑
}
```

- [ ] **步骤 3：如果服务实现尚未提供构造器注入，则补齐最小构造器**

```java
public FaceRecognitionServiceImpl(SerpApiClient serpApiClient, NameExtractor nameExtractor, TmpfilesClient tmpfilesClient) {
    this.serpApiClient = serpApiClient;
    this.nameExtractor = nameExtractor;
    this.tmpfilesClient = tmpfilesClient;
}
```

- [ ] **步骤 4：运行单测验证接口化实现通过**

运行：

```bash
mvn -Dtest=FaceRecognitionServiceImplTest test
```

预期：

- `FaceRecognitionServiceImplTest` 全部通过

- [ ] **步骤 5：提交本任务**

```bash
git add src/main/java/com/example/face2info/client/TmpfilesClient.java src/main/java/com/example/face2info/client/impl/TmpfilesClientImpl.java src/main/java/com/example/face2info/service/impl/FaceRecognitionServiceImpl.java src/test/java/com/example/face2info/service/impl/FaceRecognitionServiceImplTest.java
git commit -m "refactor(client): extract tmpfiles client interface"
```

### 任务 3：回归验证并确认实体模型边界不变

**文件：**
- 验证：`src/main/java/com/example/face2info/entity/internal/AggregationResult.java`
- 验证：`src/main/java/com/example/face2info/entity/internal/NewsApiResponse.java`
- 验证：`src/main/java/com/example/face2info/entity/internal/PersonAggregate.java`
- 验证：`src/main/java/com/example/face2info/entity/response/FaceInfoResponse.java`
- 验证：`src/main/java/com/example/face2info/entity/response/PersonInfo.java`

- [ ] **步骤 1：确认实体与 DTO 不引入数据库实体注解**

检查点：

- 不新增 `@TableName`
- 不新增 `@Autowired`
- 不把响应模型注册为 Spring Bean

- [ ] **步骤 2：运行完整验证命令**

运行：

```bash
mvn clean verify
```

预期：

- 构建成功
- 测试通过
- 没有因为 `TmpfilesClient` 接口化导致的装配或编译错误

- [ ] **步骤 3：提交验证结果**

```bash
git add docs/superpowers/specs/2026-03-26-tmpfiles-client-interface-design.md docs/superpowers/plans/2026-03-26-tmpfiles-client-interface.md
git commit -m "docs: add tmpfiles client refactor plan"
```
