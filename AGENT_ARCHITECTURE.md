# Agent 架构与依赖规范

> 本文定义 AVD 中 Agent runtime、业务策略、工具与资源数据的边界。Repository 与存储规则见
> [DATA_AND_STORAGE_ARCHITECTURE.md](DATA_AND_STORAGE_ARCHITECTURE.md)，业务工作流规则见
> [DOMAIN_ARCHITECTURE.md](DOMAIN_ARCHITECTURE.md)。

## 1. 基本原则

Agent runtime 是可替换的技术实现；工具则是具体业务资源的适配。两者只通过
`:contract:agent` 的通用 `AgentTool` 协作，**不创建全局工具发现协议，也不预设所有工具
拥有相同上下文**。

```text
:feature:workbench
        │
        ▼
:domain:agent ──────────────> :contract:agent <──── :agent:runtime-koog
        │                            ▲
        ├──────> :data:project ──────┤
        ├──────> :data:session ──────┤
        └──────> :data:ai-config ────┘
```

- `domain:agent` 可以依赖 `:contract:agent`，因为它调用抽象 `AgentRuntime`；它绝不能
  依赖 Koog runtime 或任何其他 `:agent:*` 实现模块。
- `data:<area>` 可以依赖 `:contract:agent`，只在该资源确实实现 Agent tool 时使用它。
- `agent:runtime-koog` 只依赖 core 与 contract；它不知道 data、domain、项目、会话或权限。

## 2. `:contract:agent`：唯一的 Agent 共享协议

此模块只包含可被 domain、任意 runtime 与任意工具共同理解的 SDK 无关类型：

| 类型 | 含义 |
| --- | --- |
| `AgentRuntime` | `run(AgentRunRequest): Flow<AgentRuntimeEvent>` |
| `AgentRunRequest` | prompt 与本次明确传入的 `List<AgentTool>` |
| `AgentRuntimeEvent` | started、文本增量、工具开始／完成、完成、失败 |
| `AgentTool` | SDK 无关的 definition 与 `execute` 调用 |
| `AgentToolDefinition` | 稳定工具名、模型可见说明、JSON input schema |
| `AgentToolCall` / `AgentToolResult` | JSON 参数和结构化成功／失败结果 |

```kotlin
data class AgentRunRequest(
    val prompt: String,
    val tools: List<AgentTool>,
)
```

Contract 不包含 Android、Hilt、Koog、Repository、`ProjectId`、`SessionId`、`File`、Room
entity、Git object 或 UI 类型。`AgentToolDefinition.name` 与 schema 是模型可见 API；发布后应
稳定、最小且可验证。

## 3. 工具由资源 owner 按需构造

没有 `AgentToolProvider`、`AgentToolScope` 或全局 `AgentToolContext`。不同资源的工具需要的
参数不同，不能为了统一接口把它们压成 `projectId + sessionId` 或不安全的 `Map`：

| 工具 | 建议的资源契约形状 | 原因 |
| --- | --- | --- |
| 工作区读写 | `WorkspaceAgentTools.forProject(projectId)` | 工具必须绑定一个项目根目录 |
| 会话读取 | `SessionAgentTools.forSession(sessionId)` | 不必强加项目参数 |
| 版本恢复 | `VersionAgentTools.forRestore(projectId, confirmation)` | 恢复需要显式业务授权 |
| 通用只读工具 | `DiagnosticsAgentTools.tools()` | 不需要运行时上下文 |

这些只是**在出现真实工具时**由相应 `:data:<area>` 定义的示例。不要预先创建上述所有接口，
也不要把它们并入 Repository；工具有独立的模型可见 schema、权限和测试要求时，才为其建立
资源专属的 public factory／gateway 契约。实现、底层 Repository、filesystem、Git、数据库与
Hilt binding 保持该 data 模块 `internal`。

调用者是 `domain:agent`：它根据本次业务工作流、用户确认、项目锁和状态，只调用需要的
资源专属 factory，并将结果组合成 `List<AgentTool>`。所以 domain 依赖的是它已依赖的 data
资源契约，而不是一个新的 agent 工具模块。

## 4. 一次 Agent turn 的调用链

```text
Composable
  -> ViewModel action
  -> RunAgentTurnUseCase (:domain:agent)
  -> 校验 AI 配置、项目状态、授权与锁
  -> 按需要调用 WorkspaceAgentTools / SessionAgentTools / ...（各自 data 契约）
  -> AgentTurnReceipt（请求已持久化并被 runtime 接受）

AgentRuntimeCoordinator child scope
  -> List<AgentTool>（每个工具已经绑定自己的精确参数）
  -> AgentRuntime.run(prompt + tools) (:contract:agent)
  -> KoogAgentRuntime (:agent:runtime-koog)
  -> 模型与 tool-result rounds
  -> AgentRuntimeEvent
  -> AgentRuntimeSnapshot / future session journal
  -> ViewModel 通过 named observation use case 渲染
```

当前尚无真实 data tool，因此 `RunAgentTurnUseCase` 暂时传递空工具列表；这只是 runtime
骨架，不是默认的工具注册机制。第一个真实工具落地时，应同时加入对应 data gateway、domain
调用与测试。

