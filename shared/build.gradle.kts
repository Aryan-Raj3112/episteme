import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.plugins.ExtensionAware

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover) apply false
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.aryan.reader.shared.generated.resources"
    generateResClass = always
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
    apply(plugin = "com.android.kotlin.multiplatform.library")
} else {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

kotlin {
    if (!desktopOnlyBuild) {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            namespace = "com.aryan.reader.shared"
            compileSdk {
                version = release(36)
            }
            minSdk {
                version = release(26)
            }
            androidResources {
                enable = true
            }
            withHostTest {
                isReturnDefaultValues = true
            }
        }
        iosArm64()
        iosSimulatorArm64()
    }
    jvm("desktop")
    jvmToolchain(21)

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        val pdfiumVariant = if (name.contains("Simulator", ignoreCase = true)) {
            "ios-simulator-arm64"
        } else {
            "ios-device-arm64"
        }
        val pdfiumRoot = rootProject.layout.projectDirectory.dir("third_party/pdfium/$pdfiumVariant")

        compilations.getByName("main") {
            cinterops {
                val pdfium by creating {
                    defFile(project.file("src/nativeInterop/cinterop/pdfium.def"))
                    compilerOpts("-I${pdfiumRoot.dir("include").asFile.absolutePath}")
                }
            }
        }

        binaries.framework {
            baseName = "ReaderShared"
            binaryOption("bundleId", "com.aryan.reader.shared")
            linkerOpts("-L${pdfiumRoot.dir("lib").asFile.absolutePath}", "-lpdfium")
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting
        val desktopMain by getting
        // The phone-first Compose layer is introduced to iOS first. Android
        // remains the reference app until feature parity is established.
        val mobileMain by creating {
            dependsOn(commonMain)
        }
        val readerJvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jsoup:jsoup:1.17.2")
            }
        }
        if (!desktopOnlyBuild) {
            val androidMain by getting
            androidMain.dependsOn(readerJvmMain)
            val iosMain by creating {
                dependsOn(mobileMain)
            }
            val iosArm64Main by getting {
                dependsOn(iosMain)
            }
            val iosSimulatorArm64Main by getting {
                dependsOn(iosMain)
            }
        }
        desktopMain.dependsOn(readerJvmMain)

        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
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
