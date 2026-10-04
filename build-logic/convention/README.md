# :build-logic:convention

Owner: Android Vibe Design maintainers. Provides Android, Compose, domain, Kotlin, Hilt, testing, and architecture-check convention plugins. Module build files use these plugins instead of duplicating shared toolchain configuration; application business logic is forbidden.

`verifyModuleGraph` currently permits Feature dependencies on core, domain, and
`*:api` Feature modules only. Keep the ban on dependencies between Feature implementations.
Chat, Preview, Build, and Version Routes are rendered directly by `:app`; each Screen owns its
Scaffold and TopAppBar, and Chat owns its page menu and requests navigation through callbacks,
as described in [WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md). Keep negative tests
for Feature-to-Feature implementation dependencies; do not add a container exception.
