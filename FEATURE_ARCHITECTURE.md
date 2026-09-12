# Feature 层架构规范

> 本文定义 `:feature:<area>` 的职责、公开边界、UI 状态模型与贡献规范。业务用例规则见
> [DOMAIN_ARCHITECTURE.md](DOMAIN_ARCHITECTURE.md)，数据边界见
> [DATA_AND_STORAGE_ARCHITECTURE.md](DATA_AND_STORAGE_ARCHITECTURE.md)。

## 1. Feature 的位置与目标

Feature 是一个用户可见业务区域的 Android UI 边界，而不是 data 的薄包装，也不是按每个
Composable 随意拆出的模块。它接收用户意图与导航参数，调用 domain use case，并把业务输出
转换为稳定、可渲染、可测试的 UI 状态。

```text
用户输入／导航参数
        ↓
:feature:<area>
  Composable -> Action -> ViewModel -> domain use case
        ↑                      ↓
   UiState / UiEffect      domain model / event / OperationResult
```

Feature 的产物是 UI，不是业务能力：

- 输入：页面 action、稳定 route 参数、Activity Result 等平台输入；
- 输出：`UiState`、一次性 `UiEffect`、导航请求和对 domain 的请求；
- 不拥有：Repository、数据库、文件、Git、网络、模型 SDK、Agent runtime、跨资源事务。

## 2. 模块划分与所有权

按用户任务、共享上下文与发布节奏划分 Feature；不要仅因一个页面或一个 tab 就创建模块。

| 模块 | UI owner | 不属于它的内容 |
| --- | --- | --- |
| `:feature:projects` | 项目列表、创建、编辑、初始化入口 | 工作区写入、模板展开、初始快照 |
| `:feature:workbench` | 项目内聊天、会话抽屉、预览、控制台、局部控制；观察项目 runtime 快照并发送受限命令 | Agent runtime、文件／Git 操作、会话持久化、项目 runtime Job／server／lease |
| `:feature:templates` | 模板浏览、筛选与选择 | 模板下载、缓存与内容读取 |
| `:feature:versions` | 快照历史、对比入口、恢复确认 UI | 快照恢复与文件替换 |
| `:feature:settings` | 主题、语言、AI 配置页面 | DataStore、Keystore、AppCompat locale 调用 |
| `:feature:build` | 构建相关页面与进度展示 | 构建执行与产物持久化 |

以下情况才考虑新建 Feature：它有独立 owner、独立导航入口或发布节奏，且不会共享同一个
页面级状态 holder。否则优先在现有 Feature 内以 Screen／Composable 拆分。

## 3. 允许依赖与禁止依赖

```text
:feature:<area>
  -> :domain:<area>                 // use case、domain event、业务 request/result
  -> :core:ui / :core:designsystem  // 通用 UI 与设计 token
  -> :core:navigation               // 稳定 route 与仅含 ID 的参数
  -> :feature:<name>:api            // 仅在确有跨 feature 导航时
```

对工作台而言，Feature 可以调用多个边界清晰的 domain：`:domain:agent` 处理 Agent turn，
`:domain:preview` 处理 Preview，`:domain:project` 提供项目/Session 观察并编排显式关闭。Feature
只观察这些 use case 的稳定状态、发送用户命令并保存 Pane/Session 的 UI 选择；它不持有任何 runtime。

Feature 不能依赖：

- `:data:*`、Repository、data DTO、Room entity、DAO、DataStore；
- `:agent:runtime-koog`、Koog、模型／provider SDK、Agent tool；
- `File`、文件路径、Git 类型、网络 client、`Context` 持久化访问；
- 其他 Feature 的 Screen、ViewModel、内部 Compose component 或 implementation；
- `:app`、`:shell`。

Gradle 一律先使用 `implementation`。Feature 的 public API 不应泄露 domain 内部模型；只有
跨 Feature 导航这一真实需求，才新增极小的 `:feature:<name>:api`，其中只放 route 与稳定 ID。

## 4. 推荐目录与可见性

一个有状态页面的推荐结构如下。按页面组织，避免以 `util`、`common`、`manager` 混放无归属代码。

```text
:feature:projects/src/main/
  java/.../feature/projects/
    ProjectsRoute.kt                 // route 参数、NavHost 连接
    ProjectsScreen.kt                // 纯渲染入口
    ProjectsViewModel.kt             // 页面状态 holder
    ProjectsUiState.kt               // internal 或 public（仅测试需要时）
    ProjectsAction.kt                // internal sealed interface
    ProjectsEffect.kt                // internal sealed interface
    component/
      ProjectList.kt                 // 无业务访问的可复用页面组件
      CreateProjectDialog.kt
    mapper/
      ProjectUiMapper.kt             // domain model -> feature-local UI model
    bridge/
      CreateProjectResultBridge.kt   // Activity Result／权限等平台桥接（按需）
  res/
    values/strings.xml
    values-zh-rCN/strings.xml
```

