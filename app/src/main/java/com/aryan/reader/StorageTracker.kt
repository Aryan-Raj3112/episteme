// StorageTracker.kt
package com.aryan.reader

import android.content.Context
import timber.log.Timber
import java.io.File

object StorageTracker {
    fun logStorageUsage(context: Context, tag: String) {
        try {
            val cacheSize = getDirSize(context.cacheDir)
            val filesSize = getDirSize(context.filesDir)
            val dbDir = File(context.applicationInfo.dataDir, "databases")
            val dbSize = getDirSize(dbDir)

            Timber.tag("StorageTracker").i("[$tag] Cache: ${formatSize(cacheSize)} | Files: ${formatSize(filesSize)} | DBs: ${formatSize(dbSize)}")

            // Log top 3 largest items in cache to pinpoint bloat
            val largestCacheFiles = context.cacheDir.listFiles()
                ?.sortedByDescending { getDirSize(it) }
                ?.take(3)
                ?.joinToString { "${it.name}: ${formatSize(getDirSize(it))}" }

            if (!largestCacheFiles.isNullOrEmpty()) {
                Timber.tag("StorageTracker").d("Largest in Cache -> $largestCacheFiles")
            }
        } catch (e: Exception) {
            Timber.tag("StorageTracker").e(e, "Error calculating storage")
        }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        if (dir.isFile) return dir.length()
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += getDirSize(file)
        }
        return size
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}