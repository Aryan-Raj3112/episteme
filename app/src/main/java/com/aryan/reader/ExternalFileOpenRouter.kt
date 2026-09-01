package com.aryan.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.aryan.reader.shared.MobileExternalOpenAction
import com.aryan.reader.shared.ExternalDocumentOpenMode
import com.aryan.reader.shared.mobileExternalOpenAction

const val EXTRA_TEMPORARY_EXTERNAL_OPEN = "com.aryan.reader.extra.TEMPORARY_EXTERNAL_OPEN"

object ExternalFileOpenRouteDecider {
    private const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    fun shouldOpenTemporary(externalFileBehavior: String?): Boolean {
        return mobileExternalOpenAction(externalFileBehavior) == MobileExternalOpenAction.OPEN_TEMPORARY
    }

    fun shouldOpenTemporarily(
        externalFileBehavior: String?,
        openMode: ExternalDocumentOpenMode,
    ): Boolean {
        return openMode == ExternalDocumentOpenMode.OPEN_SINGLE &&
            shouldOpenTemporary(externalFileBehavior)
    }

    /**
     * Returns the normalized open mode for an external document intent. VIEW
     * retains its historical direct-data fallback when a provider omits or
     * lies about metadata; SEND intents are accepted only after shared
     * normalization succeeds.
     */
    fun openModeForIntent(sourceIntent: Intent, context: Context? = null): ExternalDocumentOpenMode? {
        val normalizedMode = ExternalDocumentIntentMapper
            .map(sourceIntent, context)
            ?.request
            ?.openMode
        if (normalizedMode != null) return normalizedMode

        return if (sourceIntent.action == Intent.ACTION_VIEW && sourceIntent.data != null) {
            ExternalDocumentOpenMode.OPEN_SINGLE
        } else {
            null
        }
    }

    fun targetActivityClass(externalFileBehavior: String?): Class<out Activity> {
        return if (shouldOpenTemporary(externalFileBehavior)) {
            TemporaryExternalFileActivity::class.java
        } else {
            MainActivity::class.java
        }
    }

    fun flagsForInternalForward(sourceFlags: Int): Int {
        // A VIEW intent belongs to the sender's task. Forward only its URI grants,
        // then explicitly launch/reuse Episteme's normal task. This prevents the
        // router's excluded task or a sender's EXCLUDE_FROM_RECENTS flag from
        // becoming the reader task's Recents policy.
        return (sourceFlags and URI_GRANT_FLAGS) or Intent.FLAG_ACTIVITY_NEW_TASK
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
        if (sourceIntent == null) return
        val openMode = ExternalFileOpenRouteDecider.openModeForIntent(sourceIntent, this)
            ?: return
        routeIntent(sourceIntent, openMode = openMode)
    }

    private fun routeIntent(sourceIntent: Intent, openMode: ExternalDocumentOpenMode) {
        val prefs = getSharedPreferences("reader_user_prefs", Context.MODE_PRIVATE)
        val behavior = prefs.getString("external_file_behavior", "ASK")
        val shouldOpenTemporarily = ExternalFileOpenRouteDecider.shouldOpenTemporarily(behavior, openMode)
        val targetIntent = Intent(sourceIntent).apply {
            flags = ExternalFileOpenRouteDecider.flagsForInternalForward(sourceIntent.flags)
            setClass(
                this@ExternalFileOpenRouterActivity,
                if (shouldOpenTemporarily) {
                    TemporaryExternalFileActivity::class.java
                } else {
                    MainActivity::class.java
                }
            )
            if (shouldOpenTemporarily) {
                putExtra(EXTRA_TEMPORARY_EXTERNAL_OPEN, true)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
        startActivity(targetIntent)
    }
}

class TemporaryExternalFileActivity : MainActivity()
