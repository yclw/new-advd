# :app

Owner: Android Vibe Design maintainers. Owns the application entry point, root composition, feature installation, and approved adapter installation. It may depend on feature/core modules and registered binding adapters only; repositories, use cases, ViewModels, and screen logic are forbidden. The sole exception is observing `ObserveThemePreferenceUseCase` at the composition root to supply `AvdTheme`; app must not update settings or access repositories.

For the planned workbench split, `:app` will supply Chat and Preview routes to one public
Workbench content slot. Version and Build will open as separate project-scoped destinations
from Workbench entry callbacks. `:app` connects stable IDs and callbacks only; pane selection,
layout, drafts, runtime state, and use cases remain with their owning Feature or domain modules. See
[WORKBENCH_ARCHITECTURE.md](../WORKBENCH_ARCHITECTURE.md).
