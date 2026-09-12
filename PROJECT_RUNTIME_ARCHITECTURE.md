# Project Runtime 架构规范

> 本文定义 AVD 中项目级临时运行资源的 owner、命令边界、关闭协议和进程死亡恢复边界。
> 它是 [ARCHITECTURE.md](ARCHITECTURE.md) 中生命周期章节的权威细化；数据资源的原子性与
> 锁见 [DATA_AND_STORAGE_ARCHITECTURE.md](DATA_AND_STORAGE_ARCHITECTURE.md)，Agent 工具和引擎
> 边界见 [AGENT_ARCHITECTURE.md](AGENT_ARCHITECTURE.md)，UI 规则见
> [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)。

## 1. 决策与目标

`Project` 是可长期存在的业务实体；`ProjectRuntime` 是某个项目在**当前应用进程**中的临时、
可关闭运行实例。二者不能互相替代。

```text
Project (durable)
  id, name, workspace metadata, snapshots, sessions, recovery records

ProjectRuntime (in memory, one per ProjectId per process)
  child coroutine scope, running turn supervision, preview handle,
  write/Git lease coordination, live status and event fan-out
```

本设计解决以下问题：

- Chat、Preview、Console 的 UI handle 可以自由创建、隐藏、销毁，而不会错误停止项目资源；
- 一个项目的 Agent、文件写入、Git 与 Preview 有明确的并发和关闭次序；
- “关闭项目”是明确的业务动作，不等同于离开 Workbench；
- 进程死亡不依赖任何内存回调，而从持久记录恢复到可解释的状态；
- application scope、Foreground Service、WorkManager 只承载执行，不悄悄取得业务 ownership。

非目标：不建立全局服务定位器，不复制 IntelliJ/Theia 的 DI 或 SPI 框架，不自动将所有长任务
改成 Foreground Service 或 WorkManager，也不把 Chat/Preview/Console 提前拆为 feature 模块。

## 2. 生命周期树与模块归属

### 2.1 Owner 树

```text
Android application process
  └─ applicationCoroutineScope                       [execution host only]
      ├─ AgentRuntimeCoordinator                      [:domain:agent]
      │   └─ AgentExecution(projectId, turnId)         [internal]
      └─ PreviewRuntimeCoordinator                    [:domain:preview]
          └─ PreviewRuntime(projectId)                 [internal]

:feature:workbench
  └─ ViewModels call named use cases and observe their state

:domain:project
  └─ CloseProjectRuntimeUseCase / RecoverProjectUseCase
      -> AgentProjectRuntimeControl + PreviewProjectRuntimeControl
         [:contract:project-runtime]
```

两个 coordinator 可在 Hilt 中按 application lifetime 绑定；这只是创建和注入方式，不是“由 Hilt
scope 决定业务关闭”的含义。各自的 map、runtime instance、Job 和 backend handle 必须为 `internal`，
任何 Feature、data module 或外部调用方都不能取得它们。

### 2.2 为什么按 runtime 分别放在 `:domain:agent` 与 `:domain:preview`

Agent turn 与 Preview server 有不同的启动条件、状态、恢复能力和关闭实现：Agent 管理 turn、工具、
写 lease 与会话 journal；Preview 管理 server handle、endpoint 与 content revision。把它们放入一个
长生命周期 `ProjectRuntime` 会产生没有业务内聚的状态中心；把它们交给 Feature 又会把 navigation
entry 或 ViewModel 销毁变成资源关闭条件。因此它们各自拥有独立 coordinator：

```text
:feature:workbench -> :domain:agent -> :data:project / :data:session / :data:ai-config / :contract:agent
                   -> :domain:preview -> :data:project
                   -> :domain:project -> :contract:project-runtime
```

`:domain:project` 继续负责项目的创建、删除、导入导出和持久工作区恢复；快照工作流属于
`:domain:version`。“关闭项目
runtime”只关闭临时执行资源，不删除或修改 Project 实体。它只在显式关闭/恢复时通过两个固定的、
窄 lifecycle port 编排 Agent 与 Preview，自己不持有二者状态。不要建立全局 participant set、
可插拔 SPI 或泛化 manager。

### 2.3 资源边界

