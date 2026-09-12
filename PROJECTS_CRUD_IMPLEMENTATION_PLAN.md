# Projects CRUD 实现方案

> 本文是 `:feature:projects`、`:domain:project` 与 `:data:project` 的实现契约。
> 目标是在不破坏现有模块图的前提下，完成带**用户自定义项目图标**的 Project CRUD。
> 图标选择使用 uCrop；最终持久化格式固定为 **512 × 512 PNG**。

相关规范：

- UI 与 platform bridge：[`FEATURE_ARCHITECTURE.md`](FEATURE_ARCHITECTURE.md)
- domain 编排与错误边界：[`DOMAIN_ARCHITECTURE.md`](DOMAIN_ARCHITECTURE.md)
- data / filesystem 所有权：[`DATA_AND_STORAGE_ARCHITECTURE.md`](DATA_AND_STORAGE_ARCHITECTURE.md)
- 关闭运行时资源：[`PROJECT_RUNTIME_ARCHITECTURE.md`](PROJECT_RUNTIME_ARCHITECTURE.md)

---

## 1. 范围与完成标准

本期完成以下能力：

1. 观察项目列表；正确区分加载、空列表、读取失败与内容；
2. 创建草稿项目，输入名称、描述和可选的用户图片图标；
3. 编辑项目资料，支持保留、替换或移除现有图标；
4. 删除项目；运行中的项目必须先走既有 runtime 关闭协议；
5. 从图片选择器选择图片，经 uCrop 裁剪为正方形，并最终保存为**严格 512 × 512 的 PNG**；
6. 重启后能恢复项目资料和图标；中断的创建、更新、删除不会被错误显示为有效项目。

本期不实现模板内容写入、导入导出、快照、Agent 或 Preview backend；但 CRUD 的状态和 API 必须为这些流程预留正确入口，不能让 Workbench 打开未初始化的项目。

完成定义：

- 除 `bridge/` 外，Feature 不直接依赖 `:data:*`、`File`、`Uri`、`ContentResolver` 或图片文件路径；bridge 对 Android 图片 API 与 uCrop 的访问只用于输入转换；
- domain public API 不出现 `Uri`、`File`、`Bitmap`、`Context`、图片路径或 data DTO；
- data 是唯一读写项目 metadata、workspace 和 icon asset 的 owner；
- 所有 mutation 有明确的成功、可恢复失败、不可恢复校验失败和取消语义；
- `./gradlew :data:project:testDebugUnitTest :domain:project:testDebugUnitTest :feature:projects:testDebugUnitTest verifyModuleGraph verifyArchitectureSources` 通过。

---

## 2. 固定架构决策

### 2.1 保持当前模块方向

不为此次 CRUD 新建 `:contract:project`，也不把 Repository 泄露给 Feature。

```text
:feature:projects
    └─ :domain:project
          └─ :data:project
                └─ :core:filesystem

:feature:projects -- platform bridge --> Android Photo Picker / uCrop
```

这与当前 `verifyModuleGraph` 的方向一致：data 不依赖 domain，Feature 不依赖 data。`ProjectRepository` 可以继续由 `:data:project` 公开给 domain；它的签名只能使用 core/domain 允许的稳定 Kotlin 模型，不能暴露实现细节。

### 2.2 图标是 Project aggregate 的受控子资源

用户选择的原始 `Uri` 只在 Feature 的 bridge 内短暂存在。它不是 Project 的持久字段，也不能跨越 domain API。

```text
用户选择图片 Uri
  -> uCrop 交互和临时输出 Uri                         [:feature:projects bridge]
  -> 解码、缩放、编码为 512×512 PNG bytes             [:feature:projects bridge]
  -> ProjectIconUpload（纯 Kotlin value object）       [:domain:project]
  -> Create / Update Project use case                   [:domain:project]
  -> 校验并原子写入 projects/{id}/assets/icon-*.png    [:data:project]
  -> metadata 内部 revision（无路径、无 Uri）            [:data:project]
```

因此，旧项目中“Repository 接收 String URI 并直接复制文件”的做法不能迁移到新架构。

### 2.3 Project 生命周期

`ProjectStatus` 不能只是 metadata 字段；Feature 必须依赖它决定入口。

```text
Create project
  -> DRAFT
  -> （后续：选择空白/模板/导入）INITIALIZING
  -> READY

初始化失败 -> FAILED
删除开始   -> DELETING
```

