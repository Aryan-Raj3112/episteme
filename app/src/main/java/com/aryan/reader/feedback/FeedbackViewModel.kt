/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.feedback

import android.app.Application
import android.content.Context
import android.net.Uri
import timber.log.Timber
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aryan.reader.AuthRepository
import com.aryan.reader.data.FeedbackMessage
import com.aryan.reader.data.FeedbackRepository
import com.aryan.reader.data.FeedbackThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

data class FeedbackUiState(
    val threads: List<FeedbackThread> = emptyList(),
    val currentMessages: List<FeedbackMessage> = emptyList(),
    val pendingMessages: List<FeedbackMessage> = emptyList(),
    val selectedThreadId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCreatingTicket: Boolean = false,
    val newTicketMessage: String = "",
    val newTicketCategory: String = "Bug Report",
    val newTicketAttachments: List<Uri> = emptyList(),
    val chatInputMessage: String = "",
    val chatInputAttachments: List<Uri> = emptyList()
)

class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FeedbackRepository(application)
    private val authRepository = AuthRepository(application)
    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState = _uiState.asStateFlow()

    private var threadsListener: Any? = null
    private var messagesListener: Any? = null
    private val currentUser = authRepository.getSignedInUser()

    init {
        if (currentUser != null) {
            startListeningToThreads(currentUser.uid)
        } else {
            _uiState.update { it.copy(errorMessage = "You must be signed in to use feedback.") }
        }
    }

    private fun startListeningToThreads(userId: String) {
        _uiState.update { it.copy(isLoading = true) }
        threadsListener = repository.listenToFeedbackThreads(userId) { threads ->
            _uiState.update { it.copy(threads = threads, isLoading = false) }
        }
    }

    fun onThreadSelected(threadId: String) {
        Timber.d("VM: onThreadSelected $threadId")
        repository.removeListener(messagesListener)

        _uiState.update {
            it.copy(
                selectedThreadId = threadId,
                isLoading = true,
                currentMessages = emptyList(),
                pendingMessages = emptyList(),
                chatInputMessage = "",
                chatInputAttachments = emptyList()
            )
        }

        viewModelScope.launch {
            repository.markThreadAsRead(threadId)
        }

        messagesListener = repository.listenToMessages(threadId) { messages ->
            Timber.d("VM: Listener update received. ${messages.size} messages.")
            viewModelScope.launch {
                repository.markThreadAsRead(threadId)
            }

            _uiState.update { state ->
                val realIds = messages.map { it.id }.toSet()
                val remainingPending = state.pendingMessages.filter { it.id !in realIds }

                if (remainingPending.size != state.pendingMessages.size) {
                    Timber.d("VM: Reconciliation - Removed ${state.pendingMessages.size - remainingPending.size} pending messages.")
                }

                state.copy(
                    currentMessages = messages,
                    pendingMessages = remainingPending,
                    isLoading = false
                )
            }
        }
    }

    fun onBackToThreadList() {
        Timber.d("VM: Back to thread list")
        repository.removeListener(messagesListener)
        messagesListener = null
        _uiState.update { it.copy(selectedThreadId = null, currentMessages = emptyList(), pendingMessages = emptyList()) }
    }

    fun onStartCreateTicket() {
        if (currentUser == null) {
            _uiState.update { it.copy(errorMessage = "You must be signed in to submit feedback.") }
            return
        }
        _uiState.update { it.copy(isCreatingTicket = true) }
    }

    fun onCancelCreateTicket() {
        _uiState.update { it.copy(isCreatingTicket = false, newTicketMessage = "", newTicketAttachments = emptyList()) }
    }

    fun onNewTicketMessageChange(text: String) {
        _uiState.update { it.copy(newTicketMessage = text) }
    }

    fun onNewTicketCategoryChange(category: String) {
        _uiState.update { it.copy(newTicketCategory = category) }
    }

    fun onNewTicketImagesSelected(uris: List<Uri>) {
        val current = _uiState.value.newTicketAttachments
        if (current.size + uris.size > 3) {
            _uiState.update { it.copy(errorMessage = "Max 3 images allowed for tickets.") }
            return
        }
        validateAndAddImages(uris) { validUris ->
            _uiState.update { it.copy(newTicketAttachments = it.newTicketAttachments + validUris) }
        }
    }

    fun onRemoveNewTicketImage(uri: Uri) {
        _uiState.update { it.copy(newTicketAttachments = it.newTicketAttachments - uri) }
    }

    fun onChatInputChange(text: String) {
        _uiState.update { it.copy(chatInputMessage = text) }
    }

    fun onChatImagesSelected(uris: List<Uri>) {
        val current = _uiState.value.chatInputAttachments
        if (current.size + uris.size > 5) {
            _uiState.update { it.copy(errorMessage = "Max 5 images allowed per message.") }
            return
        }
        validateAndAddImages(uris) { validUris ->
            _uiState.update { it.copy(chatInputAttachments = it.chatInputAttachments + validUris) }
        }
    }

    fun onRemoveChatImage(uri: Uri) {
        _uiState.update { it.copy(chatInputAttachments = it.chatInputAttachments - uri) }
    }

    private fun validateAndAddImages(uris: List<Uri>, onValid: (List<Uri>) -> Unit) {
        val context = getApplication<Application>()
        val maxFileSize = 5 * 1024 * 1024 // 5 MB
        val validUris = mutableListOf<Uri>()
        for (uri in uris) {
            val fileSize = getFileSize(context, uri)
            if (fileSize > maxFileSize) {
                _uiState.update { it.copy(errorMessage = "One or more images exceed the 5MB limit.") }
                return
            }
            validUris.add(uri)
        }
        onValid(validUris)
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (_: Exception) { 0L }
    }

    fun onSubmitTicket() {
        val state = _uiState.value
        if (state.newTicketMessage.isBlank()) return
        val user = currentUser ?: return

        onCancelCreateTicket()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val threadId = repository.createThread(
                    userId = user.uid,
                    category = state.newTicketCategory,
                    message = state.newTicketMessage,
                    attachmentUris = state.newTicketAttachments
                )
                onThreadSelected(threadId)
            } catch (e: Exception) {
                Timber.e(e, "ViewModel: Error submitting ticket")
                _uiState.update { it.copy(errorMessage = "Failed to create ticket: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }


    fun onSendMessage() {
        val state = _uiState.value
        val threadId = state.selectedThreadId ?: return
        val textToSend = state.chatInputMessage.trim()
        val attachmentsToSend = state.chatInputAttachments

        if (textToSend.isBlank() && attachmentsToSend.isEmpty()) return
        val user = currentUser ?: return

        val messageId = repository.generateMessageId()
        Timber.d("VM: Sending message. Generated ID: $messageId")

        // Updated reference to id
        val pendingMessage = FeedbackMessage(
            id = messageId,
            text = textToSend,
            sender = "user",
            timestamp = Date(),
            attachments = attachmentsToSend.map { it.toString() }
        )

        _uiState.update {
            it.copy(
                chatInputMessage = "",
                chatInputAttachments = emptyList(),
                pendingMessages = it.pendingMessages + pendingMessage
            )
        }

        viewModelScope.launch {
            try {
                repository.addMessage(
                    threadId = threadId,
                    messageId = messageId,
                    uid = user.uid,
                    message = textToSend,
                    sender = "user",
                    attachmentUris = attachmentsToSend
                )
                Timber.d("VM: addMessage returned success for $messageId")
            } catch (e: Exception) {
                Timber.e(e, "ViewModel: Error sending message")
                _uiState.update {
                    it.copy(
                        errorMessage = "Failed to send: ${e.message}",
                        // Updated reference to id
                        pendingMessages = it.pendingMessages.filterNot { msg -> msg.id == messageId },
                        chatInputMessage = textToSend
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        repository.removeListener(messagesListener)
        repository.removeListener(threadsListener)
    }
}