package com.aryan.reader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCloudCleanupPlanTest {
    @Test
    fun `plan includes only private app storage and generated book artifacts`() {
        assertTrue(AndroidCloudCleanupPlan.shouldDeleteFilesDirEntry("books", isDirectory = true))
        assertTrue(AndroidCloudCleanupPlan.shouldDeleteFilesDirEntry("custom_fonts", isDirectory = true))
        assertTrue(AndroidCloudCleanupPlan.shouldDeleteFilesDirEntry("audiobooks", isDirectory = true))
        assertTrue(AndroidCloudCleanupPlan.shouldDeleteFilesDirEntry("book-1_reflow.html", isDirectory = false))
        assertTrue(AndroidCloudCleanupPlan.shouldDeleteCacheEntry("imported_file_book-1", isDirectory = true))
        assertTrue(AndroidCloudCleanupPlan.shouldDeleteCacheEntry("reflow_cache", isDirectory = true))
        assertFalse(AndroidCloudCleanupPlan.shouldDeleteFilesDirEntry("selected_external_file", isDirectory = false))
        assertFalse(AndroidCloudCleanupPlan.shouldDeleteCacheEntry("user_selected_cache", isDirectory = true))
    }
}
