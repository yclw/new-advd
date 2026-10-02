# Android Vibe Design

This repository contains the Android Vibe Design modular architecture. The current app has
Project CRUD and initialization UI; the workbench route is still a placeholder. Architecture
documents describe both enforced boundaries and planned work, and label the difference.

The architecture map is [ARCHITECTURE.md](ARCHITECTURE.md). The project workbench module
plan and migration checklist are in [WORKBENCH_ARCHITECTURE.md](WORKBENCH_ARCHITECTURE.md).
The first implementation phase, with AI and non-AI features kept as UI placeholders, is in
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
The project runtime lifecycle and recovery target is in
[PROJECT_RUNTIME_ARCHITECTURE.md](PROJECT_RUNTIME_ARCHITECTURE.md).

Repository 与底层存储的具体分层规则见
[DATA_AND_STORAGE_ARCHITECTURE.md](DATA_AND_STORAGE_ARCHITECTURE.md)。
Domain use case 与业务工作流规范见
[DOMAIN_ARCHITECTURE.md](DOMAIN_ARCHITECTURE.md)。
Feature UI、状态与交互规范见
[FEATURE_ARCHITECTURE.md](FEATURE_ARCHITECTURE.md)。
Agent runtime、工具与依赖规范见
[AGENT_ARCHITECTURE.md](AGENT_ARCHITECTURE.md)。

Run the architectural verification with:

```shell
./gradlew verifyModuleGraph verifyArchitectureSources
```

Run the full local verification with:

```shell
./gradlew check
```

The project is intentionally a framework. Implement a vertical slice only after its ownership and allowed dependencies are documented in the corresponding module README.
