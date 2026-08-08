package com.aryan.reader.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Ai
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.AddBooksSource
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.LibraryFilters
import com.aryan.reader.shared.IN_APP_STORAGE_SOURCE
import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.PdfReaderTool
import com.aryan.reader.shared.PdfToolbarPreferences
import com.aryan.reader.shared.isPdfReaderToolEnabledDuringTts
import com.aryan.reader.shared.PdfTocEntry
import com.aryan.reader.shared.ReadStatusFilter
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderPlatform
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SharedAudiobook
import com.aryan.reader.shared.SharedAudiobookPlaybackState
import com.aryan.reader.shared.SharedBookTtsListenState
import com.aryan.reader.shared.SharedBookTtsListeningProgress
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.SharedTtsListenItem
import com.aryan.reader.shared.SharedTtsListenStartPolicy
import com.aryan.reader.shared.Shelf
import com.aryan.reader.shared.ShelfType
import com.aryan.reader.shared.SearchHighlightMode
import com.aryan.reader.shared.SortOrder
import com.aryan.reader.shared.SyncedFolder
import com.aryan.reader.shared.Tag
import com.aryan.reader.shared.SharedFileCapabilities
import com.aryan.reader.shared.UserData
import com.aryan.reader.shared.cardAuthor
import com.aryan.reader.shared.cardTitle
import com.aryan.reader.shared.canAddSyncedFolder
import com.aryan.reader.shared.canExportOriginalFile
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.resolveReaderTheme
import com.aryan.reader.shared.applyLibraryFilters
import com.aryan.reader.shared.booksAvailableForShelfAddition
import com.aryan.reader.shared.buildSharedTtsListenItems
import com.aryan.reader.shared.opds.OpdsAcquisition
import com.aryan.reader.shared.opds.OpdsCatalog
import com.aryan.reader.shared.opds.OpdsEntry
import com.aryan.reader.shared.opds.SharedOpdsScreenState
import com.aryan.reader.shared.pdf.PdfAnnotationKind
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.sharedPdfIsInkDownAllowed
import com.aryan.reader.shared.pdf.sharedPdfIsEraserOverride
import com.aryan.reader.shared.sharedPdfStylusBarrelPressed
import com.aryan.reader.shared.pdf.PdfTtsSessionPlanner
import com.aryan.reader.shared.pdf.shouldStopPdfTtsForManualPageTurn
import com.aryan.reader.shared.pdf.pdfAutoScrollPixelsPerSecond
import com.aryan.reader.shared.pdf.PdfAutoScrollProfile
import com.aryan.reader.shared.pdf.PdfMusicianHoldDurationMillis
import com.aryan.reader.shared.pdf.planPdfMusicianGesture
import com.aryan.reader.shared.pdf.PdfPageBounds
import com.aryan.reader.shared.pdf.PdfPagePoint
import com.aryan.reader.shared.pdf.pdfPaginationEdgeTarget
import com.aryan.reader.shared.pdf.initialSharedPdfReaderState
import com.aryan.reader.shared.pdf.PdfNavigationReason
import com.aryan.reader.shared.pdf.PdfChromeMotionDurationMillis
import com.aryan.reader.shared.pdf.centeredPdfPageScrollOffset
import com.aryan.reader.shared.pdf.animatesPagination
import com.aryan.reader.shared.pdf.PdfSpreadLayout
import com.aryan.reader.shared.pdf.PdfZoomCamera
import com.aryan.reader.shared.pdf.PdfZoomPoint
import com.aryan.reader.shared.pdf.PdfZoomSize
import com.aryan.reader.shared.pdf.isZoomed
import com.aryan.reader.shared.reader.ReaderScreenOrientationMode
import com.aryan.reader.shared.pdf.pdfDoubleTapTargetScale
import com.aryan.reader.shared.pdf.pdfVerticalDoubleTapTargetScale
import com.aryan.reader.shared.pdf.pdfZoomIndicatorPercent
import com.aryan.reader.shared.pdf.visiblePdfPageBounds
import com.aryan.reader.shared.pdf.SharedPdfAnnotation
import com.aryan.reader.shared.pdf.SharedPdfBookmark
import com.aryan.reader.shared.pdf.SharedPdfAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.SharedPdfTextAnnotationDefaults
import com.aryan.reader.shared.pdf.SharedPdfTextDraft
import com.aryan.reader.shared.pdf.SharedPdfTextDragState
import com.aryan.reader.shared.pdf.SharedPdfTextStyleConfig
import com.aryan.reader.shared.pdf.sharedPdfTextDropBounds
import com.aryan.reader.shared.pdf.containsNormalizedPoint
import com.aryan.reader.shared.pdf.sharedPdfTextStyle
import com.aryan.reader.shared.pdf.toAnnotation
import com.aryan.reader.shared.pdf.withBounds
import com.aryan.reader.shared.pdf.withStyle
import com.aryan.reader.shared.pdf.withText
import com.aryan.reader.shared.pdf.SharedPdfReaderAction
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.pdf.SharedPdfJumpHistory
import com.aryan.reader.shared.pdf.SharedPdfSearchResult
import com.aryan.reader.shared.pdf.SharedPdfVirtualPage
import com.aryan.reader.shared.pdf.SharedPdfBlankPageInsertion
import com.aryan.reader.shared.pdf.buildSharedPdfVirtualPageLayout
import com.aryan.reader.shared.pdf.sharedPdfPdfPageIndexAt
import com.aryan.reader.shared.pdf.sharedPdfTextFontSizePx
import com.aryan.reader.shared.pdf.sharedPdfTextPageRelativeFontSize
import com.aryan.reader.shared.pdf.sharedPdfDisplayIndexFor
import com.aryan.reader.shared.pdf.sharedPdfNearestPdfPageIndex
import com.aryan.reader.shared.pdf.reduce
import com.aryan.reader.shared.pdf.sharedPdfPageRangeLabel
import com.aryan.reader.shared.pdf.SharedPdfKeyboardNavigationAction
import com.aryan.reader.shared.pdf.sharedPdfKeyboardNavigationAction
import com.aryan.reader.shared.pdf.sharedPdfHighlightAllColors
import com.aryan.reader.shared.pdf.sharedPdfMergeRectsIntoLines
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.SystemUiMode
import com.aryan.reader.shared.sortBooks
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.classy_fabric
import com.aryan.reader.shared.generated.resources.ep_naturalwhite
import com.aryan.reader.shared.generated.resources.grey_wash_wall
import com.aryan.reader.shared.generated.resources.light_veneer
import com.aryan.reader.shared.generated.resources.retina_wood
import com.aryan.reader.shared.generated.resources.retro_intro
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.imageResource
import kotlin.math.roundToInt
import kotlin.time.TimeSource

@Composable
fun SharedMobileAppDrawerContent(
    currentUser: UserData?,
    isProUser: Boolean,
    credits: Int,
    isSyncEnabled: Boolean,
    isFolderSyncEnabled: Boolean,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onSyncToggle: (Boolean) -> Unit,
    onFolderSyncToggle: (Boolean) -> Unit,
    onProClick: () -> Unit,
    onFontsClick: () -> Unit,
    onAiSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppThemeClick: () -> Unit,
    onFeedbackClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onLicensesClick: () -> Unit,
    isStandardEdition: Boolean = false,
    aiSettingsAvailable: Boolean = true,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (currentUser != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = currentUser.displayName ?: currentUser.email ?: "Signed in",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    currentUser.email?.let { email ->
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                if (isStandardEdition) "Standard version" else "$credits credits",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = null) },
                    label = { Text("Sign in with Google") },
                    selected = false,
                    onClick = onSignInClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = if (isStandardEdition) "Sync account and app settings." else "Sync account, Pro features, and credits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null) },
                label = {
                    Text(
                        when {
                            isStandardEdition -> "Standard version"
                            isProUser -> "Pro unlocked"
                            else -> "Upgrade to Pro"
                        }
                    )
                },
                selected = false,
                onClick = onProClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                label = { Text("Sync library") },
                selected = false,
                onClick = { onSyncToggle(!isSyncEnabled) },
                badge = { Switch(checked = isSyncEnabled, onCheckedChange = onSyncToggle) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (isSyncEnabled) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null) },
                    label = {
                        Column {
                            Text("Backup local folders")
                            Text(
                                "Keep folder metadata synced.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    selected = false,
                    onClick = { onFolderSyncToggle(!isFolderSyncEnabled) },
                    badge = { Switch(checked = isFolderSyncEnabled, onCheckedChange = onFolderSyncToggle) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = false,
                onClick = onSettingsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                label = { Text("App theme") },
                selected = false,
                onClick = onAppThemeClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                label = { Text("Custom fonts") },
                selected = false,
                onClick = onFontsClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (aiSettingsAvailable) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Ai, contentDescription = null) },
                    label = { Text("AI settings") },
                    selected = false,
                    onClick = onAiSettingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Feedback, contentDescription = null) },
                label = { Text("Help & Feedback") },
                selected = false,
                onClick = onFeedbackClick,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )

            if (currentUser != null) {
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = null) },
                    label = { Text("Sign out") },
                    selected = false,
                    onClick = onSignOutClick,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))
            val legalFooterBaseStyle = MaterialTheme.typography.labelMedium
            var legalFooterStyle by remember { mutableStateOf(legalFooterBaseStyle) }
            Text(
                text = readerString("legal_footer_combined", "Privacy Policy  •  Terms of Service  •  Licenses"),
                style = legalFooterStyle,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    if (result.didOverflowWidth) {
                        legalFooterStyle = legalFooterStyle.copy(fontSize = legalFooterStyle.fontSize * 0.95)
                    }
                },
                modifier = Modifier.height(0.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = readerString("legal_privacy_policy", "Privacy Policy"),
                    style = legalFooterStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onPrivacyPolicyClick),
                )
                Text("  •  ", style = legalFooterStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = readerString("legal_terms_of_service", "Terms of Service"),
                    style = legalFooterStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onTermsClick),
                )
                Text("  •  ", style = legalFooterStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = readerString("legal_licenses", "Licenses"),
                    style = legalFooterStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier.clickable(onClick = onLicensesClick),
                )
            }
        }
    }
}

enum class SharedMobilePdfNativeAction {
    DICTIONARY_SETTINGS,
    INSERT_BLANK_PAGE,
    SHARE,
    SAVE_COPY,
    PRINT,
    TEXT_VIEW,
}

