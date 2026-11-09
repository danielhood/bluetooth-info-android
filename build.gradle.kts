plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(file("config/detekt/detekt.yml"))
    parallel = true
    source.setFrom(
        "app/src/main/kotlin",
        "app/src/debug/kotlin",
        "app/src/release/kotlin",
        "app/src/test/kotlin",
        "app/src/androidTest/kotlin",
    )
}

// detekt 1.23.x ships a fixed Kotlin compiler; stop Gradle upgrading it to the project's Kotlin version.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.detektKotlin.get())
        }
    }
}
