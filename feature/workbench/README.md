# :feature:workbench

Owner: Android Vibe Design maintainers. Provides the project workspace container, saveable
Chat/AI and Preview/Console selection, one content slot, and project-scoped navigation callbacks.
Chat/AI and Preview/Console stay composed for the Workbench lifetime; only the selected route is
placed on screen. `:app` supplies both through the slot. Versions and Build open as
separate destinations. The placeholder UI does not read project data or call domain use cases.
Feature implementations must not depend on one another. Data repositories, Koog/provider APIs,
Room, filesystem APIs, and runtime handles remain forbidden. See
[WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md) and
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
