package com.wizlabs.mileage

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wizlabs.mileage.data.ShareCodec
import com.wizlabs.mileage.insights.Fmt
import com.wizlabs.mileage.model.AppData
import com.wizlabs.mileage.model.ThemeMode
import com.wizlabs.mileage.ui.components.IconBadge
import com.wizlabs.mileage.ui.components.Overline
import com.wizlabs.mileage.ui.components.icon
import com.wizlabs.mileage.ui.screens.FillEditor
import com.wizlabs.mileage.ui.screens.GarageScreen
import com.wizlabs.mileage.ui.screens.HistoryScreen
import com.wizlabs.mileage.ui.screens.HomeScreen
import com.wizlabs.mileage.ui.screens.InsightsScreen
import com.wizlabs.mileage.ui.screens.ReceiveScreen
import com.wizlabs.mileage.ui.screens.ShareScreen
import com.wizlabs.mileage.ui.screens.VehicleEditor
import com.wizlabs.mileage.ui.screens.WelcomeScreen
import com.wizlabs.mileage.ui.theme.DarkPalette
import com.wizlabs.mileage.ui.theme.LightPalette
import com.wizlabs.mileage.ui.theme.Wiz
import com.wizlabs.mileage.ui.theme.WizTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** A share code handed to us by another app (WhatsApp → Share → Wiz Mileage). */
    private val incoming = mutableStateOf<String?>(null)

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.contains(ShareCodec.PREFIX) }?.let { incoming.value = it }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val data by vm.data.collectAsStateWithLifecycle()
            val dark = when (data.theme) {
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            androidx.compose.runtime.LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
                window.decorView.setBackgroundColor((if (dark) DarkPalette.bg else LightPalette.bg).toArgb())
            }
            WizTheme(data.theme) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = Wiz.c.bg, contentColor = Wiz.c.text) {
                    App(data, vm, packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0", incoming.value) { incoming.value = null }
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.SpaceDashboard),
    HISTORY("History", Icons.Rounded.ReceiptLong),
    INSIGHTS("Insights", Icons.Rounded.Insights),
    GARAGE("Garage", Icons.Rounded.Garage),
}

/** Full-screen editors drawn over the tabs. Encoded as a string so it survives rotation. */
private sealed interface Overlay {
    data class Fill(val id: String?) : Overlay
    data class Vehicle(val id: String?) : Overlay
    data object Share : Overlay
    data object Receive : Overlay
    data object Privacy : Overlay

