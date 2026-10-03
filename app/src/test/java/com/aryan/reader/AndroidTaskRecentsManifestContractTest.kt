package com.aryan.reader

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Recents decides whether a task is listed from the flags on the task's *base
 * intent*, not from the intent the activity was started with. Android copies
 * `excludeFromRecents` from the manifest onto the launch intent of every start
 * of that activity, so an excludeFromRecents activity that shares the reader's
 * taskAffinity can seed the reader's task and hide the whole app from the
 * recent-apps switcher: ActivityStarter reuses any task with a matching root
 * affinity for a NEW_TASK start, and MainActivity is singleTask, so its
 * NEW_TASK flag is implied whether or not the router sets it.
 *
 * These assertions pin the invariant that keeps an external open from putting
 * the reader into a hidden task. The two entry points stay excluded themselves:
 * each roots a task that is destroyed within milliseconds, and an unfiltered
 * task that short-lived still leaves a stale Recents card behind.
 */
class AndroidTaskRecentsManifestContractTest {
    @Test
    fun `external open activities keep their own task affinity`() {
        val activities = declaredActivities()

        assertEquals(
            "MainActivity must not declare a taskAffinity; the reader task is " +
                "identified by the application id",
            null,
            activities.getValue(".MainActivity").androidAttribute("taskAffinity"),
        )
        listOf(".ExternalFileOpenRouterActivity", ".TemporaryExternalFileActivity").forEach { name ->
            val affinity = activities.getValue(name).androidAttribute("taskAffinity")
            assertTrue(
                "$name needs an explicit taskAffinity so it cannot share a task with MainActivity",
                affinity != null && affinity != APPLICATION_ID,
            )
        }
    }

    @Test
    fun `no activity hidden from recents shares the reader task affinity`() {
        val hidden = declaredActivities().filterValues { it.androidAttribute("excludeFromRecents") == "true" }

        assertEquals(
            "Only the external-open entry points may be hidden from Recents",
            setOf(".ExternalFileOpenRouterActivity", ".TemporaryExternalFileActivity"),
            hidden.keys,
        )
        hidden.forEach { (name, activity) ->
            assertTrue(
                "$name is hidden from Recents but shares MainActivity's task affinity, so an " +
                    "external open can hide the whole reader from the recent-apps switcher",
                taskAffinityOf(activity) != APPLICATION_ID,
            )
        }
    }

    /** Mirrors the framework: no declared affinity means the application id. */
    private fun taskAffinityOf(activity: Element): String =
        activity.androidAttribute("taskAffinity") ?: APPLICATION_ID

    private fun declaredActivities(): Map<String, Element> = readManifest()
        .getElementsByTagName("activity")
        .asElements()
        .mapNotNull { activity -> activity.androidAttribute("name")?.let { it to activity } }
        .toMap()

    private fun readManifest(): org.w3c.dom.Document {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.isFile }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> = buildList {
        for (index in 0 until length) {
            (item(index) as? Element)?.let(::add)
        }
    }

    private fun Element.androidAttribute(name: String): String? = attributes
        ?.getNamedItemNS("http://schemas.android.com/apk/res/android", name)
        ?.nodeValue
        ?.replace("\${applicationId}", APPLICATION_ID)

    private companion object {
        const val APPLICATION_ID = "com.aryan.reader"
    }
}