| 状态 | Projects 点击行为 | 允许编辑资料 | 允许进入 Workbench |
| --- | --- | --- | --- |
| `DRAFT` | 打开项目设置/初始化入口 | 是 | 否 |
| `INITIALIZING` | 打开初始化进度 | 否 | 否 |
| `READY` | 打开 Workbench | 是 | 是 |
| `FAILED` | 打开初始化恢复入口 | 是 | 否 |
| `DELETING` | 不可选择，显示删除中 | 否 | 否 |

本次 CRUD 只创建 `DRAFT`。在初始化功能落地前，`DRAFT` 项目进入一个明确的“项目尚未初始化”页面，而不是直接进入 Workbench。

---

## 3. 稳定模型与公开 API

### 3.1 `:core:model`：持久 Project 摘要

`core:model` 只保留跨域稳定的项目摘要，不携带图标资源的输入、读取或存储语义。

```kotlin
data class Project(
    val id: ProjectId,
    val name: String,
    val description: String,
    val hasCustomIcon: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val status: ProjectStatus,
)

```

### 3.2 `:domain:project`：Feature 可调用用例与图标输入

Feature 使用 domain 的图标契约；它们不是 core model，也不是 data DTO。

```kotlin
class ProjectIconUpload private constructor(private val pngBytes: ByteArray) {
    fun copyPngBytes(): ByteArray = pngBytes.copyOf()
}

class ProjectIconContent private constructor(private val pngBytes: ByteArray) {
    fun copyPngBytes(): ByteArray = pngBytes.copyOf()
}

sealed interface ProjectIconChange {
    data object Keep : ProjectIconChange
    data object Remove : ProjectIconChange
    data class Replace(val icon: ProjectIconUpload) : ProjectIconChange
}
```

domain 把这些类型映射为 `:data:project` 的 `ProjectIconData` / `ProjectIconDataChange`。后两者仅是
Repository 对 domain 的资源合同，Feature 永远不导入它们。

说明：

- `ProjectIconUpload` 必须复制输入字节，避免调用者在异步写入前修改数组；data 读取时再次复制；
- `ProjectIconChange` 不能用 nullable icon 代替：`null` 无法区分“保持旧图标”和“移除图标”；
- 即使 bridge 已经保证 PNG 和 512×512，data 仍必须验证，不能信任上游输入。

### 3.3 `:domain:project`：Feature 可调用用例

```kotlin
data class CreateProjectRequest(
    val name: String,
    val description: String,
    val icon: ProjectIconUpload?,
)

data class UpdateProjectProfileRequest(
    val projectId: ProjectId,
    val name: String,
    val description: String,
    val iconChange: ProjectIconChange,
)

class ObserveProjectsUseCase {
    operator fun invoke(): Flow<OperationResult<List<Project>>>
}

class CreateDraftProjectUseCase {
    suspend operator fun invoke(request: CreateProjectRequest): OperationResult<Project>
}

class UpdateProjectProfileUseCase {
    suspend operator fun invoke(request: UpdateProjectProfileRequest): OperationResult<Project>
}

class LoadProjectIconUseCase {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<ProjectIconContent?>
}

class DeleteProjectUseCase {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<Unit>
}
```

`ProjectIconContent` 同样是只读、复制保护的 PNG bytes value object。它是 data 到 Feature 的受控读取结果，不是文件句柄。Feature 将其转换为自身的瞬态图片 UI 模型；不得将它写入 SavedStateHandle 或导航参数。

用例职责：

- `CreateDraftProjectUseCase`：校验 profile，创建有 workspace 的 `DRAFT` 项目；
- `UpdateProjectProfileUseCase`：校验 profile，映射 `Keep/Remove/Replace`；
- `ObserveProjectsUseCase`：把 data 的读取失败转换为 `ProjectDomainError`，不能把错误伪装为空列表；
- `LoadProjectIconUseCase`：只读取图标内容，不暴露受控目录；
- `DeleteProjectUseCase`：在用户已确认“停止并删除”后，调用 `ConfirmProjectRuntimeCloseUseCase` 关闭 Agent / Preview，再删除项目资源；关闭失败时不触及项目目录。

### 3.4 校验与错误

名称和描述的规则只有 domain 是业务真相：

