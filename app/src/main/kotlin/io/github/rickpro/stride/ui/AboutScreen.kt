package io.github.rickpro.stride.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.rickpro.stride.R
import io.github.rickpro.stride.ui.components.StrideTopBar
import io.github.rickpro.stride.ui.components.openLink
import io.github.rickpro.stride.ui.theme.StrideColors

private const val DEVELOPER_URL = "https://ricardouzcategui.com"
private const val REPO_URL = "https://github.com/Rick-Pro/bt-control-treadmill"
private const val NOTICES_URL = "https://github.com/Rick-Pro/bt-control-treadmill/blob/main/THIRD_PARTY_NOTICES.md"

/**
 * What Stride is, who made it, and whose work it stands on. Links open in the browser with ACTION_VIEW, so the app
 * itself still has no internet permission.
 */
@Composable
fun AboutScreen(versionName: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StrideTopBar(title = stringResource(R.string.about_title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = Color.Unspecified, // keep the icon's own colors
                    modifier = Modifier.size(112.dp),
                )
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = StrideColors.Text)
                Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyLarge, color = StrideColors.Muted)
            }
            Spacer(Modifier.height(20.dp))

            Text(stringResource(R.string.about_purpose), style = MaterialTheme.typography.bodyLarge, color = StrideColors.Text)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyLarge, color = StrideColors.Text)
            Section()

            Text(stringResource(R.string.about_developer), style = MaterialTheme.typography.titleMedium, color = StrideColors.Text)
            LinkText(text = stringResource(R.string.about_developer_name), url = DEVELOPER_URL)
            LinkText(text = stringResource(R.string.about_github), url = REPO_URL)
            Section()

            Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.titleMedium, color = StrideColors.Text)
            Spacer(Modifier.height(8.dp))
            for (credit in listOf(
                R.string.about_credit_controller,
                R.string.about_credit_azmke,
                R.string.about_credit_notes,
                R.string.about_credit_libraries,
                R.string.about_credit_acsm,
            )) {
                Text(
                    text = stringResource(credit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StrideColors.Muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            LinkText(text = stringResource(R.string.about_notices), url = NOTICES_URL)
        }
    }
}

@Composable
private fun Section() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = StrideColors.Outline)
}

/** An underlined line that opens [url] in the browser. At least 48 dp tall, so it is easy to hit. */
@Composable
private fun LinkText(text: String, url: String) {
    val context = LocalContext.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = StrideColors.Accent,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.about_open_link), role = Role.Button) { openLink(context, url) }
            .padding(vertical = 12.dp),
    )
}