data class SharedMobilePdfReflowUiState(
    val isGenerating: Boolean = false,
    val progress: Float = 0f,
    val hasReflowBook: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobilePdfReaderScreen(
    book: BookItem,
    onBack: () -> Unit,
    onNativePdfAction: (BookItem, SharedMobilePdfNativeAction) -> Unit,
    pdfReflowUiState: SharedMobilePdfReflowUiState = SharedMobilePdfReflowUiState(),
    pdfTabsEnabled: Boolean = false,
    openPdfTabs: List<BookItem> = emptyList(),
    activePdfTabBookId: String? = null,
    availablePdfTabBooks: List<BookItem> = emptyList(),
    pdfTopTabStripVisible: Boolean = true,
    onPdfTopTabStripVisibilityChange: (Boolean) -> Unit = {},
    onOpenPdfTab: (BookItem) -> Unit = {},
    onClosePdfTab: (BookItem) -> Unit = {},
    onBookInfoChange: (BookItem) -> Unit = {},
    knownTags: List<Tag> = emptyList(),
    pdfToolbarPreferences: PdfToolbarPreferences = PdfToolbarPreferences(),
    onPdfToolbarPreferencesChange: (PdfToolbarPreferences) -> Unit = {},
    readerBrightness: Float? = null,
    readerCustomBrightness: Float = com.aryan.reader.shared.DefaultReaderCustomBrightness,
    onReaderBrightnessChange: (Float?) -> Unit = {},
    readerScreenOrientationMode: ReaderScreenOrientationMode = ReaderScreenOrientationMode.FOLLOW_SYSTEM,
    onReaderScreenOrientationModeChange: (ReaderScreenOrientationMode) -> Unit = {},
    onApplyReaderScreenOrientation: (ReaderScreenOrientationMode) -> Unit = {},
    readerTtsReplacementPreferences: ReaderTtsReplacementPreferences = ReaderTtsReplacementPreferences(),
    onReaderTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit = {},
    onTtsError: ((String) -> Unit)? = null,
    initialReaderState: SharedPdfReaderState? = null,
    readerDefaultSettings: ReaderSettings = ReaderSettings(themeId = "no_theme"),
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit = {},
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    initialKeepScreenOn: Boolean = false,
    onKeepScreenOnPreferenceChange: (Boolean) -> Unit = {},
    initialStylusOnlyMode: Boolean = false,
    onStylusOnlyModePreferenceChange: (Boolean) -> Unit = {},
    initialPageSliderVisible: Boolean = false,
    onPageSliderVisibilityPreferenceChange: (Boolean) -> Unit = {},
    onReaderStateChange: (SharedPdfReaderState) -> Unit = {},
    pdfAutoScrollGlobalProfile: PdfAutoScrollProfile = PdfAutoScrollProfile(),
    onPdfAutoScrollGlobalProfileChange: (PdfAutoScrollProfile) -> Unit = {},
    initialPdfAutoScrollMusicianMode: Boolean = false,
    onPdfAutoScrollMusicianModeChange: (Boolean) -> Unit = {},
    initialPdfAutoScrollUseSlider: Boolean = false,
    onPdfAutoScrollUseSliderChange: (Boolean) -> Unit = {},
    onPdfAutoScrollBookChange: (BookItem) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onSystemUiAppearanceChange: (hidden: Boolean, lightContent: Boolean, backgroundArgb: Long, edgeToEdge: Boolean) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val pdfCardTitle = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current)
    val initialPage = book.lastPageIndex?.coerceAtLeast(0) ?: 0
    var readerState by remember(book.id, initialReaderState) {
        mutableStateOf(
            initialSharedPdfReaderState(
                persistedState = initialReaderState,
                defaults = readerDefaultSettings,
                initialPageIndex = initialPage,
            )
        )
    }
    // Android intentionally starts every PDF session distraction-free.
    var showChrome by remember(book.id) { mutableStateOf(false) }
    var showReaderOptions by remember(book.id) { mutableStateOf(false) }
    var showThemePanel by remember(book.id) { mutableStateOf(false) }
    var showPageSlider by remember(book.id) { mutableStateOf(initialPageSliderVisible) }
    var showFileInformation by remember(book.id) { mutableStateOf(false) }
    var showBrightnessSheet by remember(book.id) { mutableStateOf(false) }
    var showScreenOrientationSheet by remember(book.id) { mutableStateOf(false) }
    var showToolbarCustomization by remember(book.id) { mutableStateOf(false) }
    var showTtsSettingsSheet by remember(book.id) { mutableStateOf(false) }
    var showTtsReplacementsSheet by remember(book.id) { mutableStateOf(false) }
    var showNewPdfTabSheet by remember(book.id) { mutableStateOf(false) }
    var pendingExternalLink by remember(book.id) { mutableStateOf<String?>(null) }
    var pdfPassword by remember(book.id) { mutableStateOf<String?>(null) }
    var pdfPasswordDraft by remember(book.id) { mutableStateOf("") }
    var showPasswordProtectedPrintWarning by remember(book.id) { mutableStateOf(false) }
    var showVerticalPageGap by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pdfVerticalPageGapVisible)
    }
    var showPageNumberOverlay by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pdfPageNumberOverlayVisible)
    }
    var systemUiMode by remember(book.id) {
        mutableStateOf(readerDefaultSettings.systemUiMode.toSharedMobilePdfSystemUiMode())
    }
    var rightToLeftPagination by remember(book.id) {
        mutableStateOf(readerDefaultSettings.rightToLeftPagination)
    }
    var useTwoPageSpread by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE)
    }
    var firstPageStandaloneInSpread by remember(book.id) {
        mutableStateOf(readerDefaultSettings.pdfFirstPageStandaloneInSpread)
    }
    var globalTextureTransparency by remember(book.id) {
        mutableStateOf(1f - readerDefaultSettings.textureAlpha.coerceIn(0f, 1f))
    }
    var keepScreenOn by remember(book.id) { mutableStateOf(initialKeepScreenOn) }
    var isStylusOnlyMode by remember(book.id) { mutableStateOf(initialStylusOnlyMode) }
    var autoScrollModeActive by remember(book.id) { mutableStateOf(false) }
    var autoScrollPlaying by remember(book.id) { mutableStateOf(false) }
    var autoScrollTemporarilyPaused by remember(book.id) { mutableStateOf(false) }
    var autoScrollIsLocal by remember(book.id) { mutableStateOf(book.pdfAutoScrollIsLocal) }
    var autoScrollProfile by remember(book.id) {
        mutableStateOf(
            if (book.pdfAutoScrollIsLocal && book.pdfAutoScrollLocalSpeed != null) {
                PdfAutoScrollProfile(
                    speed = book.pdfAutoScrollLocalSpeed,
                    minSpeed = book.pdfAutoScrollLocalMinSpeed ?: 0.1f,
                    maxSpeed = book.pdfAutoScrollLocalMaxSpeed ?: 10f,
                ).sanitized()
            } else {
                pdfAutoScrollGlobalProfile.sanitized()
            }
        )
    }
    var autoScrollMusicianMode by remember(book.id) { mutableStateOf(initialPdfAutoScrollMusicianMode) }
    var autoScrollUseSlider by remember(book.id) { mutableStateOf(initialPdfAutoScrollUseSlider) }
    var autoScrollCollapsed by remember(book.id) { mutableStateOf(false) }
    var autoScrollInteractionToken by remember(book.id) { mutableStateOf(0) }
    var autoScrollPauseDurationMillis by remember(book.id) { mutableStateOf(300L) }
    var tapToTurnPages by remember(book.id) { mutableStateOf(true) }
    var pdfSliderScrubbingPage by remember(book.id) { mutableStateOf<Int?>(null) }
    var showAllTextHighlights by remember(book.id) { mutableStateOf(false) }
    var isAllTextHighlightLoading by remember(book.id) { mutableStateOf(false) }
    fun toggleAllTextHighlights() {
        if (!showAllTextHighlights && !isAllTextHighlightLoading) {
            showAllTextHighlights = true
            isAllTextHighlightLoading = true
        } else if (showAllTextHighlights) {
            showAllTextHighlights = false
            isAllTextHighlightLoading = false
        }
    }
    val sanitizedPdfToolbarPreferences = pdfToolbarPreferences.sanitized(SharedMobilePdfAvailableTools)
    val visiblePdfTools = sanitizedPdfToolbarPreferences.toolOrder.filter(sanitizedPdfToolbarPreferences::isVisible)
    val pdfTopTools = visiblePdfTools.filter { it.supportsToolbarPlacement && !sanitizedPdfToolbarPreferences.isBottom(it) }
    val pdfBottomTools = visiblePdfTools.filter { it.supportsToolbarPlacement && sanitizedPdfToolbarPreferences.isBottom(it) }

    fun updateAutoScrollProfile(profile: PdfAutoScrollProfile) {
        val sanitized = profile.sanitized()
        autoScrollProfile = sanitized
        if (autoScrollIsLocal) {
            onPdfAutoScrollBookChange(
                book.copy(
                    pdfAutoScrollIsLocal = true,
                    pdfAutoScrollLocalSpeed = sanitized.speed,
                    pdfAutoScrollLocalMinSpeed = sanitized.minSpeed,
                    pdfAutoScrollLocalMaxSpeed = sanitized.maxSpeed,
                )
            )
        } else {
            onPdfAutoScrollGlobalProfileChange(sanitized)
        }
    }

    fun setAutoScrollLocalMode(local: Boolean) {
        if (local == autoScrollIsLocal) return
        autoScrollIsLocal = local
        if (local) {
            val profile = if (book.pdfAutoScrollLocalSpeed != null) {
                PdfAutoScrollProfile(
                    book.pdfAutoScrollLocalSpeed,
                    book.pdfAutoScrollLocalMinSpeed ?: 0.1f,
                    book.pdfAutoScrollLocalMaxSpeed ?: 10f,
                ).sanitized()
            } else {
                autoScrollProfile
            }
            autoScrollProfile = profile
            onPdfAutoScrollBookChange(
                book.copy(
                    pdfAutoScrollIsLocal = true,
                    pdfAutoScrollLocalSpeed = profile.speed,
                    pdfAutoScrollLocalMinSpeed = profile.minSpeed,
                    pdfAutoScrollLocalMaxSpeed = profile.maxSpeed,
                )
            )
        } else {
            autoScrollProfile = pdfAutoScrollGlobalProfile.sanitized()
            onPdfAutoScrollBookChange(book.copy(pdfAutoScrollIsLocal = false))
        }
    }

    LaunchedEffect(pdfAutoScrollGlobalProfile, autoScrollIsLocal) {
        if (!autoScrollIsLocal) autoScrollProfile = pdfAutoScrollGlobalProfile.sanitized()
    }

    DisposableEffect(readerScreenOrientationMode, onApplyReaderScreenOrientation) {
        onApplyReaderScreenOrientation(readerScreenOrientationMode)
        onDispose { onApplyReaderScreenOrientation(ReaderScreenOrientationMode.FOLLOW_SYSTEM) }
    }
    var pdfZoomCamera by remember(book.id, initialReaderState) {
        mutableStateOf(
            initialReaderState?.takeIf { it.isScrollLocked }?.let {
                PdfZoomCamera(it.lockedZoomScale, PdfZoomPoint(it.lockedZoomOffsetX, it.lockedZoomOffsetY))
            } ?: PdfZoomCamera()
        )
    }
    var navigationRequestPage by remember(book.id) { mutableStateOf(readerState.pageIndex) }
    var navigationRequestToken by remember(book.id) { mutableStateOf(0) }
    var navigationCenterFraction by remember(book.id) { mutableStateOf(0.5f) }
    var navigationReason by remember(book.id) { mutableStateOf(PdfNavigationReason.INITIAL) }
    var jumpHistory by remember(book.id) { mutableStateOf(SharedPdfJumpHistory()) }
    val pdfTts = rememberSharedMobileEpubLocalTts()
    LaunchedEffect(pdfTts.errorMessage) {
        pdfTts.errorMessage?.let { message -> onTtsError?.invoke(message) }
    }
    var ttsPageIndex by remember(book.id) { mutableStateOf(readerState.pageIndex) }
    var pendingTtsStart by remember(book.id) { mutableStateOf<Int?>(null) }
    var pendingTtsStartAtLastChunk by remember(book.id) { mutableStateOf(false) }
    var pendingTtsPlayWhenReady by remember(book.id) { mutableStateOf(true) }
    var ttsHighlightBounds by remember(book.id) { mutableStateOf<List<PdfPageBounds>>(emptyList()) }
    var lastTtsCompletionCount by remember(book.id) { mutableStateOf(pdfTts.completionCount) }
    val ttsTextSession = rememberPdfTextPageSession(book, ttsPageIndex, pdfPassword)
    var searchResults by remember(book.id) { mutableStateOf<List<SharedPdfSearchResult>>(emptyList()) }
    var tableOfContents by remember(book.id) { mutableStateOf<List<PdfTocEntry>>(emptyList()) }
    var noteAnnotationId by remember(book.id) { mutableStateOf<String?>(null) }
    var isSearchInProgress by remember(book.id) { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pdfReaderFocusRequester = remember(book.id) { FocusRequester() }
    val pdfVerticalScrollController = remember(book.id) { SharedMobilePdfVerticalScrollController() }
    LaunchedEffect(book.id) {
        runCatching { pdfReaderFocusRequester.requestFocus() }
    }
    var richTextDocumentJson by remember(book.id) { mutableStateOf(initialReaderState?.richTextDocumentJson.orEmpty()) }
    val richTextController = remember(book.id, initialReaderState) {
        SharedPdfRichTextController(
            scope = scope,
            initialDocument = SharedPdfRichTextSerializer.decode(initialReaderState?.richTextDocumentJson.orEmpty()),
            onDocumentChange = { document ->
                richTextDocumentJson = SharedPdfRichTextSerializer.encode(document)
            }
        )
    }
    DisposableEffect(book.id, richTextController) {
        onDispose { scope.launch { richTextController.saveImmediate() } }
    }
    // Document metadata must not follow the visible page. A newly requested page starts with
    // SharedMobilePdfPageRender's loading value (pageCount = 1); using that transient value here
    // used to collapse the list/pager to page zero every time the user changed pages.
    val documentRender = rememberSharedMobilePdfPageRender(book, 0, password = pdfPassword)
    val pageCount = if (documentRender.bitmap != null || documentRender.errorMessage != null) {
        documentRender.pageCount.coerceAtLeast(1)
    } else {
        readerState.pageCount.coerceAtLeast(1)
    }
    val virtualLayout = remember(pageCount, readerState.blankPageInsertions) {
        buildSharedPdfVirtualPageLayout(pageCount, readerState.blankPageInsertions)
    }
    val displayPageCount = virtualLayout.size
    val currentPdfIndex = readerState.currentNearestPdfPageIndex ?: 0
    val currentPageRender = rememberSharedMobilePdfPageRender(book, currentPdfIndex, password = pdfPassword)
    val isCurrentPageBlank = (virtualLayout.getOrNull(readerState.pageIndex) as? SharedPdfVirtualPage.BlankPage) != null
    val prefetchedTtsPageIndex = (ttsPageIndex + 1).coerceAtMost(pageCount - 1)
    val prefetchedTtsTextSession = rememberPdfTextPageSession(book, prefetchedTtsPageIndex, pdfPassword)
    val activeTheme = remember(readerState.themeId, customReaderThemes) {
        resolveReaderTheme(readerState.themeId, BuiltInPdfReaderThemes, customReaderThemes)
            ?: BuiltInPdfReaderThemes.first()
    }
    val systemBarColor = MaterialTheme.colorScheme.surface
    val hideSystemUi = when (systemUiMode) {
        SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> false
        SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> !showChrome
        SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> true
    }
    val edgeToEdgeSystemUi = systemUiMode != SharedMobilePdfSystemUiMode.ALWAYS_SHOW
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val systemNavigationInset = with(density) {
        WindowInsets.safeDrawing.getBottom(density).toDp()
    }
    val effectiveBottomSystemInset = when (systemUiMode) {
        SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> systemNavigationInset
        SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> if (showChrome) systemNavigationInset else 0.dp
        SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> 0.dp
    }
    val pdfBottomChromePadding = 56.dp + effectiveBottomSystemInset
    val isJumpHistoryVisible = showChrome && !readerState.isSearchActive && jumpHistory.hasJumpTargets
    val isPdfTtsPlayingOrLoading =
        pdfTts.state == SharedMobileEpubLocalTtsState.SPEAKING || pendingTtsStart != null
    val pdfSliderBottomPadding = pdfBottomChromePadding + if (isJumpHistoryVisible) 40.dp else 0.dp
    LaunchedEffect(hideSystemUi, systemBarColor, edgeToEdgeSystemUi) {
        onSystemUiAppearanceChange(
            hideSystemUi,
            systemBarColor.luminance() < 0.5f,
            systemBarColor.toArgb().toLong(),
            edgeToEdgeSystemUi
        )
    }
    DisposableEffect(Unit) {
        onDispose { onSystemUiAppearanceChange(false, false, 0xFFFFFFFFL, false) }
    }
    var canvasSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    val activeStroke = remember(book.id, readerState.pageIndex) { mutableStateListOf<PdfPagePoint>() }
    var textStyle by remember(book.id) { mutableStateOf(SharedPdfTextStyleConfig()) }
    var textDraft by remember(book.id) { mutableStateOf<SharedPdfTextDraft?>(null) }
    val isRichTextEditingEnabled = readerState.selectedTool == PdfInkTool.TEXT && textDraft == null

    fun dispatch(action: SharedPdfReaderAction) {
        readerState = readerState.reduce(action)
    }

    fun navigateToPage(
        pageIndex: Int,
        recordHistory: Boolean = true,
        centerFraction: Float = 0.5f,
        reason: PdfNavigationReason = PdfNavigationReason.PAGE_SLIDER
    ) {
        val target = pageIndex.coerceIn(0, (displayPageCount - 1).coerceAtLeast(0))
        if (recordHistory) jumpHistory = jumpHistory.record(readerState.pageIndex, target, displayPageCount)
        dispatch(SharedPdfReaderAction.GoToPage(target))
        navigationRequestPage = target
        navigationCenterFraction = centerFraction.coerceIn(0f, 1f)
        navigationReason = reason
        navigationRequestToken++
    }

    fun requestTts(
        pageIndex: Int = readerState.pageIndex,
        startCharIndex: Int = 0,
        startAtLastChunk: Boolean = false,
        playWhenReady: Boolean = true
    ) {
        val target = pageIndex.coerceIn(0, (displayPageCount - 1).coerceAtLeast(0))
        pdfTts.prepare()
        ttsPageIndex = sharedPdfPdfPageIndexAt(virtualLayout, target)
            ?: sharedPdfNearestPdfPageIndex(virtualLayout, target)
            ?: target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pendingTtsStart = startCharIndex.coerceAtLeast(0)
        pendingTtsStartAtLastChunk = startAtLastChunk
        pendingTtsPlayWhenReady = playWhenReady
        navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.TTS)
    }

    fun stopPdfTtsForManualPagination() {
        if (
            shouldStopPdfTtsForManualPageTurn(
                isPaginationMode = readerState.displayMode == PdfDisplayMode.PAGINATION,
                isUserInitiated = true,
                isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
            )
        ) {
            pdfTts.stop()
            pendingTtsStart = null
            pendingTtsStartAtLastChunk = false
            ttsHighlightBounds = emptyList()
        }
    }

    fun toggleDisplayMode() {
        navigationRequestPage = readerState.pageIndex
        navigationRequestToken++
        dispatch(SharedPdfReaderAction.DisplayModeToggled)
    }

    fun navigateToSearchResult(resultIndex: Int) {
        if (searchResults.isEmpty()) return
        val previousPage = readerState.pageIndex
        readerState = readerState.reduce(SharedPdfReaderAction.GoToSearchResult(resultIndex, searchResults))
        val resolvedIndex = readerState.activeSearchResultIndex
        searchResults.getOrNull(resolvedIndex)?.let { result ->
            val targetDisplay = sharedPdfDisplayIndexFor(virtualLayout, result.pageIndex)
            jumpHistory = jumpHistory.record(previousPage, targetDisplay, displayPageCount)
            navigationRequestPage = targetDisplay
            navigationCenterFraction = result.boundsList.centerYFraction()
            navigationReason = PdfNavigationReason.SEARCH_RESULT
            navigationRequestToken++
        }
    }

    fun insertBlankPageAtCurrentPosition() {
        val insertAt = readerState.pageIndex.coerceIn(0, (displayPageCount - 1).coerceAtLeast(0))
        val aspectRatio = currentPageRender.aspectRatio.coerceIn(0.1f, 10f)
        dispatch(
            SharedPdfReaderAction.InsertBlankPageAt(
                displayIndex = insertAt,
                widthPx = 1000f,
                heightPx = 1000f / aspectRatio,
                id = "blank_${currentTimestamp()}"
            )
        )
        navigationRequestPage = readerState.pageIndex
        navigationCenterFraction = 0.5f
        navigationReason = PdfNavigationReason.PAGE_SLIDER
        navigationRequestToken++
    }

    fun deleteBlankPageAtCurrentPosition() {
        dispatch(SharedPdfReaderAction.DeleteBlankPageAt(readerState.pageIndex))
        navigationRequestPage = readerState.pageIndex
        navigationCenterFraction = 0.5f
        navigationReason = PdfNavigationReason.PAGE_SLIDER
        navigationRequestToken++
    }

    // Mirrors Android's onInsertTextBox: a fixed default box (0.4 x 0.1 at 0.3, 0.45) on the
    // current page, styled with the current text style. isManuallySized keeps the bounds fixed
    // while typing, matching Android's fixed PdfTextBox bounds.
    fun insertTextBox() {
        val pageIndex = readerState.currentNearestPdfPageIndex ?: 0
        textDraft = SharedPdfTextDraft(
            id = "ios_pdf_textbox_${currentTimestamp()}_${readerState.annotations.size}",
            pageIndex = pageIndex,
            bounds = PdfPageBounds(left = 0.3f, top = 0.45f, right = 0.7f, bottom = 0.55f),
            text = "",
            style = textStyle,
            createdAt = currentTimestamp(),
            isManuallySized = true
        )
    }

    fun startEditingTextBox(annotation: SharedPdfAnnotation) {
        textDraft = SharedPdfTextDraft(
            id = annotation.id,
            pageIndex = annotation.pageIndex,
            bounds = annotation.bounds ?: PdfPageBounds(left = 0.3f, top = 0.45f, right = 0.7f, bottom = 0.55f),
            text = annotation.text,
            style = annotation.sharedPdfTextStyle(),
            createdAt = annotation.createdAt,
            isManuallySized = true
        )
    }

    fun updateTextDraft(draft: SharedPdfTextDraft) {
        textDraft = draft
    }

    // Mirrors Android's single-tap deselect: an empty box is removed, a non-empty one is kept.
    // New boxes are added; boxes that already exist as annotations are updated (or deleted when
    // their text was cleared, matching Android removing the empty box from the document).
    fun dismissTextDraft() {
        val draft = textDraft ?: return
        textDraft = null
        val isExisting = readerState.annotations.any { it.id == draft.id }
        if (SharedPdfTextAnnotationDefaults.normalizeTextDraft(draft.text).isBlank()) {
            if (isExisting) dispatch(SharedPdfReaderAction.AnnotationDeleted(draft.id))
            return
        }
        val annotation = draft.toAnnotation()
        if (isExisting) {
            dispatch(SharedPdfReaderAction.AnnotationUpdated(annotation))
        } else {
            dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        }
    }

    fun activeToolConfig(tool: PdfInkTool) = SharedPdfAnnotationDefaults.configFor(tool)

    fun setTool(tool: PdfInkTool) {
        if (tool != PdfInkTool.TEXT && readerState.selectedTool == PdfInkTool.TEXT && textDraft != null) {
            dismissTextDraft()
        }
        dispatch(SharedPdfReaderAction.ToolSelected(tool))
        if (tool != PdfInkTool.NONE) {
            activeToolConfig(tool).let { config ->
                dispatch(SharedPdfReaderAction.ColorSelected(config.colorArgb.takeIf { it != 0 } ?: readerState.selectedColorArgb))
                dispatch(SharedPdfReaderAction.StrokeWidthChanged(config.strokeWidth))
            }
        }
    }

    fun finishInkStroke(pageIndex: Int, eraserOverride: Boolean = false) {
        val effectiveTool = if (eraserOverride) PdfInkTool.ERASER else readerState.selectedTool
        if (activeStroke.size < 2 || effectiveTool == PdfInkTool.NONE || effectiveTool == PdfInkTool.TEXT) {
            activeStroke.clear()
            return
        }
        val kind = if (effectiveTool == PdfInkTool.HIGHLIGHTER || effectiveTool == PdfInkTool.HIGHLIGHTER_ROUND) {
            PdfAnnotationKind.HIGHLIGHT
        } else {
            PdfAnnotationKind.INK
        }
        val annotation = if (kind == PdfAnnotationKind.HIGHLIGHT) {
            val xs = activeStroke.map { it.x }
            val ys = activeStroke.map { it.y }
            SharedPdfAnnotation(
                id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.HIGHLIGHT,
                tool = effectiveTool,
                boundsList = listOf(
                    PdfPageBounds(
                        left = xs.minOrNull()?.coerceIn(0f, 1f) ?: 0f,
                        top = (ys.minOrNull()?.minus(0.015f))?.coerceIn(0f, 1f) ?: 0f,
                        right = xs.maxOrNull()?.coerceIn(0f, 1f) ?: 0f,
                        bottom = (ys.maxOrNull()?.plus(0.015f))?.coerceIn(0f, 1f) ?: 0f
                    )
                ),
                colorArgb = readerState.selectedColorArgb,
                highlightStyle = HighlightStyle.BACKGROUND,
                strokeWidth = readerState.strokeWidth,
                createdAt = currentTimestamp()
            )
        } else {
            SharedPdfAnnotation(
                id = "ios_pdf_annotation_${currentTimestamp()}_${readerState.annotations.size}",
                pageIndex = pageIndex,
                kind = PdfAnnotationKind.INK,
                tool = effectiveTool,
                points = activeStroke.toList(),
                colorArgb = readerState.selectedColorArgb,
                strokeWidth = readerState.strokeWidth,
                createdAt = currentTimestamp()
            )
        }
        dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        activeStroke.clear()
    }

    fun addTextHighlight(
        pageIndex: Int,
        range: com.aryan.reader.shared.pdf.PdfTextSelectionRange,
        text: String,
        bounds: List<PdfPageBounds>,
        colorArgb: Int,
        style: HighlightStyle,
        openNote: Boolean
    ) {
        val annotation = SharedPdfAnnotation(
            id = "ios_pdf_highlight_${currentTimestamp()}_${readerState.annotations.size}",
            pageIndex = pageIndex,
            kind = PdfAnnotationKind.HIGHLIGHT,
            tool = PdfInkTool.HIGHLIGHTER,
            boundsList = bounds,
            text = text,
            colorArgb = colorArgb,
            highlightStyle = style,
            strokeWidth = SharedPdfAnnotationDefaults.configFor(PdfInkTool.HIGHLIGHTER).strokeWidth,
            rangeStartIndex = range.start,
            rangeEndIndex = range.end,
            createdAt = currentTimestamp()
        )
        dispatch(SharedPdfReaderAction.AnnotationAdded(annotation))
        if (openNote) {
            noteAnnotationId = annotation.id
        }
    }

    LaunchedEffect(pageCount) {
        if (readerState.pageCount != pageCount) {
            readerState = readerState.copy(pageCount = pageCount).coerced()
        }
    }

    // Mirrors Android's auto page management for the flowing rich text document
    // (PdfViewerScreen lines ~2208-2360): text overflowing the last page appends blank pages,
    // trailing auto-added blank pages with no content are pruned. Manual blank pages are kept.
    val highestRequiredTextPageIndex by remember(richTextController.pageLayouts) {
        derivedStateOf { richTextController.pageLayouts.maxOfOrNull { it.pageIndex } ?: -1 }
    }
    fun hasRichTextOnPage(pageIndex: Int): Boolean {
        return richTextController.pageLayouts.any {
            it.pageIndex == pageIndex && it.visibleText.isNotBlank()
        }
    }
    LaunchedEffect(highestRequiredTextPageIndex, displayPageCount, richTextController.pageLayouts) {
        if (richTextController.pageLayouts.isEmpty() || highestRequiredTextPageIndex < 0) return@LaunchedEffect
        delay(500)
        val requiredPages = highestRequiredTextPageIndex + 1
        if (requiredPages > displayPageCount) {
            val aspectRatio = currentPageRender.aspectRatio.coerceIn(0.1f, 10f)
            dispatch(
                SharedPdfReaderAction.InsertBlankPageAt(
                    displayIndex = displayPageCount,
                    widthPx = 1000f,
                    heightPx = 1000f / aspectRatio,
                    id = "auto_blank_${currentTimestamp()}",
                    wasManuallyAdded = false
                )
            )
        } else {
            while (true) {
                val layout = readerState.virtualPageLayout
                val lastIndex = layout.lastIndex
                if (lastIndex < 0) break
                val lastPage = layout[lastIndex] as? SharedPdfVirtualPage.BlankPage ?: break
                if (lastPage.insertion.wasManuallyAdded) break
                if (lastIndex <= highestRequiredTextPageIndex) break
                if (hasRichTextOnPage(lastIndex)) break
                dispatch(SharedPdfReaderAction.DeleteBlankPageAt(lastIndex))
            }
        }
    }

    LaunchedEffect(readerState, richTextDocumentJson) {
        onReaderStateChange(readerState.copy(richTextDocumentJson = richTextDocumentJson))
    }

    LaunchedEffect(autoScrollInteractionToken) {
        if (autoScrollInteractionToken == 0 || !autoScrollPlaying) return@LaunchedEffect
        autoScrollTemporarilyPaused = true
        delay(autoScrollPauseDurationMillis)
        autoScrollTemporarilyPaused = false
    }

    LaunchedEffect(readerState.displayMode) {
        if (readerState.displayMode != PdfDisplayMode.VERTICAL_SCROLL) {
            autoScrollModeActive = false
            autoScrollPlaying = false
            autoScrollTemporarilyPaused = false
        }
    }

    LaunchedEffect(readerState.searchQuery) {
        val query = readerState.searchQuery.trim()
        if (query.isBlank()) {
            searchResults = emptyList()
            isSearchInProgress = false
            return@LaunchedEffect
        }
        delay(300)
        isSearchInProgress = true
        searchResults = withContext(Dispatchers.Default) {
            searchSharedMobilePdf(book, query, pdfPassword)
        }
        isSearchInProgress = false
    }

    LaunchedEffect(book.path, pdfPassword, documentRender.openError) {
        tableOfContents = if (documentRender.openError == null) {
            withContext(Dispatchers.Default) { loadSharedMobilePdfOutline(book, pdfPassword) }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(ttsTextSession, pendingTtsStart, ttsPageIndex) {
        val start = pendingTtsStart ?: return@LaunchedEffect
        val session = ttsTextSession ?: return@LaunchedEffect
        val source = session.textForRange(0, session.pageCharCount).orEmpty()
        val planned = PdfTtsSessionPlanner.page(ttsPageIndex, source, start)
        if (planned.chunks.isEmpty()) {
            val next = PdfTtsSessionPlanner.nextPage(ttsPageIndex, pageCount)
            if (next == null) {
                pendingTtsStart = null
                pdfTts.stop()
            } else {
                ttsPageIndex = next
                pendingTtsStart = 0
                navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, next), recordHistory = false, reason = PdfNavigationReason.TTS)
            }
        } else {
            pendingTtsStart = null
            pdfTts.start(
                chunks = planned.chunks,
                bookTitle = pdfCardTitle,
                bookId = book.id,
                startChunkIndex = if (pendingTtsStartAtLastChunk) planned.chunks.lastIndex else 0,
                playWhenReady = pendingTtsPlayWhenReady
            )
            pendingTtsStartAtLastChunk = false
        }
    }

    LaunchedEffect(pdfTts.progress.currentChunk, ttsTextSession, ttsPageIndex) {
        val session = ttsTextSession
        val range = PdfTtsSessionPlanner.highlightRange(pdfTts.progress.currentChunk, session?.pageCharCount ?: 0)
        ttsHighlightBounds = if (session != null && range != null) {
            session.rectsForRangeNormalized(range.start, range.length)
        } else {
            emptyList()
        }
        if (range != null) {
            navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex), recordHistory = false, centerFraction = ttsHighlightBounds.centerYFraction(), reason = PdfNavigationReason.TTS)
        }
    }

    LaunchedEffect(pdfTts.completionCount) {
        if (pdfTts.completionCount == lastTtsCompletionCount) return@LaunchedEffect
        lastTtsCompletionCount = pdfTts.completionCount
        val next = PdfTtsSessionPlanner.nextPage(ttsPageIndex, pageCount)
        if (next != null) {
            ttsPageIndex = next
            navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, next), recordHistory = false, reason = PdfNavigationReason.TTS)
            val prefetched = prefetchedTtsTextSession.takeIf { prefetchedTtsPageIndex == next }
            val source = prefetched?.textForRange(0, prefetched.pageCharCount).orEmpty()
            val planned = PdfTtsSessionPlanner.page(next, source)
            if (planned.chunks.isNotEmpty()) {
                pendingTtsStart = null
                pdfTts.start(planned.chunks, pdfCardTitle, bookId = book.id)
            } else {
                pendingTtsStart = 0
            }
        } else {
            pdfTts.stop()
            ttsHighlightBounds = emptyList()
        }
    }

    LaunchedEffect(keepScreenOn) {
        onKeepScreenOnChange(keepScreenOn)
    }

    LaunchedEffect(isStylusOnlyMode) {
        onStylusOnlyModePreferenceChange(isStylusOnlyMode)
    }

    DisposableEffect(onKeepScreenOnChange) {
        onDispose { onKeepScreenOnChange(false) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            SharedMobilePdfReaderDrawer(
                book = book,
                state = readerState,
                tableOfContents = tableOfContents,
                onGoToPage = { page ->
                    navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, page), reason = PdfNavigationReason.TABLE_OF_CONTENTS)
                    scope.launch { drawerState.close() }
                },
                onEditNote = { annotation ->
                    noteAnnotationId = annotation.id
                    scope.launch { drawerState.close() }
                },
                onDeleteHighlight = { dispatch(SharedPdfReaderAction.AnnotationDeleted(it.id)) },
                onToggleBookmark = { dispatch(SharedPdfReaderAction.BookmarkToggled(currentPdfIndex, createdAt = currentTimestamp())) },
                onRenameBookmark = { pageIndex, label -> dispatch(SharedPdfReaderAction.BookmarkRenamed(pageIndex, label)) },
                onDeleteBookmark = { pageIndex -> dispatch(SharedPdfReaderAction.BookmarkDeleted(pageIndex)) },
                onGoToDisplayPage = { displayIndex ->
                    navigateToPage(displayIndex, reason = PdfNavigationReason.TABLE_OF_CONTENTS)
                    scope.launch { drawerState.close() }
                },
                pdfPassword = pdfPassword,
                tabsEnabled = pdfTabsEnabled,
                tabs = openPdfTabs,
                activeTabBookId = activePdfTabBookId,
                isTopTabStripVisible = pdfTopTabStripVisible,
                onTopTabStripVisibilityChange = onPdfTopTabStripVisibilityChange,
                onOpenTab = {
                    onOpenPdfTab(it)
                    scope.launch { drawerState.close() }
                },
                onCloseTab = onClosePdfTab,
                onNewTab = {
                    scope.launch { drawerState.close() }
                    showNewPdfTabSheet = true
                },
            )
        },
        modifier = modifier
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = showChrome,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { -it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { -it } + fadeOut(tween(PdfChromeMotionDurationMillis))
                ) {
                    Column {
                        SharedMobilePdfReaderTopBar(
                            title = pdfCardTitle,
                            pageIndex = readerState.pageIndex,
                            pageLabel = sharedMobilePdfPageLabel(
                                readerState.pageIndex,
                                displayPageCount,
                                useTwoPageSpread && readerState.displayMode == PdfDisplayMode.PAGINATION,
                                firstPageStandaloneInSpread
                            ),
                            pageCount = displayPageCount,
                            displayMode = readerState.displayMode,
                            isSearchActive = readerState.isSearchActive,
                            searchQuery = readerState.searchQuery,
                            isBookmarked = readerState.bookmarks.any { it.pageIndex == currentPdfIndex },
                            onBack = onBack,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                            onSearchQueryChange = { query ->
                                dispatch(SharedPdfReaderAction.SearchChanged(query))
                            },
                            onCloseSearch = { dispatch(SharedPdfReaderAction.SearchClosed) },
                            onToggleBookmark = {
                                dispatch(SharedPdfReaderAction.BookmarkToggled(currentPdfIndex, createdAt = currentTimestamp()))
                            },
                            onToggleDisplayMode = ::toggleDisplayMode,
                            onTheme = { showThemePanel = true },
                            onVisualOptions = { showReaderOptions = !showReaderOptions },
                            tapToTurnPages = tapToTurnPages,
                            onToggleTapToTurnPages = { tapToTurnPages = !tapToTurnPages },
                            isScrollLocked = readerState.isScrollLocked,
                            onToggleScrollLock = {
                                dispatch(
                                    SharedPdfReaderAction.ScrollLockChanged(
                                        locked = !readerState.isScrollLocked,
                                        zoomScale = pdfZoomCamera.scale,
                                        offsetX = pdfZoomCamera.offset.x,
                                        offsetY = pdfZoomCamera.offset.y
                                    )
                                )
                            },
                            keepScreenOn = keepScreenOn,
                            onToggleKeepScreenOn = {
                                keepScreenOn = !keepScreenOn
                                onKeepScreenOnPreferenceChange(keepScreenOn)
                            },
                            autoScrollEnabled = autoScrollModeActive,
                            onToggleAutoScroll = {
                                autoScrollModeActive = true
                                autoScrollPlaying = true
                                autoScrollTemporarilyPaused = false
                                showChrome = !autoScrollMusicianMode
                            },
                            showAllTextHighlights = showAllTextHighlights,
                            isAllTextHighlightLoading = isAllTextHighlightLoading,
                            onToggleHighlights = ::toggleAllTextHighlights,
                            onHighlighterTool = { setTool(readerState.lastActiveHighlighterTool) },
                            onEditMode = {
                                setTool(if (readerState.selectedTool == PdfInkTool.NONE) PdfInkTool.PEN else PdfInkTool.NONE)
                            },
                            onShowSlider = {
                                showPageSlider = !showPageSlider
                                onPageSliderVisibilityPreferenceChange(showPageSlider)
                            },
                            onToggleTts = {
                                when (pdfTts.state) {
                                    SharedMobileEpubLocalTtsState.IDLE -> requestTts()
                                    SharedMobileEpubLocalTtsState.SPEAKING -> pdfTts.pause()
                                    SharedMobileEpubLocalTtsState.PAUSED -> pdfTts.resume()
                                }
                            },
                            onVoiceSettings = { showTtsSettingsSheet = true },
                            onWordReplacements = { showTtsReplacementsSheet = true },
                            onNativeAction = { action ->
                                if (action == SharedMobilePdfNativeAction.PRINT && pdfPassword != null) {
                                    showPasswordProtectedPrintWarning = true
                                } else {
                                    onNativePdfAction(book, action)
                                }
                            },
                            isCurrentPageBlank = isCurrentPageBlank,
                            onInsertBlankPage = ::insertBlankPageAtCurrentPosition,
                            onDeleteBlankPage = ::deleteBlankPageAtCurrentPosition,
                            pdfReflowUiState = pdfReflowUiState,
                            isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                            onFileInformation = { showFileInformation = true },
                            onBrightness = { showBrightnessSheet = true },
                            onScreenOrientation = { showScreenOrientationSheet = true },
                            topTools = pdfTopTools,
                            toolbarPreferences = sanitizedPdfToolbarPreferences,
                            onCustomizeToolbar = { showToolbarCustomization = true },
                            applySystemBarInsets = systemUiMode == SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS
                        )
                        if (
                            pdfTabsEnabled &&
                            pdfTopTabStripVisible &&
                            openPdfTabs.isNotEmpty() &&
                            !readerState.isSearchActive
                        ) {
                            SharedMobilePdfReaderTabStrip(
                                tabs = openPdfTabs,
                                activeBookId = activePdfTabBookId,
                                onOpenTab = onOpenPdfTab,
                                onCloseTab = onClosePdfTab,
                                onNewTab = { showNewPdfTabSheet = true },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = showChrome && !readerState.isSearchActive,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis))
                ) {
                    SharedMobilePdfReaderBottomBar(
                        state = readerState,
                        tools = pdfBottomTools,
                        onShowSlider = { showPageSlider = !showPageSlider },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onSearch = { dispatch(SharedPdfReaderAction.SearchOpened) },
                        onToolSelected = ::setTool,
                        onColorSelected = { dispatch(SharedPdfReaderAction.ColorSelected(it)) },
                        onStrokeWidthChange = { dispatch(SharedPdfReaderAction.StrokeWidthChanged(it)) },
                        onUndo = { dispatch(SharedPdfReaderAction.UndoLastAnnotationOnPage(readerState.pageIndex)) },
                        onRedo = { dispatch(SharedPdfReaderAction.RedoAnnotationEdit) },
                        onClearPage = { dispatch(SharedPdfReaderAction.ClearPageAnnotations(readerState.pageIndex)) },
                        isStylusOnlyMode = isStylusOnlyMode,
                        onToggleStylusOnlyMode = { isStylusOnlyMode = !isStylusOnlyMode },
                        ttsState = pdfTts.state,
                        isTtsPlayingOrLoading = isPdfTtsPlayingOrLoading,
                        onToggleTts = {
                            when (pdfTts.state) {
                                SharedMobileEpubLocalTtsState.IDLE -> requestTts()
                                SharedMobileEpubLocalTtsState.SPEAKING -> pdfTts.pause()
                                SharedMobileEpubLocalTtsState.PAUSED -> pdfTts.resume()
                            }
                        },
                        onTheme = { showThemePanel = true },
                        onBrightness = { showBrightnessSheet = true },
                        onToggleScrollLock = {
                            dispatch(
                                SharedPdfReaderAction.ScrollLockChanged(
                                    locked = !readerState.isScrollLocked,
                                    zoomScale = pdfZoomCamera.scale,
                                    offsetX = pdfZoomCamera.offset.x,
                                    offsetY = pdfZoomCamera.offset.y
                                )
                            )
                        },
                        onScreenOrientation = { showScreenOrientationSheet = true },
                        onDictionary = { onNativePdfAction(book, SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) },
                        showAllTextHighlights = showAllTextHighlights,
                        isAllTextHighlightLoading = isAllTextHighlightLoading,
                        onToggleHighlights = ::toggleAllTextHighlights,
                        applySystemBarInsets = systemUiMode == SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sharedMobilePdfViewerBackground(activeTheme, readerState.displayMode))
                    .focusRequester(pdfReaderFocusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (sharedPdfKeyboardNavigationAction(event.key, readerState.displayMode)) {
                            SharedPdfKeyboardNavigationAction.NEXT_PAGE -> {
                                navigateToPage(readerState.pageIndex + 1, recordHistory = false, reason = PdfNavigationReason.PAGE_TURN)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.PREVIOUS_PAGE -> {
                                navigateToPage(readerState.pageIndex - 1, recordHistory = false, reason = PdfNavigationReason.PAGE_TURN)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.FIRST_PAGE -> {
                                navigateToPage(0, recordHistory = false, reason = PdfNavigationReason.INITIAL)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.LAST_PAGE -> {
                                navigateToPage(displayPageCount - 1, recordHistory = false, reason = PdfNavigationReason.INITIAL)
                                true
                            }
                            SharedPdfKeyboardNavigationAction.SCROLL_DOWN -> {
                                scope.launch { runCatching { pdfVerticalScrollController.scrollByViewportFraction(0.9f) } }
                                true
                            }
                            SharedPdfKeyboardNavigationAction.SCROLL_UP -> {
                                scope.launch { runCatching { pdfVerticalScrollController.scrollByViewportFraction(-0.9f) } }
                                true
                            }
                            SharedPdfKeyboardNavigationAction.NONE -> false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL) {
                    SharedMobilePdfVerticalPages(
                        book = book,
                        pdfPassword = pdfPassword,
                        state = readerState,
                        activeTheme = activeTheme,
                        textureAlpha = 1f - globalTextureTransparency,
                        pageCount = displayPageCount,
                        virtualLayout = virtualLayout,
                        navigationRequestPage = navigationRequestPage,
                        navigationRequestToken = navigationRequestToken,
                        navigationCenterFraction = navigationCenterFraction,
                        showPageGap = showVerticalPageGap,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults,
                        ttsPageIndex = ttsPageIndex.takeIf { pdfTts.isSessionActive || pendingTtsStart != null },
                        ttsHighlightBounds = ttsHighlightBounds,
                        activeStroke = activeStroke,
                        isStylusOnlyMode = isStylusOnlyMode,
                        verticalScrollController = pdfVerticalScrollController,
                        autoScrollPlaying = autoScrollPlaying,
                        autoScrollTemporarilyPaused = autoScrollTemporarilyPaused,
                        autoScrollSpeed = autoScrollProfile.speed,
                        autoScrollMusicianMode = autoScrollMusicianMode && autoScrollModeActive,
                        onAutoScrollInteraction = { durationMillis ->
                            autoScrollPauseDurationMillis = durationMillis
                            autoScrollInteractionToken++
                        },
                        onVisiblePageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = { page, eraserOverride -> finishInkStroke(page, eraserOverride) },
                        onExternalLink = { url -> pendingExternalLink = url },
                        onInternalLink = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, it), reason = PdfNavigationReason.INTERNAL_LINK) },
                        onExistingHighlightTap = { noteAnnotationId = it.id },
                        onHighlight = { page, range, text, bounds, color, style, note -> addTextHighlight(page, range, text, bounds, color, style, note) },
                        onReadAloud = { page, charIndex -> requestTts(sharedPdfDisplayIndexFor(virtualLayout, page), charIndex) },
                        userScrollEnabled = !readerState.isScrollLocked,
                        isScrollLocked = readerState.isScrollLocked,
                        zoomCamera = pdfZoomCamera,
                        onZoomCameraChanged = { pdfZoomCamera = it },
                        textDraft = textDraft,
                        onTextDraftChange = ::updateTextDraft,
                        onTextPageTap = { annotation ->
                            if (annotation != null) startEditingTextBox(annotation) else dismissTextDraft()
                        },
                        richTextController = richTextController,
                        isRichTextEditingEnabled = isRichTextEditingEnabled,
                        showAllTextHighlights = showAllTextHighlights,
                        onAllTextHighlightsLoadingChange = { isAllTextHighlightLoading = it },
                        onToggleChrome = {
                            if (!(autoScrollMusicianMode && autoScrollModeActive)) showChrome = !showChrome
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    SharedMobilePdfPaginatedPages(
                        book = book,
                        pdfPassword = pdfPassword,
                        state = readerState,
                        activeTheme = activeTheme,
                        textureAlpha = 1f - globalTextureTransparency,
                        pageCount = displayPageCount,
                        virtualLayout = virtualLayout,
                        navigationRequestPage = navigationRequestPage,
                        navigationRequestToken = navigationRequestToken,
                        animateNavigation = navigationReason.animatesPagination(),
                        useTwoPageSpread = useTwoPageSpread,
                        firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                        rightToLeftPagination = rightToLeftPagination,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults,
                        ttsPageIndex = ttsPageIndex.takeIf { pdfTts.isSessionActive || pendingTtsStart != null },
                        ttsHighlightBounds = ttsHighlightBounds,
                        activeStroke = activeStroke,
                        isStylusOnlyMode = isStylusOnlyMode,
                        tapToTurnPages = tapToTurnPages,
                        onExternalLink = { url -> pendingExternalLink = url },
                        onInternalLink = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, it), reason = PdfNavigationReason.INTERNAL_LINK) },
                        onExistingHighlightTap = { noteAnnotationId = it.id },
                        onHighlight = { page, range, text, bounds, color, style, note -> addTextHighlight(page, range, text, bounds, color, style, note) },
                        onReadAloud = { page, charIndex -> requestTts(sharedPdfDisplayIndexFor(virtualLayout, page), charIndex) },
                        userScrollEnabled = !readerState.isScrollLocked,
                        isScrollLocked = readerState.isScrollLocked,
                        zoomCamera = pdfZoomCamera,
                        onZoomCameraChanged = { pdfZoomCamera = it },
                        textDraft = textDraft,
                        onTextDraftChange = ::updateTextDraft,
                        onTextPageTap = { annotation ->
                            if (annotation != null) startEditingTextBox(annotation) else dismissTextDraft()
                        },
                        richTextController = richTextController,
                        isRichTextEditingEnabled = isRichTextEditingEnabled,
                        showAllTextHighlights = showAllTextHighlights,
                        onAllTextHighlightsLoadingChange = { isAllTextHighlightLoading = it },
                        onPageChanged = { dispatch(SharedPdfReaderAction.GoToPage(it)) },
                        onManualPageTurnStarted = ::stopPdfTtsForManualPagination,
                        onToggleChrome = { showChrome = !showChrome },
                        onCanvasSizeChanged = { canvasSize = it },
                        onFinishInkStroke = { page, eraserOverride -> finishInkStroke(page, eraserOverride) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AnimatedVisibility(
                    visible = showChrome && showPageSlider,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfPageSlider(
                        pageIndex = readerState.pageIndex,
                        pageCount = displayPageCount,
                        onPageChange = {
                            navigateToPage(
                                pageIndex = it,
                                recordHistory = false,
                                reason = PdfNavigationReason.PAGE_SLIDER,
                            )
                        },
                        onScrubPreview = { pdfSliderScrubbingPage = it },
                        modifier = Modifier
                            .padding(start = 16.dp, end = 16.dp, bottom = pdfSliderBottomPadding)
                    )
                }
                pdfSliderScrubbingPage?.let { scrubPage ->
                    SharedMobilePdfPageScrubbingOverlay(
                        label = sharedPdfPageRangeLabel(
                            sharedMobilePdfPageLabel(
                                scrubPage,
                                displayPageCount,
                                useTwoPageSpread && readerState.displayMode == PdfDisplayMode.PAGINATION,
                                firstPageStandaloneInSpread
                            ),
                            displayPageCount
                        )
                    )
                }
                AnimatedVisibility(
                    visible = isJumpHistoryVisible,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfJumpHistoryBar(
                        history = jumpHistory,
                        onBack = {
                            jumpHistory.backPage?.let { target ->
                                jumpHistory = jumpHistory.stepBack()
                                navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.JUMP_HISTORY)
                            }
                        },
                        onForward = {
                            jumpHistory.forwardPage?.let { target ->
                                jumpHistory = jumpHistory.stepForward()
                                navigateToPage(target, recordHistory = false, reason = PdfNavigationReason.JUMP_HISTORY)
                            }
                        },
                        onClear = { jumpHistory = jumpHistory.clear() },
                        modifier = Modifier.padding(bottom = pdfBottomChromePadding)
                    )
                }
                val ttsBottomPadding by animateDpAsState(
                    targetValue = if (showChrome) {
                        56.dp + 16.dp + effectiveBottomSystemInset
                    } else {
                        16.dp + effectiveBottomSystemInset
                    },
                    animationSpec = tween(PdfChromeMotionDurationMillis),
                    label = "PdfTtsBottomPadding"
                )
                AnimatedVisibility(
                    visible = pdfTts.isSessionActive || pendingTtsStart != null,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfTtsControls(
                        tts = pdfTts,
                        pageIndex = ttsPageIndex,
                        pageCount = pageCount,
                        chunkIndex = pdfTts.progress.currentChunkIndex,
                        chunkCount = pdfTts.progress.chunks.size,
                        onPauseResume = {
                            if (pdfTts.state == SharedMobileEpubLocalTtsState.SPEAKING) pdfTts.pause() else pdfTts.resume()
                        },
                        onPreviousPage = {
                            if (pdfTts.progress.currentChunkIndex > 0) {
                                pdfTts.skipPrevious()
                            } else if (ttsPageIndex > 0) {
                                requestTts(
                                    pageIndex = sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex - 1),
                                    startAtLastChunk = true,
                                    playWhenReady = pdfTts.state != SharedMobileEpubLocalTtsState.PAUSED
                                )
                            }
                        },
                        onNextPage = {
                            if (pdfTts.progress.currentChunkIndex < pdfTts.progress.chunks.lastIndex) {
                                pdfTts.skipNext()
                            } else if (ttsPageIndex < pageCount - 1) {
                                requestTts(
                                    pageIndex = sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex + 1),
                                    playWhenReady = pdfTts.state != SharedMobileEpubLocalTtsState.PAUSED
                                )
                            }
                        },
                        onLocate = { navigateToPage(sharedPdfDisplayIndexFor(virtualLayout, ttsPageIndex), recordHistory = false, centerFraction = ttsHighlightBounds.centerYFraction(), reason = PdfNavigationReason.TTS) },
                        onStop = {
                            pdfTts.stop()
                            pendingTtsStart = null
                            pendingTtsStartAtLastChunk = false
                            ttsHighlightBounds = emptyList()
                        },
                        modifier = Modifier.padding(bottom = ttsBottomPadding)
                    )
                }
                AnimatedVisibility(
                    visible = autoScrollModeActive && readerState.displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    SharedMobilePdfAutoScrollControls(
                        isPlaying = autoScrollPlaying,
                        isTemporarilyPaused = autoScrollTemporarilyPaused,
                        profile = autoScrollProfile,
                        isLocalMode = autoScrollIsLocal,
                        isMusicianMode = autoScrollMusicianMode,
                        useSlider = autoScrollUseSlider,
                        isCollapsed = autoScrollCollapsed,
                        onPlayPause = {
                            autoScrollPlaying = !autoScrollPlaying
                            autoScrollTemporarilyPaused = false
                        },
                        onProfileChange = ::updateAutoScrollProfile,
                        onLocalModeChange = ::setAutoScrollLocalMode,
                        onMusicianModeChange = {
                            autoScrollMusicianMode = it
                            onPdfAutoScrollMusicianModeChange(it)
                            if (it) showChrome = false
                        },
                        onUseSliderChange = {
                            autoScrollUseSlider = it
                            onPdfAutoScrollUseSliderChange(it)
                        },
                        onCollapsedChange = { autoScrollCollapsed = it },
                        onScrollToTop = {
                            autoScrollPauseDurationMillis = 1_000L
                            autoScrollInteractionToken++
                            navigateToPage(0, recordHistory = false, reason = PdfNavigationReason.PAGE_TURN)
                        },
                        onClose = {
                            autoScrollModeActive = false
                            autoScrollPlaying = false
                            autoScrollTemporarilyPaused = false
                        },
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = ttsBottomPadding + if (pdfTts.isSessionActive || pendingTtsStart != null) 76.dp else 0.dp,
                        ),
                    )
                }
                AnimatedVisibility(
                    visible = readerState.isSearchActive && readerState.showSearchResultsPanel && readerState.searchQuery.isNotBlank()
                ) {
                    SharedMobilePdfSearchResultsPanel(
                        query = readerState.searchQuery,
                        results = searchResults,
                        activeResultIndex = readerState.activeSearchResultIndex,
                        isSearching = isSearchInProgress,
                        onResultClick = { index ->
                            navigateToSearchResult(index)
                            dispatch(SharedPdfReaderAction.SearchResultsPanelToggled)
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 64.dp)
                    )
                }
                AnimatedVisibility(
                    visible = readerState.isSearchActive && !readerState.showSearchResultsPanel && searchResults.isNotEmpty(),
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedMobilePdfSearchNavigationPill(
                        activeIndex = readerState.activeSearchResultIndex,
                        resultCount = searchResults.size,
                        highlightMode = readerState.searchHighlightMode,
                        onToggleHighlightMode = {
                            dispatch(SharedPdfReaderAction.SearchHighlightModeToggled)
                        },
                        onPrevious = { navigateToSearchResult(readerState.activeSearchResultIndex - 1) },
                        onNext = { navigateToSearchResult(readerState.activeSearchResultIndex + 1) },
                        onShowResults = { dispatch(SharedPdfReaderAction.SearchResultsPanelToggled) },
                        modifier = Modifier
                            .padding(bottom = 24.dp)
                    )
                }
                AnimatedVisibility(
                    visible = readerState.selectedTool == PdfInkTool.TEXT && !readerState.isSearchActive,
                    enter = slideInVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeIn(tween(PdfChromeMotionDurationMillis)),
                    exit = slideOutVertically(tween(PdfChromeMotionDurationMillis)) { it } + fadeOut(tween(PdfChromeMotionDurationMillis)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    SharedPdfTextAnnotationDock(
                        style = textStyle,
                        onStyleChange = { newStyle ->
                            textStyle = newStyle
                            textDraft?.let { draft ->
                                updateTextDraft(draft.withStyle(newStyle, canvasSize))
                            }
                        },
                        onInsertTextBox = ::insertTextBox,
                        modifier = Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = if (showChrome) 96.dp + effectiveBottomSystemInset else 16.dp + effectiveBottomSystemInset
                        )
                    )
                }
                if (pdfReflowUiState.isGenerating) {
                    SharedMobilePdfReflowProgressOverlay(progress = pdfReflowUiState.progress)
                }
            }
        }
        pendingExternalLink?.let { url ->
            AlertDialog(
                onDismissRequest = { pendingExternalLink = null },
                title = { Text(readerString("dialog_external_link_title", "Open external link?")) },
                text = {
                    Text(
                        readerString(
                            "desc_external_link_warning",
                            "This PDF wants to open an external link:\n\n%1\$s",
                            url,
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openSharedMobileExternalUrl(url)
                            pendingExternalLink = null
                        }
                    ) {
                        Text(readerString("action_visit", "Visit"))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(url))
                                pendingExternalLink = null
                            }
                        ) {
                            Text(readerString("action_copy", "Copy"))
                        }
                        TextButton(onClick = { pendingExternalLink = null }) {
                            Text(readerString("action_cancel", "Cancel"))
                        }
                    }
                },
            )
        }
        if (showThemePanel) {
            SharedMobilePdfThemePanel(
                settings = readerDefaultSettings.copy(
                    themeId = readerState.themeId,
                    textureAlpha = 1f - globalTextureTransparency,
                ),
                customThemes = customReaderThemes,
                onCustomThemesChange = onCustomReaderThemesChange,
                onSettingsChange = { settings ->
                    globalTextureTransparency = 1f - settings.textureAlpha.coerceIn(0f, 1f)
                    if (settings.themeId != readerState.themeId) {
                        settings.themeId?.let { dispatch(SharedPdfReaderAction.ThemeChanged(it)) }
                        showThemePanel = false
                    }
                    onReaderDefaultSettingsChange(settings)
                },
                onDismiss = { showThemePanel = false }
            )
        }
        if (showReaderOptions) {
            SharedMobilePdfVisualOptionsSheet(
                displayMode = readerState.displayMode,
                systemUiMode = systemUiMode,
                useTwoPageSpread = useTwoPageSpread,
                firstPageStandaloneInSpread = firstPageStandaloneInSpread,
                rightToLeftPagination = rightToLeftPagination,
                showVerticalPageGap = showVerticalPageGap,
                showPageNumberOverlay = showPageNumberOverlay,
                onSystemUiModeChange = { mode ->
                    systemUiMode = mode
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(systemUiMode = mode.toReaderSystemUiMode())
                    )
                },
                onTwoPageSpreadChange = {
                    useTwoPageSpread = it
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(
                            pageSpreadMode = if (it) {
                                ReaderPageSpreadMode.TWO_PAGE
                            } else {
                                ReaderPageSpreadMode.SINGLE
                            }
                        )
                    )
                },
                onFirstPageStandaloneChange = {
                    firstPageStandaloneInSpread = it
                    onReaderDefaultSettingsChange(
                        readerDefaultSettings.copy(pdfFirstPageStandaloneInSpread = it)
                    )
                },
                onRightToLeftPaginationChange = {
                    rightToLeftPagination = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(rightToLeftPagination = it))
                },
                onShowVerticalPageGapChange = {
                    showVerticalPageGap = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(pdfVerticalPageGapVisible = it))
                },
                onShowPageNumberOverlayChange = {
                    showPageNumberOverlay = it
                    onReaderDefaultSettingsChange(readerDefaultSettings.copy(pdfPageNumberOverlayVisible = it))
                },
                onDismiss = { showReaderOptions = false }
            )
        }
    }

    noteAnnotationId?.let { annotationId ->
        val annotation = readerState.annotations.firstOrNull { it.id == annotationId }
        if (annotation != null) {
            SharedMobilePdfAnnotationBottomSheet(
                annotation = annotation,
                onUpdate = { dispatch(SharedPdfReaderAction.AnnotationUpdated(it)) },
                onDelete = {
                    dispatch(SharedPdfReaderAction.AnnotationDeleted(annotationId))
                    noteAnnotationId = null
                },
                onReadAloud = {
                    requestTts(
                        pageIndex = annotation.pageIndex,
                        startCharIndex = annotation.rangeStartIndex ?: 0,
                    )
                    noteAnnotationId = null
                },
                onDismiss = { noteAnnotationId = null }
            )
        } else {
            noteAnnotationId = null
        }
    }
    if (showFileInformation) {
        SharedBookInfoDialog(
            book = book,
            knownTags = knownTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            canEditEmbeddedMetadata = false,
            canRenameDisplayName = true,
            canRestoreEmbeddedMetadata = false,
            onDismiss = { showFileInformation = false },
            onSave = { updated ->
                onBookInfoChange(updated)
                showFileInformation = false
            },
            onRestore = {},
        )
    }
    if (documentRender.openError == SharedMobilePdfOpenError.PASSWORD_REQUIRED) {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("Password protected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This PDF is password protected. Enter the password to open it.")
                    OutlinedTextField(
                        value = pdfPasswordDraft,
                        onValueChange = { pdfPasswordDraft = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = pdfPassword != null,
                        supportingText = if (pdfPassword != null) {
                            { Text("Incorrect password") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { pdfPassword = pdfPasswordDraft },
                    enabled = pdfPasswordDraft.isNotBlank(),
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) { Text("Cancel") }
            },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        )
    }
    if (showPasswordProtectedPrintWarning) {
        AlertDialog(
            onDismissRequest = { showPasswordProtectedPrintWarning = false },
            title = { Text("Printing unavailable") },
            text = { Text("Password-protected PDFs cannot be printed.") },
            confirmButton = {
                TextButton(onClick = { showPasswordProtectedPrintWarning = false }) {
                    Text("OK")
                }
            },
        )
    }
    if (showBrightnessSheet) {
        SharedMobileReaderBrightnessSheet(
            brightness = readerBrightness,
            rememberedCustomBrightness = readerCustomBrightness,
            onBrightnessChange = onReaderBrightnessChange,
            onDismiss = { showBrightnessSheet = false },
        )
    }
    if (showScreenOrientationSheet) {
        SharedMobileReaderScreenOrientationSheet(
            selectedMode = readerScreenOrientationMode,
            onModeSelected = onReaderScreenOrientationModeChange,
            onDismiss = { showScreenOrientationSheet = false },
        )
    }
    if (showToolbarCustomization) {
        SharedMobilePdfToolbarCustomizationSheet(
            preferences = sanitizedPdfToolbarPreferences,
            availableTools = SharedMobilePdfAvailableTools,
            onPreferencesChange = onPdfToolbarPreferencesChange,
            onDismiss = { showToolbarCustomization = false },
        )
    }
    if (showTtsReplacementsSheet) {
        ModalBottomSheet(onDismissRequest = { showTtsReplacementsSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp).padding(bottom = 32.dp)
            ) {
                SharedReaderTtsReplacementControls(
                    preferences = readerTtsReplacementPreferences,
                    bookId = book.id,
                    onPreferencesChange = onReaderTtsReplacementPreferencesChange,
                )
            }
        }
    }
    if (showTtsSettingsSheet) {
        SharedMobileReaderTtsSettingsSheet(
            tts = pdfTts,
            onDismiss = { showTtsSettingsSheet = false },
        )
    }
    if (showNewPdfTabSheet) {
        ModalBottomSheet(onDismissRequest = { showNewPdfTabSheet = false }) {
            val openIds = openPdfTabs.mapTo(mutableSetOf()) { it.id }
            val candidates = availablePdfTabBooks
                .filter { it.type == FileType.PDF && it.id !in openIds }
                .sortedByDescending { it.timestamp }
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    readerString("title_add_pdf_to_tab", "Add PDF to tab"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp),
                )
                if (candidates.isEmpty()) {
                    Text(
                        readerString("msg_no_other_pdfs_found", "No other PDFs found"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(candidates, key = { "new_pdf_tab_${it.id}" }) { candidate ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        candidate.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = candidate.author?.let { author ->
                                    { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                },
                                modifier = Modifier.clickable {
                                    showNewPdfTabSheet = false
                                    onOpenPdfTab(candidate)
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfReaderTabStrip(
    tabs: List<BookItem>,
    activeBookId: String?,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onNewTab: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        verticalAlignment = Alignment.Bottom,
    ) {
        items(tabs, key = { "pdf_reader_tab_${it.id}" }) { tab ->
            val selected = tab.id == activeBookId
            val contentColor = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .height(if (selected) 48.dp else 36.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onOpenTab(tab) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onCloseTab(tab) }, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = readerString("close_tab", "Close tab"),
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                }
            }
        }
        item {
            IconButton(
                onClick = onNewTab,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).size(36.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = readerString("content_desc_new_tab", "New tab"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfReaderTopBar(
    title: String,
    pageIndex: Int,
    pageLabel: String,
    pageCount: Int,
    displayMode: PdfDisplayMode,
    isSearchActive: Boolean,
    searchQuery: String,
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDisplayMode: () -> Unit,
    onTheme: () -> Unit,
    onVisualOptions: () -> Unit,
    tapToTurnPages: Boolean,
    onToggleTapToTurnPages: () -> Unit,
    isScrollLocked: Boolean,
    onToggleScrollLock: () -> Unit,
    keepScreenOn: Boolean,
    onToggleKeepScreenOn: () -> Unit,
    autoScrollEnabled: Boolean,
    onToggleAutoScroll: () -> Unit,
    showAllTextHighlights: Boolean,
    isAllTextHighlightLoading: Boolean,
    onToggleHighlights: () -> Unit,
    onHighlighterTool: () -> Unit,
    onEditMode: () -> Unit,
    onShowSlider: () -> Unit,
    onToggleTts: () -> Unit,
    onVoiceSettings: () -> Unit,
    onWordReplacements: () -> Unit,
    onNativeAction: (SharedMobilePdfNativeAction) -> Unit,
    isCurrentPageBlank: Boolean = false,
    onInsertBlankPage: () -> Unit = {},
    onDeleteBlankPage: () -> Unit = {},
    pdfReflowUiState: SharedMobilePdfReflowUiState,
    onFileInformation: () -> Unit,
    onBrightness: () -> Unit,
    onScreenOrientation: () -> Unit,
    isTtsPlayingOrLoading: Boolean,
    topTools: List<PdfReaderTool>,
    toolbarPreferences: PdfToolbarPreferences,
    onCustomizeToolbar: () -> Unit,
    applySystemBarInsets: Boolean
) {
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            delay(100)
            searchFocusRequester.requestFocus()
        }
    }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHiddenToolsExpanded by remember { mutableStateOf(false) }
    var showReadingModeExpanded by remember { mutableStateOf(false) }
    var showTtsSettingsExpanded by remember { mutableStateOf(false) }
    var showFileActionsExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applySystemBarInsets) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    } else {
                        Modifier
                    }
                )
                .height(64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search PDF") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                )
            } else {
                Text(
                    text = sharedPdfPageRangeLabel(pageLabel, pageCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    topTools.forEach { tool ->
                        when (tool) {
                            PdfReaderTool.DICTIONARY -> SharedMobilePdfTopToolButton("Dictionary", { onNativeAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) }) { Icon(SharedReaderIcons.Dictionary, contentDescription = null) }
                            PdfReaderTool.THEME -> SharedMobilePdfTopToolButton("Theme", onTheme) { Icon(Icons.Default.Palette, contentDescription = null) }
                            PdfReaderTool.BRIGHTNESS -> SharedMobilePdfTopToolButton("Brightness", onBrightness) { Icon(SharedReaderIcons.Contrast, contentDescription = null) }
                            PdfReaderTool.LOCK_PANNING -> SharedMobilePdfTopToolButton(if (isScrollLocked) "Unlock" else "Lock", onToggleScrollLock) { Icon(if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null) }
                            PdfReaderTool.SLIDER -> SharedMobilePdfTopToolButton("Navigation Slider", onShowSlider, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(SharedReaderIcons.Slider, contentDescription = null) }
                            PdfReaderTool.TOC -> SharedMobilePdfTopToolButton("Sidebar", onOpenDrawer, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(Icons.Default.Menu, contentDescription = null) }
                            PdfReaderTool.SEARCH -> SharedMobilePdfTopToolButton("Search", onSearch, isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading)) { Icon(Icons.Default.Search, contentDescription = null) }
                            PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfTopToolButton(
                                "Highlight Selectable Text",
                                onClick = onToggleHighlights,
                                isActive = showAllTextHighlights,
                                isLoading = isAllTextHighlightLoading,
                            ) { Icon(SharedReaderIcons.HighlightText, contentDescription = "Highlight all text") }
                            PdfReaderTool.EDIT_MODE -> SharedMobilePdfTopToolButton("Edit Mode", onEditMode) { Icon(Icons.Default.Edit, contentDescription = null) }
                            PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfTopToolButton(if (isTtsPlayingOrLoading) "Stop Reading" else "Read Aloud", onToggleTts, isActive = isTtsPlayingOrLoading) { Icon(if (isTtsPlayingOrLoading) Icons.Default.Close else SharedReaderIcons.TextToSpeech, contentDescription = null) }
                            PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfTopToolButton("Screen Orientation", onScreenOrientation) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }
                            else -> Unit
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = {
                    showHiddenToolsExpanded = false
                    showReadingModeExpanded = false
                    showTtsSettingsExpanded = false
                    showFileActionsExpanded = false
                    showMoreMenu = true
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "PDF options")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                SharedMobilePdfOverflowItem(
                    "Customize Toolbar",
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onCustomizeToolbar()
                    }
                )
                val hiddenToolbarTools = toolbarPreferences.toolOrder.filter {
                    it in SharedMobilePdfAvailableTools &&
                        it.supportsToolbarPlacement &&
                        !toolbarPreferences.isVisible(it)
                }
                if (hiddenToolbarTools.isNotEmpty()) SharedMobilePdfOverflowItem(
                    "Hidden tools",
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showHiddenToolsExpanded = !showHiddenToolsExpanded }
                )
                if (showHiddenToolsExpanded) {
                    hiddenToolbarTools.forEach { tool ->
                        val closeMenuAndRun: (() -> Unit) -> Unit = { action ->
                            showHiddenToolsExpanded = false
                            showMoreMenu = false
                            action()
                        }
                        when (tool) {
                            PdfReaderTool.DICTIONARY -> SharedMobilePdfOverflowItem("Dictionary", leadingIcon = { Icon(SharedReaderIcons.Dictionary, contentDescription = null) }, onClick = { closeMenuAndRun { onNativeAction(SharedMobilePdfNativeAction.DICTIONARY_SETTINGS) } })
                            PdfReaderTool.THEME -> SharedMobilePdfOverflowItem("Theme", onClick = { closeMenuAndRun(onTheme) })
                            PdfReaderTool.BRIGHTNESS -> SharedMobilePdfOverflowItem("Brightness", leadingIcon = { Icon(SharedReaderIcons.Contrast, contentDescription = null) }, onClick = { closeMenuAndRun(onBrightness) })
                            PdfReaderTool.LOCK_PANNING -> SharedMobilePdfOverflowItem(if (isScrollLocked) "Unlock Panning" else "Lock Panning", onClick = { closeMenuAndRun(onToggleScrollLock) })
                            PdfReaderTool.SLIDER -> SharedMobilePdfOverflowItem("Navigation Slider", enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onShowSlider) })
                            PdfReaderTool.TOC -> SharedMobilePdfOverflowItem("Sidebar", enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onOpenDrawer) })
                            PdfReaderTool.SEARCH -> SharedMobilePdfOverflowItem("Search", enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = { closeMenuAndRun(onSearch) })
                            PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfOverflowItem(
                                "Highlight Selectable Text",
                                leadingIcon = {
                                    if (isAllTextHighlightLoading) {
                                        CircularProgressIndicator(Modifier.size(20.dp))
                                    } else {
                                        Icon(
                                            SharedReaderIcons.HighlightText,
                                            contentDescription = null,
                                            tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                                        )
                                    }
                                },
                                onClick = { closeMenuAndRun(onToggleHighlights) }
                            )
                            PdfReaderTool.EDIT_MODE -> SharedMobilePdfOverflowItem("Edit Mode", onClick = { closeMenuAndRun(onEditMode) })
                            PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfOverflowItem("TTS Controls", onClick = { closeMenuAndRun(onToggleTts) })
                            PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfOverflowItem("Screen Orientation", leadingIcon = { Icon(SharedReaderIcons.ScreenRotation, contentDescription = null) }, onClick = { closeMenuAndRun(onScreenOrientation) })
                            else -> Unit
                        }
                    }
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.VISUAL_OPTIONS)) SharedMobilePdfOverflowItem(
                    "Visual Options",
                    leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onVisualOptions()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.READING_MODE)) SharedMobilePdfOverflowItem(
                    "Change Reading Mode",
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showReadingModeExpanded = !showReadingModeExpanded }
                )
                if (showReadingModeExpanded && toolbarPreferences.isVisible(PdfReaderTool.READING_MODE)) {
                    SharedMobilePdfOverflowItem(
                        "Vertical Scrolling",
                        trailingIcon = { if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            if (displayMode != PdfDisplayMode.VERTICAL_SCROLL) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                    SharedMobilePdfOverflowItem(
                        "Pagination",
                        trailingIcon = { if (displayMode == PdfDisplayMode.PAGINATION) Icon(Icons.Default.Check, contentDescription = "Selected") },
                        onClick = {
                            if (displayMode != PdfDisplayMode.PAGINATION) onToggleDisplayMode()
                            showMoreMenu = false
                        }
                    )
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.TAP_TO_TURN)) SharedMobilePdfOverflowItem(
                    "Tap to Turn Pages",
                    enabled = displayMode == PdfDisplayMode.PAGINATION,
                    trailingIcon = { if (tapToTurnPages) Icon(Icons.Default.Check, contentDescription = "Enabled") },
                    onClick = {
                        showMoreMenu = false
                        onToggleTapToTurnPages()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.KEEP_SCREEN_ON)) SharedMobilePdfOverflowItem(
                    "Keep Screen On",
                    trailingIcon = { if (keepScreenOn) Icon(Icons.Default.Check, contentDescription = "Enabled") },
                    onClick = {
                        showMoreMenu = false
                        onToggleKeepScreenOn()
                    }
                )
                if (toolbarPreferences.isVisible(PdfReaderTool.AUTO_SCROLL)) SharedMobilePdfOverflowItem(
                    "Auto Scroll",
                    enabled = displayMode == PdfDisplayMode.VERTICAL_SCROLL,
                    onClick = {
                        showMoreMenu = false
                        onToggleAutoScroll()
                    }
                )
                val showVoiceSettings = PdfReaderTool.TTS_SETTINGS in SharedMobilePdfAvailableTools &&
                    toolbarPreferences.isVisible(PdfReaderTool.TTS_SETTINGS)
                val showWordReplacements = PdfReaderTool.TTS_REPLACEMENTS in SharedMobilePdfAvailableTools &&
                    toolbarPreferences.isVisible(PdfReaderTool.TTS_REPLACEMENTS)
                if (showVoiceSettings || showWordReplacements) SharedMobilePdfOverflowItem(
                    "TTS Settings",
                    leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showTtsSettingsExpanded = !showTtsSettingsExpanded }
                )
                if (showTtsSettingsExpanded) {
                    if (showVoiceSettings) SharedMobilePdfOverflowItem(
                        "Voice Settings",
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onVoiceSettings()
                        },
                    )
                    if (showWordReplacements) SharedMobilePdfOverflowItem(
                        "Word Replacements",
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onWordReplacements()
                        },
                    )
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.BOOKMARK)) SharedMobilePdfOverflowItem(
                    if (isBookmarked) "Remove bookmark" else "Bookmark this page",
                    onClick = {
                        showMoreMenu = false
                        onToggleBookmark()
                    }
                )
                if (PdfReaderTool.PAGE_MANAGEMENT in SharedMobilePdfAvailableTools && toolbarPreferences.isVisible(PdfReaderTool.PAGE_MANAGEMENT)) {
                    SharedMobilePdfOverflowItem(
                        "Insert Blank Page",
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = {
                            showMoreMenu = false
                            onInsertBlankPage()
                        }
                    )
                    if (isCurrentPageBlank) {
                        SharedMobilePdfOverflowItem(
                            "Delete Blank Page",
                            isError = true,
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onDeleteBlankPage()
                            }
                        )
                    }
                }
                if (PdfReaderTool.REFLOW in SharedMobilePdfAvailableTools && toolbarPreferences.isVisible(PdfReaderTool.REFLOW)) SharedMobilePdfOverflowItem(
                    when {
                        pdfReflowUiState.isGenerating -> "Generating Text View…"
                        pdfReflowUiState.hasReflowBook -> "Open Text View"
                        else -> "Generate Text View"
                    },
                    enabled = !pdfReflowUiState.isGenerating,
                    leadingIcon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onNativeAction(SharedMobilePdfNativeAction.TEXT_VIEW)
                    }
                )
                if (listOf(PdfReaderTool.SHARE, PdfReaderTool.SAVE_COPY, PdfReaderTool.PRINT).any(toolbarPreferences::isVisible)) SharedMobilePdfOverflowItem(
                    "Share, Save or Print",
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    onClick = { showFileActionsExpanded = !showFileActionsExpanded }
                )
                if (showFileActionsExpanded) {
                    if (toolbarPreferences.isVisible(PdfReaderTool.SHARE)) SharedMobilePdfOverflowItem("Share", leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.SHARE) })
                    if (toolbarPreferences.isVisible(PdfReaderTool.SAVE_COPY)) SharedMobilePdfOverflowItem("Save Copy to Device", leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.SAVE_COPY) })
                    if (toolbarPreferences.isVisible(PdfReaderTool.PRINT)) SharedMobilePdfOverflowItem("Print", leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) }, onClick = { showMoreMenu = false; onNativeAction(SharedMobilePdfNativeAction.PRINT) })
                }
                if (toolbarPreferences.isVisible(PdfReaderTool.FILE_INFO)) SharedMobilePdfOverflowItem(
                    "File Information",
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        showMoreMenu = false
                        onFileInformation()
                    }
                )
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfTopToolButton(
    label: String,
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    isActive: Boolean = false,
    isLoading: Boolean = false,
    icon: @Composable () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp))
        } else {
            CompositionLocalProvider(
                LocalContentColor provides if (isActive) MaterialTheme.colorScheme.primary else LocalContentColor.current
            ) {
                icon()
            }
        }
    }
}

