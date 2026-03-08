package com.aryan.reader.epubreader

import android.content.ComponentName
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

    // Known package names for specific integrations
    private const val PKG_COLORDICT = "com.socialnmobile.colordict"
    private const val PKG_GOLDENDICT = "mobi.goldendict.android"
    private const val PKG_GOLDENDICT_FREE = "mobi.goldendict.android.free"
    private const val PKG_AARD2 = "it.t_arn.aard2"
    private const val PKG_LIVIO = "livio.pack.lang.en_US"
    private const val PKG_GOOGLE_TRANSLATE = "com.google.android.apps.translate"

    private const val PKG_OSS_DICT_FDROID = "io.github.mvasilev.dictionary"

    fun getAvailableDictionaries(context: Context): List<ExternalDictionaryApp> {
        val pm = context.packageManager
        val apps = mutableListOf<ExternalDictionaryApp>()

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        val colorDictIntent = Intent("colordict.intent.action.SEARCH")
        val colorDictInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(colorDictIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(colorDictIntent, 0)
        }

        val addedPackages = mutableSetOf<String>()

        resolveInfos.forEach { ri ->
            if (addedPackages.add(ri.activityInfo.packageName)) {
                apps.add(
                    ExternalDictionaryApp(
                        label = ri.loadLabel(pm).toString(),
                        packageName = ri.activityInfo.packageName,
                        icon = ri.loadIcon(pm)
                    )
                )
            }
        }

        colorDictInfos.forEach { ri ->
            if (addedPackages.add(ri.serviceInfo.packageName)) {
                apps.add(
                    ExternalDictionaryApp(
                        label = ri.loadLabel(pm).toString(),
                        packageName = ri.serviceInfo.packageName,
                        icon = ri.loadIcon(pm)
                    )
                )
            }
        }

        val manualList = listOf(PKG_AARD2, PKG_OSS_DICT_FDROID, "com.github.tngande.ossdict")
        manualList.forEach { pkg ->
            if (!addedPackages.contains(pkg)) {
                try {
                    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        pm.getApplicationInfo(pkg, 0)
                    }
                    apps.add(
                        ExternalDictionaryApp(
                            label = pm.getApplicationLabel(info).toString(),
                            packageName = pkg,
                            icon = pm.getApplicationIcon(info)
                        )
                    )
                    addedPackages.add(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    // Not installed
                }
            }
        }

        return apps.sortedBy { it.label }
    }

    fun launchDictionary(context: Context, packageName: String, query: String) {
        try {
            val dictIntent = Intent("colordict.intent.action.SEARCH")
            dictIntent.putExtra("EXTRA_QUERY", query)
            dictIntent.putExtra("EXTRA_FULLSCREEN", false)
            dictIntent.setPackage(packageName)

            val pm = context.packageManager
            val serviceInfo = pm.resolveService(dictIntent, 0)

            if (serviceInfo != null) {
                launchGenericSend(context, packageName, query)
            } else {
                if (dictIntent.resolveActivity(pm) != null) {
                    dictIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(dictIntent)
                    return
                }

                if (packageName == PKG_AARD2) {
                    val aardIntent = Intent("aard2.lookup")
                    aardIntent.putExtra("query", query)
                    aardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(aardIntent)
                    return
                }

                val processTextIntent = Intent(Intent.ACTION_PROCESS_TEXT)
                processTextIntent.setType("text/plain")
                processTextIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, query)
                processTextIntent.setPackage(packageName)
                processTextIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (processTextIntent.resolveActivity(pm) != null) {
                    context.startActivity(processTextIntent)
                    return
                }

                launchGenericSend(context, packageName, query)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch dictionary")
            Toast.makeText(context, "Could not launch dictionary: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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