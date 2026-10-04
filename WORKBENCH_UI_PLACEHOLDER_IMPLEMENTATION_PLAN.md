# 工作区界面占位实施方案

> 状态：历史方案；当前四个界面占位结构见 [WORKBENCH_ARCHITECTURE.md](WORKBENCH_ARCHITECTURE.md)。

## 范围

```text
项目列表
  └─ Workbench(projectId)
```

Workbench 显示中性标题、占位说明和返回按钮，不显示虚构的版本记录、构建结果或进度。
项目列表现有的 CRUD 与应用设置保持独立。

`:app` 用一个 Navigation 3 `NavDisplay` 安装 Workbench，导航 key 保存稳定的 `ProjectId`，
并传给 `WorkbenchRoute`。
`:feature:workbench` 不依赖其他 Feature 的实现，不读取项目资料，也不调用 domain use
case。版本操作与构建任务后续在 Workbench 中呈现，执行与恢复仍由相应 domain/data
owner 管理。

Chat／AI、Preview／Console、WebView、流式任务、内嵌区域选择和保活策略不属于当前实现。
后续接入时按实际需求另行设计。

## 验收

| 场景 | 预期 |
| --- | --- |
| 从项目列表进入 Workbench | 显示工作区占位内容，导航 key 保存项目 ID |
| 从 Workbench 返回 | 系统返回与页面返回按钮均可回到项目列表 |
| 从项目列表进入设置及其子页 | 保持原有导航行为 |
| 模块边界 | Feature 间零实现依赖，占位页不调用业务资源 |

执行 `./gradlew verifyModuleGraph verifyArchitectureSources`、受影响模块的编译、格式检查
与 UI 测试，并在设备上检查默认导航转场。
