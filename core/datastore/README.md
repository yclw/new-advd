# :core:datastore

Owner: Android Vibe Design maintainers. Owns DataStore creation, serialization, and
generic storage primitives. Preference meaning, keys tied to a business resource, and
Repository contracts remain in the owning data module.

It may depend only on lower `:core:*` modules. It must not depend on data, domain,
feature, contract, agent, app, or shell modules.
