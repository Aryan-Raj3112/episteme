import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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

fun desktopJdkToolName(toolName: String, osName: String = System.getProperty("os.name")): String {
    return if (desktopOsId(osName) == "windows") "$toolName.exe" else toolName
}

fun File.asDesktopJdkHome(): File {
    val absolute = absoluteFile
    return when {
        absolute.isFile && absolute.parentFile?.name?.equals("bin", ignoreCase = true) == true ->
            absolute.parentFile?.parentFile ?: absolute

        absolute.isDirectory && absolute.name.equals("bin", ignoreCase = true) ->
            absolute.parentFile ?: absolute

        absolute.resolve("Contents/Home/bin").isDirectory ->
            absolute.resolve("Contents/Home")

        else -> absolute
    }
}

fun File.desktopJdkTool(toolName: String, osName: String = System.getProperty("os.name")): File {
    return resolve("bin/${desktopJdkToolName(toolName, osName)}")
}

fun File.isDesktopPackagingJdk(osName: String = System.getProperty("os.name")): Boolean {
    return desktopJdkTool("java", osName).isFile &&
        desktopJdkTool("jlink", osName).isFile &&
        desktopJdkTool("jpackage", osName).isFile
}

fun File.desktopJdkMajorVersion(): Int? {
    val releaseFile = resolve("release")
    if (!releaseFile.isFile) return null

    val version = runCatching {
        releaseFile.useLines { lines ->
            lines.firstOrNull { it.startsWith("JAVA_VERSION=") }
                ?.substringAfter("=")
                ?.trim()
                ?.trim('"')
        }
    }.getOrNull() ?: return null

    return version.removePrefix("1.").substringBefore(".").toIntOrNull()
}

fun safeChildDirectories(root: File): List<File> {
    return runCatching {
        root.listFiles()?.filter { it.isDirectory }.orEmpty()
    }.getOrDefault(emptyList())
}

fun stableDesktopJdkCandidates(candidates: List<File>, preferredMajorVersion: Int = 21): List<File> {
    return candidates
        .map { it.asDesktopJdkHome() }
        .distinctBy { runCatching { it.canonicalPath }.getOrElse { _ -> it.absolutePath }.lowercase() }
        .sortedWith(
            compareBy<File> { if (it.desktopJdkMajorVersion() == preferredMajorVersion) 0 else 1 }
                .thenBy { it.desktopJdkMajorVersion() ?: Int.MAX_VALUE }
                .thenBy { it.absolutePath.lowercase() }
        )
}

fun desktopPathJdkCandidates(osName: String = System.getProperty("os.name")): List<File> {
    return System.getenv("PATH")
        ?.split(File.pathSeparator)
        .orEmpty()
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { File(it).resolve(desktopJdkToolName("jpackage", osName)) }
        .filter { it.isFile }
        .mapNotNull { it.parentFile?.parentFile }
        .toList()
}

fun desktopGradleJdkCandidates(): List<File> {
    val userHome = System.getProperty("user.home")?.let(::File) ?: return emptyList()
    return stableDesktopJdkCandidates(safeChildDirectories(userHome.resolve(".gradle/jdks")))
}

fun desktopPlatformJdkCandidates(osName: String = System.getProperty("os.name")): List<File> {
    val roots = when (desktopOsId(osName)) {
        "windows" -> listOfNotNull(
            System.getenv("ProgramFiles")?.let { File(it, "Java") },
            System.getenv("ProgramFiles")?.let { File(it, "Eclipse Adoptium") },
            System.getenv("ProgramFiles")?.let { File(it, "Microsoft") },
            System.getenv("ProgramFiles(x86)")?.let { File(it, "Java") }
        )

        "linux" -> listOf(
            File("/usr/lib/jvm"),
            File("/usr/java"),
            File("/opt/java"),
            File("/opt/jdk")
        )

        "macos" -> listOfNotNull(
            File("/Library/Java/JavaVirtualMachines"),
            System.getProperty("user.home")?.let { File(it, "Library/Java/JavaVirtualMachines") }
        )

        else -> emptyList()
    }

    return stableDesktopJdkCandidates(roots + roots.flatMap(::safeChildDirectories))
}

fun findDesktopPackagingJavaHome(
    explicitCandidates: List<String>,
    implicitCandidates: List<File>,
    osName: String = System.getProperty("os.name")
): File? {
    val explicitJavaHome = explicitCandidates.firstOrNull { it.isNotBlank() }
    if (explicitJavaHome != null) {
        val candidate = File(explicitJavaHome).asDesktopJdkHome()
        if (!candidate.isDesktopPackagingJdk(osName)) {
            throw GradleException(
                "Desktop packaging JDK must include java, jlink, and jpackage under " +
                    "${candidate.resolve("bin").absolutePath}. " +
                    "Set -PdesktopPackagingJavaHome=<jdk-home> or DESKTOP_PACKAGING_JAVA_HOME to a full JDK."
            )
        }
        return candidate
    }

    return implicitCandidates
        .map { it.asDesktopJdkHome() }
        .distinctBy { runCatching { it.canonicalPath }.getOrElse { _ -> it.absolutePath }.lowercase() }
        .firstOrNull { it.isDesktopPackagingJdk(osName) }
}

