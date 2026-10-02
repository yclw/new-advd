# :feature:workbench

Owner: Android Vibe Design maintainers. Currently contains a temporary project entry route
with initial version state and recording UI.
The target responsibility is the project-context UI container: project header, selected
section, a single content slot, project-scoped navigation actions, and explicit project-close confirmation.
The first stage shows placeholders for both AI and non-AI functions, with no session selection
or domain calls from the new workbench UI. Preview/Console and Chat/AI placeholders belong to
their respective Feature modules; their business behavior awaits later designs.

The container exposes one content slot for the embedded Chat/AI and Preview/Console sections. Version and Build
open as independent project-scoped destinations through `:app`; their state and workflows belong
to `:feature:versions` and `:feature:build`. Remove the current initial version prompt and its
use case calls when replacing the temporary route; do not move behavior into the placeholder.
`:app` connects stable parameters and callbacks. Feature implementations
must not depend on one another; preserve that rule in `verifyModuleGraph` and add negative tests
when the child modules are created. Data repositories, Koog/provider APIs, Room, filesystem
APIs, and runtime handles remain forbidden. See
[WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md) and
[WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md](../../WORKBENCH_UI_PLACEHOLDER_IMPLEMENTATION_PLAN.md).
