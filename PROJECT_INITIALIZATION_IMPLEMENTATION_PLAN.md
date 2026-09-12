# 项目初始化（Blank）实现计划

> 本文是 `:feature:projects`、`:domain:project`、`:data:project` 与
> `:domain:version` 的实现契约。
> 本期只实现**空白项目（Blank）初始化**；模板和导入不实现，但边界必须允许它们复用相同的发布、
> Git 首版本和恢复流程。

相关规范：

- 项目 CRUD 与状态定义：[`PROJECTS_CRUD_IMPLEMENTATION_PLAN.md`](PROJECTS_CRUD_IMPLEMENTATION_PLAN.md)
- domain 编排：[`DOMAIN_ARCHITECTURE.md`](DOMAIN_ARCHITECTURE.md)
- data、filesystem 与 Git 所有权：[`DATA_AND_STORAGE_ARCHITECTURE.md`](DATA_AND_STORAGE_ARCHITECTURE.md)
- 项目级写/Git lease 与恢复：[`PROJECT_RUNTIME_ARCHITECTURE.md`](PROJECT_RUNTIME_ARCHITECTURE.md)

---

## 1. 范围与完成标准

### 1.1 本期范围

实现用户从项目列表打开一个 `DRAFT` 项目、选择“空白项目”、完成可恢复初始化并进入
Workbench 的完整路径：

```text
Create draft
  -> DRAFT
  -> choose Blank
  -> INITIALIZING
  -> materialize blank workspace in staging
  -> create the initial linear Git revision
  -> publish workspace payload
  -> READY
  -> Workbench
```

Blank 是唯一可选来源。它使用一个由 domain 定义的、固定且可测试的最小工作区内容；本期不定义
模板、导入文件、预览服务、Agent、远端 Git、分支或合并。

### 1.2 非目标

- 不把创建 Draft 和初始化合并为一个操作；用户仍可修改或删除未初始化项目；
- 不因方便把 `DRAFT` 直接标为 `READY`；
- 不实现通用的 `InitializeProjectUseCase(source: ...)` UI/API；本期公开用例命名为
  `InitializeBlankProjectUseCase`；
- 不让 Feature 传 `File`、`Uri`、archive、Git path、Git commit message 或 lambda 给 data；
- 不实现版本列表、手动快照、恢复版本的 UI；但必须建立后续版本功能可读取的首个 revision；
- 不以初始 revision 的显示 label、普通 snapshot label 或 commit subject 编码初始化语义。

### 1.3 代码文本与注释约束

- 所有代码内容使用英文，包括标识符、字符串、异常信息、测试名称和注释；
- 注释只能说明代码当前的行为、输入、输出或限制，不写架构决策、设计理由、取舍或未来规划。

### 1.4 完成定义

- 新建项目保持 `DRAFT`，点击它进入项目设置/初始化入口，不会静默无响应；
- 用户选择 Blank 后，只有 workspace payload 和其初始 Git revision 都完整存在时项目才成为 `READY`；
- 任意一次进程死亡、I/O 失败或 Git 失败，不得把不完整项目发布为 `READY`；
- `READY` 项目总有一个可解析的初始线性 revision；
- 重启后可恢复或明确标记未完成初始化，不会重复创建首个 commit；
- 后续 Template、Import 初始化方式可以复用发布事务，不需要改写 `READY` 不变量或 Version API；
- 相关 unit/integration test 与模块图校验通过。

---

## 2. 固定决策

### 2.1 初始化来源与发布事务分离

“Blank / Template / Import”不是同一个简单操作。它们的内容准备、校验和错误都不同；只有最后的
发布过程相同。

```text
source-specific preparation                  common publication transaction

Blank       -> fixed initial workspace  ─┐
Template    -> load + validate template  ─┼-> staging -> initial revision -> READY
Import      -> inspect + normalize input ─┘
```

因此本期实现：

```kotlin
class InitializeBlankProjectUseCase
```