val desktopFlavor = providers.gradleProperty("desktopFlavor").orElse("standard").get().lowercase()
val isOssOfflineDesktop = desktopFlavor == "oss-offline"
val desktopDiagnostics = providers.gradleProperty("desktopDiagnostics")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val desktopPackageVersion = providers.gradleProperty("desktopPackageVersion").orElse("1.0.0")
val desktopOsName = System.getProperty("os.name")
val desktopOsArch = System.getProperty("os.arch")
val generatedDesktopResourcesDir = layout.buildDirectory.dir("generated/desktopAppResources")
val bundledWebViewDir = layout.projectDirectory.dir(desktopKcefBundleDirectoryName(desktopOsName, desktopOsArch))
val bundledPdfiumDir = layout.projectDirectory.dir(
    "../third_party/pdfium/${desktopPdfiumDirectoryName(desktopOsName, desktopOsArch)}"
)
val bundledPdfiumLibraryPath = desktopPdfiumLibraryPath(desktopOsName, desktopOsArch)
val desktopWindowsIconFile = layout.projectDirectory.file("src/desktopMain/resources/episteme.ico")
val desktopLinuxIconFile = layout.projectDirectory.file("src/desktopMain/resources/episteme_icon.png")
val desktopWindowsUpgradeUuid = if (isOssOfflineDesktop) {
    "ca13b201-940a-420a-8a3f-16e7d83d12a8"
} else {
    "c04c5823-b25a-4f38-a1cf-0da7b02ac397"
}
val desktopPackagingJavaHome = findDesktopPackagingJavaHome(
    explicitCandidates = listOfNotNull(
        providers.gradleProperty("desktopPackagingJavaHome").orNull,
        providers.environmentVariable("DESKTOP_PACKAGING_JAVA_HOME").orNull,
        providers.environmentVariable("JPACKAGE_HOME").orNull
    ),
    implicitCandidates = buildList {
        addAll(desktopGradleJdkCandidates())
        providers.gradleProperty("org.gradle.java.home").orNull?.let { add(File(it)) }
        providers.environmentVariable("GRADLE_LOCAL_JAVA_HOME").orNull?.let { add(File(it)) }
        providers.environmentVariable("JAVA_HOME").orNull?.let { add(File(it)) }
        providers.environmentVariable("JDK_HOME").orNull?.let { add(File(it)) }
        add(File(System.getProperty("java.home")))
        addAll(desktopPathJdkCandidates(desktopOsName))
        addAll(desktopPlatformJdkCandidates(desktopOsName))
    },
    osName = desktopOsName
)?.absolutePath

val checkBundledWebViewRuntime by tasks.registering {
    val requiredPaths = bundledWebViewRequiredPaths(desktopOsName, desktopOsArch)
    inputs.dir(bundledWebViewDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("osName", desktopOsName)
    inputs.property("osArch", desktopOsArch)
    inputs.property("requiredPaths", requiredPaths)

    doLast {
        val bundleRoot = bundledWebViewDir.asFile
        val missingFiles = requiredPaths.filterNot { bundleRoot.resolve(it).exists() }
        if (missingFiles.isNotEmpty()) {
            throw GradleException(
                "Missing bundled KCEF runtime at ${bundleRoot.absolutePath}. " +
                    "Expected ${missingFiles.joinToString()} for $desktopOsName $desktopOsArch desktop packages."
            )
        }
    }
}

val checkBundledPdfiumRuntime by tasks.registering {
    inputs.dir(bundledPdfiumDir).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.property("libraryPath", bundledPdfiumLibraryPath)

    doLast {
        val bundleRoot = bundledPdfiumDir.asFile
        val library = bundleRoot.resolve(bundledPdfiumLibraryPath)
        if (!library.isFile) {
            throw GradleException(
                "Missing bundled Pdfium runtime at ${library.absolutePath}. " +
                    "Expected $bundledPdfiumLibraryPath inside ${bundleRoot.absolutePath}."
            )
        }
    }
}

val prepareBundledDesktopResources by tasks.registering(Sync::class) {
    dependsOn(checkBundledWebViewRuntime, checkBundledPdfiumRuntime)
    from(bundledWebViewDir) {
        into("kcef-bundle")
    }
    from(bundledPdfiumDir) {
        into("third_party/pdfium/${desktopPdfiumDirectoryName(desktopOsName, desktopOsArch)}")
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
        desktopPackagingJavaHome?.let { javaHome = it }

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
