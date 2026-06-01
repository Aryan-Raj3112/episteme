import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

@DisableCachingByDefault(because = "Verification task has no outputs.")
abstract class CheckBundledPdfiumRuntimeTask : DefaultTask() {
    @get:Input
    abstract val bundleRootPath: Property<String>

    @get:Input
    abstract val libraryPath: Property<String>

    @TaskAction
    fun checkRuntime() {
        val bundleRoot = File(bundleRootPath.get())
        val library = bundleRoot.resolve(libraryPath.get())
        if (!library.isFile) {
            throw GradleException(
                "Missing bundled Pdfium runtime at ${library.absolutePath}. " +
                    "Expected ${libraryPath.get()} inside ${bundleRoot.absolutePath}."
            )
        }
    }
}

@DisableCachingByDefault(because = "Renames package output produced by jpackage.")
abstract class RenameDesktopMsiOutputTask : DefaultTask() {
    @get:Input
    abstract val msiDirectoryPath: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val architecture: Property<String>

    @TaskAction
    fun renameOutput() {
        val msiDirectory = File(msiDirectoryPath.get())
        val outputPackageName = packageName.get()
        val outputPackageVersion = packageVersion.get()
        val source = msiDirectory.resolve("$outputPackageName-$outputPackageVersion.msi")
        if (!source.isFile) return

        val target = msiDirectory.resolve("$outputPackageName-$outputPackageVersion-${architecture.get()}.msi")
        if (target.exists() && !target.delete()) {
            throw GradleException("Could not replace existing MSI at ${target.absolutePath}.")
        }
        if (!source.renameTo(target)) {
            throw GradleException("Could not rename MSI from ${source.absolutePath} to ${target.absolutePath}.")
        }
    }
}

@DisableCachingByDefault(because = "Generates local desktop service config for native packages.")
abstract class GenerateDesktopCloudConfigTask : DefaultTask() {
    @get:Input
    abstract val configValues: MapProperty<String, String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            configValues.get().entries.joinToString(separator = "\n", postfix = "\n") { (key, value) ->
                "$key=${value.replace("\\", "\\\\").replace("\n", "")}"
            }
        )
    }
}

@DisableCachingByDefault(because = "Verification task has no outputs.")
abstract class VerifyDesktopNativePackagingTask : DefaultTask() {
    @get:Input
    abstract val supportedHost: Property<Boolean>

    @get:Input
    abstract val hostOsId: Property<String>

    @get:Input
    abstract val hostArchId: Property<String>

    @get:Input
    abstract val missingStandardServiceConfig: ListProperty<String>

    @TaskAction
    fun verify() {
        if (!supportedHost.get()) {
            throw GradleException(
                "Desktop native packaging is currently release-supported only on Windows x64 and Linux x64. " +
                    "Current host: ${hostOsId.get()} ${hostArchId.get()}."
            )
        }
        val missing = missingStandardServiceConfig.get()
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Standard desktop packages require account/sync service config. Missing: " +
                    missing.joinToString(", ") + ". " +
                    "Set DESKTOP_FIREBASE_WEB_API_KEY and DESKTOP_GOOGLE_OAUTH_CLIENT_ID, " +
                    "use -PdesktopFlavor=oss for the offline build, or set " +
                    "-PdesktopAllowUnconfiguredStandardServices=true for a local non-GA package."
            )
        }
    }
}

@DisableCachingByDefault(because = "Strips stale jar signatures in-place after ProGuard rewrites signed dependencies.")
abstract class StripInvalidJarSignaturesTask : DefaultTask() {
    @get:Input
    abstract val jarDirectoryPath: Property<String>

    @TaskAction
    fun stripSignatures() {
        val jarDirectory = File(jarDirectoryPath.get())
        if (!jarDirectory.isDirectory) return

        var strippedJarCount = 0
        jarDirectory.walkTopDown()
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .forEach { jar ->
                val strippedEntries = stripInvalidJarSignatures(jar)
                if (strippedEntries > 0) {
                    strippedJarCount += 1
                    logger.lifecycle("Stripped $strippedEntries stale jar signature entr${if (strippedEntries == 1) "y" else "ies"} from ${jar.name}")
                }
            }

        if (strippedJarCount > 0) {
            logger.lifecycle("Stripped stale jar signatures from $strippedJarCount ProGuard output jar${if (strippedJarCount == 1) "" else "s"}.")
        }
    }

