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

import android.net.Uri
import timber.log.Timber
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.aryan.reader.R
import com.aryan.reader.data.FeedbackMessage
import com.aryan.reader.data.FeedbackThread
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.Intent
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.outlined.Email
import androidx.core.net.toUri


private fun launchEmailFeedback(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:epistemereader@gmail.com".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "Feedback: Episteme Reader")
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Timber.e(e, "Could not launch email intent")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    navController: NavHostController,
    viewModel: FeedbackViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val activeThreadsCount = remember(uiState.threads) { uiState.threads.count { it.status == "open" } }
    val isLimitReached = activeThreadsCount >= 3

    // State for Tabs
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    BackHandler(enabled = uiState.selectedThreadId != null) {
        viewModel.onBackToThreadList()
    }

    // Helper to determine if current chat is closed
    val currentThread = remember(uiState.selectedThreadId, uiState.threads) {
        uiState.threads.find { it.id == uiState.selectedThreadId }
    }
    val isCurrentChatClosed = currentThread?.status == "closed"

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.selectedThreadId == null) "Help & Feedback" else "Support Chat")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectedThreadId != null) {
                            viewModel.onBackToThreadList()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedThreadId == null) {
                        IconButton(onClick = { launchEmailFeedback(context) }) {
                            Icon(
                                imageVector = Icons.Outlined.Email,
                                contentDescription = "Send Email Feedback"
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.selectedThreadId == null && selectedTabIndex == 0) {
                Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = { if (!isLimitReached) viewModel.onStartCreateTicket() },
                        icon = { Icon(Icons.Default.Add, "New Ticket") },
                        text = { Text("New Ticket") },
                        containerColor = if (isLimitReached) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isLimitReached) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (uiState.selectedThreadId == null) {
                // TABS & LIST
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Active") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Closed") }
                        )
                    }

                    // Limit Hint Text
                    if (selectedTabIndex == 0 && isLimitReached) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Limit of 3 active tickets reached.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val displayedThreads = if (selectedTabIndex == 0) {
                        uiState.threads.filter { it.status == "open" }
                    } else {
                        uiState.threads.filter { it.status == "closed" }
                    }

                    ThreadList(
                        threads = displayedThreads,
                        onThreadClick = { viewModel.onThreadSelected(it.id) },
                        emptyMessage = if (selectedTabIndex == 0) "No active tickets" else "No closed tickets",
                        onEmailClick = { launchEmailFeedback(context) }
                    )
                }
            } else {
                ChatView(
                    messages = uiState.currentMessages,
                    pendingMessages = uiState.pendingMessages,
                    inputMessage = uiState.chatInputMessage,
                    inputAttachments = uiState.chatInputAttachments,
                    onInputChange = { viewModel.onChatInputChange(it) },
                    onAttachmentsSelected = { viewModel.onChatImagesSelected(it) },
                    onRemoveAttachment = { viewModel.onRemoveChatImage(it) },
                    onSend = { viewModel.onSendMessage() },
                    isClosed = isCurrentChatClosed
                )
            }

            if (uiState.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (uiState.isCreatingTicket) {
        CreateTicketDialog(
            message = uiState.newTicketMessage,
            category = uiState.newTicketCategory,
            attachments = uiState.newTicketAttachments,
            onMessageChange = viewModel::onNewTicketMessageChange,
            onCategoryChange = viewModel::onNewTicketCategoryChange,
            onAttachmentsSelected = viewModel::onNewTicketImagesSelected,
            onRemoveAttachment = viewModel::onRemoveNewTicketImage,
            onSubmit = viewModel::onSubmitTicket,
            onDismiss = viewModel::onCancelCreateTicket
        )
    }
}

