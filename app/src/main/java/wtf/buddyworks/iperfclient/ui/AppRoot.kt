package wtf.buddyworks.iperfclient.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import wtf.buddyworks.iperfclient.R
import wtf.buddyworks.iperfclient.data.TestRecord
import wtf.buddyworks.iperfclient.iperfApp
import wtf.buddyworks.iperfclient.ui.components.CardMargin
import wtf.buddyworks.iperfclient.ui.components.FloatingTabBar
import wtf.buddyworks.iperfclient.ui.components.FloatingTabBarHeight
import wtf.buddyworks.iperfclient.ui.components.NavItem
import wtf.buddyworks.iperfclient.ui.components.NavRail
import wtf.buddyworks.iperfclient.ui.components.RailWidth
import wtf.buddyworks.iperfclient.ui.components.Toolbar
import wtf.buddyworks.iperfclient.ui.components.ToolbarIcon
import wtf.buddyworks.iperfclient.ui.history.HistoryDetail
import wtf.buddyworks.iperfclient.ui.history.HistoryListPane
import wtf.buddyworks.iperfclient.ui.servers.OwnServerHelp
import wtf.buddyworks.iperfclient.ui.servers.ServersPane
import wtf.buddyworks.iperfclient.ui.test.ChartCard
import wtf.buddyworks.iperfclient.ui.test.ErrorCard
import wtf.buddyworks.iperfclient.ui.test.OptionsCard
import wtf.buddyworks.iperfclient.ui.test.ResultTiles
import wtf.buddyworks.iperfclient.ui.test.ServerCard
import wtf.buddyworks.iperfclient.ui.test.SpeedTestViewModel
import wtf.buddyworks.iperfclient.ui.test.TestEffects
import wtf.buddyworks.iperfclient.ui.test.TestHero
import wtf.buddyworks.iperfclient.ui.test.cardWidth
import wtf.buddyworks.iperfclient.ui.test.isPingMode
import wtf.buddyworks.iperfclient.ui.test.rememberStartAction
import wtf.buddyworks.iperfclient.ui.theme.OneUi

enum class Tab { Test, History, Servers }

/** How a list pane is placed on screen. */
data class PaneConfig(
    /** One UI large title in the top third (tall single-pane screens only). */
    val expandableTitle: Boolean,
    val topInset: Dp,
    val bottomPadding: Dp,
    /** True when a detail pane is shown next to the list. */
    val twoPane: Boolean,
)

/** Where the panes go, derived from window size and hinge position. */
private sealed interface Panes {
    data object Single : Panes

    /** Book posture or a wide window: left | right, split at the hinge when there is one. */
    data class SideBySide(val leftEnd: Dp, val rightStart: Dp) : Panes

    /** Tabletop posture: gauge above the fold, controls below it. */
    data class Stacked(val topBottom: Dp, val bottomTop: Dp) : Panes
}

private val PaneGap = 8.dp
private val OuterMargin = 12.dp