| 关注点 | 真正 owner | Feature 的权限 |
| --- | --- | --- |
| Project 元数据、文件、快照、Preview 日志、资源级锁、Git 适配 | `:data:project` | 无 |
| Session、消息、turn record | `:data:session` | 无 |
| Agent 授权、工具选择、turn state、快照与恢复策略 | `:domain:agent` | 通过 Agent use case 请求 |
| Agent streaming/tool rounds | `:agent:runtime-koog` | 无 |
| Agent execution Job | `:domain:agent` 的 `AgentRuntimeCoordinator` | 观察 Agent state、发送受限命令 |
| Preview 的受控 root、backend handle 与 Preview state | `:domain:preview` 的 `PreviewRuntimeCoordinator` | WebView 仅为 client |
| 项目关闭顺序与跨资源恢复 | `:domain:project` | 请求关闭、观察结果 |
| 抽屉、当前 Pane、WebView、滚动位置、草稿 | `:feature:workbench` | 自己拥有 |

项目 runtime 资源不是 Repository，不能持久化为 `ProjectRuntimeEntity`；也不是 Agent runtime，不能将
ProjectId、SessionId、文件或 Git 注入 `:agent:runtime-koog`。

### 2.4 项目关闭的窄 contract

`:contract:project-runtime` 只为 `:domain:project` 的关闭/恢复编排提供两个固定 port；它不是
participant registry、事件总线或可插拔 SPI，Feature 不能依赖它。

```kotlin
interface AgentProjectRuntimeControl {
    suspend fun hasRunningTurn(projectId: ProjectId): Boolean
    suspend fun requestStop(projectId: ProjectId): OperationResult<Unit>
    suspend fun awaitStopped(projectId: ProjectId, deadline: Duration): OperationResult<Unit>
    suspend fun completeClose(projectId: ProjectId): OperationResult<Unit>
    suspend fun markInterruptedAfterProcessDeath(projectId: ProjectId): OperationResult<Unit>
}

interface PreviewProjectRuntimeControl {
    suspend fun stop(projectId: ProjectId): OperationResult<Unit>
}
```

`:domain:agent` 与 `:domain:preview` 分别实现自己的 port；App composition root 只将这两个实现绑定为
port，不能取得或管理它们的 Job／handle。`:domain:project` 仅调用这两个明确能力，决定顺序、超时和
恢复语义，绝不读取对方的 state 或 handle。

## 3. 公开 API

Feature 不面向 manager instance，也不面向一个可任意扩张的 `dispatch(command)` facade。它只注入
命名的 use case；每个 domain 内部只协调自己拥有的 runtime：

```kotlin
// :domain:preview
class ObservePreviewRuntimeUseCase { operator fun invoke(projectId: ProjectId): Flow<PreviewRuntimeSnapshot> }
class StartPreviewUseCase { suspend operator fun invoke(projectId: ProjectId): OperationResult<PreviewRequestReceipt> }
class StopPreviewUseCase { suspend operator fun invoke(projectId: ProjectId): OperationResult<Unit> }

// :domain:agent
class ObserveAgentRuntimeUseCase { operator fun invoke(projectId: ProjectId): Flow<AgentRuntimeSnapshot> }
class RunAgentTurnUseCase { suspend operator fun invoke(request: RunAgentTurnRequest): OperationResult<AgentTurnReceipt> }
class CancelAgentTurnUseCase { suspend operator fun invoke(projectId: ProjectId, turnId: TurnId): OperationResult<Unit> }

// :domain:project
class RequestProjectRuntimeCloseUseCase { suspend operator fun invoke(projectId: ProjectId): CloseRequestResult }
class ConfirmProjectRuntimeCloseUseCase { suspend operator fun invoke(projectId: ProjectId): OperationResult<Unit> }
class RecoverProjectUseCase { suspend operator fun invoke(projectId: ProjectId): OperationResult<ProjectRecoveryResult> }
```

约束如下：

1. `StartPreviewUseCase` 与 `RunAgentTurnUseCase` 在必要时创建各自的 runtime；任何 observe use case
   都不应因 UI collector 出现而隐式启动 Agent 或 Preview。
