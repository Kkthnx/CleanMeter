package app.cleanmeter.target.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import app.cleanmeter.core.designsystem.LocalColorScheme
import app.cleanmeter.core.designsystem.LocalTypography

/**
 * A single, consistently styled hover tooltip. Wrap any control so hovering it
 * explains what it does, instead of the bare floating text the app used before.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InfoTooltip(
    text: String,
    delayMillis: Int = 400,
    content: @Composable () -> Unit,
) {
    TooltipArea(
        delayMillis = delayMillis,
        tooltip = {
            Box(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(8.dp))
                    .background(LocalColorScheme.current.background.surfaceRaised, RoundedCornerShape(8.dp))
                    .border(1.dp, LocalColorScheme.current.border.bolder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = 260.dp)
            ) {
                Text(
                    text = text,
                    style = LocalTypography.current.labelM,
                    color = LocalColorScheme.current.text.heading,
                )
            }
        },
        content = content,
    )
}
