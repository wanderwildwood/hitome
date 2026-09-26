package com.wanderwildwood.hitome

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.hitome.ui.AboutDialog
import com.wanderwildwood.hitome.ui.BarButton
import com.wanderwildwood.hitome.ui.Icons
import com.wanderwildwood.hitome.ui.monochrome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThemeMMD(colorScheme = monochrome) { MainScreen() } }
    }
}

/**
 * The one screen: whether the panel is on, and where its lines come from. There is nothing to
 * set here - each app's own settings say whether it shows - so the screen is a door to the
 * accessibility switch and a list of the apps that take part.
 */
@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var aboutOpen by remember { mutableStateOf(false) }
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
                TextMMD(
                    text = stringResource(R.string.sources_heading),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                )
            }
            SOURCES.forEach { (pkg, label) ->
                item(key = pkg) {
                    val here = installed[pkg] == true
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

/** The apps that take part, in the order the panel draws them. */
private val SOURCES = listOf(
    "com.wanderwildwood.koyomi" to R.string.source_calendar,
    "com.wanderwildwood.soramoyo" to R.string.source_sky,
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