2. `RequestProjectRuntimeCloseUseCase` 可以返回 `NeedsConfirmation`，因为运行中的 turn、Git 或写
   lease 需要用户明确确认；用户取消确认即取消关闭请求，不能把“后台继续”伪装成已关闭。
3. `RunAgentTurnUseCase` 只接收稳定 ID、prompt 和幂等 key；它不接收 WebView、File、Uri、SDK object 或
   lambda callback。
4. domain API 不暴露 coordinator、`Job`、`Mutex`、server handle、port、异常对象或 data DTO。
5. 每个 runtime 只串行化自己拥有的状态；同一 turn `idempotencyKey` 重试必须返回原 receipt 或
   已知终态，不能并行启动第二个 turn。

这些 command use case 只负责校验、持久化请求并把工作交给 runtime；receipt 表示请求已被接受或
拒绝，**不是** server 已启动或 Agent 已完成。Feature 通过各自的 observe use case 观察 Preview/turn
的真实后续状态；会话消息和历史则通过独立的 session observation use case 读取。

当前选中的 `SessionId` 是 `feature:workbench` 的 navigation/UI state。切换抽屉选中项、恢复滚动位置
或打开另一个聊天记录不会重启 runtime；只有 `RunAgentTurnRequest.sessionId` 将某次 turn 明确关联到
一个持久 Session。

`ChatViewModel.viewModelScope` 不得 collect 或拥有实际 Agent Job。`AgentRuntimeCoordinator` 的 child
scope 是执行 Agent runtime Flow、记录事件、更新状态和处理取消的唯一 owner。

### 3.1 Feature 调用示例

启动 Preview 是一次短暂的 use case 调用；ViewModel 等待的是“请求已受理”，而不是它所拥有的
server Job：

```kotlin
fun startPreview() = viewModelScope.launch {
    when (val result = startPreviewUseCase(projectId)) {
        is OperationResult.Success -> Unit // 后续 Starting/Running 由 observeRuntime 提供
        is OperationResult.Failure -> showPreviewRequestError(result.error)
    }
}

val preview: StateFlow<PreviewRuntimeSnapshot> =
    observePreviewRuntimeUseCase(projectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), initial)
```

运行 Agent 同样先获得 receipt；实际 streaming、工具调用、写 lease 和取消全部在 Agent coordinator
的 runtime scope 中：

```kotlin
fun runTurn(prompt: String) = viewModelScope.launch {
    runAgentTurnUseCase(
        RunAgentTurnRequest(
            projectId = projectId,
            sessionId = selectedSessionId,
            prompt = prompt,
            idempotencyKey = newOperationId(),
        ),
    )
}
```

ViewModel 另外通过 session observation use case 渲染消息/历史；它无需也不得持有 Agent `Job`。若
ViewModel 在请求返回后被清除，已被 runtime 接受的 preview/turn 仍按自己的关闭和恢复规则继续。

## 4. 状态模型与事件模型

避免一个包含所有组合的巨大 enum，也不建立总的 `ProjectRuntimeSnapshot`。Preview 与 Agent 分别
发布自己的状态；Feature 只为渲染而组合它们，项目关闭则有独立的短暂 operation state。

```kotlin
sealed interface AgentRuntimeSnapshot {
    data object Idle : AgentRuntimeSnapshot
    data class Preparing(val turnId: TurnId) : AgentRuntimeSnapshot
    data class Running(val turnId: TurnId) : AgentRuntimeSnapshot
    data class Cancelling(val turnId: TurnId) : AgentRuntimeSnapshot
    data class Interrupted(val turnId: TurnId?) : AgentRuntimeSnapshot
}

sealed interface PreviewRuntimeSnapshot {
    data object Stopped : PreviewRuntimeSnapshot
    data object Starting : PreviewRuntimeSnapshot
    data class Running(val endpoint: PreviewEndpoint, val contentRevision: Long) : PreviewRuntimeSnapshot
    data object Stopping : PreviewRuntimeSnapshot
    data class Failed(val error: PreviewError) : PreviewRuntimeSnapshot
}
```

`PreviewEndpoint` 是稳定且可显示的 endpoint 表示，不能泄露 Ktor server 或绝对文件路径；
Feature 仅在自身 UI state 中将其映射为 WebView URL。

### 4.1 关键状态转移

