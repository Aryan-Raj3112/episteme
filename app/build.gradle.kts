@file:Suppress("UnstableApiUsage")

import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.xml.sax.InputSource

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
    id("com.diffplug.spotless") version "8.2.1"
    alias(libs.plugins.kover)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// Pinned so every machine (dev, CI, release pipeline) links the office_oxide crate
// with the same NDK clang. CI greps this line and installs that NDK before Gradle
// runs, because the cargo target linker is resolved at configuration time.
val androidNdkVersion = "28.2.13676358"

fun configuredAppLocaleTags(): Set<String> {
    val localesConfigXml = providers
        .fileContents(layout.projectDirectory.file("src/main/res/xml/locales_config.xml"))
        .asText
        .get()
    val androidNamespace = "http://schemas.android.com/apk/res/android"
    val document = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(InputSource(StringReader(localesConfigXml)))
    val localeNodes = document.getElementsByTagName("locale")

    return buildSet {
        for (index in 0 until localeNodes.length) {
            val name = localeNodes.item(index)
                .attributes
                ?.getNamedItemNS(androidNamespace, "name")
                ?.nodeValue
                ?.takeIf { it.isNotBlank() }
            if (name != null) add(name)
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

android {
    namespace = "com.aryan.reader"
    compileSdk = 37
    compileSdkMinor = 0
    ndkVersion = androidNdkVersion

    defaultConfig {
        applicationId = "com.aryan.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 65
        versionName = "1.0.59"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += ""
            }
        }
        buildConfigField("boolean", "IS_PRO", "false")
        buildConfigField("boolean", "IS_OFFLINE", "false")
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
            buildConfigField("String", "TTS_WORKER_URL", "\"\"")
        }
    }

    packaging {
        jniLibs {
            excludes += "**/libbrotlienc.so"
            excludes += "**/libbrotlidec.so"
            excludes += "**/libbrotlicommon.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/services/io.grpc.LoadBalancerProvider"
            excludes += "composeResources/**"
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
            val storePath = localProperties.getProperty("MYAPP_RELEASE_STORE_FILE")
            if (!storePath.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("releaseOffline") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "IS_OFFLINE", "true")
            buildConfigField("String", "TTS_WORKER_URL", "\"\"")
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    androidResources {
        localeFilters += configuredAppLocaleTags()
            .map { it.toAndroidResourceConfiguration() }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "4g"
            it.jvmArgs("-Xss2m")
            // The resource-contract tests (launcher icon, string resources) read src/main/res
            // straight off disk rather than through the merged resources, so Gradle sees no
            // input change when only an asset does and skips them as up-to-date. That leaves
            // the launcher icon's own contract green against an icon it no longer describes,
            // which is the one case it exists to catch -- regenerating the icon changes nothing
            // else the test task depends on.
            it.inputs.dir(layout.projectDirectory.dir("src/main/res"))
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .optional()
        }
    }
    configurations {
        named("testImplementation") {
            exclude(group = "org.slf4j", module = "slf4j-android")
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val flavor = variant.productFlavors.firstOrNull()?.second.orEmpty()
        val buildType = variant.buildType.orEmpty()
        val versionName = variant.outputs.single().versionName
        val taskName = "copy${variant.name.replaceFirstChar { it.uppercase() }}Apk"

        val copyApk = tasks.register<Copy>(taskName) {
            from(variant.artifacts.get(SingleArtifact.APK))
            include("*.apk")
            into(layout.buildDirectory.dir("outputs/renamed-apk/${variant.name}"))
            rename {
                "Episteme-$flavor-v${versionName.get()}-$buildType.apk"
            }
        }

        val assembleTaskName = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
        tasks.configureEach {
            if (name == assembleTaskName) {
                finalizedBy(copyApk)
            }
        }
    }
}

fun String.toAndroidResourceConfiguration(): String {
    if (this == "id") return "in"

    val languageTagParts = split("-")
    return if (languageTagParts.size == 2 && languageTagParts[1].length == 2) {
        "${languageTagParts[0]}-r${languageTagParts[1].uppercase()}"
    } else {
        this
    }
}

// ===================================================================
// office_oxide_bridge (Rust) — see rust/office-oxide-bridge
// ===================================================================
// Cargo target triple per Android ABI this app ships. x86_64 is included so
// debug builds on an x86_64 emulator keep working; the release packaging step
// still strips x86 ABIs (see the androidComponents block below).
val officeOxideAbis = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "armeabi-v7a" to "armv7-linux-androideabi",
    "x86_64" to "x86_64-linux-android",
    // 32-bit x86 is only ever built for debug emulators; release packaging
    // strips x86 ABIs (see the androidComponents block below).
    "x86" to "i686-linux-android",
)

val officeOxideCrateDir = rootProject.layout.projectDirectory.dir("rust/office-oxide-bridge")
val officeOxideJniLibsDir = layout.buildDirectory.dir("generated/officeOxideJni")
fun resolveCargoExecutable(): String? {
    val pathEntries = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
    val candidates = buildList {
        addAll(pathEntries.map { File(it, "cargo") })
        add(File(System.getProperty("user.home"), ".cargo/bin/cargo"))
    }
    return candidates.firstOrNull { it.canExecute() }?.absolutePath
}

/** The `CARGO_HOME` cargo will use, resolved the same way cargo resolves it. */
fun resolveCargoHome(): String = System.getenv("CARGO_HOME")
    ?.takeIf { it.isNotBlank() }
    ?: File(System.getProperty("user.home"), ".cargo").absolutePath

/**
 * `rustup` installs the toolchain pinned by `rust-toolchain.toml` on the first
 * cargo invocation, and it is not safe against several processes doing that at
 * the same time: they race renaming the same `downloads/<sha>.partial` and the
 * losers fail the build with "component download failed … could not rename
 * downloaded file". Every per-ABI cargo build therefore waits for this single
 * warm-up, which also installs the Android targets declared in that file.
 */
val officeOxideToolchainWarmUp = tasks.register<Exec>("warmOfficeOxideToolchain") {
    group = "build"
    description = "Installs the Rust toolchain pinned by rust-toolchain.toml, before the per-ABI cargo builds."
    val cargo: String? = resolveCargoExecutable()
    onlyIf("Rust toolchain (cargo) is installed") { cargo != null }
    workingDir = officeOxideCrateDir.asFile
    commandLine(cargo ?: "cargo", "--version")
}

/**
 * Builds `liboffice_oxide_bridge.so` for one ABI with cargo, then stages it into
 * `build/generated/officeOxideJni/<abi>/`, which the Android plugin packages as
 * a jniLib. Staging (rather than pointing at cargo's own output directory) keeps
 * the packaged path ABI-shaped and lets Gradle model the dependency.
 */
fun registerOfficeOxideLibrary(abi: String, cargoTriple: String): TaskProvider<Sync> {
    val label = abi.replaceFirstChar { it.uppercase() }

    // Everything below is resolved here, at configuration time, into plain
    // Strings: the tasks themselves then carry no closures, which keeps them
    // compatible with the configuration cache (org.gradle.configuration-cache=true).
    //
    // Resolution is deliberately lenient: a machine without the Rust toolchain
    // (IDE sync, partial builds) must still configure. The tasks simply skip
    // via onlyIf, and CMake fails later with an actionable message if the .so
    // is actually needed but missing.
    val cargo: String? = resolveCargoExecutable()
    if (cargo == null) {
        logger.warn(
            "office_oxide: cargo not found; :app:buildOfficeOxide* tasks will skip. " +
                "Install the Rust toolchain from https://rustup.rs to build native Office support."
        )
    }
    // The NDK's clang wrapper is the only linker that understands the flags
    // rustc emits for Android. Without this cargo falls back to the host `cc`
    // and fails with `ld: unknown options: --version-script ...`.
    val ndkLinker: String? = runCatching {
        File(
            resolveNdkDir(),
            "toolchains/llvm/prebuilt/${hostNdkPrebuilt()}/bin/${abiToNdkClang(abi)}",
        ).absolutePath
    }.getOrNull()
    if (cargo != null && ndkLinker == null) {
        logger.warn(
            "office_oxide: no Android NDK found for $abi; cargo will fall back to the host " +
                "'cc' linker and fail. Install the NDK (sdkmanager \"ndk;$androidNdkVersion\") or set " +
                "ANDROID_NDK_HOME."
        )
    }

    val cargoBuild = tasks.register<Exec>("buildOfficeOxide$label") {
        group = "build"
        description = "Builds the office_oxide Rust bridge for $abi ($cargoTriple)."
        onlyIf("Rust toolchain (cargo) is installed") { cargo != null }
        dependsOn(officeOxideToolchainWarmUp)
        workingDir = officeOxideCrateDir.asFile

        inputs.dir(officeOxideCrateDir.dir("src"))
        inputs.files(
            officeOxideCrateDir.file("Cargo.toml"),
            officeOxideCrateDir.file("Cargo.lock"),
            officeOxideCrateDir.file("rust-toolchain.toml"),
        )
        outputs.dir(officeOxideCrateDir.dir("target/$cargoTriple/release"))

        // Environment variables are not part of an Exec task's up-to-date check, so
        // fold the linker and the remapped registry prefix into the cache key.
        val cargoRustflags = "--remap-path-prefix=${resolveCargoHome()}/registry=/cargo/registry"
        inputs.property("cargoTargetLinker", ndkLinker ?: "")
        inputs.property("cargoEncodedRustflags", cargoRustflags)

        commandLine(
            cargo ?: "cargo",
            "build",
            "--release",
            "--lib",
            "--target", cargoTriple,
            // Keep the artifact out of Gradle's way; the Sync task below
            // copies only what gets packaged.
            "--target-dir", "${officeOxideCrateDir.asFile}/target",
        )
        if (ndkLinker != null) {
            environment(
                "CARGO_TARGET_${cargoTriple.uppercase().replace('-', '_')}_LINKER",
                ndkLinker,
            )
        }
        // Cargo compiles every dependency's panic locations into the cdylib as
        // absolute paths into the registry (141 of them for this dependency graph),
        // so two machines with different CARGO_HOME (/root vs /home/vagrant) link
        // byte-different .so files and F-Droid's "compare with the published
        // binary" step fails. Remap that prefix to a fixed path so a given commit
        // produces identical binaries anywhere.
        environment("CARGO_ENCODED_RUSTFLAGS", cargoRustflags)
    }

    return tasks.register<Sync>("stageOfficeOxide${label}JniLibs") {
        group = "build"
        description = "Stages liboffice_oxide_bridge.so for $abi into the packaged jniLibs."
        onlyIf("Rust toolchain (cargo) is installed") { cargo != null }
        dependsOn(cargoBuild)
        from(officeOxideCrateDir.file("target/$cargoTriple/release/liboffice_oxide_bridge.so"))
        into(officeOxideJniLibsDir.map { it.dir(abi) })
    }
}

/** NDK clang wrapper name for an ABI. */
fun abiToNdkClang(abi: String): String = when (abi) {
    "arm64-v8a" -> "aarch64-linux-android26-clang"
    "armeabi-v7a" -> "armv7a-linux-androideabi26-clang"
    "x86" -> "i686-linux-android26-clang"
    "x86_64" -> "x86_64-linux-android26-clang"
    else -> throw GradleException("No NDK clang wrapper known for ABI '$abi'")
}

/** NDK prebuilt directory name for this host OS. */
fun hostNdkPrebuilt(): String = if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
    "darwin-x86_64"
} else {
    "linux-x86_64"
}

