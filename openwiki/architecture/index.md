# Files

- [App Shell, Navigation, and Cross-Module Wiring](app-shell-navigation.md) - How the :app shell wires AppRoot/AppNavHost Route+State+Actions pages, the AppRoutes table, Activity-scoped ViewModel assembly, permission coordination, theming, ToastHost attribution, and the CompositionLocal emotion-correction controller across feature modules.
- [Data Persistence: Room and DataStore](data-persistence.md)
- [Module Layering and Dependency Boundaries](module-graph.md) - How the app is layered across :app, :feature:*, :domain, :data, :player and :core:*, which dependency directions are legal, where Hilt binds :data/:player implementations to :domain interfaces and playback Ports, and how the ~530-line verifyProductArchitecture Gradle task turns those boundaries into hard build gates.
