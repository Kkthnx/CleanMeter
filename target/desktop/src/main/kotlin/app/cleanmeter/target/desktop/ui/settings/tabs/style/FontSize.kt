package app.cleanmeter.target.desktop.ui.settings.tabs.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cleanmeter.core.designsystem.LocalColorScheme
import app.cleanmeter.target.desktop.model.OverlaySettings
import app.cleanmeter.target.desktop.ui.components.SliderThumb
import app.cleanmeter.target.desktop.ui.components.coercedValueAsFraction
import app.cleanmeter.target.desktop.ui.components.drawTrack
import app.cleanmeter.target.desktop.ui.components.section.CollapsibleSection

private const val MIN_SCALE = 0.75f
private const val MAX_SCALE = 1.75f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontSize(
    overlaySettings: OverlaySettings,
    onFontScaleChange: (Float) -> Unit
) {
    CollapsibleSection(
        title = "FONT SIZE",
        tooltip = "Scale the whole overlay up or down. Larger is easier to read from across the room.",
    ) {
        Column {
            val inactiveTrackColor = LocalColorScheme.current.background.surfaceSunkenSubtle
            val activeTrackColor = LocalColorScheme.current.background.brand
            val inactiveTickColor = LocalColorScheme.current.background.surfaceSunken
            val activeTickColor = LocalColorScheme.current.background.brandHover
            Slider(
                value = overlaySettings.fontScale,
                onValueChange = {
                    onFontScaleChange(it.coerceIn(MIN_SCALE, MAX_SCALE))
                },
                valueRange = MIN_SCALE..MAX_SCALE,
                steps = 3,
                track = { sliderState ->
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    ) {
                        drawTrack(
                            tickFractions = FloatArray(sliderState.steps + 2) { it.toFloat() / (sliderState.steps + 1) },
                            activeRangeStart = 0f,
                            activeRangeEnd = sliderState.coercedValueAsFraction,
                            inactiveTrackColor = inactiveTrackColor,
                            activeTrackColor = activeTrackColor,
                            inactiveTickColor = inactiveTickColor,
                            activeTickColor = activeTickColor,
                        )
                    }
                },
                thumb = {
                    SliderThumb()
                }
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = "A", fontSize = 12.sp, color = LocalColorScheme.current.icon.bolderActive)
                Text(text = "A", fontSize = 20.sp, color = LocalColorScheme.current.icon.bolderActive)
            }
        }
    }
}