@Composable
fun AppRoot() {
    val app = LocalContext.current.iperfApp
    val store = app.store
    val vm = viewModel { SpeedTestViewModel(app) }
    val servers by store.servers.collectAsStateWithLifecycle()
    val selectedId by store.selectedId.collectAsStateWithLifecycle()
    val options by store.options.collectAsStateWithLifecycle()
    val history by store.history.collectAsStateWithLifecycle()
    val connection by app.connection.collectAsStateWithLifecycle()
    val selectedServer = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()

    var tab by rememberSaveable { mutableStateOf(Tab.Test) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    TestEffects(vm)
    val startTest = rememberStartAction(vm, selectedServer)

    val fold = rememberFoldingFeature()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val stateHolder = rememberSaveableStateHolder()

    val navItems = listOf(
        NavItem(stringResource(R.string.nav_test), Icons.Outlined.Speed, Icons.Filled.Speed),
        NavItem(stringResource(R.string.nav_history), Icons.Outlined.History, Icons.Filled.History),
        NavItem(stringResource(R.string.nav_servers), Icons.Outlined.Dns, Icons.Filled.Dns),
    )
    val openServers = { tab = Tab.Servers }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(OneUi.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        val useRail = maxWidth >= 600.dp
        val railWidth = if (useRail) RailWidth else 0.dp
        val panes = computePanes(maxWidth, maxHeight, railWidth, fold, density)
        val single = panes is Panes.Single
        val tall = maxHeight >= 560.dp
        val fullWidth = maxWidth
        val tabBar = !useRail
        val bottomPadding = bottomInset + if (tabBar) FloatingTabBarHeight + 28.dp else 16.dp

        val rail: @Composable () -> Unit = {
            if (useRail) NavRail(navItems, tab.ordinal, { tab = Tab.entries[it]; detailOpen = false }, topInset)
        }

        @Composable
        fun controls(includeTiles: Boolean, modifier: Modifier) {
            // A new error card appears at the top: bring it into view.
            val scroll = rememberScrollState()
            LaunchedEffect(vm.error) { if (vm.error != null) scroll.animateScrollTo(0) }
            Column(
                modifier.verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val error = vm.error
                if (error != null) {
                    ErrorCard(error, vm::dismissError, { vm.dismissError(); openServers() }, Modifier.cardWidth())
                }
                if (includeTiles) ResultTiles(vm, vm.isPingMode(options.mode), Modifier.cardWidth())
                ServerCard(selectedServer, connection, openServers, enabled = !vm.running, modifier = Modifier.cardWidth())
                ChartCard(vm, vm.isPingMode(options.mode), Modifier.cardWidth())
                OptionsCard(options, enabled = !vm.running, onChange = store::setOptions, modifier = Modifier.cardWidth())
            }
        }

        @Composable
        fun testContent() {
            when (panes) {
                Panes.Single -> Column(Modifier.fillMaxSize()) {
                    Toolbar(stringResource(R.string.nav_test), topInset)
                    val scroll = rememberScrollState()
                    LaunchedEffect(vm.error) { if (vm.error != null) scroll.animateScrollTo(0) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll)
                            .padding(start = CardMargin, end = CardMargin, bottom = bottomPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val heroWidth = (fullWidth - CardMargin * 2).coerceAtMost(520.dp)
                        PaneCard(Modifier.width(heroWidth).height(heroWidth * 0.9f + 150.dp)) {
                            TestHero(vm, connection, startTest, Modifier.fillMaxSize(), idleMode = options.mode)
                        }
                        val error = vm.error
                        if (error != null) ErrorCard(error, vm::dismissError, { vm.dismissError(); openServers() })
                        ServerCard(selectedServer, connection, openServers, enabled = !vm.running)
                        ChartCard(vm, vm.isPingMode(options.mode))
                        OptionsCard(options, enabled = !vm.running, onChange = store::setOptions)
                    }
                }

                is Panes.SideBySide -> Row(Modifier.fillMaxSize()) {
                    rail()
                    Column(
                        Modifier
                            .width(panes.leftEnd - railWidth)
                            .fillMaxHeight(),
                    ) {
                        Toolbar(stringResource(R.string.nav_test), topInset)
                        PaneCard(
                            Modifier
                                .fillMaxSize()
                                .padding(start = if (useRail) 0.dp else OuterMargin, bottom = bottomInset + OuterMargin),
                        ) {
                            TestHero(vm, connection, startTest, Modifier.fillMaxSize(), idleMode = options.mode)
                        }
                    }
                    Spacer(Modifier.width(panes.rightStart - panes.leftEnd))
                    controls(
                        includeTiles = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = topInset + PaneGap, end = OuterMargin)
                            .padding(bottom = bottomInset + OuterMargin),
                    )
                }

                is Panes.Stacked -> Row(Modifier.fillMaxSize()) {
                    rail()
                    Column(Modifier.weight(1f)) {
                        PaneCard(
                            Modifier
                                .fillMaxWidth()
                                .height(panes.topBottom - PaneGap)
                                .padding(top = topInset + PaneGap, start = if (useRail) 0.dp else OuterMargin, end = OuterMargin),
                        ) {
                            TestHero(vm, connection, startTest, Modifier.fillMaxSize(), tiles = false, idleMode = options.mode)
                        }
                        Spacer(Modifier.height(panes.bottomTop - panes.topBottom + PaneGap * 2))
                        controls(
                            includeTiles = true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = if (useRail) 0.dp else OuterMargin, end = OuterMargin)
                                .padding(bottom = bottomPadding),
                        )
                    }
                }
            }
        }

        val selectRecord: (TestRecord) -> Unit = { detailId = it.id; detailOpen = true }
        val shownRecord = history.firstOrNull { it.id == detailId } ?: if (!single) history.firstOrNull() else null

        @Composable
        fun historyContent() {
            if (panes is Panes.SideBySide) {
                Row(Modifier.fillMaxSize()) {
                    rail()
                    HistoryListPane(
                        history, shownRecord?.id, selectRecord, store::clearHistory,
                        PaneConfig(false, topInset, bottomInset + 16.dp, twoPane = true),
                        Modifier
                            .width(panes.leftEnd - railWidth)
                            .fillMaxHeight(),
                    )
                    Spacer(Modifier.width(panes.rightStart - panes.leftEnd))
                    PaneCard(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = topInset + PaneGap, bottom = bottomInset + OuterMargin, end = OuterMargin),
                    ) {
                        Crossfade(shownRecord, animationSpec = tween(220), label = "detail") { record ->
                            if (record == null) {
                                Placeholder(stringResource(R.string.history_empty))
                            } else {
                                HistoryDetail(record, { store.deleteRecord(record.id) }, PaddingValues(vertical = 16.dp))
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    rail()
                    HistoryListPane(
                        history, null, selectRecord, store::clearHistory,
                        PaneConfig(expandableTitle = tall, topInset, bottomPadding, twoPane = false),
                        Modifier.weight(1f),
                    )
                }
            }
        }

        @Composable
        fun serversContent() {
            Row(Modifier.fillMaxSize()) {
                rail()
                val sideBySide = panes is Panes.SideBySide
                ServersPane(
                    servers = servers,
                    selectedId = selectedServer?.id,
                    onSelect = { store.select(it.id) },
                    onSave = { store.upsert(it) },
                    onDelete = { store.delete(it.id) },
                    onRestorePublic = store::restorePublic,
                    config = PaneConfig(
                        expandableTitle = !sideBySide && tall,
                        topInset = topInset,
                        bottomPadding = if (sideBySide) bottomInset + 16.dp else bottomPadding,
                        twoPane = sideBySide,
                    ),
                    showHelp = !sideBySide,
                    modifier = if (sideBySide) Modifier.width(panes.leftEnd - railWidth).fillMaxHeight() else Modifier.weight(1f),
                )
                if (panes is Panes.SideBySide) {
                    Spacer(Modifier.width(panes.rightStart - panes.leftEnd))
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(top = topInset + ToolbarGap, end = OuterMargin, bottom = bottomInset + OuterMargin),
                    ) {
                        OwnServerHelp()
                    }
                }
            }
        }

        Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { current ->
            stateHolder.SaveableStateProvider(current) {
                when (current) {
                    Tab.Test -> if (panes is Panes.Single) Row(Modifier.fillMaxSize()) { rail(); Box(Modifier.weight(1f)) { testContent() } } else testContent()
                    Tab.History -> historyContent()
                    Tab.Servers -> serversContent()
                }
            }
        }

        if (tabBar) {
            FloatingTabBar(
                navItems,
                tab.ordinal,
                { tab = Tab.entries[it]; detailOpen = false },
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 14.dp),
            )
        }

        // Single pane: a history entry opens as its own screen.
        val detailRecord = history.firstOrNull { it.id == detailId }
        AnimatedVisibility(
            visible = !(panes is Panes.SideBySide) && tab == Tab.History && detailOpen && detailRecord != null,
            enter = slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)),
        ) {
            val record = remember(detailId, history) { detailRecord } ?: return@AnimatedVisibility
            Column(
                Modifier
                    .fillMaxSize()
                    .background(OneUi.colors.background),
            ) {
                Toolbar(
                    title = "",
                    topInset = topInset,
                    navigationIcon = { ToolbarIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), { detailOpen = false }) },
                )
                PaneCard(
                    Modifier
                        .fillMaxSize()
                        .padding(start = OuterMargin, end = OuterMargin, bottom = bottomInset + OuterMargin),
                ) {
                    HistoryDetail(record, { store.deleteRecord(record.id); detailOpen = false }, PaddingValues(vertical = 16.dp))
                }
            }
        }
        BackHandler(enabled = detailOpen && tab == Tab.History && panes !is Panes.SideBySide) { detailOpen = false }
        BackHandler(enabled = tab != Tab.Test && !(detailOpen && tab == Tab.History && panes !is Panes.SideBySide)) { tab = Tab.Test }
    }
}

