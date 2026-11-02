package io.github.rickpro.stride.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rickpro.stride.ui.theme.NumberStyle
import io.github.rickpro.stride.ui.theme.StrideColors

/** One number with its label and unit, used for time, distance, calories and steps. */
@Composable
fun StatTile(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(StrideColors.Surface)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = StrideColors.Muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = NumberStyle.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = StrideColors.Text,
            maxLines = 1,
        )
        if (unit != null) {
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = StrideColors.Muted,
                maxLines = 1,
            )
        }
    }
}
