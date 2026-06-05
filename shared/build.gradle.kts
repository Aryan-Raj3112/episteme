import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
}

fun isDesktopOnlyBuild(): Boolean {
    providers.gradleProperty("desktopOnly").orNull
        ?.let { return it.equals("true", ignoreCase = true) }

    val requestedTasks = gradle.startParameter.taskNames
    return requestedTasks.isNotEmpty() && requestedTasks.all { taskName ->
        val normalized = taskName.removePrefix(":")
        normalized.startsWith("desktopApp:")
    }
}

val desktopOnlyBuild = isDesktopOnlyBuild()

if (!desktopOnlyBuild) {
    apply(plugin = "com.android.library")
}

kotlin {
    if (!desktopOnlyBuild) {
        androidTarget()
    }
    jvm("desktop")
    jvmToolchain(21)

    sourceSets {
        val commonMain by getting
        val desktopMain by getting
        val readerJvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jsoup:jsoup:1.17.2")
            }
        }
        if (!desktopOnlyBuild) {
            val androidMain by getting
            androidMain.dependsOn(readerJvmMain)
        }
        desktopMain.dependsOn(readerJvmMain)

        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
            implementation("com.materialkolor:material-kolor:5.0.0-alpha07")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

if (!desktopOnlyBuild) {
    extensions.configure<LibraryExtension>("android") {
        namespace = "com.aryan.reader.shared"
        compileSdk = 36

        defaultConfig {
            minSdk = 26
        }

        buildFeatures {
            buildConfig = true
        }
    }
}
