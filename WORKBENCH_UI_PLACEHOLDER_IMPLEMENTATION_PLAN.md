# 工作区界面占位实施方案

> 状态：已实施，工作区默认显示 Chat／AI。以 2026-10-03 的代码为实施基线。本阶段只完成工作区及项目内功能的
> Feature 层界面、占位内容和 `:app` 导航组合；AI 与非 AI 功能一律不接业务实现。
> 最终模块边界参考 [WORKBENCH_ARCHITECTURE.md](WORKBENCH_ARCHITECTURE.md) 和
> [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)，其中后续业务能力不属于本阶段。

## 1. 交付范围

从现有项目列表打开 `Workbench(projectId)`，呈现工作区界面结构：标题区、项目内导航、
内容区、返回行为，以及 Chat／AI、Preview／Console、Versions、Build
的占位页面。页面应有清楚的功能名称和“尚未接入”说明，不显示虚构的业务结果。
项目列表已有的 CRUD 和应用设置沿用现状；本方案不改写它们。

“只留在 Feature 层”指本轮新增的工作区页面逻辑仅限 Compose UI、页面内选择状态、
资源文案和导航回调。`:app` 只安装 destination、连接 Workbench 内容插槽与子
Feature Route，并传递 `ProjectId`。不为占位页面新增或调用 domain use case、
Repository、data adapter、Runtime、WebView 后端、构建服务或持久化状态。

原有 Workbench 临时页面会调用初始版本用例。本阶段用新容器替换它，并移除该页面的
版本弹窗、`WorkbenchVersionState` 及版本用例调用；不把调用迁到 Versions 占位页。
原有 domain/data 实现留待真正接入时使用，本轮不扩展或重构它们。

## 2. 界面结构与模块归属

```text
Project list（现有功能）
  -> Workbench(projectId)
       ├─ Chat／AI（内嵌 Feature 占位）
       ├─ Preview／Console（内嵌 Feature 占位）
       ├─ Versions(projectId)（独立 destination，占位）
       ├─ Build(projectId)（独立 destination，占位）
       └─ App settings（现有 destination）
```

| 模块 | 本阶段实现 | 本阶段不实现 |
| --- | --- | --- |
| `:feature:workbench` | 容器、区域选择、项目内入口、单一内容插槽、返回 UI | 项目资料读取、版本状态、Session 选择、运行资源关闭 |
| `:feature:chat`（新增） | 不含输入框的 Chat／AI 占位 Route | 消息、会话、Agent 状态、发送和持久化 |
| `:feature:preview`（新增） | Preview 与 Console 的静态占位 Route | WebView、预览服务、日志采集、启动／停止 |
| `:feature:versions` | 独立 Version 占位 Route | 初始版本记录、历史、创建、恢复 |
| `:feature:build` | 独立 Build 占位 Route | 构建配置、进度、产物、导出 |
| `:app` | 安装 Route，连接一个内嵌内容插槽及两个项目级 destination | 业务状态、用例调用、工作区布局 |

Workbench 接收稳定的 `ProjectId`，但本阶段不读取项目详情。标题用中性的“工作区”
文案，不把 ID 当作项目名称，也不伪造名称、描述、版本、预览或构建结果。
默认显示 Chat／AI 占位。项目不存在／读取失败等业务状态
留到项目读取接入时处理；本轮仅处理导航参数缺失或格式不合法等纯路由错误。

Chat／AI 与 Preview／Console 是内嵌区域，由各自 Feature 提供最小公开 Route；
Workbench 通过**一个**内容插槽同时组合两个 Route，只放置 `WorkbenchSection` 选中的
界面；切换不卸载另一个 Route。
Versions 与 Build 由 Workbench 发出导航意图，`:app` 以同一 `ProjectId` 打开独立
destination。Feature 之间没有实现依赖，也不共享 ViewModel。

