# Data 与底层存储架构规范

> 本文定义业务数据模块（`:data:*`）与技术存储模块（`:core:*`）的分层、公开 API、
> 可见性和依赖规则；本文解决“数据操作究竟应放在哪里”。与 Feature、Domain、Agent 的边界
> 分别见 [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)、
> [DOMAIN_ARCHITECTURE.md](DOMAIN_ARCHITECTURE.md) 与
> [AGENT_ARCHITECTURE.md](AGENT_ARCHITECTURE.md)。

## 1. 目标与基本判断

本项目不采用“一个巨大 `:data` 模块”的方式，也不为每个 Repository 固定创建
`api`／`impl` Gradle 子模块。采用两级结构：

```text
:feature:*  -> :domain:* -> :data:<business-area> -> :core:<technology>
```

- `:data:<business-area>` 按业务资源划分，拥有 Repository 合同和该资源的默认实现。
- `:core:<technology>` 按业务无关的技术能力划分，只解决“如何存取”，不解释“为何存取”。
- `:domain:<area>` 协调多个 Repository 的业务操作、授权、恢复、顺序和跨资源一致性。

判断代码位置时，按顺序提问：

1. API 是否出现 `Project`、`Session`、`Template`、`Theme`、Provider 等业务概念？是则属于
   相应 `:data:<area>`。
2. 是否需要协调两个以上资源、做业务校验或定义恢复流程？是则属于 `:domain:<area>`。
3. 是否只封装 Room、DataStore、文件、Git、Keystore 或 HTTP，且不理解业务语义？是则属于
   对应 `:core:<technology>`。
4. 仅被一个 data 模块使用、尚未证明可复用的技术 helper，先放该 data 模块的 `internal`
   包；不要预先创建 core API。

## 2. 当前模块关系

```text
:data:project ───┬─> :core:filesystem
                 ├─> :core:git
                 └─> :core:database                 （按需接入）

:data:session ─────> :core:database                 （按需接入）
:data:template ────> :core:filesystem / :core:network（按需接入）
:data:settings ────> :core:datastore                （共享原语出现后接入）
:data:ai-config ───> :core:datastore / :core:secure-storage（按需接入）

:domain:* ────────> :data:* / :core:common / :core:model / :contract:agent
:feature:* ───────> :domain:* / UI 相关 :core:*
```

“按需接入”很重要：已创建的 core 技术模块目前只建立了可验证的 Gradle 边界和职责，
没有虚构 Room schema、Git facade 或网络 client。第一个真实 vertical slice 需要某项能力时，
才在相应 core 模块实现并让 data 模块依赖它。

## 3. `:data:<area>` 的职责

### 3.1 资源所有权

| 模块 | 资源 owner | 下层技术能力 |
| --- | --- | --- |
| `:data:project` | 项目元数据、工作区文件、导入导出、项目锁、版本快照与 Preview 日志 | filesystem、git、database |
| `:data:template` | 模板索引、内容、本地资产和远端模板来源 | filesystem、network、database |
| `:data:session` | 会话、消息、turn 状态与恢复记录 | database |
| `:data:settings` | 主题和应用语言偏好 | datastore、AppCompat locale |
| `:data:ai-config` | Provider 配置、已选模型、密钥引用与配置状态 | datastore、secure-storage |

一个 data 模块负责自身资源的原子性、并发控制、迁移和存储异常映射。例如 workspace
可以保证一次文件写入是原子的，但“创建项目后再建立首个版本快照”的业务顺序属于
`domain:project`。

### 3.2 Repository 的公开 API

Repository 是 domain 使用的数据资源合同，不是 UI API。每个公开方法必须有资源范围，
通常是 `ProjectId`、`SessionId` 等 `:core:common` value class；不得以绝对路径、数据库
主键或 Android `Uri` 作为跨层身份。

允许的返回：

- 长期观察：`Flow<稳定业务模型>` 或 `Flow<List<稳定业务模型>>`；列表和元素必须不可变。
- 可选查询：`T?` 或 `Flow<T?>`，仅在“资源不存在是调用者的正常分支”时使用。
- 必需读取与写操作：`suspend fun ...: OperationResult<T>`，或项目统一约定的明确结果类型。
- 无业务返回值的成功操作：`OperationResult<Unit>`，而不是以 Boolean 混淆失败原因。

不允许从 Repository 返回：

- `MutableStateFlow`、可变集合、Room `Entity`／`Dao`、网络 response／DTO；
- `File`、绝对路径、`Uri`、`Context`、DataStore `Preferences`、Git／SDK client；
- 原始异常、未脱敏日志内容、密钥、token 或凭据本身；
- Compose、资源 ID、已本地化文案或 UI state。

Repository 可以定义资源专属 request／result DTO；它们只可被 domain 使用，不能穿过
domain 的 public use case API 到 feature。确实跨多个独立业务区稳定的模型才进入
`:core:model`。

