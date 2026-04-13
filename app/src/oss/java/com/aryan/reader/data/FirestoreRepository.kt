// src\oss
@file:Suppress("unused", "RedundantSuspendModifier")

package com.aryan.reader.data

import java.util.Date

// --- Data Classes used by ViewModel ---
data class BookMetadata(
    val bookId: String = "",
    val title: String? = null,
    val author: String? = null,
    val displayName: String = "",
    val type: String = "",
    val lastPositionCfi: String? = null,
    val lastChapterIndex: Int? = null,
    val locatorBlockIndex: Int? = null,
    val locatorCharOffset: Int? = null,
    val lastPage: Int? = null,
    val progressPercentage: Float? = null,
    var isRecent: Boolean = true,
    var isDeleted: Boolean = false,
    val lastModifiedTimestamp: Long = 0L,
    val bookmarksJson: String? = null,
    val hasAnnotations: Boolean = false,
    val customName: String? = null,
    val highlightsJson: String? = null
)

data class DeviceItem(
    val deviceId: String = "",
    val deviceName: String = "",
    val firstRegistered: Date? = null,
    val lastSeen: Date? = null,
    val status: String = "active"
)

sealed class DeviceStatus {
    data object Active : DeviceStatus()
    data object Revoked : DeviceStatus()
    data object NotFound : DeviceStatus()
    data class Error(val exception: Exception) : DeviceStatus()
}

data class FontMetadata(
    val id: String = "",
    val displayName: String = "",
    val fileName: String = "",
    val fileExtension: String = "",
    val timestamp: Long = 0L,
    var isDeleted: Boolean = false
)

// --- Feedback Models Stubs ---
data class FeedbackThread(
    val id: String = "",
    val uid: String = "",
    val category: String = "",
    val status: String = "open",
    val lastUpdated: Date? = null,
    var hasUnreadAdminReply: Boolean = false,
    val slackThreadTs: String? = null,
    val preview: String = ""
)

data class FeedbackMessage(
    val id: String = "",
    val text: String = "",
    val sender: String = "",
    val timestamp: Date? = null,
    val attachments: List<String> = emptyList()
)


// --- Stub Class ---
class FirestoreRepository {

    // Helper to safely ignore listener removal in ViewModel
    fun removeListener(listener: Any?) {
        // No-op
    }

    fun listenToUserProfile(userId: String, onUpdate: (isPro: Boolean, credits: Int) -> Unit): Any? {
        onUpdate(false, 0)
        return null
    }

    fun getFcmToken(onComplete: (String?) -> Unit) {
        onComplete(null)
    }

    suspend fun getDeviceStatus(userId: String, deviceId: String): DeviceStatus = DeviceStatus.NotFound
    suspend fun getRegisteredDevices(userId: String): List<DeviceItem> = emptyList()
    suspend fun updateDeviceLastSeen(userId: String, deviceId: String) {}
    suspend fun deleteDevice(userId: String, deviceId: String) {}
    suspend fun registerOrUpdateDevice(userId: String, deviceId: String, deviceName: String, fcmToken: String) {}
    suspend fun replaceDevice(userId: String, deviceToRemoveId: String, newDeviceId: String, newDeviceName: String, originDeviceId: String): Boolean = false

    suspend fun syncFontMetadata(userId: String, font: FontMetadata) {}
    suspend fun getAllFonts(userId: String): List<FontMetadata> = emptyList()
    suspend fun deleteFontMetadata(userId: String, fontId: String) {}

    suspend fun syncBookMetadata(userId: String, book: BookMetadata, originDeviceId: String) {}
    suspend fun getAllBooks(userId: String): List<BookMetadata> = emptyList()
    suspend fun getBookMetadata(userId: String, bookId: String): BookMetadata? = null

    suspend fun syncShelf(userId: String, shelf: ShelfMetadata, originDeviceId: String) {}
    suspend fun getAllShelves(userId: String): List<ShelfMetadata> = emptyList()
    suspend fun getShelf(userId: String, shelfName: String): ShelfMetadata? = null

    suspend fun deleteAllUserFirestoreData(userId: String) {}
    suspend fun updateFcmToken(userId: String, deviceId: String, token: String) {}

    // --- Feedback Stubs ---
    fun listenForUnreadFeedback(userId: String, onUpdate: (Boolean) -> Unit): Any? {
        onUpdate(false)
        return null
    }

    fun listenToFeedbackThreads(userId: String, onUpdate: (List<FeedbackThread>) -> Unit): Any? {
        onUpdate(emptyList())
        return null
    }

    fun listenToFeedbackMessages(threadId: String, onUpdate: (List<FeedbackMessage>) -> Unit): Any? {
        onUpdate(emptyList())
        return null
    }

    fun generateMessageId(): String {
        return java.util.UUID.randomUUID().toString()
    }

    suspend fun createFeedbackThread(userId: String, category: String, initialMessage: String, attachments: List<String> = emptyList()): String = ""

    suspend fun addMessageToThread(threadId: String, messageId: String, uid: String, text: String, sender: String, attachments: List<String>) {}
    suspend fun markThreadAsRead(threadId: String) {}
}