| 字段 | 规则 |
| --- | --- |
| name | trim 后非空；最多 60 Unicode code points；NFC 规范化；重名比较使用 `lowercase(Locale.ROOT)` |
| description | trim；可为空；最多 280 Unicode code points |
| icon upload | 非空 PNG；解码后严格 512×512；不超过 2 MiB |

Feature 可做同样的即时校验以禁用提交按钮，但不得依赖它保障数据正确性。

新增/明确以下 domain error：

```text
project_invalid_name
project_invalid_description
project_name_exists
project_not_found
project_icon_invalid
project_icon_too_large
project_storage_unavailable
project_invalid_state
project_runtime_close_failed
```

读取某个项目的 metadata 损坏时，data 不应令整个列表崩溃；跳过该目录并记录可诊断的 `project_metadata_corrupt`。若根目录无法读取，则 `ObserveProjectsUseCase` 返回失败，UI 显示重试页而不是空态。

---

## 4. data:project 实现

### 4.1 目录和 metadata

```text
projects/
  .staging/
    {project-id}/                    # 仅创建事务中存在
  {project-id}/
    project.json
    workspace/
    assets/
      icon-{epochMillis}.png         # 单调递增的时间戳；只允许 data 生成
```

建议 metadata 使用显式 schema version，且只记录相对、受控的信息：

```json
{
  "schemaVersion": 1,
  "id": "…",
  "name": "My App",
  "description": "…",
  "status": "DRAFT",
  "createdAtEpochMillis": 0,
  "updatedAtEpochMillis": 0,
  "iconRevision": "1735689600000"
}
```

不得存储：用户输入 URI、绝对路径、`content://`、`file://`、Bitmap、base64 图片、裁剪 SDK 的对象。

### 4.2 需要补足的通用 filesystem 原语

当前 `ControlledFileSystem` 只有文本写入，无法安全完成图标与创建提交。只新增 CRUD 实际需要的最小能力：

```kotlin
suspend fun readBytes(path: RelativePath): FileSystemResult<ByteArray?>
suspend fun writeBytesAtomically(path: RelativePath, bytes: ByteArray): FileSystemResult<Unit>
suspend fun moveDirectoryAtomically(
    source: RelativePath,
    destination: RelativePath,
): FileSystemResult<Unit>
```

这些都是业务无关的文件系统操作：接口名称、参数和错误类型不能出现 `Project`、`workspace`、`icon`、
metadata 或任何 data/domain 模型。项目目录布局、staging 目录名称与“何时移动目录”的事务语义只属于
`:data:project`；filesystem 只保证受控根目录内的通用读、写、删除与移动。

`moveDirectoryAtomically` 在不支持同文件系统原子 move 时必须失败；data 保留 staging 目录供恢复清理，不能悄悄退化为跨目录复制。所有路径继续由 `RelativePath` 约束。

### 4.3 创建事务

创建不能依赖“失败后尽力 delete”的回滚作为唯一保障。实现顺序：

1. 在 `projects/.staging/{id}` 创建 `workspace/` 与 `assets/`；
2. 若有图标，验证 PNG 后原子写入 `assets/icon-{epochMillis}.png`；
3. 原子写入完整 `project.json`，状态为 `DRAFT`；
4. 原子移动 staging 目录至 `projects/{id}`；
5. 仅在第 4 步成功后向 `StateFlow` 发布新列表。

应用启动或首次加载列表前清理 `.staging/` 中未提交目录。若清理失败，返回 storage error；不能把它当成正常空列表。

### 4.4 更新资料与替换图标

`UpdateProjectProfile` 在 data 的同一把 project mutex 内执行。

- `Keep`：只原子覆盖 metadata；
- `Replace`：验证 upload，写入新 `icon-{epochMillis}.png`，再原子覆盖引用新 revision 的 metadata；旧图标保留，作为未来历史图标列表的资源。
- `Remove`：原子覆盖 iconRevision 为 null；已存在的图标文件保留，移除只改变当前图标的引用。
- 写新图标后、metadata 提交前进程死亡：该文件未进入历史列表，后续可由专门的资源维护任务清理；不得在项目加载路径中猜测性删除历史资源。

Project 图标总是通过 metadata 的 revision 查找；不允许根据用户输入拼接路径。

### 4.5 读取、排序和内存状态

`observeProjects()` 的资源合同应表达失败：

```kotlin
fun observeProjects(): Flow<OperationResult<List<Project>>>
```

