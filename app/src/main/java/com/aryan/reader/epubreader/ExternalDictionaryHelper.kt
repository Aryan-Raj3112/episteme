// ExternalDictionaryHelper.kt
package com.aryan.reader.epubreader

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
import timber.log.Timber

data class ExternalDictionaryApp(
    val label: String,
    val packageName: String,
    val icon: Drawable?
)

object ExternalDictionaryHelper {
    const val GOOGLE_SEARCH_PKG = "app.internal.google_search"

    private val PACKAGE_BLOCKLIST = setOf(
        "com.samsung.android.samsungpassautofill",
        "com.samsung.android.samsungpass",
        "com.samsung.android.app.pass",
        "com.google.android.gms",
        "com.truecaller",
        "com.adobe.reader",
        "com.reddit.frontpage"
    )

    fun getAvailableDictionaries(context: Context): List<ExternalDictionaryApp> {
        val pm = context.packageManager
        val apps = mutableListOf<ExternalDictionaryApp>()
        val addedPackages = mutableSetOf<String>()

        val processTextIntent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val textResolvers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(processTextIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(processTextIntent, 0)
        }

        textResolvers.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (!PACKAGE_BLOCKLIST.contains(pkg) && addedPackages.add(pkg)) {
                apps.add(ExternalDictionaryApp(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm)
                ))
            }
        }

        val colorDictIntent = Intent("colordict.intent.action.SEARCH")
        val colorResolvers = pm.queryIntentActivities(colorDictIntent, 0)
        colorResolvers.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (!PACKAGE_BLOCKLIST.contains(pkg) && addedPackages.add(pkg)) {
                apps.add(ExternalDictionaryApp(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm)
                ))
            }
        }

        val sortedApps = apps.sortedBy { it.label }.toMutableList()

        // Inject Google Search at the top
        sortedApps.add(
            0,
            ExternalDictionaryApp(
                label = "Search",
                packageName = GOOGLE_SEARCH_PKG,
                icon = null
            )
        )

        return sortedApps
    }

    fun launchDictionary(context: Context, packageName: String, query: String) {
        val pm = context.packageManager
        try {
            if (packageName == GOOGLE_SEARCH_PKG) {
                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(searchIntent)
                return
            }

            val processTextIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_PROCESS_TEXT, query)
                putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (processTextIntent.resolveActivity(pm) != null) {
                context.startActivity(processTextIntent)
                return
            }

            val dictIntent = Intent("colordict.intent.action.SEARCH").apply {
                putExtra("EXTRA_QUERY", query)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (dictIntent.resolveActivity(pm) != null) {
                context.startActivity(dictIntent)
                return
            }

            if (packageName == "it.t_arn.aard2") {
                val aardIntent = Intent("aard2.lookup").apply {
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(aardIntent)
                return
            }

            launchGenericSend(context, packageName, query)

        } catch (e: Exception) {
            Timber.e(e, "Failed to launch dictionary app: $packageName")
            Toast.makeText(context, "Error opening dictionary", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGenericSend(context: Context, packageName: String, query: String) {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "text/plain"
        sendIntent.putExtra(Intent.EXTRA_TEXT, query)
        sendIntent.setPackage(packageName)
        sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(sendIntent)
        } catch (e: Exception) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                throw e
            }
        }
    }
}