# :app

Owner: Android Vibe Design maintainers. Owns the application entry point, root composition, feature installation, and approved adapter installation. It may depend on feature/core modules and registered binding adapters only; repositories, use cases, ViewModels, and screen logic are forbidden. The sole exception is observing `ObserveThemePreferenceUseCase` at the composition root to supply `AvdTheme`; app must not update settings or access repositories.

`:app` uses one Navigation 3 `NavDisplay` for the project list, Workbench, and settings.
Workbench currently contains only a placeholder and a back callback. `:app` connects stable
IDs and callbacks only; layout, runtime state,
and use cases remain with their owning Feature or domain modules. See
[WORKBENCH_ARCHITECTURE.md](../WORKBENCH_ARCHITECTURE.md).
