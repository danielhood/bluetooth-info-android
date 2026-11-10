package dev.danielhood.btscan.architecture

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enforces docs/architecture.md layer rules by scanning source imports (no extra dependency).
 * Working directory during unit tests is the `app` module directory.
 */
class ArchitectureTest {
    private val base = "dev/danielhood/btscan"
    private val pkg = "dev.danielhood.btscan"

    private fun sources(vararg sets: String): List<File> =
        sets
            .map { File("src/$it/kotlin") }
            .filter { it.exists() }
            .flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }

    private fun imports(file: File): List<String> =
        file
            .readLines()
            .map { it.trim() }
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ") }

    private fun File.relative(): String = path.substringAfter("$base/", missingDelimiterValue = path)

    private fun inLayer(layer: String) = { f: File -> f.relative().startsWith("$layer/") }

    private fun violations(files: List<File>, forbidden: (String) -> Boolean): List<String> =
        files.flatMap { f -> imports(f).filter(forbidden).map { "${f.path}: import $it" } }

    private fun assertNone(description: String, found: List<String>) {
        assertTrue("$description:\n${found.joinToString("\n")}", found.isEmpty())
    }

    private val allMainDebugRelease = sources("main", "debug", "release")

    @Test
    fun `scanner finds the source files so the rules are not vacuous`() {
        assertTrue(sources("main").size >= 10)
        assertTrue(sources("main").any { inLayer("domain")(it) })
        assertTrue(sources("main").any { inLayer("ui")(it) })
    }

    @Test
    fun `domain imports no android classes`() {
        assertNone(
            "domain must be plain Kotlin/JVM",
            violations(sources("main", "debug").filter(inLayer("domain"))) { it.startsWith("android.") },
        )
    }

    @Test
    fun `domain does not depend on ui or data`() {
        assertNone(
            "domain must not import ui or data",
            violations(sources("main").filter(inLayer("domain"))) {
                it.startsWith("$pkg.ui.") || it.startsWith("$pkg.data.")
            },
        )
    }

    @Test
    fun `ui does not import data`() {
        assertNone(
            "ui must go through domain ports",
            violations(sources("main").filter(inLayer("ui"))) { it.startsWith("$pkg.data.") },
        )
    }

    @Test
    fun `android bluetooth classes are imported only under data ble android`() {
        val offenders = allMainDebugRelease.filter { !it.relative().startsWith("data/ble/android/") }
        assertNone(
            "android.bluetooth.* is only allowed in data/ble/android",
            violations(offenders) { it.startsWith("android.bluetooth") },
        )
    }

    @Test
    fun `view models do not use context or dispatchers directly`() {
        val viewModels = allMainDebugRelease.filter { it.name.endsWith("ViewModel.kt") }
        assertNone(
            "ViewModels take no Context and pick no Dispatchers",
            violations(
                viewModels,
            ) { it == "android.content.Context" || it.startsWith("kotlinx.coroutines.Dispatchers") },
        )
    }

    @Test
    fun `domain does not pick dispatchers or read the system clock`() {
        val domain = sources("main").filter(inLayer("domain"))
        val offenders =
            domain.filter { f ->
                val text = f.readText()
                "Dispatchers." in text || "Instant.now()" in text || "System.currentTimeMillis" in text
            }
        assertNone("time and dispatchers are injected", offenders.map { it.path })
    }

    @Test
    fun `main code never references the fake scanner package`() {
        assertNone(
            "fakes live in src/debug and are reached only through DebugOverrides",
            violations(sources("main", "release")) { it.startsWith("$pkg.data.ble.fake") },
        )
    }

    @Test
    fun `nothing connects to a device`() {
        val offenders =
            allMainDebugRelease.filter { f ->
                val text = f.readText()
                "connectGatt" in text || "BLUETOOTH_CONNECT" in text
            }
        assertNone("the app must never connect or request BLUETOOTH_CONNECT", offenders.map { it.path })
    }

    @Test
    fun `manifest requests neither internet nor the connect permission`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val requested =
            Regex("""android:name="android\.permission\.([A-Z_]+)"""")
                .findAll(manifest)
                .map {
                    it.groupValues[1]
                }.toSet()
        assertTrue("manifest should declare permissions: $requested", "BLUETOOTH_SCAN" in requested)
        assertTrue(
            "no network and no connect permission, found: $requested",
            requested.none {
                it == "INTERNET" || it == "BLUETOOTH_CONNECT" || it == "ACCESS_NETWORK_STATE"
            },
        )
    }
}