    companion object {
        fun encode(o: Overlay?): String = when (o) {
            null -> ""
            is Fill -> "f:" + (o.id ?: "")
            is Vehicle -> "v:" + (o.id ?: "")
            Share -> "s:"
            Receive -> "r:"
            Privacy -> "p:"
        }
        fun decode(s: String): Overlay? = when {
            s.startsWith("f:") -> Fill(s.drop(2).ifBlank { null })
            s.startsWith("v:") -> Vehicle(s.drop(2).ifBlank { null })
            s == "s:" -> Share
            s == "r:" -> Receive
            s == "p:" -> Privacy
            else -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(data: AppData, vm: AppViewModel, version: String, incoming: String?, onIncomingSeen: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var overlayKey by rememberSaveable { mutableStateOf("") }
    var welcomeDone by rememberSaveable { mutableStateOf(false) }
    var switcher by remember { mutableStateOf(false) }
    var receiveText by rememberSaveable { mutableStateOf<String?>(null) }
    val overlay = Overlay.decode(overlayKey)
    fun open(o: Overlay?) { overlayKey = Overlay.encode(o) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            vm.exportCsv(uri).onSuccess { n -> snackbar.showSnackbar("Exported $n fill-ups") }
                .onFailure { snackbar.showSnackbar("Export failed: ${it.message}") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            vm.importCsv(uri).onSuccess { r ->
                snackbar.showSnackbar(buildString {
                    append("Imported ${r.added} fill-up${if (r.added == 1) "" else "s"}")
                    if (r.newVehicles > 0) append(", ${r.newVehicles} new vehicle${if (r.newVehicles == 1) "" else "s"}")
                    if (r.skipped > 0) append(" · ${r.skipped} skipped")
                })
            }.onFailure { snackbar.showSnackbar("Couldn't import: ${it.message}") }
        }
    }

    val vehicle = data.selectedVehicle

    // A code shared into the app opens the receive screen straight away.
    androidx.compose.runtime.LaunchedEffect(incoming) {
        if (incoming != null) { receiveText = incoming; open(Overlay.Receive); onIncomingSeen() }
    }

    // First run: welcome, then the vehicle editor.
    if (vehicle == null && overlay == null) {
        if (!welcomeDone) {
            WelcomeScreen(
                onStart = { welcomeDone = true; open(Overlay.Vehicle(null)) },
                onReceive = { receiveText = null; open(Overlay.Receive) },
            )
            return
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { open(Overlay.Vehicle(null)) }
        return
    }

    AnimatedContent(
        targetState = overlay,
        transitionSpec = {
            (slideInVertically { it / 8 } + fadeIn()) togetherWith (slideOutVertically { it / 8 } + fadeOut())
        },
        contentKey = { Overlay.encode(it).substringBefore(":") },
        label = "overlay",
    ) { o ->
        when (o) {
            is Overlay.Fill -> {
                val v = vehicle ?: return@AnimatedContent
                val existing = o.id?.let { id -> data.fills.firstOrNull { it.id == id } }
                BackHandler { open(null) }
                FillEditor(
                    vehicle = v, existing = existing, allFills = data.fillsFor(v.id),
                    onSave = { vm.saveFill(it); open(null); tab = if (tab == Tab.GARAGE) Tab.HOME else tab },
                    onDelete = { f ->
                        vm.deleteFill(f.id); open(null)
                        scope.launch {
                            val r = snackbar.showSnackbar("Fill-up deleted", "Undo", duration = SnackbarDuration.Long)
                            if (r == SnackbarResult.ActionPerformed) vm.saveFill(f)
                        }
                    },
                    onClose = { open(null) },
                )
            }
            is Overlay.Vehicle -> {
                val existing = o.id?.let { id -> data.vehicles.firstOrNull { it.id == id } }
                val firstRun = data.vehicles.isEmpty()
                BackHandler { if (firstRun) { welcomeDone = false; open(null) } else open(null) }
                VehicleEditor(
                    existing = existing,
                    fillCount = existing?.let { e -> data.fills.count { it.vehicleId == e.id } } ?: 0,
                    firstRun = firstRun,
                    onSave = { vm.saveVehicle(it); open(null); if (existing == null) tab = Tab.HOME },
                    onDelete = { vm.deleteVehicle(it.id); open(null) },
                    onClose = { if (firstRun) { welcomeDone = false; open(null) } else open(null) },
                )
            }
            Overlay.Share -> {
                val v = vehicle ?: return@AnimatedContent
                BackHandler { open(null) }
                ShareScreen(data, v, onClose = { open(null) }, onCopied = { scope.launch { snackbar.showSnackbar("Share code copied") } })
            }
            Overlay.Privacy -> {
                BackHandler { open(null) }
                com.wizlabs.mileage.ui.screens.PrivacyScreen(onClose = { open(null) })
            }
            Overlay.Receive -> {
                val close = { receiveText = null; open(null) }
                BackHandler { close() }
                ReceiveScreen(
                    data = data, initialText = receiveText,
                    onImport = { b ->
                        val plan = vm.importShared(b)
                        welcomeDone = true; close(); tab = Tab.HOME
                        scope.launch {
                            snackbar.showSnackbar(buildString {
                                append("Imported ${plan.newFills} fill-up${if (plan.newFills == 1) "" else "s"}")
                                if (plan.newVehicles.isNotEmpty()) append(" · new: ${plan.newVehicles.joinToString()}")
                                if (plan.duplicates > 0) append(" · ${plan.duplicates} skipped")
                            })
                        }
                    },
                    onClose = close,
                )
            }
            null -> {
                val v = vehicle ?: return@AnimatedContent
                val fills = data.fillsFor(v.id)
                Scaffold(
                    containerColor = Wiz.c.bg,
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    Modifier.clip(RoundedCornerShape(50)).clickable { switcher = true }.padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconBadge(v.type.icon(), size = 36)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(v.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                        Text(
                                            if (tab == Tab.GARAGE) "Garage & settings" else greeting(),
                                            style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted, maxLines = 1,
                                        )
                                    }
                                    Icon(Icons.Rounded.KeyboardArrowDown, "Switch vehicle", tint = Wiz.c.muted)
                                }
                            },
                            actions = {
                                IconButton(onClick = { receiveText = null; open(Overlay.Receive) }) {
                                    Icon(Icons.Rounded.QrCodeScanner, "Receive logs", tint = Wiz.c.text)
                                }
                                IconButton(onClick = { open(Overlay.Share) }) {
                                    Icon(Icons.Rounded.IosShare, "Share logs", tint = Wiz.c.text)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Wiz.c.bg),
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = Wiz.c.surface, tonalElevation = 0.dp) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Wiz.c.onAccent, indicatorColor = Wiz.c.accent,
                                        selectedTextColor = Wiz.c.text, unselectedIconColor = Wiz.c.muted, unselectedTextColor = Wiz.c.muted,
                                    ),
                                )
                            }
                        }
                    },
                    floatingActionButton = {
                        if (tab == Tab.HOME || tab == Tab.HISTORY) ExtendedFloatingActionButton(
                            onClick = { open(Overlay.Fill(null)) },
                            containerColor = Wiz.c.accent, contentColor = Wiz.c.onAccent, shape = RoundedCornerShape(20.dp),
                            icon = { Icon(Icons.Rounded.Add, null) },
                            text = { Text("Add fill-up", fontWeight = FontWeight.Bold) },
                        )
                    },
                ) { pad ->
                    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            v, fills, pad, onAdd = { open(Overlay.Fill(null)) }, onOpenFill = { open(Overlay.Fill(it.id)) },
                            onSeeInsights = { tab = Tab.INSIGHTS }, onSeeHistory = { tab = Tab.HISTORY },
                        )
                        Tab.HISTORY -> HistoryScreen(v, fills, pad) { open(Overlay.Fill(it.id)) }
                        Tab.INSIGHTS -> InsightsScreen(data, v, pad)
                        Tab.GARAGE -> GarageScreen(
                            data, version, pad,
                            onSelect = { vm.selectVehicle(it.id) },
                            onEdit = { open(Overlay.Vehicle(it.id)) },
                            onAddVehicle = { open(Overlay.Vehicle(null)) },
                            onTheme = vm::setTheme,
                            onExport = { exportLauncher.launch("wiz-mileage-${LocalDate.now()}.csv") },
                            onShare = { open(Overlay.Share) },
                            onReceive = { receiveText = null; open(Overlay.Receive) },
                            onPrivacy = { open(Overlay.Privacy) },
                            onImport = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
                        )
                    }
                }

                if (switcher) ModalBottomSheet(onDismissRequest = { switcher = false }, containerColor = Wiz.c.surface) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Overline("Switch vehicle", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        data.vehicles.forEach { x ->
                            val sel = x.id == v.id
                            val xf = data.fills.filter { it.vehicleId == x.id }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                    .background(if (sel) Wiz.c.surface2 else Wiz.c.surface)
                                    .clickable { vm.selectVehicle(x.id); switcher = false }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconBadge(x.type.icon(), tint = if (sel) Wiz.c.onAccent else Wiz.c.text, bg = if (sel) Wiz.c.accent else Wiz.c.surface2)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(x.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        listOf(x.type.label, x.registration, "${xf.size} fill-ups", Fmt.inr(xf.sumOf { it.amount }))
                                            .filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = Wiz.c.muted,
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                                .clickable { switcher = false; open(Overlay.Vehicle(null)) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(Icons.Rounded.Add, tint = Wiz.c.text, bg = Wiz.c.surface2)
                            Spacer(Modifier.width(14.dp))
                            Text("Add vehicle", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

private fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when { h < 5 -> "Late drive?"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; else -> "Good evening" }
}