首次订阅前加载持久化数据，成功后按 `updatedAtEpochMillis DESC, id ASC` 排序发布。根目录读取失败发出 `Failure(StorageUnavailable)`；之后的显式重试重新加载。不要用 `onStart { ensureLoaded() }` 后继续发空 `StateFlow`，否则 UI 无法分辨加载失败与无项目。

`loadProjectIcon(projectId)` 必须校验 metadata 中的 revision、文件存在性、PNG header 与 512×512 尺寸；图标文件损坏只影响该图标，返回稳定错误或空 icon，不能使整个项目列表不可用。

---

## 5. Feature: 图片选择、裁剪与展示

### 5.1 `ProjectIconPickerBridge`

bridge 放在 `:feature:projects/bridge/`，只负责 Android callback 到稳定输入的转换：

```text
PickVisualMedia(ImageOnly)
  -> uCrop.of(sourceUri, cacheOutputUri)
       .withAspectRatio(1f, 1f)
       .withMaxResultSize(512, 512)
       .withCompressionFormat(PNG)
  -> decode crop output
  -> 若不是 512×512，缩放至严格 512×512
  -> PNG encode
  -> ProjectIconUpload.fromPng(bytes)
  -> ProjectsAction.IconPrepared(upload)
```

`withMaxResultSize` 只能保证最大尺寸；小图可能仍小于 512，因此必须在 crop 回调后解码并缩放一次，最终输出才符合“512×512 PNG”的产品要求。

bridge 的规则：

- cache 输出 URI 只在 bridge / 当前 UI 流程有效；不进入 ViewModel 的持久状态、domain 请求或 data metadata；
- crop 被取消时不产生 action，保留原图标；
- crop/解码/编码失败时产生 `IconPreparationFailed` action，由 Feature 显示本地化错误；
- 使用有采样与上限的解码，避免超大图片消耗内存；最终输出仍必须不超过 2 MiB；
- uCrop 和 Android 图片 API 的依赖只加入 `:feature:projects`，不进入 domain/data public API。
- 当前使用的 JitPack uCrop artifact 不携带完整 manifest / transitive metadata，因此 Feature 显式声明
  `UCropActivity`，并直接依赖 `androidx.transition`、`androidx.exifinterface`；裁剪输出必须通过
  本应用的 FileProvider `content://` URI，不得传递 `file://` URI。

### 5.2 UI state 与 action

表单的未提交文字、打开的 sheet 与临时预览是页面局部状态；持久项目资料和 mutation 结果是 ViewModel state。不要把原始 URI 放进 `rememberSaveable`。

建议状态最少包含：

```kotlin
data class ProjectsUiState(
    val content: ContentState<List<ProjectItemUi>> = ContentState.Loading,
    val operation: ProjectOperationUiState = ProjectOperationUiState.Idle,
)

data class ProjectItemUi(
    val id: ProjectId,
    val name: String,
    val description: String,
    val status: ProjectStatus,
    val updatedAtEpochMillis: Long,
    val icon: ProjectIconUi,
)
```

`ProjectIconUi` 只能是 feature-local、内存中的“未加载/默认/已加载内容”模型；它不是 URI 或路径，也不进 SavedStateHandle。列表图标按有限并发读取，读取中显示默认占位图，项目的 metadata 列表不因单个图标加载失败而失败。

建议 action：

```kotlin
CreateConfirmed(name, description, icon: ProjectIconUpload?)
UpdateConfirmed(projectId, name, description, iconChange)
DeleteConfirmed(projectId)
SelectProject(projectId)
IconPrepared(ProjectIconUpload)
IconPreparationFailed
RetryListLoad
AcknowledgeOperation
```

不要保留“失败时重放上一条任意 action”的 `retryAction`。重试只用于列表加载；创建、更新、删除失败后，用户必须在保留的表单/确认框中再次明确提交，避免误重放删除。

### 5.3 Screen 交互

- 列表项显示项目图标、名称、最多两行描述、更新时间、状态；
- 创建/编辑使用同一 profile form，支持预览、替换、移除图标；
- mutation 进行中禁用 FAB、提交、编辑和删除入口，ViewModel 也必须拒绝第二个 mutation；
- 删除 dialog 明确说明会删除 workspace、图标和项目资料；
- `READY` 进入 Workbench；其他状态路由至 `:feature:projects` 内的 setup/recovery screen；
- 所有图标按钮、预览、编辑和删除动作提供 content description；错误不只用颜色表达。