/**
 * Locates the NDK directory from the standard environment variables, an
 * explicit `ndk.dir`, or the highest side-by-side NDK under the SDK. Called at
 * configuration time, so it must not touch the `android` extension (which AGP
 * finalizes later); the side-by-side heuristic matches AGP's own default NDK
 * selection.
 */
fun resolveNdkDir(): File {
    // Deliberately no `android.ndkPath` here: this runs at configuration time,
    // when AGP has not finalized it, and capturing the extension would break
    // the configuration cache.
    listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT", "NDK_HOME", "NDK_ROOT").forEach { key ->
        System.getenv(key)
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?.let { return it }
    }
    // Explicit ndk.dir wins over side-by-side discovery.
    localProperties.getProperty("ndk.dir")
        ?.takeIf { it.isNotBlank() }
        ?.let { File(it) }
        ?.takeIf { it.isDirectory }
        ?.let { return it }
    val sdkDir = localProperties.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: File(System.getProperty("user.home"), "Library/Android/sdk").absolutePath
    val sideBySide = File(File(sdkDir), "ndk")
        .listFiles { file -> file.isDirectory }
        ?.sortedByDescending { it.name }
        ?.firstOrNull()
        ?: throw GradleException(
            "Could not locate the Android NDK (tried " +
                "ANDROID_NDK_HOME/ANDROID_NDK_ROOT/NDK_HOME/NDK_ROOT, ndk.dir, " +
                "and $sdkDir/ndk/*)."
        )
    return sideBySide
}

