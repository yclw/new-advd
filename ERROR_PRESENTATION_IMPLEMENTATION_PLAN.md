# 错误呈现机制实现方案

> 本文定义 AVD 的 UI 错误归属、状态建模、呈现入口与迁移规则。
> 目标是让业务错误在一个清晰的 UI 边界被呈现，避免子组件层层持有
> `error` 状态并继续通过 `onError` 回调向上传递。

相关文档：

- [`FEATURE_ARCHITECTURE.md`](FEATURE_ARCHITECTURE.md)
- [`DOMAIN_ARCHITECTURE.md`](DOMAIN_ARCHITECTURE.md)
- [`PROJECT_INITIALIZATION_IMPLEMENTATION_PLAN.md`](PROJECT_INITIALIZATION_IMPLEMENTATION_PLAN.md)

---

## 1. 问题与目标

当前工程已有 `AppError`、`OperationResult`、`UiError`、`ContentState` 与
`OperationState`，可以将 data/domain 的稳定错误码传到 Feature。但是错误的**呈现**仍分散：

- Screen 可能用全页错误、Dialog 或固定 `Text`；
- 组件可能通过 `rememberSaveable { mutableStateOf(...) }` 保存错误；
- platform bridge 通过 `onError: () -> Unit` 将失败传给直接父组件；
- 初始化恢复失败目前可能继续呈现为“进行中”。

这会导致同一失败在不同层重复建模、展示不一致，并让子组件知道它不应决定的 UX 细节。

本方案的完成标准：

1. 每个错误只有一个明确 owner，且该 owner 决定它是否应作为状态、消息或字段错误呈现；
2. feature 内的子组件不保存通用业务错误文本，也不负责决定 Toast、Dialog 或全页错误；
3. 同一个 Feature 在任意嵌套 Route/Dialog/组件中，非阻塞消息均由一个 Feature-level host 呈现；
4. 所有重试均重发命名 action，不重放旧 effect、lambda 或技术异常；
5. 进程重建后仍应存在的错误保留在 `UiState`；只需通知一次的错误使用 event；
6. UI 不展示 `Throwable`、文件路径、JNI/Git 原始输出或未经映射的错误码。

本方案不建立跨整个 app 的全局业务错误队列，不让 data/domain 依赖 Android UI 类型，也不将字段校验
伪装成 Toast。

---

## 2. 固定决策

### 2.1 错误按用户可见语义分类，而不是按异常来源分类

| 类别 | 示例 | 保存位置 | 呈现位置 | 是否可重建 |
| --- | --- | --- | --- | --- |
| 内容错误 | 项目列表无法读取 | `ScreenUiState` | Route 的全页错误内容 | 是 |
| 阻塞操作错误 | 创建、删除、Blank 初始化失败 | `ScreenUiState.operation` | Route 的阻塞状态或确认 Dialog | 视操作而定 |
| 非阻塞、无 action 的操作反馈 | 图标裁剪失败、复制失败 | `UiEvent.ShowMessage` | Android Toast | 否 |
| 字段校验 | 项目名称为空、描述超长 | `FormUiState` | 对应字段的 supporting/error content | 是 |
| 平台输入失败 | 图库选择取消、权限拒绝、裁剪器错误 | bridge 结果后转换为上述类别 | 不由 bridge 呈现 | 取决于转换后类别 |
| 恢复/不变量错误 | 初始化 journal 不一致、READY 缺少初始 revision | `ScreenUiState.RecoveryRequired` | 专用恢复页面 | 是 |

“可重建”指 activity/进程重建后用户是否仍需要看到这个状态。只有这类状态才能进入 `StateFlow` 的
持久 UI 状态；Toast、导航、打开系统选择器等一次性动作不得作为可重放状态保存。

### 2.2 由 Feature Route 呈现一次性消息

一个 Feature 的 Route 收集自己的 message event，并通过 Android Toast 呈现无 action 的短反馈。
Toast 不属于页面 Scaffold 层级，因此不会被同一 app 的 Dialog 或 Drawer 遮挡。App Root 只提供
app-wide 状态（例如离线），不接收所有 Feature 的业务错误。

```text
data/domain AppError
        ↓
Feature ViewModel maps to UiError or UiEvent
        ↓
Feature Route collects UiEvent
        ↓
Android Toast
        ↓
Screen / Dialog / child component
```

如一个 Feature 有多个独立导航 destination，每个可见 Route 只收集自己的 event；消息不得穿透已离开
的 destination 后在另一个页面展示。

### 2.3 子组件上报结果，不上报呈现请求

bridge 和可复用组件只能上报其自身的输入、输出及有限结果。例如：

```kotlin
sealed interface IconPreparationResult {
    data class Ready(val icon: ProjectIconUpload) : IconPreparationResult
    data object InvalidImage : IconPreparationResult
    data object ProcessingFailed : IconPreparationResult
}
```

父 Screen 将结果发送为 action；ViewModel 根据当前业务流程决定：

- 成功：更新 `FormUiState`；
- 用户可立即修正的输入错误：更新字段状态；
- 无法在组件中解释的失败：发送 `UiEvent.ShowMessage`；
- 必须阻止完成操作的失败：更新操作状态。

