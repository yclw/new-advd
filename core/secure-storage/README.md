# :core:secure-storage

Owner: Android Vibe Design maintainers. Owns Android Keystore-backed encryption and
secure byte/string storage primitives. Provider configuration, secret identity, and
validation remain in `:data:ai-config`.

It may depend only on lower `:core:*` modules. It must not depend on data, domain,
feature, contract, agent, app, or shell modules.
