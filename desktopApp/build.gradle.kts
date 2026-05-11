import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
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

    @TaskAction
    fun checkRuntime() {
        val bundleRoot = bundleDir.get().asFile
        val missingFiles = listOf("jcef.dll", "libcef.dll")
            .filterNot { bundleRoot.resolve(it).isFile }
        if (missingFiles.isNotEmpty()) {
            throw GradleException(
                "Missing bundled KCEF runtime at ${bundleRoot.absolutePath}. " +
                    "Expected ${missingFiles.joinToString()} for both standard and oss-offline desktop packages."
            )
        }
    }
}

val desktopFlavor = providers.gradleProperty("desktopFlavor").orElse("standard").get().lowercase()
val isOssOfflineDesktop = desktopFlavor == "oss-offline"
val generatedDesktopResourcesDir = layout.buildDirectory.dir("generated/desktopAppResources")
val bundledWebViewDir = layout.projectDirectory.dir("kcef-bundle")
val desktopWindowsIconFile = layout.projectDirectory.file("src/desktopMain/resources/episteme.ico")

val checkBundledWebViewRuntime by tasks.registering(CheckBundledWebViewRuntimeTask::class) {
    bundleDir.set(bundledWebViewDir)
}

val prepareBundledDesktopResources by tasks.registering(Sync::class) {
    dependsOn(checkBundledWebViewRuntime)
    from(bundledWebViewDir) {
        into("kcef-bundle")
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

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = if (isOssOfflineDesktop) "Episteme OSS Offline" else "Episteme"
            packageVersion = "1.0.0"
            description = if (isOssOfflineDesktop) {
                "Episteme desktop offline shell"
            } else {
                "Episteme desktop shell"
            }
            vendor = "Aryan Reader"
            appResourcesRootDir.set(generatedDesktopResourcesDir)
            windows {
                iconFile.set(desktopWindowsIconFile)
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
    jvmArgs("-Depisteme.desktop.flavor=$desktopFlavor")
    if (System.getProperty("os.name").contains("Mac")) {
        jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
    }
}

tasks.matching {
    it.name in setOf(
        "createDistributable",
        "prepareAppResources",
        "packageDistributionForCurrentOS",
        "packageExe",
        "packageMsi"
    )
}.configureEach {
    dependsOn(prepareBundledDesktopResources)
}
