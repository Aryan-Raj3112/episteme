import java.io.File

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kover) apply false
}

val test by tasks.registering {
    group = "verification"
    description = "Runs available unit tests for the included projects."
}

val verifyCodebaseArchitecture by tasks.registering {
    group = "verification"
    description = "Enforces production Kotlin size ratchets, source-set direction, and retired dumping grounds."

    val productionSources = files(
        fileTree("app/src/main/java") { include("**/*.kt") },
        fileTree("shared/src/commonMain/kotlin") { include("**/*.kt") },
        fileTree("shared/src/mobileMain/kotlin") { include("**/*.kt") },
        fileTree("shared/src/readerJvmMain/kotlin") { include("**/*.kt") },
        fileTree("shared/src/androidMain/kotlin") { include("**/*.kt") },
        fileTree("shared/src/iosMain/kotlin") { include("**/*.kt") },
    )
    inputs.files(productionSources)
    val repositoryRootPath = rootDir.absolutePath

    doLast {
        val defaultMaximumLines = 1_500
        val ratchetedExceptions = mapOf(
            "app/src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt" to 8_133,
            "app/src/main/java/com/aryan/reader/MainViewModel.kt" to 7_647,
            "app/src/main/java/com/aryan/reader/epubreader/EpubReaderScreen.kt" to 7_422,
            "shared/src/iosMain/kotlin/com/aryan/reader/shared/ios/ReaderIosApp.kt" to 4_364,
            "app/src/main/java/com/aryan/reader/pdf/PdfPageComposable.kt" to 4_311,
            "app/src/main/java/com/aryan/reader/paginatedreader/NativeVerticalReaderScreen.kt" to 3_504,
            "shared/src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobilePdfReaderScreen.kt" to 3_496,
            "app/src/main/java/com/aryan/reader/paginatedreader/PaginatedReaderContent.kt" to 3_386,
            "app/src/main/java/com/aryan/reader/tts/TtsPlaybackManager.kt" to 2_825,
            "app/src/main/java/com/aryan/reader/pdf/PdfVerticalReader.kt" to 2_600,
            "shared/src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobileLibraryScreens.kt" to 2_494,
            "shared/src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobilePdfRendering.kt" to 2_385,
            "app/src/main/java/com/aryan/reader/epubreader/EpubReaderControls.kt" to 2_231,
            "shared/src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobileAudiobooksUi.kt" to 2_180,
            "app/src/main/java/com/aryan/reader/paginatedreader/BookPaginator.kt" to 2_037,
            "app/src/main/java/com/aryan/reader/HomeScreen.kt" to 2_023,
            "shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/pptx/SharedPptxDocument.kt" to 1_969,
            "app/src/main/java/com/aryan/reader/paginatedreader/PaginatedReaderSelection.kt" to 1_961,
            "shared/src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobileLibraryComponents.kt" to 1_923,
            "app/src/main/java/com/aryan/reader/paginatedreader/Paginator.kt" to 1_909,
            "shared/src/commonMain/kotlin/com/aryan/reader/shared/pdf/SharedPdfRichText.kt" to 1_906,
            "shared/src/readerJvmMain/kotlin/com/aryan/reader/shared/reader/SharedJvmBookLoader.kt" to 1_878,
            "shared/src/commonMain/kotlin/com/aryan/reader/paginatedreader/CssParser.kt" to 1_864,
            "shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/SharedPdfAnnotationUi.kt" to 1_824,
            "app/src/main/java/com/aryan/reader/AudiobooksUi.kt" to 1_810,
            "shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/NonReaderScreens.kt" to 1_758,
            "shared/src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobileEpubReader.kt" to 1_707,
            "shared/src/commonMain/kotlin/androidx/compose/material/icons/filled/FilledIcons.kt" to 1_603,
            "app/src/main/java/com/aryan/reader/LibraryScreen.kt" to 1_581,
            "app/src/main/java/com/aryan/reader/epubreader/ChapterWebView.kt" to 1_573,
            "shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/SharedAppThemeSettings.kt" to 1_507,
            "shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/ReaderWorkspaceShell.kt" to 1_505,
        )
        val retiredDumpingGrounds = setOf("Common.kt")
        val forbiddenPortableImport = Regex("(?m)^import\\s+(android|java|javax)\\.")
        val forbiddenAndroidDependency = Regex("(?m)^import\\s+com\\.aryan\\.reader\\.(shared\\.ios|desktop)(\\.|$)")
        val violations = mutableListOf<String>()

        inputs.files.files.sortedBy { it.path }.forEach { source ->
            val relativePath = source.relativeTo(File(repositoryRootPath)).invariantSeparatorsPath
            val lineCount = source.useLines { it.count() }
            val maximumLines = ratchetedExceptions[relativePath] ?: defaultMaximumLines
            if (lineCount > maximumLines) {
                violations += "$relativePath has $lineCount lines; maximum is $maximumLines"
            }
            if (source.name in retiredDumpingGrounds) {
                violations += "$relativePath revives retired dumping ground ${source.name}"
            }

            val content = source.readText()
            val isPortableSharedSource = relativePath.startsWith("shared/src/commonMain/") ||
                relativePath.startsWith("shared/src/mobileMain/")
            if (isPortableSharedSource && forbiddenPortableImport.containsMatchIn(content)) {
                violations += "$relativePath imports an Android/JVM API from a portable source set"
            }
            if (relativePath.startsWith("app/src/main/") && forbiddenAndroidDependency.containsMatchIn(content)) {
                violations += "$relativePath imports an iOS/desktop implementation into Android"
            }
        }

        check(violations.isEmpty()) {
            "Codebase architecture verification failed:\n${violations.joinToString("\n") { "- $it" }}"
        }
    }
}

val check by tasks.registering {
    group = "verification"
    description = "Runs codebase architecture gates and project verification tasks."
    dependsOn("verifyCodebaseArchitecture")
}

subprojects {
    val rootTest = rootProject.tasks.named("test")
    val rootArchitectureCheck = rootProject.tasks.named("verifyCodebaseArchitecture")
    tasks.matching {
        it.name == "allTests" ||
            it.name == "desktopTest" ||
            it.name.endsWith("DebugUnitTest")
    }.configureEach {
        rootTest.configure {
            dependsOn(this@configureEach)
        }
    }
    tasks.withType<Test>().configureEach {
        maxHeapSize = "4g"
    }
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootArchitectureCheck)
    }
}