### 3.3 模块内可见性：最小优先

一个 data 模块的默认形态只有公开合同、一个内部实现和需要时的 DI binding：

```text
:data:project/src/main/kotlin/.../data/project/
  ProjectStorageLayout.kt        // internal
  project/
    ProjectRepository.kt        // public
    DefaultProjectRepository.kt // internal
    ProjectDataModule.kt        // internal；仅需要 Hilt binding 时创建
  workspace/
    WorkspaceRepository.kt      // public
  version/
    VersionRepository.kt        // public
  preview/log/
    PreviewLogRepository.kt     // public
```

`project`、`workspace`、`version`、`preview.log` 按资源分包，但它们仍属于同一个项目 aggregate；跨这些资源的
物理目录布局由 aggregate root 中的 internal 类型共享。不要为了层次感预建 `local/`、`remote/`、`mapper/` 或 `tool/` 目录。只有一个文件已经
明显承担独立职责时才提取，例如：Repository 同时读 Room 和网络时再增加 internal
`LocalProjectSource`／`RemoteProjectSource`；存在多个非平凡转换时再增加 mapper；真正实现
Agent 工具时再增加 tool 文件或包。目录是代码复杂度的结果，不是提交代码前必须满足的模板。

无论文件放在哪里，以下规则固定：

- Repository interface、资源 DTO 和必要的错误类型是刻意设计的 public API。
- 默认实现、额外 data source、mapper、缓存、DataStore key 和 Hilt binding 必须为
  `internal`。
- `:core:database` 的 DAO／entity、`:core:network` 的 transport DTO 等可被 data 编译，
  但永远不得泄露到 Repository 签名。
- Feature 不注入 Repository；ViewModel 只请求 domain use case。

### 3.4 错误、取消与并发

- 可预期失败（缺少资源、冲突、校验失败、无配置）在 data 边界映射为稳定的
  `OperationResult.Failure(AppError)`／错误 code。
- 第三方、存储或网络异常在最接近的 adapter 处脱敏记录一次，再映射为安全错误；不把同一
  异常在 data、domain、feature 重复记录。
- `CancellationException` 必须原样重新抛出，不得转换为失败或错误日志。
- 单资源锁属于 resource owner。例如项目工作区文件锁由 `:data:project` 管理；跨项目
  初始化、快照和会话恢复的协调由 domain 管理。
- 写操作应明确幂等性、原子写边界与失败后的可恢复状态；不能把部分成功伪装成成功。

## 4. 技术 core 模块的职责

技术 core 默认不依赖任何项目模块，绝不依赖 `:data:*`、domain、feature、agent、app 或
shell。只有某项技术能力确实要建立在另一项更基础的 core 技术能力上时，才允许依赖更低层
`:core:*`；该例外必须在两个模块 README 和架构文档中说明。它们不定义 Repository，也不接收
业务 use case。

### 4.1 `:core:database`

负责 Room database 实例、entity、DAO、migration、事务和数据库 provider。它的任务是
持久化结构与 SQL 原子性；不应包含 `ProjectRepository`、会话生命周期或版本恢复策略。

- entity 是存储模型，不是 `:core:model` 业务模型，也不能流向 feature。
- DAO 可返回 entity／存储投影，但只能被 data adapter 消费。
- migration 必须可测试，并且数据 schema 变化需要版本和迁移策略。
- data mapper 负责 entity 与 Repository 模型之间的双向转换。

### 4.2 `:core:datastore`

负责 DataStore 创建、serializer、通用读写原语和迁移基础。不解释某个 key 的产品含义。

- `ThemeMode`、`ThemePalette`、Provider 配置等 key 及其默认业务语义由相应 data 模块拥有。
- 仅在多个 data 模块需要共享 DataStore 创建、加密或通用序列化逻辑时下沉。
- DataStore 文件名、scope、损坏处理和 serializer 由此模块统一；资源级 mapping 保留 data。
- 不返回 Android UI locale、Repository 或本地化文案。

### 4.3 `:core:filesystem`

负责经过校验的相对路径、原子读写、目录创建／列举、临时文件、移动与文件锁等基础操作。

- 只接受受控 root 与相对路径；不得提供“任意绝对路径读写”的模型可见 API。
- 不知道 `ProjectId`、项目目录布局、导入格式、模板或版本快照。
- workspace data 模块把 `ProjectId` 映射为受控工作区 root，再调用 filesystem。
- 所有写操作应有明确覆盖、原子替换和取消语义。

### 4.4 `:core:git`

负责窄 Git adapter：初始化 repository、stage/commit、读取 revision／status、checkout 或恢复
指定 revision。它不决定“何时应该提交”。

- 不接收项目业务对象；调用方传入已受控的工作区位置与技术参数。
- commit message、快照命名、恢复前授权、审计记录和失败后 journal 属于 data/domain。
- Git 库类型、命令输出和异常不得逃出模块；对 data 暴露稳定技术结果。
- 禁止执行未验证的任意 shell command。

