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
            outputs.file(outputDirectory.resolve("libmobi.a"))
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
                    // Static link (same pattern as libarchive): the archive is
                    // merged into the static ReaderShared framework, so Xcode
                    // must not link or embed any libmobi dylib.
                    extraOpts(
                        "-libraryPath", mobiRoot.get().asFile.absolutePath,
                        "-staticLibrary", "libmobi.a"
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

        // libpdfium.dylib's LC_ID_DYLIB is "./libpdfium.dylib" (CWD-relative), so a plain
        // -L/-lpdfium records a dependency the simulator test runner cannot resolve
        // (dyld looks under RuntimeRoot). Stage a copy with id "@rpath/libpdfium.dylib"
        // and link with -rpath so the staged dir is searched. An absolute install name
        // is too long for install_name_tool (no headerpad in the shipped dylib).
        val stagedPdfiumDir = layout.buildDirectory.dir("native/pdfium/$name")
        val stagePdfiumTask = tasks.register("stagePdfium${name.replaceFirstChar(Char::uppercaseChar)}") {
            inputs.file(pdfiumRoot.file("lib/libpdfium.dylib"))
            outputs.file(stagedPdfiumDir.map { it.file("libpdfium.dylib") })
            doLast {
                val dest = stagedPdfiumDir.get().asFile.resolve("libpdfium.dylib")
                dest.parentFile.mkdirs()
                pdfiumRoot.file("lib/libpdfium.dylib").asFile.copyTo(dest, overwrite = true)
                val process = ProcessBuilder(
                    "install_name_tool",
                    "-id", "@rpath/libpdfium.dylib",
                    dest.absolutePath,
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                if (process.waitFor() != 0) {
                    throw GradleException("install_name_tool failed for $dest:\n$output")
                }
            }
        }
        binaries.configureEach {
            // Test executables also need pdfium; previously only the framework
            // was linked, so wiring iosTest made linkDebugTest* fail on FPDF_*.
            linkTaskProvider.configure { dependsOn(stagePdfiumTask) }
            val stagedLibDir = stagedPdfiumDir.get().asFile.absolutePath
            linkerOpts(
                "-L$stagedLibDir",
                "-lpdfium",
                "-Wl,-rpath,$stagedLibDir"
            )
        }
        binaries.framework {
            baseName = "ReaderShared"
            binaryOption("bundleId", "com.aryan.reader.shared")
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
                // Charset detection for legacy "ANSI" text files (GBK, Big5,
                // Shift-JIS, EUC-KR, windows-125x) shared by Android + desktop.
                implementation("com.github.albfernandez:juniversalchardet:2.5.0")
            }
        }
        if (!desktopOnlyBuild) {
            val androidMain by getting {
                dependencies {
                    implementation(libs.androidx.core.ktx)
                    implementation("io.coil-kt:coil:2.7.0")
                    implementation("io.coil-kt:coil-svg:2.7.0")
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
            // Manual iosMain intermediates above skip the default hierarchy's iosTest
            // wiring; without this, src/iosTest is never compiled or run.
            val iosTest by creating {
                dependsOn(commonTest.get())
            }
            val iosArm64Test by getting {
                dependsOn(iosTest)
            }
            val iosSimulatorArm64Test by getting {
                dependsOn(iosTest)
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
        val desktopTest by getting {
            dependencies {
                runtimeOnly(compose.desktop.currentOs)
            }
        }
    }
}

val verifyPortableSharedSources by tasks.registering {
    group = "verification"
    description = "Rejects platform APIs from portable shared source sets."
    val portableSources = files(
        fileTree("src/commonMain/kotlin") { include("**/*.kt") },
        fileTree("src/mobileMain/kotlin") { include("**/*.kt") },
    )
    inputs.files(portableSources)
    doLast {
        val forbiddenImport = Regex("(?m)^import\\s+(android|java|javax)\\.")
        val violations = inputs.files.files
            .filter { forbiddenImport.containsMatchIn(it.readText()) }
            .map { it.path }
            .sorted()
        check(violations.isEmpty()) {
            "Portable shared sources must use expect/actual or platform ports; forbidden imports: ${violations.joinToString()}"
        }
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(verifyPortableSharedSources)
}
