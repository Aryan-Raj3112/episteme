package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class FolderAnnotationExportPolicyTest {
    @Test
    fun `normal edits use quiet period`() {
        assertEquals(2_000L, folderAnnotationExportDelayMillis(1_000L, 1_250L, immediate = false))
    }

    @Test
    fun `continuous edits cannot exceed maximum dirty age`() {
        assertEquals(250L, folderAnnotationExportDelayMillis(1_000L, 10_750L, immediate = false))
        assertEquals(0L, folderAnnotationExportDelayMillis(1_000L, 11_000L, immediate = false))
    }

    @Test
    fun `lifecycle flush is immediate`() {
        assertEquals(0L, folderAnnotationExportDelayMillis(1_000L, 1_050L, immediate = true))
    }

    @Test
    fun `clock rollback keeps the normal quiet period`() {
        assertEquals(2_000L, folderAnnotationExportDelayMillis(2_000L, 1_000L, immediate = false))
    }
}
