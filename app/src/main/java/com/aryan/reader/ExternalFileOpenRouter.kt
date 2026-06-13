package com.aryan.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

const val EXTRA_TEMPORARY_EXTERNAL_OPEN = "com.aryan.reader.extra.TEMPORARY_EXTERNAL_OPEN"

object ExternalFileOpenRouteDecider {
    const val BEHAVIOR_TEMPORARY = "TEMPORARY"

    fun shouldOpenTemporary(externalFileBehavior: String?): Boolean {
        return externalFileBehavior == BEHAVIOR_TEMPORARY
    }

    fun targetActivityClass(externalFileBehavior: String?): Class<out Activity> {
        return if (shouldOpenTemporary(externalFileBehavior)) {
            TemporaryExternalFileActivity::class.java
        } else {
            MainActivity::class.java
        }
    }
}

class ExternalFileOpenRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeExternalOpen(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeExternalOpen(intent)
        finish()
    }

    private fun routeExternalOpen(sourceIntent: Intent?) {
        if (sourceIntent?.action != Intent.ACTION_VIEW || sourceIntent.data == null) return

        val prefs = getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val behavior = prefs.getString("external_file_behavior", "ASK")
        val temporary = ExternalFileOpenRouteDecider.shouldOpenTemporary(behavior)
        val targetIntent = Intent(sourceIntent).apply {
            setClass(this@ExternalFileOpenRouterActivity, ExternalFileOpenRouteDecider.targetActivityClass(behavior))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (temporary) {
                putExtra(EXTRA_TEMPORARY_EXTERNAL_OPEN, true)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
        startActivity(targetIntent)
    }
}

class TemporaryExternalFileActivity : MainActivity()