## 5. `:domain:agent`：Agent 策略与编排 owner

`domain:agent` 调用 `AgentRuntime`，负责：

- 校验项目、会话和 AI 配置是否可用；
- 定义每种资源工具的授权、锁、快照、审计、恢复与取消前置条件；
- 调用实际需要的 data tool factory，组合工具列表；
- 将 `AgentRuntimeEvent` 映射为稳定的 runtime state，并在 session journal contract 迁移后记录持久事件；
- 定义失败、进程中断与恢复的业务语义；
- 持有每个项目的 Agent execution state 与 supervised turn Job，但不拥有 Preview、WebView 或当前
  选中的 Session。

Domain 不实现文件或 Git 操作，不导入 Koog，也不以“所有已注册工具”代替业务选择。

项目级 Agent Job 的父 owner 不是 ViewModel，而是本模块的 Agent runtime coordinator；它监督 turn
event stream，以实现取消、lease 和进程中断恢复。项目关闭时由 `domain:project` 通过窄 port 请求
它停止；具体协议见
[PROJECT_RUNTIME_ARCHITECTURE.md](PROJECT_RUNTIME_ARCHITECTURE.md)。

## 6. `:agent:runtime-koog`：引擎编排 owner

Koog runtime 只接收已选好的通用工具：

- 建立 Koog agent、prompt、model client；
- 将 `AgentTool` 转为 Koog tool，将调用转为 `AgentToolCall`；
- 执行 streaming、tool-result rounds、超时、最大轮次与 runtime 级取消；
- 将 Koog／provider 原始事件和异常转换为安全的 `AgentRuntimeEvent`。

它不知道项目、会话、权限、Repository、filesystem、Git 或 data tool factory。当前
`KoogAgentRuntime` 仍是 started/completed 临时骨架；真实 ReAct graph 在此模块实现。

## 7. data 工具规范

| 工具类别 | owner | 可使用的下层能力 |
| --- | --- | --- |
| 工作区文件读取、列举、搜索、编辑 | `:data:project` | workspace Repository、filesystem |
| 版本快照读取／恢复 | `:data:project` | VersionRepository、git、filesystem |
| Preview 日志 | `:data:project` | PreviewLogRepository |
| 会话读取／恢复 | `:data:session` | SessionRepository、database |

工具与人工操作必须遵循相同的项目锁、原子写、审计、快照和恢复规则；它不是绕过 data/domain
约束的快捷通道。

- schema 最小化；参数在工具内二次校验。
- 只接受 project root 下受控的相对路径；不得接受任意绝对路径或 shell command。
- 输出限定大小、结构化且脱敏；不返回 secret、环境变量、原始异常、SDK object 或大文件内容。
- 预期失败返回 `AgentToolResult.Failure`；`CancellationException` 原样传播。
- 需要多个资源协调的行为，先由 domain 建立授权／状态；data tool 不跨 data 模块自行编排。

## 8. DI 与测试

Hilt 只连接具体依赖，不定义工具协议：

```text
:data:project
  internal implementation -> public WorkspaceAgentTools（出现真实工作区工具后）

:domain:agent
  inject WorkspaceAgentTools / 其他实际需要的 data gateway
  inject AgentRuntime

:agent:runtime-koog
  @Binds KoogAgentRuntime as AgentRuntime
```

不使用全局 `Set<AgentTool>`、`@IntoSet` 或 app 中的空集合声明。它们会隐藏业务选择，并迫使
所有工具接受同一套无关参数。App 仅确保最终 feature、domain、runtime 与有 binding 的 data
模块可达，不手写业务流程。

| 测试对象 | 最低验证 |
| --- | --- |
| `:contract:agent` | fake runtime／tool、schema 与结果合同 |
| `:agent:runtime-koog` | 明确传入 fake `List<AgentTool>` 的 text turn、tool round、失败、取消、上限 |
| `:domain:agent` | fake runtime 与实际使用的 data gateway 下的授权、event mapping、恢复、取消 |
| data tool | 自己的 factory 参数、路径逃逸、secret 脱敏、锁、预期失败 |

新增工具顺序：

1. 确认资源 owner，并在对应 `:data:<area>` 定义／复用资源能力。
2. 由 `domain:agent` 定义授权、锁、快照、审计和恢复前置条件。
3. 仅为该工具在 data 模块定义参数精确的 factory／gateway，并实现返回的 `AgentTool`。
4. 在 domain 调用该 gateway，向 runtime 显式传入结果。
5. 补齐 data、domain、runtime fake／集成测试，并执行架构校验。

提交前检查：

- [ ] generic contract 没有 Project／Session、Android、Repository 或 SDK 类型。
- [ ] runtime 没有 data 或 domain 依赖；data tool 没有 Koog 依赖。
- [ ] 不存在全局 tool provider／scope；工具参数由资源 owner 的专属契约定义。
- [ ] tool 的参数、schema、路径与输出受限，且不泄露 secret。
- [ ] 跨资源策略在 domain，单资源读写在 data。
