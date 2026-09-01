package com.aryan.reader.data

/**
 * Private app-owned paths that may be removed by the destructive cloud/local
 * clear action. External SAF/file-provider selections are intentionally not
 * represented here and therefore cannot be deleted by this plan.
 */
internal object AndroidCloudCleanupPlan {
    val privateFilesDirectories: Set<String> = setOf(
        "books",
        "custom_fonts",
        "audiobooks",
        "cover_cache",
        "annotations",
        "pdf_rich_text",
        "page_layouts",
        "pdf_text_boxes",
        "pdf_highlights",
        "textboxes",
        "derived",
    )

    val generatedFilesDirPrefixes: Set<String> = setOf(
        "rich_doc_",
    )

    val generatedCacheDirectories: Set<String> = setOf(
        "chapter_summaries",
        "pdfium_annotation_export",
        "reflow_cache",
        "reflow_images",
    )

    fun shouldDeleteFilesDirEntry(name: String, isDirectory: Boolean): Boolean =
        (isDirectory && name in privateFilesDirectories) ||
            (!isDirectory && generatedFilesDirPrefixes.any(name::startsWith)) ||
            (!isDirectory && name.endsWith("_reflow.html"))

    fun shouldDeleteCacheEntry(name: String, isDirectory: Boolean): Boolean =
        (isDirectory && name in generatedCacheDirectories) ||
            name.startsWith("imported_file_") ||
            name.startsWith("temp_") ||
            name.startsWith("sync_bundle_") ||
            name.startsWith("remote_sync_bundle_") ||
            name.startsWith("temp_download_") ||
            name.startsWith("temp_bg_download_")
}
