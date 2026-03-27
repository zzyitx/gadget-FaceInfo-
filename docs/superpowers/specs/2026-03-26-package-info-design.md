# package-info.java 补回设计

## 概述

本次改动目标是为主业务包重新补回 `package-info.java`，恢复包级职责说明文档，便于阅读代码结构和维护分层边界。

本次范围限定为主业务包，不扩展到实现子包：

- `com.example.face2info.client`
- `com.example.face2info.config`
- `com.example.face2info.controller`
- `com.example.face2info.entity`
- `com.example.face2info.entity.internal`
- `com.example.face2info.entity.response`
- `com.example.face2info.exception`
- `com.example.face2info.service`
- `com.example.face2info.util`

## 设计原则

- 每个包补充一个 `package-info.java`
- 文件内容统一使用中文
- 只包含简短的包级 Javadoc 和 `package` 声明
- 不在 `package-info.java` 中添加额外注解
- 不修改现有业务代码与运行逻辑

## 内容风格

包说明保持简洁、职责明确，重点描述：

- 该包承担什么职责
- 与项目分层的关系
- 是否面向外部接口、内部流转或基础设施

避免写过长的背景介绍，也不重复类级注释内容。

## 风险控制

这是一次文档结构恢复，不应引入行为变化。

需要确保：

- 包声明与目录结构完全一致
- 不覆盖或干扰已有源码文件
- 保持 UTF-8 中文说明

## 验证方式

最小验证为：

- 相关 `package-info.java` 文件均已创建
- `mvn -DskipTests compile` 能通过编译

## 非本次范围

- 不补 `client.impl`、`service.impl` 等实现子包
- 不调整 README
- 不修改类注释
- 不引入测试代码
