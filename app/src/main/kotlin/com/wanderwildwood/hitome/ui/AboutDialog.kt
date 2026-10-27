package com.wanderwildwood.hitome.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.hitome.BuildConfig
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.hitome.R

/**
 * What this is, what it does with what it knows, and whose work it follows - a page of its own,
 * because Privacy and Terms are longer than a dialog can hold without scrolling.
 *
 * The privacy text says what an accessibility service and a notification listener could read,
 * because a stranger cannot tell from outside how much this one reads. It has no network access.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.about_title, BuildConfig.VERSION_NAME)) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.cd_back), onBack) },
            )
        },
    ) { padding ->
        PagedColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                AboutHeading(R.string.about_privacy_heading)
                AboutText(R.string.about_privacy_1)
            }
            item { AboutText(R.string.about_privacy_2) }
            item { AboutText(R.string.about_privacy_3) }
            item { AboutText(R.string.about_privacy_4) }
            item {
                AboutHeading(R.string.about_terms_heading)
                AboutText(R.string.about_terms)
            }
            item {
                AboutHeading(R.string.about_components_heading)
                AboutText(R.string.about_after)
                AboutText(R.string.about_components)
                AboutText(R.string.about_icons)
            }
            item {
                AboutText(R.string.about_licence, Modifier.padding(top = 14.dp))
                AboutText(R.string.about_source)
            }
            item { Llama() }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AboutHeading(text: Int) {
    TextMMD(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun AboutText(text: Int, modifier: Modifier = Modifier) {
    TextMMD(
        text = stringResource(text),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

/**
 * A llama at the foot of the About, which opens the page a donation goes to.
 * The site's address sits at the start of the same line and opens the site; the llama and its words open the page.
 *
 * Three words rather than an address: a verb and an object, so what happens when you press
 * them is not a surprise even though the page is not named. The drawing is his own, and it is
 * ink rather than an emoji, which is a colour glyph and reaches the panel as a pale smudge.
 *
 * The Kompakt may have nothing registered for a web address, so the intent is allowed to fail
 * quietly rather than take the dialog down with it.
 */
@Composable
private fun Llama() {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextMMD(
            text = "wanderthe.dev",
            style = MaterialTheme.typography.labelSmall,
            // The site's address opens the site, the way the llama beside it opens its page.
            modifier = Modifier
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://wanderthe.dev")),
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    // Straight to the checkout. The Donate button on the site only leads
                    // here anyway, so the page in between is a press the reader does not need.
                    // The short square.link form, not the long checkout.square.site address it
                    // redirects to -- the short one is what the site itself links to, so a
                    // regenerated checkout follows it and a published app does not break.
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://square.link/u/AGu8oT10")),
                        )
                    }.onFailure {
                        Toast.makeText(context, context.getString(R.string.about_no_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                .heightIn(min = 48.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.llama),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(text = stringResource(R.string.about_feed_the_llamas), style = MaterialTheme.typography.labelSmall)
        }
    }
}
