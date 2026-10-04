# :feature:workbench

Owner: Android Vibe Design maintainers. This module is reserved for future project-level UI
that does not belong to Chat, Preview, Build, or Version, such as a Workbench-level WebView.
It currently owns no screen or navigation menu. `:app` registers the four Feature Routes
directly with Navigation 3; Chat owns the page menu and requests navigation through callbacks.

The module does not read project data or call domain use cases. Future long-running work and
recovery remain with domain and data owners. See
[WORKBENCH_ARCHITECTURE.md](../../WORKBENCH_ARCHITECTURE.md).
