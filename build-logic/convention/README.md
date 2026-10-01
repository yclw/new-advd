# :build-logic:convention

Owner: Android Vibe Design maintainers. Provides Android, Compose, domain, Kotlin, Hilt, testing, and architecture-check convention plugins. Module build files use these plugins instead of duplicating shared toolchain configuration; application business logic is forbidden.

`verifyModuleGraph` currently permits Feature dependencies on core, domain, and
`*:api` Feature modules only. The planned `:feature:workbench -> :feature:chat/:feature:preview`
container exception is documented in
[WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md) but is not implemented here yet.
When those child modules are added, update the checker and its positive and negative tests in
the same change; keep reverse and sibling Feature dependencies forbidden.
