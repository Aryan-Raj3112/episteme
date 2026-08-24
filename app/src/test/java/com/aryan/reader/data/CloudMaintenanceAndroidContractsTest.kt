package com.aryan.reader.data

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudMaintenanceAndroidContractsTest {
    @Test
    fun `disconnect releases both persisted URI grant directions`() {
        assertEquals(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            PERSISTED_URI_GRANT_FLAGS,
        )
    }
}
