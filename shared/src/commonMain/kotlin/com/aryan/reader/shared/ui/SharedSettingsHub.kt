package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fonts
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BuiltInPdfReaderThemes
import com.aryan.reader.shared.CustomFontItem
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.SharedSettingsAction
import com.aryan.reader.shared.SharedSettingsCategoryModel
import com.aryan.reader.shared.SharedSettingsDestination
import com.aryan.reader.shared.SharedSettingsHubModel
import com.aryan.reader.shared.SharedSettingsItemGroup
import com.aryan.reader.shared.SharedSettingsItemKind
import com.aryan.reader.shared.SharedSettingsItemModel
import com.aryan.reader.shared.SharedSettingsPageKind
import com.aryan.reader.shared.SharedSettingsPageModel
import com.aryan.reader.shared.SharedSettingsSearchResult
import com.aryan.reader.shared.SharedText
import com.aryan.reader.shared.navigationDepth
import com.aryan.reader.shared.parentDestination
import com.aryan.reader.shared.reader.ReaderSettings
import com.aryan.reader.shared.ui.SharedSettingsTokens.ChevronSize
import com.aryan.reader.shared.ui.SharedSettingsTokens.CompactContentMaxWidth
import com.aryan.reader.shared.ui.SharedSettingsTokens.DividerInset
import com.aryan.reader.shared.ui.SharedSettingsTokens.ExpandedBreakpoint
import com.aryan.reader.shared.ui.SharedSettingsTokens.IconSize
import com.aryan.reader.shared.ui.SharedSettingsTokens.NavigationPaneWidth
import com.aryan.reader.shared.ui.SharedSettingsTokens.RowHorizontalPadding
import com.aryan.reader.shared.ui.SharedSettingsTokens.RowMinHeight
import com.aryan.reader.shared.ui.SharedSettingsTokens.RowVerticalPadding
import com.aryan.reader.shared.ui.SharedSettingsTokens.ScreenGutterCompact
import com.aryan.reader.shared.ui.SharedSettingsTokens.ScreenGutterExpanded
import com.aryan.reader.shared.ui.SharedSettingsTokens.SpaceLg
import com.aryan.reader.shared.ui.SharedSettingsTokens.SpaceSm
import com.aryan.reader.shared.ui.SharedSettingsTokens.SpaceXl
import com.aryan.reader.shared.ui.SharedSettingsTokens.SpaceXxs
import com.aryan.reader.shared.ui.SharedSettingsTokens.SpaceXs
import com.aryan.reader.shared.ui.SharedSettingsTokens.TwoPaneMaxWidth

/**
 * The settings hub.
 *
 * Layout contract:
 * - compact (< 600dp): one column, app-bar-owned title, docked search above the list.
 * - medium (600-839dp): one column, bounded to [CompactContentMaxWidth].
 * - expanded (>= 840dp): two panes. Categories on the left, the selected page on the right, so
 *   widening the window adds navigation context instead of longer rows.
 *
 * The renderer is the only place that decides an affordance: a row's [SharedSettingsItemKind] picks
 * the trailing control and the click semantics, so a switch can never sit inside a navigating row
 * and a destructive row can never look like navigation.
 */