    private fun stripInvalidJarSignatures(jar: File): Int {
        val temp = Files.createTempFile(jar.parentFile.toPath(), "${jar.nameWithoutExtension}-unsigned-", ".jar")
        var strippedEntries = 0

        ZipFile(jar).use { source ->
            ZipOutputStream(Files.newOutputStream(temp)).use { target ->
                val seenEntries = mutableSetOf<String>()
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val sourceEntry = entries.nextElement()
                    val entryName = sourceEntry.name
                    if (!seenEntries.add(entryName)) continue
                    if (isJarSignatureResource(entryName)) {
                        strippedEntries += 1
                        continue
                    }

                    val targetEntry = ZipEntry(entryName)
                    if (sourceEntry.time >= 0) {
                        targetEntry.time = sourceEntry.time
                    }
                    target.putNextEntry(targetEntry)
                    if (!sourceEntry.isDirectory) {
                        source.getInputStream(sourceEntry).use { input ->
                            input.copyTo(target)
                        }
                    }
                    target.closeEntry()
                }
            }
        }

        if (strippedEntries > 0) {
            try {
                Files.move(temp, jar.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } else {
            Files.deleteIfExists(temp)
        }

        return strippedEntries
    }

    private fun isJarSignatureResource(entryName: String): Boolean {
        val normalized = entryName.replace('\\', '/').uppercase()
        if (!normalized.startsWith("META-INF/")) return false

        val metaInfName = normalized.removePrefix("META-INF/")
        if (metaInfName.contains("/")) return false

        return metaInfName.startsWith("SIG-") ||
            metaInfName.endsWith(".SF") ||
            metaInfName.endsWith(".DSA") ||
            metaInfName.endsWith(".RSA") ||
            metaInfName.endsWith(".EC")
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

fun desktopSwtArtifactId(
    osName: String = System.getProperty("os.name"),
    osArch: String = System.getProperty("os.arch")
): String? {
    return when (desktopOsId(osName)) {
        "windows" -> when (desktopArchId(osArch)) {
            "arm64" -> "org.eclipse.swt.win32.win32.aarch64"
            else -> "org.eclipse.swt.win32.win32.x86_64"
        }

        "linux" -> when (desktopArchId(osArch)) {
            "arm64" -> "org.eclipse.swt.gtk.linux.aarch64"
            else -> "org.eclipse.swt.gtk.linux.x86_64"
        }

        "macos" -> when (desktopArchId(osArch)) {
            "arm64" -> "org.eclipse.swt.cocoa.macosx.aarch64"
            else -> "org.eclipse.swt.cocoa.macosx.x86_64"
        }

        else -> null
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

fun normalizeDesktopPackageVersion(rawVersion: String): String {
    val coreVersion = rawVersion.trim()
        .substringBefore("-")
        .substringBefore("+")
        .takeIf { it.isNotBlank() }
        ?: "1.0.0"
    val parts = coreVersion.split(".")
    val numericParts = parts.map { it.toIntOrNull() }
    val normalizedParts = when {
        parts.size in 1..3 && numericParts.all { it != null } ->
            numericParts.map { it ?: 0 } + List(3 - parts.size) { 0 }

        else -> throw GradleException(
            "desktopPackageVersion must be numeric MAJOR[.MINOR[.BUILD]], but was '$rawVersion'."
        )
    }
    val (major, minor, build) = normalizedParts
    if (major !in 0..255 || minor !in 0..255 || build !in 0..65535) {
        throw GradleException(
            "desktopPackageVersion '$rawVersion' is outside the Windows package version range. " +
                "Expected MAJOR 0..255, MINOR 0..255, BUILD 0..65535."
        )
    }
    return "$major.$minor.$build"
}

fun normalizeDesktopVersionName(rawVersion: String): String {
    return rawVersion.trim().takeIf { it.isNotBlank() } ?: "1.0.0"
}

fun normalizeDesktopFlavor(rawFlavor: String): String {
    val flavor = rawFlavor.trim().lowercase()
    return when (flavor) {
        "oss", "oss-offline", "episteme-oss" -> "oss-offline"
        else -> "standard"
    }
}

fun normalizeDesktopPackageArchitecture(osArch: String): String {
    val normalizedArch = desktopArchId(osArch)
    return if (normalizedArch != "unknown") {
        normalizedArch
    } else {
        osArch.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "unknown" }
    }
}

fun desktopDefaultPackageFormats(osName: String = System.getProperty("os.name")): String {
    return when (desktopOsId(osName)) {
        "windows" -> "msi"
        "linux" -> "deb,rpm"
        "macos" -> "dmg"
        else -> ""
    }
}

fun desktopTargetFormatForId(format: String): TargetFormat {
    return when (format.lowercase()) {
        "exe" -> TargetFormat.Exe
        "msi" -> TargetFormat.Msi
        "deb" -> TargetFormat.Deb
        "rpm" -> TargetFormat.Rpm
        "dmg" -> TargetFormat.Dmg
        "pkg" -> TargetFormat.Pkg
        else -> throw GradleException(
            "Unsupported desktopPackageFormats entry '$format'. " +
                "Use one or more of: msi, exe, deb, rpm, dmg, pkg."
        )
    }
}

fun desktopPackageFormatId(format: TargetFormat): String {
    return when (format) {
        TargetFormat.Exe -> "exe"
        TargetFormat.Msi -> "msi"
        TargetFormat.Deb -> "deb"
        TargetFormat.Rpm -> "rpm"
        TargetFormat.Dmg -> "dmg"
        TargetFormat.Pkg -> "pkg"
        else -> format.name.lowercase()
    }
}

fun desktopPackageFormatSupportedOnHost(
    format: TargetFormat,
    osName: String = System.getProperty("os.name")
): Boolean {
    return when (desktopOsId(osName)) {
        "windows" -> format == TargetFormat.Msi || format == TargetFormat.Exe
        "linux" -> format == TargetFormat.Deb || format == TargetFormat.Rpm
        "macos" -> format == TargetFormat.Dmg || format == TargetFormat.Pkg
        else -> false
    }
}

fun normalizeDesktopPackageFormats(
    rawFormats: String,
    osName: String = System.getProperty("os.name")
): List<TargetFormat> {
    val formats = rawFormats
        .split(',', ';', ' ', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map(::desktopTargetFormatForId)
        .distinct()
    if (formats.isEmpty()) {
        throw GradleException(
            "desktopPackageFormats resolved to no package formats for ${desktopOsId(osName)}. " +
                "Set -PdesktopPackageFormats=msi on Windows or -PdesktopPackageFormats=deb,rpm on Linux."
        )
    }
    val unsupported = formats.filterNot { desktopPackageFormatSupportedOnHost(it, osName) }
    if (unsupported.isNotEmpty()) {
        throw GradleException(
            "desktopPackageFormats=${formats.joinToString(",") { desktopPackageFormatId(it) }} does not match " +
                "the current packaging host ${desktopOsId(osName)}. Unsupported here: " +
                unsupported.joinToString(",") { desktopPackageFormatId(it) } + "."
        )
    }
    return formats
}

val desktopVersionName = "1.0.1"
val desktopFlavor = providers.gradleProperty("desktopFlavor")
    .orElse("standard")
    .map(::normalizeDesktopFlavor)
    .get()
val isOssOfflineDesktop = desktopFlavor == "oss-offline"
val desktopDiagnostics = providers.gradleProperty("desktopDiagnostics")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
val desktopDiagnosticTags = providers.gradleProperty("desktopDiagnosticTags")
    .orElse("")
val desktopResolvedVersionName = providers.gradleProperty("desktopVersionName")
    .orElse(providers.gradleProperty("desktopVersion"))
    .orElse(desktopVersionName)
    .map(::normalizeDesktopVersionName)
val desktopPackageVersion = providers.gradleProperty("desktopPackageVersion")
    .orElse(desktopResolvedVersionName)
    .map(::normalizeDesktopPackageVersion)
val desktopPackageName = if (isOssOfflineDesktop) "Episteme oss" else "Episteme"
val desktopPackageDescription = if (isOssOfflineDesktop) {
    "Episteme oss offline desktop reader"
} else {
    "Episteme desktop reader"
}
val desktopVendor = providers.gradleProperty("desktopVendor").orElse("Aryan")
val desktopOsName = System.getProperty("os.name")
val desktopOsArch = System.getProperty("os.arch")
val desktopPackageArchitecture = normalizeDesktopPackageArchitecture(desktopOsArch)
val desktopPackageTargetFormats = providers.gradleProperty("desktopPackageFormats")
    .orElse(desktopDefaultPackageFormats(desktopOsName))
    .map { normalizeDesktopPackageFormats(it, desktopOsName) }
    .get()
val desktopNativePackageSupportedHost = desktopOsId(desktopOsName) in setOf("windows", "linux") &&
    desktopArchId(desktopOsArch) == "x64"
val desktopReleaseProguardEnabled = providers.gradleProperty("desktopReleaseProguard")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()
val desktopSwtVersion = "3.133.0"
val desktopSwtDependency = desktopSwtArtifactId(desktopOsName, desktopOsArch)
    ?.let { artifactId -> "org.eclipse.platform:$artifactId:$desktopSwtVersion" }
val generatedDesktopResourcesDir = layout.buildDirectory.dir("generated/desktopAppResources")
val generatedDesktopCloudConfigFile = layout.buildDirectory.file("generated/desktopCloudConfig/desktop-cloud.properties")
val generatedDesktopStringResourcesDir = layout.buildDirectory.dir("generated/desktopStringResources")
val rootLocalProperties = Properties()
val rootLocalPropertiesFile = rootProject.file("local.properties")
if (rootLocalPropertiesFile.exists()) {
    rootLocalPropertiesFile.inputStream().use(rootLocalProperties::load)
}
fun desktopConfigValue(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        providers.gradleProperty(key).orNull
            ?: rootLocalProperties.getProperty(key)
            ?: System.getenv(key)
    }?.trim().orEmpty()
}
val desktopCloudConfig = mapOf(
    "AI_WORKER_URL" to desktopConfigValue("DESKTOP_AI_WORKER_URL", "AI_WORKER_URL"),
    "TTS_WORKER_URL" to desktopConfigValue("DESKTOP_TTS_WORKER_URL", "TTS_WORKER_URL"),
    "FIREBASE_WEB_API_KEY" to desktopConfigValue("DESKTOP_FIREBASE_WEB_API_KEY", "FIREBASE_WEB_API_KEY", "GOOGLE_API_KEY"),
    "FIREBASE_PROJECT_ID" to desktopConfigValue("DESKTOP_FIREBASE_PROJECT_ID", "FIREBASE_PROJECT_ID").ifBlank { "reader-9fc469d7" },
    "GOOGLE_OAUTH_CLIENT_ID" to desktopConfigValue("DESKTOP_GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_OAUTH_CLIENT_ID", "GOOGLE_WEB_CLIENT_ID", "DEFAULT_WEB_CLIENT_ID"),
    "GOOGLE_OAUTH_CLIENT_SECRET" to desktopConfigValue("DESKTOP_GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_OAUTH_CLIENT_SECRET", "GOOGLE_WEB_CLIENT_SECRET", "DEFAULT_WEB_CLIENT_SECRET")
)
val desktopAllowUnconfiguredStandardServices = providers.gradleProperty("desktopAllowUnconfiguredStandardServices")
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)
    .get()
val desktopMissingStandardServiceConfig = if (isOssOfflineDesktop || desktopAllowUnconfiguredStandardServices) {
    emptyList()
} else {
    listOf("FIREBASE_WEB_API_KEY", "GOOGLE_OAUTH_CLIENT_ID")
        .filter { key -> desktopCloudConfig[key].isNullOrBlank() }
}
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

val checkBundledPdfiumRuntime by tasks.registering(CheckBundledPdfiumRuntimeTask::class) {
    bundleRootPath.set(bundledPdfiumDir.asFile.absolutePath)
    libraryPath.set(bundledPdfiumLibraryPath)
}

val generateDesktopCloudConfig by tasks.registering(GenerateDesktopCloudConfigTask::class) {
    configValues.set(desktopCloudConfig)
    outputFile.set(generatedDesktopCloudConfigFile)
}

val prepareBundledDesktopResources by tasks.registering(Sync::class) {
    dependsOn(checkBundledPdfiumRuntime, generateDesktopCloudConfig)
    from(bundledPdfiumDir) {
        into("common/third_party/pdfium/${desktopPdfiumDirectoryName(desktopOsName, desktopOsArch)}")
    }
    into("common") {
        from(generatedDesktopCloudConfigFile)
    }
    into(generatedDesktopResourcesDir)
}

val prepareDesktopStringResources by tasks.registering(Sync::class) {
    // Reuse Android string resources as the localization source for desktop.
    from(rootProject.layout.projectDirectory.dir("app/src/main/res")) {
        include("values*/strings.xml")
        include("values*/plurals.xml")
        into("desktop-android-res")
    }
    into(generatedDesktopStringResourcesDir)
}

val verifyDesktopNativePackaging by tasks.registering(VerifyDesktopNativePackagingTask::class) {
    supportedHost.set(desktopNativePackageSupportedHost)
    hostOsId.set(desktopOsId(desktopOsName))
    hostArchId.set(desktopArchId(desktopOsArch))
    missingStandardServiceConfig.set(desktopMissingStandardServiceConfig)
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)

    sourceSets {
        val desktopMain by getting {
            resources.srcDir(prepareDesktopStringResources)
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                desktopSwtDependency?.let { dependency ->
                    compileOnly(dependency)
                    runtimeOnly(dependency)
                }
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
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
        mainClass = "com.aryan.reader.desktop.LauncherKt"
        desktopPackagingJavaHome?.let { javaHome = it }

        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
        jvmArgs("-Depisteme.desktop.flavor=$desktopFlavor")
        jvmArgs("-Depisteme.desktop.diagnostics=${desktopDiagnostics.get()}")
        jvmArgs("-Depisteme.desktop.diagnostics.tags=${desktopDiagnosticTags.get()}")
        jvmArgs("-Depisteme.desktop.version=${desktopResolvedVersionName.get()}")

        buildTypes.release.proguard {
            // ProGuard still rewrites and shrinks release jars even when optimization and
            // obfuscation are disabled. That has produced invalid stack-map frames in large
            // Compose/PDF lambdas and stripped WebView bridge behavior in packaged MSIs.
            isEnabled.set(desktopReleaseProguardEnabled)
            obfuscate.set(false)
            // Compose/Kotlin generated methods can produce very large stack-map frames.
            // ProGuard optimization has emitted invalid frames for SharedAppTheme in release builds.
            optimize.set(false)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(*desktopPackageTargetFormats.toTypedArray())
            modules(
                "java.datatransfer",
                "java.desktop",
                "java.logging",
                "java.management",
                "java.net.http",
                "jdk.charsets",
                "jdk.httpserver",
                "jdk.unsupported"
            )
            packageName = desktopPackageName
            packageVersion = desktopPackageVersion.get()
            description = desktopPackageDescription
            vendor = desktopVendor.get()
            appResourcesRootDir.set(generatedDesktopResourcesDir)
            windows {
                iconFile.set(desktopWindowsIconFile)
                dirChooser = true
                shortcut = true
                menu = true
                menuGroup = "Episteme"
                perUserInstall = true
                upgradeUuid = desktopWindowsUpgradeUuid
            }
            linux {
                iconFile.set(desktopLinuxIconFile)
                packageName = if (isOssOfflineDesktop) "episteme-oss" else "episteme"
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
    jvmArgs("-Depisteme.desktop.diagnostics.tags=${desktopDiagnosticTags.get()}")
    jvmArgs("-Depisteme.desktop.version=${desktopResolvedVersionName.get()}")
    if (System.getProperty("os.name").contains("Mac")) {
        jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to desktopPackageName,
            "Implementation-Version" to desktopResolvedVersionName.get(),
            "Implementation-Vendor" to desktopVendor.get()
        )
    }
}

val stripReleaseProguardJarSignatures = if (desktopReleaseProguardEnabled) {
    tasks.registering(StripInvalidJarSignaturesTask::class) {
        dependsOn("proguardReleaseJars")
        jarDirectoryPath.set(layout.buildDirectory.dir("compose/tmp/main-release/proguard").map { it.asFile.absolutePath })
    }
} else {
    null
}

tasks.matching {
    it.name in setOf(
        "createReleaseDistributable",
        "packageReleaseDistributionForCurrentOS",
        "packageReleaseExe",
        "packageReleaseMsi",
        "packageReleaseDeb",
        "packageReleaseRpm",
        "runReleaseDistributable"
    )
}.configureEach {
    stripReleaseProguardJarSignatures?.let { dependsOn(it) }
}

mapOf(
    "packageMsi" to "main",
    "packageReleaseMsi" to "main-release"
).forEach { (taskName, distributionName) ->
    val renameTask = tasks.register<RenameDesktopMsiOutputTask>("rename${taskName.replaceFirstChar(Char::titlecase)}Output") {
        msiDirectoryPath.set(layout.buildDirectory.dir("compose/binaries/$distributionName/msi").get().asFile.absolutePath)
        packageName.set(desktopPackageName)
        packageVersion.set(desktopPackageVersion.get())
        architecture.set(desktopPackageArchitecture)
    }
    tasks.matching { it.name == taskName }.configureEach {
        finalizedBy(renameTask)
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
    dependsOn(verifyDesktopNativePackaging)
    dependsOn(prepareBundledDesktopResources)
    inputs.dir(generatedDesktopResourcesDir)
        .withPropertyName("bundledDesktopResources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
