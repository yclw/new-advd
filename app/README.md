# :app

Owner: Android Vibe Design maintainers. Owns the application entry point, root composition, feature installation, and approved adapter installation. It may depend on feature/core modules and registered binding adapters only; repositories, use cases, ViewModels, and screen logic are forbidden. The sole exception is observing `ObserveThemePreferenceUseCase` at the composition root to supply `AvdTheme`; app must not update settings or access repositories.

`:app` supplies Chat and Preview routes to one public Workbench content slot. Workbench keeps both
routes composed while it is present and places only the selected route. Version and Build
open as separate project-scoped destinations from Workbench callbacks. A Navigation 3 scene keeps
the Workbench entry composed beneath Versions, Build, and settings destinations, including settings
subpages. `:app` connects stable IDs and callbacks only; pane selection,
layout, drafts, runtime state, and use cases remain with their owning Feature or domain modules. See
[WORKBENCH_ARCHITECTURE.md](../WORKBENCH_ARCHITECTURE.md).