@Composable
fun SharedSettingsHub(
    model: SharedSettingsHubModel,
    query: String,
    onQueryChange: (String) -> Unit,
    readerDefaultSettings: ReaderSettings,
    onReaderDefaultSettingsChange: (ReaderSettings) -> Unit,
    pdfReaderDefaultSettings: ReaderSettings = readerDefaultSettings,
    onPdfReaderDefaultSettingsChange: (ReaderSettings) -> Unit = onReaderDefaultSettingsChange,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onAction: (SharedSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    readerToolbarPreferences: ReaderToolbarPreferences? = null,
    onReaderToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit = {},
    customFonts: List<CustomFontItem> = emptyList(),
    onPickCustomFont: (() -> String?)? = null,
    customReaderThemes: List<ReaderTheme> = emptyList(),
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit = {},
    readerCustomTextureIds: List<String> = emptyList(),
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)? = null,
    onBack: (() -> Unit)? = null,
    destination: SharedSettingsDestination = SharedSettingsDestination.ROOT,
    onDestinationChange: (SharedSettingsDestination) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reduceMotion: Boolean = false
) {
    val page = remember(model, destination) { model.page(destination) }
    val searchResults = remember(model, query) { model.searchResults(query) }

    fun navigateTo(next: SharedSettingsDestination) {
        onQueryChange("")
        onDestinationChange(next)
    }

    fun navigateUp() {
        if (query.isNotBlank()) {
            onQueryChange("")
            return
        }
        val parent = destination.parentDestination()
        if (parent != null) {
            onDestinationChange(parent)
        } else {
            onBack?.invoke()
        }
    }

    // Built once and shared by both layouts so the compact column and the expanded detail pane can
    // never diverge in wiring. It takes the page it renders because a page swap keeps the outgoing
    // page composed while it animates out, and that page must render its own controls, not the
    // incoming page's.
    val detailContent: @Composable (SharedSettingsPageModel) -> Unit = { detailPage ->
        SettingsDetailPage(
            page = detailPage,
            settings = readerDefaultSettings,
            onSettingsChange = onReaderDefaultSettingsChange,
            pdfSettings = pdfReaderDefaultSettings,
            onPdfSettingsChange = onPdfReaderDefaultSettingsChange,
            toolbarPreferences = readerToolbarPreferences,
            onToolbarPreferencesChange = onReaderToolbarPreferencesChange,
            ttsReplacementPreferences = ttsReplacementPreferences,
            onTtsReplacementPreferencesChange = onTtsReplacementPreferencesChange,
            customFonts = customFonts,
            onPickCustomFont = onPickCustomFont,
            customReaderThemes = customReaderThemes,
            onCustomReaderThemesChange = onCustomReaderThemesChange,
            readerCustomTextureIds = readerCustomTextureIds,
            onImportReaderTexture = onImportReaderTexture
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        val expanded = maxWidth >= ExpandedBreakpoint
        val gutter = if (expanded) ScreenGutterExpanded else ScreenGutterCompact

        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .widthIn(max = TwoPaneMaxWidth)
            ) {
                SettingsNavigationPane(
                    categories = model.rootCategories,
                    destination = destination,
                    query = query,
                    onQueryChange = onQueryChange,
                    onNavigate = ::navigateTo,
                    modifier = Modifier
                        .width(NavigationPaneWidth)
                        .fillMaxHeight()
                        .padding(vertical = SpaceXl)
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    SettingsPageSwap(
                        model = model,
                        destination = page.destination,
                        searching = query.isNotBlank(),
                        reduceMotion = reduceMotion,
                        modifier = Modifier.fillMaxSize()
                    ) { state, statePage ->
                        if (!state.searching && statePage.kind == SharedSettingsPageKind.ROOT) {
                            // AnimatedContent is not a Box, so the placeholder gets its own Box
                            // instead of relying on the enclosing pane's BoxScope alignment.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                SettingsSelectCategoryPlaceholder(Modifier)
                            }
                        } else {
                            SettingsPageBody(
                                page = statePage,
                                searching = state.searching,
                                query = query,
                                onQueryChange = onQueryChange,
                                searchResults = searchResults,
                                onNavigate = ::navigateTo,
                                onAction = onAction,
                                contentGutter = gutter,
                                detailContent = detailContent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        } else {
            // The docked search sits above the scrolling list instead of inside it: a search bar
            // that scrolls away with the first fling is not a docked bar.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .widthIn(max = CompactContentMaxWidth)
            ) {
                SettingsSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = gutter, end = gutter, top = SpaceXl, bottom = SpaceSm)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = gutter, end = gutter, bottom = SpaceXl),
                    verticalArrangement = Arrangement.spacedBy(SpaceSm)
                ) {
                    // One item, not four: the page body is the thing that animates, so it has to be
                    // a single composable node that AnimatedContent can hold on screen while it
                    // exits.
                    item(key = "page") {
                        SettingsPageSwap(
                            model = model,
                            destination = page.destination,
                            searching = query.isNotBlank(),
                            reduceMotion = reduceMotion,
                            modifier = Modifier.fillMaxWidth()
                        ) { state, statePage ->
                            Column(verticalArrangement = Arrangement.spacedBy(SpaceSm)) {
                                if (!state.searching && statePage.kind != SharedSettingsPageKind.ROOT) {
                                    SettingsPageIntro(
                                        page = statePage,
                                        modifier = Modifier.padding(bottom = SpaceSm)
                                    )
                                }
                                when {
                                    state.searching -> SettingsSearchResults(
                                        results = searchResults,
                                        query = query,
                                        onNavigate = ::navigateTo,
                                        onQueryChange = onQueryChange
                                    )
                                    statePage.kind == SharedSettingsPageKind.ROOT -> SettingsGroupContainer {
                                        statePage.categories.forEachIndexed { index, category ->
                                            SettingsCategoryRow(
                                                category = category,
                                                onNavigate = ::navigateTo
                                            )
                                            SettingsRowDivider(visible = index != statePage.categories.lastIndex)
                                        }
                                    }
                                    statePage.kind == SharedSettingsPageKind.CATEGORY -> SettingsCategoryPage(
                                        page = statePage,
                                        onAction = onAction,
                                        onNavigate = ::navigateTo
                                    )
                                    else -> detailContent(statePage)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expanded-window body. Reuses the same pieces as the compact column so both layouts can never
 * drift apart in content or ordering.
 */
@Composable
private fun SettingsPageBody(
    page: SharedSettingsPageModel,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<SharedSettingsSearchResult>,
    onNavigate: (SharedSettingsDestination) -> Unit,
    onAction: (SharedSettingsAction) -> Unit,
    contentGutter: androidx.compose.ui.unit.Dp,
    detailContent: @Composable (SharedSettingsPageModel) -> Unit,
    modifier: Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = contentGutter,
            end = contentGutter,
            top = SpaceXl,
            bottom = SpaceXl
        ),
        verticalArrangement = Arrangement.spacedBy(SpaceSm)
    ) {
        if (!searching && page.kind != SharedSettingsPageKind.ROOT) {
            item(key = "intro") {
                SettingsPageIntro(page = page, modifier = Modifier.padding(bottom = SpaceSm))
            }
        }
        when {
            searching -> item(key = "results") {
                SettingsSearchResults(
                    results = searchResults,
                    query = query,
                    onNavigate = onNavigate,
                    onQueryChange = onQueryChange
                )
            }
            page.kind == SharedSettingsPageKind.CATEGORY -> item(key = "page") {
                SettingsCategoryPage(page = page, onAction = onAction, onNavigate = onNavigate)
            }
            page.kind == SharedSettingsPageKind.DETAIL -> item(key = "detail") {
                detailContent(page)
            }
        }
    }
}

/**
 * What the hub is showing, as the animation sees it.
 *
 * [searching] is part of the identity because the page and the result list are unrelated states:
 * moving between them must not slide, or the search list would look like a child of the page.
 */
private data class SharedSettingsPageKey(
    val destination: SharedSettingsDestination,
    val searching: Boolean
) {
    val depth: Int get() = destination.navigationDepth()
}

/**
 * Holds the outgoing page on screen while the incoming one arrives.
 *
 * Both layouts render through this so the compact column and the expanded pane can never animate
 * differently. Direction comes from [SharedSettingsDestination.navigationDepth]: deeper slides
 * forward, shallower slides back, and search changes fade through because they are lateral.
 */
@Composable
private fun SettingsPageSwap(
    model: SharedSettingsHubModel,
    destination: SharedSettingsDestination,
    searching: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (SharedSettingsPageKey, SharedSettingsPageModel) -> Unit
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val key = remember(destination, searching) { SharedSettingsPageKey(destination, searching) }

    AnimatedContent(
        targetState = key,
        modifier = modifier,
        transitionSpec = {
            when {
                reduceMotion -> sharedReducedMotionSwap()
                targetState.searching != initialState.searching -> sharedFadeThrough()
                // Equal depth counts as forward: two sibling pages are not a step back.
                else -> sharedAxisSlide(rtl = rtl, forward = targetState.depth >= initialState.depth)
            }
        },
        label = "settings-page"
    ) { state ->
        val statePage = remember(model, state.destination) { model.page(state.destination) }
        content(state, statePage)
    }
}

@Composable
private fun SettingsNavigationPane(
    categories: List<SharedSettingsCategoryModel>,
    destination: SharedSettingsDestination,
    query: String,
    onQueryChange: (String) -> Unit,
    onNavigate: (SharedSettingsDestination) -> Unit,
    modifier: Modifier
) {
    Column(modifier = modifier.padding(horizontal = SpaceXl)) {
        SettingsSearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(bottom = SpaceXl)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SpaceXs)
        ) {
            item(key = "settings-root") {
                SettingsNavRow(
                    icon = Icons.Default.Settings,
                    title = readerString("settings_hub_root_title", "Settings"),
                    selected = destination == SharedSettingsDestination.ROOT,
                    onClick = { onNavigate(SharedSettingsDestination.ROOT) }
                )
            }
            items(categories.size) { index ->
                val category = categories[index]
                SettingsNavRow(
                    icon = category.destination.iconForSettingsDestination(),
                    title = readerSharedText(category.title),
                    selected = destination == category.destination ||
                        destination.parentDestination() == category.destination,
                    onClick = { onNavigate(category.destination) }
                )
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // The selected category is the only thing telling the user which page they are on in the wide
    // layout. NavigationDrawerItem owns the M3 navigation-drawer treatment (56dp row, full-corner
    // active indicator, secondaryContainer/onSecondaryContainer pairing) and animates the indicator
    // itself, so the hub no longer paints selection by hand.
    NavigationDrawerItem(
        label = { Text(title, style = MaterialTheme.typography.titleSmall) },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(SharedSettingsTokens.IconSize)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun SettingsSelectCategoryPlaceholder(modifier: Modifier) {
    Column(
        modifier = modifier.padding(SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSm)
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            readerString("settings_select_category", "Select a category to see its settings"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SharedSearchField(
        query = query,
        onQueryChange = onQueryChange,
        placeholder = readerString("desktop_search_settings", "Search settings"),
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(tween(SharedMotion.DurationShort4, easing = SharedMotion.StandardDecelerate)) +
                    expandHorizontally(tween(SharedMotion.DurationShort4, easing = SharedMotion.StandardDecelerate)),
                exit = fadeOut(tween(SharedMotion.DurationShort2, easing = SharedMotion.StandardAccelerate)) +
                    shrinkHorizontally(tween(SharedMotion.DurationShort2, easing = SharedMotion.StandardAccelerate))
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = readerString("action_clear_search", "Clear search")
                    )
                }
            }
        }
    )
}

/**
 * Page context under the app bar. The title stays in the host's app bar, so repeating it here would
 * make every screen announce itself twice; the summary is what the hub owns.
 */
@Composable
private fun SettingsPageIntro(page: SharedSettingsPageModel, modifier: Modifier = Modifier) {
    if (page.summary.fallback.isBlank()) return
    Text(
        text = readerSharedText(page.summary),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Category page: regular rows grouped by section, then one trailing group for destructive actions.
 * Splitting destructive rows out is structural, not cosmetic: they lose the chevron, keep the error
 * label, and sit away from routine navigation so they are hard to hit by muscle memory.
 */
@Composable
private fun SettingsCategoryPage(
    page: SharedSettingsPageModel,
    onAction: (SharedSettingsAction) -> Unit,
    onNavigate: (SharedSettingsDestination) -> Unit
) {
    val regularGroups = page.groups
        .map { group -> group.copy(items = group.items.filterNot { it.kind == SharedSettingsItemKind.DESTRUCTIVE }) }
        .filter { it.items.isNotEmpty() }
    val destructiveItems = page.items.filter { it.kind == SharedSettingsItemKind.DESTRUCTIVE }

    Column(verticalArrangement = Arrangement.spacedBy(SpaceXl)) {
        page.localOverrideNote?.let { note ->
            SettingsInlineNote(note)
        }

        regularGroups.forEach { group ->
            SettingsSection(group = group, onAction = onAction, onNavigate = onNavigate)
        }

        if (destructiveItems.isNotEmpty()) {
            SettingsGroupContainer {
                destructiveItems.forEachIndexed { index, item ->
                    SettingsRow(item = item, onClick = { onAction(item.action) })
                    SettingsRowDivider(visible = index != destructiveItems.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    group: SharedSettingsItemGroup,
    onAction: (SharedSettingsAction) -> Unit,
    onNavigate: (SharedSettingsDestination) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpaceSm)) {
        group.header?.let { header ->
            Text(
                text = readerSharedText(header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = SpaceXs)
            )
        }
        SettingsGroupContainer {
            group.items.forEachIndexed { index, item ->
                SettingsRow(
                    item = item,
                    // A row that declares a destination navigates; only control, toggle, and
                    // destructive rows run an action. Dispatching the action first is what made
                    // every category row look dead: the hosts map navigation actions to Unit.
                    onClick = { item.destination?.let(onNavigate) ?: onAction(item.action) }
                )
                SettingsRowDivider(visible = index != group.items.lastIndex)
            }
        }
    }
}

@Composable
private fun SettingsGroupContainer(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SharedSettingsTokens.groupShape(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(content = { content() })
    }
}

@Composable
private fun SettingsRowDivider(visible: Boolean) {
    if (!visible) return
    HorizontalDivider(
        modifier = Modifier.padding(start = DividerInset),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun SettingsRow(
    item: SharedSettingsItemModel,
    onClick: () -> Unit
) {
    val isToggle = item.kind == SharedSettingsItemKind.TOGGLE
    val isDestructive = item.kind == SharedSettingsItemKind.DESTRUCTIVE
    val interactive = item.kind != SharedSettingsItemKind.INFO && item.enabled

    val interaction = when {
        isToggle -> Modifier.toggleable(
            value = item.checked == true,
            enabled = item.enabled,
            role = Role.Switch,
            onValueChange = { onClick() }
        )
        item.kind == SharedSettingsItemKind.INFO -> Modifier
        else -> Modifier.clickable(
            enabled = interactive,
            role = Role.Button,
            onClick = onClick
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(interaction)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceLg)
    ) {
        Icon(
            imageVector = item.action.iconForSettings(),
            contentDescription = null,
            modifier = Modifier.size(IconSize),
            tint = when {
                isDestructive -> MaterialTheme.colorScheme.error
                item.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            }
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SpaceXxs)
        ) {
            Text(
                text = readerSharedText(item.title),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isDestructive -> MaterialTheme.colorScheme.error
                    item.enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            Text(
                text = readerSharedText(item.summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (item.value != null) {
            // The host owns this text ("Literata · 100% · Justified"), so it changes underneath the
            // row while the user is elsewhere. Crossfading it keeps the row from appearing to
            // re-flow when they come back.
            Crossfade(
                targetState = item.value,
                animationSpec = tween(SharedMotion.DurationShort4, easing = SharedMotion.Standard),
                label = "settings-row-value"
            ) { value ->
                Text(
                    text = value.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        when (item.kind) {
            SharedSettingsItemKind.TOGGLE -> Switch(
                checked = item.checked == true,
                onCheckedChange = null,
                enabled = item.enabled
            )
            SharedSettingsItemKind.NAVIGATION -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SharedSettingsItemKind.CONTROL,
            SharedSettingsItemKind.DESTRUCTIVE,
            SharedSettingsItemKind.INFO -> Unit
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    category: SharedSettingsCategoryModel,
    onNavigate: (SharedSettingsDestination) -> Unit
) {
    Surface(
        onClick = { onNavigate(category.destination) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = RowMinHeight)
                .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceLg)
        ) {
            Icon(
                imageVector = category.destination.iconForSettingsDestination(),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXxs)
            ) {
                Text(
                    text = readerSharedText(category.title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = readerSharedText(category.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSearchResults(
    results: List<SharedSettingsSearchResult>,
    query: String,
    onNavigate: (SharedSettingsDestination) -> Unit,
    onQueryChange: (String) -> Unit
) {
    if (results.isEmpty()) {
        SettingsEmptySearch(query = query, onQueryChange = onQueryChange)
        return
    }

    SettingsGroupContainer {
        results.forEachIndexed { index, result ->
            SettingsSearchResultRow(result = result, onNavigate = onNavigate)
            SettingsRowDivider(visible = index != results.lastIndex)
        }
    }
}

@Composable
private fun SettingsSearchResultRow(
    result: SharedSettingsSearchResult,
    onNavigate: (SharedSettingsDestination) -> Unit
) {
    val stringResolver = LocalSharedStringResolver.current
    val localizedBreadcrumb = result.breadcrumb.joinToString(" / ") { stringResolver.sharedText(it) }

    Surface(
        onClick = { onNavigate(result.destination) },
        color = Color.Transparent,
        enabled = result.enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = RowMinHeight)
                .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpaceLg)
        ) {
            Icon(
                imageVector = result.destination.iconForSettingsDestination(),
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = if (result.kind == SharedSettingsItemKind.DESTRUCTIVE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpaceXxs)
            ) {
                Text(
                    text = readerSharedText(result.title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = localizedBreadcrumb,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = readerSharedText(result.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(ChevronSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsEmptySearch(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SpaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpaceSm)
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = readerString("desktop_no_settings_found", "No settings found"),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(SpaceSm),
            modifier = Modifier.padding(top = SpaceXs)
        ) {
            listOf(
                "theme" to readerString("settings_search_suggestion_theme", "theme"),
                "font" to readerString("settings_search_suggestion_font", "font"),
                "sync" to readerString("settings_search_suggestion_sync", "sync")
            ).forEach { (rawQuery, label) ->
                SuggestionChip(
                    onClick = { onQueryChange(rawQuery) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun SettingsInlineNote(note: SharedSettingsItemModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpaceXs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SpaceSm)
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
            Text(
                text = readerSharedText(note.title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = readerSharedText(note.summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsDetailPage(
    page: SharedSettingsPageModel,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    pdfSettings: ReaderSettings,
    onPdfSettingsChange: (ReaderSettings) -> Unit,
    toolbarPreferences: ReaderToolbarPreferences?,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    customFonts: List<CustomFontItem>,
    onPickCustomFont: (() -> String?)?,
    customReaderThemes: List<ReaderTheme>,
    onCustomReaderThemesChange: (List<ReaderTheme>) -> Unit,
    readerCustomTextureIds: List<String>,
    onImportReaderTexture: ((ReaderSettings) -> ReaderSettings?)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(SpaceXl)) {
        page.localOverrideNote?.let { note -> SettingsInlineNote(note) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SharedSettingsTokens.groupShape(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(SpaceLg),
                verticalArrangement = Arrangement.spacedBy(SpaceLg)
            ) {
                when (page.destination) {
                    SharedSettingsDestination.EPUB_FORMAT -> {
                        SharedReaderFormatControls(
                            settings = settings,
                            onPickCustomFont = onPickCustomFont,
                            customFonts = customFonts,
                            onReaderAction = { action ->
                                if (action is ReaderAction.SettingsChanged) onSettingsChange(action.settings)
                            }
                        )
                    }
                    SharedSettingsDestination.EPUB_THEME_TEXTURE -> {
                        SharedReaderThemeControls(
                            settings = settings,
                            customThemes = customReaderThemes,
                            onCustomThemesChange = onCustomReaderThemesChange,
                            customTextureIds = readerCustomTextureIds,
                            onImportTexture = onImportReaderTexture,
                            onSettingsChange = onSettingsChange
                        )
                    }
                    SharedSettingsDestination.EPUB_VISUAL_DEFAULTS -> {
                        SharedReaderVisualOptionsControls(
                            settings = settings,
                            onReaderAction = { action ->
                                if (action is ReaderAction.SettingsChanged) onSettingsChange(action.settings)
                            }
                        )
                    }
                    SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS -> {
                        Column(verticalArrangement = Arrangement.spacedBy(SpaceLg)) {
                            Text(
                                readerString("desktop_fixed_layout_appearance", "Fixed-layout appearance"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                readerString(
                                    "desktop_pdf_appearance_defaults_desc",
                                    "These defaults apply where the platform supports shared PDF appearance. Per-book PDF overrides stay in the PDF reader."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SharedReaderThemeControls(
                                settings = pdfSettings,
                                builtInThemes = BuiltInPdfReaderThemes,
                                customThemes = customReaderThemes,
                                onCustomThemesChange = onCustomReaderThemesChange,
                                customTextureIds = readerCustomTextureIds,
                                onImportTexture = onImportReaderTexture,
                                onSettingsChange = onPdfSettingsChange
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                readerString("visual_options_title", "Visual options"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            SettingsToggleLine(
                                title = readerString("menu_right_to_left_pagination", "Paginated (right-to-left)"),
                                summary = readerString(
                                    "visual_options_right_to_left_pagination_desc",
                                    "Uses right-to-left page order when PDF pagination mode is active."
                                ),
                                checked = pdfSettings.rightToLeftPagination,
                                onCheckedChange = { enabled ->
                                    onPdfSettingsChange(pdfSettings.copy(rightToLeftPagination = enabled))
                                }
                            )
                            SettingsToggleLine(
                                title = readerString("visual_options_remove_page_gap", "Remove gap between pages"),
                                summary = readerString(
                                    "desktop_remove_gap_between_pages_desc",
                                    "Applies to vertical reading and two-page spreads."
                                ),
                                checked = !pdfSettings.pdfVerticalPageGapVisible,
                                onCheckedChange = { removeGap ->
                                    onPdfSettingsChange(pdfSettings.copy(pdfVerticalPageGapVisible = !removeGap))
                                }
                            )
                            SettingsToggleLine(
                                title = readerString("visual_options_hide_page_number_overlay", "Hide page number overlay"),
                                summary = readerString(
                                    "visual_options_hide_page_number_overlay_desc",
                                    "Removes the small page count label from each page."
                                ),
                                checked = !pdfSettings.pdfPageNumberOverlayVisible,
                                onCheckedChange = { hideOverlay ->
                                    onPdfSettingsChange(pdfSettings.copy(pdfPageNumberOverlayVisible = !hideOverlay))
                                }
                            )
                        }
                    }
                    SharedSettingsDestination.READER_TOOLBAR_DEFAULTS -> {
                        if (toolbarPreferences == null) {
                            Text(
                                readerString(
                                    "desktop_reader_toolbar_managed_in_reader",
                                    "Reader toolbar defaults are managed from the reader on this platform."
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            SharedReaderToolbarControls(
                                toolbarPreferences = toolbarPreferences,
                                onToolbarPreferencesChange = onToolbarPreferencesChange
                            )
                        }
                    }
                    SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> {
                        SharedReaderTtsReplacementControls(
                            preferences = ttsReplacementPreferences,
                            bookId = "global",
                            onPreferencesChange = onTtsReplacementPreferencesChange,
                            allowBookScope = false
                        )
                    }
                    else -> Text(
                        readerSharedText(page.summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleLine(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpaceLg)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SpaceXxs)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun SharedSettingsDestination.iconForSettingsDestination(): ImageVector {
    return when (this) {
        SharedSettingsDestination.EPUB_TEXT -> Icons.AutoMirrored.Filled.MenuBook
        SharedSettingsDestination.PDF_COMICS -> Icons.Default.Description
        SharedSettingsDestination.APP_LIBRARY -> Icons.Default.Palette
        SharedSettingsDestination.TTS_AI -> Icons.Default.GraphicEq
        SharedSettingsDestination.SYNC_ACCOUNT -> Icons.Default.Sync
        SharedSettingsDestination.STORAGE_PRIVACY -> Icons.Default.Policy
        SharedSettingsDestination.HELP_ABOUT -> Icons.Default.Info
        SharedSettingsDestination.ROOT -> Icons.Default.Settings
        SharedSettingsDestination.EPUB_FORMAT,
        SharedSettingsDestination.EPUB_THEME_TEXTURE,
        SharedSettingsDestination.EPUB_VISUAL_DEFAULTS,
        SharedSettingsDestination.PDF_APPEARANCE_DEFAULTS,
        SharedSettingsDestination.READER_TOOLBAR_DEFAULTS,
        SharedSettingsDestination.GLOBAL_TTS_REPLACEMENTS -> Icons.Default.Tune
    }
}

private fun SharedSettingsAction.iconForSettings(): ImageVector {
    return when (this) {
        SharedSettingsAction.TEXT_READER_DEFAULTS -> Icons.Default.Fonts
        SharedSettingsAction.PDF_READER_DEFAULTS -> Icons.Default.Description
        SharedSettingsAction.READER_TOOLBAR -> Icons.Default.Tune
        SharedSettingsAction.TTS_REPLACEMENTS -> Icons.Default.TextFields
        SharedSettingsAction.LOCAL_OVERRIDE_NOTE -> Icons.Default.Info
        SharedSettingsAction.APP_THEME -> Icons.Default.Palette
        SharedSettingsAction.LANGUAGE -> Icons.Default.Translate
        SharedSettingsAction.TABS_TOGGLE -> Icons.Default.Book
        SharedSettingsAction.STRICT_FILE_FILTER -> Icons.Default.FilterList
        SharedSettingsAction.PDF_FILENAME_DISPLAY_NAME -> Icons.Default.Description
        SharedSettingsAction.EXTERNAL_FILE_BEHAVIOR -> Icons.Default.FileOpen
        SharedSettingsAction.SCREEN_CAPTURE_PROTECTION -> Icons.Default.VisibilityOff
        SharedSettingsAction.CUSTOM_FONTS -> Icons.Default.Fonts
        SharedSettingsAction.SIGN_IN -> Icons.Default.AccountCircle
        SharedSettingsAction.SIGN_OUT -> Icons.Default.Logout
        SharedSettingsAction.DELETE_ACCOUNT -> Icons.Default.Delete
        SharedSettingsAction.CLOUD_SYNC -> Icons.Default.Cloud
        SharedSettingsAction.FOLDER_SYNC -> Icons.Default.Folder
        SharedSettingsAction.DEVICE_MANAGEMENT -> Icons.Default.Smartphone
        SharedSettingsAction.AI_SETTINGS -> Icons.Default.Psychology
        SharedSettingsAction.HIDE_READER_AI -> Icons.Default.Psychology
        SharedSettingsAction.TTS_SETTINGS -> Icons.Default.GraphicEq
        SharedSettingsAction.CLEAR_BOOK_CACHE -> Icons.Default.Delete
        SharedSettingsAction.CLEAR_REFLOW_CACHE -> Icons.Default.Delete
        SharedSettingsAction.CLEAR_CLOUD_LOCAL_DATA -> Icons.Default.Delete
        SharedSettingsAction.TEST_PANEL_DETECTION -> Icons.Default.BugReport
        SharedSettingsAction.TEST_SPEECH_BUBBLE_DETECTION -> Icons.Default.BugReport
        SharedSettingsAction.EXPORT_LOGS -> Icons.Default.Share
        SharedSettingsAction.FPS_OVERLAY -> Icons.Default.Tune
        SharedSettingsAction.HELP_FEEDBACK -> Icons.Default.Feedback
        SharedSettingsAction.SUPPORT -> Icons.Default.Favorite
        SharedSettingsAction.ABOUT -> Icons.Default.Info
    }
}