而不是提前暴露包含尚不存在的 `TemplateId` / archive 输入的泛化 source union。第二种初始化方式
真正落地后，再抽取 shared internal publisher；不要让猜测出来的抽象先成为 public API。

### 2.2 domain 编排 Project 与 Version 资源

初始化确实同时依赖 Project 和 Version：前者拥有项目状态、workspace、journal 和发布；后者拥有
线性 Git history。关系必须在 domain 明确，而不是让任一 Repository 依赖另一 Repository 的协议。

```text
:feature:projects
    -> InitializeBlankProjectUseCase                    [:domain:project]
         -> ProjectRepository.prepareInitialization()   [:data:project]
         -> VersionRepository.createInitialRevision()   [:data:project]
         -> ProjectRepository.publishInitialization()   [:data:project]
```

`ProjectRepository` 不导入或调用 `VersionRepository`；`VersionRepository` 也不负责把 `Project`
改为 `READY`。初始化实现必须为两者提供同一项目的 mutation coordination；不能把
`DefaultVersionRepository` 的进程内 `Mutex` 当作与 Project 操作共享的 lease。

### 2.3 初始 revision 是初始化的硬前置条件

`READY` 的定义为：

```text
published workspace payload exists
AND it contains a valid Git work tree + Git directory
AND HEAD resolves to the initial revision recorded by the initialization journal
AND project metadata is READY
```

初始 revision 不是版本页稍后调用的 best-effort `ensureInitialSnapshot()`，也不是普通的带用户 label
的快照。Git 失败则初始化未完成；不能发布 `READY`。

### 2.4 Initial revision 的 Version 语义

Version API 为初始化提供专用方法，**不接收 creator 或 label**：

```kotlin
suspend fun createInitialRevision(
    projectId: ProjectId,
): OperationResult<VersionSnapshot>
```

该方法固定创建 `INITIALIZATION` / `SYSTEM` 快照。用户确认 Blank 只代表确认初始化动作，不能改变
首个 revision 的 creator。若 repository 已存在，它只接受已有的、唯一的 `INITIALIZATION` /
`SYSTEM` revision；空 history 才创建首 commit。

所有快照以 Git revision id 为 `SnapshotId`，创建时间使用 Git commit 时间。`VersionSnapshot` 表达
`id`、`projectId`、`creator`、`type`、`createdAtEpochMillis`，并只为 `RESTORATION` 表达
`restoredFromSnapshotId`。不使用 label，也不写独立的 created-at trailer。

版本实现使用下列 trailers；调用方不得通过 commit message text 传递这些语义：

```text
AVD-Snapshot-Schema: 1
AVD-Snapshot-Type: <VersionSnapshotType>
AVD-Snapshot-Creator: <VersionCreator>
AVD-Operation-Id: <UUID>
AVD-Restored-From: <SnapshotId>     # 仅 RESTORATION
```

commit subject 固定为单个空格（`" "`），不承载用户可见文本或 i18n 内容。解析时必须拒绝重复的 AVD
trailer、不支持的 schema、非线性 history、缺失或多个 initial revision，以及引用不存在快照的
`restoredFromSnapshotId`。

### 2.5 Git/workspace payload 的发布边界

Git dir 不应暴露给 Preview 或普通 workspace 浏览。当前 `ProjectGitRepositoryLocator` 的 published
布局为：

```text
projects/<projectId>/
  project.json
  initialization.json             # 仅 INITIALIZING / recovery-needed 时存在
  workspace/                      # Workbench / Preview 的唯一受控 root
  git/                            # 外置 Git dir，不属于 workspace root

projects/.staging/<projectId>/<operationId>/
  workspace/
  git/
```

Blank 的所有文件和 Git repository 都先在 staging 建立。`VersionRepository` 只接收 `ProjectId`，
并通过 `ProjectGitRepositoryLocator` 决定 repository location；因此，在 `INITIALIZING` 期间 locator
必须依据 initialization journal 解析到这次 staging 的 `workspace/` 与 `git/`，发布完成后再解析到
published 位置。不得让 Version API 接收路径，也不得在 staging 中调用一个仍指向 published 位置的
locator。

