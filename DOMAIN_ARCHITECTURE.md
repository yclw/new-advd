# Domain 层架构规范

> 本文定义 `:domain:*` 的职责、公开 API、依赖关系与编码规范。Repository 与技术存储的细则
> 见 [DATA_AND_STORAGE_ARCHITECTURE.md](DATA_AND_STORAGE_ARCHITECTURE.md)，页面边界见
> [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)。

## 1. Domain 的位置

```text
:feature:<area>
       │ 页面 action、导航参数
       ▼
:domain:<area>
       │ 有名称的应用操作、业务状态、业务事件
       ▼
:data:<resource>
       │ Repository 合同、单资源读写
       ▼
:core:<technology>
```

Domain 是 feature 使用业务能力的唯一入口。它不显示 UI，不持久化数据，也不直接处理
Room、DataStore、文件、Git、网络、Keystore 或 Koog SDK；它定义“应用要完成什么操作、
按什么业务顺序完成、失败后对调用者意味着什么”。

当前业务 owner：

| 模块 | 负责的业务能力 |
| --- | --- |
| `:domain:project` | 项目创建、重命名、删除、导入导出、模板应用、项目生命周期与恢复 |
| `:domain:template` | 模板目录浏览与模板内容读取 |
| `:domain:version` | 项目版本快照浏览、创建与恢复 |
| `:domain:agent` | Agent turn 的授权、运行、取消、恢复、快照前置条件和事件映射 |
| `:domain:preview` | Preview server 的启动、停止、项目级 Preview state 与恢复后的 stopped 语义 |
| `:domain:settings` | 主题、语言、Provider 配置等应用级设置操作 |

不要为每个 feature 或每个 Repository 自动创建 domain 模块。只有一个业务区域拥有稳定的
应用操作、工作流或独立发布边界时才拆分。

## 2. Domain 应做什么、不应做什么

### 2.1 应做的事

- 定义 feature 可调用的、有名称的 application use case。
- 编排多个 Repository：顺序、前置校验、授权、幂等、事务边界、重试和恢复。
- 将 data 的资源模型／结果转换为 feature 可消费的 domain 请求、结果和事件。
- 定义跨资源不变量。例如初始化项目必须先建立工作区、再写入模板内容、再建立初始快照，
  最后才发布为 `READY`。
- 决定什么是用户可处理的错误，向 feature 返回稳定 `AppError`／`ErrorCode`，而非技术异常。
- 为 Agent 场景决定 scope、权限、快照和恢复政策；runtime 只负责模型及工具调度。

### 2.2 不应做的事

- 不直接使用 DAO、Entity、DataStore、`File`、`Uri`、Git client、HTTP client、Keystore、
  Room、WorkManager、WebView 或 provider SDK。
- 不保存 UI state、不使用 Compose、资源 ID 或本地化文本。
- 不把一个 Repository 的每个方法机械地包成同名 use case；没有新增业务名称或边界的包装
  只会增加维护成本。
- 不把单资源的 SQL 原子性、文件原子写、DataStore 序列化、网络 DTO mapping 下放／复制到
  domain；这些由 data 或技术 core 负责。
- 不因方便而让 feature 绕过 domain 注入 Repository。

## 3. Use case 与公开 API

### 3.1 最小默认形态

一个简单业务区不需要目录层次，只需一个明确的 use case：

```text
:domain:project/src/main/kotlin/.../domain/project/
  CreateProjectUseCase.kt
```

```kotlin
class CreateProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository
) {
    suspend operator fun invoke(request: CreateProjectRequest): OperationResult<ProjectSummary>
}
```

当同一业务区已有多个清晰子领域时，才按能力分包，例如 `project/`、`importexport/`、
`version/`；不要强制建立 `usecase/`、`model/`、`mapper/`、`di/` 等目录。

### 3.2 命名与粒度

公开 use case 名称使用动词或观察语义，表达应用行为，而非技术细节：