---

## 6. 删除与 runtime / session 协调

删除不能只调用 `data:project.delete()`。

```text
Feature: 用户确认删除
  -> domain:project DeleteProjectUseCase
  -> ConfirmProjectRuntimeCloseUseCase 停止 Agent / Preview
  -> 删除 project 目录（幂等）
  -> 从列表移除
```

本期确认框明确为“停止并删除”；`DeleteProjectUseCase` 只有在 runtime close 成功后才调用
repository。close 失败返回 `project_runtime_close_failed`，目录与 metadata 保持不变。

当 session 有持久化实现后，必须在同一个 domain 用例中加入窄的 `DeleteSessionsForProject` 合同，
并把 `DELETING` 做成可恢复的持久状态；届时 session 清理成功但目录删除失败时，项目不能重新变成可打开的
`READY`。不要在当前没有 session owner 时伪造一个空的“成功删除 session”。跨资源顺序、重试和恢复属于
`:domain:project`，单个目录删除原子性属于 `:data:project`。

---

## 7. 实施顺序

1. **模型和 filesystem**：扩展 `Project`、新增 icon value object、bytes 原语、staging directory move；验证相对路径与原子写。
2. **data CRUD**：实现 metadata schema、创建 staging 提交、观察错误、profile 更新、图标读取和恢复清理。
3. **domain 用例**：输入校验、data error mapping、`CreateDraft` / `UpdateProfile` / `LoadIcon` / `Delete`。
4. **Feature 基础 CRUD**：重做 ViewModel operation 状态与 Retry 语义；补项目列表、创建/编辑/删除 UI 与状态路由。
5. **图片 bridge**：接入 Photo Picker + uCrop，严格规范化为 512×512 PNG，接入创建和编辑表单。
6. **删除协调**：接入 project runtime close contract 和 session 删除；再允许删除 `READY` 项目。

每一步都先写对应测试，再接下一层；不要先做完整 UI 后再补 storage 原子性。

---

## 8. 最低测试矩阵

| 层 | 必须覆盖 |
| --- | --- |
| `core:filesystem` | bytes 原子写、路径逃逸拒绝、staging move 失败不伪装成功 |
| `data:project` | 创建有/无图标、PNG 非法/尺寸非 512/超限拒绝、重启恢复、替换/移除图标、metadata 提交前中断后的清理、根目录读取失败、损坏单项目 metadata、稳定排序 |
| `domain:project` | 名称/描述规范化与重名、所有 error mapping、状态禁止操作、删除前 runtime close、取消原样传播 |
| `feature:projects` | loading/empty/content/error、重复提交被拒绝、mutation 失败不重放删除、DRAFT 与 READY 路由、图标准备失败、操作中控件禁用 |
| `feature:projects` androidTest | 创建填写资料后分发 `CreateConfirmed`、编辑保留图标意图并分发 `UpdateConfirmed`、删除二次确认后分发 `DeleteConfirmed` |
| bridge / UI test | Photo Picker 取消、uCrop 成功、crop 失败、最终输出 PNG 且为 512×512、替换和移除预览、无障碍语义 |

提交前运行：

```bash
./gradlew \
  :core:filesystem:testDebugUnitTest \
  :data:project:testDebugUnitTest \
  :domain:project:testDebugUnitTest \
  :feature:projects:testDebugUnitTest \
  :feature:projects:connectedDebugAndroidTest \
  verifyModuleGraph \
  verifyArchitectureSources
```

---

## 9. 禁止项

- ViewModel 或非 bridge 的 Feature 代码注入 `ProjectRepository`、`ControlledFileSystem`、`ContentResolver` 或 `File`；
- 在 Project metadata 保存 `Uri`、绝对路径、图片 base64 或 uCrop 输出文件名；
- domain API 接受 Android `Uri`、`Bitmap`、`Context`、`File` 或 lambda callback；
- data 根据用户输入拼接 icon 文件路径；
- 用空列表表示存储读取失败；
- 删除时吞掉 session / runtime 失败，或在 runtime 未关闭时直接删目录；
- 用失败 action 的自动重放实现 mutation retry；
- 把图片字节、临时 URI 或 Bitmap 放入 SavedStateHandle、导航参数或持久 UI state。

这份方案的关键不在于新增很多层，而是让每个临时图片输入、持久图标资源、项目状态与跨资源删除都有唯一 owner 和可验证的恢复语义。