- 默认使用 `internal`：页面组件、UI model、mapper、action、effect、bridge 与 Hilt module 都不应
  成为跨模块 API。
- `Screen` 也不应被其他 Feature 直接调用；跨 Feature 由 app 根导航和 route 协调。
- 只有明确复用、没有业务归属的 UI 控件才可迁至 `:core:ui` 或 `:core:designsystem`；不能把某页
  的 state、文案、业务校验迁入 core。

## 5. Screen、Route 与 ViewModel

### 5.1 Route：导航与平台入口

`<Area>Route` 负责读取稳定导航参数、获得 ViewModel，并连接需要平台生命周期的 bridge。
它不直接读 Repository，也不实现业务流程。

Route 参数只能是稳定 route、`ProjectId`、`SessionId` 等 ID 或可序列化的极小值对象；不得传递
`Parcelable` 业务对象、Repository、ViewModel、`File`、Composable lambda 或 SDK object。

### 5.2 Screen：尽量纯的渲染函数

`<Area>Screen` 接收 `UiState` 与回调，专注 layout、Material 组件、无障碍语义和局部 UI 行为。
它不注入 use case、不观察 Repository、不调用 data，也不创建业务 coroutine。

允许 Screen 使用 `remember`／`rememberSaveable` 保存**纯 UI 瞬态状态**，例如输入框光标、滚动
位置、抽屉是否展开、尚未提交的草稿文本。会影响业务真相、进程恢复或其他页面观察结果的状态，
必须由 ViewModel／domain 管理。

### 5.3 ViewModel：页面级状态 holder

ViewModel 只注入 domain use case 和必要的稳定导航参数。它负责：

1. 将 route 参数变成 domain 请求；
2. 观察 domain `Flow` 并组合成不可变 `UiState`；
3. 接收 `Action`，调用 use case，映射业务结果；
4. 输出一次性 `UiEffect`；
5. 在 `viewModelScope` 管理页面级任务和取消。

ViewModel 不得持有或注入 `Context`、`Activity`、`View`、`File`、`Uri`、WebView、Repository、
Room entity、DAO、DataStore、Git／网络 client、Koog message 或任何 SDK client。若某项能力不能
以 domain use case 表达，应先补齐 domain 边界，而不是在 ViewModel 绕过它。

## 6. 状态、Action 与 Effect

### 6.1 `UiState`

每个页面以一个不可变、可完整渲染的 `UiState` 为唯一长期 UI 状态来源。使用 value object 和
feature-local item model，而非直接暴露 domain／data DTO。

```kotlin
data class ProjectsUiState(
    val isLoading: Boolean = true,
    val projects: List<ProjectItemUi> = emptyList(),
    val pendingOperation: PendingOperation? = null,
    val error: UiError? = null,
)
```

- 列表必须不可变；不要向 UI 暴露 `MutableList`、`MutableStateFlow` 或可变 entity。
- loading、empty、content、refreshing、operation-in-progress 与可恢复错误应能从 state 明确区分。
- 不在 `UiState` 保存 `Throwable`、本地化文本、`@StringRes`、`Context` 或平台对象；使用稳定
  error code／语义类型，再由 UI 映射文案。
- 一个 Feature 可以有一个页面级 ViewModel；若独立子流程拥有自己的生命周期和状态，可建立
  子 ViewModel，但不能让多个 ViewModel 同时作为同一页面业务真相的 owner。

### 6.2 `Action`

简单点击可用语义明确的方法；交互多于少量动作时，定义 sealed `Action`，使所有用户意图可见、
可审查、可测试。

```kotlin
sealed interface ProjectsAction {
    data object Refresh : ProjectsAction
    data object CreateProjectClicked : ProjectsAction
    data class ProjectSelected(val projectId: ProjectId) : ProjectsAction
    data class ProjectNameChanged(val value: String) : ProjectsAction
    data object CreateConfirmed : ProjectsAction
}
```

Action 描述用户意图，而不是实现细节：使用 `CreateConfirmed`，而不是 `InsertProjectToRoom`；
使用 `ProjectSelected(projectId)`，而不是传递整个 data model。

### 6.3 `UiEffect`

`UiEffect` 只表示不可从持久 state 重放的单次行为：导航、打开系统文件选择器、请求权限、复制
剪贴板、显示短暂 Snackbar。它不承载列表内容、加载状态或可恢复业务真相。

```kotlin
sealed interface ProjectsEffect {
    data class NavigateToProject(val projectId: ProjectId) : ProjectsEffect
    data object LaunchTemplatePicker : ProjectsEffect
    data class ShowMessage(val errorCode: ErrorCode) : ProjectsEffect
}
```

