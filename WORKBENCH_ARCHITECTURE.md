# 项目工作区 UI 架构

当前工作区只提供一个占位界面。版本操作、构建、Chat、Preview、WebView 和流式任务
尚未接入；这些功能后续都在 Workbench 中呈现，具体 UI 与生命周期方案在功能设计时确定。
通用 Feature 规则见 [FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)，运行资源规则见
[PROJECT_RUNTIME_ARCHITECTURE.md](PROJECT_RUNTIME_ARCHITECTURE.md)。

## 模块边界

| 模块 | 当前职责 | 不拥有 |
| --- | --- | --- |
| `:feature:workbench` | 工作区占位内容、返回请求；后续承载版本和构建等项目功能的 UI | 版本和构建执行、聊天和预览运行资源 |
| `:app` | 单个 Navigation 3 返回栈、Route 安装、稳定 ID 和导航回调 | 业务状态与工作区布局 |

`:app` 从项目列表以稳定的 `ProjectId` 打开 Workbench。返回时弹出 Workbench，系统返回
与页面返回按钮遵循相同的栈顺序。应用设置仍可从项目列表进入。

当前页面不读取项目详情，不创建版本、构建任务或其他业务对象。标题使用中性文案；
导航参数只包含稳定 ID。Feature 之间没有实现依赖，也不直接访问其他 Feature 的
ViewModel。后续功能接入时，在 Workbench 中设计界面，并由 domain/data 管理长任务和
可恢复状态；占位页不预设插槽或保活机制。

## 验证

运行 `verifyModuleGraph`、`verifyArchitectureSources` 和相关 UI 测试。设备上验证
项目列表 → Workbench 的进入、返回和默认 Navigation 3 转场；占位页面不证明真实
版本或构建能力可用。
