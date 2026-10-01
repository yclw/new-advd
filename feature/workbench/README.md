# :feature:workbench

Owner: Android Vibe Design maintainers. Currently contains a temporary project entry route.
The target responsibility is the project-context UI container: project header, pane and selected
session IDs, cross-feature entry points, and explicit project-close confirmation. Chat/session UI
and Preview/Console UI belong to planned `:feature:chat` and `:feature:preview` modules.

The target container may depend on the public routes of those two child features only. This
exception is not yet permitted by `verifyModuleGraph`; add the modules, narrowly update that
rule and its tests, and then migrate the UI in one coherent change. Child features must not
depend on this container or each other. Data repositories, Koog/provider APIs, Room, filesystem
APIs, and runtime handles remain forbidden. See
[WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md).
