# :feature:workbench

Owner: Android Vibe Design maintainers. Provides the project workspace placeholder and back
navigation callback. The placeholder UI does not read project data or call domain use cases.
Version operations and builds will be presented within Workbench; their execution and recovery
belong to the relevant domain and data owners.
Feature implementations must not depend on one another. Data repositories, Koog/provider APIs,
Room, filesystem APIs, and runtime handles remain forbidden. See
[WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md) and
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
