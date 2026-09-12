# Android Vibe Design

This repository contains the Android Vibe Design modular architecture.

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
./gradlew verifyModuleGraph
```

Run the full local verification with:

```shell
./gradlew check
```

The project is intentionally a framework. Implement a vertical slice only after its ownership and allowed dependencies are documented in the corresponding module README.
