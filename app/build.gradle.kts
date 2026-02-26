@file:Suppress("UnstableApiUsage")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.20"
    alias(libs.plugins.kotlin.ksp)
    id("com.diffplug.spotless") version "8.2.1"
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "com.aryan.reader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aryan.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 33
        versionName = "1.0.32"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
        buildConfigField("boolean", "IS_PRO", "false")
    }

    flavorDimensions += "version"
    productFlavors {
        create("oss") {
            dimension = "version"
            applicationIdSuffix = ".oss"
            versionNameSuffix = "-oss"
            buildConfigField("String", "AI_WORKER_URL", "\"\"")
            buildConfigField("String", "VERIFIER_WORKER_URL", "\"\"")
            buildConfigField("String", "FEEDBACK_WORKER_URL", "\"\"")
            buildConfigField("boolean", "IS_PRO", "false")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        create("release") {
            val storePath = localProperties.getProperty("MYAPP_RELEASE_STORE_FILE")
            if (!storePath.isNullOrEmpty()) {
                storeFile = file(storePath)
                storePassword = localProperties.getProperty("MYAPP_RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("MYAPP_RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("MYAPP_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            val flavor = variant.productFlavors.getOrNull(0)?.name ?: ""
            val buildType = variant.buildType.name
            val version = variant.versionName

            output.outputFileName = "Episteme-$flavor-v$version-$buildType.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    publishing {
        singleVariant("release") {
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
//noinspection UseTomlInstead
dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size.class1.android)
    implementation(libs.androidx.credentials)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.7.0")
    androidTestImplementation("com.google.truth:truth:1.4.2")
    androidTestImplementation("androidx.navigation:navigation-testing:2.9.6")
    androidTestImplementation("io.mockk:mockk-android:1.13.11") {
        exclude(group = "org.junit.jupiter")
    }
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)

    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    implementation("androidx.appcompat:appcompat:1.7.1")

    //noinspection GradleDependency (Updating these might cause the custom toolbox in pagination to break)
    implementation("androidx.navigation:navigation-compose:2.9.2")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    //noinspection GradleDependency
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    //noinspection GradleDependency
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0-alpha11")

    implementation("org.jsoup:jsoup:1.17.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.6.0")

    implementation(project(":pdfiumandroid"))

    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")

    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.compose.runtime:runtime-livedata:1.9.3")

    implementation("org.slf4j:slf4j-android:1.7.36")

    implementation("org.commonmark:commonmark:0.22.0")

    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("androidx.room:room-paging:2.7.1")

    // Flexmark for Markdown parsing (MD -> HTML)
    implementation("com.vladsch.flexmark:flexmark:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-tables:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-strikethrough:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-gfm-tasklist:0.64.8")
    implementation("com.vladsch.flexmark:flexmark-ext-autolink:0.64.8")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.browser:browser:1.8.0")
}

spotless {
    kotlin {
        target("src/main/java/**/*.kt", "src/main/kotlin/**/*.kt")
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
    cpp {
        target("src/main/cpp/mobi_jni_bridge.c", "src/main/cpp/Woff2Converter.cpp")
        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
    }
}
