package com.aryan.reader

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudFolderSyncLogTest {
    @Test
    fun transferClassifierRecognizesOnlyCloudFolderFailures() {
        val transfer = CloudFolderTransferException(
            stage = "download",
            category = "network",
            statusCategory = "network",
        )
        val drive = CloudFolderDriveException(
            httpStatusCode = 503,
            bodyCategory = "backend",
            statusCategory = "network",
        )

        assertTrue(isCloudFolderTransferFailure(transfer))
        assertTrue(isCloudFolderTransferFailure(RuntimeException("wrapped", drive)))
        assertFalse(isCloudFolderTransferFailure(IOException("ordinary library failure")))
    }
}
