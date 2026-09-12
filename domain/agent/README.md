# :domain:agent

Owner: Android Vibe Design maintainers. Owns the business workflow and process-scoped supervision of an Agent turn:
authorization, recovery policy, snapshots, UI-independent runtime state, and cancellation. It calls
`AgentRuntime` through `:contract:agent`. Preview, WebView, current Session selection, and project-close sequencing
are outside this module. See [PROJECT_RUNTIME_ARCHITECTURE.md](../../PROJECT_RUNTIME_ARCHITECTURE.md).