禁止新增下列通用模式：

```kotlin
var error by rememberSaveable { mutableStateOf<String?>(null) }
onError = { error = "..." }
```

子组件可保存纯视觉/输入临时状态（文本输入、焦点、展开状态、裁剪交互进度），但不得保存跨组件的
业务错误或硬编码用户文案。

### 2.4 `UiError` 仅表达稳定用户语义

`UiError` 不持有 `Throwable`。其 message key 必须映射到资源文案；必要时可带稳定的 action 和
operation id。

建议将现有模型扩展为：

```kotlin
data class UiError(
    val messageKey: String,
    val retryable: Boolean,
    val operationId: String? = null,
)

sealed interface UiEvent {
    data class ShowMessage(
        val error: UiError,
        val action: UiMessageAction? = null,
    ) : UiEvent
}

data class UiMessageAction(
    val labelKey: String,
    val action: UiAction,
)
```

`UiAction` 是 Feature 自己定义的 sealed action，不是 lambda、`Throwable`、`Uri` 或 repository 请求。
只有确实支持重试的错误才提供 Retry action。

---

## 3. 层级职责

| 层 | 允许做什么 | 禁止做什么 |
| --- | --- | --- |
| data | 将 I/O、Git、平台失败映射为稳定 `AppError`；保留恢复所需状态 | 返回 UI 字符串、Toast 类型或 `Throwable` 给 Feature |
| domain | 编排资源操作；将资源错误映射为领域错误 | 决定 UI 呈现方式 |
| ViewModel | 将领域结果映射为 `UiState` 和 `UiEvent`；处理语义 action | 直接调用 Android Toast 或持有任何 Android UI host |
| Route | 收集 `UiState`/`UiEvent`；创建 feature host；把 host action 转为 ViewModel action | 解析 data/domain 错误、让子组件自行订阅 event |
| Screen/Dialog | 无状态渲染；把用户输入转换为 action | 保存通用业务错误、决定跨页面消息策略 |
| bridge | 返回受限结果；原样传播取消 | 直接显示 Toast/Dialog，或把技术异常暴露给 UI |

---

## 4. 状态与事件契约

### 4.1 页面级状态

每个 Route 使用面向该页面的 sealed `ScreenUiState`，不要为了方便把所有内容都塞进一个可空 data class。

```kotlin
sealed interface ProjectSetupUiState {
    data class Ready(
        val form: ProjectSetupFormUiState,
        val operation: SetupOperationState = SetupOperationState.Idle,
    ) : ProjectSetupUiState

    data class RecoveryRequired(
        val error: UiError,
    ) : ProjectSetupUiState

    data object Loading : ProjectSetupUiState
}
```

可阻塞的长操作使用页面自己的 operation state。它必须是 projectId 作用域的状态，并在成功、失败、
取消或页面切换时进入终态；不得让旧项目的 `InProgress` 禁用新项目页面。

```kotlin
sealed interface SetupOperationState {
    data object Idle : SetupOperationState
    data object InitializingBlank : SetupOperationState
    data class Failed(val error: UiError) : SetupOperationState
}
```

如果失败表示项目处于可恢复但未完成的 `INITIALIZING`，页面状态应为 `RecoveryRequired` 或明确的
`Initializing` 失败态，而不是永远显示 spinner。

### 4.2 一次性事件

`UiEvent` 使用 `Channel` 或配置为无 replay 的 `MutableSharedFlow` 发出。Route 只在当前可见时收集。

```kotlin
sealed interface ProjectsEvent {
    data class ShowMessage(val error: UiError) : ProjectsEvent
    data class NavigateToProject(val projectId: ProjectId) : ProjectsEvent
}
```

导航与消息都是 event；两者不能写入同一个 `OperationState.Succeeded` 后再由 Screen 回放。一个动作的
成功状态应回到正常 `UiState`，需要导航时另发一次 event。

### 4.3 表单状态

字段校验是可见、可编辑、可恢复的状态，应属于表单而非消息队列。

```kotlin
data class ProjectProfileFormUiState(
    val name: String,
    val description: String,
    val icon: ProjectIconPreview?,
    val nameError: UiError? = null,
    val descriptionError: UiError? = null,
)
```

输入变化应清除与该字段对应的错误。提交失败的跨字段/服务端错误不能被伪装成任意一个字段错误。

---

## 5. 呈现规则

### 5.1 全页错误

只有无法展示主要内容、或继续操作会破坏状态时，使用全页错误。必须提供明确的语义 action：

- Retry：重新执行命名加载/recovery action；
- Back：返回安全的上一级 destination；
- Delete/Reset：仅在恢复策略明确支持时出现。

全页错误不显示“未知错误”。无法确定具体原因时，显示稳定的通用恢复文案并记录技术细节到日志。

### 5.2 Toast

Toast 适用于用户无需离开当前上下文、无需阅读长解释、且不需要 action 的反馈。例如图标处理失败、
复制失败。它可覆盖同一 app 的 Dialog 和 Drawer，因此适用于由 modal 流程触发的短错误。

