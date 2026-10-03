# 项目工作区 UI 架构

> 本文描述工作区的模块边界与迁移顺序。当前 UI 占位阶段已建立
> `:feature:workbench` 容器、`:feature:chat` 与 `:feature:preview` 内嵌 Route，
> 以及 `:feature:versions`、`:feature:build` 项目级 Route。模块图校验器禁止
> Feature 实现之间的依赖，并以反例测试覆盖新增模块。
> 通用 Feature 规则见 [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)，运行资源的
> 生命周期见 [PROJECT_RUNTIME_ARCHITECTURE.md](PROJECT_RUNTIME_ARCHITECTURE.md)。
> 当前已实施[工作区界面占位方案](WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md)：
> AI 与非 AI 功能都只完成 Feature 层占位和导航，不向 domain/data 接线。

## 1. 拆分依据

工作区是多数项目内功能的入口，不等于这些功能的共同实现模块。按独立的用户任务、
状态与业务 owner 拆分；一个 Pane、Composable 或工具栏按钮本身不是建模块的理由。

| 模块 | UI 所有权 | 不拥有 |
| --- | --- | --- |
| `:feature:workbench` | 工作区容器、项目标题、内嵌区域选择、项目内功能入口、显式关闭项目的确认 UI | 聊天记录、版本／构建流程、Agent turn、Preview server、WebView、控制台日志 |
| `:feature:chat` | 当前仅提供 Chat／AI 占位；未来界面范围待 Chat／Session／Agent 方案确定 | 当前阶段不创建会话或 Agent 状态 |
| `:feature:preview` | 当前仅提供 Preview／Console 占位；未来拥有预览控制、WebView bridge 与日志选择 | Preview backend handle、项目文件、日志存储 |
| `:feature:versions` | 版本历史、初始版本提示与记录、对比与恢复确认 | Git 与文件恢复 |
| `:feature:build` | 构建配置、进度与结果展示 | 构建执行和产物存储 |

文件浏览与编辑形成实际用户流程时，再建立 `:feature:files`；其文件读写必须经 domain
use case。控制台先属于 Preview。会话相关 UI 的归属留待 AI 阶段设计。

## 2. 依赖与组合

```text
:app
  ├─> :feature:workbench                // 容器、内容插槽及项目内入口
  ├─> :feature:chat                     // 本阶段只提供占位 Route
  ├─> :feature:preview                  // 本阶段只提供占位 Route
  ├─> :feature:versions                 // 独立的项目版本 destination
  └─> :feature:build                    // 独立的项目构建 destination

// 以上是当前占位阶段的新增依赖；功能接入后才决定各 Feature 的 domain 依赖。
```

`:app` 在 Workbench destination 中调用 `WorkbenchRoute`。容器提供一个项目内容插槽，
用明确的 `WorkbenchSection` 和稳定 UI 参数告诉 `:app`
该显示哪个 Route。容器决定项目标题、区域切换、返回和显式关闭；它不 import
其他 Feature。Chat 与 Preview 在 Workbench 存续期间始终留在组合中，容器只放置当前选中
区域；切换区域不销毁另一界面的 Compose 状态或作用域。一个内容插槽避免每新增项目内功能就给容器增加一个 Compose 参数；
`WorkbenchSection` 只列真实的内嵌区域，不成为动态 Feature 注册表。
当前区域为 Chat／AI 占位和 Preview／Console 占位，默认显示 Chat／AI；正式业务状态与
会话选择在各自方案确定后再接入。

Version 与 Build 当前适合独立的项目级页面：Workbench 通过
`onVersionsRequested(projectId)`／`onBuildRequested(projectId)` 表达导航意图，
`:app` 将 `ProjectId` 入栈并安装对应 Feature Route。返回时回到同一个 Workbench
destination，保留其区域选择。`:app` 的 Navigation 3 Scene 将 Workbench 与当前顶层页面
同时保持组合，只放置顶层页面；打开 Versions、Build、应用设置及设置子页不会卸载
Workbench、Chat 或 Preview。它们的页面、状态、用例调用和业务错误都留在
各自 Feature。将来确需常驻工作区内嵌面板时，再给 `WorkbenchSection` 增加一项，
由 `:app` 映射至该 Feature Route；无需增加 Feature 间依赖或第二套组合协议。

`:app` 只连接 Route、稳定参数和回调，不观察业务 Flow、注入 use case、维护业务
状态，也不实现工作区布局。子功能仅公开组合或导航所需的 Route 和最小输入／回调，
其 Screen、ViewModel、UiState、组件及 bridge 默认 `internal`。

各 Feature 之间均无 Gradle 实现依赖。现有 `verifyModuleGraph` 允许 `:app` 依赖
Feature，并禁止 Feature 依赖另一个 Feature 的实现；新增模块后应保留这条规则，
为 `workbench -> chat`、`workbench -> versions`、`chat -> preview` 等错误方向补
反例测试。无需增加容器特例。
容器当前对 `:domain:agent`、`:domain:preview` 的直接依赖未被页面使用；占位阶段移除。
当前 Workbench 的初始版本提示直接调用 `:domain:version`；替换临时页面时移除该
提示与用例调用，不在 Versions 占位页继续执行。真实版本 UI 留待后续接入。

## 3. 状态与跨功能交互

占位阶段，`:feature:workbench` 只保存 `ProjectId`、当前区域和容器级瞬态 UI
状态。当前不读取项目资料，不观察 Preview 或其他业务状态；标题使用中性文案。

内容插槽只使用 `WorkbenchSection`、稳定 ID、简单 UI 值和语义回调。`:app`
将 Chat／AI、Preview／Console 区域映射到各自的占位 Route，并同时组合两个 Route。
Version／Build 经
`:app` 导航到独立占位页面。Feature 之间不直接取得对方的
ViewModel，也不建立全局 UI 事件总线。

- 切换区域只改变可见界面，Chat 和 Preview 的 Compose 生命周期跟随 Workbench；当前没有 Preview、Agent 或 Build runtime 连接。
- 返回项目列表只改变导航；占位界面不提供“关闭项目运行资源”动作。
- 打开 Version／Build／应用设置只发导航请求；Workbench 及内嵌界面保持组合，返回后继续原区域。

导航参数仅包含稳定 ID；Composable slot 或回调是进程内组合接口，不得序列化为导航参数。
页面旋转与进程恢复时，容器重建区域选择；当前子功能没有待观察的业务状态。
Session 选择归属、Agent 生命周期、聊天草稿交接和日志转聊天的语义仍待 AI 阶段设计；
当前不引入 `SessionId` 或对应的占位业务状态。

## 4. 迁移顺序与验收

1. 将临时 `WorkbenchRoute` 改为可恢复区域选择的纯 UI 容器，移除初始版本弹窗、
   状态和 use case 调用。默认显示 Chat／AI 占位，不读取项目资料。
2. 在 `:feature:chat`、`:feature:preview` 中建立内嵌占位 Route；在已有
   `:feature:versions`、`:feature:build` 中建立项目级占位 Route，由 `:app` 组合。
3. 验证导航、返回、状态恢复、文案及 Feature 间零实现依赖；运行
   `verifyModuleGraph`、`verifyArchitectureSources` 与适用 UI 测试。

真实项目资料、Version、Preview、Build、Chat／Session／Agent 的功能接入均另行设计。
本阶段的交付与验证矩阵见[工作区界面占位方案](WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md)。
