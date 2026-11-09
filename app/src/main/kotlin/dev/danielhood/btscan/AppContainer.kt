package dev.danielhood.btscan

/** Manual dependency injection root. The only place allowed to see every layer. */
class AppContainer {
    val debugOverrides: DebugOverrides = DebugOverridesImpl()
}
