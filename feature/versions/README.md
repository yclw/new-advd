# :feature:versions

Owner: Android Vibe Design maintainers. Owns version history and restore UI. It may depend on core and domain only; data repositories and Git APIs are forbidden.

The planned Workbench entry opens a project-scoped Version route with `ProjectId` via `:app`.
The first UI stage adds only a clearly labeled placeholder route. Initial version prompt and
recording UI currently live in the temporary Workbench route; remove those calls when replacing
it, then design the real Version flow in a later stage. This module does not depend on Workbench
or Build. See [WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
