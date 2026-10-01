# 项目工作区 UI 架构

> 本文描述工作区的目标模块边界与迁移顺序。当前工程只有占位的
> `:feature:workbench`；下述 `:feature:chat` 和 `:feature:preview` 尚未创建。
> 创建它们之前，必须在同一变更中更新 `settings.gradle.kts`、模块图校验器及测试。
> 通用 Feature 规则见 [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)，运行资源的
> 生命周期见 [PROJECT_RUNTIME_ARCHITECTURE.md](PROJECT_RUNTIME_ARCHITECTURE.md)。

## 1. 拆分依据

工作区是多数项目内功能的入口，不等于这些功能的共同实现模块。按独立的用户任务、
状态与业务 owner 拆分；一个 Pane、Composable 或工具栏按钮本身不是建模块的理由。

| 模块 | UI 所有权 | 不拥有 |
| --- | --- | --- |
| `:feature:workbench` | 工作区容器、项目标题、Pane 选择、当前会话 ID、跨功能入口、显式关闭项目的确认 UI | 聊天记录、Agent turn、Preview server、WebView、控制台日志 |
| `:feature:chat`（计划） | 聊天时间线、输入与草稿、Agent 状态展示、会话抽屉及会话操作 | Agent Job、会话持久化、项目文件 |
| `:feature:preview`（计划） | 预览控制、WebView bridge、预览状态、预览控制台与日志选择 | Preview backend handle、项目文件、日志存储 |
| `:feature:versions` | 版本历史、对比与恢复确认 | Git 与文件恢复 |
| `:feature:build` | 构建入口、进度与结果展示 | 构建执行和产物存储 |

文件浏览与编辑形成实际用户流程时，再建立 `:feature:files`；其文件读写必须经 domain
use case。会话抽屉先属于 Chat，控制台先属于 Preview。只有出现不同的 owner、导航生命周期
或独立业务流程时，再评估是否拆出会话或日志 Feature。

## 2. 依赖与组合

```text
:app
  -> :feature:workbench                 // 安装项目内入口
       -> :feature:chat                 // 仅调用公开 ChatRoute
       -> :feature:preview              // 仅调用公开 PreviewRoute
       -> :domain:project               // 项目标题与显式关闭

:feature:chat    -> :domain:agent / :domain:project
:feature:preview -> :domain:preview
```

`workbench -> chat/preview` 是**唯一计划中的 Feature 实现依赖例外**。方向固定为容器到
子功能；子功能不得依赖容器或彼此，也不得通过 `api` 重新导出另一个 Feature。子功能只公开
供容器组合的 Route 与最小输入/回调，其 Screen、ViewModel、UiState、组件及 bridge 默认
`internal`。跨 Feature 的顶层导航仍由 `:app` 负责，不能把 AppRoot 的导航图复制进容器。

当前 `verifyModuleGraph` 只允许 Feature 依赖 core、domain 或 `:feature:*:api`。
因此这张目标依赖图**尚不能通过当前校验**。建立第一个子功能模块时，须将例外精确限制为
`:feature:workbench -> :feature:chat/:feature:preview`，并为允许的方向、反向依赖和同级
依赖添加校验测试；在校验器更新前不得用绕过检查或把逻辑塞回容器的方式迁移。
容器当前对 `:domain:agent`、`:domain:preview` 的直接依赖只是占位阶段的配置；
拆分后若无容器级用例，应移除这些依赖。

## 3. 状态与跨功能交互

`:feature:workbench` 只保存 `ProjectId`、选中的 `SessionId?`、当前 Pane 及容器级瞬态
UI 状态。Chat 和 Preview 各自从 domain use case 观察自己的业务状态；不建立汇总所有
消息、日志、预览和运行状态的 `WorkbenchViewModel`，也不在多个 ViewModel 中复制同一
业务真相。

容器组合接口只暴露稳定输入与语义回调。例如 Chat Route 接收 `projectId`、
`selectedSessionId`，通过 `onSessionSelected(SessionId?)` 把抽屉选择交还容器；Preview
Route 接收 `projectId`，通过 `onAddLogExcerptToChat` 提交有长度上限的日志摘录。
容器切到 Chat Pane 并保存待交付的草稿请求，直到 Chat 确认接收。不能依赖隐藏 Pane
仍在组合树中，也不能直接调用其 ViewModel。日志原件和运行中状态仍由各自的资源 owner
保存；这份待交付草稿只是 UI 输入。

- 选中会话或 Pane 只改变 UI 选择，不取消 Agent turn，不停止 Preview。
- 新会话可以由 `selectedSessionId == null` 表示；发送消息时由 domain 保证创建会话、
  持久化消息与发起 turn 的顺序。不能只在 UI 生成一个尚未持久化的 ID。
- Chat 通过 `:domain:agent` 发起／取消 turn，通过经 domain 暴露的会话观察用例读取
  时间线；ViewModel 不能拥有运行中的 Agent Job。
- Preview 通过 `:domain:preview` 观察与控制 runtime。WebView 仅是 UI client；
  可见时根据 endpoint 和 content revision 加载或刷新。
- “将日志加入聊天草稿”等跨功能操作由容器接收子功能的类型化回调，再传给目标子功能。
  不直接取得另一个 Feature 的 ViewModel，也不建立全局 UI 事件总线。若请求必须跨进程
  恢复，应将其持久化在对应业务 owner，而不是只保存在回调中。
- 返回项目列表只改变导航。明确的“关闭项目运行资源”才调用
  `RequestProjectRuntimeCloseUseCase` / `ConfirmProjectRuntimeCloseUseCase`。

导航参数仅包含稳定 ID；Composable slot 或回调是进程内组合接口，不得序列化为导航参数。
页面旋转与进程恢复时，容器重建 UI 选择，子功能通过 ID 重新观察 domain/data 状态。

## 4. 迁移顺序与验收

1. 修订模块图校验规则并建立两个子功能模块的空 Route。验证正向依赖通过、反向和同级
   Feature 依赖失败。
2. 将占位 `WorkbenchRoute` 改为真实容器；项目详情须经 `:domain:project` 读取，
   并处理项目不存在或不是 `READY` 的状态。验证返回列表不关闭 runtime。
3. 先完成会话及消息的 data/domain 契约，再接入 Chat 的列表、空态、发送、失败、重试和
   取消。真实 Agent runtime 尚未迁入时，不把占位运行结果显示为成功回复。
4. 为 Preview 接入受控工作区后端、内容修订号与日志契约，再迁移 WebView／Console UI。
   后端未配置时显示明确不可用状态，不由 ViewModel 启动旧 server。
5. 接入显式关闭、跨功能草稿和版本／构建入口。每一阶段运行
   `verifyModuleGraph`、`verifyArchitectureSources` 与对应模块测试。

最低 UI 验证包括：空白与不存在的项目、会话切换时运行中的 turn、Chat ViewModel 重建、
Preview 隐藏后重开、日志转草稿、返回与显式关闭的不同效果，以及窄屏和大字体下的
Pane 切换。运行时与持久恢复的验证矩阵见 Project Runtime 文档。
