# :data:project

Owner: Android Vibe Design maintainers. Owns the project aggregate: project metadata,
workspace files, import/export, version snapshots, and Preview logs. Public contracts are grouped by
resource under `project`, `workspace`, `version`, and `preview.log`; physical directory layout stays
an `internal` type at the aggregate root. Filesystem, Git, journals, Hilt bindings, and tool implementations
never depend on the Koog runtime.
