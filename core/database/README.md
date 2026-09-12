# :core:database

Owner: Android Vibe Design maintainers. Owns Room database setup, entities, DAOs, and
migrations when persistent relational storage is introduced. It does not define
Repository contracts or business operations; data modules map its storage models to
their public resource models.

It may depend only on lower `:core:*` modules. It must not depend on data, domain,
feature, contract, agent, app, or shell modules.