Toast 不用于：字段校验、初始化/Git 恢复等阻塞不变量错误、需要用户确认的数据删除，以及需要 Retry/
Undo 等 action 的反馈。后一类必须保留在页面/Dialog 状态，或在未来引入位于 modal 之上的 app overlay。

### 5.3 Dialog

Dialog 只用于确认或需要立即作出明确选择的场景。它的可见性是 Screen state 的一部分；错误信息可以
作为 Dialog 内容，但 Dialog 不应作为任何错误的默认出口。

### 5.4 组件内联错误

输入字段的校验使用组件内联 error/supporting content。组件接收结构化字段状态，而不是接收硬编码
字符串或自行定义业务失败文案。

---

## 6. `:feature:projects` 迁移范围

### 6.1 当前问题

- `ProjectProfileDialog` 保存 `iconError`，并由 `ProjectIconPickerBridge.onError` 直接改变；
- `ProjectSetupRoute` 将初始化失败固定显示为 storage error，未使用实际 `UiError`；
- `ProjectInitializationRoute` 恢复失败没有可见失败状态；
- `ProjectsScreen` 同时包含列表错误、操作 Dialog 和局部错误文字，尚未统一一次性消息出口；
- `ProjectsEffect` 只表达导航，无法承载统一的非阻塞反馈。

### 6.2 目标结构

```text
ProjectIconPickerBridge
  -> ProjectsAction.IconPreparationCompleted(result)
  -> ProjectsViewModel
       -> profile form state (correctable field state)
       -> ProjectsEvent.ShowMessage(UiError) (non-blocking failure)
       -> operation / recovery state (blocking failure)
  -> ProjectsRoute Android Toast
  -> ProjectsScreen / ProjectProfileDialog render state only
```

### 6.3 实施步骤

1. 在 `:core:ui` 新增最小 `UiEvent`/message action contract；不修改 `AppError` 或 data/domain API。
2. 在 `:feature:projects` 为项目列表、Setup、Initialization Route 定义各自的页面 state 和 event。
3. 在 `ProjectsRoute` 收集 message event 并通过 Toast 呈现；保证仅当前 destination 消费消息。
4. 将 `ProjectIconPickerBridge` 改为返回结构化 result；移除 `ProjectProfileDialog.iconError` 和通用
   `onError` 回调。
5. 将创建、更新、删除、初始化的 error 映射统一放入 projects mapper；补足字符串资源。
6. 将 journal/recovery 错误渲染为明确的恢复页面，提供可用的 retry/back action，移除无限 spinner。
7. 删除由本次迁移产生的旧错误 state、重复字符串和无效 acknowledge action。

不在此迁移中把所有 Feature 的状态类统一成同一个泛型 `Async<T>`；不同页面对 Empty、Loading、
RecoveryRequired 和操作中状态的需求不同。

---

## 7. 与 Now in Android 的关系

NIA 可作为 Compose 状态与事件边界的参考，而不是直接复制的完整错误框架：

- `TopicViewModel` 将流的 `Result` 映射为 screen 的 sealed UI state；
- app `Scaffold` 创建 `SnackbarHostState`，并通过 `CompositionLocal` 提供给当前导航入口；
- Bookmarks navigation entry 将 screen 的 snackbar 请求连接至该 host；这属于 NIA 的呈现选择，不是
  AVD 项目流程的实现；
- ViewModel 不直接持有 UI host。

AVD 采用相同的“状态归 ViewModel、事件由 Route 消费”的边界。NIA 的 Snackbar 位于 app Scaffold，
但 AVD 的项目流程会从 Dialog 与 Drawer 触发短反馈；因此无 action 的反馈使用 Toast。项目初始化有
恢复、Git 和持久状态不变量，必须保留 Feature 自己的 `RecoveryRequired` 页面状态，不能仅以 Toast
代替。

---

## 8. 测试矩阵

| 层 | 场景 | 预期 |
| --- | --- | --- |
| ViewModel | 图标处理失败 | 发出一个 message event；Dialog 不保存通用错误文本 |
| ViewModel | 名称为空 | 更新 `nameError`；不发送 Toast |
| ViewModel | Blank 初始化失败 | 操作进入可重试失败态；不导航 Workbench |
| ViewModel | 恢复失败 | 进入 `RecoveryRequired`；不永久显示 loading |
| Route | 收到 message event | 当前 destination 显示一次 Toast；离开后不在下一页重放 |
| Screen | `RecoveryRequired` | 显示错误、Retry 和 Back，不展示无限 spinner |
| UI | 图标处理成功 | 清除此前字段/输入错误，更新 icon state |
| UI | 项目 A 初始化后进入项目 B | A 的 operation/error 状态不会禁用或污染 B |

提交前至少执行：

```bash
./gradlew \
  :core:ui:testDebugUnitTest \
  :feature:projects:testDebugUnitTest \
  :feature:projects:connectedDebugAndroidTest \
  verifyModuleGraph \
  verifyArchitectureSources
```

若设备测试不可用，必须至少为 Route 的 event 消费和各个 Screen UI state 补充 Compose/unit test；
不能只验证 ViewModel 而不验证 Toast、全页恢复错误与内联字段错误的实际呈现。
