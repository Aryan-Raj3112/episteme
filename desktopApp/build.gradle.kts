import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

abstract class CheckBundledWebViewRuntimeTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleDir: DirectoryProperty

    @get:Input
    abstract val osName: Property<String>

    @get:Input
    abstract val osArch: Property<String>

    @TaskAction
    fun checkRuntime() {
        val bundleRoot = bundleDir.get().asFile
        val missingFiles = bundledWebViewRequiredPaths(osName.get(), osArch.get())
            .filterNot { bundleRoot.resolve(it).exists() }
        if (missingFiles.isNotEmpty()) {
            throw GradleException(
                "Missing bundled KCEF runtime at ${bundleRoot.absolutePath}. " +
                    "Expected ${missingFiles.joinToString()} for ${osName.get()} ${osArch.get()} desktop packages."
            )
        }
    }
}

abstract class CheckBundledPdfiumRuntimeTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val bundleDir: DirectoryProperty

    @get:Input
    abstract val libraryPath: Property<String>

    @TaskAction
    fun checkRuntime() {
        val bundleRoot = bundleDir.get().asFile
        val library = bundleRoot.resolve(libraryPath.get())
        if (!library.isFile) {
            throw GradleException(
                "Missing bundled Pdfium runtime at ${library.absolutePath}. " +
                    "Expected ${libraryPath.get()} inside ${bundleRoot.absolutePath}."
            )
        }
    }
}

fun desktopOsId(osName: String = System.getProperty("os.name")): String {
    val normalized = osName.lowercase()
    return when {
        normalized.startsWith("windows") -> "windows"
        normalized == "linux" || normalized.contains("linux") -> "linux"
        normalized.startsWith("mac") || normalized.contains("darwin") -> "macos"
        else -> "other"
    }
}

fun desktopArchId(osArch: String = System.getProperty("os.arch")): String {
    return when (osArch.lowercase()) {
        "amd64", "x86_64", "x64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        "x86", "i386", "i686" -> "x86"
        else -> "unknown"
    }
}

fun desktopKcefBundleDirectoryName(
    osName: String = System.getProperty("os.name"),
    osArch: String = System.getProperty("os.arch")
): String {
    return when (desktopOsId(osName)) {
        "windows" -> "kcef-bundle"
        "linux" -> "kcef-bundle-linux-${desktopArchId(osArch)}"
        "macos" -> "kcef-bundle-macos-${desktopArchId(osArch)}"
        else -> "kcef-bundle-${desktopArchId(osArch)}"
    }
}

fun bundledWebViewRequiredPaths(osName: String, osArch: String): List<String> {
    return when (desktopOsId(osName)) {
        "windows" -> listOf("jcef.dll", "libcef.dll")
        "linux" -> listOf("libcef.so", "chrome-sandbox", "icudtl.dat", "locales")
        "macos" -> listOf("jcef Helper.app", "Chromium Embedded Framework.framework")
        else -> emptyList()
    }
}

fun desktopPdfiumDirectoryName(
    osName: String = System.getProperty("os.name"),
    osArch: String = System.getProperty("os.arch")
): String {
    return when (desktopOsId(osName)) {
        "windows" -> "win-${desktopArchId(osArch)}-v8"
        "linux" -> "linux-${desktopArchId(osArch)}-v8"
        "macos" -> "mac-${desktopArchId(osArch)}-v8"
        else -> "${desktopArchId(osArch)}-v8"
    }
}

fun desktopPdfiumLibraryPath(
    osName: String = System.getProperty("os.name"),
    osArch: String = System.getProperty("os.arch")
): String {
    return when (desktopOsId(osName)) {
        "windows" -> "bin/pdfium.dll"
        "linux" -> "lib/libpdfium.so"
        "macos" -> "lib/libpdfium.dylib"
        else -> "lib/pdfium"
    }
}