```text
Agent:   Idle -- RunAgentTurn --> Preparing -> Running -> Idle
                                 \-> Cancelling -> Idle / Interrupted

Preview: Stopped -- StartPreview --> Starting -> Running(endpoint, contentRevision)
                                      \-> Failed
         Running -- StopPreview --> Stopping -> Stopped

Close:   RequestProjectRuntimeClose --> Closing -> Closed | CloseTimedOut
```

Agent turn 结束后其 execution Job 从 Agent coordinator 移除；Preview stopped 后其 backend handle 从
Preview coordinator 移除。下一次明确 start/run 创建新的资源；绝不复用已经 cancel 或 stop 的对象。

### 4.2 UI handle 与真实 runtime 的关系

```text
Chat Pane destroyed       != CancelTurn
Preview Pane destroyed    != StopPreview
Console hidden            != Stop log collection
Workbench navigation away != RequestProjectRuntimeClose
```

Feature collector 停止只会使其自身停止渲染。runtime 使用 replayable `StateFlow`/repository `Flow`
提供当前快照，而不是用只能被当时 collector 接到的 `SharedFlow` 表达业务事实。

Agent 修改文件后的 Preview 刷新采用状态而非 UI 命令：`:data:project` 在受控写入成功后发布
项目 content revision；`:domain:preview` 观察该资源变化并更新自己的 `contentRevision`。可见 WebView
观察 revision 后 reload；隐藏 Pane 在重新显示时直接加载最新 endpoint/revision。Preview server 不因
每一次文件编辑而重启，`:domain:agent` 也不依赖 `:domain:preview`。

## 5. 并发、lease 与 Agent 工具

### 5.1 两层协调

- `:data:project` 是项目文件写锁和 Git 资源锁的 owner。它保证相对路径校验、原子写、同一资源
  的排他性、Git adapter 访问和资源级错误。
- `:domain:agent` 在 Agent turn 中请求 lease；`:domain:project` 在恢复与显式关闭中编排其释放。
  两者都不自己实现文件锁、Git 或原子写。

所有会改变工作区的 Agent tool 必须使用与人工操作相同的 `WorkspaceWriteLease`。Git 操作使用
同一项目的排他协调域，不能与文件写入并发。只读文件操作可并发，但关闭开始后不得开启新的
写/Git 操作。

### 5.2 Agent turn 协议

```text
RunAgentTurn
  -> validate project/session/config and idempotency key
  -> persist TurnRecord(PREPARING)
  -> acquire required write intent / authorize tools
  -> AgentRuntimeCoordinator launches supervised collector
  -> runtime event -> session event/journal + live log + snapshot
  -> tool write -> data-level atomic mutation + mutation journal
  -> terminal event -> persist COMPLETED / FAILED / CANCELLED
  -> release turn-held lease, agent becomes Idle
```

`CancellationException` 必须向上传播：不能映射为失败 Snackbar 或普通 Agent error。用户请求取消时，
先持久化 `CANCEL_REQUESTED`，再 cooperative cancel Job；终态由 collector 在资源清理后写入。

`reload_preview` 不应成为一个 UI `SharedFlow`。若它只是“写后显示最新文件”，应删除该独立工具，
由 workspace content revision 驱动 Preview reload；若将来需要真正重启 Preview，必须由用户显式调用
`StartPreviewUseCase`/`StopPreviewUseCase`，不能让 Agent 直接控制 Preview domain。

## 6. 显式关闭协议

“关闭项目”是一个业务工作流，必须幂等、可观察、可等待。它不是 `ViewModel.onCleared()`，也不
是导航 back stack 的副作用。

```text
1. Admission barrier
   CloseProjectRuntimeUseCase 持久化 close/cancel intent；通过 Agent port 拒绝新的 Agent turn。

2. Stop writers first
   调用 AgentProjectRuntimeControl.requestStop(projectId)，请求正在运行的 turn cooperative cancellation。

3. Await / reconcile work
   等待 turn、原子写、Git 操作达到 terminal state；未完成操作写入 recovery-needed journal。

4. Stop preview
   调用 PreviewProjectRuntimeControl.stop(projectId)，停止 local preview backend（其 own grace period +
   timeout）；不再接受 client。

5. Finalize durable state
   写入 terminal runtime/turn event，flush 必要 journal，关闭 live log fan-out。

6. Release resources
   释放 workspace/Git lease；Agent 与 Preview 各自移除已结束的 execution/backend handle。
```

