# :feature:build

Owner: Android Vibe Design maintainers. Owns build-related UI. It may depend on core and domain only; data repositories and build-tool implementations are forbidden.

Workbench opens the project-scoped `BuildRoute` with `ProjectId` through `:app`. The current
route is a clearly labeled UI placeholder without simulated progress or products. Build progress,
results, and user actions belong here when a real build contract is designed. This module does
not depend on Workbench or Version. See
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
