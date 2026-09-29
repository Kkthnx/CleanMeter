package app.cleanmeter.target.desktop.ui.overlay.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cleanmeter.core.common.hardwaremonitor.HardwareMonitorData
import app.cleanmeter.core.common.hardwaremonitor.getReading
import app.cleanmeter.target.desktop.model.OverlaySettings
import app.cleanmeter.target.desktop.ui.ColorTokens.Cyan
import app.cleanmeter.target.desktop.ui.ColorTokens.OffWhite
import app.cleanmeter.target.desktop.ui.ColorTokens.Purple
import app.cleanmeter.target.desktop.ui.components.Pill
import app.cleanmeter.target.desktop.ui.overlay.conditional

@Composable
internal fun NetSection(overlaySettings: OverlaySettings, data: HardwareMonitorData) {
    if (overlaySettings.sensors.upRate.isValid() || overlaySettings.sensors.downRate.isValid()) {
        if (overlaySettings.isHorizontal) {
            Pill(
                title = "NET",
                isHorizontal = true,
            ) {
                if (overlaySettings.sensors.downRate.isValid()) {
                    val dlRate = data.getReading(overlaySettings.sensors.downRate.customReadingId)?.Value ?: 0f
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(
                            painterResource("icons/arrow_down.svg"),
                            "",
                            tint = Cyan,
                            modifier = Modifier.padding(end = 4.dp, bottom = 3.dp).alpha(dlRate.coerceAtMost(1f))
                        )
                    }
                }

                if (overlaySettings.sensors.upRate.isValid()) {
                    val upRate = data.getReading(overlaySettings.sensors.upRate.customReadingId)?.Value ?: 0f
                    Row(verticalAlignment = Alignment.Bottom) {
                        Icon(
                            painterResource("icons/arrow_down.svg"),
                            "",
                            tint = Purple,
                            modifier = Modifier.padding(end = 4.dp, bottom = 3.dp).rotate(180f).alpha(upRate.coerceAtMost(1f))
                        )
                    }
                }

                if (overlaySettings.netGraph) {
                    NetGraph(data = data, isHorizontal = true, overlaySettings = overlaySettings)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "NET",
                        fontSize = 10.sp,
                        color = OffWhite,
                        lineHeight = 0.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (overlaySettings.sensors.downRate.isValid()) {
                            val dlRate = data.getReading(overlaySettings.sensors.downRate.customReadingId)?.Value ?: 0f
                            Row(verticalAlignment = Alignment.Bottom) {
                                Icon(
                                    painterResource("icons/arrow_down.svg"),
                                    "",
                                    tint = Cyan,
                                    modifier = Modifier.padding(end = 4.dp, bottom = 3.dp).alpha(dlRate.coerceAtMost(1f))
                                )
                            }
                        }

                        if (overlaySettings.sensors.upRate.isValid()) {
                            val upRate = data.getReading(overlaySettings.sensors.upRate.customReadingId)?.Value ?: 0f
                            Row(verticalAlignment = Alignment.Bottom) {
                                Icon(
                                    painterResource("icons/arrow_down.svg"),
                                    "",
                                    tint = Purple,
                                    modifier = Modifier.padding(end = 4.dp, bottom = 3.dp).rotate(180f).alpha(upRate.coerceAtMost(1f))
                                )
                            }
                        }
                    }
                }

                if (overlaySettings.netGraph) {
                    NetGraph(data = data, isHorizontal = false, overlaySettings = overlaySettings)
                }
            }
        }
    }
}

@Composable
private fun NetGraph(data: HardwareMonitorData, isHorizontal: Boolean, overlaySettings: OverlaySettings) {
    if (!overlaySettings.sensors.upRate.isEnabled && !overlaySettings.sensors.downRate.isEnabled) return

    val listSize = 30
    // Raw rates, not pre-normalized, so the scale is recomputed fresh from
    // whichever window is currently on screen at draw time.
    val upRatePoints = remember { mutableStateListOf<Float>() }
    val downRatePoints = remember { mutableStateListOf<Float>() }

    val upRatePaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = Purple
            strokeWidth = 1f
            blendMode = BlendMode.DstAtop
        }
    }
    val downRatePaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = Cyan
            strokeWidth = 1f
            blendMode = BlendMode.DstAtop
        }
    }
    // Constant, so hoisted out of the draw phase instead of allocating a new
    // list and brush on every redraw. horizontalGradient resolves against
    // the actual draw size each time, so the same Brush instance is safe to
    // reuse across draws.
    val fadeBrush = remember { Brush.horizontalGradient(listOf(Color.Transparent, Color.Black, Color.Black, Color.Black, Color.Transparent)) }

    LaunchedEffect(data) {
        val dlRate = data.getReading(overlaySettings.sensors.downRate.customReadingId)?.Value ?: 0f
        val upRate = data.getReading(overlaySettings.sensors.upRate.customReadingId)?.Value ?: 0f

        upRatePoints.add(upRate)
        downRatePoints.add(dlRate)
        if (upRatePoints.size > listSize) upRatePoints.removeFirst()
        if (downRatePoints.size > listSize) downRatePoints.removeFirst()
    }

    Box(modifier = Modifier
        .conditional(
            predicate = isHorizontal,
            ifTrue = { width(100.dp) },
            ifFalse = { fillMaxWidth() },
        )
        .height(if (isHorizontal) 45.dp else 30.dp)
        .graphicsLayer { alpha = 0.99f }
        .drawWithContent {
            // Each line scales to the larger of its own window and the
            // other's, so one direction maxing out does not make the other
            // look artificially busier than it is.
            val largest = maxOf(
                upRatePoints.maxOrNull() ?: 0f,
                downRatePoints.maxOrNull() ?: 0f,
            ).coerceAtLeast(1f)
            val upRateZip = upRatePoints.map { (it / largest).coerceIn(0f, 1f) }.zipWithNext()
            val downRateZip = downRatePoints.map { (it / largest + .2f).coerceIn(0f, 1f) }.zipWithNext()

            drawIntoCanvas { canvas ->
                if (overlaySettings.sensors.upRate.isEnabled) {
                    drawLine(upRateZip, listSize, canvas, upRatePaint)
                }
                if (overlaySettings.sensors.downRate.isEnabled) {
                    drawLine(downRateZip, listSize, canvas, downRatePaint)
                }
            }
            drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
        })
}