### 4.5 `:core:secure-storage`

负责 Android Keystore-backed 加密、密钥 alias 生命周期和安全字节／字符串存取原语。

- 只保存敏感值或受控引用，不定义 Provider 名称、endpoint 或配置 UI。
- API key 不得进入 `toString()`、日志、错误信息、analytics、Room entity 或 Agent tool result。
- 删除／轮换必须是显式操作，并定义“密钥不可用”而非静默返回空字符串。
- `:data:ai-config` 保存业务配置与密钥引用，使用本模块读写真正的 secret。

### 4.6 `:core:network`

负责 transport client、认证传输、request／response DTO、序列化和网络错误的初步分类。

- 不定义 `TemplateRepository`、项目同步策略或 AI 配置业务语义。
- DTO 只在 network/data adapter 内流动；data mapper 转换为稳定资源模型。
- 超时、重试、缓存、离线策略由调用的 data/domain 按业务需求决定；不要在通用 client
  隐式重试会改变数据的请求。
- 认证凭据由 secure-storage／data 配置提供，network 不自行持久化 secret。

## 5. 依赖、DI 与 Agent 工具

### 5.1 允许的依赖

| 来源 | 可依赖 | 禁止依赖 |
| --- | --- | --- |
| `:core:<technology>` | 默认无；有明确技术分层时可依赖更低层 `:core:*` | data、domain、feature、contract、agent、app、shell |
| `:data:<area>` | `:core:*`、经批准的 `:contract:*` | 其他 data、domain、feature、agent runtime、app、shell |
| `:domain:<area>` | `:data:*`、`core:common/model`、必要 contract | feature、app、storage SDK 类型 |
| `:feature:<area>` | domain 与 UI 相关 core | data、技术 core 存储模块、contract、runtime |

默认使用 Gradle `implementation`。只有公开 API 的签名确实包含某个依赖的刻意类型时才使用
`api`；技术存储类型不应成为这种例外。

### 5.2 Hilt

- `:core:database`、`:core:datastore`、`:core:secure-storage` 在出现 Android root provider
  后，将 provider 放在自身模块。
- `:data:<area>` 在自身模块用 `internal` Hilt module 绑定 Repository interface 与默认实现。
- `:app` 只确保带有 binding 的模块进入最终依赖图，不手写 Repository 或业务流程。
- 测试可用 fake Repository／data source；不能要求启动整个 app 才能测试 data mapping。

### 5.3 Agent 工具

工作区、版本、日志等工具实现放在拥有资源的 `:data:<area>`。当出现真实工具时，data 模块为
该资源定义参数精确的 public tool factory／gateway，domain 仅调用实际需要的 factory 并将返回
的通用 `AgentTool` 交给 runtime；runtime 不知道文件、Git 或数据库。不得建立全局 Provider、
scope 或 `@IntoSet` 注册表。

## 6. 下沉与实现的时机

新增代码时遵循以下路径：

1. 先在 `:data:<area>` 完成一个真实资源能力与测试。
2. 若其中出现可复用、无业务语义的操作，再提取最小 adapter 到相应 `:core:<technology>`。
3. 将 data 改为依赖该 adapter，并保留资源 mapping、错误语义和业务边界。
4. 为新增 module dependency 更新模块图反例测试、模块 README 和本文。

示例：实现“创建项目并写入模板”时，先在 `:data:project` 定义资源合同；当需要安全写入
文件时提取 `AtomicFileStore` 到 `:core:filesystem`。项目目录布局、模板展开、项目锁和错误
code 仍留在 workspace data，而不是进入 filesystem。

## 7. 测试与评审清单

| 改动 | 最低测试 |
| --- | --- |
| Repository contract／mapping | data 单元测试：成功、空值、失败、取消、不可变返回值 |
| Room schema／migration | database migration／DAO 集成测试 |
| DataStore serializer／损坏恢复 | datastore 单元或 Android 测试 |
| 文件原子写／路径校验／锁 | filesystem 集成测试，含取消与并发 |
| Git adapter | 临时仓库集成测试，含失败与恢复 |
| Keystore／secret | secure-storage Android 测试；确认日志和错误不会泄露 secret |
| 网络 adapter | mock server 测试：超时、错误 DTO、取消、认证缺失 |
| 新 Gradle 依赖 | `./gradlew verifyModuleGraph` 与一条负向架构测试 |

提交前检查：

- [ ] Repository 公开签名没有泄露 storage／SDK／Android 类型。
- [ ] core 技术模块中没有业务资源名、Repository 或 use case。
- [ ] 单资源一致性位于 data，跨资源流程位于 domain。
- [ ] expected failure、取消、secret 脱敏和恢复语义已有明确 owner。
- [ ] 没有为了“未来可能复用”创建无使用者的公共 abstraction。
- [ ] 已运行 `./gradlew check`。
