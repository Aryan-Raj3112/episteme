plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm("desktop")
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
