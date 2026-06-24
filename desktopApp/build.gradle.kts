import org.gradle.api.GradleException
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Compression
import org.gradle.api.tasks.bundling.Tar
import org.gradle.jvm.tasks.Jar
import org.gradle.process.ExecOperations
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.inject.Inject

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

@DisableCachingByDefault(because = "Generates an MSIX manifest from package metadata.")
abstract class GenerateDesktopMsixManifestTask : DefaultTask() {
    @get:Input
    abstract val identityName: Property<String>

    @get:Input
    abstract val publisher: Property<String>

    @get:Input
    abstract val publisherDisplayName: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val packageDescription: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val architecture: Property<String>

    @get:Input
    abstract val executablePath: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        fun xmlEscaped(value: String): String {
            return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
        }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <Package
              xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
              xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
              xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
              IgnorableNamespaces="uap rescap">
              <Identity
                Name="${xmlEscaped(identityName.get())}"
                Publisher="${xmlEscaped(publisher.get())}"
                Version="${xmlEscaped(packageVersion.get())}"
                ProcessorArchitecture="${xmlEscaped(architecture.get())}" />
              <Properties>
                <DisplayName>${xmlEscaped(packageName.get())}</DisplayName>
                <PublisherDisplayName>${xmlEscaped(publisherDisplayName.get())}</PublisherDisplayName>
                <Logo>Assets\StoreLogo.png</Logo>
              </Properties>
              <Resources>
                <Resource Language="en-us" />
              </Resources>
              <Dependencies>
                <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.22621.0" />
              </Dependencies>
              <Applications>
                <Application Id="Episteme" Executable="${xmlEscaped(executablePath.get())}" EntryPoint="Windows.FullTrustApplication">
                  <uap:VisualElements
                    DisplayName="${xmlEscaped(packageName.get())}"
                    Description="${xmlEscaped(packageDescription.get())}"
                    BackgroundColor="transparent"
                    Square44x44Logo="Assets\Square44x44Logo.png"
                    Square150x150Logo="Assets\Square150x150Logo.png" />
                </Application>
              </Applications>
              <Capabilities>
                <rescap:Capability Name="runFullTrust" />
              </Capabilities>
            </Package>
            """.trimIndent() + "\n",
            Charsets.UTF_8
        )
    }
}

@DisableCachingByDefault(because = "Generates fixed-size MSIX logo assets from the desktop icon.")
abstract class GenerateDesktopMsixAssetsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceIconFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val source = ImageIO.read(sourceIconFile.get().asFile)
            ?: throw GradleException("Could not read MSIX source icon ${sourceIconFile.get().asFile.absolutePath}.")
        val output = outputDirectory.get().asFile
        output.mkdirs()
        writePng(source, output.resolve("Square44x44Logo.png"), 44)
        writePng(source, output.resolve("Square150x150Logo.png"), 150)
        writePng(source, output.resolve("StoreLogo.png"), 50)
    }

    private fun writePng(source: BufferedImage, target: File, size: Int) {
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            )
            graphics.drawImage(source, 0, 0, size, size, null)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", target)
    }
}

@DisableCachingByDefault(because = "Packages the staged MSIX app image with Windows SDK makeappx.")
abstract class PackageDesktopMsixTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packageRootDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val makeAppxPath: Property<String>

    @get:Input
    abstract val hostOsId: Property<String>

    @get:Input
    abstract val hostArchId: Property<String>

    @TaskAction
    fun packageMsix() {
        if (hostOsId.get() != "windows" || hostArchId.get() != "x64") {
            throw GradleException(
                "MSIX packaging requires a Windows x64 packaging host. " +
                    "Current host: ${hostOsId.get()} ${hostArchId.get()}."
            )
        }

        val makeAppx = File(makeAppxPath.get())
        if (!makeAppx.isFile) {
            throw GradleException(
                "Windows SDK makeappx.exe was not found at ${makeAppx.absolutePath}. " +
                    "Install the Windows SDK MSIX packaging tools or set " +
                    "-PdesktopMakeAppxPath=<path-to-makeappx.exe>."
            )
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        if (output.exists() && !output.delete()) {
            throw GradleException("Could not replace existing MSIX at ${output.absolutePath}.")
        }

        execOperations.exec {
            executable = makeAppx.absolutePath
            args(
                "pack",
                "/d",
                packageRootDirectory.get().asFile.absolutePath,
                "/p",
                output.absolutePath,
                "/o"
            )
        }
    }
}

@DisableCachingByDefault(because = "Signs the MSIX package with Windows SDK signtool.")
abstract class SignDesktopMsixTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val unsignedMsixFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val certificateFile: RegularFileProperty

    @get:Input
    abstract val signToolPath: Property<String>

    @get:Input
    abstract val certificatePassword: Property<String>

    @get:Input
    abstract val timestampUrl: Property<String>

    @TaskAction
    fun signMsix() {
        val signTool = File(signToolPath.get())
        if (!signTool.isFile) {
            throw GradleException(
                "Windows SDK signtool.exe was not found at ${signTool.absolutePath}. " +
                    "Install the Windows SDK or set -PdesktopSignToolPath=<path-to-signtool.exe>."
            )
        }

        val signArgs = mutableListOf(
            "sign",
            "/fd",
            "SHA256",
            "/f",
            certificateFile.get().asFile.absolutePath
        )
        val password = certificatePassword.get().trim()
        if (password.isNotEmpty()) {
            signArgs += listOf("/p", password)
        }
        val timestamp = timestampUrl.get().trim()
        if (timestamp.isNotEmpty()) {
            signArgs += listOf("/tr", timestamp, "/td", "SHA256")
        }
        signArgs += unsignedMsixFile.get().asFile.absolutePath

        execOperations.exec {
            executable = signTool.absolutePath
            args(signArgs)
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

@DisableCachingByDefault(because = "Generates AUR package metadata from the local Linux distributable.")
abstract class PrepareDesktopAurPackageTask : DefaultTask() {
    @get:Input
    abstract val aurPackageName: Property<String>

    @get:Input
    abstract val providedPackageName: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val packageRelease: Property<String>

    @get:Input
    abstract val packageDescription: Property<String>

    @get:Input
    abstract val appDisplayName: Property<String>

    @get:Input
    abstract val installDirectoryName: Property<String>

    @get:Input
    abstract val launcherName: Property<String>

    @get:Input
    abstract val executableName: Property<String>

    @get:Input
    abstract val sourceUrl: Property<String>

    @get:Input
    abstract val projectUrl: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val linuxTarFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val output = outputDirectory.get().asFile
        val sourceTar = linuxTarFile.get().asFile
        if (!sourceTar.isFile) {
            throw GradleException("Missing Linux tarball for AUR packaging: ${sourceTar.absolutePath}")
        }

        output.deleteRecursively()
        output.mkdirs()

        val stagedTar = output.resolve(sourceTar.name)
        sourceTar.copyTo(stagedTar, overwrite = true)
        val sha256 = stagedTar.sha256()
        val configuredSourceUrl = sourceUrl.get().trim()
        val sourceEntry = if (configuredSourceUrl.isBlank()) {
            stagedTar.name
        } else {
            "${stagedTar.name}::$configuredSourceUrl"
        }

        output.resolve("PKGBUILD").writeText(
            aurPkgbuild(
                pkgname = aurPackageName.get(),
                providedPackage = providedPackageName.get(),
                pkgver = packageVersion.get(),
                pkgrel = packageRelease.get(),
                pkgdesc = packageDescription.get(),
                appName = appDisplayName.get(),
                installDir = installDirectoryName.get(),
                launcher = launcherName.get(),
                executable = executableName.get(),
                source = sourceEntry,
                sha256 = sha256,
                projectUrl = projectUrl.get()
            )
        )
        output.resolve(".SRCINFO").writeText(
            aurSrcInfo(
                pkgname = aurPackageName.get(),
                providedPackage = providedPackageName.get(),
                pkgver = packageVersion.get(),
                pkgrel = packageRelease.get(),
                pkgdesc = packageDescription.get(),
                source = sourceEntry,
                sha256 = sha256,
                projectUrl = projectUrl.get()
            )
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun shellSingleQuoted(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun archRuntimeDependencies(): List<String> {
        return listOf(
            "alsa-lib",
            "atk",
            "cairo",
            "dbus",
            "expat",
            "fontconfig",
            "freetype2",
            "gcc-libs",
            "gdk-pixbuf2",
            "glib2",
            "glibc",
            "gtk3",
            "libcups",
            "libarchive",
            "libsecret",
            "libx11",
            "libxcomposite",
            "libxdamage",
            "libxext",
            "libxi",
            "libxrandr",
            "libxrender",
            "libxtst",
            "nss",
            "pango",
            "zlib"
        )
    }

    private fun aurPkgbuild(
        pkgname: String,
        providedPackage: String,
        pkgver: String,
        pkgrel: String,
        pkgdesc: String,
        appName: String,
        installDir: String,
        launcher: String,
        executable: String,
        source: String,
        sha256: String,
        projectUrl: String
    ): String {
        val desktopFile = "$providedPackage.desktop"
        val iconName = providedPackage
        val depends = archRuntimeDependencies()
        val mimeTypes = archDesktopMimeTypes()
        return """
pkgname=${shellSingleQuoted(pkgname)}
pkgver=${shellSingleQuoted(pkgver)}
pkgrel=${shellSingleQuoted(pkgrel)}
pkgdesc=${shellSingleQuoted(pkgdesc)}
arch=('x86_64')
url=${shellSingleQuoted(projectUrl)}
license=('AGPL-3.0-only')
depends=(${depends.joinToString(" ") { shellSingleQuoted(it) }})
provides=(${shellSingleQuoted(providedPackage)})
conflicts=(${shellSingleQuoted(providedPackage)})
source=(${shellSingleQuoted(source)})
sha256sums=(${shellSingleQuoted(sha256)})
options=('!debug')

package() {
  install -dm755 "${'$'}pkgdir/opt/$installDir"
  cp -a "$installDir/." "${'$'}pkgdir/opt/$installDir/"
  chmod 755 "${'$'}pkgdir/opt/$installDir/bin/$executable"

  install -dm755 "${'$'}pkgdir/usr/bin"
  ln -sf "/opt/$installDir/bin/$executable" "${'$'}pkgdir/usr/bin/$launcher"

  install -Dm644 "${'$'}pkgdir/opt/$installDir/share/licenses/LICENSE" "${'$'}pkgdir/usr/share/licenses/${'$'}pkgname/LICENSE"

  local icon_path
  icon_path="${'$'}(find "${'$'}pkgdir/opt/$installDir" -name 'episteme_icon.png' -print -quit)"
  if [[ -n "${'$'}icon_path" ]]; then
    install -Dm644 "${'$'}icon_path" "${'$'}pkgdir/usr/share/icons/hicolor/512x512/apps/$iconName.png"
    install -Dm644 "${'$'}icon_path" "${'$'}pkgdir/usr/share/pixmaps/$iconName.png"
  fi

  install -Dm644 /dev/stdin "${'$'}pkgdir/usr/share/applications/$desktopFile" <<'EOF'
[Desktop Entry]
Type=Application
Name=$appName
Comment=$pkgdesc
Exec=$launcher %F
Icon=$iconName
Terminal=false
Categories=Office;Viewer;
MimeType=${mimeTypes.joinToString(";")};
EOF
}
""".trimIndent() + "\n"
    }

    private fun aurSrcInfo(
        pkgname: String,
        providedPackage: String,
        pkgver: String,
        pkgrel: String,
        pkgdesc: String,
        source: String,
        sha256: String,
        projectUrl: String
    ): String {
        val depends = archRuntimeDependencies()
        return """
pkgbase = $pkgname
	pkgdesc = $pkgdesc
	pkgver = $pkgver
	pkgrel = $pkgrel
	url = $projectUrl
	arch = x86_64
	license = AGPL-3.0-only
${depends.joinToString("\n") { "\tdepends = $it" }}
	provides = $providedPackage
	conflicts = $providedPackage
	source = $source
	sha256sums = $sha256

pkgname = $pkgname
""".trimIndent() + "\n"
    }

    private fun archDesktopMimeTypes(): List<String> {
        return listOf(
            "application/pdf",
            "application/epub+zip",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/vnd.amazon.mobi8-ebook",
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "text/html",
            "application/xhtml+xml",
            "application/x-fictionbook+xml",
            "application/x-zip-compressed-fb2",
            "application/zip",
            "application/vnd.comicbook+zip",
            "application/x-cbz",
            "application/vnd.comicbook-rar",
            "application/x-cbr",
            "application/x-rar-compressed",
            "application/x-cb7",
            "application/x-7z-compressed",
            "application/vnd.comicbook+tar",
            "application/x-cbt",
            "application/x-tar",
            "application/tar",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/x-vnd.oasis.opendocument.text-flat-xml"
        )
    }
}


@DisableCachingByDefault(because = "Generates Flatpak metadata from the local Linux distributable.")
abstract class PrepareDesktopFlatpakPackageTask : DefaultTask() {
    @get:Input
    abstract val appId: Property<String>

    @get:Input
    abstract val runtimeVersion: Property<String>

    @get:Input
    abstract val packageVersion: Property<String>

    @get:Input
    abstract val packageDescription: Property<String>

    @get:Input
    abstract val appDisplayName: Property<String>

    @get:Input
    abstract val installDirectoryName: Property<String>

    @get:Input
    abstract val launcherName: Property<String>

    @get:Input
    abstract val executableName: Property<String>

    @get:Input
    abstract val projectUrl: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distributableDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val iconFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val licenseFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val appIdValue = appId.get()
        val output = outputDirectory.get().asFile
        val sourceDir = output.resolve("sources")
        val appDir = sourceDir.resolve("app")
        val metadataDir = sourceDir.resolve("metadata")
        val manifestFile = output.resolve("$appIdValue.yml")

        output.deleteRecursively()
        appDir.mkdirs()
        metadataDir.mkdirs()

        distributableDirectory.get().asFile.copyRecursively(appDir, overwrite = true)
        iconFile.get().asFile.copyTo(metadataDir.resolve("$appIdValue.png"), overwrite = true)
        licenseFile.get().asFile.copyTo(metadataDir.resolve("LICENSE"), overwrite = true)
        metadataDir.resolve("$appIdValue.desktop").writeText(flatpakDesktopEntry(), Charsets.UTF_8)
        metadataDir.resolve("$appIdValue.metainfo.xml").writeText(flatpakMetaInfo(), Charsets.UTF_8)
        manifestFile.writeText(flatpakManifest(sourceDir), Charsets.UTF_8)
    }

    private fun flatpakManifest(sourceDir: File): String {
        val appIdValue = appId.get()
        val appSourcePath = sourceDir.resolve("app").absolutePath.replace(File.separatorChar, '/')
        val metadataSourcePath = sourceDir.resolve("metadata").absolutePath.replace(File.separatorChar, '/')
        val quotedExecutable = shellSingleQuoted(executableName.get())
        return """
app-id: $appIdValue
runtime: org.freedesktop.Platform
runtime-version: '${runtimeVersion.get()}'
sdk: org.freedesktop.Sdk
command: ${launcherName.get()}
finish-args:
  - --share=ipc
  - --socket=fallback-x11
  - --socket=wayland
  - --socket=pulseaudio
  - --device=dri
  - --share=network
  - --filesystem=home
modules:
  - name: episteme-desktop
    buildsystem: simple
    build-commands:
      - install -dm755 /app/${installDirectoryName.get()}
      - cp -a app/. /app/${installDirectoryName.get()}/
      - chmod 755 /app/${installDirectoryName.get()}/bin/$quotedExecutable
      - install -dm755 /app/bin
      - ln -sf /app/${installDirectoryName.get()}/bin/$quotedExecutable /app/bin/${launcherName.get()}
      - install -Dm644 metadata/$appIdValue.desktop /app/share/applications/$appIdValue.desktop
      - install -Dm644 metadata/$appIdValue.metainfo.xml /app/share/metainfo/$appIdValue.metainfo.xml
      - install -Dm644 metadata/$appIdValue.png /app/share/icons/hicolor/512x512/apps/$appIdValue.png
      - install -Dm644 metadata/LICENSE /app/share/licenses/$appIdValue/LICENSE
    sources:
      - type: dir
        path: $appSourcePath
        dest: app
      - type: dir
        path: $metadataSourcePath
        dest: metadata
""".trimIndent() + "\n"
    }

    private fun flatpakDesktopEntry(): String {
        return """
[Desktop Entry]
Type=Application
Name=${appDisplayName.get()}
Comment=${packageDescription.get()}
Exec=${launcherName.get()} %F
Icon=${appId.get()}
Terminal=false
Categories=Office;Viewer;
MimeType=${flatpakDesktopMimeTypes().joinToString(";")};
""".trimIndent() + "\n"
    }

    private fun flatpakMetaInfo(): String {
        val appIdValue = appId.get()
        val version = packageVersion.get()
        val escapedName = xmlEscaped(appDisplayName.get())
        val escapedDescription = xmlEscaped(packageDescription.get())
        val escapedProjectUrl = xmlEscaped(projectUrl.get())
        return """
<?xml version="1.0" encoding="UTF-8"?>
<component type="desktop-application">
  <id>$appIdValue</id>
  <metadata_license>CC0-1.0</metadata_license>
  <project_license>AGPL-3.0-only</project_license>
  <name>$escapedName</name>
  <summary>$escapedDescription</summary>
  <description>
    <p>$escapedDescription with support for EPUB, PDF, comics, documents, presentations, OPDS, and local libraries.</p>
  </description>
  <launchable type="desktop-id">$appIdValue.desktop</launchable>
  <url type="homepage">$escapedProjectUrl</url>
  <categories>
    <category>Office</category>
    <category>Viewer</category>
  </categories>
  <provides>
    <binary>${xmlEscaped(executableName.get())}</binary>
  </provides>
  <releases>
    <release version="$version" date="${LocalDate.now()}" />
  </releases>
</component>
""".trimIndent() + "\n"
    }

    private fun shellSingleQuoted(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun flatpakDesktopMimeTypes(): List<String> {
        return listOf(
            "application/pdf",
            "application/epub+zip",
            "application/x-mobipocket-ebook",
            "application/vnd.amazon.ebook",
            "application/vnd.amazon.mobi8-ebook",
            "text/markdown",
            "text/x-markdown",
            "text/plain",
            "text/html",
            "application/xhtml+xml",
            "application/x-fictionbook+xml",
            "application/x-zip-compressed-fb2",
            "application/zip",
            "application/vnd.comicbook+zip",
            "application/x-cbz",
            "application/vnd.comicbook-rar",
            "application/x-cbr",
            "application/x-rar-compressed",
            "application/x-cb7",
            "application/x-7z-compressed",
            "application/vnd.comicbook+tar",
            "application/x-cbt",
            "application/x-tar",
            "application/tar",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/x-vnd.oasis.opendocument.text-flat-xml"
        )
    }

    private fun xmlEscaped(value: String): String {
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

@DisableCachingByDefault(because = "Runs flatpak-builder to package a local Flatpak bundle.")
abstract class PackageDesktopFlatpakTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifestFile: RegularFileProperty

    @get:Input
    abstract val appId: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:OutputDirectory
    abstract val repoDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val buildDirectory: DirectoryProperty

    @TaskAction
    fun packageFlatpak() {
        val repo = repoDirectory.get().asFile
        val build = buildDirectory.get().asFile
        val output = outputFile.get().asFile

        build.deleteRecursively()
        output.parentFile.mkdirs()
        if (output.exists() && !output.delete()) {
            throw GradleException("Could not replace existing Flatpak bundle at ${output.absolutePath}.")
        }

        execOperations.exec {
            commandLine(
                "flatpak-builder",
                "--force-clean",
                "--repo=${repo.absolutePath}",
                build.absolutePath,
                manifestFile.get().asFile.absolutePath
            )
        }
        execOperations.exec {
            commandLine(
                "flatpak",
                "build-bundle",
                repo.absolutePath,
                output.absolutePath,
                appId.get()
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

fun normalizeDesktopMsixVersion(rawVersion: String): String {
    val parts = rawVersion.trim().split('.')
    if (parts.size !in 3..4 || parts.any { it.isBlank() || it.all(Char::isDigit).not() }) {
        throw GradleException(
            "desktopMsixVersion must be a numeric Windows package version with three or four parts, " +
                "for example 1.0.1 or 1.0.1.0."
        )
    }
    val normalized = if (parts.size == 3) parts + "0" else parts
    normalized.forEach { part ->
        val value = part.toIntOrNull()
        if (value == null || value !in 0..65535) {
            throw GradleException("desktopMsixVersion part '$part' is outside the MSIX range 0..65535.")
        }
    }
    return normalized.joinToString(".")
}

fun normalizeDesktopMsixIdentityName(rawName: String): String {
    val normalized = rawName.trim()
    if (!Regex("[A-Za-z0-9][A-Za-z0-9.-]{2,49}").matches(normalized)) {
        throw GradleException(
            "desktopMsixIdentityName must be 3-50 characters using letters, numbers, dots, or hyphens."
        )
    }
    return normalized
}

fun desktopMsixArchitecture(osArch: String = System.getProperty("os.arch")): String {
    return when (desktopArchId(osArch)) {
        "x64" -> "x64"
        "arm64" -> "arm64"
        "x86" -> "x86"
        else -> "neutral"
    }
}

fun latestExistingFile(candidates: List<File>): File? {
    return candidates.filter { it.isFile }.maxByOrNull { it.absolutePath }
}

fun windowsSdkToolCandidates(toolName: String): List<File> {
    val roots = listOfNotNull(
        System.getenv("WindowsSdkDir")?.let(::File),
        File("C:/Program Files (x86)/Windows Kits/10"),
        File("C:/Program Files/Windows Kits/10"),
        File("C:/Program Files (x86)/Windows Kits/10/App Certification Kit"),
        File("C:/Program Files/Windows Kits/10/App Certification Kit")
    ).distinctBy { it.absolutePath.lowercase() }
    val sdkBins = roots.flatMap { root ->
        safeChildDirectories(root.resolve("bin")).flatMap { versionDir ->
            listOf(
                versionDir.resolve("x64/$toolName.exe"),
                versionDir.resolve("x86/$toolName.exe"),
                versionDir.resolve(toolName)
            )
        }
    }
    val directBins = roots.map { root -> root.resolve("$toolName.exe") }
    val pathBins = (System.getenv("PATH") ?: "")
        .split(File.pathSeparator)
        .filter { it.isNotBlank() }
        .map { File(it).resolve("$toolName.exe") }
    return sdkBins + directBins + pathBins
}

fun findWindowsSdkTool(toolName: String, explicitPath: String?): File {
    val explicit = explicitPath?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
    if (explicit != null) return explicit
    return latestExistingFile(windowsSdkToolCandidates(toolName))
        ?: File(rootProject.projectDir, "__missing_windows_sdk_tool__/$toolName.exe")
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
val desktopPackageName = "Episteme"
val desktopLinuxPackageName = if (isOssOfflineDesktop) "episteme-oss" else "episteme"
val desktopPackageDescription = if (isOssOfflineDesktop) {
    "Episteme oss offline desktop reader"
} else {
    "Episteme desktop reader"
}
val desktopVendor = providers.gradleProperty("desktopVendor").orElse("Aryan")
val desktopVendorName = desktopVendor.get()
val desktopProjectUrl = providers.gradleProperty("desktopProjectUrl")
    .orElse("https://github.com/Aryan-Raj3112/episteme")
val desktopOsName = System.getProperty("os.name")
val desktopOsArch = System.getProperty("os.arch")
val desktopPackageArchitecture = normalizeDesktopPackageArchitecture(desktopOsArch)
val desktopAurPackageName = providers.gradleProperty("desktopAurPackageName")
    .orElse(if (isOssOfflineDesktop) "episteme-oss-bin" else "episteme-bin")
val desktopAurPackageRelease = providers.gradleProperty("desktopAurPackageRelease")
    .orElse("1")
val desktopAurSourceUrl = providers.gradleProperty("desktopAurSourceUrl")
    .orElse("")
val desktopFlatpakAppId = providers.gradleProperty("desktopFlatpakAppId")
    .orElse(if (isOssOfflineDesktop) "io.github.Aryan_Raj3112.episteme_oss" else "io.github.Aryan_Raj3112.episteme")
val desktopFlatpakRuntimeVersion = providers.gradleProperty("desktopFlatpakRuntimeVersion")
    .orElse("25.08")
val desktopPackageTargetFormats = providers.gradleProperty("desktopPackageFormats")
    .orElse(desktopDefaultPackageFormats(desktopOsName))
    .map { normalizeDesktopPackageFormats(it, desktopOsName) }
    .get()
val desktopMsixIdentityName = providers.gradleProperty("desktopMsixIdentityName")
    .orElse(if (isOssOfflineDesktop) "Aryan.EpistemeOss" else "Aryan.Episteme")
    .map(::normalizeDesktopMsixIdentityName)
    .get()
val desktopMsixPublisher = providers.gradleProperty("desktopMsixPublisher")
    .orElse("CN=$desktopVendorName")
val desktopMsixPublisherDisplayName = providers.gradleProperty("desktopMsixPublisherDisplayName")
    .orElse(desktopVendor)
val desktopMsixVersion = providers.gradleProperty("desktopMsixVersion")
    .orElse(desktopPackageVersion)
    .map(::normalizeDesktopMsixVersion)
    .get()
val desktopMsixArchitecture = desktopMsixArchitecture(desktopOsArch)
val desktopMakeAppxPath = providers.gradleProperty("desktopMakeAppxPath").orNull
val desktopSignToolPath = providers.gradleProperty("desktopSignToolPath").orNull
val desktopMsixCertificatePath = providers.gradleProperty("desktopMsixCertificatePath").orNull
val desktopMsixCertificatePassword = providers.gradleProperty("desktopMsixCertificatePassword")
    .orElse("")
val desktopMsixTimestampUrl = providers.gradleProperty("desktopMsixTimestampUrl")
    .orElse("http://timestamp.digicert.com")
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

val desktopDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main/app/$desktopPackageName")
val desktopReleaseDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main-release/app/$desktopPackageName")
val desktopLinuxTarFileName = "${desktopLinuxPackageName}-${desktopPackageVersion.get()}-linux-$desktopPackageArchitecture.tar.gz"
val desktopAurOutputDir = layout.buildDirectory.dir("aur/${desktopAurPackageName.get()}")
val desktopFlatpakOutputDir = layout.buildDirectory.dir("flatpak/${desktopFlatpakAppId.get()}")
val desktopFlatpakManifestFile = desktopFlatpakOutputDir.map { it.file("${desktopFlatpakAppId.get()}.yml") }
val desktopFlatpakRepoDir = layout.buildDirectory.dir("flatpak/repo/${desktopFlatpakAppId.get()}")
val desktopFlatpakBuildDir = layout.buildDirectory.dir("flatpak/build/${desktopFlatpakAppId.get()}")
val desktopFlatpakBundleFile = layout.buildDirectory.file(
    "compose/binaries/main/flatpak/${desktopLinuxPackageName}-${desktopPackageVersion.get()}-linux-$desktopPackageArchitecture.flatpak"
)
val desktopMsixPackageDir = layout.buildDirectory.dir("msix/package")
val desktopMsixAssetsDir = layout.buildDirectory.dir("msix/generated/assets")
val desktopMsixManifestFile = layout.buildDirectory.file("msix/generated/AppxManifest.xml")
val desktopMsixOutputFile = layout.buildDirectory.file(
    "compose/binaries/main-release/msix/${desktopLinuxPackageName}-${desktopPackageVersion.get()}-windows-$desktopPackageArchitecture.msix"
)

val packageLinuxTar by tasks.registering(Tar::class) {
    group = "distribution"
    description = "Packages the Linux desktop distributable as a tar.gz for Arch/AUR packaging."
    dependsOn("createDistributable")

    archiveFileName.set(desktopLinuxTarFileName)
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/linux-tar"))
    compression = Compression.GZIP

    from(desktopDistributableAppDir) {
        into(desktopLinuxPackageName)
    }
    from(desktopLinuxIconFile) {
        into("$desktopLinuxPackageName/share")
    }
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("$desktopLinuxPackageName/share/licenses")
    }
}

val prepareAurPackage by tasks.registering(PrepareDesktopAurPackageTask::class) {
    group = "distribution"
    description = "Generates a local AUR package directory with PKGBUILD and .SRCINFO."
    dependsOn(packageLinuxTar)

    aurPackageName.set(desktopAurPackageName)
    providedPackageName.set(desktopLinuxPackageName)
    packageVersion.set(desktopPackageVersion)
    packageRelease.set(desktopAurPackageRelease)
    packageDescription.set(desktopPackageDescription)
    appDisplayName.set(desktopPackageName)
    installDirectoryName.set(desktopLinuxPackageName)
    launcherName.set(desktopLinuxPackageName)
    executableName.set(desktopPackageName)
    sourceUrl.set(desktopAurSourceUrl)
    projectUrl.set(desktopProjectUrl)
    linuxTarFile.set(packageLinuxTar.flatMap { it.archiveFile })
    outputDirectory.set(desktopAurOutputDir)
}

tasks.register<Exec>("packageAur") {
    group = "distribution"
    description = "Builds the generated AUR package with makepkg. Run this on Arch Linux."
    dependsOn(prepareAurPackage)

    commandLine("makepkg", "-sf", "--cleanbuild")
    workingDir = desktopAurOutputDir.get().asFile
}

val prepareFlatpakPackage by tasks.registering(PrepareDesktopFlatpakPackageTask::class) {
    group = "distribution"
    description = "Generates a local Flatpak manifest and metadata from the Linux desktop distributable."
    dependsOn("createDistributable")

    appId.set(desktopFlatpakAppId)
    runtimeVersion.set(desktopFlatpakRuntimeVersion)
    packageVersion.set(desktopPackageVersion)
    packageDescription.set(desktopPackageDescription)
    appDisplayName.set(desktopPackageName)
    installDirectoryName.set(desktopLinuxPackageName)
    launcherName.set(desktopLinuxPackageName)
    executableName.set(desktopPackageName)
    projectUrl.set(desktopProjectUrl)
    distributableDirectory.set(desktopDistributableAppDir)
    iconFile.set(desktopLinuxIconFile)
    licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
    outputDirectory.set(desktopFlatpakOutputDir)
}

val packageFlatpak by tasks.registering(PackageDesktopFlatpakTask::class) {
    group = "distribution"
    description = "Builds a single-file Flatpak bundle with flatpak-builder. Run this on Linux with Flatpak tooling installed."
    dependsOn(prepareFlatpakPackage)

    manifestFile.set(desktopFlatpakManifestFile)
    appId.set(desktopFlatpakAppId)
    outputFile.set(desktopFlatpakBundleFile)
    repoDirectory.set(desktopFlatpakRepoDir)
    buildDirectory.set(desktopFlatpakBuildDir)
}

val generateDesktopMsixManifest by tasks.registering(GenerateDesktopMsixManifestTask::class) {
    identityName.set(desktopMsixIdentityName)
    publisher.set(desktopMsixPublisher)
    publisherDisplayName.set(desktopMsixPublisherDisplayName)
    packageName.set(desktopPackageName)
    packageDescription.set(desktopPackageDescription)
    packageVersion.set(desktopMsixVersion)
    architecture.set(desktopMsixArchitecture)
    executablePath.set("$desktopPackageName.exe")
    outputFile.set(desktopMsixManifestFile)
}

val generateDesktopMsixAssets by tasks.registering(GenerateDesktopMsixAssetsTask::class) {
    sourceIconFile.set(desktopLinuxIconFile)
    outputDirectory.set(desktopMsixAssetsDir)
}

val prepareReleaseMsixPackage by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages the release Windows app image and MSIX metadata for makeappx."
    dependsOn("createReleaseDistributable", generateDesktopMsixManifest, generateDesktopMsixAssets)

    from(desktopReleaseDistributableAppDir)
    from(desktopMsixManifestFile)
    from(desktopMsixAssetsDir) {
        into("Assets")
    }
    into(desktopMsixPackageDir)
}

val packageReleaseMsix by tasks.registering(PackageDesktopMsixTask::class) {
    group = "distribution"
    description = "Packages the release Windows app image as an MSIX using Windows SDK makeappx."
    dependsOn(prepareReleaseMsixPackage)

    val makeAppx = findWindowsSdkTool("makeappx", desktopMakeAppxPath)
    packageRootDirectory.set(desktopMsixPackageDir)
    outputFile.set(desktopMsixOutputFile)
    makeAppxPath.set(makeAppx.absolutePath)
    hostOsId.set(desktopOsId(desktopOsName))
    hostArchId.set(desktopArchId(desktopOsArch))
}

val signReleaseMsix = desktopMsixCertificatePath?.trim()?.takeIf { it.isNotEmpty() }?.let { certificatePath ->
    tasks.register<SignDesktopMsixTask>("signReleaseMsix") {
        group = "distribution"
        description = "Signs the release MSIX with signtool when -PdesktopMsixCertificatePath is configured."
        dependsOn(packageReleaseMsix)

        val signTool = findWindowsSdkTool("signtool", desktopSignToolPath)
        val resolvedCertificateFile = File(certificatePath).let { file ->
            if (file.isAbsolute) file else project.file(certificatePath)
        }
        unsignedMsixFile.set(desktopMsixOutputFile)
        certificateFile.set(resolvedCertificateFile)
        signToolPath.set(signTool.absolutePath)
        certificatePassword.set(desktopMsixCertificatePassword)
        timestampUrl.set(desktopMsixTimestampUrl)
    }
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
                packageName = desktopLinuxPackageName
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
        "packageReleaseMsix",
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
        "prepareReleaseMsixPackage",
        "packageReleaseMsix",
        "signReleaseMsix",
        "packageDeb",
        "packageReleaseDeb",
        "packageRpm",
        "packageReleaseRpm",
        "packageLinuxTar",
        "prepareAurPackage",
        "packageAur",
        "prepareFlatpakPackage",
        "packageFlatpak",
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
