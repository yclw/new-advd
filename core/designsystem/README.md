# :core:designsystem

Owner: Android Vibe Design maintainers. Contains application-wide Material theme
foundations, visual tokens, and reusable visual components. It may depend only on core;
screen-specific behavior, ViewModels, business data, and user-facing feature strings are
forbidden. `AvdTheme` is the sole application theme entry point.

Static Compose color schemes are transcribed from the three Material Theme
Builder Compose exports in the workspace's `material-theme (1..3)` directories.
Replace a palette from its export as a complete set, never by changing isolated
roles.
