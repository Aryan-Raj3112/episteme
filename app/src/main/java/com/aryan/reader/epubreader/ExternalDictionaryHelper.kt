// ExternalDictionaryHelper.kt
package com.aryan.reader.epubreader

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.Toast
import timber.log.Timber
import androidx.core.net.toUri
import com.aryan.reader.R

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
                label = context.getString(R.string.dict_app_label_search),
                packageName = GOOGLE_SEARCH_PKG,
                icon = null
            )
        )

        return apps.sortedBy { it.label }
    }

    fun launchDictionary(context: Context, packageName: String, query: String) {
        if (packageName.isEmpty()) return
        val pm = context.packageManager
        try {
            if (packageName == GOOGLE_SEARCH_PKG) {
                launchSearch(context, packageName, query)
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
            Toast.makeText(context, context.getString(R.string.error_opening_dictionary), Toast.LENGTH_SHORT).show()
        }
    }

    fun launchTranslate(context: Context, packageName: String, query: String) {
        if (packageName.isEmpty()) return
        val pm = context.packageManager
        try {
            if (packageName == GOOGLE_SEARCH_PKG) {
                launchSearch(context, packageName, query)
                return
            }

            // Google Translate specific intent
            if (packageName == "com.google.android.apps.translate") {
                val translateIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_PROCESS_TEXT, query)
                    putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (translateIntent.resolveActivity(pm) != null) {
                    context.startActivity(translateIntent)
                    return
                }
            }

            // Generic text processing intent
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

            launchGenericSend(context, packageName, query)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch translate app: $packageName")
            Toast.makeText(context, context.getString(R.string.error_opening_translate), Toast.LENGTH_SHORT).show()
        }
    }

    fun launchSearch(context: Context, packageName: String, query: String) {
        try {
            if (packageName == GOOGLE_SEARCH_PKG) {
                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(searchIntent)
                return
            }

            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(searchIntent)
                return
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                data = "https://www.google.com/search?q=${Uri.encode(query)}".toUri()
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (viewIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(viewIntent)
                return
            }

            launchGenericSend(context, packageName, query)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch search app: $packageName")
            Toast.makeText(context, context.getString(R.string.error_opening_search), Toast.LENGTH_SHORT).show()
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

    fun getAvailableSearchApps(context: Context): List<ExternalDictionaryApp> {
        val pm = context.packageManager
        val apps = mutableListOf<ExternalDictionaryApp>()
        val addedPackages = mutableSetOf<String>()

        val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH)
        val searchResolvers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(webSearchIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(webSearchIntent, 0)
        }
        searchResolvers.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (!PACKAGE_BLOCKLIST.contains(pkg) && addedPackages.add(pkg)) {
                apps.add(
                    ExternalDictionaryApp(
                        label = ri.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = ri.loadIcon(pm)
                    )
                )
            }
        }

        val browserIntent = Intent(Intent.ACTION_VIEW, "http://".toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val browserResolvers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(browserIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(browserIntent, 0)
        }
        browserResolvers.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (!PACKAGE_BLOCKLIST.contains(pkg) && addedPackages.add(pkg)) {
                apps.add(
                    ExternalDictionaryApp(
                        label = ri.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = ri.loadIcon(pm)
                    )
                )
            }
        }

        return apps.sortedBy { it.label }
    }
}