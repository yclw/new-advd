# :feature:build

Owner: Android Vibe Design maintainers. Owns build-related UI. It may depend on core and domain only; data repositories and build-tool implementations are forbidden.

The planned Workbench entry opens a project-scoped Build route with `ProjectId` via `:app`.
The first UI stage adds only a clearly labeled placeholder route; it does not show simulated
progress or products. Build progress, results, and user actions belong here when a real build
contract is designed. This module does not depend on Workbench or Version. See
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
