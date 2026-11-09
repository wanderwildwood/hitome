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
import com.wanderwildwood.hitome.ui.AboutDialog
import com.wanderwildwood.hitome.ui.BarButton
import com.wanderwildwood.hitome.ui.GlancePanel
import com.wanderwildwood.hitome.ui.Icons
import com.wanderwildwood.hitome.ui.houseTypography
import com.wanderwildwood.hitome.ui.monochrome
import com.wanderwildwood.hitome.ui.PANEL_WIDTH_DP
import com.wanderwildwood.hitome.ui.DialogButtons
import com.wanderwildwood.hitome.ui.TimeDialog
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThemeMMD(colorScheme = monochrome, typography = houseTypography) { MainScreen() } }
    }
}

/**
 * Whether the panel is on, and where its lines come from: one list of the phone's apps
 * ([AppsScreen]), the shop's and everyone else's together, with whatever belongs to one app
 * alone under it.
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
        // Opened inside quiet hours, or back from granting notification access: put it right.
        QuietHours.sync(context)
        onPauseOrDispose { }
    }
    val serviceOn = remember(checks) { serviceEnabled(context) }
    // The shop's apps Glance knows by name, and any other that keeps the same provider.
    val discovered = remember(checks) {
        Glance.forgetDiscovered()
        Glance.discovered(context)
    }
    val installed = remember(checks) {
        Glance.SOURCES.associateWith { Glance.installed(context.packageManager, it) } +
            discovered.associateWith { true }
    }
    val access = remember(checks) { Notices.accessGranted(context) }
    // Asked for and still off on the way back: on Android 13+ that is restricted settings.
    var askedService by rememberSaveable { mutableStateOf(false) }
    var askedAccess by rememberSaveable { mutableStateOf(false) }
    val restricted = remember(checks) { RestrictedSettings.blocked(context) }
    val appsOn = remember(checks, choosing) { appsOnCount(context) }
    val othersChosen = remember(checks, choosing) { Notices.chosen(context).isNotEmpty() }
    var showText by remember { mutableStateOf(Notices.showText(context)) }
    var quietOn by remember(checks) { mutableStateOf(Quiet.on(context)) }
    var torchOn by remember { mutableStateOf(Torch.enabled(context)) }
    var quietHoursOn by remember { mutableStateOf(QuietHours.enabled(context)) }
    var quietStart by remember { mutableStateOf(QuietHours.start(context)) }
    var quietEnd by remember { mutableStateOf(QuietHours.end(context)) }
    var quietDays by remember { mutableStateOf(QuietHours.days(context)) }
    var repeatCallers by remember { mutableStateOf(QuietHours.repeatCallers(context)) }
    var settingTime by remember { mutableStateOf<Boolean?>(null) } // true: the start, false: the end
    var pickingDays by remember { mutableStateOf(false) }
    // Whether the night has begun, and when it ends: read afresh on return and on each change.
    val quietUntil = remember(checks, quietHoursOn, quietStart, quietEnd, quietDays) {
        val schedule = QuietHours.schedule(context)
        val now = ZonedDateTime.now()
        if (quietHoursOn && schedule.inside(now)) schedule.nextChange(now) else null
    }
    var textSize by remember { mutableIntStateOf(Glance.textSize(context)) }
    var pickingSize by remember { mutableStateOf(false) }
    val hasTorch = remember { Torch.available(context) }

    var todayOn by remember { mutableStateOf(Today.enabled(context)) }
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

    var order by remember(discovered) { mutableStateOf(Glance.order(context)) }
    var ordering by remember { mutableStateOf(false) }

    // A newer source without its app is not listed, and is stepped over when moving.
    fun listed(key: String) = Glance.listed(key, installed, access)

    // Without Calendar, Glance reads today's events itself, if asked to.
    @Composable
    fun TodayRow() {
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
    }
    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })
    if (choosing) {
        BackHandler { choosing = false }
        AppsScreen(
            access = access,
            onNeedAccess = {
                askedAccess = true
                openNotificationAccess(context)
            },
            onBack = { choosing = false },
        )
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = { BarButton(Icons.Info, stringResource(R.string.cd_about)) { aboutOpen = true } },
            )
        },
    ) { padding ->
        PagedColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp), showCutRow = true) {
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

            // One list of every app on the phone, the shop's and everyone else's, on its own page.
            item {
                Heading(stringResource(R.string.sources_heading))
                Row(
                    title = stringResource(R.string.apps_title),
                    value = if (appsOn == 0) stringResource(R.string.apps_none) else stringResource(R.string.apps_on, appsOn),
                    chevron = true,
                ) { choosing = true }
            }
            if (!installed.getOrDefault(Glance.CALENDAR, false)) item { TodayRow() }
            item {
                Row(title = stringResource(R.string.order_title), value = stringResource(R.string.order_note), chevron = true) { ordering = true }
            }
            // Other apps are counted from their notifications, which needs Android's leave.
            if (othersChosen && !access) {
                item {
                    Row(
                        title = stringResource(R.string.access_title),
                        value = stringResource(R.string.access_off_note),
                        chevron = true,
                    ) {
                        askedAccess = true
                        openNotificationAccess(context)
                    }
                }
                // With the panel on, restricted settings were already allowed: nothing to say here.
                if (askedAccess && !serviceOn && restricted) item { RestrictedHint() }
            }
            if (access) {
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
            item {
                SwitchRow(
                    title = stringResource(R.string.quiet_hours),
                    note = when {
                        !access -> stringResource(R.string.quiet_needs_access)
                        quietUntil != null -> stringResource(R.string.quiet_hours_now, clockTime(quietUntil.toLocalTime()))
                        else -> stringResource(R.string.quiet_hours_note)
                    },
                    checked = quietHoursOn,
                ) {
                    if (!Quiet.allowed(context)) {
                        askedAccess = true
                        openNotificationAccess(context)
                        return@SwitchRow
                    }
                    quietHoursOn = !quietHoursOn
                    QuietHours.setEnabled(context, quietHoursOn)
                }
            }
            // How it is set, only while it is on.
            if (quietHoursOn) {
                item {
                    Row(title = stringResource(R.string.quiet_hours_start), value = clockTime(quietStart)) { settingTime = true }
                }
                item {
                    Row(
                        title = stringResource(R.string.quiet_hours_end),
                        value = if (quietEnd.isAfter(quietStart)) clockTime(quietEnd)
                            else stringResource(R.string.quiet_hours_next_day, clockTime(quietEnd)),
                    ) { settingTime = false }
                }
                item {
                    Row(title = stringResource(R.string.quiet_hours_days), value = daysLabel(quietDays)) { pickingDays = true }
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.quiet_hours_repeat),
                        note = stringResource(R.string.quiet_hours_repeat_note),
                        checked = repeatCallers,
                    ) {
                        repeatCallers = !repeatCallers
                        QuietHours.setRepeatCallers(context, repeatCallers)
                    }
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
    settingTime?.let { starting ->
        TimeDialog(
            title = stringResource(if (starting) R.string.quiet_hours_start else R.string.quiet_hours_end),
            initial = if (starting) quietStart else quietEnd,
            onPick = { time ->
                if (starting) {
                    quietStart = time
                    QuietHours.setStart(context, time)
                } else {
                    quietEnd = time
                    QuietHours.setEnd(context, time)
                }
            },
            onDismiss = { settingTime = null },
        )
    }
    if (pickingDays) {
        EInkDialog(onDismiss = { pickingDays = false }) {
            TextMMD(text = stringResource(R.string.quiet_hours_days), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            TextMMD(
                text = stringResource(R.string.quiet_hours_days_note),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
            weekInOrder().forEach { day ->
                val on = day in quietDays
                SwitchRow(title = day.getDisplayName(TextStyle.FULL, Locale.getDefault()), note = null, checked = on) {
                    // At least one day stays chosen; switching quiet hours off is the switch above.
                    val next = if (on) quietDays - day else quietDays + day
                    if (next.isNotEmpty()) {
                        quietDays = next
                        QuietHours.setDays(context, next)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DialogButtons(
                cancel = "",
                confirm = stringResource(R.string.done),
                onCancel = null,
                onConfirm = { pickingDays = false },
            )
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

/** A time of day on the phone's own clock, 12- or 24-hour as it is set. */
@Composable
private fun clockTime(time: LocalTime): String {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(
        locale,
        if (android.text.format.DateFormat.is24HourFormat(context)) "Hmm" else "hmma",
    )
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/** The week as the phone's language starts it: Sunday first in the US, Monday in much of Europe. */
private fun weekInOrder(): List<DayOfWeek> {
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    return (0L until 7L).map { first.plus(it) }
}

/** "Every day", or the chosen days in the week's order: "Mon, Tue, Fri". */
@Composable
private fun daysLabel(days: Set<DayOfWeek>): String =
    if (days.size == 7) stringResource(R.string.quiet_hours_every_day)
    else weekInOrder().filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

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
        PagedColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp), showCutRow = true) {
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
                            text = sourceLabel(key),
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
private fun Row(title: String, value: String?, chevron: Boolean = false, indent: Boolean = false, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp)
            .padding(start = if (indent) 16.dp else 0.dp).padding(vertical = 14.dp),
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
 * Every app on the phone with a place in the launcher, A to Z, the shop's and everyone else's
 * together. An app that hands Glance its own lines ([Glance.SOURCES], or one found keeping the
 * provider) is switched by Glance's own switch for it; any other has its notifications
 * counted. What belongs to one app alone, such as its own lock-screen switch, sits under it
 * while it is on.
 */
@Composable
private fun AppsScreen(access: Boolean, onNeedAccess: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { phoneApps(context) }
    val providers = remember { Glance.SOURCES.toSet() + Glance.discovered(context) }
    // Two apps may share a name (Android's own Calendar and this one): those say which is which.
    val shared = remember(apps) { apps.groupBy { it.second }.filterValues { it.size > 1 }.keys }
    var chosen by remember { mutableStateOf(Notices.chosen(context)) }
    var shownHere by remember { mutableStateOf(providers.filter { Glance.shownHere(context, it) }.toSet()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.apps_title)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        PagedColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp), showCutRow = true) {
            item {
                TextMMD(
                    text = stringResource(R.string.apps_page_note),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
            }
            apps.forEach { (pkg, label) ->
                item(key = pkg) {
                    Column {
                        if (pkg in providers) {
                            val on = pkg in shownHere
                            // What it puts on the panel, the part of its name after the colon.
                            val shows = (SOURCES + UNREAD_SOURCES)[pkg]?.let { stringResource(it).substringAfter(": ") }
                            SwitchRow(label, shows ?: if (label in shared) pkg else null, on) {
                                Glance.setShownHere(context, pkg, !on)
                                shownHere = if (on) shownHere - pkg else shownHere + pkg
                            }
                            if (on) AppExtras(pkg, label)
                        } else {
                            val on = pkg in chosen
                            SwitchRow(
                                label,
                                when {
                                    on && !access -> stringResource(R.string.app_needs_access)
                                    on -> stringResource(R.string.app_counted)
                                    label in shared -> pkg
                                    else -> null
                                },
                                on,
                            ) {
                                Notices.setChosen(context, pkg, !on)
                                chosen = Notices.chosen(context)
                                if (!on && !access) onNeedAccess()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * What belongs to [pkg] alone, under its row while it is on: Messaging's and Mailbox's own
 * lock-screen switch, reached directly; for the apps that choose in their own settings what
 * they show (Datebook, Sky, Memo, Music Box, Audio Reader), a door to them.
 */
@Composable
private fun AppExtras(pkg: String, label: String) {
    val context = LocalContext.current
    val door = OWN_SWITCH_DOORS[pkg]
    when {
        door != null -> Row(title = stringResource(door.first), value = stringResource(door.second), chevron = true, indent = true) {
            Glance.ownSwitchIntent(context.packageManager, pkg)?.let { runCatching { context.startActivity(it) } }
        }
        pkg in Glance.STACKED && !Glance.isOptIn(pkg) -> Row(
            title = stringResource(R.string.app_settings_in, label),
            value = stringResource(R.string.app_settings_note),
            chevron = true,
            indent = true,
        ) {
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { runCatching { context.startActivity(it) } }
        }
    }
}

/**
 * The apps [AppsScreen] lists, as package and name, A to Z: every launcher app but Glance.
 * Music Box K answers as Music Box, whose place on the panel it shares.
 */
private fun phoneApps(context: android.content.Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { (if (it.packageName == Glance.MUSIC_K) Glance.MUSIC else it.packageName) to pm.getApplicationLabel(it).toString() }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
}

/** How many of [phoneApps] are on: shown here, or counted from their notifications. */
private fun appsOnCount(context: android.content.Context): Int {
    val providers = Glance.SOURCES.toSet() + Glance.discovered(context)
    val chosen = Notices.chosen(context)
    return phoneApps(context).count { (pkg, _) ->
        if (pkg in providers) Glance.shownHere(context, pkg) else pkg in chosen
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

/**
 * The name of a place in the order: the shop's own word for a known app, and for an app found
 * keeping the provider ([Glance.discovered]), its own name on the phone.
 */
@Composable
private fun sourceLabel(key: String): String {
    if (key == Glance.UNREAD) return stringResource(R.string.unread_title)
    SOURCES[key]?.let { return stringResource(it) }
    val context = LocalContext.current
    return remember(key) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(key, 0)).toString()
        }.getOrDefault(key)
    }
}

/** The counts, which keep one place in the order between them. */
private val UNREAD_SOURCES = listOf(
    "com.wanderwildwood.kotozute" to R.string.source_messaging,
    "com.wanderwildwood.tayori" to R.string.source_email,
)

/** The door under each count's switch: where it goes, and the name of the switch it lands on. */
private val OWN_SWITCH_DOORS = mapOf(
    "com.wanderwildwood.kotozute" to (R.string.door_messaging to R.string.door_messaging_switch),
    "com.wanderwildwood.tayori" to (R.string.door_email to R.string.door_email_switch),
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