workspace 与 git directory 是两个 sibling 目录；发布不是跨资源 ACID 事务。目录移动和
`project.json` 的 `READY` 写入都必须以 journal 和恢复处理，不得把任一步骤假定为整体原子发布。

当前 CRUD 会为 `DRAFT` 创建空 `workspace/`。本期将该行为移除：Draft 只拥有 metadata/assets，
没有 published workspace。对旧开发数据中的空目录可在安全检查后清理；若发现非空旧 Draft
workspace，绝不静默删除，应进入可诊断恢复/失败路径。

---

## 3. 状态、journal 与恢复

### 3.1 状态机

```text
DRAFT
  -- user confirms Blank --> INITIALIZING
  -- delete --> DELETING --> removed

FAILED
  -- user retries Blank --> INITIALIZING
  -- delete --> DELETING --> removed

INITIALIZING
  -- staged files + initial Git revision + payload publish --> READY
  -- known pre-commit failure --> FAILED
  -- crash / uncertain I/O / uncertain Git result --> INITIALIZING + recovery-needed

READY
  -- later snapshot/edit/version operations (outside this slice)
```

重复开始初始化、对 `READY` 初始化、对 `DELETING` 初始化都必须返回稳定 `InvalidState`。操作中
不允许编辑、删除、再次选择来源或打开 Workbench。

### 3.2 Durable initialization journal

`initialization.json` 是 data 内部的恢复记录，不是 Feature UiState，也不等同 Git metadata。它至少
持久化：

- operation id；
- source kind（本期固定 `BLANK`）；
- 当前 durable phase；
- staging payload 的受控相对位置；
- 已确认的 initial revision id（若存在）；
- 可展示/映射的稳定失败 code（若已确认失败）。

建议 phase：

```text
PREPARING
STAGED
INITIAL_REVISION_CREATED(revisionId)
PAYLOAD_PUBLISHED(revisionId)
READY_PUBLISHED
```

journal 的每次 phase 更新必须在推进下一步前原子写入；不存 `Throwable`、Job、锁、绝对路径、Git
handle 或原始错误输出。

### 3.3 失败、取消与恢复规则

| 位置 | 所有可见状态与后续动作 |
| --- | --- |
| staging 文件写入前/中失败 | 清理安全的 staging 内容，记录稳定错误，转 `FAILED` |
| 初始 Git commit 明确失败且 journal 未记录 revision | 清理安全的 staging 内容，转 `FAILED` |
| Git 返回不确定结果 | 保持 `INITIALIZING`；恢复时检查 staging `HEAD`，禁止盲目重试或删除 |
| 已记录 initial revision、尚未 publish payload | 恢复时校验 revision，继续发布或明确失败 |
| payload 已 publish、尚未写 READY | 校验已发布 payload 的 `HEAD`，补写 `READY` |
| READY 已写、journal 尚未删除 | 只清理 journal；不得创建第二个初始 commit |

`CancellationException` 原样传播。若取消发生在 durable phase 之前，可安全清理并回到 `DRAFT`；一旦
有 journal 或 Git 操作不确定，保持 `INITIALIZING` 并交给恢复流程决定，不能把取消伪装为成功或
确定失败。

恢复入口为 `RecoverProjectUseCase` 或其内部的初始化恢复步骤：应用启动、列表刷新/打开项目，或用户
在初始化页选择重试时调用。恢复必须串行化同项目的文件与 Git mutation。

---

## 4. 模块与 API 变更

### 4.1 `:data:project`：Project 资源契约

`ProjectRepository` 增加初始化的资源操作。名称可随现有代码风格调整，但语义不可省略：