区域选择使用 `rememberSaveable` 或等价的纯 UI 恢复机制。系统返回从工作区直接回
项目列表；从 Versions／Build 返回只弹出当前页面，
Workbench、Chat 和 Preview 保持组合并保留区域选择。应用设置及其子页同样覆盖在
Workbench 上方，不卸载内嵌界面。返回不触发项目资源关闭。

## 3. 占位页约束

- 每页明确标注“界面占位／功能尚未接入”，并使用已有设计系统、双语字符串与无障碍标签。
- 可以展示静态布局示意，但示意内容须可辨为示例；不展示假成功记录、假日志、
  假会话、假构建产物或会变化的假进度。
- 不提供看似可执行的“发送”“启动预览”“恢复版本”“开始构建”等按钮。
  如需保留操作位置，只使用禁用状态并说明尚未接入。
- 不因为未来可能需要某个状态而创建 Session ID、Agent Job、Preview handle、
  Build task、草稿交接协议或持久化模型。
- 本阶段的新 Feature 模块不依赖 `:domain:*` 或 `:data:*`；Workbench 现有的
  版本／Agent／Preview 依赖应在临时页面替换后移除。稳定 `ProjectId` 类型和
  通用 UI 模块可以继续使用。

## 4. 实施顺序

| 步骤 | 具体改动 | 完成标准 |
| --- | --- | --- |
| A. 容器 | 替换临时 Workbench 页面；移除版本弹窗与用例调用；加入标题、导航、内容插槽和纯 UI 区域状态 | 从项目列表能进入；切换区域和返回正常；工作区不触发版本操作 |
| B. 内嵌占位 | 创建 `:feature:chat`、`:feature:preview` 的最小 Route、资源与模块注册；由 `:app` 接入同一插槽 | 两个区域能切换；没有 Session／Agent／Preview 后端调用 |
| C. 项目级占位 | 在 `:feature:versions`、`:feature:build` 增加占位 Route；在 `:app` 安装 destination | 带同一 `ProjectId` 进入、返回；不创建版本或构建结果 |
| D. 收尾 | 更新模块 README、文案与界面适配；检查依赖图 | Feature 间零实现依赖；占位状态表达一致；窄屏和大字体可用 |

这些步骤只为界面骨架提供可 review 的增量。实际功能接入时另写各功能的实施方案，
再决定 domain/data 契约、生命周期和失败恢复；不能把占位验收当作业务功能完成。

## 5. 最低验证矩阵

| 场景 | 预期 |
| --- | --- |
| 从已有项目列表进入工作区 | `ProjectId` 正确传递；显示中性标题和明确占位内容 |
| Chat／AI、Preview／Console 之间切换并旋转屏幕 | 两个 Route 保持组合，区域选择恢复；没有业务调用或虚构状态 |
| 从任一内嵌区域进入 Versions／Build／应用设置后返回 | Workbench、Chat、Preview 不退出组合；回到原区域 |
| 系统返回与应用内返回 | 行为一致；不会调用项目资源关闭或功能动作 |
| 窄屏、大字体、中文与英文 | 导航和占位说明可读、可访问 |
| 模块边界 | `verifyModuleGraph` 拒绝 Feature 实现互依；新占位 Feature 没有 domain/data 依赖 |

执行 `./gradlew verifyModuleGraph verifyArchitectureSources` 及受影响模块的编译与
适用 UI 测试。检查最终 UI 截图或设备运行效果；测试只验证导航、状态恢复和占位
表达，不以占位页面证明 Version、Preview、Build 或 AI 功能已经可用。

## 6. 后续接入边界

项目资料、Versions、Preview／Console、Build、Chat／Session／Agent 的真实能力
分别在后续阶段设计与接入。接入时保留 `:app` 组合、稳定 `ProjectId` 导航、
Feature 间零实现依赖，以及工作区 UI 不直接拥有业务资源的边界。
Session 与 Agent 生命周期、Build 产物契约、Preview 后端及项目资料读取方式
目前均不由占位界面预先决定。
