# :core:filesystem

Owner: Android Vibe Design maintainers. Owns controlled filesystem primitives and the Android app-private root provider:
validated relative paths, atomic writes, directory creation, listing, and file locks.
It does not know projects, workspace layout, import/export, or version semantics.

It may depend only on lower `:core:*` modules. It must not depend on data, domain,
feature, contract, agent, app, or shell modules.