| 推荐 | 不推荐 | 原因 |
| --- | --- | --- |
| `CreateProjectUseCase` | `InsertProjectUseCase` | 对调用者表达业务结果，而不是存储动作 |
| `InitializeProjectFromTemplateUseCase` | `CopyTemplateFilesUseCase` | 明确完整工作流与前置条件 |
| `ObserveProjectSummariesUseCase` | `GetProjectsUseCase` | 明确长期观察而非一次性读取 |
| `RestoreSnapshotUseCase` | `GitCheckoutUseCase` | 隐藏 Git 技术实现，表达恢复语义 |
| `RunAgentTurnUseCase` | `KoogAgentRunnerUseCase` | runtime 可替换，业务语义稳定 |

一个 use case 可以只读取一个 Repository，只要它代表明确的 feature 操作或为 feature 提供
稳定的、隔离 data 的观察模型。不要为了“必须编排多个 Repository”而把简单读取暴露给
feature 直连 data。

### 3.3 输入与输出

Domain public API 只使用以下类型：

- `:core:common` 的稳定 ID、`OperationResult`、`AppError` 与错误 code；
- `:core:model` 中已被多个业务区共享且稳定的模型；
- domain 自己定义的 request、result、summary、event；
- Kotlin／coroutines 基础类型，例如 `suspend`、`Flow`、不可变 `List`。

允许的形态：

```kotlin
suspend operator fun invoke(request: RenameSnapshotRequest): OperationResult<SnapshotSummary>
operator fun invoke(projectId: ProjectId): Flow<List<ProjectSummary>>
suspend operator fun invoke(request: RunAgentTurnRequest): OperationResult<AgentTurnReceipt>
```

禁止在 public API 出现：

- `data.*` 的 Repository DTO、entity、DAO、storage request/result；
- Android `Context`、资源 ID、`Uri`、`File`、`Parcelable`、Activity Result 类型；
- Room、DataStore、Git、网络 response、Keystore、Koog 或任意 provider SDK 类型；
- `Throwable`、本地化文本、Compose state 或 UI effect。

若 data 的模型已足够稳定且属于 `:core:model`，domain 可以直接返回它；否则必须在 domain
映射为 feature 所需的 summary／result。不要仅为避免 mapper 而把 data DTO 泄露到 feature。

## 4. 工作流、状态与一致性

### 4.1 责任边界

| 问题 | Owner |
| --- | --- |
| 单次 SQL transaction、entity migration | `:core:database`／对应 data adapter |
| 原子文件写、相对路径校验、文件锁 | `:core:filesystem`／`:data:project` |
| 单个 Repository 的缓存、一致性、资源级错误映射 | `:data:<area>` |
| 多 Repository 的顺序、业务校验、幂等、恢复、发布状态 | `:domain:<area>` |
| 页面状态、权限弹窗、文件选择、Snackbar、导航 | `:feature:<area>` |

例如恢复版本快照：domain 检查项目状态、权限与运行中 Agent；data/workspace 执行受控的
文件／Git 恢复；domain 再发出稳定结果或恢复事件。页面只渲染状态和请求用户确认。

### 4.2 长事务与恢复

项目初始化、导入导出、版本恢复和 Agent 文件编辑不能被视为一次普通 suspend 调用。Domain
use case 必须定义：

1. 前置条件、锁 owner 和幂等 key；
2. 可持久化的阶段与何时发布成功；
3. 可取消点，以及取消后对用户可见的状态；
4. 进程死亡或中断后的恢复入口；
5. 哪一层保留 journal／日志，哪一层把它转换为业务事件。

Data 保证资源层面的原子性与 journal 写入；domain 定义这些资源如何组成一个可恢复的
产品操作。

## 5. 错误、取消与事件

- 正常结果返回模型、`Flow` 或 `OperationResult.Success`。
- 可预期、用户可处理的失败返回 `OperationResult.Failure(AppError)`；错误 code 必须稳定，
  feature 再映射为本地化文案。