使用有明确消费语义的 `SharedFlow`／Channel，并在 Route 层以 lifecycle-aware 方式收集。进程
恢复后仍必须存在的信息，放回 `UiState` 或 domain，而不是 effect。

## 7. 异步、错误与取消

- 在 ViewModel 中使用 `viewModelScope`；不要在 Composable 直接启动承载业务的长生命周期 coroutine。
- Domain 的 `Flow` 用 `stateIn`／`shareIn` 或明确收集策略转换为 state；避免每次重组创建新订阅。
- `CancellationException` 必须原样传播，不转换为用户错误或成功状态。
- domain 返回的 `OperationResult`、`AppError`、事件和 error code 应映射为 feature-local 状态；
  UI 再用 string resource 决定具体文案。
- 重试必须重发语义 action（如 `RetryInitialization`），不能重放 UI effect 或复制技术异常。
- 长操作显示可理解的 pending 状态；能取消时暴露取消 action，取消／恢复规则由 domain 定义。

## 8. 平台 bridge 的边界

权限、Activity Result、文件选择、分享、剪贴板、WebView 回调和系统设置跳转属于 UI bridge。

```text
Android callback / Activity Result
        -> bridge 解析、校验为稳定 UI 输入
        -> ViewModel Action
        -> domain request
```

bridge 可以引用 Android API，却只做平台输入输出转换：

- 可暂时接收用户选择的 `Uri`，但必须先转换为 domain request 所需的受控输入；`Uri` 本身
  不得跨越 domain API；
- 不解析数据库、不直接写文件、不执行 Git、不过滤模型 response；
- 不把 `Context` 或 `Uri` 存进 ViewModel state；
- 平台权限被拒绝时以明确 `Action`／`UiState` 表达，而不是静默失败。

如果 bridge 膨胀为业务流程，应先将流程移到 domain，bridge 保留最薄的平台适配层。

## 9. 导航规范

- 根导航由 `:app` 安装；Feature 定义自己的 route key 与参数解析，但不控制其他 Feature 的内部实现。
- Feature 间仅传 route 和稳定 ID。目标页面重新通过 domain 加载数据，避免携带过期对象。
- 若多个 Feature 必须直接使用同一 destination，只能新增极小 `:feature:<name>:api`；API 不包含
  Screen、ViewModel、Compose、Repository 或业务模型。
- 返回、深链和进程恢复都应能用同一组稳定参数重建页面。

## 10. 主题、i18n 与无障碍

- 使用 `MaterialTheme` 的 color、typography、shape token 与 `:core:designsystem` 组件；不得在
  Feature 定义另一套应用色板或启用 dynamic color。
- 用户可见文案归拥有页面的 Feature，在 `values/strings.xml` 与 `values-zh-rCN/strings.xml`
  同步维护。不要在 ViewModel、domain 或 data 返回本地化文本／resource ID。
- 为图标、图像、按钮、输入控件提供合适的 content description、label、状态和点击语义；纯装饰
  图标明确标记为无语义。
- 不以颜色作为唯一状态表达；错误、选中、处理中等状态必须有文本、图标或语义补充。
- 组件应支持字体缩放、窄屏、RTL 和触控目标；页面本地尺寸不应破坏设计系统约束。

## 11. DI、测试与评审

Hilt 只是实例化工具：Feature 可使用 `@HiltViewModel` 注入 domain use case，不能因为 Hilt
能访问 data 就注入 Repository。Feature 内部的 mapper／formatter 通常直接构造；仅当它有真实
替换需求时才建立 binding。

最低测试集：

| 改动 | 最低验证 |
| --- | --- |
| ViewModel | action -> use case request -> `UiState`／`UiEffect`；loading、空态、失败、重试、取消 |
| mapper | domain model／error code 到 feature UI model 的映射 |
| Screen | 关键状态的 Compose UI 测试：loading、empty、content、error、禁用／进行中 |
| bridge | Activity Result／权限成功、拒绝、取消与无效输入 |
| 导航 | route 参数解析、只传 ID、返回与深链重建 |

提交前检查：

- [ ] Feature 只依赖 domain、允许的 core 与极小 feature API；没有 data／runtime import。
- [ ] ViewModel 只调用 use case，没有 Repository、平台对象或 SDK client。
- [ ] Screen 能由明确 `UiState` 独立渲染，action 语义清楚。
- [ ] 持久状态在 `UiState`／domain，单次行为在 `UiEffect`。
- [ ] 用户文案已提供英文和 `zh-rCN` 资源，并使用 Material theme token。
- [ ] 新增 bridge 只做平台转换，不承载业务流程。
- [ ] 对应 ViewModel 和关键 UI 状态已有测试，且 `./gradlew check` 通过。
