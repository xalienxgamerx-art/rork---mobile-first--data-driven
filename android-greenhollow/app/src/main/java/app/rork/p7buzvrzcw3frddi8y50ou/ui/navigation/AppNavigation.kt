package app.rork.p7buzvrzcw3frddi8y50ou.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.rork.p7buzvrzcw3frddi8y50ou.GameViewModel
import app.rork.p7buzvrzcw3frddi8y50ou.Phase
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Command
import app.rork.p7buzvrzcw3frddi8y50ou.sim.GameEngine
import app.rork.p7buzvrzcw3frddi8y50ou.sim.Settings
import app.rork.p7buzvrzcw3frddi8y50ou.sim.UiEvent
import app.rork.p7buzvrzcw3frddi8y50ou.ui.parchment
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.CreationScreen
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.DialogueSheet
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.ForgeScreen
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.JournalScreen
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.NpcDetailScreen
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.PackScreen
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.ReadingSheet
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.SettingsSheet
import app.rork.p7buzvrzcw3frddi8y50ou.ui.screens.WorldScreen
import app.rork.p7buzvrzcw3frddi8y50ou.ui.theme.Ink
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("world", "World", Icons.Filled.Public),
    Tab("journal", "Journal", Icons.AutoMirrored.Filled.MenuBook),
    Tab("pack", "Pack", Icons.Filled.Backpack),
    Tab("forge", "Forge", Icons.Filled.Handyman),
)

private fun NavHostController.toTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavigation(vm: GameViewModel) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeSeq by remember { mutableIntStateOf(0) }
    val announce: (String) -> Unit = {
        notice = it
        noticeSeq++
    }

    LaunchedEffect(Unit) { vm.notices.collect { announce(it) } }
    LaunchedEffect(noticeSeq) {
        if (notice != null) {
            delay(3000)
            notice = null
        }
    }

    Box(Modifier.fillMaxSize().parchment()) {
        when (val p = phase) {
            Phase.Loading -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Ink.Leather)
                Text(
                    "Unrolling the map…",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            is Phase.Creation -> CreationScreen(p.content, onSettings = { showSettings = true }, onBegin = vm::startNewGame)
            is Phase.Playing -> key(p.engine) {
                GameShell(p.engine, vm, settings, onSettings = { showSettings = true }, announce = announce)
            }
        }

        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 150.dp, start = 24.dp, end = 24.dp),
        ) {
            var shown by remember { mutableStateOf("") }
            notice?.let { shown = it }
            Text(
                shown,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .clip(CircleShape)
                    .background(Ink.ParchmentLight.copy(alpha = 0.94f))
                    .border(1.2.dp, Ink.Leather.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
}

@Composable
private fun GameShell(
    engine: GameEngine,
    vm: GameViewModel,
    settings: Settings,
    onSettings: () -> Unit,
    announce: (String) -> Unit,
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == null || tabs.any { it.route == route }
    val haptics = LocalHapticFeedback.current
    var hurtPulse by remember { mutableIntStateOf(0) }
    var dialogue by remember { mutableStateOf<UiEvent.Dialogue?>(null) }
    var reading by remember { mutableStateOf<UiEvent.Read?>(null) }

    LaunchedEffect(engine) {
        engine.events.collect { e ->
            when (e) {
                is UiEvent.Toast -> announce(e.text)
                is UiEvent.Dialogue -> dialogue = e
                is UiEvent.Read -> reading = e
                UiEvent.EndDialogue -> dialogue = null
                UiEvent.OpenForge -> nav.toTab("forge")
                UiEvent.Hit -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                UiEvent.Hurt -> {
                    hurtPulse++
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }
        }
    }

    Scaffold(
        containerColor = Ink.Parchment,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                Column {
                    HorizontalDivider(thickness = 1.5.dp, color = Ink.Leather.copy(alpha = 0.7f))
                    NavigationBar(containerColor = Ink.Parchment, contentColor = Ink.Text, tonalElevation = 0.dp) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = (route ?: "world") == tab.route,
                                onClick = { nav.toTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Ink.Leather,
                                    selectedIconColor = Ink.ParchmentLight,
                                    selectedTextColor = Ink.Text,
                                    unselectedIconColor = Ink.LeatherDark,
                                    unselectedTextColor = Ink.Text,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = "world",
            modifier = Modifier.padding(inner),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable("world") {
                WorldScreen(engine, settings, hurtPulse, onJournal = { nav.toTab("journal") }, onSettings = onSettings)
            }
            composable("journal") {
                JournalScreen(engine, onSettings = onSettings, onNpc = { nav.navigate("npc/$it") })
            }
            composable("pack") { PackScreen(engine, onSettings = onSettings) }
            composable("forge") { ForgeScreen(engine, vm.forge, onSettings = onSettings) }
            composable("npc/{id}") { back ->
                NpcDetailScreen(engine, back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
        }
    }

    dialogue?.let { d ->
        DialogueSheet(engine, d.npcId, d.line) {
            dialogue = null
            engine.submit(Command.EndDialogue)
        }
    }
    reading?.let { r -> ReadingSheet(r.title, r.text) { reading = null } }
}
