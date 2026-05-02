import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("net.java.dev.jna:jna:5.17.0")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aryan.reader.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Episteme"
            packageVersion = "1.0.0"
            description = "Episteme desktop shell"
            vendor = "Aryan Reader"
        }
    }
}
