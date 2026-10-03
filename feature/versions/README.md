# :feature:versions

Owner: Android Vibe Design maintainers. Owns version history and restore UI. It may depend on core and domain only; data repositories and Git APIs are forbidden.

Workbench opens the project-scoped `VersionsRoute` with `ProjectId` through `:app`. The current
route is a clearly labeled UI placeholder with no version use case calls. This module does not
depend on Workbench or Build. See
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