```kotlin
interface ProjectRepository {
    suspend fun prepareInitialization(
        projectId: ProjectId,
        content: InitialWorkspaceContent,
    ): OperationResult<Unit>

    suspend fun publishInitialization(
        projectId: ProjectId,
        initialRevisionId: SnapshotId,
    ): OperationResult<Project>

    suspend fun resolveInitializationFailure(
        projectId: ProjectId,
        error: AppError,
    ): OperationResult<Unit>
}
```

`InitialWorkspaceContent` 是资源专属、不可变、仅含受控相对路径和内容的值对象。它不是 UI input、
`File`、`Uri`、archive 或 Git path。`prepareInitialization()` 负责：

1. 校验 `DRAFT` / `FAILED` 前置状态；
2. 创建或重建本次 staging payload；
3. 写入 journal 与 `INITIALIZING` metadata；
4. 校验路径、重复路径、大小和 Blank 所需文件；
5. 原子写入 staging workspace；
6. 发布 `STAGED` phase。

`publishInitialization()` 必须使用 data 内部的 Git reader 校验 journal、payload `HEAD` 与传入的
revision id 一致，再发布 `workspace/` 与 `git/`、写 `READY` metadata 并清理 journal。它不调用
`VersionRepository`。

`resolveInitializationFailure()` 读取 journal 决定可以清理并转 `FAILED`，还是必须维持
`INITIALIZING` 等待恢复；domain 不根据一次下层失败直接删除 staging。

上述接口仅表达资源操作。模板读取、导入解压、用户选择来源和初始 revision 的业务顺序不属于
`ProjectRepository`。

### 4.2 `:data:project`：Version 资源契约

扩展既有 `VersionRepository`：

```kotlin
interface VersionRepository {
    fun observeSnapshots(projectId: ProjectId): Flow<OperationResult<List<VersionSnapshot>>>

    suspend fun createInitialRevision(projectId: ProjectId): OperationResult<VersionSnapshot>

    suspend fun createSnapshot(
        projectId: ProjectId,
        creator: VersionCreator,
        type: VersionSnapshotType,
    ): OperationResult<VersionSnapshot>

    suspend fun restore(
        projectId: ProjectId,
        snapshotId: SnapshotId,
        creator: VersionCreator,
    ): OperationResult<VersionSnapshot>
}
```

`observeSnapshots()` 必须把 history 读取、trailer 解析或 repository 不可读等失败表达为
`OperationResult.Failure`，而不是把它们伪装成空列表。

`createInitialRevision()` 在 locator 当前解析的 repository location 初始化 Git、创建首个线性 commit
并返回快照。它不读取或更新 initialization journal，不发布 workspace，也不更新 `Project.status`；
staging 与 published location 的选择由 locator 配合 Project 初始化状态完成。

`createSnapshot()` 只接受 `MANUAL` 或 `REPAIR`。`INITIALIZATION`、`PRE_RESTORE` 与
`RESTORATION` 由 repository 内部创建。`restore()` 在 worktree 有改动时先创建 `PRE_RESTORE`，然后
用目标 revision 替换 worktree 内容（不移动 `HEAD`），最后创建引用目标 snapshot 的
`RESTORATION` 快照并返回它。

Project 与 Version 的协作不构成 Repository-to-Repository 调用，也不将 layout、journal 或 lease
暴露给 domain。

### 4.3 `:domain:project`：Blank 用例和共享发布序列

新增下列 feature-facing API：

```kotlin
class InitializeBlankProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val versionRepository: VersionRepository,
) {
    suspend operator fun invoke(projectId: ProjectId): OperationResult<Project>
}
```

流程为：

```text
BlankWorkspaceDefinition
  -> projectRepository.prepareInitialization(projectId, content)
  -> versionRepository.createInitialRevision(projectId)
  -> projectRepository.publishInitialization(projectId, initialRevision.id)
```

它负责跨资源顺序、错误映射、取消传播和调用 `resolveInitializationFailure()`；不直接访问文件、Git
layout 或 journal。

