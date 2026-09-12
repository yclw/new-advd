# :core:git

Owner: Android Vibe Design maintainers. Owns a narrow Git adapter for repository
initialization, commits, revision inspection, and checkout. Snapshot policy, recovery,
audit, and project authorization remain in data/domain modules.

It may depend only on lower `:core:*` modules. It must not depend on data, domain,
feature, contract, agent, app, or shell modules.