android {
    sourceSets {
        getByName("main") {
            // Cargo output staged by the stageOfficeOxide*JniLibs tasks.
            // `srcDir` is deprecated in AGP 9; `directories` is its replacement.
            jniLibs.directories.add(officeOxideJniLibsDir.get().asFile.absolutePath)
        }
    }
}

val officeOxideStagingTasks = officeOxideAbis.map { (abi, triple) ->
    registerOfficeOxideLibrary(abi, triple)
}

// Every variant's native build links the Rust .so, and every merge has to see the
// staged copy in jniLibs before packaging.
tasks.matching { it.name.startsWith("buildCMake") }.configureEach {
    dependsOn(officeOxideStagingTasks)
}
// CMakeLists.txt hard-fails when the .so is absent, so the per-ABI configure step
// must run after cargo produced it. Ordering it against buildCMake alone is not
// enough: configure* is an input of buildCMake*, so the two can be scheduled in
// either order (and in parallel), which broke clean builds with
// "Missing liboffice_oxide_bridge.so for <abi>".
tasks.matching { it.name.startsWith("configureCMake") }.configureEach {
    dependsOn(officeOxideStagingTasks)
}
// Both consumers of the staged jniLibs: merge*NativeLibs (packaging) and
// merge*JniLibFolders (source-set merging). Without the explicit dependsOn,
// Gradle fails validation with an "implicit dependency" error.
tasks.matching {
    it.name.startsWith("merge") &&
        (it.name.endsWith("NativeLibs") || it.name.contains("JniLibFolders"))
}.configureEach {
    dependsOn(officeOxideStagingTasks)
}

