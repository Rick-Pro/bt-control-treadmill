package io.github.rickpro.stride.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.rickpro.stride.R
import io.github.rickpro.stride.ui.theme.StrideColors

/** A plain title row with a back arrow and room for actions on the right. No Material top app bar, so no extra color. */
@Composable
fun StrideTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = StrideColors.Text)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = StrideColors.Text,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** An icon button drawn from one of the app's own vector drawables. */
@Composable
fun StrideIconButton(iconRes: Int, contentDescription: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) StrideColors.Text else StrideColors.Muted,
            modifier = Modifier.size(24.dp),
        )
    }
}