`BlankWorkspaceDefinition` 初期可以是 domain 内部固定文件集。首个 Blank 的文件名和内容要先产品
确认；在确认前不得擅自生成框架、构建脚本、依赖锁文件或 README。测试可使用一个小的确定性 fixture。

当 Template 或 Import 真正加入时，抽取 domain internal 的 `ProjectInitializationPublisher`，复用
“prepare -> initial revision -> publish”序列；每个来源仍保留其独立的 use case 和 source-specific
验证。不要让 Feature 传通用 source 参数以绕过命名用例。

### 4.4 `:domain:version` 与 `:core:git`

`:domain:version` 的快照浏览、手动创建和线性恢复 API 仅面对 `READY` 项目；它不再补建缺失的
initial revision。版本列表为空的 `READY` 项目是数据不变量破坏，应触发 recovery/稳定错误，而不是
创建一个普通 snapshot 掩盖问题。

`:core:git` 提供以受控 work tree / git dir 初始化 repository、创建 commit、读取 history、检查
repository 状态，以及以某 revision 覆盖 worktree 而不移动 `HEAD` 的技术能力。它不认识项目、
初始化 source、状态、creator 或 trailer 的业务含义。Version data 层负责写入并解析业务 trailers。

### 4.5 `:feature:projects` 与 App 入口

`ProjectsViewModel` 的 `SelectProject` 改为完整状态路由：

| ProjectStatus | effect / 页面 |
| --- | --- |
| `DRAFT` | `NavigateToProjectSetup(projectId)` |
| `INITIALIZING` | `NavigateToInitializationProgress(projectId)` |
| `READY` | `NavigateToProject(projectId)` |
| `FAILED` | `NavigateToProjectSetup(projectId, retry = true)` |
| `DELETING` | 禁用选择，显示删除中 |

新增 `ProjectSetupRoute`（本期只展示 Blank 动作）与 `ProjectInitializationRoute`（恢复/进行中）。
ViewModel 在初始化执行期间显示 operation state、禁用重复提交，并在成功后发出一次
`NavigateToProject` effect。进程重启后的真相来自项目 status 和 journal 恢复结果，不来自旧 effect。

`AppRoot` 当前的 nullable `selectedProjectId` 只能表示列表/Workbench；需改为能表达 List、Setup、
Initializing 和 Workbench 的小型 sealed destination state，或接入等价导航图。返回列表必须清除当前
destination；Workbench 入口仍只接受已验证 `READY` 项目。

---

## 5. 实施顺序

1. **冻结 Blank 内容和不变量**
   - 明确最小文件集、路径限制、最大内容大小、是否允许空工作区；
   - 确认初始 revision 固定为 `INITIALIZATION` / `SYSTEM` 及 `READY` 不变量；
   - 验证：domain unit test 能构造确定性 `InitialWorkspaceContent`。

2. **建立 data layout、journal 和 lease**
   - 为 initialization journal 增加 staging location，并让 `ProjectGitRepositoryLocator` 在
     `INITIALIZING` 时解析 staging、在 `READY` 时解析 published `{workspace,git}`；
   - 移除 Draft 创建时的正式 workspace 创建；
   - 为 Project 与 Version mutation 提供同一项目的 shared lease；
   - 验证：同项目并发初始化/快照被串行化，不同项目可独立进行。

3. **实现 Project 初始化资源操作**
   - `prepareInitialization()`、`publishInitialization()`、`resolveInitializationFailure()`；
   - 实现 phase 原子写、staging 清理和启动恢复；
   - 验证：所有 crash phase 都不会产生错误的 `READY`。

4. **实现 Version 初始 revision 操作**
   - 经由已解析到 staging 的 locator 连接 `:core:git`；
   - 创建一次且仅一次初始线性 commit，记录 journal revision；
   - 验证：不存在 label 依赖；commit 固定具备 `INITIALIZATION` / `SYSTEM` 语义；重复恢复不产生第二个首 commit。

