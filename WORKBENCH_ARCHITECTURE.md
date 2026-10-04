# 项目工作区 UI 架构

`:feature:chat`、`:feature:preview`、`:feature:build`、`:feature:version` 各自拥有
Route、Scaffold Screen 和 TopAppBar。`:app` 在同一个 Navigation 3 `NavDisplay` 中
分别注册 `Chat(projectId)`、`Preview(projectId)`、`Build(projectId)`、
`Version(projectId)`。从项目列表打开项目时直接进入 Chat，没有额外首页。

Chat 自己显示“页面”菜单，并通过 `onNavigateToPreview`、`onNavigateToBuild`、
`onNavigateToVersion`
回调请求导航。其他三个页面没有页面菜单，返回时回到 Chat。所有页面使用根
`NavDisplay` 的同一套转场和返回栈。

`:feature:workbench` 模块保留给未来真正属于工作区层的 UI，例如不适合归入 Chat
的 WebView；当前不拥有这些页面、不提供页面菜单，也不决定 WebView 状态的保存方式。
四个页面目前只做 UI 占位，未接入 domain 或执行真实任务。

## 模块边界

| 模块 | 当前职责 |
| --- | --- |
| `:app` | 注册四个独立导航目的地，传入稳定项目 ID 和导航回调 |
| `:feature:chat` | Chat Scaffold、草稿 UI 状态和页面菜单 |
| `:feature:preview` | Preview Scaffold、预览区域与控制台占位 |
| `:feature:build` | Build Scaffold 与构建输出占位 |
| `:feature:version` | Version Scaffold 与版本历史占位 |
| `:feature:workbench` | 为后续工作区级 UI 保留模块边界；当前无页面实现 |

Feature 之间没有实现依赖。导航 key 只包含稳定的项目 ID，`:app` 将其以 `ProjectId`
传入每个 Feature Route；Route 用项目 ID 隔离页面状态，后续也可用于获取自身业务数据。
长任务和可恢复状态
后续由 domain/data 管理，不由 Screen 或导航 entry 持有。

## 验证

运行 `verifyModuleGraph`、`verifyArchitectureSources` 和相关 UI 测试。设备上验证
项目列表 → Chat → Preview／Build／Version 的进入、返回和统一 Navigation 3 转场。
占位页面不证明真实业务能力可用。
