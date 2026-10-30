package com.wanderwildwood.hitome

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.ThemeMMD
import androidx.compose.ui.text.font.FontWeight
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.radio_button.RadioButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.hitome.ui.PagedColumn
import com.wanderwildwood.hitome.ui.EInkDialog
import com.wanderwildwood.hitome.ui.HouseButton
import com.wanderwildwood.hitome.ui.RestrictedHint
import com.wanderwildwood.hitome.ui.RestrictedSettings
import com.wanderwildwood.hitome.ui.AboutScreen
import com.wanderwildwood.hitome.ui.BarButton
import com.wanderwildwood.hitome.ui.GlancePanel
import com.wanderwildwood.hitome.ui.Icons
import com.wanderwildwood.hitome.ui.monochrome
import com.wanderwildwood.hitome.ui.PANEL_WIDTH_DP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThemeMMD(colorScheme = monochrome) { MainScreen() } }
    }
}

/**
 * Whether the panel is on, and where its lines come from. Calendar, Sky, Messaging and Email
 * each say in their own settings whether they show, so for them this screen is only a door;
 * other apps are counted from their notifications, which is switched on and chosen here.
 */
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var aboutOpen by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    // Re-read on every return, which is usually from the accessibility settings.
    var checks by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        checks++
        onPauseOrDispose { }
    }
    val serviceOn = remember(checks) { serviceEnabled(context) }
    val installed = remember(checks) {
        Glance.SOURCES.associateWith { Glance.installed(context.packageManager, it) }
    }
    val access = remember(checks) { Notices.accessGranted(context) }
    // Asked for and still off on the way back: on Android 13+ that is restricted settings.
    var askedService by rememberSaveable { mutableStateOf(false) }
    var askedAccess by rememberSaveable { mutableStateOf(false) }
    val restricted = remember(checks) { RestrictedSettings.blocked(context) }
    val chosenCount = remember(checks, choosing) { Notices.chosen(context).size }
    var showText by remember { mutableStateOf(Notices.showText(context)) }
    var quietOn by remember(checks) { mutableStateOf(Quiet.on(context)) }
    var torchOn by remember { mutableStateOf(Torch.enabled(context)) }
    var textSize by remember { mutableIntStateOf(Glance.textSize(context)) }
    var pickingSize by remember { mutableStateOf(false) }
    val hasTorch = remember { Torch.available(context) }

    var todayOn by remember { mutableStateOf(Today.enabled(context)) }
    var switchedOn by remember { mutableStateOf(Glance.OPT_IN.filter { Glance.switchedOn(context, it) }.toSet()) }
    val calendarAccess = remember(checks) { Today.canReadCalendars(context) }
    val muditaCalendar = remember(checks) { Today.muditaCalendarInstalled(context) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        checks++
        // Refused for good: only Android's own page for Glance can allow it now.
        if (!granted && context is ComponentActivity &&
            !context.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CALENDAR)
        ) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                )
            }
        }
    }

    var order by remember { mutableStateOf(Glance.order(context)) }
    var ordering by remember { mutableStateOf(false) }

    // A newer source without its app is not listed, and is stepped over when moving.
    fun listed(key: String) = Glance.listed(key, installed, access)

    @Composable
    fun SourceRow(pkg: String, label: Int) {
        val here = installed[pkg] == true
        // The newer sources: not listed without their app, and off until switched on here.
        if (pkg in Glance.OPT_IN) {
            val on = pkg in switchedOn
            SwitchRow(title = stringResource(label), note = null, checked = on) {
                Glance.setSwitchedOn(context, pkg, !on)
                switchedOn = if (on) switchedOn - pkg else switchedOn + pkg
            }
            return
        }
        // Without Calendar, Glance reads today's events itself, if asked to.
        if (pkg == Glance.CALENDAR && !here) {
            SwitchRow(
                title = stringResource(R.string.today_switch),
                note = stringResource(
                    when {
                        muditaCalendar && calendarAccess -> R.string.today_note_all
                        muditaCalendar && todayOn -> R.string.today_note_mudita
                        muditaCalendar -> R.string.today_note_mudita_off
                        calendarAccess -> R.string.today_note_shared
                        else -> R.string.today_note_none
                    },
                ),
                checked = todayOn,
            ) {
                // With it on, a press on a row still missing the other calendars asks for them.
                if (todayOn && !calendarAccess) {
                    askCalendar.launch(android.Manifest.permission.READ_CALENDAR)
                    return@SwitchRow
                }
                todayOn = !todayOn
                Today.setEnabled(context, todayOn)
                if (todayOn && !calendarAccess) askCalendar.launch(android.Manifest.permission.READ_CALENDAR)
            }
            return
        }
        // Not installed, these are named once together, below the list.
        if (!here) return
        // Switched on or off in the app itself, so the row is a door to it.
        Row(title = stringResource(label), value = null, chevron = true) {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it) } }
        }
    }
    if (aboutOpen) {
        BackHandler { aboutOpen = false }
        AboutScreen { aboutOpen = false }
        return
    }
    if (choosing) {
        BackHandler { choosing = false }
        ChooseAppsScreen { choosing = false }
        return
    }
    if (previewing) {
        BackHandler { previewing = false }
        PreviewScreen(serviceOn) { previewing = false }
        return
    }
    if (ordering) {
        BackHandler { ordering = false }
        OrderScreen(
            keys = order.filter { listed(it) },
            onMove = { key, by ->
                val shown = order.filter { listed(it) }
                val next = shown.getOrNull(shown.indexOf(key) + by)
                if (next != null) {
                    order = Glance.moved(order, order.indexOf(key), order.indexOf(next))
                    Glance.setOrder(context, order)
                }
            },
            onBack = { ordering = false },
        )
        return
    }

    // The older sources whose app is missing: Calendar's place is taken by the Today switch.
    val missing = (Glance.SOURCES - Glance.OPT_IN - Glance.CALENDAR)
        .filter { installed[it] != true }
        .mapNotNull { (SOURCES + UNREAD_SOURCES)[it] }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about)) { aboutOpen = true } },
            )
        },
    ) { padding ->
        PagedColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            // Said only while there is something to do about it.
            if (!serviceOn) {
                item {
                    Column {
                        HouseButton(
                            stringResource(R.string.service_off),
                            {
                                askedService = true
                                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            },
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        TextMMD(
                            text = stringResource(R.string.service_off_note),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                        )
                    }
                }
                if (askedService && restricted) item { RestrictedHint() }
            }
            item {
                Row(title = stringResource(R.string.preview), value = stringResource(R.string.preview_note), chevron = true) { previewing = true }
            }
            item {
                Row(title = stringResource(R.string.text_size), value = stringResource(TEXT_SIZE_NAMES[textSize])) { pickingSize = true }
            }

            // Each heading is drawn in the item of the row under it, so a page never ends on a
            // heading alone.
            order.filter { listed(it) }.forEachIndexed { i, key ->
                item(key = key) {
                    if (i == 0) Heading(stringResource(R.string.sources_heading))
                    if (key == Glance.UNREAD) {
                        UNREAD_SOURCES.forEach { (pkg, label) -> SourceRow(pkg, label) }
                        if (UNREAD_SOURCES.none { installed[it.first] == true }) {
                            Row(title = stringResource(R.string.unread_others), value = stringResource(R.string.unread_others_note)) {}
                        }
                    } else {
                        SourceRow(key, SOURCES.getValue(key))
                    }
                }
            }
            item {
                Row(title = stringResource(R.string.order_title), value = stringResource(R.string.order_note), chevron = true) { ordering = true }
            }
            item {
                TextMMD(
                    text = stringResource(R.string.own_settings_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (missing.isNotEmpty()) {
                item {
                    val names = missing.map { stringResource(it).substringBefore(':') }
                    TextMMD(
                        text = stringResource(R.string.not_installed, names.joinToString(", ")),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            item {
                Heading(stringResource(R.string.others_heading))
                Row(
                    title = stringResource(R.string.access_title),
                    value = stringResource(if (access) R.string.access_on_short else R.string.access_off_note),
                    chevron = true,
                ) {
                    askedAccess = true
                    openNotificationAccess(context)
                }
            }
            // With the panel on, restricted settings were already allowed: nothing to say here.
            if (askedAccess && !access && !serviceOn && restricted) item { RestrictedHint() }
            if (access) {
                item {
                    Row(
                        title = stringResource(R.string.choose_apps),
                        value = if (chosenCount == 0) stringResource(R.string.chosen_none)
                            else stringResource(R.string.chosen_some).format(chosenCount),
                        chevron = true,
                    ) { choosing = true }
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.show_text),
                        note = stringResource(R.string.show_text_note),
                        checked = showText,
                    ) {
                        showText = !showText
                        Notices.setShowText(context, showText)
                    }
                }
                // What is shown with that switch off, said under it rather than at the foot.
                item {
                    TextMMD(
                        text = stringResource(R.string.others_note),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
            item {
                Heading(stringResource(R.string.sound_heading))
                SwitchRow(
                    title = stringResource(R.string.quiet),
                    note = stringResource(if (access) R.string.quiet_note else R.string.quiet_needs_access),
                    checked = quietOn,
                ) {
                    if (!Quiet.allowed(context)) {
                        askedAccess = true
                        openNotificationAccess(context)
                        return@SwitchRow
                    }
                    if (Quiet.set(context, !quietOn)) quietOn = !quietOn
                }
            }
            // Only on a phone with a torch to switch.
            if (hasTorch) {
                item {
                    Heading(stringResource(R.string.torch_heading))
                    SwitchRow(title = stringResource(R.string.torch), note = stringResource(R.string.torch_note), checked = torchOn) {
                        torchOn = !torchOn
                        Torch.setEnabled(context, torchOn)
                        // Its line on the lock screen goes with the switch, and Mudita's own
                        // controls cannot see this torch: off with the switch, so it is never left on.
                        if (!torchOn) Torch.off(context)
                    }
                }
            }
        }
    }
    if (pickingSize) {
        EInkDialog(onDismiss = { pickingSize = false }) {
            TextMMD(text = stringResource(R.string.text_size), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            TEXT_SIZE_NAMES.forEachIndexed { i, name ->
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth().clickable {
                        textSize = i
                        Glance.setTextSize(context, i)
                        pickingSize = false
                    }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButtonMMD(selected = i == textSize, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    TextMMD(text = stringResource(name), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The names of [Glance.TEXT_SCALES], in the same order. */
private val TEXT_SIZE_NAMES = listOf(R.string.text_size_normal, R.string.text_size_large, R.string.text_size_larger)

/** A section's name, in bold, set apart by space so the screen reads as its parts. */
@Composable
private fun Heading(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 2.dp)) {
        TextMMD(text = text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * The order of the panel, on its own page: each source with an arrow up and an arrow down.
 * When there is not room on the lock screen for all of them, the ones at the top are shown.
 */
@Composable
private fun OrderScreen(keys: List<String>, onMove: (String, Int) -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.order_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        PagedColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            item {
                TextMMD(
                    text = stringResource(R.string.order_page_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
            }
            keys.forEachIndexed { i, key ->
                item(key = key) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextMMD(
                            text = stringResource(if (key == Glance.UNREAD) R.string.unread_title else SOURCES.getValue(key)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        // Not there for the first row, nor down for the last: no press that does nothing.
                        Box(Modifier.width(48.dp)) {
                            if (i > 0) BarButton(Icons.Up, stringResource(R.string.move_up)) { onMove(key, -1) }
                        }
                        Box(Modifier.width(48.dp)) {
                            if (i < keys.size - 1) BarButton(Icons.Down, stringResource(R.string.move_down)) { onMove(key, 1) }
                        }
                    }
                }
            }
        }
    }
}

/** A row that opens something: another page, another app, a setting of Android's. */
@Composable
private fun Row(title: String, value: String?, chevron: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            if (value != null) TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
        }
        if (chevron) TextMMD(text = "›", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}

/**
 * The panel as the lock screen would draw it now, from the same lines, so what each switch
 * does can be seen without locking the phone. Pressing it does nothing here.
 */
@Composable
private fun PreviewScreen(serviceOn: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var sections by remember { mutableStateOf<List<Glance.Section>?>(null) }
    val notices by Notices.now.collectAsState()
    LaunchedEffect(Unit) { sections = withContext(Dispatchers.IO) { Glance.readAll(context) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.preview)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            val shown = sections ?: return@Column
            if (shown.isEmpty() && notices.isEmpty()) {
                TextMMD(text = stringResource(R.string.preview_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    val scale = Glance.textScale(context)
                    Box(Modifier.width((PANEL_WIDTH_DP * scale).dp)) {
                        GlancePanel(
                            sections = shown,
                            notices = notices,
                            order = Glance.order(context),
                            roomDp = (PREVIEW_ROOM_DP / scale).toInt(),
                            onOpen = {},
                            scale = scale,
                        )
                    }
                }
            }
            if (!serviceOn) {
                TextMMD(
                    text = stringResource(R.string.preview_off),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
    }
}

/** Room given the preview: about what a lock screen with only a clock and date leaves. */
private const val PREVIEW_ROOM_DP = 380

/**
 * Every app with a place in the launcher, to choose which have their notifications counted.
 * Calendar, Sky, Messaging and Email are left out: they have their own place on the panel.
 * Glance too, which posts nothing.
 */
@Composable
private fun ChooseAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val apps = remember {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName && it.packageName !in Glance.SOURCES }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }
    // Two apps may share a name (Android's own Calendar and this one): those say which is which.
    val shared = remember(apps) { apps.groupBy { it.second }.filterValues { it.size > 1 }.keys }
    var chosen by remember { mutableStateOf(Notices.chosen(context)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.choose_apps)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        PagedColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            apps.forEach { (pkg, label) ->
                item(key = pkg) {
                    val on = pkg in chosen
                    SwitchRow(label, if (label in shared) pkg else null, on) {
                        Notices.setChosen(context, pkg, !on)
                        chosen = Notices.chosen(context)
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String?, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
            if (note != null) TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(12.dp))
        SwitchMMD(checked = checked, onCheckedChange = null)
    }
}

/** Android's notification-access page, Glance's own entry if the phone has that page. */
private fun openNotificationAccess(context: android.content.Context) {
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, NoticeListener::class.java).flattenToString(),
        )
    try {
        context.startActivity(detail)
    } catch (_: Exception) {
        try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
    }
}

/** The stacked sources' names, listed in the person's order ([Glance.order]). */
private val SOURCES = mapOf(
    "com.wanderwildwood.zatsuno" to R.string.source_field_kit,
    "com.wanderwildwood.koyomi" to R.string.source_calendar,
    "com.wanderwildwood.fukuyaku" to R.string.source_medicine,
    "com.wanderwildwood.soramoyo" to R.string.source_sky,
    "com.wanderwildwood.oboegaki" to R.string.source_notes,
    "com.wanderwildwood.satsuire" to R.string.source_wallet,
    "com.wanderwildwood.jimeikin" to R.string.source_music,
    "com.wanderwildwood.mimidoku" to R.string.source_audio_reading,
    "com.wanderwildwood.zeni" to R.string.source_bitcoin,
)

/** The counts, which keep one place in the order between them. */
private val UNREAD_SOURCES = listOf(
    "com.wanderwildwood.kotozute" to R.string.source_messaging,
    "com.wanderwildwood.tayori" to R.string.source_email,
)

/**
 * Whether Glance's service is switched on. The setting may name it in full or in the short form
 * (`package/.Class`), so each entry is read as a component rather than compared as text.
 */
private fun serviceEnabled(context: android.content.Context): Boolean {
    val mine = ComponentName(context, GlanceService::class.java)
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
}