// One task to warm every ABI, mirroring the message CMake prints when the .so is
// missing. `./gradlew :app:buildOfficeOxideLibraries` is the documented entry point.
tasks.register("buildOfficeOxideLibraries") {
    group = "build"
    description = "Builds the office_oxide Rust bridge for every shipped Android ABI."
    dependsOn(officeOxideStagingTasks)
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.BuildConfig",
                    "*.ComposableSingletons*",
                    "*_Impl",
                    "*Database_Impl",
                    "*Dao_Impl"
                )
            }
        }
    }
}
//noinspection UseTomlInstead
dependencies {

    implementation(project(":shared"))

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
    androidTestImplementation("androidx.navigation:navigation-testing:2.10.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.11") {
        exclude(group = "org.junit.jupiter")
    }
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    ksp(libs.androidx.room.compiler)

    implementation("androidx.appcompat:appcompat:1.7.1")

    //noinspection GradleDependency (Updating these might cause the custom toolbox in pagination to break)
    implementation("androidx.navigation:navigation-compose:2.10.0")
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
    implementation("io.coil-kt:coil-svg:2.7.0")

    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")

    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.compose.runtime:runtime-livedata:1.9.3")

    implementation("org.slf4j:slf4j-android:1.7.36")

    implementation("org.commonmark:commonmark:0.22.0")

    implementation("com.jakewharton.timber:timber:5.0.1")

    implementation("me.zhanghai.android.libarchive:library:1.1.6")

    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("androidx.room:room-paging:2.7.1")

    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.browser:browser:1.8.0")

    implementation("io.legere:pdfiumandroid:2.0.0")
    implementation("org.zwobble.mammoth:mammoth:1.4.2")

    implementation("com.materialkolor:material-kolor:5.0.0-alpha07")

    debugImplementation("org.tensorflow:tensorflow-lite:2.17.0")
    debugImplementation("org.tensorflow:tensorflow-lite-support:0.5.0")
    debugImplementation("org.tensorflow:tensorflow-lite-gpu:2.17.0")
    debugImplementation("org.tensorflow:tensorflow-lite-gpu-api:2.17.0")

    implementation("androidx.core:core-splashscreen:1.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("io.mockk:mockk-android:1.14.9")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.json:json:20251224")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.slf4j:slf4j-nop:2.0.17")
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

androidComponents {
    onVariants { variant ->
        if (variant.buildType != "debug") {
            variant.externalNativeBuild?.abiFilters?.set(setOf("arm64-v8a", "armeabi-v7a"))
            variant.packaging.jniLibs.excludes.addAll(
                setOf("**/x86/**", "**/x86_64/**")
            )
        }
    }
}
