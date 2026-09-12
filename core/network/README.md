# :core:network

Owner: Android Vibe Design maintainers. Owns transport clients, request/response DTOs,
and authentication transport primitives when a remote source is introduced. It does
not define template, project, session, or AI configuration Repository contracts.

It may depend only on lower `:core:*` modules. It must not depend on data, domain,
feature, contract, agent, app, or shell modules.
