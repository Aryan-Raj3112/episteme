// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kover) apply false
}

val test by tasks.registering {
    group = "verification"
    description = "Runs available unit tests for the included projects."
}

subprojects {
    val rootTest = rootProject.tasks.named("test")
    tasks.matching {
        it.name == "allTests" ||
            it.name == "desktopTest" ||
            it.name.endsWith("DebugUnitTest")
    }.configureEach {
        rootTest.configure {
            dependsOn(this@configureEach)
        }
    }
    tasks.withType<Test>().configureEach {
        maxHeapSize = "4g"
    }
}