- data 的技术异常由 data 先脱敏、分类；domain 可进一步把资源级失败合并为应用操作的
  错误，但不能重新泄露技术细节。
- `CancellationException` 永远重新抛出；它不是失败、Toast 或 error log。
- 长运行操作使用稳定 event／状态模型，例如 `Preparing`、`Running`、`RecoverableFailure`、
  `Completed`，不把日志文本或 SDK streaming object 传给 feature。
- 同一异常只在最靠近根因的层记录一次；domain 仅记录额外的业务上下文，且不能包含密钥、
  prompt 内容或绝对文件路径。

## 6. 依赖、DI 与 Agent

### 6.1 Gradle 依赖

```text
:domain:<area>
  -> :data:<owned-resource>
  -> :core:common / :core:model（及真正通用的 core）
  -> :contract:<port>（仅真实反转端口）
```

Domain 不依赖 feature、app、shell、`agent:runtime-*` 或技术 core 的具体存储模块。它通过
Repository 和 contract 取得能力。某个 domain 模块可以使用 Android library 以消费 Android-
backed data，但这不放宽源码边界：domain 源码及 public API 仍不得 import Android／SDK 类型。

### 6.2 DI

- 默认 use case 是公开 concrete class，并以 `@Inject constructor` 接收 Repository／port。
- 只有确实需要替换 use case 实现时才定义 use case interface 并建立 Hilt binding。
- `@Inject`、`javax.inject` 等不携带 Android 类型的注解可使用；禁止 domain import
  `dagger.hilt.android.*`。
- Repository binding 位于 data；runtime binding 位于 `:agent:runtime-koog`；domain 不承担
  存储或 runtime 的 composition root 职责。

### 6.3 Agent 与 Preview runtime 特例

`domain:agent` 调用通用 `AgentRuntime` contract，而不调用 Koog。需要工具时，它只调用本次
工作流实际需要的 data resource gateway；每个 gateway 自己定义创建工具所需的项目、会话、授权
或无参数输入，再返回通用 `AgentTool`。它负责 event 转换、工具授权、项目锁、快照、会话持久化、
取消和恢复；domain 不实现具体文件或 Git 工具。

`domain:preview` 独立拥有每个项目的 Preview backend handle 与 Preview state；它不依赖 Agent，
也不拥有 WebView。`domain:project` 通过窄 lifecycle port 编排显式关闭和恢复，但不持有 Agent
Job 或 Preview handle。详情见 [PROJECT_RUNTIME_ARCHITECTURE.md](PROJECT_RUNTIME_ARCHITECTURE.md)。

## 7. 测试与代码评审

Domain 测试以 fake Repository 与 fake `AgentRuntime` 为主，不应启动 Room、DataStore、文件系统、
Git、Keystore、网络或 Android UI。

| 改动 | 最低验证 |
| --- | --- |
| 单个 use case | 输入校验、成功、每类预期失败、取消、输出 mapping 的 unit test |
| 多资源工作流 | 调用顺序、前置条件、幂等、部分失败、恢复路径测试 |
| 事件流 | 各阶段 event、完成与可恢复失败、取消后无错误事件 |
| Agent turn | fake runtime/tool provider 的 scope、权限、快照与事件映射测试 |
| 模块边界 | `./gradlew verifyArchitectureSources`、`./gradlew verifyModuleGraph` |

提交前检查：

- [ ] use case 表达应用行为，而不是 DAO／文件／Git／SDK 操作。
- [ ] Feature 只能见到 domain API，data DTO 没有越过 use case 边界。
- [ ] 单资源一致性留在 data；多资源流程、授权、恢复和幂等在 domain。
- [ ] 公开签名没有 Android、存储、SDK、`Throwable` 或本地化文本。
- [ ] 取消被重新抛出，预期失败有稳定错误语义。
- [ ] 新模块或依赖变更已更新 README、架构文档与反例测试。
