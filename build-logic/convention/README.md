# :build-logic:convention

Owner: Android Vibe Design maintainers. Provides Android, Compose, domain, Kotlin, Hilt, testing, and architecture-check convention plugins. Module build files use these plugins instead of duplicating shared toolchain configuration; application business logic is forbidden.

`verifyModuleGraph` currently permits Feature dependencies on core, domain, and
`*:api` Feature modules only. Keep the ban on dependencies between Feature implementations.
The planned Chat and Preview routes are composed with Workbench UI slots in `:app`, as described
in [WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md). When those modules are added,
add negative tests for `workbench -> chat`, `chat -> preview`, and the reverse directions;
do not add a container exception.