@Composable
fun ThreadList(
    threads: List<FeedbackThread>,
    onThreadClick: (FeedbackThread) -> Unit,
    emptyMessage: String = "No conversations yet",
    onEmailClick: () -> Unit
) {
    if (threads.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Prefer email or can't sign in?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    TextButton(onClick = onEmailClick) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contact via Email")
                    }
                }
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(threads, key = { it.id }) { thread ->
                ThreadItem(thread = thread, onClick = { onThreadClick(thread) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ThreadItem(
    thread: FeedbackThread,
    onClick: () -> Unit
) {
    val dateStr = remember(thread.lastUpdated) {
        thread.lastUpdated?.let {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(it)
        } ?: "Just now"
    }

    val itemAlpha = if (thread.status == "closed") 0.6f else 1f

    ListItem(
        modifier = Modifier
            .clickable(onClick = onClick)
            .graphicsLayer { alpha = itemAlpha },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(thread.category, fontWeight = FontWeight.Bold)
                if (thread.hasUnreadAdminReply) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                    )
                }
            }
        },
        supportingContent = {
            val isPreviewEmpty = thread.preview.isBlank()
            val previewText = if (isPreviewEmpty) "Attached Image" else thread.preview

            Text(
                text = previewText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontStyle = if (isPreviewEmpty) FontStyle.Italic else FontStyle.Normal
            )
        },
        trailingContent = {
            Text(dateStr, style = MaterialTheme.typography.labelSmall)
        }
    )
}

@Composable
fun ChatView(
    messages: List<FeedbackMessage>,
    pendingMessages: List<FeedbackMessage>,
    inputMessage: String,
    inputAttachments: List<Uri>,
    onInputChange: (String) -> Unit,
    onAttachmentsSelected: (List<Uri>) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSend: () -> Unit,
    isClosed: Boolean = false
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5),
        onResult = { uris -> if (uris.isNotEmpty()) onAttachmentsSelected(uris) }
    )
    val listState = rememberLazyListState()
    val displayedMessages = remember(messages, pendingMessages) {
        val realIds = messages.map { it.id }.toSet()
        val uniquePending = pendingMessages.filter { it.id !in realIds }

        Timber.d("ChatView Recomposition: ${messages.size} real, ${uniquePending.size} pending unique")

        messages + uniquePending
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LaunchedEffect(displayedMessages.size) {
            if (displayedMessages.isNotEmpty()) {
                listState.animateScrollToItem(displayedMessages.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(displayedMessages, key = { it.id }) { msg ->
                MessageBubble(
                    message = msg,
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                )
            }
        }

        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isClosed) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This ticket is closed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    if (inputAttachments.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(inputAttachments) { uri ->
                                Box(modifier = Modifier.size(60.dp)) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                                            .padding(2.dp)
                                            .clickable { onRemoveAttachment(uri) },
                                        tint = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.image),
                                contentDescription = "Add Image",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = inputMessage,
                            onValueChange = onInputChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            maxLines = 3,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onSend,
                            enabled = inputMessage.isNotBlank() || inputAttachments.isNotEmpty()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputMessage.isNotBlank() || inputAttachments.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageBubble(
    message: FeedbackMessage,
    modifier: Modifier = Modifier
) {
    val isMe = message.sender == "user"
    val alignment = if (isMe) Alignment.End else Alignment.Start
    val color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = color,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.attachments.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(bottom = if(message.text.isNotEmpty()) 8.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        message.attachments.forEach { url ->
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Attachment",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.broken_image),
                                            contentDescription = "Image unavailable",
                                            modifier = Modifier.padding(24.dp).fillMaxSize(),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                if (message.text.isNotEmpty()) {
                    Text(
                        text = message.text,
                        color = textColor
                    )
                }
            }
        }

        message.timestamp?.let {
            Text(
                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(it),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketDialog(
    message: String,
    category: String,
    attachments: List<Uri>,
    onMessageChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAttachmentsSelected: (List<Uri>) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3),
        onResult = { uris -> if (uris.isNotEmpty()) onAttachmentsSelected(uris) }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Ticket") },
        text = {
            Column {
                val categories = listOf("Bug Report", "Feature Request", "Feedback", "Other")
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    categories.take(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { onCategoryChange(cat) },
                            label = { Text(cat) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = message,
                    onValueChange = onMessageChange,
                    label = { Text("Describe your issue...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { imagePickerLauncher.launch(PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    ) {
                        Icon(painter = painterResource(id = R.drawable.image), contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Image")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "${attachments.size}/3",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (attachments.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(attachments) { uri ->
                            Box(modifier = Modifier.size(60.dp)) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.TopEnd)
                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), CircleShape)
                                        .padding(2.dp)
                                        .clickable { onRemoveAttachment(uri) },
                                    tint = androidx.compose.ui.graphics.Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = message.isNotBlank()
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}