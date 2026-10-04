# :app

Owner: Android Vibe Design maintainers. Owns the application entry point, root composition, feature installation, and approved adapter installation. It may depend on feature/core modules and registered binding adapters only; repositories, use cases, ViewModels, and screen logic are forbidden. The sole exception is observing `ObserveThemePreferenceUseCase` at the composition root to supply `AvdTheme`; app must not update settings or access repositories.

`:app` uses one Navigation 3 `NavDisplay` for the project list, Chat, Preview, Build, Version,
and settings. Opening a project adds `Chat(projectId)`; Chat requests the other three destinations
through callbacks. Each navigation entry passes its `ProjectId` to the Feature Route. Each Feature
Screen owns its Scaffold, TopAppBar, and page actions. Chat owns
its page menu. `:feature:workbench` remains reserved for future project-level UI. Business runtime state and
use cases remain with their domain owners. See
[WORKBENCH_ARCHITECTURE.md](../WORKBENCH_ARCHITECTURE.md).