5. **实现 `InitializeBlankProjectUseCase`**
   - 注入 `ProjectRepository` 与 `VersionRepository`；
   - 只编排 Blank 文件定义、三步调用、错误/取消策略；
   - 验证：fake repository 测试调用顺序和失败分支；不向 `createInitialRevision()` 传 creator。

6. **接入 Feature 路由与 UI**
   - DRAFT/FAILED 设置页、Blank 确认、INITIALIZING 页面和 READY 自动跳转；
   - 验证：Compose test 覆盖列表点击不再静默、重复点击禁用、成功进入 Workbench、失败可重试。

7. **集成恢复与回归验证**
   - 覆盖应用重启、payload publish 中断、metadata 写入中断和 Git 不确定结果；
   - 运行模块测试、架构验证和全工程相关 lint。

---

## 6. 测试矩阵

| 层 | 场景 | 预期 |
| --- | --- | --- |
| data project | Draft 无 published workspace | `DRAFT` 不能被 Workbench 打开 |
| data project | Blank staging 文件写入失败 | `FAILED`，无 published workspace 或 git directory |
| data version | 首 commit 成功 | staging `HEAD` 为唯一初始 revision，语义为 `INITIALIZATION` + `SYSTEM` |
| data version | 首 commit 失败/不确定 | 不发布 `READY`；journal 可恢复 |
| data integration | staging 目录发布后 metadata 写失败 | 重启后校验 HEAD 并补写 `READY` |
| data integration | 已 READY journal 残留 | 清理 journal，不创建第二个 commit |
| data integration | 初始 Git revision 不匹配 | 拒绝 publish，维持可恢复状态 |
| data version | history 无法读取或 trailer 不兼容 | `observeSnapshots()` 发出稳定失败，不发出空列表 |
| data version | restore 有未提交改动 | 先创建 `PRE_RESTORE`，再返回带 `restoredFromSnapshotId` 的 `RESTORATION` |
| domain | Blank 成功顺序 | prepare → initial revision → publish |
| domain | Version 失败 | 调用 Project failure resolution；不擅自删除不确定 Git state |
| domain | cancellation | 取消原样传播，不伪造 `FAILED`/`READY` |
| feature | DRAFT 点击 | 打开 Setup，不再静默 return |
| feature | Blank 成功 | 仅在 READY 后导航 Workbench |
| feature | FAILED 点击/重试 | 进入 Setup，允许重新确认 Blank |
| feature | INITIALIZING 点击 | 显示进度/恢复信息，不重复发起初始化 |

提交前至少执行：

```bash
./gradlew \
  :data:project:testDebugUnitTest \
  :domain:project:testDebugUnitTest \
  :domain:version:testDebugUnitTest \
  :feature:projects:testDebugUnitTest \
  verifyModuleGraph \
  verifyArchitectureSources
```

若 Git adapter 需要 Android/device 或临时真实 filesystem 覆盖，补充对应 integration test；不要以
Feature/UI test 代替 filesystem、Git 或 crash recovery 验证。

---

## 7. 后续初始化方式的接入规则

新增 Template 或 Import 时必须：

1. 使用自己的命名 use case 和来源特有输入/校验；
2. 在进入 `prepareInitialization()` 前将输入转换为已验证的 `InitialWorkspaceContent`；
3. 复用相同 journal、lease、`createInitialRevision()` 与 publish 语义；
4. 不绕过 staging、直接写 active workspace，也不创建特殊的无 Git `READY` 项目；
5. 在 journal 记录 source kind，但不将 Android `Uri`、archive 路径、模板本地路径或远端 URL
   作为长期 Project metadata；
6. 为该来源新增独立的恢复、取消、安全路径和内容大小测试。

这保证“初始化来源可扩展”只增加准备阶段，不分裂 Project 状态机、版本线性历史或 Workbench 的
`READY` 前置条件。
