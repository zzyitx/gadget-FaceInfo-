# TmpfilesClient 接口化设计

## 概述

本次改动的目标，是让 `TmpfilesClient` 与现有的 `NewsApiClient`、`SerpApiClient` 保持一致，统一采用“接口定义 + `impl` 实现类”的客户端分层方式。

本次范围保持收敛，只做以下内容：

- 在 `client` 包中保留 `TmpfilesClient` 接口
- 在 `client.impl` 包中新增 `TmpfilesClientImpl`
- 更新调用方，改为依赖接口注入
- `entity.internal` 与 `entity.response` 继续保持普通数据模型，不引入数据库实体语义

## 架构方案

`TmpfilesClient` 作为临时文件上传能力的对外抽象，负责声明“上传图片并返回可访问预览地址”的能力边界。

`TmpfilesClientImpl` 负责以下实现细节：

- Spring Bean 注册
- `RestTemplate`、`ObjectMapper` 等依赖注入
- multipart 上传请求构造
- tempfile 返回结果解析
- 异常包装与日志记录

这样可以继续满足项目当前的分层约束：第三方调用统一收敛在 `client` 层，不把外部服务访问逻辑散落到 `service` 中。

## 实体与 DTO 注解决策

当前 `entity.internal` 下的类用于服务内部流转，`entity.response` 下的类用于接口响应输出。它们都不是数据库持久化实体。

因此本次明确不做以下事情：

- 不添加 `@TableName`
- 不把 DTO 或内部模型声明为 Spring Bean
- 不在模型类上使用 `@Autowired`

如果后续项目引入 MyBatis-Plus 或数据库落库能力，应新增独立的持久化实体包，而不是复用当前的响应模型或内部聚合模型。

## 错误处理

本次重构不引入行为变化。

`TmpfilesClientImpl` 必须保持当前语义不变：

- 支持上传本地 `File`
- 支持上传 `MultipartFile`
- 能从 tempfile 返回体中提取预览地址
- 返回体异常或解析失败时抛出 `ApiCallException`

## 测试要求

这次改动虽然以结构调整为主，但仍需覆盖回归验证。

最少需要验证：

- 接口化后现有测试仍可编译并通过
- `FaceRecognitionServiceImpl` 仍然通过 `TmpfilesClient` 接口完成协作
- Spring 上下文能够正确装配 `TmpfilesClient` 实现

## 非本次范围

- 不引入数据库实体注解
- 不修改对外响应结构
- 不变更 tempfile 服务交互协议
- 不进行大范围 Lombok 改造