顺序的关键是**先停止所有可能写入项目的工作，再释放写 lease**。超时不能被解释为“强制释放
一切”：若一个不可中断的 writer 或 Git operation 仍可能碰触工作区，workspace close operation 必须
保留 `CLOSING` 和 lease，并记录 `RuntimeIssue.CloseTimedOut`。可对 Preview 使用其技术实现提供的
forced stop；不能安全地强杀普通 Kotlin thread。

关闭过程中状态仍可被 UI 观察，Feature 应显示“正在关闭/需要恢复”而非立即把项目当作已关闭。

## 7. 持久化与恢复

### 7.1 必须持久化

| 记录 | owner | 用途 |
| --- | --- | --- |
| Project metadata、workspace layout、snapshot metadata | `:data:project` | 长期项目真相 |
| Session、messages、turn ID、idempotency key、phase、cancel intent、terminal result | `:data:session` | 重放聊天历史及识别中断 turn |
| 文件/Git mutation journal、最后安全 checkpoint、recovery-needed 标记 | `:data:project` | 进程死亡后校验、回滚或引导恢复 |
| 有界、脱敏的 operation summary（若产品承诺诊断） | 对应 data resource | 恢复诊断与审计 |

### 7.2 绝不持久化

`CoroutineScope`、`Job`、`Mutex`、OS file lock、Ktor handle、端口、WebView、Flow/SharedFlow
buffer、SDK/client object、原始异常、secret、未经截断的 provider/console output 都只存在内存。

`PreviewLogRepository` 第一版可保持每项目有界内存。进程死亡恢复依赖 session/mutation journal，
而不是依赖完整 console replay；若未来产品承诺跨重启日志，再为脱敏且有大小上限的记录增加
持久实现，不能直接存原始输出。

### 7.3 恢复流程

```text
Application start or explicit project open
  -> :domain:project runs RecoverProjectUseCase
  -> load unfinished turn and mutation journals
  -> validate/reconcile atomic file and Git operations
  -> mark an uncompleted provider stream as INTERRUPTED (not FAILED or COMPLETED)
  -> :domain:agent publishes Interrupted/Idle as appropriate; Preview remains Stopped
  -> publish RecoveryResult or a stable recovery action
```

不能自动“恢复”已经断开的模型流或旧 Preview port。只有 provider 协议和持久 checkpoint 明确支持
continuation 时，才在用户确认后以新的、带幂等 key 的 operation 继续；默认操作是展示“重试”或
“从最后安全 checkpoint 继续”。

## 8. Android 生命周期语义

| 事件 | runtime 语义 | 不应做的事 |
| --- | --- | --- |
| Chat/Preview/Console 被销毁或隐藏 | detach UI observer；runtime 不变 | 取消 Agent、停 server、清日志 |
| 离开 Workbench | 不隐式 close | 将导航当作项目关闭 |
| Activity stop / app 进后台 | 默认不改变业务状态 | 依赖 `onCleared()` 做资源释放 |
| 用户选择关闭项目 | 执行第 6 节 close protocol | 仅 pop navigation |
| 进程被杀 | 下次按 journal recovery | 假设 finally/onTerminate 一定运行 |

执行载体的职责严格限定如下：

- **application coroutine scope**：仅让 Agent execution scope 与 Preview backend scope 在进程仍存活时
  跨页面运行；不是业务真相，也不能恢复进程死亡。
- **Foreground Service**：只有用户明确需要并且 Android 平台许可后台持续 Agent 工作时才启用。
  它承载一个已有 operation，不拥有关闭语义；service 被杀同样走 recovery。
- **WorkManager**：只运行可延迟、幂等、可重试的清理或恢复工作。不得把交互式 streaming turn
  或 local preview server 塞进 WorkManager。

## 9. 对现有实现的迁移约束

以下旧行为在新工程中禁止复制：

