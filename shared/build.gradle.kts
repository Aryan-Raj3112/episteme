import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Exec

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
        val mobiSdk = if (name.contains("Simulator", ignoreCase = true)) "iphonesimulator" else "iphoneos"
        val mobiRoot = layout.buildDirectory.dir("native/libmobi/$name")
        val libarchiveRoot = layout.buildDirectory.dir("native/libarchive/$name")
        val buildMobiTask = tasks.register<Exec>("buildMobi${name.replaceFirstChar(Char::uppercaseChar)}") {
            val outputDirectory = mobiRoot.get().asFile
            inputs.files(
                rootProject.fileTree("app/src/main/cpp/libmobi/src") {
                    include("*.c", "*.h")
                },
                project.file("src/nativeInterop/cinterop/mobi_reader_bridge.c"),
                project.file("src/nativeInterop/cinterop/mobi_reader_bridge.h")
            )
            inputs.file(rootProject.file("scripts/build_ios_libmobi.sh"))
            outputs.file(outputDirectory.resolve("libmobi.dylib"))
            commandLine(
                "sh",
                rootProject.file("scripts/build_ios_libmobi.sh").absolutePath,
                mobiSdk,
                "arm64",
                outputDirectory.absolutePath
            )
            environment("DEVELOPER_DIR", "/Applications/Xcode.app/Contents/Developer")
        }
        val buildLibarchiveTask = tasks.register<Exec>("buildLibarchive${name.replaceFirstChar(Char::uppercaseChar)}") {
            val outputDirectory = libarchiveRoot.get().asFile
            inputs.files(
                rootProject.fileTree("third_party/libarchive"),
                rootProject.fileTree("third_party/xz")
            )
            inputs.file(rootProject.file("scripts/build_ios_libarchive.sh"))
            outputs.file(outputDirectory.resolve("libreaderarchive.a"))
            commandLine(
                "sh",
                rootProject.file("scripts/build_ios_libarchive.sh").absolutePath,
                mobiSdk,
                "arm64",
                outputDirectory.absolutePath
            )
            environment("DEVELOPER_DIR", "/Applications/Xcode.app/Contents/Developer")
        }

        compilations.getByName("main") {
            cinterops {
                val pdfium by creating {
                    defFile(project.file("src/nativeInterop/cinterop/pdfium.def"))
                    compilerOpts("-I${pdfiumRoot.dir("include").asFile.absolutePath}")
                }
                val mobi by creating {
                    defFile(project.file("src/nativeInterop/cinterop/mobi.def"))
                    compilerOpts(
                        "-I${rootProject.file("app/src/main/cpp/libmobi/src").absolutePath}",
                        "-I${project.file("src/nativeInterop/cinterop").absolutePath}"
                    )
                    tasks.named(interopProcessingTaskName).configure {
                        dependsOn(buildMobiTask)
                    }
                }
                val libarchive by creating {
                    defFile(project.file("src/nativeInterop/cinterop/libarchive.def"))
                    compilerOpts("-I${rootProject.file("third_party/libarchive/libarchive").absolutePath}")
                    extraOpts(
                        "-libraryPath", libarchiveRoot.get().asFile.absolutePath,
                        "-staticLibrary", "libreaderarchive.a"
                    )
                    tasks.named(interopProcessingTaskName).configure {
                        dependsOn(buildLibarchiveTask)
                    }
                }
            }
        }

        binaries.framework {
            baseName = "ReaderShared"
            binaryOption("bundleId", "com.aryan.reader.shared")
            linkerOpts(
                "-L${pdfiumRoot.dir("lib").asFile.absolutePath}",
                "-lpdfium",
                "-L${mobiRoot.get().asFile.absolutePath}",
                "-lmobi"
            )
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting
        val desktopMain by getting
        // Shared phone/tablet UI. Android remains the behavioral and visual
        // reference while ownership moves here incrementally.
        val mobileMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        val readerJvmMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jsoup:jsoup:1.17.2")
            }
        }
        if (!desktopOnlyBuild) {
            val androidMain by getting {
                dependencies {
                    implementation(libs.androidx.core.ktx)
                    implementation("io.coil-kt:coil:2.7.0")
                    implementation("io.coil-kt:coil-svg:2.6.0")
                    implementation("io.legere:pdfiumandroid:2.0.0")
                }
            }
            androidMain.dependsOn(mobileMain)
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
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
