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

    // ---- Phase 2 guardrails (docs/phase2/policy-and-guardrails.md section 3) ----

    /** Every Kotlin file under app/src, except this test (which names the forbidden tokens). */
    private val allSourcesIncludingTests: List<File> =
        File("src")
            .walkTopDown()
            .filter { f -> f.isFile && f.extension == "kt" && f.name != "ArchitectureTest.kt" }
            .toList()

    private fun offendersContaining(files: List<File>, tokens: List<String>): List<String> =
        files.flatMap { f ->
            val text = f.readText()
            tokens.filter { it in text }.map { "${f.path}: $it" }
        }

    @Test
    fun `the app never writes a characteristic`() {
        assertNone(
            "policy: read-only; characteristic writes are forbidden",
            offendersContaining(allSourcesIncludingTests, listOf("writeCharacteristic")),
        )
    }

    @Test
    fun `the app never creates or changes a bond`() {
        assertNone(
            "policy: no pairing, ever",
            offendersContaining(
                allSourcesIncludingTests,
                listOf("createBond", "removeBond", "setPin", "setPairingConfirmation", "cancelBondProcess"),
            ),
        )
    }

    @Test
    fun `no reflection into bluetooth classes`() {
        val bluetoothFiles =
            allSourcesIncludingTests.filter { f ->
                f.relative().startsWith("data/ble/") || "import android.bluetooth" in f.readText()
            }
        assertNone(
            "policy: only public Bluetooth APIs, no reflection",
            offendersContaining(bluetoothFiles, listOf("getDeclaredMethod", "getMethod(", "Class.forName")),
        )
    }

    @Test
    fun `main code uses no network classes`() {
        assertNone(
            "policy: nothing is sent anywhere",
            violations(sources("main", "release")) {
                it.startsWith("java.net.") ||
                    it.startsWith("javax.net.") ||
                    it.startsWith("okhttp3.") ||
                    it.startsWith("retrofit2.") ||
                    (it.startsWith("android.net.") && it != "android.net.Uri")
            },
        )
    }

    @Test
    fun `manifest requests exactly the allowed permissions`() {
        val allowed =
            setOf(
                "BLUETOOTH_SCAN",
                "BLUETOOTH",
                "BLUETOOTH_ADMIN",
                "ACCESS_FINE_LOCATION",
            )
        val manifests = File("src").walkTopDown().filter { it.name == "AndroidManifest.xml" }.toList()
        assertTrue("expected the main manifest, found: $manifests", manifests.any { it.path.contains("src/main/") })
        val requested =
            manifests
                .flatMap { m ->
                    Regex("""<uses-permission(?:-sdk-23)?\s[^>]*?android:name="android\.permission\.([A-Z_]+)"""")
                        .findAll(m.readText())
                        .map { it.groupValues[1] }
                        .toList()
                }.toSet()
        assertTrue(
            "permission set changed without updating the policy: requested=$requested allowed=$allowed. " +
                "Update docs/phase2/policy-and-guardrails.md and this list in the same commit.",
            requested == allowed,
        )
    }

    @Test
    fun `legacy and scan permissions keep their restrictions`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val entries =
            Regex("""<uses-permission\s[^>]*?/>""", RegexOption.DOT_MATCHES_ALL)
                .findAll(manifest)
                .map {
                    it.value
                }.toList()

        fun entry(name: String) = entries.single { "android.permission.$name\"" in it }
        assertTrue("scan must stay neverForLocation", "neverForLocation" in entry("BLUETOOTH_SCAN"))
        listOf("BLUETOOTH", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION").forEach {
            assertTrue("$it must be limited to API 30 and below", "android:maxSdkVersion=\"30\"" in entry(it))
        }
    }
}
