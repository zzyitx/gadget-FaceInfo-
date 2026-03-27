# application-git 配置策略设计文档

## 背景

当前项目本地运行依赖 `src/main/resources/application.yml`。用户希望在该文件中保留真实 key 和本地配置，不再因为 Git 提交而反复删除或改写敏感信息。同时，仓库仍需要保留一份可提交、可推送、结构完整但已经脱敏的配置模板，便于协作者了解配置结构并进行同步维护。

现有状态存在两个问题：

- `application.yml` 同时承担“本地真实配置”和“仓库模板配置”两种职责，容易互相干扰。
- 每次提交配置时，如果直接使用 `application.yml`，就会逼迫本地开发环境删掉真实 key，影响日常使用。

本次需要把这两类职责拆开。

## 目标

- 保留 `src/main/resources/application.yml` 作为本地真实配置文件。
- 新增 `src/main/resources/application-git.yml` 作为仓库内可提交的脱敏配置文件。
- 运行时继续默认读取 `application.yml`，不改变 Spring Boot 默认加载方式。
- Git 提交时只提交 `application-git.yml`，不提交本地真实配置。
- 形成明确规则：每次修改配置时，必须同步更新 git 版配置文件。
- 增加测试或校验，防止 `application-git.yml` 中出现明文 key。

## 非目标

- 不改变 Spring Boot 的默认配置加载机制。
- 不引入新的 profile 体系来替代 `application.yml`。
- 不把本地真实 key 强制迁移到环境变量。
- 不在本次改造中开发自动同步脚本。

## 现有上下文

- 当前 `.gitignore` 已忽略 `src/main/resources/application.yml`
- 项目实际运行依旧依赖 `application.yml`
- `README.md` 和 `AGENTS.md` 已承载配置和协作规则说明
- 控制层已有配置安全测试，会检查配置文件中的 `api-key:` 是否包含明文默认值

## 选定方案

采用“双文件配置策略”：

- 本地运行文件：`src/main/resources/application.yml`
- Git 脱敏文件：`src/main/resources/application-git.yml`

其中：

- `application.yml` 允许保留本地真实 key，仅用于本地运行，不提交到远端
- `application-git.yml` 结构必须与 `application.yml` 保持同步，但所有敏感信息必须脱敏后再提交

## 备选方案对比

### 方案一：`application.yml` + `application-git.yml`（采用）

优点：

- 不改变项目启动方式
- 不影响现有 Spring Boot 默认配置加载
- 文件职责清晰，便于理解
- 与用户当前工作方式一致

缺点：

- 需要维护两份配置同步

### 方案二：`application.yml` + `application-local.yml`

优点：

- 更接近 Spring 常见 profile 习惯

缺点：

- 用户诉求是“本地真实版”和“Git 脱敏版”明确分离，这种命名不够直观
- 仍然不能很好表达“Git 专用模板文件”的职责

### 方案三：完全改成环境变量管理

优点：

- 安全性更高

缺点：

- 不是当前用户要的工作模式
- 会增加本地开发维护成本

## 文件设计

### 本地真实配置

文件路径：

- `src/main/resources/application.yml`

职责：

- 本地开发和运行时真实生效的配置
- 可以保留真实 key、私有地址、代理等本地敏感项

规则：

- 保持被 `.gitignore` 忽略
- 不允许提交到远端

### Git 脱敏配置

文件路径：

- `src/main/resources/application-git.yml`

职责：

- 向仓库提交的配置模板
- 作为协作者查看配置结构和更新配置项的基准文件

规则：

- 必须提交到远端
- 所有敏感值必须脱敏
- 结构必须与本地 `application.yml` 保持一致

## 脱敏规则

以下字段在 `application-git.yml` 中必须脱敏：

- `api-key`
- 私有代理账号或认证信息
- 本地专用地址
- 任何不能公开的认证凭据

推荐写法：

- key 使用空占位，例如 `${SERP_API_KEY:}`
- 可公开的默认值可以保留，例如公共 API 地址、超时、线程池参数

禁止做法：

- 在 `application-git.yml` 中保留明文 key
- 为了提交而修改本地 `application.yml` 中的真实 key

## Git 与忽略规则

`.gitignore` 需要满足以下状态：

- 忽略 `src/main/resources/application.yml`
- 不忽略 `src/main/resources/application-git.yml`

如果后续配置目录再新增本地专用文件，也要遵循同样原则：本地真实文件忽略，Git 版模板文件提交。

## 协作流程

每次配置变更必须按以下顺序执行：

1. 修改本地真实配置 `application.yml`
2. 将相同结构的变更同步到 `application-git.yml`
3. 对 `application-git.yml` 执行脱敏检查
4. Git 提交时只提交 `application-git.yml`

这条流程需要同步写入：

- `AGENTS.md`
- `README.md`

## 测试与校验

需要把现有配置安全测试切换为检查 `application-git.yml`，重点覆盖：

- `api-key:` 不允许出现明文默认值
- Git 版配置文件必须存在
- Git 版配置文件包含主要配置段，例如：
  - `serp`
  - `news`
  - `jina`
  - `kimi`
  - `summary`

本次不做自动对比脚本，但测试中至少要保证 Git 版文件存在且安全。

## 预期改动文件

配置文件：

- `src/main/resources/application-git.yml`

忽略规则：

- `.gitignore`

文档：

- `README.md`
- `AGENTS.md`

测试：

- `src/test/java/com/example/face2info/controller/FaceInfoControllerTest.java`
  - 或新增专门的配置文件测试

## 已确认的设计决策

- 运行时默认继续读取 `application.yml`
- `application-git.yml` 只作为仓库脱敏配置副本
- 本地真实配置不提交、不推送
- 每次配置调整都必须同步更新 Git 版文件

## 实施注意事项

- 不要在本次改造中误改 Spring Boot 配置加载入口
- 不要把 `application-git.yml` 当作运行时配置文件引用
- 不要删除用户本地 `application.yml` 中的真实 key
- 提交前必须确认 `application.yml` 仍被忽略，`application-git.yml` 已被纳入版本控制