private val SharedMobilePdfAvailableTools = setOf(
    PdfReaderTool.DICTIONARY,
    PdfReaderTool.THEME,
    PdfReaderTool.BRIGHTNESS,
    PdfReaderTool.LOCK_PANNING,
    PdfReaderTool.FILE_INFO,
    PdfReaderTool.VISUAL_OPTIONS,
    PdfReaderTool.TAP_TO_TURN,
    PdfReaderTool.SLIDER,
    PdfReaderTool.TOC,
    PdfReaderTool.SEARCH,
    PdfReaderTool.HIGHLIGHT_ALL,
    PdfReaderTool.EDIT_MODE,
    PdfReaderTool.TTS_CONTROLS,
    PdfReaderTool.TTS_SETTINGS,
    PdfReaderTool.TTS_REPLACEMENTS,
    PdfReaderTool.READING_MODE,
    PdfReaderTool.KEEP_SCREEN_ON,
    PdfReaderTool.SCREEN_ORIENTATION,
    PdfReaderTool.AUTO_SCROLL,
    PdfReaderTool.BOOKMARK,
    PdfReaderTool.PAGE_MANAGEMENT,
    PdfReaderTool.SHARE,
    PdfReaderTool.SAVE_COPY,
    PdfReaderTool.PRINT,
    PdfReaderTool.REFLOW,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfToolbarCustomizationSheet(
    preferences: PdfToolbarPreferences,
    availableTools: Set<PdfReaderTool>,
    onPreferencesChange: (PdfToolbarPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    var localHiddenTools by remember { mutableStateOf(preferences.hiddenToolIds) }
    var flatItems by remember {
        mutableStateOf(buildSharedPdfToolbarItems(preferences, availableTools))
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = rememberSharedToolbarDragDropState(
        lazyListState = lazyListState,
        flatItems = { flatItems },
        onFlatItemsChange = { flatItems = it },
    )

    val commitDragDrop = {
        val next = buildSharedPdfToolbarCommit(flatItems, localHiddenTools, availableTools)
        localHiddenTools = next.hiddenToolIds
        onPreferencesChange(next)
    }

    val resetToDefault = {
        val defaults = PdfToolbarPreferences()
        localHiddenTools = defaults.hiddenToolIds
        flatItems = buildSharedPdfToolbarItems(defaults, availableTools)
        onPreferencesChange(defaults.sanitized(availableTools))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
        ) {
            SharedToolbarCustomizationHeader(
                title = "Customize Toolbar",
                onReset = resetToDefault,
                onDismiss = onDismiss,
            )
            SharedToolbarDragDropList(
                flatItems = flatItems,
                dragDropState = dragDropState,
                emptyPlaceholderTitle = "Drop tools here",
                moreMenuTitle = "More Menu",
                toolRow = { item, isDragging ->
                    val tool = item.toolId?.let(PdfReaderTool::fromId)
                    if (tool != null) {
                        SharedToolbarDragRow(
                            title = tool.title,
                            isDragging = isDragging,
                            leadingIcon = { SharedPdfToolbarDragIcon(tool) },
                            onDragStart = { dragDropState.onDragStart(item.id) },
                            onDrag = { dragDropState.onDrag(it) },
                            onDragEnd = {
                                dragDropState.onDragEnd()
                                flatItems = sanitizeSharedToolbarPlaceholders(flatItems)
                                commitDragDrop()
                            },
                        )
                    }
                },
                moreToolRow = { item ->
                    val tool = item.toolId?.let(PdfReaderTool::fromId)
                    if (tool != null) {
                        SharedToolbarMoreVisibilityRow(
                            title = tool.title,
                            visible = !localHiddenTools.contains(tool.id),
                            onToggle = {
                                val next = if (localHiddenTools.contains(tool.id)) {
                                    localHiddenTools - tool.id
                                } else {
                                    localHiddenTools + tool.id
                                }
                                localHiddenTools = next
                                onPreferencesChange(preferences.copy(hiddenToolIds = next).sanitized(availableTools))
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SharedMobilePdfReflowProgressOverlay(progress: Float) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = true, onClick = {}),
        color = Color.Black.copy(alpha = 0.35f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .width(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Generating Text View…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfOverflowItem(
    text: String,
    enabled: Boolean = true,
    isError: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .width(300.dp)
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            leadingIcon?.invoke()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (!enabled) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            } else if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            trailingIcon?.invoke()
        }
    }
    HorizontalDivider()
}

private enum class SharedMobilePdfSystemUiMode(val label: String) {
    ALWAYS_SHOW("Always Show"),
    SYNC_WITH_MENUS("Sync with Menus"),
    ALWAYS_HIDE("Always Hide")
}

private fun SystemUiMode.toSharedMobilePdfSystemUiMode(): SharedMobilePdfSystemUiMode = when (this) {
    SystemUiMode.DEFAULT -> SharedMobilePdfSystemUiMode.ALWAYS_SHOW
    SystemUiMode.SYNC -> SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS
    SystemUiMode.HIDDEN -> SharedMobilePdfSystemUiMode.ALWAYS_HIDE
}

private fun SharedMobilePdfSystemUiMode.toReaderSystemUiMode(): SystemUiMode = when (this) {
    SharedMobilePdfSystemUiMode.ALWAYS_SHOW -> SystemUiMode.DEFAULT
    SharedMobilePdfSystemUiMode.SYNC_WITH_MENUS -> SystemUiMode.SYNC
    SharedMobilePdfSystemUiMode.ALWAYS_HIDE -> SystemUiMode.HIDDEN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfVisualOptionsSheet(
    displayMode: PdfDisplayMode,
    systemUiMode: SharedMobilePdfSystemUiMode,
    useTwoPageSpread: Boolean,
    firstPageStandaloneInSpread: Boolean,
    rightToLeftPagination: Boolean,
    showVerticalPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    onSystemUiModeChange: (SharedMobilePdfSystemUiMode) -> Unit,
    onTwoPageSpreadChange: (Boolean) -> Unit,
    onFirstPageStandaloneChange: (Boolean) -> Unit,
    onRightToLeftPaginationChange: (Boolean) -> Unit,
    onShowVerticalPageGapChange: (Boolean) -> Unit,
    onShowPageNumberOverlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Visual Options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("System UI", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose when the reader toolbars and system controls are visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            SharedMobilePdfSegmentedControl(
                options = SharedMobilePdfSystemUiMode.entries,
                selectedOption = systemUiMode,
                onOptionSelected = onSystemUiModeChange,
                label = { it.label }
            )
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Page Layout", style = MaterialTheme.typography.titleMedium)
            if (displayMode == PdfDisplayMode.PAGINATION) {
                Spacer(Modifier.height(4.dp))
                Text("PDF Page Spread", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                SharedMobilePdfSegmentedControl(
                    options = listOf(false, true),
                    selectedOption = useTwoPageSpread,
                    onOptionSelected = onTwoPageSpreadChange,
                    label = { if (it) "Two Page" else "Single" }
                )
                if (useTwoPageSpread) {
                    SharedMobilePdfVisualOptionSwitchRow(
                        title = "First Page Alone",
                        description = "Show the cover by itself before paired pages.",
                        checked = firstPageStandaloneInSpread,
                        onCheckedChange = onFirstPageStandaloneChange
                    )
                }
                SharedMobilePdfVisualOptionSwitchRow(
                    title = "Paginated (right-to-left)",
                    description = "Use right-to-left page order and edge navigation.",
                    checked = rightToLeftPagination,
                    onCheckedChange = onRightToLeftPaginationChange,
                )
                Spacer(Modifier.height(12.dp))
            }
            SharedMobilePdfVisualOptionSwitchRow(
                title = "Remove Page Gap",
                description = "Display adjacent pages without spacing.",
                checked = !showVerticalPageGap,
                onCheckedChange = { onShowVerticalPageGapChange(!it) }
            )
            SharedMobilePdfVisualOptionSwitchRow(
                title = "Hide Page Number Overlay",
                description = "Hide the number shown on each PDF page.",
                checked = !showPageNumberOverlay,
                onCheckedChange = { onShowPageNumberOverlayChange(!it) }
            )
            }
        }
    }
@Composable
private fun <T> SharedMobilePdfSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    label: (T) -> String
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfVisualOptionSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SharedMobilePdfReaderBottomBar(
    state: SharedPdfReaderState,
    tools: List<PdfReaderTool>,
    isStylusOnlyMode: Boolean = false,
    onToggleStylusOnlyMode: (() -> Unit)? = null,
    onShowSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSearch: () -> Unit,
    onToolSelected: (PdfInkTool) -> Unit,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearPage: () -> Unit,
    ttsState: SharedMobileEpubLocalTtsState,
    isTtsPlayingOrLoading: Boolean,
            onToggleTts: () -> Unit,
            onTheme: () -> Unit,
            onBrightness: () -> Unit,
            onToggleScrollLock: () -> Unit,
            onScreenOrientation: () -> Unit,
            onDictionary: () -> Unit,
            showAllTextHighlights: Boolean = false,
            isAllTextHighlightLoading: Boolean = false,
            onToggleHighlights: () -> Unit = {},
            applySystemBarInsets: Boolean,
            modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.then(
                if (applySystemBarInsets) {
                    Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                } else {
                    Modifier
                }
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tools.forEach { tool ->
                    when (tool) {
                        PdfReaderTool.SLIDER -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onShowSlider) { Icon(SharedReaderIcons.Slider, contentDescription = "Navigation slider") }
                        PdfReaderTool.TOC -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onOpenDrawer) { Icon(Icons.Default.Menu, contentDescription = "Contents") }
                        PdfReaderTool.SEARCH -> SharedMobilePdfBottomToolButton(enabled = isPdfReaderToolEnabledDuringTts(tool, isTtsPlayingOrLoading), onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        PdfReaderTool.HIGHLIGHT_ALL -> SharedMobilePdfBottomToolButton(
                            selected = showAllTextHighlights,
                            onClick = onToggleHighlights,
                        ) {
                            if (isAllTextHighlightLoading) {
                                CircularProgressIndicator(Modifier.size(20.dp))
                            } else {
                                Icon(SharedReaderIcons.HighlightText, contentDescription = "Highlight selectable text")
                            }
                        }
                        PdfReaderTool.EDIT_MODE -> SharedMobilePdfBottomToolButton(
                            selected = state.selectedTool != PdfInkTool.NONE,
                            onClick = { onToolSelected(if (state.selectedTool == PdfInkTool.NONE) PdfInkTool.PEN else PdfInkTool.NONE) },
                        ) { Icon(Icons.Default.Edit, contentDescription = "Edit mode") }
                        PdfReaderTool.TTS_CONTROLS -> SharedMobilePdfBottomToolButton(onClick = onToggleTts) {
                            Icon(
                                if (ttsState != SharedMobileEpubLocalTtsState.IDLE) Icons.Default.Close else SharedReaderIcons.TextToSpeech,
                                contentDescription = "Text to speech",
                            )
                        }
                        PdfReaderTool.DICTIONARY -> SharedMobilePdfBottomToolButton(onClick = onDictionary) { Icon(SharedReaderIcons.Dictionary, contentDescription = "Dictionary") }
                        PdfReaderTool.THEME -> SharedMobilePdfBottomToolButton(onClick = onTheme) { Icon(Icons.Default.Palette, contentDescription = "Theme") }
                        PdfReaderTool.BRIGHTNESS -> SharedMobilePdfBottomToolButton(onClick = onBrightness) { Icon(SharedReaderIcons.Contrast, contentDescription = "Brightness") }
                        PdfReaderTool.LOCK_PANNING -> SharedMobilePdfBottomToolButton(onClick = onToggleScrollLock) { Icon(if (state.isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (state.isScrollLocked) "Unlock panning" else "Lock panning") }
                        PdfReaderTool.SCREEN_ORIENTATION -> SharedMobilePdfBottomToolButton(onClick = onScreenOrientation) { Icon(SharedReaderIcons.ScreenRotation, contentDescription = "Screen orientation") }
                        else -> Unit
                    }
                }
            }
            if (state.selectedTool != PdfInkTool.NONE) {
                SharedPdfInteractionDock(
                    isTextSelectionMode = false,
                    isStylusOnlyMode = isStylusOnlyMode,
                    onToggleStylusOnlyMode = onToggleStylusOnlyMode,
                    selectedTool = state.selectedTool,
                    selectedColor = state.selectedColorArgb,
                    strokeWidth = state.strokeWidth,
                    toolConfigs = state.toolConfigs,
                    penPalette = state.penPalette,
                    lastActivePenTool = state.lastActivePenTool,
                    lastActiveHighlighterTool = state.lastActiveHighlighterTool,
                    onPanSelected = { onToolSelected(PdfInkTool.NONE) },
                    onTextSelectionSelected = { onToolSelected(PdfInkTool.NONE) },
                    onToolSelected = onToolSelected,
                    onColorSelected = onColorSelected,
                    onStrokeWidthChange = onStrokeWidthChange,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onClearPage = onClearPage,
                    canUndo = state.annotations.any { it.pageIndex == state.pageIndex },
                    canRedo = state.canRedoAnnotationEdit,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfBottomToolButton(
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .widthIn(min = 44.dp)
    ) {
        Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun SharedMobilePdfReaderDrawer(
    book: BookItem,
    state: SharedPdfReaderState,
    tableOfContents: List<PdfTocEntry>,
    onGoToPage: (Int) -> Unit,
    onEditNote: (SharedPdfAnnotation) -> Unit,
    onDeleteHighlight: (SharedPdfAnnotation) -> Unit,
    onToggleBookmark: () -> Unit,
    onRenameBookmark: (Int, String) -> Unit,
    onDeleteBookmark: (Int) -> Unit,
    onGoToDisplayPage: (Int) -> Unit,
    pdfPassword: String?,
    tabsEnabled: Boolean,
    tabs: List<BookItem>,
    activeTabBookId: String?,
    isTopTabStripVisible: Boolean,
    onTopTabStripVisibilityChange: (Boolean) -> Unit,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onNewTab: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sections = remember(tabsEnabled, tabs.isNotEmpty()) {
        buildList {
            add(SharedMobilePdfDrawerSection.CHAPTERS)
            add(SharedMobilePdfDrawerSection.PAGES)
            add(SharedMobilePdfDrawerSection.BOOKMARKS)
            add(SharedMobilePdfDrawerSection.HIGHLIGHTS)
            if (tabsEnabled && tabs.isNotEmpty()) add(SharedMobilePdfDrawerSection.TABS)
        }
    }
    val pagerState = rememberPagerState(initialPage = 0) { sections.size }
    LaunchedEffect(sections.size) {
        val maxPage = sections.size - 1
        if (pagerState.currentPage > maxPage) pagerState.scrollToPage(maxPage.coerceAtLeast(0))
    }
    ModalDrawerSheet(modifier = Modifier.width(348.dp)) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage.coerceAtMost(sections.lastIndex)) {
                sections.forEachIndexed { index, section ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(section.label) }
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (sections.getOrNull(page)) {
                    SharedMobilePdfDrawerSection.CHAPTERS -> SharedMobilePdfChaptersDrawerPage(
                        entries = tableOfContents,
                        currentPageIndex = state.currentNearestPdfPageIndex ?: 0,
                        onGoToPage = onGoToPage,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.PAGES -> SharedMobilePdfPagesDrawerPage(
                        book = book,
                        state = state,
                        pdfPassword = pdfPassword,
                        onGoToPage = onGoToDisplayPage,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.BOOKMARKS -> SharedMobilePdfBookmarksDrawerPage(
                        state = state,
                        onGoToPage = onGoToPage,
                        onRenameBookmark = onRenameBookmark,
                        onDeleteBookmark = onDeleteBookmark,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.HIGHLIGHTS -> SharedMobilePdfAnnotationsDrawerPage(
                        state = state,
                        onGoToPage = onGoToPage,
                        onEditNote = onEditNote,
                        onDeleteHighlight = onDeleteHighlight,
                        modifier = Modifier.fillMaxSize()
                    )
                    SharedMobilePdfDrawerSection.TABS -> SharedMobilePdfTabsDrawerPage(
                        tabs = tabs,
                        activeTabBookId = activeTabBookId,
                        isTopTabStripVisible = isTopTabStripVisible,
                        onTopTabStripVisibilityChange = onTopTabStripVisibilityChange,
                        onOpenTab = onOpenTab,
                        onCloseTab = onCloseTab,
                        onNewTab = onNewTab,
                        modifier = Modifier.fillMaxSize(),
                    )
                    null -> Unit
                }
            }
        }
    }
}

private enum class SharedMobilePdfDrawerSection(val label: String) {
    CHAPTERS("Chapters"),
    PAGES("Pages"),
    BOOKMARKS("Bookmarks"),
    HIGHLIGHTS("Highlights"),
    TABS("Tabs"),
}

@Composable
private fun SharedMobilePdfTabsDrawerPage(
    tabs: List<BookItem>,
    activeTabBookId: String?,
    isTopTabStripVisible: Boolean,
    onTopTabStripVisibilityChange: (Boolean) -> Unit,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ListItem(
            headlineContent = { Text(readerString("tabs", "Tabs")) },
            supportingContent = { Text("${tabs.size}") },
            trailingContent = {
                TextButton(onClick = onNewTab) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(readerString("content_desc_new_tab", "New tab"))
                }
            },
        )
        ListItem(
            headlineContent = { Text(readerString("pdf_show_top_tab_strip", "Show top tab strip")) },
            trailingContent = {
                Switch(
                    checked = isTopTabStripVisible,
                    onCheckedChange = onTopTabStripVisibilityChange,
                )
            },
            modifier = Modifier.clickable {
                onTopTabStripVisibilityChange(!isTopTabStripVisible)
            },
        )
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(tabs, key = { "pdf_drawer_tab_${it.id}" }) { tab ->
                ListItem(
                    headlineContent = {
                        Text(
                            tab.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = tab.author?.let { author ->
                        { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    leadingContent = if (tab.id == activeTabBookId) {
                        { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Selected")) }
                    } else {
                        null
                    },
                    trailingContent = {
                        IconButton(onClick = { onCloseTab(tab) }) {
                            Icon(Icons.Default.Close, contentDescription = readerString("close_tab", "Close tab"))
                        }
                    },
                    modifier = Modifier.clickable { onOpenTab(tab) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SharedMobilePdfPagesDrawerPage(
    book: BookItem,
    state: SharedPdfReaderState,
    pdfPassword: String?,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayPageCount = state.displayPageCount
    val pageRows = remember(displayPageCount) { sharedPdfThumbnailRows(displayPageCount) }
    val currentRowIndex = sharedPdfThumbnailRowFor(state.pageIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(
                onClick = {
                    scope.launch {
                        if (currentRowIndex in pageRows.indices) {
                            listState.animateScrollToItem(currentRowIndex)
                        }
                    }
                }
            ) {
                Text("Locate")
            }
        }
        HorizontalDivider()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 12.dp)
            ) {
                items(pageRows, key = { it.firstOrNull() ?: 0 }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { pageIdx ->
                            val isBlank = state.virtualPageLayout.getOrNull(pageIdx) is SharedPdfVirtualPage.BlankPage
                            val pdfPage = sharedPdfPdfPageIndexAt(state.virtualPageLayout, pageIdx)
                            val isCurrent = state.pageIndex == pageIdx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(0.707f)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        width = if (isCurrent) 2.dp else 1.dp,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { onGoToPage(pageIdx) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isBlank && pdfPage != null) {
                                    val thumbnail = rememberSharedMobilePdfPageThumbnail(
                                        book = book,
                                        pageIndex = pdfPage,
                                        password = pdfPassword,
                                    )
                                    thumbnail.bitmap?.let { bitmap ->
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Page ${pageIdx + 1}",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Text(
                                    text = "${pageIdx + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
            SharedMobileLazyListScrollbar(
                state = listState,
                itemCount = pageRows.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedMobilePdfChaptersDrawerPage(
    entries: List<PdfTocEntry>,
    currentPageIndex: Int,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) {
        SharedMobilePdfEmptyDrawerPage("No chapters found in this PDF.", modifier)
        return
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val parentIndices: Set<Int> = remember(entries) {
        entries.indices.filter { index ->
            entries.getOrNull(index + 1)?.nestLevel?.let { it > entries[index].nestLevel } == true
        }.toSet()
    }
    var expandedIndices by remember(entries) { mutableStateOf(parentIndices) }
    var query by remember(entries) { mutableStateOf("") }
    val activeIndex = entries.indexOfLast { it.pageIndex <= currentPageIndex }
    val visibleEntries = if (query.isNotBlank()) {
        entries.mapIndexedNotNull { index, entry ->
            (index to entry).takeIf { entry.title.contains(query.trim(), ignoreCase = true) }
        }
    } else {
        buildList {
            val visibleAtLevel = BooleanArray(65)
            visibleAtLevel[0] = true
            entries.forEachIndexed { index, entry ->
                val level = entry.nestLevel.coerceIn(0, visibleAtLevel.lastIndex)
                if (visibleAtLevel[level]) add(index to entry)
                if (level < visibleAtLevel.lastIndex) {
                    visibleAtLevel[level + 1] = visibleAtLevel[level] && index in expandedIndices
                }
            }
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, contentDescription = "Clear search") } }
            } else null,
            placeholder = { Text("Search chapters") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { expandedIndices = parentIndices }) { Text("Expand all") }
            TextButton(onClick = { expandedIndices = emptySet() }) { Text("Collapse all") }
            TextButton(onClick = {
                val target = visibleEntries.indexOfFirst { it.first == activeIndex }
                if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
            }) { Text("Locate") }
        }
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(visibleEntries, key = { (index, entry) ->
                    "${entry.pageIndex}_${entry.nestLevel}_${entry.title}_$index"
                }) { (index, entry) ->
                    val hasChildren = index in parentIndices
                    val expanded = index in expandedIndices
                    NavigationDrawerItem(
                        icon = if (hasChildren) {
                            {
                                IconButton(
                                    onClick = {
                                        expandedIndices = if (expanded) expandedIndices - index else expandedIndices + index
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = if (expanded) "Collapse chapter" else "Expand chapter"
                                    )
                                }
                            }
                        } else null,
                        label = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        selected = index == activeIndex,
                        onClick = { onGoToPage(entry.pageIndex) },
                        badge = { Text("${entry.pageIndex + 1}", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.padding(
                            start = (4 + entry.nestLevel.coerceAtMost(6) * 16).dp,
                            end = 4.dp,
                            top = 2.dp,
                            bottom = 2.dp
                        )
                    )
                }
            }
            SharedMobileLazyListScrollbar(
                state = listState,
                itemCount = visibleEntries.size,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SharedMobileLazyListScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    if (itemCount <= 1) return
    val visibleCount = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    if (visibleCount >= itemCount) return
    val scope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableStateOf(1) }
    val thumbFraction = (visibleCount.toFloat() / itemCount).coerceIn(0.08f, 1f)
    val maxFirst = (itemCount - visibleCount).coerceAtLeast(1)
    val progress = (state.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Canvas(
        modifier = modifier
            .width(12.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(itemCount, visibleCount) {
                detectDragGestures { change, _ ->
                    val target = ((change.position.y / trackHeightPx) * maxFirst).toInt().coerceIn(0, maxFirst)
                    scope.launch { state.scrollToItem(target) }
                }
            }
    ) {
        val thumbHeight = size.height * thumbFraction
        val thumbTop = (size.height - thumbHeight) * progress
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(size.width - 4.dp.toPx(), thumbTop),
            size = androidx.compose.ui.geometry.Size(3.dp.toPx(), thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
        )
    }
}

@Composable
private fun SharedMobilePdfEmptyDrawerPage(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SharedMobilePdfBookmarksDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    onRenameBookmark: (Int, String) -> Unit,
    onDeleteBookmark: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.bookmarks.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No bookmarks yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var menuBookmark by remember { mutableStateOf<SharedPdfBookmark?>(null) }
    var renameBookmark by remember { mutableStateOf<SharedPdfBookmark?>(null) }
    var deleteBookmark by remember { mutableStateOf<SharedPdfBookmark?>(null) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 0.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
    ) {
        items(state.bookmarks.sortedBy { it.pageIndex }, key = { "bookmark_${it.pageIndex}_${it.createdAt}" }) { bookmark ->
            NavigationDrawerItem(
                icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                label = { Text(bookmark.label.ifBlank { "Page ${bookmark.pageIndex + 1}" }) },
                selected = bookmark.pageIndex == (state.currentNearestPdfPageIndex ?: 0),
                onClick = { onGoToPage(bookmark.pageIndex) },
                badge = {
                    Box {
                        IconButton(onClick = { menuBookmark = bookmark }) { Icon(Icons.Default.MoreVert, contentDescription = "Bookmark options", modifier = Modifier.size(18.dp)) }
                        DropdownMenu(expanded = menuBookmark?.pageIndex == bookmark.pageIndex, onDismissRequest = { menuBookmark = null }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { renameBookmark = bookmark; menuBookmark = null })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { deleteBookmark = bookmark; menuBookmark = null })
                        }
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
    renameBookmark?.let { bookmark ->
        var label by remember(bookmark.pageIndex, bookmark.createdAt) { mutableStateOf(bookmark.label) }
        AlertDialog(
            onDismissRequest = { renameBookmark = null },
            title = { Text("Rename Bookmark") },
            text = { OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("New name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRenameBookmark(bookmark.pageIndex, label); renameBookmark = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameBookmark = null }) { Text("Cancel") } }
        )
    }
    deleteBookmark?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { deleteBookmark = null },
            title = { Text("Delete Bookmark?") },
            text = { Text("This bookmark will be removed from the document.") },
            confirmButton = { TextButton(onClick = { onDeleteBookmark(bookmark.pageIndex); deleteBookmark = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteBookmark = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedMobilePdfAnnotationsDrawerPage(
    state: SharedPdfReaderState,
    onGoToPage: (Int) -> Unit,
    onEditNote: (SharedPdfAnnotation) -> Unit,
    onDeleteHighlight: (SharedPdfAnnotation) -> Unit,
    modifier: Modifier = Modifier
) {
    val highlights = state.annotations.filter { it.kind == PdfAnnotationKind.HIGHLIGHT }
    if (highlights.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No highlights yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var notesOnly by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SharedPdfAnnotation?>(null) }
    val filtered = highlights.filter { !notesOnly || !it.note.isNullOrBlank() }
        .sortedWith(compareBy({ it.pageIndex }, { it.createdAt }, { it.id }))
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !notesOnly, onClick = { notesOnly = false }, label = { Text("All") })
            FilterChip(selected = notesOnly, onClick = { notesOnly = true }, label = { Text("With notes") })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(filtered, key = { it.id }) { annotation ->
                var menuExpanded by remember(annotation.id) { mutableStateOf(false) }
                ListItem(
                    headlineContent = {
                        Text(annotation.text.ifBlank { "Highlighted section" }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(Color(annotation.colorArgb), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text("Page ${annotation.pageIndex + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (annotation.comments.any { it.contents.isNotBlank() }) {
                                    Spacer(Modifier.width(8.dp)); Text("${annotation.comments.count { it.contents.isNotBlank() }} comments", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            annotation.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Spacer(Modifier.height(8.dp))
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f), modifier = Modifier.fillMaxWidth()) {
                                    Text(note, style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic), modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Highlight options") }
                            DropdownMenu(menuExpanded, { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (annotation.note.isNullOrBlank()) "Add note" else "Edit note") },
                                    onClick = { menuExpanded = false; onEditNote(annotation) }
                                )
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; deleteTarget = annotation })
                            }
                        }
                    },
                    modifier = Modifier.clickable { onGoToPage(annotation.pageIndex) }
                )
                HorizontalDivider()
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete highlight?") },
            text = { Text("This highlight, its note, and its comments will be removed.") },
            confirmButton = { TextButton(onClick = { onDeleteHighlight(target); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SharedMobilePdfSearchResultsPanel(
    query: String,
    results: List<SharedPdfSearchResult>,
    activeResultIndex: Int,
    isSearching: Boolean,
    onResultClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        when {
            isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            query.isBlank() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Enter a search term", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            results.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No results for “${query.trim()}”", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Text(
                        text = "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(results.size) { index ->
                    val result = results[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index == activeResultIndex) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onResultClick(index) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "${result.pageIndex + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.widthIn(min = 28.dp)
                        )
                        Text(
                            text = result.preview.ifBlank { "Match on page ${result.pageIndex + 1}" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SharedMobilePdfSearchNavigationPill(
    activeIndex: Int,
    resultCount: Int,
    highlightMode: SearchHighlightMode,
    onToggleHighlightMode: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShowResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleHighlightMode) {
                Icon(
                    if (highlightMode == SearchHighlightMode.ALL) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle search highlights",
                    tint = if (highlightMode == SearchHighlightMode.ALL) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
            IconButton(onClick = onPrevious, enabled = activeIndex > 0) {
                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous result")
            }
            TextButton(onClick = onShowResults) {
                Text(
                    if (activeIndex in 0 until resultCount) "${activeIndex + 1} of $resultCount"
                    else "$resultCount results"
                )
            }
            IconButton(onClick = onNext, enabled = activeIndex < resultCount - 1) {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next result")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobilePdfThemePanel(
    settings: ReaderSettings,
    customThemes: List<ReaderTheme>,
    onCustomThemesChange: (List<ReaderTheme>) -> Unit,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        ) {
            item {
                SharedReaderThemeControls(
                    settings = settings,
                    builtInThemes = BuiltInPdfReaderThemes,
                    customThemes = customThemes,
                    onCustomThemesChange = onCustomThemesChange,
                    onSettingsChange = onSettingsChange,
                )
            }
        }
    }
}

@Composable
private fun sharedMobilePdfViewerBackground(theme: ReaderTheme, displayMode: PdfDisplayMode): Color {
    return when (theme.id) {
        "no_theme", "system" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) MaterialTheme.colorScheme.surfaceContainer else Color.Black
        "reverse" -> if (displayMode == PdfDisplayMode.VERTICAL_SCROLL) Color.Black else Color.White
        else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
    }
}

private fun sharedMobilePdfPageBackground(theme: ReaderTheme): Color {
    return when (theme.id) {
        "no_theme", "system" -> Color.White
        "reverse" -> Color.Black
        else -> theme.backgroundColor.takeIf { it.isSpecified } ?: Color.White
    }
}

private fun sharedMobilePdfPageTextColor(theme: ReaderTheme): Color {
    return when (theme.id) {
        "no_theme", "system" -> Color.Black
        "reverse" -> Color.White
        else -> theme.textColor.takeIf { it.isSpecified } ?: Color.Black
    }
}

private fun sharedMobilePdfColorFilter(theme: ReaderTheme): ColorFilter? {
    if (theme.id == "no_theme" || theme.id == "system") return null
    val matrix = if (theme.id == "reverse") {
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    } else {
        val background = sharedMobilePdfPageBackground(theme)
        val foreground = sharedMobilePdfPageTextColor(theme)
        val backgroundRed = background.red * 255f
        val backgroundGreen = background.green * 255f
        val backgroundBlue = background.blue * 255f
        val foregroundRed = foreground.red * 255f
        val foregroundGreen = foreground.green * 255f
        val foregroundBlue = foreground.blue * 255f
        val deltaRed = (backgroundRed - foregroundRed) / 255f
        val deltaGreen = (backgroundGreen - foregroundGreen) / 255f
        val deltaBlue = (backgroundBlue - foregroundBlue) / 255f
        floatArrayOf(
            deltaRed * 0.2126f, deltaRed * 0.7152f, deltaRed * 0.0722f, 0f, foregroundRed,
            deltaGreen * 0.2126f, deltaGreen * 0.7152f, deltaGreen * 0.0722f, 0f, foregroundGreen,
            deltaBlue * 0.2126f, deltaBlue * 0.7152f, deltaBlue * 0.0722f, 0f, foregroundBlue,
            0f, 0f, 0f, 1f, 0f
        )
    }
    return ColorFilter.colorMatrix(ColorMatrix(matrix))
}

@Composable
private fun SharedMobilePdfZoomViewport(
    camera: PdfZoomCamera,
    onCameraChanged: (PdfZoomCamera) -> Unit,
    zoomEnabled: Boolean,
    tapGesturesEnabled: Boolean = true,
    maxScale: Float,
    verticalDocumentMode: Boolean = false,
    onSingleTap: (Offset) -> Unit = {},
    onZoomChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PdfZoomCamera) -> Unit
) {
    val latestCamera by rememberUpdatedState(camera)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val oneHandZoomDistancePx = with(LocalDensity.current) { 240.dp.toPx() }
    val scope = rememberCoroutineScope()
    var cameraAnimationJob by remember { mutableStateOf<Job?>(null) }
    var showZoomIndicator by remember { mutableStateOf(false) }
    val zoomPercentage = pdfZoomIndicatorPercent(camera.scale)

    LaunchedEffect(zoomPercentage) {
        showZoomIndicator = camera.isZoomed()
        if (showZoomIndicator) {
            delay(1500)
            showZoomIndicator = false
        }
    }

    fun updateCamera(next: PdfZoomCamera) {
        onCameraChanged(next)
        onZoomChanged(next.scale)
    }

    fun animateCameraTo(target: PdfZoomCamera, durationMillis: Int) {
        cameraAnimationJob?.cancel()
        val start = latestCamera
        cameraAnimationJob = scope.launch {
            try {
                Animatable(0f).animateTo(
                    1f,
                    animationSpec = tween(durationMillis, easing = FastOutSlowInEasing)
                ) {
                    val progress = value
                    updateCamera(
                        PdfZoomCamera(
                            scale = start.scale + (target.scale - start.scale) * progress,
                            offset = PdfZoomPoint(
                                start.offset.x + (target.offset.x - start.offset.x) * progress,
                                start.offset.y + (target.offset.y - start.offset.y) * progress
                            )
                        )
                    )
                }
                updateCamera(target)
            } finally {
                cameraAnimationJob = null
            }
        }
    }

    fun flingCamera(velocityX: Float, velocityY: Float) {
        val speed = kotlin.math.sqrt(velocityX * velocityX + velocityY * velocityY)
        if (speed < 600f || viewport.width <= 0 || viewport.height <= 0) return
        cameraAnimationJob?.cancel()
        val start = latestCamera
        val directionX = velocityX / speed
        val directionY = velocityY / speed
        val viewportSize = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat())
        cameraAnimationJob = scope.launch {
            try {
                Animatable(0f).animateDecay(
                    initialVelocity = speed * 0.72f,
                    animationSpec = exponentialDecay(frictionMultiplier = 2f)
                ) {
                    updateCamera(
                        PdfZoomCamera(
                            scale = start.scale,
                            offset = PdfZoomPoint(
                                start.offset.x + value * directionX,
                                start.offset.y + value * directionY
                            )
                        ).normalized(viewportSize, viewportSize, maxScale = maxScale)
                    )
                }
            } finally {
                cameraAnimationJob = null
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewport = it }
            .pointerInput(zoomEnabled, viewport, maxScale, verticalDocumentMode) {
                if (!zoomEnabled) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    cameraAnimationJob?.cancel()
                    val velocityTracker = VelocityTracker()
                    velocityTracker.addPosition(firstDown.uptimeMillis, firstDown.position)
                    var gestureAccepted = latestCamera.isZoomed() && !verticalDocumentMode
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        event.changes.firstOrNull { it.pressed }?.let {
                            velocityTracker.addPosition(it.uptimeMillis, it.position)
                        }
                        if (pressedCount > 1 && kotlin.math.abs(zoom - 1f) > 0.005f) gestureAccepted = true
                        if (
                            verticalDocumentMode && latestCamera.isZoomed() && pressedCount == 1 &&
                            kotlin.math.abs(pan.x) > kotlin.math.abs(pan.y) * 1.2f
                        ) gestureAccepted = true
                        if (gestureAccepted && viewport.width > 0 && viewport.height > 0) {
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val pivot = if (centroid == Offset.Unspecified) {
                                Offset(viewport.width / 2f, viewport.height / 2f)
                            } else centroid
                            updateCamera(
                                latestCamera.transformed(
                                    zoomChange = if (pressedCount > 1) zoom else 1f,
                                    panChange = PdfZoomPoint(pan.x, pan.y),
                                    pivot = PdfZoomPoint(pivot.x, pivot.y),
                                    viewport = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat()),
                                    content = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat()),
                                    maxScale = maxScale
                                )
                            )
                            event.changes.forEach { if (it.pressed) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (latestCamera.scale <= 1.05f) updateCamera(PdfZoomCamera())
                    if (gestureAccepted && latestCamera.isZoomed()) {
                        velocityTracker.calculateVelocity().let { velocity ->
                            flingCamera(velocity.x, if (verticalDocumentMode) 0f else velocity.y)
                        }
                    }
                }
            }
            .pointerInput(zoomEnabled, tapGesturesEnabled, viewport, verticalDocumentMode, oneHandZoomDistancePx) {
                if (!tapGesturesEnabled) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val firstUp = waitForUpOrCancellation() ?: return@awaitEachGesture
                    var secondDown: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    try {
                        withTimeout(viewConfiguration.doubleTapTimeoutMillis) {
                            while (secondDown == null) {
                                secondDown = awaitPointerEvent().changes.firstOrNull { it.changedToDown() }
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (!firstUp.isConsumed) onSingleTap(firstDown.position)
                        return@awaitEachGesture
                    }
                    val pivot = secondDown?.position ?: firstDown.position
                    val startCamera = latestCamera
                    var latest = pivot
                    var quickUp = false
                    var earlyOneHandZoom = false
                    val movementSlop = maxOf(2f, viewConfiguration.touchSlop * 0.35f)
                    try {
                        withTimeout(90L) {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == secondDown?.id }
                                    ?: return@withTimeout
                                latest = change.position
                                val delta = latest - pivot
                                if (zoomEnabled &&
                                    kotlin.math.abs(delta.y) >= movementSlop &&
                                    kotlin.math.abs(delta.y) >= kotlin.math.abs(delta.x) * 1.1f
                                ) {
                                    earlyOneHandZoom = true
                                    change.consume()
                                    return@withTimeout
                                }
                                if (change.changedToUp()) {
                                    quickUp = true
                                    change.consume()
                                    return@withTimeout
                                }
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        // Holding the second tap enters Android's one-hand zoom mode.
                    }

                    if (!zoomEnabled) {
                        if (!quickUp) waitForUpOrCancellation()
                        return@awaitEachGesture
                    }

                    val viewportSize = PdfZoomSize(viewport.width.toFloat(), viewport.height.toFloat())
                    if (quickUp && !earlyOneHandZoom) {
                        val target = if (verticalDocumentMode) {
                            pdfVerticalDoubleTapTargetScale(startCamera.scale)
                        } else {
                            pdfDoubleTapTargetScale(startCamera.scale)
                        }
                        val targetCamera = if (target <= 1f) PdfZoomCamera()
                            else startCamera.transformed(
                                zoomChange = target / startCamera.scale,
                                panChange = PdfZoomPoint(0f, 0f),
                                pivot = PdfZoomPoint(pivot.x, pivot.y),
                                viewport = viewportSize,
                                content = viewportSize,
                                maxScale = maxScale
                            )
                        animateCameraTo(targetCamera, if (verticalDocumentMode) 400 else 300)
                        return@awaitEachGesture
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == secondDown?.id } ?: break
                        latest = change.position
                        val rawDragY = latest.y - pivot.y
                        val nextScale = com.aryan.reader.shared.pdf.pdfOneHandZoomScale(
                            startScale = startCamera.scale,
                            totalDragY = if (verticalDocumentMode) rawDragY * startCamera.scale else rawDragY,
                            dragDistanceForDoublePx = oneHandZoomDistancePx,
                            maxScale = maxScale
                        )
                        updateCamera(
                            startCamera.transformed(
                                zoomChange = nextScale / startCamera.scale,
                                panChange = PdfZoomPoint(0f, 0f),
                                pivot = PdfZoomPoint(viewport.width / 2f, viewport.height / 2f),
                                viewport = viewportSize,
                                content = viewportSize,
                                maxScale = maxScale
                            )
                        )
                        change.consume()
                        if (change.changedToUp()) break
                    }
                    if (latestCamera.scale <= 1.05f) animateCameraTo(PdfZoomCamera(), 180)
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = camera.scale
                    scaleY = camera.scale
                    translationX = camera.offset.x
                    translationY = camera.offset.y
                    transformOrigin = TransformOrigin.Center
                }
        ) {
            content(camera)
        }
        AnimatedVisibility(
            visible = showZoomIndicator,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 88.dp, end = 16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.clickable(enabled = zoomEnabled) { animateCameraTo(PdfZoomCamera(), 400) }
            ) {
                Text(
                    text = "$zoomPercentage%",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/** Lets the reader screen drive the vertical-scroll list (hardware arrow keys) from outside it. */
class SharedMobilePdfVerticalScrollController {
    private var lazyListState: LazyListState? = null

    internal fun attach(state: LazyListState) {
        lazyListState = state
    }

    internal fun detach() {
        lazyListState = null
    }

    suspend fun scrollByViewportFraction(fraction: Float) {
        val state = lazyListState ?: return
        val viewportHeightPx = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
        if (viewportHeightPx <= 0) return
        state.scrollBy(viewportHeightPx * fraction)
    }
}

@Composable
private fun SharedMobilePdfVerticalPages(
    book: BookItem,
    pdfPassword: String?,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    textureAlpha: Float,
    pageCount: Int,
    virtualLayout: List<SharedPdfVirtualPage>,
    navigationRequestPage: Int,
    navigationRequestToken: Int,
    navigationCenterFraction: Float,
    showPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    searchResults: List<SharedPdfSearchResult>,
    ttsPageIndex: Int?,
    ttsHighlightBounds: List<PdfPageBounds>,
    activeStroke: List<PdfPagePoint>,
    isStylusOnlyMode: Boolean = false,
    verticalScrollController: SharedMobilePdfVerticalScrollController? = null,
    autoScrollPlaying: Boolean,
    autoScrollTemporarilyPaused: Boolean,
    autoScrollSpeed: Float,
    autoScrollMusicianMode: Boolean,
    onAutoScrollInteraction: (Long) -> Unit,
    onVisiblePageChanged: (Int) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int, Boolean) -> Unit,
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (Int, com.aryan.reader.shared.pdf.PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int, Int) -> Unit,
    userScrollEnabled: Boolean,
    isScrollLocked: Boolean,
    zoomCamera: PdfZoomCamera,
    onZoomCameraChanged: (PdfZoomCamera) -> Unit,
    textDraft: SharedPdfTextDraft?,
    onTextDraftChange: (SharedPdfTextDraft) -> Unit,
    onTextPageTap: (SharedPdfAnnotation?) -> Unit,
    richTextController: SharedPdfRichTextController?,
    isRichTextEditingEnabled: Boolean,
    showAllTextHighlights: Boolean = false,
    onAllTextHighlightsLoadingChange: (Boolean) -> Unit = {},
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.pageIndex.coerceIn(0, pageCount - 1))
    val scope = rememberCoroutineScope()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    DisposableEffect(verticalScrollController, listState) {
        verticalScrollController?.attach(listState)
        onDispose { verticalScrollController?.detach() }
    }
    var viewportSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    var musicianLeftHoldProgress by remember(book.id) { mutableStateOf(0f) }
    var musicianRightHoldProgress by remember(book.id) { mutableStateOf(0f) }
    LaunchedEffect(isListDragged) {
        if (isListDragged && autoScrollPlaying) onAutoScrollInteraction(300L)
    }
    LaunchedEffect(autoScrollPlaying, autoScrollTemporarilyPaused, autoScrollSpeed, listState) {
        if (!autoScrollPlaying || autoScrollTemporarilyPaused) return@LaunchedEffect
        var previousFrame = withFrameNanos { it }
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val frame = withFrameNanos { it }
            val deltaSeconds = (frame - previousFrame) / 1_000_000_000f
            previousFrame = frame
            if (deltaSeconds <= 0f || deltaSeconds > 0.1f) continue
            listState.scrollBy(pdfAutoScrollPixelsPerSecond(autoScrollSpeed) * deltaSeconds)
        }
    }
    val navigationRender = rememberSharedMobilePdfPageRender(
        book = book,
        pageIndex = sharedPdfPdfPageIndexAt(virtualLayout, navigationRequestPage)
            ?: sharedPdfNearestPdfPageIndex(virtualLayout, navigationRequestPage)
            ?: 0,
        zoomScale = zoomCamera.scale,
        password = pdfPassword,
    )
    LaunchedEffect(navigationRequestToken, pageCount, viewportSize, navigationRender.aspectRatio) {
        if (viewportSize.height <= 0) return@LaunchedEffect
        val target = navigationRequestPage.coerceIn(0, pageCount - 1)
        val pageHeight = (viewportSize.width / navigationRender.aspectRatio.coerceIn(0.1f, 10f)).roundToInt()
        val centeredOffset = centeredPdfPageScrollOffset(
            viewportHeightPx = viewportSize.height,
            pageHeightPx = pageHeight,
            pageFraction = navigationCenterFraction
        )
        listState.animateScrollToItem(
            index = target,
            scrollOffset = centeredOffset
        )
    }
    LaunchedEffect(listState, pageCount) {
        snapshotFlow {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - center)
            }?.index ?: listState.firstVisibleItemIndex
        }
            .distinctUntilChanged()
            .collect { visiblePage ->
                onVisiblePageChanged(visiblePage.coerceIn(0, pageCount - 1))
            }
    }
    Box(modifier) {
        SharedMobilePdfZoomViewport(
            camera = zoomCamera,
            onCameraChanged = onZoomCameraChanged,
            zoomEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
            tapGesturesEnabled = state.selectedTool == PdfInkTool.NONE || state.selectedTool == PdfInkTool.TEXT || isStylusOnlyMode,
            maxScale = 5f,
            verticalDocumentMode = true,
            onSingleTap = { onToggleChrome() },
            modifier = Modifier.fillMaxSize()
        ) { zoomCamera ->
            val zoomScale = zoomCamera.scale
            LazyColumn(
                state = listState,
                userScrollEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
                modifier = Modifier.fillMaxSize().onSizeChanged { viewportSize = it },
                contentPadding = PaddingValues(0.dp),
                verticalArrangement = Arrangement.spacedBy(if (showPageGap) 8.dp else 0.dp)
            ) {
                items(pageCount) { page ->
                    val pdfPage = sharedPdfPdfPageIndexAt(virtualLayout, page)
                    if (pdfPage == null) {
                        SharedMobilePdfBlankPageSurface(
                            insertion = (virtualLayout[page] as SharedPdfVirtualPage.BlankPage).insertion,
                            displayIndex = page,
                            displayPageCount = pageCount,
                            activeTheme = activeTheme,
                            showPageNumberOverlay = showPageNumberOverlay,
                            richTextController = richTextController,
                            isRichTextEditingEnabled = isRichTextEditingEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val render = rememberSharedMobilePdfPageRender(book, pdfPage, zoomScale, pdfPassword)
                        SharedMobilePdfPageSurface(
                        book = book,
                        pdfPassword = pdfPassword,
                        pageIndex = pdfPage,
                        pageCount = pageCount,
                        overlayPageNumber = page + 1,
                        overlayPageCount = pageCount,
                        pageRender = render,
                        zoomCamera = zoomCamera,
                        activeTheme = activeTheme,
                        textureAlpha = textureAlpha,
                        showPageNumberOverlay = showPageNumberOverlay,
                        searchResults = searchResults.filter { it.pageIndex == pdfPage },
                        focusedSearchResult = searchResults.getOrNull(state.activeSearchResultIndex)
                            ?.takeIf { it.pageIndex == pdfPage },
                        searchHighlightMode = state.searchHighlightMode,
                        ttsHighlights = if (ttsPageIndex == pdfPage && !zoomCamera.isZoomed()) ttsHighlightBounds else emptyList(),
                        annotations = state.annotations.filter { it.pageIndex == pdfPage },
                        activeStroke = if (page == state.pageIndex) activeStroke else emptyList(),
                        isStylusOnlyMode = isStylusOnlyMode,
                        selectedTool = state.selectedTool,
                        selectedColorArgb = state.selectedColorArgb,
                        strokeWidth = state.strokeWidth,
                        textDraft = textDraft,
                        onTextDraftChange = onTextDraftChange,
                        onTextPageTap = onTextPageTap,
                        richTextController = richTextController,
                        isRichTextEditingEnabled = isRichTextEditingEnabled,
                        displayPageIndex = page,
                        onExternalLink = onExternalLink,
                        onInternalLink = onInternalLink,
                        onExistingHighlightTap = onExistingHighlightTap,
                        onHighlight = onHighlight,
                        onReadAloud = onReadAloud,
                        onCanvasSizeChanged = onCanvasSizeChanged,
                        onFinishInkStroke = onFinishInkStroke,
                            showAllTextHighlights = showAllTextHighlights,
                            onAllTextHighlightsLoadingChange = onAllTextHighlightsLoadingChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        if (autoScrollMusicianMode) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 100.dp)
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.4f)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .pointerInput(book.id) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var longPressTriggered = false
                            val holdJob = scope.launch {
                                val start = TimeSource.Monotonic.markNow()
                                while (isActive) {
                                    val elapsedMillis = start.elapsedNow().inWholeMilliseconds
                                    musicianLeftHoldProgress =
                                        (elapsedMillis / PdfMusicianHoldDurationMillis.toFloat()).coerceIn(0f, 1f)
                                    if (elapsedMillis >= PdfMusicianHoldDurationMillis) {
                                        musicianLeftHoldProgress = 0f
                                        longPressTriggered = true
                                        val plan = planPdfMusicianGesture(isRightRegion = false, isLongPress = true)
                                        onAutoScrollInteraction(plan.pauseMillis)
                                        listState.scrollToItem(0)
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            musicianLeftHoldProgress = 0f
                            if (!longPressTriggered && up != null) {
                                up.consume()
                                val plan = planPdfMusicianGesture(isRightRegion = false, isLongPress = false)
                                onAutoScrollInteraction(plan.pauseMillis)
                                scope.launch { listState.scrollBy(viewportSize.height * plan.relativeViewportDelta) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (musicianLeftHoldProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { musicianLeftHoldProgress },
                        modifier = Modifier.size(48.dp).alpha(0.6f),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                    Icon(Icons.Default.ArrowUpward, null, Modifier.size(24.dp).alpha(0.6f))
                }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 100.dp)
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.4f)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .pointerInput(book.id, pageCount) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var longPressTriggered = false
                            val holdJob = scope.launch {
                                val start = TimeSource.Monotonic.markNow()
                                while (isActive) {
                                    val elapsedMillis = start.elapsedNow().inWholeMilliseconds
                                    musicianRightHoldProgress =
                                        (elapsedMillis / PdfMusicianHoldDurationMillis.toFloat()).coerceIn(0f, 1f)
                                    if (elapsedMillis >= PdfMusicianHoldDurationMillis) {
                                        musicianRightHoldProgress = 0f
                                        longPressTriggered = true
                                        val plan = planPdfMusicianGesture(isRightRegion = true, isLongPress = true)
                                        onAutoScrollInteraction(plan.pauseMillis)
                                        listState.scrollToItem(pageCount - 1, Int.MAX_VALUE)
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            musicianRightHoldProgress = 0f
                            if (!longPressTriggered && up != null) {
                                up.consume()
                                val plan = planPdfMusicianGesture(isRightRegion = true, isLongPress = false)
                                onAutoScrollInteraction(plan.pauseMillis)
                                scope.launch { listState.scrollBy(viewportSize.height * plan.relativeViewportDelta) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (musicianRightHoldProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { musicianRightHoldProgress },
                        modifier = Modifier.size(48.dp).alpha(0.6f),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                    Icon(Icons.Default.ArrowDownward, null, Modifier.size(24.dp).alpha(0.6f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobilePdfPaginatedPages(
    book: BookItem,
    pdfPassword: String?,
    state: SharedPdfReaderState,
    activeTheme: ReaderTheme,
    textureAlpha: Float,
    pageCount: Int,
    virtualLayout: List<SharedPdfVirtualPage>,
    navigationRequestPage: Int,
    navigationRequestToken: Int,
    animateNavigation: Boolean,
    useTwoPageSpread: Boolean,
    firstPageStandaloneInSpread: Boolean,
    rightToLeftPagination: Boolean,
    showPageNumberOverlay: Boolean,
    searchResults: List<SharedPdfSearchResult>,
    ttsPageIndex: Int?,
    ttsHighlightBounds: List<PdfPageBounds>,
    activeStroke: List<PdfPagePoint>,
    isStylusOnlyMode: Boolean = false,
    tapToTurnPages: Boolean,
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (Int, com.aryan.reader.shared.pdf.PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int, Int) -> Unit,
    userScrollEnabled: Boolean,
    isScrollLocked: Boolean,
    zoomCamera: PdfZoomCamera,
    onZoomCameraChanged: (PdfZoomCamera) -> Unit,
    textDraft: SharedPdfTextDraft?,
    onTextDraftChange: (SharedPdfTextDraft) -> Unit,
    onTextPageTap: (SharedPdfAnnotation?) -> Unit,
    richTextController: SharedPdfRichTextController?,
    isRichTextEditingEnabled: Boolean,
    onPageChanged: (Int) -> Unit,
    onManualPageTurnStarted: () -> Unit,
    onToggleChrome: () -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int, Boolean) -> Unit,
    showAllTextHighlights: Boolean = false,
    onAllTextHighlightsLoadingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var paginationViewportSize by remember(book.id) { mutableStateOf(IntSize.Zero) }
    var pagerWindowRect by remember(book.id) { mutableStateOf<Rect?>(null) }
    val pageSurfaceWindowRects = remember(book.id) { mutableStateMapOf<Int, Rect>() }
    var textDrag by remember(book.id) { mutableStateOf<SharedPdfTextDragState?>(null) }
    val spreadStarts = remember(pageCount, useTwoPageSpread, firstPageStandaloneInSpread) {
        sharedMobilePdfSpreadStarts(pageCount, useTwoPageSpread, firstPageStandaloneInSpread)
    }
    fun pagerIndexForPage(pageIndex: Int): Int {
        val target = pageIndex.coerceIn(0, pageCount - 1)
        return spreadStarts.indexOfLast { it <= target }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = pagerIndexForPage(state.pageIndex),
        pageCount = { spreadStarts.size.coerceAtLeast(1) }
    )
    val isPagerDragged by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isPagerDragged) {
        if (isPagerDragged) onManualPageTurnStarted()
    }
    LaunchedEffect(navigationRequestToken, spreadStarts) {
        val requestedPage = if (navigationRequestToken == 0) state.pageIndex else navigationRequestPage
        val target = pagerIndexForPage(requestedPage)
        if (pagerState.currentPage != target) {
            if (animateNavigation) pagerState.animateScrollToPage(target) else pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, spreadStarts) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                onPageChanged(spreadStarts.getOrElse(settledPage) { 0 })
            }
    }
    var previousSettledPage by remember(book.id) { mutableStateOf(pagerState.settledPage) }
    LaunchedEffect(pagerState.settledPage, isScrollLocked) {
        if (pagerState.settledPage != previousSettledPage) {
            if (!isScrollLocked) onZoomCameraChanged(PdfZoomCamera())
            previousSettledPage = pagerState.settledPage
        }
    }
    val dragEdgeThresholdPx = with(LocalDensity.current) { 60.dp.toPx() }
    val textDropPaddingPx = with(LocalDensity.current) { 14.dp.toPx() }
    fun startTextDrag(topLeft: Offset, canvasSize: IntSize, displayPage: Int) {
        val draft = textDraft ?: return
        textDrag = SharedPdfTextDragState(
            draftId = draft.id,
            originDisplayPage = displayPage,
            originPdfPage = draft.pageIndex,
            relWidth = draft.bounds.right - draft.bounds.left,
            relHeight = draft.bounds.bottom - draft.bounds.top,
            dragOffset = topLeft,
            originCanvasSize = canvasSize,
            dragWidthPx = (draft.bounds.right - draft.bounds.left) * canvasSize.width * zoomCamera.scale,
            dragHeightPx = (draft.bounds.bottom - draft.bounds.top) * canvasSize.height * zoomCamera.scale,
            dragCameraScale = zoomCamera.scale
        )
    }
    fun updateTextDrag(delta: Offset) {
        val drag = textDrag ?: return
        val pagerRect = pagerWindowRect
        var updated = drag.copy(dragOffset = drag.dragOffset + delta)
        if (pagerRect != null) {
            updated = updated.copy(
                dragOffset = Offset(
                    updated.dragOffset.x.coerceIn(pagerRect.left - drag.dragWidthPx * 0.25f, pagerRect.right - drag.dragWidthPx * 0.25f),
                    updated.dragOffset.y.coerceIn(pagerRect.top - drag.dragHeightPx * 0.5f, pagerRect.bottom - drag.dragHeightPx * 0.5f)
                )
            )
        }
        textDrag = updated
        if (pagerRect == null) return
        val midX = updated.dragOffset.x + updated.dragWidthPx * 0.25f
        val targetPager = when {
            midX <= pagerRect.left + dragEdgeThresholdPx -> (pagerState.currentPage - 1).coerceAtLeast(0)
            midX >= pagerRect.right - dragEdgeThresholdPx -> (pagerState.currentPage + 1).coerceAtMost(spreadStarts.lastIndex)
            else -> return
        }
        if (targetPager != pagerState.currentPage) {
            scope.launch { pagerState.animateScrollToPage(targetPager) }
        }
    }
    fun endTextDrag() {
        val drag = textDrag ?: return
        textDrag = null
        val draft = textDraft?.takeIf { it.id == drag.draftId } ?: return
        val pagerRect = pagerWindowRect ?: return
        val spreadStart = spreadStarts.getOrElse(pagerState.currentPage) { 0 }
        val currentSpreadPages = if (useTwoPageSpread && spreadStart + 1 < pageCount) listOf(spreadStart, spreadStart + 1) else listOf(spreadStart)
        val targetDisplay = if (drag.originDisplayPage in currentSpreadPages) drag.originDisplayPage else currentSpreadPages.first()
        val targetRect = pageSurfaceWindowRects[targetDisplay] ?: return
        val bounds = sharedPdfTextDropBounds(
            dropTopLeft = drag.dragOffset,
            targetRect = targetRect,
            relWidth = drag.relWidth,
            relHeight = drag.relHeight,
            paddingPx = textDropPaddingPx
        )
        val targetPdfPage = sharedPdfNearestPdfPageIndex(virtualLayout, targetDisplay) ?: drag.originPdfPage
        val targetCanvasHeight = (targetRect.height / zoomCamera.scale.coerceAtLeast(0.1f)).coerceAtLeast(1f)
        val fontScale = if (drag.originCanvasSize.height > 0) targetCanvasHeight / drag.originCanvasSize.height else 1f
        val scaledNorm = (draft.style.sharedPdfTextPageRelativeFontSize() * fontScale).coerceIn(0.012f, 0.12f)
        onTextDraftChange(
            draft.copy(
                pageIndex = targetPdfPage,
                bounds = bounds,
                isManuallySized = true,
                style = draft.style.copy(pageRelativeFontSize = scaledNorm)
            )
        )
    }
    fun cancelTextDrag() {
        textDrag = null
    }
    Box(
        modifier = modifier.onGloballyPositioned { pagerWindowRect = it.boundsInWindow() }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE && !zoomCamera.isZoomed(),
            reverseLayout = rightToLeftPagination,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { pagerPage ->
        val spreadPages = remember(pagerPage, spreadStarts, pageCount, useTwoPageSpread, firstPageStandaloneInSpread) {
            val start = spreadStarts.getOrElse(pagerPage) { 0 }
            when {
                !useTwoPageSpread -> listOf(start)
                firstPageStandaloneInSpread && start == 0 -> listOf(0)
                else -> listOf(start, start + 1).filter { it in 0 until pageCount }
            }
        }
        SharedMobilePdfZoomViewport(
            camera = zoomCamera,
            onCameraChanged = onZoomCameraChanged,
            zoomEnabled = userScrollEnabled && state.selectedTool == PdfInkTool.NONE,
            tapGesturesEnabled = state.selectedTool == PdfInkTool.NONE || state.selectedTool == PdfInkTool.TEXT || isStylusOnlyMode,
            maxScale = 4f,
            onSingleTap = { offset ->
                val viewportWidthForTap = paginationViewportSize.width.toFloat()
                val edge = viewportWidthForTap * 0.25f
                when {
                    tapToTurnPages && !zoomCamera.isZoomed() && offset.x < edge ->
                        pdfPaginationEdgeTarget(
                            currentPage = pagerPage,
                            lastPage = spreadStarts.lastIndex,
                            tappedLeftEdge = true,
                            rightToLeft = rightToLeftPagination,
                        )?.let { target ->
                            onManualPageTurnStarted()
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    tapToTurnPages && !zoomCamera.isZoomed() && offset.x > viewportWidthForTap - edge ->
                        pdfPaginationEdgeTarget(
                            currentPage = pagerPage,
                            lastPage = spreadStarts.lastIndex,
                            tappedLeftEdge = false,
                            rightToLeft = rightToLeftPagination,
                        )?.let { target ->
                            onManualPageTurnStarted()
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                    else -> onToggleChrome()
                }
            },
            modifier = Modifier.fillMaxSize().onSizeChanged { paginationViewportSize = it }
        ) { activeZoomCamera ->
          val zoomScale = activeZoomCamera.scale
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val pageGap = if (spreadPages.size > 1) 8.dp else 0.dp
                val viewportHeight = maxHeight
                val slotWidth = (maxWidth - pageGap * (spreadPages.size - 1)) / spreadPages.size
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(pageGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    spreadPages.forEach { displayPage ->
                        val pdfPage = sharedPdfPdfPageIndexAt(virtualLayout, displayPage)
                        if (pdfPage == null) {
                            val insertion = (virtualLayout[displayPage] as SharedPdfVirtualPage.BlankPage).insertion
                            val aspectRatio = (insertion.widthPx / insertion.heightPx.coerceAtLeast(1f)).coerceIn(0.1f, 10f)
                            val widthLimited = slotWidth.value / viewportHeight.value <= aspectRatio
                            val fittedWidth = if (widthLimited) slotWidth else viewportHeight * aspectRatio
                            val fittedHeight = fittedWidth / aspectRatio
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                SharedMobilePdfBlankPageSurface(
                                    insertion = insertion,
                                    displayIndex = displayPage,
                                    displayPageCount = pageCount,
                                    activeTheme = activeTheme,
                                    showPageNumberOverlay = showPageNumberOverlay,
                                    richTextController = richTextController,
                                    isRichTextEditingEnabled = isRichTextEditingEnabled,
                                    modifier = Modifier.size(fittedWidth, fittedHeight)
                                )
                            }
                        } else {
                            val render = rememberSharedMobilePdfPageRender(book, pdfPage, zoomScale, pdfPassword)
                            val aspectRatio = render.aspectRatio.coerceIn(0.1f, 10f)
                            val widthLimited = slotWidth.value / viewportHeight.value <= aspectRatio
                            val fittedWidth = if (widthLimited) slotWidth else viewportHeight * aspectRatio
                            val fittedHeight = fittedWidth / aspectRatio
                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                SharedMobilePdfPageSurface(
                                    book = book,
                                    pdfPassword = pdfPassword,
                                    pageIndex = pdfPage,
                                    pageCount = pageCount,
                                    overlayPageNumber = displayPage + 1,
                                    overlayPageCount = pageCount,
                                    pageRender = render,
                                    zoomCamera = activeZoomCamera,
                                    activeTheme = activeTheme,
                                    textureAlpha = textureAlpha,
                                    showPageNumberOverlay = showPageNumberOverlay,
                                    searchResults = searchResults.filter { it.pageIndex == pdfPage },
                                    focusedSearchResult = searchResults.getOrNull(state.activeSearchResultIndex)
                                        ?.takeIf { it.pageIndex == pdfPage },
                                    searchHighlightMode = state.searchHighlightMode,
                                    ttsHighlights = if (ttsPageIndex == pdfPage && !activeZoomCamera.isZoomed()) ttsHighlightBounds else emptyList(),
                                    annotations = state.annotations.filter { it.pageIndex == pdfPage },
                                    activeStroke = if (displayPage == state.pageIndex) activeStroke else emptyList(),
                                    isStylusOnlyMode = isStylusOnlyMode,
                                    selectedTool = state.selectedTool,
                                    selectedColorArgb = state.selectedColorArgb,
                                    strokeWidth = state.strokeWidth,
                                    textDraft = textDraft,
                                    onTextDraftChange = onTextDraftChange,
                                    onTextPageTap = onTextPageTap,
                                    richTextController = richTextController,
                                    isRichTextEditingEnabled = isRichTextEditingEnabled,
                                    displayPageIndex = displayPage,
                                    onTextDragStart = ::startTextDrag,
                                    onTextDrag = ::updateTextDrag,
                                    onTextDragEnd = ::endTextDrag,
                                    onTextDragCancel = ::cancelTextDrag,
                                    isTextDraftDragging = textDrag?.draftId == textDraft?.id,
                                    containerWindowRect = pagerWindowRect,
                                    onSurfaceWindowRectChanged = { rect -> pageSurfaceWindowRects[displayPage] = rect },
                                    onExternalLink = onExternalLink,
                                    onInternalLink = onInternalLink,
                                    onExistingHighlightTap = onExistingHighlightTap,
                                    onHighlight = onHighlight,
                                    onReadAloud = onReadAloud,
                                    onCanvasSizeChanged = onCanvasSizeChanged,
                                    onFinishInkStroke = onFinishInkStroke,
                                    showAllTextHighlights = showAllTextHighlights,
                                    onAllTextHighlightsLoadingChange = onAllTextHighlightsLoadingChange,
                                    modifier = Modifier.size(fittedWidth, fittedHeight)
                                )
                            }
                        }
          }
        }
    }
}
            }
        }
            textDrag?.let { drag ->
                val draft = textDraft?.takeIf { it.id == drag.draftId } ?: return@let
                val leftInPager = (drag.dragOffset.x - (pagerWindowRect?.left ?: 0f)).roundToInt()
                val topInPager = (drag.dragOffset.y - (pagerWindowRect?.top ?: 0f)).roundToInt()
                val widthDp = with(LocalDensity.current) { drag.dragWidthPx.toDp() }
                val heightDp = with(LocalDensity.current) { drag.dragHeightPx.toDp() }
                Box(
                    modifier = Modifier
                        .zIndex(10f)
                        .offset { IntOffset(leftInPager, topInPager) }
                        .graphicsLayer {
                            scaleX = drag.dragCameraScale
                            scaleY = drag.dragCameraScale
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .size(widthDp, heightDp)
                        .background(Color(0xFFFFFFFF), RoundedCornerShape(2.dp))
                        .border(1.dp, Color(0xFF444444), RoundedCornerShape(2.dp))
                ) {
                    Box(Modifier.padding(6.dp)) {
                        Text(
                            text = draft.text,
                            color = Color(0xFF111111),
                            fontSize = with(LocalDensity.current) { draft.style.sharedPdfTextFontSizePx(drag.originCanvasSize).toSp() },
                            fontFamily = sharedPdfFontFamily(draft.style.fontName ?: draft.style.fontPath),
                            textAlign = TextAlign.Start
                        )
                }
            }
        }
    }
}

private fun sharedMobilePdfSpreadStarts(
    pageCount: Int,
    useTwoPageSpread: Boolean,
    firstPageStandalone: Boolean
): List<Int> {
    return PdfSpreadLayout.spreadStartPageIndices(
        pageCount = pageCount,
        settings = ReaderSettings(
            pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
            pdfFirstPageStandaloneInSpread = firstPageStandalone
        )
    ).ifEmpty { listOf(0) }
}

private fun sharedMobilePdfPageLabel(
    pageIndex: Int,
    pageCount: Int,
    useTwoPageSpread: Boolean,
    firstPageStandalone: Boolean
): String {
    return PdfSpreadLayout.pageRangeLabel(
        pageIndex = pageIndex,
        pageCount = pageCount,
        settings = ReaderSettings(
            pageSpreadMode = if (useTwoPageSpread) ReaderPageSpreadMode.TWO_PAGE else ReaderPageSpreadMode.SINGLE,
            pdfFirstPageStandaloneInSpread = firstPageStandalone
        )
    )
}

/**
 * Renders the highlight-all overlay, mirroring Android's PdfHighlightsLayer:
 * merged text-line rects, filled over the text; in light mode a full-page
 * scrim dims everything except the text (punched out with BlendMode.Clear).
 */
@Composable
private fun SharedMobilePdfAllTextHighlightOverlay(
    bounds: List<PdfPageBounds>,
    isDarkMode: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    if (bounds.isEmpty()) return
    val merged = remember(bounds) { sharedPdfMergeRectsIntoLines(bounds) }
    val colors = remember(isDarkMode, primaryColor) {
        sharedPdfHighlightAllColors(isDarkMode, primaryColor)
    }
    Canvas(modifier) {
        val canvasSize = size
        if (colors.scrimColor.alpha > 0f) {
            with(drawContext.canvas) {
                saveLayer(Rect(Offset.Zero, canvasSize), Paint())
                drawRect(colors.scrimColor, Offset.Zero, size)
                merged.forEach { item ->
                    val left = item.left * size.width
                    val top = item.top * size.height
                    val right = item.right * size.width
                    val bottom = item.bottom * size.height
                    if (right > left && bottom > top) {
                        drawRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            blendMode = BlendMode.Clear,
                        )
                    }
                }
                restore()
            }
        }
        merged.forEach { item ->
            val left = item.left * size.width
            val top = item.top * size.height
            val right = item.right * size.width
            val bottom = item.bottom * size.height
            if (right > left && bottom > top) {
                drawRect(colors.rectColor, Offset(left, top), Size(right - left, bottom - top))
            }
        }
    }
}

@Composable
private fun SharedMobilePdfSearchHighlightOverlay(
    backgroundBounds: List<PdfPageBounds>,
    focusedBounds: List<PdfPageBounds>,
    modifier: Modifier = Modifier
) {
    if (backgroundBounds.isEmpty() && focusedBounds.isEmpty()) return
    val focusedStrokeWidth = with(LocalDensity.current) { 3.dp.toPx() }
    Canvas(modifier) {
        fun drawSearchBounds(
            bounds: List<PdfPageBounds>,
            padding: Float,
            fill: Color,
            border: Color? = null,
        ) {
            bounds.forEach { item ->
                val left = item.left * size.width
                val top = item.top * size.height
                val right = item.right * size.width
                val bottom = item.bottom * size.height
                if (right > left && bottom > top) {
                    val paddedLeft = (left - padding).coerceAtLeast(0f)
                    val paddedTop = (top - padding).coerceAtLeast(0f)
                    val paddedRight = (right + padding).coerceAtMost(size.width)
                    val paddedBottom = (bottom + padding).coerceAtMost(size.height)
                    val paddedSize = androidx.compose.ui.geometry.Size(
                        paddedRight - paddedLeft,
                        paddedBottom - paddedTop,
                    )
                    drawRect(fill, Offset(paddedLeft, paddedTop), paddedSize)
                    if (border != null) {
                        drawRect(
                            color = border,
                            topLeft = Offset(paddedLeft, paddedTop),
                            size = paddedSize,
                            style = Stroke(width = focusedStrokeWidth),
                        )
                    }
                }
            }
        }
        drawSearchBounds(backgroundBounds, 3f, Color(0x66FFEB3B))
        drawSearchBounds(
            bounds = focusedBounds,
            padding = 5f,
            fill = Color(0x66FF6D00),
            border = Color(0xE6FF6D00),
        )
    }
}

@Composable
private fun SharedMobilePdfTtsHighlightOverlay(
    bounds: List<PdfPageBounds>,
    modifier: Modifier = Modifier
) {
    if (bounds.isEmpty()) return
    Canvas(modifier) {
        bounds.forEach { item ->
            val left = item.left * size.width
            val top = item.top * size.height
            val right = item.right * size.width
            val bottom = item.bottom * size.height
            if (right > left && bottom > top) {
                drawRect(
                    color = Color(0x66FFCC33),
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfJumpHistoryBar(
    history: SharedPdfJumpHistory,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack, enabled = history.backPage != null, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous jump", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(history.backPage?.let { "Page ${it + 1}" }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Close, contentDescription = "Clear page history", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear", maxLines = 1)
            }
            TextButton(onClick = onForward, enabled = history.forwardPage != null, modifier = Modifier.weight(1f)) {
                Text(history.forwardPage?.let { "Page ${it + 1}" }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next jump", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun SharedMobilePdfAutoScrollControls(
    isPlaying: Boolean,
    isTemporarilyPaused: Boolean,
    profile: PdfAutoScrollProfile,
    isLocalMode: Boolean,
    isMusicianMode: Boolean,
    useSlider: Boolean,
    isCollapsed: Boolean,
    onPlayPause: () -> Unit,
    onProfileChange: (PdfAutoScrollProfile) -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onMusicianModeChange: (Boolean) -> Unit,
    onUseSliderChange: (Boolean) -> Unit,
    onCollapsedChange: (Boolean) -> Unit,
    onScrollToTop: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sanitized = profile.sanitized()
    var showModeMenu by remember { mutableStateOf(false) }
    var showMinMenu by remember { mutableStateOf(false) }
    var showMaxMenu by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.1f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
    Surface(
        modifier = modifier.widthIn(max = 400.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    ) {
        if (isCollapsed) {
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { onCollapsedChange(false) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Expand auto scroll")
                }
                Box(contentAlignment = Alignment.Center) {
                    FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause auto scroll" else "Resume auto scroll",
                        )
                    }
                    if (isPlaying && isTemporarilyPaused) {
                        CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.dp)
                    }
                }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        TextButton(onClick = { showModeMenu = true }) {
                            Text(if (isLocalMode) "Local speed" else "Global speed")
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select speed profile")
                        }
                        DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                            DropdownMenuItem(
                                text = { Column { Text("Global speed"); Text("Applies to all files", style = MaterialTheme.typography.bodySmall) } },
                                onClick = { onLocalModeChange(false); showModeMenu = false },
                                trailingIcon = { if (!isLocalMode) Icon(Icons.Default.Check, null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Column { Text("Local speed"); Text("Saved for this file", style = MaterialTheme.typography.bodySmall) } },
                                onClick = { onLocalModeChange(true); showModeMenu = false },
                                trailingIcon = { if (isLocalMode) Icon(Icons.Default.Check, null) },
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onScrollToTop, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Scroll to top", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onMusicianModeChange(!isMusicianMode) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            SharedReaderIcons.MusicNote,
                            contentDescription = if (isMusicianMode) "Disable musician mode" else "Enable musician mode",
                            tint = if (isMusicianMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(onClick = { onUseSliderChange(!useSlider) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Swap speed controls", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onCollapsedChange(true) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Collapse auto scroll", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Stop auto scroll", tint = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause auto scroll" else "Resume auto scroll")
                        }
                        if (isPlaying && isTemporarilyPaused) {
                            CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 3.dp)
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box {
                                TextButton(onClick = { showMinMenu = true }) { Text("Min ${sanitized.minSpeed}×") }
                                DropdownMenu(expanded = showMinMenu, onDismissRequest = { showMinMenu = false }) {
                                    speedOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("${value}×") },
                                            onClick = { onProfileChange(sanitized.withMinSpeed(value)); showMinMenu = false },
                                        )
                                    }
                                }
                            }
                            Box {
                                TextButton(onClick = { showMaxMenu = true }) { Text("Max ${sanitized.maxSpeed}×") }
                                DropdownMenu(expanded = showMaxMenu, onDismissRequest = { showMaxMenu = false }) {
                                    speedOptions.forEach { value ->
                                        DropdownMenuItem(
                                            text = { Text("${value}×") },
                                            onClick = { onProfileChange(sanitized.withMaxSpeed(value)); showMaxMenu = false },
                                        )
                                    }
                                }
                            }
                        }
                        if (useSlider) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${sanitized.speed}×", modifier = Modifier.width(48.dp))
                                Slider(
                                    value = sanitized.speed,
                                    onValueChange = { onProfileChange(sanitized.copy(speed = (it * 10f).roundToInt() / 10f).sanitized()) },
                                    valueRange = sanitized.minSpeed..sanitized.maxSpeed.coerceAtLeast(sanitized.minSpeed + 0.1f),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconButton(onClick = {
                                        onProfileChange(sanitized.copy(speed = (sanitized.speed - 0.1f).coerceAtLeast(sanitized.minSpeed)))
                                    }) { Icon(Icons.Default.Remove, "Slower") }
                                    Text("${sanitized.speed}×", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = {
                                        onProfileChange(sanitized.copy(speed = (sanitized.speed + 0.1f).coerceAtMost(sanitized.maxSpeed)))
                                    }) { Icon(Icons.Default.Add, "Faster") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SharedPdfTtsOverlaySize { LARGE, MEDIUM, SMALL }

@Composable
private fun SharedMobilePdfTtsControls(
    tts: SharedMobileEpubLocalTts,
    pageIndex: Int,
    pageCount: Int,
    chunkIndex: Int,
    chunkCount: Int,
    onPauseResume: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onLocate: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    var overlaySize by remember { mutableStateOf(SharedPdfTtsOverlaySize.LARGE) }
    var rate by remember(tts.speechRate) { mutableStateOf(tts.speechRate) }
    var pitch by remember(tts.speechPitch) { mutableStateOf(tts.speechPitch) }
    val isSpeaking = tts.state == SharedMobileEpubLocalTtsState.SPEAKING
    val isPreparing = chunkIndex < 0 || chunkCount <= 0
    val canPrevious = !isPreparing && (chunkIndex > 0 || pageIndex > 0)
    val canNext = !isPreparing && (chunkIndex < chunkCount - 1 || pageIndex < pageCount - 1)
    val status = if (isPreparing) "Preparing" else "Part ${chunkIndex + 1} of $chunkCount"

    Surface(
        modifier = modifier
            .widthIn(max = if (overlaySize == SharedPdfTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
    ) {
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "PdfTtsOverlayUnified"
        ) { size ->
            when (size) {
                SharedPdfTtsOverlaySize.SMALL -> Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.LARGE }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Expand TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.MEDIUM }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Expand TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PdfTtsPlayButton(isSpeaking, isPreparing, onPauseResume, 36.dp, 20.dp)
                }

                SharedPdfTtsOverlaySize.MEDIUM -> Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onLocate)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = onPreviousPage, enabled = canPrevious, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous reading part")
                    }
                    PdfTtsPlayButton(isSpeaking, isPreparing, onPauseResume, 48.dp, 22.dp)
                    IconButton(onClick = onNextPage, enabled = canNext, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.SkipNext, "Next reading part")
                    }
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.LARGE }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Expand TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.SMALL }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowRight, "Collapse TTS player", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                SharedPdfTtsOverlaySize.LARGE -> Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text("Device native", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onLocate, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PushPin, "Locate current part", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.MEDIUM }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.KeyboardArrowDown, "Collapse TTS player", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { overlaySize = SharedPdfTtsOverlaySize.SMALL }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.KeyboardArrowRight, "Collapse TTS player", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Stop reading", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Page ${pageIndex + 1}", style = MaterialTheme.typography.labelLarge)
                            Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPreviousPage, enabled = canPrevious, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Previous reading part")
                        }
                        PdfTtsPlayButton(isSpeaking, isPreparing, onPauseResume, 56.dp, 28.dp)
                        IconButton(onClick = onNextPage, enabled = canNext, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.SkipNext, "Next reading part")
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfTtsParameterSlider("Speed", rate, 0.5f..3f) {
                                rate = it
                                tts.setSpeechParameters(rate, pitch)
                            }
                            PdfTtsParameterSlider("Pitch", pitch, 0.5f..2f) {
                                pitch = it
                                tts.setSpeechParameters(rate, pitch)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfTtsPlayButton(
    isSpeaking: Boolean,
    isPreparing: Boolean,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp
) {
    Box(Modifier.size(buttonSize), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = !isPreparing,
            modifier = Modifier.size(buttonSize.coerceAtMost(56.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(if (isSpeaking) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", modifier = Modifier.size(iconSize))
        }
        if (isPreparing) CircularProgressIndicator(Modifier.size(buttonSize), strokeWidth = 2.dp)
    }
}

@Composable
private fun PdfTtsParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label ${((value * 10).roundToInt() / 10f)}×", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.height(20.dp))
    }
}

private fun List<PdfPageBounds>.centerYFraction(): Float {
    if (isEmpty()) return 0.5f
    return ((minOf { it.top } + maxOf { it.bottom }) / 2f).coerceIn(0f, 1f)
}

@Composable
private fun SharedMobilePdfPageSlider(
    pageIndex: Int,
    pageCount: Int,
    onPageChange: (Int) -> Unit,
    onScrubPreview: (Int?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sliderValue by remember(pageCount) { mutableStateOf(pageIndex.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(pageIndex, isScrubbing) {
        if (!isScrubbing) sliderValue = pageIndex.toFloat()
    }
    DisposableEffect(Unit) {
        onDispose { scrubJob?.cancel() }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    scrubJob?.cancel()
                    val target = pageIndex - 1
                    onPageChange(target.coerceIn(0, pageCount - 1))
                    onScrubPreview(null)
                },
                enabled = pageCount > 1 && pageIndex > 0
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateBefore,
                    contentDescription = "Previous page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (pageIndex > 0) 0.9f else 0.32f)
                )
            }
            Slider(
                value = if (pageCount > 1) sliderValue else 0f,
                onValueChange = { next ->
                    isScrubbing = true
                    sliderValue = next
                    onScrubPreview(next.roundToInt().coerceIn(0, pageCount - 1))
                    scrubJob?.cancel()
                    scrubJob = scope.launch {
                        delay(200)
                        onPageChange(next.roundToInt().coerceIn(0, pageCount - 1))
                    }
                },
                onValueChangeFinished = {
                    scrubJob?.cancel()
                    onPageChange(sliderValue.roundToInt().coerceIn(0, pageCount - 1))
                    isScrubbing = false
                    onScrubPreview(null)
                },
                valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                steps = (pageCount - 2).coerceAtLeast(0),
                enabled = pageCount > 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    scrubJob?.cancel()
                    val target = pageIndex + 1
                    onPageChange(target.coerceIn(0, pageCount - 1))
                    onScrubPreview(null)
                },
                enabled = pageCount > 1 && pageIndex < pageCount - 1
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = "Next page",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (pageIndex < pageCount - 1) 0.9f else 0.32f)
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfPageScrubbingOverlay(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                SharedReaderIcons.Slider,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SharedMobilePdfPageSurface(
    book: BookItem,
    pdfPassword: String?,
    pageIndex: Int,
    pageCount: Int,
    pageRender: SharedMobilePdfPageRender,
    zoomCamera: PdfZoomCamera,
    activeTheme: ReaderTheme,
    textureAlpha: Float,
    showPageNumberOverlay: Boolean,
    overlayPageNumber: Int = pageIndex + 1,
    overlayPageCount: Int = pageCount,
    searchResults: List<SharedPdfSearchResult>,
    focusedSearchResult: SharedPdfSearchResult?,
    searchHighlightMode: SearchHighlightMode,
    ttsHighlights: List<PdfPageBounds>,
    annotations: List<SharedPdfAnnotation>,
    activeStroke: List<PdfPagePoint>,
    isStylusOnlyMode: Boolean = false,
    selectedTool: PdfInkTool,
    selectedColorArgb: Int,
    strokeWidth: Float,
    textDraft: SharedPdfTextDraft?,
    onTextDraftChange: (SharedPdfTextDraft) -> Unit,
    onTextPageTap: (SharedPdfAnnotation?) -> Unit,
    richTextController: SharedPdfRichTextController?,
    isRichTextEditingEnabled: Boolean,
    displayPageIndex: Int,
    onTextDragStart: (Offset, IntSize, Int) -> Unit = { _, _, _ -> },
    onTextDrag: (Offset) -> Unit = {},
    onTextDragEnd: () -> Unit = {},
    onTextDragCancel: () -> Unit = {},
    isTextDraftDragging: Boolean = false,
    containerWindowRect: Rect? = null,
    onSurfaceWindowRectChanged: (Rect) -> Unit = {},
    onExternalLink: (String) -> Unit,
    onInternalLink: (Int) -> Unit,
    onExistingHighlightTap: (SharedPdfAnnotation) -> Unit,
    onHighlight: (Int, com.aryan.reader.shared.pdf.PdfTextSelectionRange, String, List<PdfPageBounds>, Int, HighlightStyle, Boolean) -> Unit,
    onReadAloud: (Int, Int) -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onFinishInkStroke: (Int, Boolean) -> Unit,
    showAllTextHighlights: Boolean = false,
    onAllTextHighlightsLoadingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var localCanvasSize by remember(pageIndex) { mutableStateOf(IntSize.Zero) }
    var pageSurfaceWindowRect by remember(pageIndex) { mutableStateOf(Rect.Zero) }
    var isEraserOverrideActive by remember(pageIndex) { mutableStateOf(false) }
    var eraserOverridePosition by remember(pageIndex) { mutableStateOf<Offset?>(null) }
    var visiblePageBounds by remember(pageIndex) { mutableStateOf<PdfPageBounds?>(null) }
    val textSession = rememberPdfTextPageSession(book, pageIndex, pdfPassword)
    var allTextHighlightBounds by remember(pageIndex) { mutableStateOf<List<PdfPageBounds>>(emptyList()) }
    LaunchedEffect(showAllTextHighlights, pageIndex, pageRender.bitmap, zoomCamera.scale, textSession) {
        if (zoomCamera.scale > 1f) {
            if (allTextHighlightBounds.isNotEmpty()) allTextHighlightBounds = emptyList()
            return@LaunchedEffect
        }
        if (!showAllTextHighlights) {
            if (allTextHighlightBounds.isNotEmpty()) allTextHighlightBounds = emptyList()
            return@LaunchedEffect
        }
        if (pageRender.bitmap == null) return@LaunchedEffect
        onAllTextHighlightsLoadingChange(true)
        val session = textSession
        val pdfiumBounds = if (session != null && session.pageCharCount > 0) {
            session.rectsForRangeNormalized(0, session.pageCharCount)
        } else {
            emptyList()
        }
        allTextHighlightBounds = if (pdfiumBounds.isNotEmpty()) {
            pdfiumBounds
        } else {
            sharedMobilePdfOcrTextBounds(book, pageIndex, pdfPassword)
        }
        onAllTextHighlightsLoadingChange(false)
    }
    fun searchResultBounds(result: SharedPdfSearchResult): List<PdfPageBounds> {
        return result.boundsList.ifEmpty {
            textSession?.rectsForRangeNormalized(result.matchIndex, result.matchLength).orEmpty()
        }
    }
    val allSearchHighlights = remember(textSession, searchResults, searchHighlightMode) {
        if (searchHighlightMode == SearchHighlightMode.ALL) searchResults.flatMap(::searchResultBounds)
        else emptyList()
    }
    val focusedSearchHighlights = remember(textSession, focusedSearchResult) {
        focusedSearchResult?.let(::searchResultBounds).orEmpty()
    }
    val textureBitmap = sharedMobilePdfTextureBitmap(activeTheme)
    val highResolutionTiles = rememberSharedMobilePdfTileRenders(
        book = book,
        pageIndex = pageIndex,
        pageAspectRatio = pageRender.aspectRatio,
        zoomScale = zoomCamera.scale,
        visibleBounds = visiblePageBounds,
        password = pdfPassword,
    )
    Surface(
        color = sharedMobilePdfPageBackground(activeTheme),
        contentColor = sharedMobilePdfPageTextColor(activeTheme),
        shape = RoundedCornerShape(2.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .aspectRatio(pageRender.aspectRatio)
            .clipToBounds()
            .onSizeChanged {
                localCanvasSize = it
                onCanvasSizeChanged(it)
            }
            .onGloballyPositioned { coordinates ->
                val page = coordinates.boundsInWindow()
                pageSurfaceWindowRect = page
                onSurfaceWindowRectChanged(page)
                val viewport = coordinates.findRootCoordinates().boundsInWindow()
                visiblePageBounds = visiblePdfPageBounds(
                    camera = zoomCamera,
                    transformedPageLeft = page.left,
                    transformedPageTop = page.top,
                    transformedPageRight = page.right,
                    transformedPageBottom = page.bottom,
                    viewportLeft = viewport.left,
                    viewportTop = viewport.top,
                    viewportRight = viewport.right,
                    viewportBottom = viewport.bottom
                )
            }
            .then(
                if (selectedTool == PdfInkTool.NONE) Modifier
                else Modifier.pointerInput(selectedTool, localCanvasSize, pageIndex, isStylusOnlyMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!sharedPdfIsInkDownAllowed(isStylusOnlyMode, down.type)) {
                            return@awaitEachGesture
                        }
                        val eraserOverride = sharedPdfIsEraserOverride(down.type, sharedPdfStylusBarrelPressed(currentEvent))
                        val touchSlop = viewConfiguration.touchSlop
                        var dragStarted = false
                        var committed = false
                        var dragSum = Offset.Zero
                        var lastPoint: Offset? = null
                        if (eraserOverride && localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                            eraserOverridePosition = down.position
                            isEraserOverrideActive = true
                        }
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) {
                                    (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                    return@awaitEachGesture
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                if (change.changedToUp()) {
                                    change.consume()
                                    if (dragStarted) {
                                        onFinishInkStroke(pageIndex, eraserOverride)
                                        committed = true
                                    } else {
                                        (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                    }
                                    return@awaitEachGesture
                                }
                                if (change.isConsumed) continue
                                if (!dragStarted) {
                                    if (change.positionChanged()) {
                                        dragSum += change.positionChange()
                                        if (dragSum.getDistance() > touchSlop) {
                                            dragStarted = true
                                            (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                                            if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                                val startPoint = lastPoint ?: change.position
                                                (activeStroke as? MutableList<PdfPagePoint>)?.add(startPoint.toSharedMobilePdfPoint(localCanvasSize))
                                                if (eraserOverride) eraserOverridePosition = startPoint
                                            }
                                            change.consume()
                                        }
                                    }
                                    lastPoint = change.position
                                } else {
                                    if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                        (activeStroke as? MutableList<PdfPagePoint>)?.add(change.position.toSharedMobilePdfPoint(localCanvasSize))
                                        if (eraserOverride) eraserOverridePosition = change.position
                                    }
                                    change.consume()
                                }
                            }
                        } finally {
                            if (!committed) {
                                (activeStroke as? MutableList<PdfPagePoint>)?.clear()
                            }
                            isEraserOverrideActive = false
                            eraserOverridePosition = null
                        }
                    }
                }
            )
            .then(
                if (selectedTool == PdfInkTool.TEXT) {
                    Modifier.pointerInput(
                        pageIndex,
                        localCanvasSize,
                        annotations,
                        textDraft,
                        richTextController,
                        isRichTextEditingEnabled,
                        displayPageIndex
                    ) {
                        detectTapGestures { offset ->
                            if (localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                                val point = offset.toSharedMobilePdfPoint(localCanvasSize)
                                val hit = annotations.firstOrNull {
                                    it.kind == PdfAnnotationKind.TEXT &&
                                        it.bounds?.containsNormalizedPoint(point.x, point.y) == true
                                }
                                if (hit != null) {
                                    onTextPageTap(hit)
                                    return@detectTapGestures
                                }
                                if (textDraft != null) {
                                    onTextPageTap(null)
                                    return@detectTapGestures
                                }
                                // No box under the tap: place the flowing document cursor
                                // (Android's RichTextLayer tap handling, handled at surface level
                                // so box hit-testing wins on box areas).
                                val controller = richTextController ?: return@detectTapGestures
                                if (!isRichTextEditingEnabled) return@detectTapGestures
                                val marginX = localCanvasSize.width * 0.1f
                                val marginY = localCanvasSize.height * 0.08f
                                val editorWidth = localCanvasSize.width - marginX * 2f
                                val editorHeight = localCanvasSize.height - marginY * 2f
                                val editorLocal = Offset(offset.x - marginX, offset.y - marginY)
                                if (
                                    editorLocal.x >= 0f &&
                                    editorLocal.y >= 0f &&
                                    editorLocal.x <= editorWidth &&
                                    editorLocal.y <= editorHeight
                                ) {
                                    controller.handleTapOnPage(displayPageIndex, editorLocal)
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(Modifier.fillMaxSize()) {
            if (pageRender.bitmap != null) {
                Image(
                    bitmap = pageRender.bitmap,
                    contentDescription = book.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = sharedMobilePdfColorFilter(activeTheme)
                )
                if (highResolutionTiles.isNotEmpty()) {
                    Canvas(Modifier.fillMaxSize()) {
                        highResolutionTiles.forEach { tile ->
                            val bounds = tile.request.normalizedBounds
                            val left = (bounds.left * size.width).roundToInt()
                            val top = (bounds.top * size.height).roundToInt()
                            val right = (bounds.right * size.width).roundToInt()
                            val bottom = (bounds.bottom * size.height).roundToInt()
                            drawImage(
                                image = tile.bitmap,
                                dstOffset = IntOffset(left, top),
                                dstSize = IntSize((right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1)),
                                colorFilter = sharedMobilePdfColorFilter(activeTheme)
                            )
                        }
                    }
                }
            } else {
                SharedMobilePdfPagePlaceholder(
                    book = book,
                    pageIndex = pageIndex,
                    errorMessage = pageRender.errorMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (textureBitmap != null && textureAlpha > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        brush = ShaderBrush(
                            ImageShader(
                                image = textureBitmap,
                                tileModeX = TileMode.Repeated,
                                tileModeY = TileMode.Repeated
                            )
                        ),
                        alpha = textureAlpha.coerceIn(0f, 1f),
                        blendMode = if (activeTheme.isDark || activeTheme.id == "reverse") BlendMode.Screen else BlendMode.Multiply
                    )
                }
            }
            SharedMobilePdfSearchHighlightOverlay(
                backgroundBounds = allSearchHighlights,
                focusedBounds = focusedSearchHighlights,
                modifier = Modifier.fillMaxSize()
            )
            if (showAllTextHighlights && allTextHighlightBounds.isNotEmpty()) {
                SharedMobilePdfAllTextHighlightOverlay(
                    bounds = allTextHighlightBounds,
                    isDarkMode = activeTheme.isDark,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SharedMobilePdfTtsHighlightOverlay(
                bounds = ttsHighlights,
                modifier = Modifier.fillMaxSize()
            )
            if (richTextController != null && localCanvasSize.width > 0 && localCanvasSize.height > 0) {
                SharedPdfRichTextLayer(
                    pageIndex = displayPageIndex,
                    controller = richTextController,
                    pageWidth = localCanvasSize.width.toFloat(),
                    pageHeight = localCanvasSize.height.toFloat(),
                    isTextEditingEnabled = isRichTextEditingEnabled,
                    isDarkMode = activeTheme.isDark,
                    tapHandlingEnabled = false
                )
            }
            SharedPdfAnnotationOverlay(
                annotations = annotations,
                activeStroke = activeStroke,
                canvasSize = localCanvasSize,
                activeTool = if (isEraserOverrideActive) PdfInkTool.ERASER else selectedTool,
                activeStrokeColorArgb = selectedColorArgb,
                activeStrokeWidth = strokeWidth,
                eraserPosition = eraserOverridePosition,
                showEraserIndicator = isEraserOverrideActive
            )
            textDraft?.takeIf { it.pageIndex == pageIndex }?.let { draft ->
                SharedPdfTextBoxEditorOverlay(
                    id = draft.id,
                    text = draft.text,
                    style = draft.style,
                    bounds = draft.bounds,
                    canvasSize = localCanvasSize,
                    onTextChange = { nextText ->
                        onTextDraftChange(draft.withText(nextText, localCanvasSize))
                    },
                    onBoundsChange = { nextBounds ->
                        onTextDraftChange(draft.withBounds(nextBounds))
                    },
                    onGlobalDragStart = {
                        val container = containerWindowRect ?: return@SharedPdfTextBoxEditorOverlay
                        val scale = zoomCamera.scale.coerceAtLeast(0.1f)
                        val boxTopLeftInWindow = Offset(
                            pageSurfaceWindowRect.left + draft.bounds.left * localCanvasSize.width * scale,
                            pageSurfaceWindowRect.top + draft.bounds.top * localCanvasSize.height * scale
                        )
                        onTextDragStart(boxTopLeftInWindow, localCanvasSize, displayPageIndex)
                    },
                    onGlobalDrag = { delta ->
                        onTextDrag(Offset(delta.x * zoomCamera.scale, delta.y * zoomCamera.scale))
                    },
                    onGlobalDragEnd = { onTextDragEnd() },
                    onGlobalDragCancel = { onTextDragCancel() },
                    isDraggingGlobally = isTextDraftDragging,
                    modifier = Modifier.fillMaxSize()
                )
            }
            SharedMobilePdfTextSelectionOverlay(
                book = book,
                pageIndex = pageIndex,
                password = pdfPassword,
                textSession = textSession,
                canvasSize = localCanvasSize,
                selectedTool = selectedTool,
                pageRender = pageRender,
                zoomTiles = highResolutionTiles,
                zoomScale = zoomCamera.scale,
                magnifierColorFilter = sharedMobilePdfColorFilter(activeTheme),
                onExternalLink = onExternalLink,
                onInternalLink = onInternalLink,
                existingHighlights = annotations.filter { it.kind == PdfAnnotationKind.HIGHLIGHT },
                onExistingHighlightTap = onExistingHighlightTap,
                onHighlight = { range, text, bounds, color, style, note -> onHighlight(pageIndex, range, text, bounds, color, style, note) },
                onReadAloud = { charIndex -> onReadAloud(pageIndex, charIndex) },
                modifier = Modifier.fillMaxSize()
            )
            if (showPageNumberOverlay) {
                SharedPdfPageNumberOverlay(
                    pageIndex = overlayPageNumber - 1,
                    pageCount = overlayPageCount,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun SharedMobilePdfBlankPageSurface(
    insertion: SharedPdfBlankPageInsertion,
    displayIndex: Int,
    displayPageCount: Int,
    activeTheme: ReaderTheme,
    showPageNumberOverlay: Boolean,
    richTextController: SharedPdfRichTextController? = null,
    isRichTextEditingEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    var blankCanvasSize by remember(displayIndex) { mutableStateOf(IntSize.Zero) }
    Surface(
        color = sharedMobilePdfPageBackground(activeTheme),
        shape = RoundedCornerShape(2.dp),
        shadowElevation = 4.dp,
        modifier = modifier
            .aspectRatio((insertion.widthPx / insertion.heightPx.coerceAtLeast(1f)).coerceIn(0.1f, 10f))
            .clipToBounds()
            .onSizeChanged { blankCanvasSize = it }
    ) {
        Box(Modifier.fillMaxSize()) {
            if (
                richTextController != null &&
                blankCanvasSize.width > 0 &&
                blankCanvasSize.height > 0
            ) {
                SharedPdfRichTextLayer(
                    pageIndex = displayIndex,
                    controller = richTextController,
                    pageWidth = blankCanvasSize.width.toFloat(),
                    pageHeight = blankCanvasSize.height.toFloat(),
                    isTextEditingEnabled = isRichTextEditingEnabled,
                    isDarkMode = activeTheme.isDark,
                    tapHandlingEnabled = true
                )
            }
            if (showPageNumberOverlay) {
                SharedPdfPageNumberOverlay(
                    pageIndex = displayIndex,
                    pageCount = displayPageCount,
                    modifier = Modifier.fillMaxSize(),
                    isDarkPage = sharedMobilePdfPageTextColor(activeTheme).luminance() < 0.5f
                )
            }
        }
    }
}

@Composable
private fun sharedMobilePdfTextureBitmap(theme: ReaderTheme): ImageBitmap? {
    val resource = when (theme.textureId) {
        "asset:ep_naturalwhite.webp" -> Res.drawable.ep_naturalwhite
        "asset:retina_wood.webp" -> Res.drawable.retina_wood
        "asset:light-veneer.webp" -> Res.drawable.light_veneer
        "asset:grey_wash_wall.webp" -> Res.drawable.grey_wash_wall
        "asset:classy_fabric.webp" -> Res.drawable.classy_fabric
        "asset:retro_intro.webp" -> Res.drawable.retro_intro
        else -> null
    }
    return resource?.let { imageResource(it) }
}

@Composable
private fun SharedMobilePdfPagePlaceholder(
    book: BookItem,
    pageIndex: Int,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val margin = size.width * 0.09f
        val lineStart = margin
        val lineEnd = size.width - margin
        val top = size.height * 0.16f
        val path = Path().apply {
            moveTo(lineStart, top)
            lineTo(lineEnd * 0.72f, top)
        }
        drawPath(
            path = path,
            color = Color(0xFF303030),
            style = Stroke(width = 3f)
        )
        repeat(10) { index ->
            val y = top + 44f + index * 34f
            val end = if (index % 4 == 3) lineEnd * 0.72f else lineEnd
            drawLine(
                color = Color(0xFF9E9E9E),
                start = Offset(lineStart, y),
                end = Offset(end, y),
                strokeWidth = 2f
            )
        }
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset(lineStart, size.height * 0.62f),
            size = androidx.compose.ui.geometry.Size(size.width - margin * 2f, size.height * 0.2f)
        )
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Text(
                text = book.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = errorMessage ?: "Rendering PDF page ${pageIndex + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF616161)
            )
        }
    }
}

private fun Offset.toSharedMobilePdfPoint(size: IntSize): PdfPagePoint {
    return PdfPagePoint(
        x = (x / size.width.toFloat()).coerceIn(0f, 1f),
        y = (y / size.height.toFloat()).coerceIn(0f, 1f),
        timestamp = currentTimestamp()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileUnifiedLibraryScreen(
    state: SharedReaderScreenState,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onUpdateBook: (BookItem) -> Unit,
    onCreateShelf: (String) -> Unit,
    onImportBooks: () -> Unit,
    onAddFolder: () -> Unit,
    onScanFolders: () -> Unit,
    onSyncFolderMetadata: () -> Unit,
    onFolderLocalSyncChange: (SyncedFolder, Boolean) -> Unit,
    onFolderFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit,
    onRemoveFolder: (SyncedFolder) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppTheme: () -> Unit,
    onOpenFonts: () -> Unit,
    catalogContent: @Composable (Modifier) -> Unit,
    initialSection: Int = 0,
    onSectionChange: (Int) -> Unit = {},
    audiobooks: List<SharedAudiobook> = emptyList(),
    audiobookPlayback: SharedAudiobookPlaybackState = SharedAudiobookPlaybackState(),
    onPlayAudiobook: (SharedAudiobook) -> Unit = {},
    onToggleAudiobookPlayback: () -> Unit = {},
    onSeekAudiobook: (Long) -> Unit = {},
    onAudiobookSpeedChange: (Float) -> Unit = {},
    onAudiobookSleepTimer: (Int?) -> Unit = {},
    onStopAudiobookPlayback: () -> Unit = {},
    ttsListenState: SharedBookTtsListenState = SharedBookTtsListenState(),
    ttsProgress: List<SharedBookTtsListeningProgress> = emptyList(),
    ttsChapterTitles: Map<String, List<String>> = emptyMap(),
    onStartTtsListen: (BookItem, SharedTtsListenStartPolicy, Int?) -> Unit = { _, _, _ -> },
    onToggleTtsPlayback: () -> Unit = {},
    onSeekTtsChunk: (Int) -> Unit = {},
    onSeekTtsChapter: (Int) -> Unit = {},
    onTtsSpeedChange: (Float) -> Unit = {},
    onTtsSleepTimer: (Int?) -> Unit = {},
    onStopTtsPlayback: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(MobileUnifiedLibraryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    var section by remember(initialSection) {
        mutableStateOf(MobileUnifiedLibrarySection.fromPersisted(initialSection))
    }
    var selectedShelfId by remember { mutableStateOf<String?>(null) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var playerBook by remember { mutableStateOf<SharedAudiobook?>(null) }
    var showPlayerSheet by remember { mutableStateOf(false) }
    var ttsPlayerItem by remember { mutableStateOf<SharedTtsListenItem?>(null) }
    var showTtsPlayerSheet by remember { mutableStateOf(false) }
    val unifiedDrawerState = rememberDrawerState(DrawerValue.Closed)
    val unifiedScope = rememberCoroutineScope()
    val visibleBooks = remember(state.rawLibraryBooks, filter, query) {
        mobileUnifiedLibraryBooks(state.rawLibraryBooks, filter, query)
    }
    val continueReading = remember(state.rawLibraryBooks) {
        mobileUnifiedContinueReadingBook(state.rawLibraryBooks)
    }
    val ttsItems = remember(state.rawLibraryBooks, ttsProgress) {
        buildSharedTtsListenItems(state.rawLibraryBooks, ttsProgress)
    }

    fun closeDrawerAnd(action: () -> Unit) {
        unifiedScope.launch {
            unifiedDrawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = unifiedDrawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    readerString("unified_library_drawer_title", "Your library"),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                NavigationDrawerItem(
                    label = { Text(readerString("unified_library_home", "Home")) },
                    selected = section == MobileUnifiedLibrarySection.HOME,
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    onClick = { closeDrawerAnd { section = MobileUnifiedLibrarySection.HOME; selectedShelfId = null; onSectionChange(section.persistedValue) } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(readerString("tab_shelves", "Shelves")) },
                    selected = section == MobileUnifiedLibrarySection.SHELVES,
                    icon = { Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null) },
                    onClick = { closeDrawerAnd { section = MobileUnifiedLibrarySection.SHELVES; selectedShelfId = null; onSectionChange(section.persistedValue) } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(readerString("tab_folders", "Folders")) },
                    selected = section == MobileUnifiedLibrarySection.FOLDERS,
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { closeDrawerAnd { section = MobileUnifiedLibrarySection.FOLDERS; selectedShelfId = null; onSectionChange(section.persistedValue) } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(readerString("tab_catalogs", "Catalogs")) },
                    selected = section == MobileUnifiedLibrarySection.CATALOGS,
                    icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    onClick = { closeDrawerAnd { section = MobileUnifiedLibrarySection.CATALOGS; selectedShelfId = null; onSectionChange(section.persistedValue) } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(readerString("audiobooks_title", "Audiobooks")) },
                    selected = section == MobileUnifiedLibrarySection.AUDIOBOOKS,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = { closeDrawerAnd { section = MobileUnifiedLibrarySection.AUDIOBOOKS; selectedShelfId = null; onSectionChange(section.persistedValue) } },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                Text(
                    readerString("unified_library_appearance", "Appearance"),
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationDrawerItem(
                    label = { Text(readerString("settings", "Settings")) },
                    selected = false,
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { closeDrawerAnd(onOpenSettings) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(readerString("app_theme", "App theme")) },
                    selected = false,
                    icon = { Icon(Icons.Default.Palette, contentDescription = null) },
                    onClick = { closeDrawerAnd(onOpenAppTheme) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(
                    label = { Text(readerString("custom_fonts", "Custom Fonts")) },
                    selected = false,
                    icon = { Icon(Icons.Default.Fonts, contentDescription = null) },
                    onClick = { closeDrawerAnd(onOpenFonts) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        },
    ) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedShelfId?.let { id -> state.shelves.firstOrNull { it.id == id }?.name }
                                ?: when (section) {
                                    MobileUnifiedLibrarySection.HOME -> readerString("nav_unified_library", "Library Beta")
                                    MobileUnifiedLibrarySection.SHELVES -> readerString("tab_shelves", "Shelves")
                                    MobileUnifiedLibrarySection.FOLDERS -> readerString("tab_folders", "Folders")
                                    MobileUnifiedLibrarySection.CATALOGS -> readerString("tab_catalogs", "Catalogs")
                                    MobileUnifiedLibrarySection.AUDIOBOOKS -> readerString("audiobooks_title", "Audiobooks")
                                }
                        )
                        Spacer(Modifier.width(8.dp))
                        if (section == MobileUnifiedLibrarySection.HOME) Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                readerString("unified_library_beta", "BETA"),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedShelfId != null) selectedShelfId = null
                        else unifiedScope.launch { unifiedDrawerState.open() }
                    }) {
                        Icon(
                            if (selectedShelfId != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                            contentDescription = if (selectedShelfId != null) readerString("unified_library_back_to_shelves", "All shelves") else readerString("unified_library_drawer_title", "Your library"),
                        )
                    }
                },
                actions = {
                    if (section == MobileUnifiedLibrarySection.HOME) IconButton(onClick = { searchVisible = !searchVisible; if (!searchVisible) query = "" }) {
                        Icon(
                            if (searchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = readerString("unified_library_search_books", "Search your books"),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            when {
                section == MobileUnifiedLibrarySection.HOME -> ExtendedFloatingActionButton(
                    onClick = onImportBooks,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("unified_library_import", "Import files")) },
                )
                section == MobileUnifiedLibrarySection.SHELVES && selectedShelfId == null -> ExtendedFloatingActionButton(
                    onClick = { showCreateShelf = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("fab_new_shelf", "New shelf")) },
                )
                section == MobileUnifiedLibrarySection.FOLDERS -> Unit
                section == MobileUnifiedLibrarySection.CATALOGS -> Unit
                section == MobileUnifiedLibrarySection.AUDIOBOOKS -> ExtendedFloatingActionButton(
                    onClick = onImportBooks,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("audiobooks_add", "Add audiobook")) },
                )
            }
        },
        bottomBar = {
            val activeAudiobook = audiobooks.firstOrNull { it.bookId == audiobookPlayback.bookId }
            val activeTts = ttsItems.firstOrNull { it.book.id == ttsListenState.bookId && ttsListenState.connected }
            when {
                activeAudiobook != null -> SharedMobileAudiobookMiniPlayer(
                    audiobook = activeAudiobook,
                    playback = audiobookPlayback,
                    onTogglePlayback = onToggleAudiobookPlayback,
                    onExpand = {
                        playerBook = activeAudiobook
                        showPlayerSheet = true
                    },
                    onStopPlayback = onStopAudiobookPlayback,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
                activeTts != null -> SharedMobileTtsMiniPlayer(
                    item = activeTts,
                    playback = ttsListenState,
                    onTogglePlayback = onToggleTtsPlayback,
                    onExpand = {
                        ttsPlayerItem = activeTts
                        showTtsPlayerSheet = true
                    },
                    onStopPlayback = onStopTtsPlayback,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        },
    ) { padding ->
        if (section == MobileUnifiedLibrarySection.SHELVES) {
            SharedMobileUnifiedShelvesSection(
                shelves = state.shelves,
                selectedShelfId = selectedShelfId,
                selectedBookIds = state.selectedBookIds,
                pinnedBookIds = state.pinnedLibraryBookIds,
                downloadingBookIds = state.downloadingBookIds,
                onShelfSelected = { selectedShelfId = it.id },
                onOpenBook = onOpenBook,
                onLongPressBook = onLongPressBook,
                onTogglePinned = onTogglePinned,
                onShowBookInfo = { infoBook = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        if (section == MobileUnifiedLibrarySection.FOLDERS) {
            SharedMobileFolderSyncScreen(
                folders = state.syncedFolders,
                books = state.rawLibraryBooks,
                isLoading = state.isRefreshing,
                onAddFolder = onAddFolder,
                onScanAll = onScanFolders,
                onSyncMetadata = onSyncFolderMetadata,
                onLocalSyncChange = onFolderLocalSyncChange,
                onFileTypesChange = onFolderFileTypesChange,
                onRemoveFolder = onRemoveFolder,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        if (section == MobileUnifiedLibrarySection.CATALOGS) {
            catalogContent(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (section == MobileUnifiedLibrarySection.AUDIOBOOKS) {
            SharedMobileAudiobooksSection(
                audiobooks = audiobooks,
                playback = audiobookPlayback,
                ttsItems = ttsItems,
                ttsPlayback = ttsListenState,
                onAddAudiobook = onImportBooks,
                onOpenPlayer = { book ->
                    if (audiobookPlayback.bookId != book.bookId) {
                        onPlayAudiobook(book)
                    }
                    playerBook = book
                    showPlayerSheet = true
                },
                onOpenTtsPlayer = { item, autoStart ->
                    if (autoStart) {
                        onStartTtsListen(item.book, SharedTtsListenStartPolicy.RESUME, null)
                    }
                    ttsPlayerItem = item
                    showTtsPlayerSheet = true
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(readerString("unified_library_search_books", "Search your books")) },
                    )
                }
            } else {
                continueReading?.let { book ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book) },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(readerString("unified_library_continue_reading", "Continue reading").uppercase(), style = MaterialTheme.typography.labelLarge)
                                Text(book.cardTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                book.author?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Text(readerString("progress_complete", "%1\$d%% complete", (book.progressPercentage ?: 0f).roundToInt()))
                            }
                        }
                    }
                }
            }
            item {
                Text(readerString("unified_library_your_books", "Your books"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(readerQuantityString("book_count", visibleBooks.size, "%1\$d book", "%1\$d books", visibleBooks.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MobileUnifiedLibraryFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { filter = option },
                            label = { Text(readerString(option.stringKey, option.fallbackLabel)) },
                        )
                    }
                }
            }
            if (visibleBooks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        Text(readerString("unified_library_no_books", "No books in this view"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    SharedMobileBookGridSection(
                        title = "",
                        books = visibleBooks,
                        selectedBookIds = state.selectedBookIds,
                        pinnedBookIds = state.pinnedLibraryBookIds,
                        downloadingBookIds = state.downloadingBookIds,
                        onOpenBook = { book ->
                            if (state.selectedBookIds.isEmpty()) onOpenBook(book) else onLongPressBook(book)
                        },
                        onLongPressBook = onLongPressBook,
                        onTogglePinned = onTogglePinned,
                        onShowBookInfo = { infoBook = it },
                    )
                }
            }
        }
    }
    }

    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = state.allTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp.takeIf { it > 0L }?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onDismiss = { infoBook = null },
            onSave = { updated -> onUpdateBook(updated); infoBook = null },
            onRestore = { restored -> onUpdateBook(restored); infoBook = null },
        )
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = readerString("fab_new_shelf", "New shelf"),
            onDismiss = { showCreateShelf = false },
            onCreate = { name -> onCreateShelf(name); showCreateShelf = false },
        )
    }
    if (showPlayerSheet) {
        playerBook?.let { book ->
            SharedMobileAudiobookPlayerSheet(
                audiobook = book,
                playback = audiobookPlayback,
                onTogglePlayback = {
                    if (audiobookPlayback.bookId != book.bookId) {
                        onPlayAudiobook(book)
                    } else {
                        onToggleAudiobookPlayback()
                    }
                },
                onSeek = onSeekAudiobook,
                onSpeedChange = onAudiobookSpeedChange,
                onSleepTimer = onAudiobookSleepTimer,
                onStopPlayback = onStopAudiobookPlayback,
                onDismiss = { showPlayerSheet = false },
            )
        }
    }
    if (showTtsPlayerSheet) {
        ttsPlayerItem?.let { item ->
            SharedMobileTtsPlayerSheet(
                item = item,
                playback = ttsListenState,
                chapterTitles = ttsChapterTitles[item.book.id],
                onTogglePlayback = {
                    if (ttsListenState.bookId != item.book.id || !ttsListenState.connected) {
                        onStartTtsListen(item.book, SharedTtsListenStartPolicy.RESUME, null)
                    } else {
                        onToggleTtsPlayback()
                    }
                },
                onSeekChunk = onSeekTtsChunk,
                onSeekChapter = { index ->
                    if (ttsListenState.bookId == item.book.id && ttsListenState.connected) {
                        onSeekTtsChapter(index)
                    } else {
                        onStartTtsListen(item.book, SharedTtsListenStartPolicy.CHAPTER, index)
                    }
                },
                onSpeedChange = onTtsSpeedChange,
                onSleepTimer = onTtsSleepTimer,
                onStopPlayback = onStopTtsPlayback,
                onDismiss = { showTtsPlayerSheet = false },
            )
        }
    }
}

private enum class MobileUnifiedLibrarySection(val persistedValue: Int) {
    HOME(0),
    SHELVES(1),
    FOLDERS(2),
    CATALOGS(3),
    AUDIOBOOKS(4);

    companion object {
        fun fromPersisted(value: Int): MobileUnifiedLibrarySection =
            entries.firstOrNull { it.persistedValue == value } ?: HOME
    }
}

@Composable
private fun SharedMobileUnifiedShelvesSection(
    shelves: List<Shelf>,
    selectedShelfId: String?,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onShelfSelected: (Shelf) -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onShowBookInfo: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedShelf = shelves.firstOrNull { it.id == selectedShelfId }
    if (selectedShelf == null) {
        val visibleShelves = remember(shelves) {
            shelves.filter { it.type != ShelfType.TAG && it.parentShelfId == null }
        }
        if (visibleShelves.isEmpty()) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(readerString("unified_library_no_shelves", "No shelves yet"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleShelves, key = { it.id }) { shelf ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onShelfSelected(shelf) }) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(readerQuantityString("book_count", shelf.bookCount, "%1\$d book", "%1\$d books", shelf.bookCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    } else {
        LazyColumn(modifier, contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 96.dp)) {
            item {
                SharedMobileBookGridSection(
                    title = "",
                    books = selectedShelf.directBooks,
                    selectedBookIds = selectedBookIds,
                    pinnedBookIds = pinnedBookIds,
                    downloadingBookIds = downloadingBookIds,
                    onOpenBook = { book -> if (selectedBookIds.isEmpty()) onOpenBook(book) else onLongPressBook(book) },
                    onLongPressBook = onLongPressBook,
                    onTogglePinned = onTogglePinned,
                    onShowBookInfo = onShowBookInfo,
                )
            }
        }
    }
}

private val MobileUnifiedLibraryFilter.stringKey: String
    get() = when (this) {
        MobileUnifiedLibraryFilter.ALL -> "unified_library_all"
        MobileUnifiedLibraryFilter.READING -> "unified_library_reading"
        MobileUnifiedLibraryFilter.FINISHED -> "unified_library_finished"
        MobileUnifiedLibraryFilter.UNREAD -> "unified_library_unread"
    }

private val MobileUnifiedLibraryFilter.fallbackLabel: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileHomeScreen(
    state: SharedReaderScreenState,
    actions: SharedMobileHomeActions,
    importedCoverPath: String? = null,
    showTopBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
    val isContextualMode = selectedIds.isNotEmpty()
    var showCreateShelf by remember { mutableStateOf(false) }
    var showAddToShelf by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showRemoveFromRecents by remember { mutableStateOf(false) }
    var showCloseAllTabsConfirmation by remember { mutableStateOf(false) }
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    val model = remember(
        state.recentBooks,
        state.openTabs,
        state.openTabIds,
        state.activeTabBookId,
        state.isTabsEnabled,
        state.pinnedHomeBookIds,
        state.selectedBookIds,
        state.rawLibraryBooks
    ) {
        state.toNonReaderHomeLayoutModel()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                if (isContextualMode) {
                    SharedMobileContextualTopBar(
                        selectedCount = selectedIds.size,
                        onClose = actions::clearSelection,
                        onSelectAll = actions::selectAll,
                        onPin = actions::toggleSelectedPins,
                        onAddToShelf = { showAddToShelf = true },
                        onTag = { showTagDialog = true },
                        onInfo = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }?.let { book ->
                                { infoBook = book }
                            }
                        },
                        onSave = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { actions.saveBook(book) }
                            }
                        },
                        onShare = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { actions.shareBook(book) }
                            }
                        },
                        onExportAnnotations = selectedIds.singleOrNull()?.let { id ->
                            state.recentBooks.firstOrNull { it.id == id }?.let { book ->
                                { actions.exportAnnotations(book) }
                            }
                        },
                        onDelete = { showRemoveFromRecents = true }
                    )
                } else {
                    SharedMobileHomeTopBar(
                        onDrawerClick = actions::openDrawer,
                        onSettingsClick = actions::openSettings,
                        onAppThemeClick = actions::openAppTheme,
                        onRecentLimitClick = actions::openRecentLimit,
                        isTabsEnabled = state.isTabsEnabled,
                        useStrictFileFilter = state.useStrictFileFilter,
                        usePdfFileNameAsDisplayName = state.usePdfFileNameAsDisplayName,
                        onAboutClick = actions::openAbout,
                        onTabsToggle = actions::toggleTabs,
                        onExternalFileBehaviorClick = actions::openExternalFileBehavior,
                        onStrictFilterToggle = actions::toggleStrictFileFilter,
                        onPdfFileNameToggle = actions::togglePdfFileNameDisplay,
                        onLanguageClick = actions::openLanguage,
                    )
                }
            }
        }
    ) { padding ->
        val homeContent: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (model.isEmpty) {
                    SharedMobileEmptyLibrary(
                        title = if (model.isLibraryEmpty) {
                            readerString("library_empty_title", "Your library is empty")
                        } else {
                            readerString("recent_empty_title", "No recent files")
                        },
                        message = if (model.isLibraryEmpty) {
                            readerString("library_empty_desc", "Select a PDF, EPUB, comic, or document to start reading.")
                        } else {
                            readerString("recent_empty_desc", "Open books from the library and they will appear here.")
                        },
                        actionLabel = readerString("select_file", "Select file"),
                        onAction = actions::importBooks,
                        secondaryActionLabel = if (model.isLibraryEmpty) readerString("sync_folder", "Sync folder") else null,
                        onSecondaryAction = actions::navigateToFolderSync,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        if (state.isTabsEnabled && model.activeTabs.isNotEmpty()) {
                            item(key = "tabs") {
                                SharedMobileActiveTabs(
                                    openTabs = model.activeTabs,
                                    onOpenTab = { book ->
                                        when (mobileBookTapIntent(selectedIds)) {
                                            SharedMobileBookTapIntent.OPEN -> actions.openBook(book)
                                            SharedMobileBookTapIntent.TOGGLE_SELECTION -> actions.longPressBook(book)
                                        }
                                    },
                                    onCloseTab = actions::closeTab,
                                    onCloseAllTabs = { showCloseAllTabsConfirmation = true }
                                )
                            }
                        }

                        item(key = "recent") {
                            SharedMobileBookGridSection(
                                title = readerString("recent_files", "Recent files"),
                                books = state.mobileRecentBooks(),
                                selectedBookIds = selectedIds,
                                pinnedBookIds = state.pinnedHomeBookIds,
                                downloadingBookIds = state.downloadingBookIds,
                                onOpenBook = { book ->
                                    when (mobileBookTapIntent(selectedIds)) {
                                        SharedMobileBookTapIntent.OPEN -> actions.openBook(book)
                                        SharedMobileBookTapIntent.TOGGLE_SELECTION -> actions.longPressBook(book)
                                    }
                                },
                                onLongPressBook = { book ->
                                    if (shouldSelectBookOnLongPress(book.id, selectedIds)) {
                                        actions.longPressBook(book)
                                    }
                                },
                                onTogglePinned = actions::togglePinned,
                                onShowBookInfo = { infoBook = it }
                            )
                        }
                    }
                }
                if (!model.isEmpty) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = actions::importBooks) { Text(readerString("select_file", "Select file")) }
                        Button(onClick = actions::navigateToFolderSync) { Text(readerString("sync_folder", "Sync folder")) }
                    }
                }
                if (state.isLoading) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        val canRefresh = state.isSyncEnabled || state.syncedFolders.any { it.localSyncEnabled }
        if (canRefresh) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = actions::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                homeContent()
            }
        } else {
            homeContent()
        }
    }
    if (showRemoveFromRecents) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_remove_from_recents", "Remove from recent files?"),
            body = "Remove ${selectedIds.size} selected book(s) from recent files? They will remain in your library.",
            onDismiss = { showRemoveFromRecents = false },
            onConfirm = {
                actions.removeSelectedBooksFromRecents()
                showRemoveFromRecents = false
            },
        )
    }
    if (showCloseAllTabsConfirmation) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_close_all_tabs", "Close all tabs?"),
            body = readerString(
                "dialog_close_all_tabs_desc",
                "Are you sure you want to close all open tabs?",
            ),
            confirmLabel = readerString("action_close", "Close"),
            emphasizeConfirm = true,
            onDismiss = { showCloseAllTabsConfirmation = false },
            onConfirm = {
                actions.closeAllTabs()
                showCloseAllTabsConfirmation = false
            },
        )
    }
    if (showAddToShelf) {
        SharedAddToShelfDialog(
            shelves = state.shelves.filter { it.type == ShelfType.MANUAL },
            onDismiss = { showAddToShelf = false },
            onCreateShelf = {
                showAddToShelf = false
                showCreateShelf = true
            },
            onShelvesSelected = { shelfIds ->
                actions.addSelectedBooksToShelves(shelfIds)
                showAddToShelf = false
            },
        )
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = readerString("desktop_add_to_shelf", "Add selected books to shelf"),
            onDismiss = { showCreateShelf = false },
            onCreate = { name ->
                actions.createShelfFromSelectedBooks(name)
                showCreateShelf = false
            }
        )
    }
    if (showTagDialog) {
        SharedMobileTagSelectionSheet(
            allTags = state.allTags,
            selectedBookIds = selectedIds,
            books = state.rawLibraryBooks,
            onCreateAndAssign = actions::createAndAssignTag,
            onToggleTag = actions::toggleTagForSelectedBooks,
            onDeleteTag = actions::deleteTag,
            onDismiss = { showTagDialog = false },
        )
    }
    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = state.allTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onRequestCover = actions::importCover,
            externallySelectedCoverPath = importedCoverPath,
            onDismiss = { infoBook = null },
            onSave = {
                actions.updateBook(it)
                infoBook = null
            },
            onRestore = {
                actions.updateBook(it)
                infoBook = null
            }
        )
    }
}

@Composable
fun SharedMobileLibraryScreen(
    state: SharedReaderScreenState,
    selectedTab: SharedMobileLibraryTab,
    onTabChange: (SharedMobileLibraryTab) -> Unit,
    opdsState: SharedOpdsScreenState,
    onImportBooks: () -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchActiveChange: (Boolean) -> Unit = {},
    onSortOrderChange: (SortOrder) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectAll: (Set<String>) -> Unit = { _ -> },
    onFilterClick: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRemoveFilters: (LibraryFilters) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNewShelfClick: () -> Unit = {},
    onOpenShelf: (Shelf) -> Unit = {},
    onLongPressShelf: (Shelf) -> Unit = {},
    onTogglePinned: (BookItem) -> Unit = {},
    onToggleSelectedPins: (Set<String>) -> Unit = {},
    onUpdateBook: (BookItem) -> Unit = {},
    onSaveBook: (BookItem) -> Unit = {},
    onShareBook: (BookItem) -> Unit = {},
    onExportAnnotations: (BookItem) -> Unit = {},
    onImportCover: () -> Unit = {},
    importedCoverPath: String? = null,
    onCreateAndAssignTag: (Set<String>, String) -> Unit = { _, _ -> },
    onToggleTagForBooks: (Set<String>, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteTag: (String) -> Unit = {},
    onCreateShelf: (String, Set<String>) -> Unit = { _, _ -> },
    onAddBooksToShelves: (Set<String>, Set<String>) -> Unit = { _, _ -> },
    onRemoveBooksFromShelf: (Shelf, Set<String>) -> Unit = { _, _ -> },
    onDeleteBooks: (Set<String>) -> Unit = {},
    onDeleteShelves: (Set<String>) -> Unit = {},
    onAddFolder: () -> Unit = {},
    onScanFolders: () -> Unit = {},
    onSyncFolderMetadata: () -> Unit = {},
    onFolderLocalSyncChange: (SyncedFolder, Boolean) -> Unit = { _, _ -> },
    onFolderFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit = { _, _ -> },
    onRemoveFolder: (SyncedFolder) -> Unit = {},
    onRenameShelf: (Shelf, String) -> Unit = { _, _ -> },
    onNavigateShelfBack: () -> Unit = {},
    onShelfAddBooksStateChange: (Boolean, AddBooksSource) -> Unit = { _, _ -> },
    onOpenCatalog: (OpdsCatalog) -> Unit = {},
    onOpenFeedUrl: (String) -> Unit = {},
    onOpdsNavigateBack: () -> Unit = {},
    onOpdsSearch: (String) -> Unit = {},
    onOpdsLoadNextPage: () -> Unit = {},
    onAddCatalog: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
    onUpdateCatalog: (String, String, String, String?, String?) -> Unit = { _, _, _, _, _ -> },
    onRemoveCatalog: (OpdsCatalog) -> Unit = {},
    onDeleteCatalogStreams: (String) -> Unit = {},
    onDownloadOpdsBook: (OpdsEntry, OpdsAcquisition) -> Unit = { _, _ -> },
    onStreamOpdsBook: (OpdsEntry, OpdsCatalog?) -> Unit = { _, _ -> },
    onClearOpdsError: () -> Unit = {},
    opdsCoverContent: (@Composable (OpdsEntry, Modifier) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val selectedIds = state.selectedBookIds
    val selectedShelves = state.selectedShelfIds
    val isBookContextualMode = selectedIds.isNotEmpty()
    val isShelfContextualMode = selectedShelves.isNotEmpty() &&
        selectedTab in setOf(SharedMobileLibraryTab.SHELVES, SharedMobileLibraryTab.FOLDERS)
    var showFilters by remember { mutableStateOf(false) }
    var showCreateShelf by remember { mutableStateOf(false) }
    var showAddToShelf by remember { mutableStateOf(false) }
    var showDeleteBooks by remember { mutableStateOf(false) }
    var showDeleteShelves by remember { mutableStateOf(false) }
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    val viewedShelf = state.viewingShelfId?.let { id -> state.shelves.firstOrNull { it.id == id } }
    val sortedSearchedBooks = remember(
        state.rawLibraryBooks,
        state.searchQuery,
        state.libraryFilters,
        state.syncedFolders,
        state.sortOrder,
        state.pinnedLibraryBookIds,
    ) {
        state.visibleIosLibraryBooks()
    }

    if (viewedShelf != null) {
        SharedMobileShelfDetail(
            shelf = viewedShelf,
            libraryBooks = state.rawLibraryBooks,
            shelves = state.shelves,
            knownTags = state.allTags,
            sortOrder = state.sortOrder,
            selectedBookIds = selectedIds,
            pinnedBookIds = state.pinnedLibraryBookIds,
            downloadingBookIds = state.downloadingBookIds,
            onBack = {
                viewedShelf.parentShelfId
                    ?.let { parentId -> state.shelves.firstOrNull { it.id == parentId } }
                    ?.let(onOpenShelf)
                    ?: onNavigateShelfBack()
            },
            onOpenChildShelf = onOpenShelf,
            onOpenBook = onOpenBook,
            onLongPressBook = onLongPressBook,
            onTogglePinned = onTogglePinned,
            onClearSelection = onClearSelection,
            onSortOrderChange = onSortOrderChange,
            onCreateAndAssignTag = onCreateAndAssignTag,
            onToggleTagForBooks = onToggleTagForBooks,
            onDeleteTag = onDeleteTag,
            onUpdateBook = onUpdateBook,
            onSaveBook = onSaveBook,
            onShareBook = onShareBook,
            onExportAnnotations = onExportAnnotations,
            onImportCover = onImportCover,
            importedCoverPath = importedCoverPath,
            onRemoveBooks = { ids -> onRemoveBooksFromShelf(viewedShelf, ids) },
            onAddBooks = { ids -> onAddBooksToShelves(ids, setOf(viewedShelf.id)) },
            onRenameShelf = { name -> onRenameShelf(viewedShelf, name) },
            onDeleteShelf = { onDeleteShelves(setOf(viewedShelf.id)) },
            initialIsAddingBooks = state.isAddingBooksToShelf,
            initialAddBooksSource = state.addBooksSource,
            onAddingBooksStateChange = onShelfAddBooksStateChange,
            modifier = modifier
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                when {
                    isBookContextualMode -> SharedMobileContextualTopBar(
                        selectedCount = selectedIds.size,
                        onClose = onClearSelection,
                        onSelectAll = {
                            onSelectAll(sortedSearchedBooks.mapTo(linkedSetOf()) { it.id })
                        },
                        onPin = { onToggleSelectedPins(selectedIds) },
                        onAddToShelf = { showAddToShelf = true },
                        onTag = { showTagDialog = true },
                        onShare = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { onShareBook(book) }
                            }
                        },
                        onExportAnnotations = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }?.let { book ->
                                { onExportAnnotations(book) }
                            }
                        },
                        onInfo = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }?.let { book ->
                                { infoBook = book }
                            }
                        },
                        onSave = selectedIds.singleOrNull()?.let { id ->
                            state.libraryBooks.firstOrNull { it.id == id }
                                ?.takeIf { it.canExportOriginalFile() }
                                ?.let { book ->
                                { onSaveBook(book) }
                            }
                        },
                        onDelete = { showDeleteBooks = true }
                    )

                    isShelfContextualMode -> SharedMobileContextualTopBar(
                        selectedCount = selectedShelves.size,
                        onClose = onClearSelection,
                        onSelectAll = null,
                        onPin = null,
                        onDelete = { showDeleteShelves = true }
                    )

                    state.isSearchActive -> SharedMobileSearchTopBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = { onSearchActiveChange(false) }
                    )

                    else -> SharedMobileLibraryTopBar(
                        selectedTab = selectedTab,
                        sortOrder = state.sortOrder,
                        isFilterActive = state.libraryFilters.isActive,
                        onFilterClick = { showFilters = true; onFilterClick() },
                        onSortOrderChange = onSortOrderChange,
                        onSearchClick = { onSearchActiveChange(true) },
                        onSettingsClick = onSettingsClick
                    )
                }
                if (!state.isSearchActive && !isBookContextualMode && !isShelfContextualMode) {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        SharedMobileLibraryTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedTab == tab,
                                onClick = { onTabChange(tab) },
                                text = { Text(tab.sharedMobileLabel()) }
                            )
                        }
                    }
                    if (selectedTab == SharedMobileLibraryTab.BOOKS && state.libraryFilters.isActive) {
                        SharedMobileLibraryFilterChips(
                            filters = state.libraryFilters,
                            fileTypesLabel = readerString(
                                "filter_types",
                                "Types: %1\$s",
                                state.libraryFilters.fileTypes.joinToString { it.name },
                            ),
                            foldersLabel = readerString(
                                "filter_folders",
                                "Folders: %1\$s",
                                state.libraryFilters.sourceFolders.size,
                            ),
                            statusLabel = readerString(
                                "filter_status",
                                "Status: %1\$s",
                                state.libraryFilters.readStatus.sharedMobileLabel(),
                            ),
                            tagsLabel = readerString(
                                "filter_tags",
                                "Tags: %1\$s",
                                state.libraryFilters.tagIds.size,
                            ),
                            clearContentDescription = readerString("action_clear", "Clear"),
                            onRemoveFilters = onRemoveFilters,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isBookContextualMode && !isShelfContextualMode) {
                when (selectedTab) {
                    SharedMobileLibraryTab.BOOKS -> if (sortedSearchedBooks.isNotEmpty()) {
                        ExtendedFloatingActionButton(
                            text = { Text(readerString("select_file", "Add file")) },
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = onImportBooks
                        )
                    }

                    SharedMobileLibraryTab.SHELVES -> ExtendedFloatingActionButton(
                        text = { Text(readerString("fab_new_shelf", "New shelf")) },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { showCreateShelf = true; onNewShelfClick() }
                    )

                    else -> Unit
                }
            }
        }
    ) { padding ->
        when (selectedTab) {
            SharedMobileLibraryTab.BOOKS -> when (
                mobileLibraryBooksState(sortedSearchedBooks.size, state.searchQuery)
            ) {
                SharedMobileLibraryBooksState.CONTENT -> SharedMobileBookList(
                    books = sortedSearchedBooks,
                    selectedBookIds = state.selectedBookIds,
                    pinnedBookIds = state.pinnedLibraryBookIds,
                    downloadingBookIds = state.downloadingBookIds,
                    onOpenBook = { book ->
                        when (mobileBookTapIntent(selectedIds)) {
                            SharedMobileBookTapIntent.OPEN -> onOpenBook(book)
                            SharedMobileBookTapIntent.TOGGLE_SELECTION -> onLongPressBook(book)
                        }
                    },
                    onLongPressBook = onLongPressBook,
                    onTogglePinned = onTogglePinned,
                    onShowBookInfo = { infoBook = it },
                    empty = {},
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                )

                SharedMobileLibraryBooksState.SEARCH_NO_RESULTS -> Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        readerString(
                            "no_results_found",
                            "No results found for \"%1\$s\"",
                            state.searchQuery.trim(),
                        )
                    )
                }

                SharedMobileLibraryBooksState.EMPTY_LIBRARY -> SharedMobileEmptyLibrary(
                    title = readerString("library_empty_title", "Your library is empty"),
                    message = readerString("library_empty_desc", "Select a PDF, EPUB, comic, or document to start reading."),
                    actionLabel = readerString("select_file", "Select file"),
                    onAction = onImportBooks,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }

            SharedMobileLibraryTab.SHELVES -> SharedMobileShelfList(
                shelves = topLevelMobileShelves(state.shelves),
                onOpenShelf = { shelf ->
                    when (mobileShelfTapIntent(selectedShelves)) {
                        SharedMobileShelfTapIntent.OPEN -> onOpenShelf(shelf)
                        SharedMobileShelfTapIntent.TOGGLE_SELECTION -> onLongPressShelf(shelf)
                    }
                },
                onLongPressShelf = { shelf ->
                    if (
                        shelf.type == ShelfType.MANUAL &&
                        shelf.id != "unshelved" &&
                        shouldSelectShelfOnLongPress(shelf.id, selectedShelves)
                    ) {
                        onLongPressShelf(shelf)
                    }
                },
                selectedShelfIds = state.selectedShelfIds,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            SharedMobileLibraryTab.FOLDERS -> SharedMobileFolderSyncScreen(
                folders = state.syncedFolders,
                books = state.rawLibraryBooks,
                isLoading = state.isRefreshing,
                onAddFolder = onAddFolder,
                onScanAll = onScanFolders,
                onSyncMetadata = onSyncFolderMetadata,
                onLocalSyncChange = onFolderLocalSyncChange,
                onFileTypesChange = onFolderFileTypesChange,
                onRemoveFolder = onRemoveFolder,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            SharedMobileLibraryTab.CATALOGS -> {
                val catalogModifier = Modifier.fillMaxSize().padding(padding)
                if (opdsCoverContent == null) {
                    SharedOpdsScreen(
                        state = opdsState, localLibraryBooks = state.rawLibraryBooks,
                        onOpenCatalog = onOpenCatalog, onOpenFeedUrl = onOpenFeedUrl,
                        onNavigateBack = onOpdsNavigateBack, onSearch = onOpdsSearch,
                        onLoadNextPage = onOpdsLoadNextPage, onAddCatalog = onAddCatalog,
                        onUpdateCatalog = onUpdateCatalog, onRemoveCatalog = onRemoveCatalog,
                        onDeleteCatalogStreams = onDeleteCatalogStreams,
                        onDownloadBook = onDownloadOpdsBook, onReadBook = onOpenBook,
                        onStreamBook = onStreamOpdsBook, onClearError = onClearOpdsError,
                        mobileLayout = true, modifier = catalogModifier,
                    )
                } else {
                    SharedOpdsScreen(
                        state = opdsState, localLibraryBooks = state.rawLibraryBooks,
                        onOpenCatalog = onOpenCatalog, onOpenFeedUrl = onOpenFeedUrl,
                        onNavigateBack = onOpdsNavigateBack, onSearch = onOpdsSearch,
                        onLoadNextPage = onOpdsLoadNextPage, onAddCatalog = onAddCatalog,
                        onUpdateCatalog = onUpdateCatalog, onRemoveCatalog = onRemoveCatalog,
                        onDeleteCatalogStreams = onDeleteCatalogStreams,
                        onDownloadBook = onDownloadOpdsBook, onReadBook = onOpenBook,
                        onStreamBook = onStreamOpdsBook, onClearError = onClearOpdsError,
                        coverContent = opdsCoverContent, mobileLayout = true, modifier = catalogModifier,
                    )
                }
            }
        }
    }

    if (showFilters) {
        SharedMobileLibraryFilterDialog(
            state = state,
            onDismiss = { showFilters = false },
            onFiltersChange = onRemoveFilters
        )
    }
    if (showCreateShelf) {
        SharedMobileCreateShelfDialog(
            title = if (selectedIds.isEmpty()) {
                readerString("fab_new_shelf", "New shelf")
            } else {
                readerString("desktop_add_to_shelf", "Add selected books to shelf")
            },
            onDismiss = { showCreateShelf = false },
            onCreate = { name ->
                onCreateShelf(name, selectedIds)
                showCreateShelf = false
            }
        )
    }
    if (showAddToShelf) {
        SharedAddToShelfDialog(
            shelves = state.shelves.filter { it.type == ShelfType.MANUAL },
            onDismiss = { showAddToShelf = false },
            onCreateShelf = {
                showAddToShelf = false
                showCreateShelf = true
            },
            onShelvesSelected = { shelfIds ->
                onAddBooksToShelves(selectedIds, shelfIds)
                showAddToShelf = false
            },
        )
    }
    if (showDeleteBooks) {
        val containsFolderBooks = state.rawLibraryBooks.any {
            it.id in selectedIds && it.sourceFolder != null
        }
        SharedMobileDeleteConfirmationDialog(
            title = "Permanently delete ${selectedIds.size} selected book(s)?",
            body = if (containsFolderBooks) {
                "Warning: Some selected items are synced from a local folder. Proceeding will delete the actual files from your device storage.\n\nThis action cannot be undone."
            } else {
                "Permanently delete ${selectedIds.size} selected book(s)? This action cannot be undone."
            },
            confirmLabel = readerString("action_delete", "Delete"),
            emphasizeConfirm = containsFolderBooks,
            onDismiss = { showDeleteBooks = false },
            onConfirm = {
                onDeleteBooks(selectedIds)
                showDeleteBooks = false
            }
        )
    }
    if (showDeleteShelves) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_delete_shelves_title", "Delete shelves?"),
            body = "Delete ${selectedShelves.size} selected shelf/shelves? Books will remain in the library.",
            confirmLabel = readerString("action_delete", "Delete"),
            onDismiss = { showDeleteShelves = false },
            onConfirm = {
                onDeleteShelves(selectedShelves)
                showDeleteShelves = false
            }
        )
    }
    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = state.allTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onRequestCover = onImportCover,
            externallySelectedCoverPath = importedCoverPath,
            onDismiss = { infoBook = null },
            onSave = {
                onUpdateBook(it)
                infoBook = null
            },
            onRestore = {
                onUpdateBook(it)
                infoBook = null
            }
        )
    }
    if (showTagDialog) {
        SharedMobileTagSelectionSheet(
            allTags = state.allTags,
            selectedBookIds = selectedIds,
            books = state.rawLibraryBooks,
            onCreateAndAssign = { name -> onCreateAndAssignTag(selectedIds, name) },
            onToggleTag = { tagId, assign -> onToggleTagForBooks(selectedIds, tagId, assign) },
            onDeleteTag = onDeleteTag,
            onDismiss = { showTagDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileShelfDetail(
    shelf: Shelf,
    libraryBooks: List<BookItem>,
    shelves: List<Shelf>,
    knownTags: List<Tag>,
    sortOrder: SortOrder,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onBack: () -> Unit,
    onOpenChildShelf: (Shelf) -> Unit,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onClearSelection: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onCreateAndAssignTag: (Set<String>, String) -> Unit,
    onToggleTagForBooks: (Set<String>, String, Boolean) -> Unit,
    onDeleteTag: (String) -> Unit,
    onUpdateBook: (BookItem) -> Unit,
    onSaveBook: (BookItem) -> Unit,
    onShareBook: (BookItem) -> Unit,
    onExportAnnotations: (BookItem) -> Unit,
    onImportCover: () -> Unit,
    importedCoverPath: String?,
    onRemoveBooks: (Set<String>) -> Unit,
    onAddBooks: (Set<String>) -> Unit,
    onRenameShelf: (String) -> Unit,
    onDeleteShelf: () -> Unit,
    initialIsAddingBooks: Boolean,
    initialAddBooksSource: AddBooksSource,
    onAddingBooksStateChange: (Boolean, AddBooksSource) -> Unit,
    modifier: Modifier = Modifier
) {
    var infoBook by remember { mutableStateOf<BookItem?>(null) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showRemoveBooks by remember { mutableStateOf(false) }
    var isAddingBooks by remember(shelf.id) { mutableStateOf(initialIsAddingBooks) }
    var addBooksSource by remember(shelf.id) { mutableStateOf(initialAddBooksSource) }
    var booksSelectedForAdding by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember(shelf.id) { mutableStateOf(false) }
    var searchQuery by remember(shelf.id) { mutableStateOf("") }
    val shelfSearchFocusRequester = remember(shelf.id) { FocusRequester() }
    var showRenameShelf by remember { mutableStateOf(false) }
    var showDeleteShelf by remember { mutableStateOf(false) }
    val selectedShelfBooks = shelf.directBooks.filter { it.id in selectedBookIds }
    val normalizedQuery = searchQuery.trim()
    val childShelves = remember(shelves, shelf.childShelfIds) {
        shelf.childShelfIds.mapNotNull { childId -> shelves.firstOrNull { it.id == childId } }
    }
    val visibleChildShelves = remember(childShelves, normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            childShelves
        } else {
            childShelves.filter { child ->
                child.name.contains(normalizedQuery, ignoreCase = true) ||
                    child.books.filteredSharedMobileBooks(normalizedQuery).isNotEmpty()
            }
        }
    }
    val visibleBooks = remember(shelf.directBooks, normalizedQuery, sortOrder) {
        sortBooks(shelf.directBooks.filteredSharedMobileBooks(normalizedQuery), sortOrder)
    }
    LaunchedEffect(isSearchActive, shelf.id) {
        if (isSearchActive) shelfSearchFocusRequester.requestFocus()
    }
    if (isAddingBooks) {
        SharedMobileAddBooksToShelfScreen(
            shelf = shelf,
            libraryBooks = libraryBooks,
            shelves = shelves,
            source = addBooksSource,
            sortOrder = sortOrder,
            selectedBookIds = booksSelectedForAdding,
            downloadingBookIds = downloadingBookIds,
            onSourceChange = {
                addBooksSource = it
                booksSelectedForAdding =
                    mobileAddBooksSelectionAfterSourceChange(booksSelectedForAdding)
                onAddingBooksStateChange(true, it)
            },
            onSortOrderChange = onSortOrderChange,
            onToggleBook = { id ->
                booksSelectedForAdding = if (id in booksSelectedForAdding) {
                    booksSelectedForAdding - id
                } else {
                    booksSelectedForAdding + id
                }
            },
            onBack = {
                isAddingBooks = false
                booksSelectedForAdding = emptySet()
                onAddingBooksStateChange(false, AddBooksSource.UNSHELVED)
            },
            onAddSelectedBooks = {
                onAddBooks(booksSelectedForAdding)
                isAddingBooks = false
                booksSelectedForAdding = emptySet()
                onAddingBooksStateChange(false, AddBooksSource.UNSHELVED)
            },
            modifier = modifier,
        )
        return
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectedShelfBooks.isNotEmpty()) {
                SharedMobileContextualTopBar(
                    selectedCount = selectedShelfBooks.size,
                    onClose = onClearSelection,
                    onSelectAll = null,
                    onPin = null,
                    onTag = { showTagDialog = true },
                    onInfo = selectedShelfBooks.singleOrNull()?.let { book -> { infoBook = book } },
                    onSave = selectedShelfBooks.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { book -> { onSaveBook(book) } },
                    onShare = selectedShelfBooks.singleOrNull()
                        ?.takeIf { it.canExportOriginalFile() }
                        ?.let { book -> { onShareBook(book) } },
                    onExportAnnotations = selectedShelfBooks.singleOrNull()?.let { book -> { onExportAnnotations(book) } },
                    onDelete = { showRemoveBooks = true },
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(readerString("search_placeholder", "Search title or author…")) },
                            singleLine = true,
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = readerString("content_desc_clear_query", "Clear query"))
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(shelfSearchFocusRequester),
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("content_desc_close_search", "Close search"))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(shelf.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (shelf.type == ShelfType.FOLDER && shelf.childShelfCount > 0) {
                                    "${shelf.childShelfCount} folder${if (shelf.childShelfCount == 1) "" else "s"} · ${shelf.directBookCount} book${if (shelf.directBookCount == 1) "" else "s"}"
                                } else {
                                    "${shelf.bookCount} book${if (shelf.bookCount == 1) "" else "s"}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = readerString("content_desc_sort", "Sort"))
                                Spacer(Modifier.width(8.dp))
                                Text(sortOrder.sharedMobileLabel())
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.sharedMobileLabel()) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = if (order == sortOrder) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = readerString("content_desc_search_shelf", "Search shelf"))
                        }
                        if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved") {
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = readerString("content_desc_more_options", "More options"))
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_rename_shelf", "Rename shelf")) },
                                        onClick = {
                                            showMoreMenu = false
                                            showRenameShelf = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(readerString("menu_delete_shelf", "Delete shelf")) },
                                        onClick = {
                                            showMoreMenu = false
                                            showDeleteShelf = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (shelf.type == ShelfType.MANUAL && shelf.id != "unshelved" && selectedShelfBooks.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        isAddingBooks = true
                        addBooksSource = AddBooksSource.UNSHELVED
                        booksSelectedForAdding = emptySet()
                        onAddingBooksStateChange(true, AddBooksSource.UNSHELVED)
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(readerString("fab_add_books", "Add books")) },
                )
            }
        },
    ) { padding ->
        if (visibleChildShelves.isEmpty() && visibleBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (normalizedQuery.isBlank()) {
                    readerString("shelf_empty", "This shelf is empty")
                } else {
                    readerString("no_results_found", "No results found for \"%1\$s\"", normalizedQuery)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (visibleChildShelves.isNotEmpty()) {
                    if (shelf.type == ShelfType.FOLDER) {
                        item("folder_section") {
                            Text(
                                readerString("section_folders", "Folders"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(visibleChildShelves, key = { "child_${it.id}" }) { child ->
                        SharedMobileShelfRow(
                            shelf = child,
                            selected = false,
                            onClick = { onOpenChildShelf(child) },
                            onLongClick = {},
                        )
                    }
                }
                if (visibleBooks.isNotEmpty() && shelf.type == ShelfType.FOLDER && visibleChildShelves.isNotEmpty()) {
                    item("file_section") {
                        Text(
                            readerString("section_files", "Files"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(visibleBooks, key = { "book_${it.id}" }) { book ->
                    SharedMobileLibraryListItem(
                        book = book,
                        selected = book.id in selectedBookIds,
                        pinned = book.id in pinnedBookIds,
                        downloading = book.id in downloadingBookIds,
                        onClick = {
                            when (mobileBookTapIntent(selectedBookIds)) {
                                SharedMobileBookTapIntent.OPEN -> onOpenBook(book)
                                SharedMobileBookTapIntent.TOGGLE_SELECTION -> onLongPressBook(book)
                            }
                        },
                        onLongClick = {
                            if (shouldSelectBookOnLongPress(book.id, selectedBookIds)) {
                                onLongPressBook(book)
                            }
                        },
                        onTogglePinned = { onTogglePinned(book) },
                        onShowBookInfo = { infoBook = book },
                    )
                }
            }
        }
    }
    if (showRemoveBooks) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_remove_from_shelf", "Remove from shelf?"),
            body = "Remove ${selectedShelfBooks.size} selected book(s) from \"${shelf.name}\"? The books will remain in the library.",
            onDismiss = { showRemoveBooks = false },
            onConfirm = {
                onRemoveBooks(selectedShelfBooks.mapTo(mutableSetOf()) { it.id })
                showRemoveBooks = false
            },
        )
    }
    if (showRenameShelf) {
        SharedMobileCreateShelfDialog(
            title = readerString("dialog_rename_shelf", "Rename shelf"),
            initialName = shelf.name,
            confirmLabel = readerString("action_rename", "Rename"),
            onDismiss = { showRenameShelf = false },
            onCreate = { name ->
                onRenameShelf(name)
                showRenameShelf = false
            },
        )
    }
    if (showDeleteShelf) {
        SharedMobileDeleteConfirmationDialog(
            title = readerString("dialog_delete_shelf", "Delete shelf?"),
            body = readerString(
                "dialog_delete_shelf_desc",
                "Are you sure you want to delete \"%1\$s\"? All books will be moved to Unshelved.",
                shelf.name,
            ),
            confirmLabel = readerString("action_delete", "Delete"),
            onDismiss = { showDeleteShelf = false },
            onConfirm = {
                onDeleteShelf()
                showDeleteShelf = false
            },
        )
    }
    if (showTagDialog) {
        SharedMobileTagSelectionSheet(
            allTags = knownTags,
            selectedBookIds = selectedShelfBooks.mapTo(mutableSetOf()) { it.id },
            books = selectedShelfBooks,
            onCreateAndAssign = { name ->
                onCreateAndAssignTag(selectedShelfBooks.mapTo(mutableSetOf()) { it.id }, name)
            },
            onToggleTag = { tagId, assign ->
                onToggleTagForBooks(
                    selectedShelfBooks.mapTo(mutableSetOf()) { it.id },
                    tagId,
                    assign,
                )
            },
            onDeleteTag = onDeleteTag,
            onDismiss = { showTagDialog = false },
        )
    }
    infoBook?.let { book ->
        SharedBookInfoDialog(
            book = book,
            knownTags = knownTags,
            formattedAddedDate = formatSharedMobileBookInfoDateTime(book.timestamp),
            formattedModifiedDate = book.fileContentModifiedTimestamp
                .takeIf { it > 0L }
                ?.let(::formatSharedMobileBookInfoDateTime),
            displayLocation = mobileBookInfoDisplayLocation(
                book,
                opdsLabel = readerString("source_opds", "Source: OPDS Stream"),
                inAppLabel = readerString("source_in_app", "In-App Storage"),
            ),
            onRequestCover = onImportCover,
            externallySelectedCoverPath = importedCoverPath,
            onDismiss = { infoBook = null },
            onSave = {
                onUpdateBook(it)
                infoBook = null
            },
            onRestore = {
                onUpdateBook(it)
                infoBook = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileAddBooksToShelfScreen(
    shelf: Shelf,
    libraryBooks: List<BookItem>,
    shelves: List<Shelf>,
    source: AddBooksSource,
    sortOrder: SortOrder,
    selectedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onSourceChange: (AddBooksSource) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onToggleBook: (String) -> Unit,
    onBack: () -> Unit,
    onAddSelectedBooks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val availableBooks = remember(libraryBooks, shelves, shelf.id, source, sortOrder) {
        sortBooks(
            booksAvailableForShelfAddition(libraryBooks, shelves, shelf.id, source),
            sortOrder,
        )
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(readerString("add_to_shelf", "Add to %1\$s", shelf.name)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = readerString("action_back", "Back"))
                        }
                    },
                    actions = {
                        Box {
                            TextButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(sortOrder.sharedMobileLabel())
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.sharedMobileLabel()) },
                                        onClick = {
                                            onSortOrderChange(order)
                                            showSortMenu = false
                                        },
                                        trailingIcon = if (order == sortOrder) {
                                            { Icon(Icons.Default.Check, contentDescription = null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AddBooksSource.entries.forEach { candidate ->
                        FilterChip(
                            selected = candidate == source,
                            onClick = { onSourceChange(candidate) },
                            label = {
                                Text(
                                    when (candidate) {
                                        AddBooksSource.UNSHELVED -> readerString("add_books_source_unshelved", "Unshelved")
                                        AddBooksSource.ALL_BOOKS -> readerString("add_books_source_all_books", "All books")
                                    }
                                )
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedBookIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddSelectedBooks,
                    icon = { Icon(Icons.Default.Check, contentDescription = null) },
                    text = { Text(readerString("fab_add_count", "Add (%1\$d)", selectedBookIds.size)) },
                )
            }
        },
    ) { padding ->
        if (availableBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (source == AddBooksSource.UNSHELVED) {
                        readerString("no_unshelved_books", "No unshelved books")
                    } else {
                        readerString("all_books_in_shelf", "All books are already in this shelf")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            SharedMobileBookList(
                books = availableBooks,
                selectedBookIds = selectedBookIds,
                pinnedBookIds = emptySet(),
                downloadingBookIds = downloadingBookIds,
                onOpenBook = { onToggleBook(it.id) },
                onLongPressBook = { onToggleBook(it.id) },
                onTogglePinned = {},
                empty = {},
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileTagSelectionSheet(
    allTags: List<Tag>,
    selectedBookIds: Set<String>,
    books: List<BookItem>,
    onCreateAndAssign: (String) -> Unit,
    onToggleTag: (String, Boolean) -> Unit,
    onDeleteTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var tagPendingDeletion by remember { mutableStateOf<Tag?>(null) }
    val filteredTags = remember(allTags, searchQuery) {
        if (searchQuery.isBlank()) {
            allTags
        } else {
            allTags.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }
    val exactMatch = allTags.any { it.name.equals(searchQuery.trim(), ignoreCase = true) }
    val selectedBooks = remember(books, selectedBookIds) {
        books.filter { it.id in selectedBookIds }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                readerString("title_apply_tags", "Apply tags"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(readerString("placeholder_search_create_tag", "Search or create a tag")) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (searchQuery.isNotBlank() && !exactMatch) {
                    item("create_tag") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCreateAndAssign(searchQuery.trim())
                                    searchQuery = ""
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                readerString("action_create_tag", "Create \"%1\$s\"", searchQuery.trim()),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                items(filteredTags, key = { it.id }) { tag ->
                    val tagColor = Color(tag.color ?: 0xFF64B5F6.toInt())
                    val checkedCount = selectedBooks.count { book -> book.tags.any { it.id == tag.id } }
                    val toggleState = when (checkedCount) {
                        0 -> ToggleableState.Off
                        selectedBookIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleTag(tag.id, toggleState != ToggleableState.On) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(state = toggleState, onClick = null)
                        Surface(
                            shape = CircleShape,
                            color = tagColor.copy(alpha = 0.2f),
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(tagColor, CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { tagPendingDeletion = tag }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = readerString("menu_delete_tag", "Delete tag"),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }

    tagPendingDeletion?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagPendingDeletion = null },
            title = { Text(readerString("menu_delete_tag", "Delete tag")) },
            text = {
                Text(
                    readerString(
                        "dialog_delete_tag_desc",
                        "Delete \"%1\$s\"? It will be removed from every book.",
                        tag.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTag(tag.id)
                        tagPendingDeletion = null
                    }
                ) {
                    Text(readerString("action_delete", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDeletion = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileLibraryFilterDialog(
    state: SharedReaderScreenState,
    onDismiss: () -> Unit,
    onFiltersChange: (LibraryFilters) -> Unit
) {
    var currentFilters by remember(state.libraryFilters, state.syncedFolders) {
        mutableStateOf(state.libraryFilters.withIosFolderFilterIdentities(state.syncedFolders))
    }
    val readableTypes = remember { SharedFileCapabilities.readableTypesFor(ReaderPlatform.IOS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(readerString("filter_library", "Filter library"), style = MaterialTheme.typography.titleLarge)

            Text(readerString("filter_file_type", "File type"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readableTypes.forEach { type ->
                    FilterChip(
                        selected = type in currentFilters.fileTypes,
                        onClick = {
                            currentFilters = currentFilters.copy(
                                fileTypes = currentFilters.fileTypes.toggleMember(type)
                            )
                        },
                        label = { Text(SharedFileCapabilities.displayNameFor(type)) },
                    )
                }
            }

            Text(readerString("filter_source_folder", "Source folder"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = IN_APP_STORAGE_SOURCE in currentFilters.sourceFolders,
                    onClick = {
                        currentFilters = currentFilters.copy(
                            sourceFolders = currentFilters.sourceFolders.toggleMember(IN_APP_STORAGE_SOURCE)
                        )
                    },
                    label = { Text(readerString("filter_in_app_storage", "In-app storage")) },
                )
                state.syncedFolders.forEach { folder ->
                    FilterChip(
                        selected = currentFilters.sourceFolders.any {
                            it == folder.uriString || it == folder.name
                        },
                        onClick = {
                            currentFilters = currentFilters.toggleIosFolderFilter(folder)
                        },
                        label = { Text(folder.name) },
                    )
                }
            }

            Text(readerString("filter_read_status", "Reading status"), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected = currentFilters.readStatus == status,
                        onClick = { currentFilters = currentFilters.copy(readStatus = status) },
                        label = { Text(status.sharedMobileLabel()) },
                    )
                }
            }

            if (state.allTags.isNotEmpty()) {
                Text(readerString("section_tags", "Tags"), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.allTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in currentFilters.tagIds,
                            onClick = {
                                currentFilters = currentFilters.copy(
                                    tagIds = currentFilters.tagIds.toggleMember(tag.id)
                                )
                            },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            Color(tag.color ?: 0xFF64B5F6.toInt()),
                                            CircleShape,
                                        )
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { currentFilters = LibraryFilters() }) {
                    Text(readerString("clear_all", "Clear all"))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onFiltersChange(currentFilters)
                        onDismiss()
                    }
                ) {
                    Text(readerString("action_apply", "Apply"))
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun <T> Set<T>.toggleMember(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
private fun SharedMobileCreateShelfDialog(
    title: String,
    initialName: String = "",
    fieldLabel: String = "Shelf name",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(fieldLabel) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) } }
    )
}

@Composable
private fun SharedMobileDeleteConfirmationDialog(
    title: String,
    body: String,
    confirmLabel: String = "Remove",
    emphasizeConfirm: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (emphasizeConfirm) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) } }
    )
}

enum class SharedMobileLibraryTab {
    BOOKS,
    SHELVES,
    FOLDERS,
    CATALOGS,
}

@Composable
private fun SharedMobileLibraryTab.sharedMobileLabel(): String = when (this) {
    SharedMobileLibraryTab.BOOKS -> readerString("tab_all_books", "All Books")
    SharedMobileLibraryTab.SHELVES -> readerString("tab_shelves", "Shelves")
    SharedMobileLibraryTab.FOLDERS -> readerString("tab_folders", "Folders")
    SharedMobileLibraryTab.CATALOGS -> readerString("tab_catalogs", "Catalogs")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileHomeTopBar(
    onDrawerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAppThemeClick: () -> Unit,
    onRecentLimitClick: () -> Unit,
    isTabsEnabled: Boolean,
    useStrictFileFilter: Boolean,
    usePdfFileNameAsDisplayName: Boolean,
    onAboutClick: () -> Unit,
    onTabsToggle: () -> Unit,
    onExternalFileBehaviorClick: () -> Unit,
    onStrictFilterToggle: () -> Unit,
    onPdfFileNameToggle: () -> Unit,
    onLanguageClick: () -> Unit,
) {
    var showOptionsMenu by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onDrawerClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
            IconButton(onClick = onAppThemeClick) {
                Icon(Icons.Default.Palette, contentDescription = readerString("app_theme", "App theme"))
            }
            IconButton(onClick = onRecentLimitClick) {
                Icon(
                    Icons.Default.FormatListNumbered,
                    contentDescription = readerString("options_recent_limit", "Recent files limit"),
                )
            }
            Box {
                IconButton(onClick = { showOptionsMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = showOptionsMenu,
                    onDismissRequest = { showOptionsMenu = false },
                ) {
                    SharedMobileHomeOption(
                        label = readerString("about_title", "About"),
                        onClick = {
                            showOptionsMenu = false
                            onAboutClick()
                        },
                    )
                    HorizontalDivider()
                    SharedMobileHomeOption(
                        label = readerString("options_enable_multi_tab_reading", "Enable multi-tab reading"),
                        checked = isTabsEnabled,
                        onClick = {
                            showOptionsMenu = false
                            onTabsToggle()
                        },
                    )
                    SharedMobileHomeOption(
                        label = readerString("options_external_file_behavior", "External file behavior"),
                        onClick = {
                            showOptionsMenu = false
                            onExternalFileBehaviorClick()
                        },
                    )
                    SharedMobileHomeOption(
                        label = readerString("options_use_strict_file_filter", "Use strict file filter"),
                        checked = useStrictFileFilter,
                        onClick = {
                            showOptionsMenu = false
                            onStrictFilterToggle()
                        },
                    )
                    SharedMobileHomeOption(
                        label = readerString(
                            "options_use_pdf_filename_display_name",
                            "Use PDF filename as display name",
                        ),
                        checked = usePdfFileNameAsDisplayName,
                        onClick = {
                            showOptionsMenu = false
                            onPdfFileNameToggle()
                        },
                    )
                    HorizontalDivider()
                    SharedMobileHomeOption(
                        label = readerString("options_language", "Language"),
                        onClick = {
                            showOptionsMenu = false
                            onLanguageClick()
                        },
                    )
                }
            }
        }
    )
}

@Composable
private fun SharedMobileHomeOption(
    label: String,
    checked: Boolean? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        trailingIcon = if (checked == true) {
            { Icon(Icons.Default.Check, contentDescription = readerString("content_desc_enabled", "Enabled")) }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileLibraryTopBar(
    selectedTab: SharedMobileLibraryTab,
    sortOrder: SortOrder,
    isFilterActive: Boolean,
    onFilterClick: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = { Text(readerString("library_title", "Library")) },
        actions = {
            if (selectedTab == SharedMobileLibraryTab.BOOKS) {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = readerString("content_desc_filter", "Filter"),
                        tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SharedMobileLibrarySortControl(
                    sortOrder = sortOrder,
                    labels = SortOrder.entries.associateWith { it.sharedMobileLabel() },
                    selectedContentDescription = readerString("content_desc_selected", "Selected"),
                    onSortOrderChange = onSortOrderChange,
                    icon = {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = readerString("content_desc_sort", "Sort"),
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Default.Search, contentDescription = readerString("action_search", "Search"))
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = readerString("settings", "Settings"))
            }
        }
    )
}

@Composable
fun SharedMobileLibrarySortControl(
    sortOrder: SortOrder,
    labels: Map<SortOrder, String>,
    selectedContentDescription: String,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { showSortMenu = true },
            modifier = modifier,
        ) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(labels[sortOrder].orEmpty())
        }
        DropdownMenu(
            expanded = showSortMenu,
            onDismissRequest = { showSortMenu = false },
        ) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(labels[order].orEmpty()) },
                    onClick = {
                        onSortOrderChange(order)
                        showSortMenu = false
                    },
                    trailingIcon = {
                        if (order == sortOrder) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = selectedContentDescription,
                            )
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedMobileLibraryBookListCardFrame(
    isAvailable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable BoxScope.() -> Unit,
    header: @Composable RowScope.() -> Unit,
    metadata: @Composable RowScope.() -> Unit,
    progress: @Composable () -> Unit,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 6.dp else 2.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (isAvailable) 1f else 0.8f }
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                } else {
                    Modifier
                },
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .height(132.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.medium)
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        MaterialTheme.shapes.medium,
                    ),
                content = cover,
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    content = header,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = metadata,
                )
                Spacer(Modifier.weight(1f))
                progress()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedMobileShelfListCardFrame(
    isSelected: Boolean,
    contentStartIndent: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
                } else {
                    Modifier
                },
            )
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = 12.dp + contentStartIndent,
                end = 12.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun SharedMobileTopBanner(
    text: String,
    visible: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = if (isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
fun SharedMobileTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Box(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

data class SharedMobileContextualActionLabels(
    val selectedCount: String,
    val clearSelection: String,
    val info: String,
    val pin: String,
    val selectAll: String,
    val delete: String,
    val moreOptions: String,
    val tag: String,
    val addToShelf: String,
    val save: String,
    val share: String,
    val exportAnnotations: String,
    val clear: String,
)

@Composable
fun SharedMobileContextualActionBar(
    selectedItemCount: Int,
    labels: SharedMobileContextualActionLabels,
    onNavigateBack: () -> Unit,
    onDelete: () -> Unit,
    compact: Boolean,
    onInfo: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    onTag: (() -> Unit)? = null,
    onAddToShelf: (() -> Unit)? = null,
    onSelectAll: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    tagIcon: @Composable (String?) -> Unit,
) {
    SharedMobileTopAppBar(
        title = {
            Text(labels.selectedCount, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = labels.clearSelection)
            }
        },
        actions = {
            if (compact) {
                SharedMobileCompactSelectionActions(
                    selectedItemCount = selectedItemCount,
                    labels = labels,
                    onInfo = onInfo,
                    onPin = onPin,
                    onSelectAll = onSelectAll,
                    onTag = onTag,
                    onAddToShelf = onAddToShelf,
                    onSave = onSave,
                    onShare = onShare,
                    onExportAnnotations = onExportAnnotations,
                    onClear = onClear ?: onNavigateBack,
                    onDelete = onDelete,
                    tagIcon = tagIcon,
                )
            } else {
                onTag?.let { action ->
                    IconButton(onClick = action) { tagIcon(labels.tag) }
                }
                onPin?.let { action ->
                    IconButton(onClick = action) {
                        Icon(Icons.Default.PushPin, contentDescription = labels.pin)
                    }
                }
                if (selectedItemCount == 1) {
                    onInfo?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Info, contentDescription = labels.info)
                        }
                    }
                    onSave?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Save, contentDescription = labels.save)
                        }
                    }
                    onShare?.let { action ->
                        IconButton(onClick = action) {
                            Icon(Icons.Default.Share, contentDescription = labels.share)
                        }
                    }
                }
                onSelectAll?.let { action ->
                    IconButton(onClick = action) {
                        Icon(Icons.Default.SelectAll, contentDescription = labels.selectAll)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = labels.delete)
                }
            }
        },
    )
}

@Composable
private fun SharedMobileCompactSelectionActions(
    selectedItemCount: Int,
    labels: SharedMobileContextualActionLabels,
    onInfo: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onSelectAll: (() -> Unit)?,
    onTag: (() -> Unit)?,
    onAddToShelf: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onExportAnnotations: (() -> Unit)?,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    tagIcon: @Composable (String?) -> Unit,
) {
    var showMore by remember { mutableStateOf(false) }
    if (selectedItemCount == 1) {
        onInfo?.let { action ->
            IconButton(onClick = action) { Icon(Icons.Default.Info, contentDescription = labels.info) }
        }
    }
    onPin?.let { action ->
        IconButton(onClick = action) { Icon(Icons.Default.PushPin, contentDescription = labels.pin) }
    }
    onSelectAll?.let { action ->
        IconButton(onClick = action) { Icon(Icons.Default.SelectAll, contentDescription = labels.selectAll) }
    }
    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = labels.delete) }
    Box {
        IconButton(onClick = { showMore = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = labels.moreOptions)
        }
        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
            onTag?.let { action ->
                DropdownMenuItem(
                    text = { Text(labels.tag) },
                    leadingIcon = { tagIcon(null) },
                    onClick = { showMore = false; action() },
                )
            }
            onAddToShelf?.let { action ->
                DropdownMenuItem(
                    text = { Text(labels.addToShelf) },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                    onClick = { showMore = false; action() },
                )
            }
            if (selectedItemCount == 1) {
                onSave?.let { action ->
                    DropdownMenuItem(
                        text = { Text(labels.save) },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = { showMore = false; action() },
                    )
                }
                onShare?.let { action ->
                    DropdownMenuItem(
                        text = { Text(labels.share) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { showMore = false; action() },
                    )
                }
                onExportAnnotations?.let { action ->
                    DropdownMenuItem(
                        text = { Text(labels.exportAnnotations) },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = { showMore = false; action() },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(labels.clear) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                onClick = { showMore = false; onClear() },
            )
        }
    }
}

@Composable
fun SharedMobileLibrarySearchTopBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    showClear: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    closeContentDescription: String,
    clearContentDescription: String,
    focusRequester: FocusRequester,
    textFieldModifier: Modifier = Modifier,
) {
    Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = closeContentDescription)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                modifier = textFieldModifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (showClear) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = clearContentDescription)
                        }
                    }
                },
            )
        }
    }
}

data class SharedMobileLibraryFilterLabels(
    val title: String,
    val fileType: String,
    val sourceFolder: String,
    val inAppStorage: String,
    val readStatus: String,
    val tags: String,
    val clearAll: String,
    val apply: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedMobileLibraryFilterDialog(
    filters: LibraryFilters,
    allTags: List<Tag>,
    syncedFolders: List<SyncedFolder>,
    readableFileTypes: Set<FileType>,
    fileTypeLabels: Map<FileType, String>,
    readStatusLabels: Map<ReadStatusFilter, String>,
    labels: SharedMobileLibraryFilterLabels,
    onApply: (LibraryFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentFilters by remember { mutableStateOf(filters) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(labels.title, style = MaterialTheme.typography.titleLarge)
            Text(labels.fileType, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readableFileTypes.forEach { type ->
                    FilterChip(
                        selected = type in currentFilters.fileTypes,
                        onClick = {
                            currentFilters = currentFilters.copy(
                                fileTypes = currentFilters.fileTypes.toggleMember(type),
                            )
                        },
                        label = { Text(fileTypeLabels[type].orEmpty()) },
                    )
                }
            }
            Text(labels.sourceFolder, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = IN_APP_STORAGE_SOURCE in currentFilters.sourceFolders,
                    onClick = {
                        currentFilters = currentFilters.copy(
                            sourceFolders = currentFilters.sourceFolders.toggleMember(IN_APP_STORAGE_SOURCE),
                        )
                    },
                    label = { Text(labels.inAppStorage) },
                )
                syncedFolders.forEach { folder ->
                    FilterChip(
                        selected = folder.uriString in currentFilters.sourceFolders,
                        onClick = {
                            currentFilters = currentFilters.copy(
                                sourceFolders = currentFilters.sourceFolders.toggleMember(folder.uriString),
                            )
                        },
                        label = { Text(folder.name) },
                    )
                }
            }
            Text(labels.readStatus, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadStatusFilter.entries.forEach { status ->
                    FilterChip(
                        selected = currentFilters.readStatus == status,
                        onClick = { currentFilters = currentFilters.copy(readStatus = status) },
                        label = { Text(readStatusLabels[status].orEmpty()) },
                    )
                }
            }
            if (allTags.isNotEmpty()) {
                Text(labels.tags, style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    allTags.forEach { tag ->
                        val selected = tag.id in currentFilters.tagIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                currentFilters = currentFilters.copy(
                                    tagIds = currentFilters.tagIds.toggleMember(tag.id),
                                )
                            },
                            label = { Text(tag.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier.size(10.dp).background(
                                        Color(tag.color ?: 0xFF64B5F6.toInt()),
                                        CircleShape,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { currentFilters = LibraryFilters() }) { Text(labels.clearAll) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onApply(currentFilters); onDismiss() }) { Text(labels.apply) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    CenterAlignedTopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(readerString("action_search", "Search")) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = readerString("content_desc_clear_query", "Clear search"),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = readerString("content_desc_close_search", "Close search"),
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedMobileContextualTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: (() -> Unit)?,
    onPin: (() -> Unit)?,
    onAddToShelf: (() -> Unit)? = null,
    onTag: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onExportAnnotations: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    CenterAlignedTopAppBar(
        title = { Text(readerString("selected_count", "%1\$d selected", selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            if (onInfo != null && selectedCount == 1) {
                IconButton(onClick = onInfo) {
                    Icon(Icons.Default.Info, contentDescription = "Book info")
                }
            }
            if (onPin != null) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Default.PushPin, contentDescription = "Pin")
                }
            }
            if (onSelectAll != null) {
                IconButton(onClick = onSelectAll) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove selected")
                }
            }
            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    onTag?.let { tag ->
                        DropdownMenuItem(
                            text = { Text(readerString("section_tags", "Tags")) },
                            onClick = { showMoreMenu = false; tag() }
                        )
                    }
                    onAddToShelf?.let { addToShelf ->
                        DropdownMenuItem(
                            text = { Text(readerString("desktop_add_to_shelf", "Add to shelf")) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = { showMoreMenu = false; addToShelf() }
                        )
                    }
                    onRename?.let { rename ->
                        DropdownMenuItem(
                            text = { Text(readerString("action_rename", "Rename")) },
                            onClick = { showMoreMenu = false; rename() }
                        )
                    }
                    if (selectedCount == 1) {
                        onSave?.let { save ->
                            DropdownMenuItem(
                                text = { Text(readerString("action_save_copy_to_device", "Save copy to device")) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = { showMoreMenu = false; save() }
                            )
                        }
                        onShare?.let { share ->
                            DropdownMenuItem(
                                text = { Text(readerString("action_share_original_file", "Share original file")) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = { showMoreMenu = false; share() }
                            )
                        }
                        onExportAnnotations?.let { export ->
                            DropdownMenuItem(
                                text = { Text(readerString("action_export_annotations", "Export annotations")) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = { showMoreMenu = false; export() }
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(readerString("action_clear", "Clear")) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = { showMoreMenu = false; onClose() }
                    )
                }
            }
        }
    )
}

@Composable
fun SharedMobileLibraryFilterChips(
    filters: LibraryFilters,
    fileTypesLabel: String,
    foldersLabel: String,
    statusLabel: String,
    tagsLabel: String,
    clearContentDescription: String,
    onRemoveFilters: (LibraryFilters) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filters.fileTypes.isNotEmpty()) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(fileTypes = emptySet())) },
                label = { Text(fileTypesLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
        if (filters.sourceFolders.isNotEmpty()) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(sourceFolders = emptySet())) },
                label = { Text(foldersLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
        if (filters.readStatus != ReadStatusFilter.ALL) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(readStatus = ReadStatusFilter.ALL)) },
                label = { Text(statusLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
        if (filters.tagIds.isNotEmpty()) {
            AssistChip(
                onClick = { onRemoveFilters(filters.copy(tagIds = emptySet())) },
                label = { Text(tagsLabel) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = clearContentDescription, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun SharedMobileActiveTabs(
    openTabs: List<BookItem>,
    onOpenTab: (BookItem) -> Unit,
    onCloseTab: (BookItem) -> Unit,
    onCloseAllTabs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Active tabs", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onCloseAllTabs) {
                Icon(Icons.Default.Close, contentDescription = "Close all tabs", tint = MaterialTheme.colorScheme.error)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(openTabs, key = { "tab_${it.id}" }) { tab ->
                InputChip(
                    selected = false,
                    onClick = { onOpenTab(tab) },
                    label = {
                        Text(
                            text = tab.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onCloseTab(tab) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SharedMobileBookGridSection(
    title: String,
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String>,
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onShowBookInfo: (BookItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.height((((books.size + 2) / 3).coerceAtLeast(1) * 244).dp)
        ) {
            items(books, key = { it.id }) { book ->
                SharedMobileBookCard(
                    book = book,
                    selected = book.id in selectedBookIds,
                    pinned = book.id in pinnedBookIds,
                    downloading = book.id in downloadingBookIds,
                    onClick = { onOpenBook(book) },
                    onLongClick = {
                        if (shouldSelectBookOnLongPress(book.id, selectedBookIds)) {
                            onLongPressBook(book)
                        }
                    },
                    onTogglePinned = { onTogglePinned(book) },
                    onShowBookInfo = { onShowBookInfo(book) }
                )
            }
        }
    }
}

@Composable
private fun SharedMobileBookList(
    books: List<BookItem>,
    selectedBookIds: Set<String>,
    pinnedBookIds: Set<String>,
    downloadingBookIds: Set<String> = emptySet(),
    onOpenBook: (BookItem) -> Unit,
    onLongPressBook: (BookItem) -> Unit,
    onTogglePinned: (BookItem) -> Unit,
    onShowBookInfo: (BookItem) -> Unit = {},
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (books.isEmpty()) {
        empty()
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            SharedMobileLibraryListItem(
                book = book,
                selected = book.id in selectedBookIds,
                pinned = book.id in pinnedBookIds,
                downloading = book.id in downloadingBookIds,
                onClick = { onOpenBook(book) },
                onLongClick = {
                    if (shouldSelectBookOnLongPress(book.id, selectedBookIds)) {
                        onLongPressBook(book)
                    }
                },
                onTogglePinned = { onTogglePinned(book) },
                onShowBookInfo = { onShowBookInfo(book) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobileBookCard(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    downloading: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onShowBookInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .graphicsLayer { alpha = if (book.isAvailable) 1f else 0.8f }
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 6.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                SharedMobileBookCover(
                    book = book,
                    selected = selected,
                    pinned = pinned,
                    onTogglePinned = onTogglePinned,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.74f)
                )
                IconButton(
                    onClick = onShowBookInfo,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.48f)) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Book details",
                            tint = Color.White,
                            modifier = Modifier.padding(6.dp).size(18.dp)
                        )
                    }
                }
                if (downloading) {
                    Surface(
                        modifier = Modifier.matchParentSize(),
                        color = Color.Black.copy(alpha = 0.38f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.cardAuthor().ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobileLibraryListItem(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    downloading: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onShowBookInfo: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (book.isAvailable) 1f else 0.8f }
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else Modifier)
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .height(132.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            SharedMobileBookCover(
                book = book,
                selected = selected,
                pinned = pinned,
                onTogglePinned = onTogglePinned,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(0.7f),
                compact = true
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        book.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mobileBookStatusBadges(book, pinned).forEach { badge ->
                            SharedMobileCoverStatusBadge(
                                badge = badge,
                                onClick = onTogglePinned.takeIf { badge == SharedMobileBookStatusBadge.PINNED },
                                overlay = false,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    book.cardAuthor(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        book.type.name,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                book.progressPercentage?.takeIf { it > 0f }?.coerceIn(0f, 100f)?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${progress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }
            if (downloading) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    color = Color.Black.copy(alpha = 0.32f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMobileBookCover(
    book: BookItem,
    selected: Boolean,
    pinned: Boolean,
    onTogglePinned: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val color = fileTypeColor(book.type)
    val statusBadges = mobileBookStatusBadges(book, pinned)
    val progressPercent = book.progressPercentage
        ?.takeIf { it > 0f }
        ?.coerceIn(0f, 100f)
        ?.toInt()
    Surface(
        modifier = modifier,
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
        tonalElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!book.coverImagePath.isNullOrBlank()) {
                LocalBookCoverImage(
                    path = book.coverImagePath,
                    contentDescription = book.cardTitle(LocalUsePdfFileNameAsDisplayName.current),
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.15f),
                                0.3f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.5f),
                            )
                        ),
                )
            } else {
                Icon(Icons.Default.Book, contentDescription = null, modifier = Modifier.size(if (compact) 24.dp else 38.dp))
            }
            if (!compact) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    statusBadges.forEach { badge ->
                        SharedMobileCoverStatusBadge(
                            badge = badge,
                            onClick = onTogglePinned.takeIf { badge == SharedMobileBookStatusBadge.PINNED },
                            overlay = true,
                        )
                    }
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        modifier = Modifier
                            .size(if (compact) 32.dp else 48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(8.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            if (!book.isAvailable) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Not available locally",
                        modifier = Modifier.size(if (compact) 32.dp else 48.dp),
                        tint = Color.White,
                    )
                }
            }
            if (!compact) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    progressPercent?.let { percent ->
                        SharedMobileCoverTextBadge(
                            text = "$percent%",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SharedMobileCoverTextBadge(
                        text = book.type.name,
                        containerColor = Color.Black.copy(alpha = 0.62f),
                        contentColor = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedMobileCoverTextBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun SharedMobileCoverStatusBadge(
    badge: SharedMobileBookStatusBadge,
    onClick: (() -> Unit)?,
    overlay: Boolean,
) {
    val icon = when (badge) {
        SharedMobileBookStatusBadge.PINNED -> Icons.Default.PushPin
        SharedMobileBookStatusBadge.FOLDER -> Icons.Default.Folder
        SharedMobileBookStatusBadge.CATALOG -> Icons.Default.Cloud
    }
    val label = when (badge) {
        SharedMobileBookStatusBadge.PINNED -> readerString("pinned", "Pinned")
        SharedMobileBookStatusBadge.FOLDER -> readerString("desktop_book_badge_folder", "Folder")
        SharedMobileBookStatusBadge.CATALOG -> readerString("action_stream", "Stream")
    }
    Surface(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (overlay) {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (overlay) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = if (overlay) 0.dp else 2.dp,
        shadowElevation = if (overlay) 0.dp else 1.dp,
        border = if (overlay) BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)) else null,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.padding(6.dp).size(14.dp),
        )
    }
}

@Composable
private fun SharedMobileFolderSyncScreen(
    folders: List<SyncedFolder>,
    books: List<BookItem>,
    isLoading: Boolean,
    onAddFolder: () -> Unit,
    onScanAll: () -> Unit,
    onSyncMetadata: () -> Unit,
    onLocalSyncChange: (SyncedFolder, Boolean) -> Unit,
    onFileTypesChange: (SyncedFolder, Set<FileType>) -> Unit,
    onRemoveFolder: (SyncedFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    var disablingFolder by remember { mutableStateOf<SyncedFolder?>(null) }
    if (folders.isEmpty()) {
        SharedMobileEmptyLibrary(
            title = readerString("sync_local_folders", "Sync Local Folders"),
            message = readerString(
                "sync_folders_desc",
                "Connect local folders to create a live library. Reader will monitor supported files.",
            ),
            actionLabel = readerString("action_select_folder", "Select Folder"),
            onAction = onAddFolder,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                FilledTonalButton(
                    onClick = onScanAll,
                    enabled = !isLoading && folders.any { it.localSyncEnabled },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isLoading) readerString("scanning", "Scanning…")
                        else readerString("scan_all", "Scan All")
                    )
                }
                OutlinedButton(
                    onClick = onSyncMetadata,
                    enabled = !isLoading && folders.any { it.localSyncEnabled },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(readerString("sync_meta", "Sync Meta"))
                }
            }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(folders, key = { it.uriString }) { folder ->
                        val folderBooks = books.filter {
                            it.sourceFolder == folder.name || it.sourceFolder == folder.uriString
                        }
                        SharedMobileFolderCard(
                            folder = folder,
                            books = folderBooks,
                            onEditFilters = { editingFolder = folder },
                            onLocalSyncChange = { enabled ->
                                if (enabled) onLocalSyncChange(folder, true) else disablingFolder = folder
                            },
                            onRemove = { onRemoveFolder(folder) },
                        )
                    }
                }
            }
            if (canAddSyncedFolder(folders)) {
                ExtendedFloatingActionButton(
                    text = { Text(readerString("fab_add_folder", "Add Folder")) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = onAddFolder,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }

    editingFolder?.let { folder ->
        SharedMobileFolderFiltersDialog(
            folder = folder,
            onDismiss = { editingFolder = null },
            onSave = {
                onFileTypesChange(folder, it)
                editingFolder = null
            },
        )
    }
    disablingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { disablingFolder = null },
            title = {
                Text(readerString("dialog_disable_folder_local_sync_title", "Disable local sync?"))
            },
            text = {
                Text(
                    readerString(
                        "dialog_disable_folder_local_sync_ios_desc",
                        "Reader will stop refreshing \"%1\$s\". Existing managed copies remain available for reading.",
                        folder.name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLocalSyncChange(folder, false)
                        disablingFolder = null
                    }
                ) {
                    Text(readerString("action_disable_keep_sync_data", "Disable and keep data"))
                }
            },
            dismissButton = {
                TextButton(onClick = { disablingFolder = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun SharedMobileFolderCard(
    folder: SyncedFolder,
    books: List<BookItem>,
    onEditFilters: () -> Unit,
    onLocalSyncChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        folder.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!folder.localSyncEnabled) {
                        Text(
                            readerString("folder_local_sync_disabled", "Local sync disabled"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Folder options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(readerString("menu_edit_filters", "Edit Filters")) },
                            onClick = { showMenu = false; onEditFilters() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (folder.localSyncEnabled) {
                                        readerString("menu_disable_folder_local_sync", "Disable local sync")
                                    } else {
                                        readerString("menu_enable_folder_local_sync", "Enable local sync")
                                    },
                                )
                            },
                            onClick = {
                                showMenu = false
                                onLocalSyncChange(!folder.localSyncEnabled)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(readerString("menu_remove_folder", "Remove Folder")) },
                            onClick = { showMenu = false; onRemove() },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        readerString("last_sync", "LAST SYNC"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (folder.lastScanTime == 0L) {
                            readerString("never", "Never")
                        } else {
                            formatSharedMobileDateTime(folder.lastScanTime)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        readerString("books_count", "BOOKS"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(books.size.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val counts = books.groupingBy { it.type }.eachCount()
            if (counts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(counts.entries.toList(), key = { it.key.name }) { (type, count) ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text("${type.name}: $count") },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMobileFolderFiltersDialog(
    folder: SyncedFolder,
    onDismiss: () -> Unit,
    onSave: (Set<FileType>) -> Unit,
) {
    val availableTypes = remember {
        SharedFileCapabilities.all
            .filter { it.syncEligible && it.isReadableOnIos }
            .map { it.type }
    }
    var selectedTypes by remember(folder.uriString, folder.allowedFileTypes) {
        mutableStateOf(folder.allowedFileTypes.intersect(availableTypes.toSet()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(readerString("filter_file_types", "Filter File Types")) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableTypes, key = { it.name }) { type ->
                    FilterChip(
                        selected = type in selectedTypes,
                        onClick = {
                            selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                        },
                        label = { Text(type.name) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedTypes) },
                enabled = selectedTypes.isNotEmpty(),
            ) { Text(readerString("action_save", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(readerString("action_cancel", "Cancel")) }
        },
    )
}

@Composable
private fun SharedMobileShelfList(
    shelves: List<Shelf>,
    onOpenShelf: (Shelf) -> Unit,
    onLongPressShelf: (Shelf) -> Unit,
    selectedShelfIds: Set<String> = emptySet(),
    emptyTitle: String = "No shelves yet",
    emptyMessage: String = "Create shelves to organize your library.",
    modifier: Modifier = Modifier
) {
    if (shelves.isEmpty()) {
        SharedMobileEmptyLibrary(
            title = emptyTitle,
            message = emptyMessage,
            actionLabel = null,
            onAction = {},
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shelves, key = { it.id }) { shelf ->
            SharedMobileShelfRow(
                shelf = shelf,
                selected = shelf.id in selectedShelfIds,
                onClick = { onOpenShelf(shelf) },
                onLongClick = { onLongPressShelf(shelf) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SharedMobileShelfRow(
    shelf: Shelf,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(shelf.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${shelf.bookCount} ${if (shelf.bookCount == 1) "book" else "books"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
fun SharedMobileEmptyLibrary(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FileOpen,
            contentDescription = readerString("content_desc_no_files_icon", "No files"),
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(32.dp))
            FilledTonalButton(
                onClick = onAction,
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(actionLabel)
            }
        }
        if (secondaryActionLabel != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onSecondaryAction) {
                Text(secondaryActionLabel)
            }
        }
    }
}

private fun fileTypeColor(type: FileType): Color {
    return when (type) {
        FileType.PDF -> Color(0xFFE53935)
        FileType.EPUB, FileType.MOBI, FileType.FB2 -> Color(0xFF1E88E5)
        FileType.CBZ, FileType.CBR, FileType.CB7, FileType.CBT -> Color(0xFF8E24AA)
        FileType.DOCX, FileType.ODT, FileType.FODT -> Color(0xFF3949AB)
        FileType.MD, FileType.TXT, FileType.HTML -> Color(0xFF00897B)
        FileType.PPTX -> Color(0xFFF4511E)
        FileType.AUDIOBOOK -> Color(0xFF6D4C41)
        FileType.UNKNOWN -> Color(0xFF757575)
    }
}

private fun List<BookItem>.filteredSharedMobileBooks(query: String): List<BookItem> {
    val normalized = query.trim()
    if (normalized.isBlank()) return this
    return filter { book ->
        book.displayName.contains(normalized, ignoreCase = true) ||
            book.title?.contains(normalized, ignoreCase = true) == true ||
            book.author?.contains(normalized, ignoreCase = true) == true ||
            book.sourceFolder?.contains(normalized, ignoreCase = true) == true ||
            book.tags.any { it.name.contains(normalized, ignoreCase = true) }
    }
}

@Composable
private fun SortOrder.sharedMobileLabel(): String {
    return when (this) {
        SortOrder.RECENT -> readerString("sort_recent", "Recent")
        SortOrder.DATE_ADDED_NEWEST -> readerString("sort_date_added_newest", "Newest")
        SortOrder.DATE_ADDED_OLDEST -> readerString("sort_date_added_oldest", "Oldest")
        SortOrder.TITLE_ASC -> readerString("sort_title_az", "Title A-Z")
        SortOrder.AUTHOR_ASC -> readerString("sort_author_az", "Author A-Z")
        SortOrder.PERCENT_ASC -> readerString("sort_percent_asc", "Percent complete 0–100")
        SortOrder.PERCENT_DESC -> readerString("sort_percent_desc", "Percent complete 100–0")
        SortOrder.SIZE_ASC -> readerString("sort_size_smallest", "Size (Smallest)")
        SortOrder.SIZE_DESC -> readerString("sort_size_biggest", "Size (Biggest)")
    }
}

@Composable
private fun ReadStatusFilter.sharedMobileLabel(): String {
    return when (this) {
        ReadStatusFilter.ALL -> readerString("read_status_all", "All")
        ReadStatusFilter.UNREAD -> readerString("read_status_unread", "Unread")
        ReadStatusFilter.IN_PROGRESS -> readerString("read_status_in_progress", "In Progress")
        ReadStatusFilter.COMPLETED -> readerString("read_status_completed", "Completed")
    }
}
val LocalUsePdfFileNameAsDisplayName = staticCompositionLocalOf { false }
