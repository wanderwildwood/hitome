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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.hitome.ui.AboutDialog
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
        SOURCES.associate { (pkg, _) -> pkg to (context.packageManager.getLaunchIntentForPackage(pkg) != null) }
    }
    val access = remember(checks) { Notices.accessGranted(context) }
    val chosenCount = remember(checks, choosing) { Notices.chosen(context).size }
    var showText by remember { mutableStateOf(Notices.showText(context)) }

    var todayOn by remember { mutableStateOf(Today.enabled(context)) }
    var keep by remember { mutableStateOf(Glance.keep(context)) }
    val calendarAccess = remember(checks) { Today.canReadCalendars(context) }
    val muditaCalendar = remember(checks) { Today.muditaCalendarInstalled(context) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        checks++
        // Refused for good: only Android's own page for Glance can allow it now.
        if (!granted && context is ComponentActivity &&
            !context.shouldShowRequestPermissionRationale(android.Manifest.permission.READ_CALENDAR)
        ) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
            )
        }
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about)) { aboutOpen = true } },
            )
        },
    ) { padding ->
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(
                    title = stringResource(if (serviceOn) R.string.service_on else R.string.service_off),
                    value = stringResource(if (serviceOn) R.string.service_on_note else R.string.service_off_note),
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
            item {
                Row(
                    title = stringResource(R.string.preview),
                    value = stringResource(R.string.preview_note),
                ) { previewing = true }
            }
            item {
                TextMMD(
                    text = stringResource(R.string.sources_heading),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                )
            }
            SOURCES.forEach { (pkg, label) ->
                item(key = pkg) {
                    val here = installed[pkg] == true
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
                        return@item
                    }
                    Row(
                        title = stringResource(label),
                        value = if (here) null else stringResource(R.string.source_missing),
                    ) {
                        context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
                    }
                }
            }
            item {
                TextMMD(
                    text = stringResource(R.string.sources_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            item {
                // Pressed, it moves on to the next part, the way round the panel draws them.
                Row(
                    title = stringResource(R.string.keep_title),
                    value = stringResource(
                        when (keep) {
                            Glance.Keep.TODAY -> R.string.keep_today
                            Glance.Keep.WEATHER -> R.string.keep_weather
                            Glance.Keep.COUNTS -> R.string.keep_counts
                        },
                    ),
                ) {
                    keep = Glance.Keep.entries[(keep.ordinal + 1) % Glance.Keep.entries.size]
                    Glance.setKeep(context, keep)
                }
            }
            item {
                TextMMD(
                    text = stringResource(R.string.others_heading),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                )
            }
            item {
                Row(
                    title = stringResource(if (access) R.string.access_on else R.string.access_off),
                    value = stringResource(if (access) R.string.access_on_note else R.string.access_off_note),
                ) { openNotificationAccess(context) }
            }
            if (access) {
                item {
                    Row(
                        title = stringResource(R.string.choose_apps),
                        value = if (chosenCount == 0) stringResource(R.string.chosen_none)
                            else stringResource(R.string.chosen_some).format(chosenCount),
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
            }
            item {
                TextMMD(
                    text = stringResource(R.string.others_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
                )
            }
        }
    }
    if (aboutOpen) AboutDialog { aboutOpen = false }
}

@Composable
private fun Row(title: String, value: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
        if (value != null) TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
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
                    Box(Modifier.width(PANEL_WIDTH_DP.dp)) {
                        GlancePanel(sections = shown, notices = notices, roomDp = PREVIEW_ROOM_DP, onOpen = {})
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
        LazyColumnMMD(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            apps.forEach { (pkg, label) ->
                item(key = pkg) {
                    val on = pkg in chosen
                    SwitchRow(label, if (label in shared) pkg else null, on) {
                        Notices.setChosen(context, pkg, !on)
                        chosen = Notices.chosen(context)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String?, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
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

/** The apps that take part, in the order the panel draws them. */
private val SOURCES = listOf(
    "com.wanderwildwood.koyomi" to R.string.source_calendar,
    "com.wanderwildwood.soramoyo" to R.string.source_sky,
    "com.wanderwildwood.oboegaki" to R.string.source_notes,
    "com.wanderwildwood.jimeikin" to R.string.source_music,
    "com.wanderwildwood.mimidoku" to R.string.source_audio_reading,
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