- `ProjectWorkspaceViewModel` 创建/停止 server，并在 `onCleared()` 停止它；
- `ChatViewModel` 在 `viewModelScope` 启动并拥有 Agent Job；
- Agent 通过 UI `SharedFlow` 请求 Preview reload；
- UI Pane 直接持有项目文件、Git、Agent runtime 或 runtime log store。

迁移顺序应是：

1. 在 data/session 与 data/workspace 建立最小 turn/mutation recovery record 和 lease contract；
2. 在 domain/agent 完成可取消、可持久化事件的 Agent runtime coordinator；
3. 创建 domain/preview，完成独立 Preview runtime coordinator 与 `Start/Stop/Observe` use case；
4. 新建 `:contract:project-runtime` 的两个固定 lifecycle port，并由 domain/agent 与 domain/preview 实现；
5. 在 domain/workspace 实现无状态的 close/recovery orchestrator；
6. 让 `feature:workbench` 仅调用命名 use case、分别映射 Agent 与 Preview state；
7. 最后删除 ViewModel/server/SharedFlow ownership，并为进程中断和关闭顺序补测试。

当前框架已完成第 2–5 步中的 runtime owner、port、关闭顺序和 unit test。受控 workspace root、真实
Preview backend、write/Git lease 与 durable turn/mutation journal 尚未在新工程 data 层实现；在它们迁移前，
`UnavailablePreviewBackend` 会返回稳定的 `preview_backend_not_configured`，而不会退回由 Feature 持有 server。

## 10. 最低验证矩阵

| 场景 | 应验证的结果 |
| --- | --- |
| Chat ViewModel 重建 | 正在运行的 turn 未取消，新 UI 可观察同一 turn |
| Preview Pane 隐藏后重开 | server 未被重启；新 WebView 读取当前 endpoint/revision |
| Console 隐藏 | live log pipeline 仍接收数据，重新显示可读当前有界日志 |
| Close with active Agent | 新命令被拒绝，cancel intent 先持久化，写 lease 最后释放 |
| Agent 取消时发生文件写 | data 原子性不被破坏；journal 可恢复 |
| Close timeout | workspace close operation 留在 CLOSING，writer lease 不提前释放，出现稳定错误码 |
| Process death during turn | 下次标记 Interrupted，执行 journal reconcile，不伪造 Completed |
| 同一 RunAgentTurn 重试 | 相同 idempotency key 不启动第二个 turn |
| Agent 写后 Preview | contentRevision 更新；无 UI SharedFlow 依赖 |

测试以 fake `AgentRuntime`、fake preview gateway、fake clock、可控 lease 和临时 workspace 为主。测试
不得要求启动真实 WebView、Koog provider 或完整 Android app；文件/Git 原子性在 data/core 集成测试中
验证，domain/runtime 重点验证顺序、取消、状态和恢复语义。

## 11. 参考实现来源

- IntelliJ 的 Project owner、scope cancellation 与 dispose 顺序：
  [ProjectManagerImpl.kt](../reference-projects/intellij-community/platform/platform-impl/src/com/intellij/openapi/project/impl/ProjectManagerImpl.kt)、
  [ComponentManagerImpl.kt](../reference-projects/intellij-community/platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt)。
- VS Code 的可等待、有序 shutdown joiner 与 storage scope：
  [lifecycleService.ts](../reference-projects/vscode/src/vs/workbench/services/lifecycle/electron-browser/lifecycleService.ts)、
  [storage.ts](../reference-projects/vscode/src/vs/platform/storage/common/storage.ts)。
- JupyterLab 对 context dispose 与 explicit session shutdown 的分离：
  [sessioncontext.tsx](../reference-projects/jupyterlab/packages/apputils/src/sessioncontext.tsx)。
- Coder 对持久 workspace transition、agent lifecycle 和日志的建模：
  [000003_workspaces.up.sql](../reference-projects/coder/coderd/database/migrations/000003_workspaces.up.sql)、
  [models.go](../reference-projects/coder/coderd/database/models.go)。
- OpenHands 前端将 websocket/UI state 与远端 conversation 控制命令分离：
  [conversation-websocket-context.tsx](../reference-projects/openhands/src/contexts/conversation-websocket-context.tsx)、
  [conversation-mutation-utils.ts](../reference-projects/openhands/src/hooks/mutation/conversation-mutation-utils.ts)。