private val ToolbarGap = 64.dp

/** The big rounded card One UI uses for a pane. */
@Composable
fun PaneCard(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(OneUi.paneShape)
            .background(OneUi.colors.card),
    ) { content() }
}

@Composable
private fun Placeholder(text: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = OneUi.colors.textSection, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = OneUi.type.body, color = OneUi.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun rememberFoldingFeature(): FoldingFeature? {
    val activity = LocalActivity.current ?: return null
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info: WindowLayoutInfo? by flow.collectAsStateWithLifecycle(initialValue = null)
    return info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
}

private fun computePanes(width: Dp, height: Dp, railWidth: Dp, fold: FoldingFeature?, density: Density): Panes {
    if (fold != null && fold.state == FoldingFeature.State.HALF_OPENED &&
        fold.orientation == FoldingFeature.Orientation.HORIZONTAL
    ) {
        val top = with(density) { fold.bounds.top.toDp() }
        val bottom = with(density) { fold.bounds.bottom.toDp() }
        if (top > 220.dp && height - bottom > 200.dp) return Panes.Stacked(top, bottom)
    }
    if (width < 720.dp) return Panes.Single
    if (fold != null && fold.orientation == FoldingFeature.Orientation.VERTICAL) {
        val left = with(density) { fold.bounds.left.toDp() }
        val right = with(density) { fold.bounds.right.toDp() }
        if (left - railWidth >= 320.dp && width - right >= 320.dp) {
            return Panes.SideBySide(leftEnd = left - PaneGap, rightStart = right + PaneGap)
        }
    }
    val leftWidth = ((width - railWidth) * 0.5f).coerceIn(340.dp, 620.dp)
    return Panes.SideBySide(leftEnd = railWidth + leftWidth, rightStart = railWidth + leftWidth + PaneGap * 2)
}