val desktopFlavor = providers.gradleProperty("desktopFlavor").orElse("standard").get().lowercase()
val isOssOfflineDesktop = desktopFlavor == "oss-offline"
val desktopDiagnostics = providers.gradleProperty("desktopDiagnostics")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val desktopPackageVersion = providers.gradleProperty("desktopPackageVersion").orElse("1.0.0")
val generatedDesktopResourcesDir = layout.buildDirectory.dir("generated/desktopAppResources")
val bundledWebViewDir = layout.projectDirectory.dir(desktopKcefBundleDirectoryName())
val bundledPdfiumDir = layout.projectDirectory.dir("../third_party/pdfium/${desktopPdfiumDirectoryName()}")
val desktopWindowsIconFile = layout.projectDirectory.file("src/desktopMain/resources/episteme.ico")
val desktopLinuxIconFile = layout.projectDirectory.file("src/desktopMain/resources/episteme_icon.png")
val desktopWindowsUpgradeUuid = if (isOssOfflineDesktop) {
    "ca13b201-940a-420a-8a3f-16e7d83d12a8"
} else {
    "c04c5823-b25a-4f38-a1cf-0da7b02ac397"
}

val checkBundledWebViewRuntime by tasks.registering(CheckBundledWebViewRuntimeTask::class) {
    bundleDir.set(bundledWebViewDir)
    osName.set(System.getProperty("os.name"))
    osArch.set(System.getProperty("os.arch"))
}

val checkBundledPdfiumRuntime by tasks.registering(CheckBundledPdfiumRuntimeTask::class) {
    bundleDir.set(bundledPdfiumDir)
    libraryPath.set(desktopPdfiumLibraryPath())
}

val prepareBundledDesktopResources by tasks.registering(Sync::class) {
    dependsOn(checkBundledWebViewRuntime, checkBundledPdfiumRuntime)
    from(bundledWebViewDir) {
        into("kcef-bundle")
    }
    from(bundledPdfiumDir) {
        into("third_party/pdfium/${desktopPdfiumDirectoryName()}")
    }
    into(generatedDesktopResourcesDir)
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("io.github.kevinnzou:compose-webview-multiplatform:2.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("net.java.dev.jna:jna:5.17.0")
                implementation("org.apache.commons:commons-compress:1.28.0")
                implementation("org.tukaani:xz:1.10")
                implementation("com.twelvemonkeys.imageio:imageio-webp:3.13.1")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aryan.reader.desktop.MainKt"

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
        jvmArgs("-Depisteme.desktop.flavor=$desktopFlavor")
        jvmArgs("-Depisteme.desktop.diagnostics=${desktopDiagnostics.get()}")

        buildTypes.release.proguard {
            obfuscate.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = if (isOssOfflineDesktop) "Episteme OSS Offline" else "Episteme"
            packageVersion = desktopPackageVersion.get()
            description = if (isOssOfflineDesktop) {
                "Episteme desktop offline shell"
            } else {
                "Episteme desktop shell"
            }
            vendor = "Aryan Reader"
            appResourcesRootDir.set(generatedDesktopResourcesDir)
            windows {
                iconFile.set(desktopWindowsIconFile)
                dirChooser = true
                menuGroup = "Episteme"
                perUserInstall = true
                upgradeUuid = desktopWindowsUpgradeUuid
            }
            linux {
                iconFile.set(desktopLinuxIconFile)
                packageName = if (isOssOfflineDesktop) "episteme-oss-offline" else "episteme"
                debMaintainer = "epistemereader@gmail.com"
                menuGroup = "Office"
                appCategory = "Office"
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
    jvmArgs("-Depisteme.desktop.flavor=$desktopFlavor")
    jvmArgs("-Depisteme.desktop.diagnostics=${desktopDiagnostics.get()}")
    if (System.getProperty("os.name").contains("Mac")) {
        jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
    }
}

tasks.matching {
    it.name in setOf(
        "createDistributable",
        "createReleaseDistributable",
        "prepareAppResources",
        "prepareReleaseAppResources",
        "packageDistributionForCurrentOS",
        "packageReleaseDistributionForCurrentOS",
        "packageExe",
        "packageReleaseExe",
        "packageMsi",
        "packageReleaseMsi",
        "packageDeb",
        "packageReleaseDeb",
        "packageRpm",
        "packageReleaseRpm",
        "runDistributable",
        "runReleaseDistributable"
    )
}.configureEach {
    dependsOn(prepareBundledDesktopResources)
}
