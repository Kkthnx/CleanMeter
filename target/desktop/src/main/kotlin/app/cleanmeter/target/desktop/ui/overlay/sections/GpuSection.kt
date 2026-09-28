package app.cleanmeter.target.desktop.ui.overlay.sections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cleanmeter.core.common.hardwaremonitor.HardwareMonitorData
import app.cleanmeter.core.common.hardwaremonitor.getReading
import app.cleanmeter.target.desktop.model.OverlaySettings
import app.cleanmeter.target.desktop.ui.components.CustomReadingProgress
import app.cleanmeter.target.desktop.ui.components.Pill
import app.cleanmeter.target.desktop.ui.components.Progress
import app.cleanmeter.target.desktop.ui.components.ProgressLabel
import app.cleanmeter.target.desktop.ui.components.ProgressUnit
import java.util.*
import kotlin.math.roundToInt

private fun OverlaySettings.Sensors.isAllValid(): Boolean {
    return gpuTemp.isValid() || gpuUsage.isValid() || vramUsage.isValid()
}

@Composable
internal fun GpuSection(overlaySettings: OverlaySettings, data: HardwareMonitorData) {
    if (overlaySettings.sensors.isAllValid()) {
        Pill(
            title = "GPU",
            isHorizontal = overlaySettings.isHorizontal,
        ) {
            if (overlaySettings.sensors.gpuTemp.isValid()) {
                CustomReadingProgress(
                    data = data,
                    customReadingId = overlaySettings.sensors.gpuTemp.customReadingId,
                    progressType = overlaySettings.progressType,
                    progressUnit = "°C",
                    label = { "${it.roundToInt()}" },
                    boundaries = overlaySettings.sensors.gpuTemp.boundaries,
                    zeroIsMissing = true,
                )
            }

            if (overlaySettings.sensors.gpuUsage.isValid()) {
                CustomReadingProgress(
                    data = data,
                    customReadingId = overlaySettings.sensors.gpuUsage.customReadingId,
                    progressType = overlaySettings.progressType,
                    progressUnit = "%",
                    label = { String.format("%02d", it.roundToInt(), Locale.US) },
                    boundaries = overlaySettings.sensors.gpuUsage.boundaries,
                )
            }

            if (overlaySettings.sensors.vramUsage.isValid() && overlaySettings.sensors.totalVramUsed.isValid()) {
                val vramUsageReading = data.getReading(overlaySettings.sensors.vramUsage.customReadingId, "memory")
                val totalVramUsedReading = data.getReading(overlaySettings.sensors.totalVramUsed.customReadingId)
                val isMissing = vramUsageReading == null || totalVramUsedReading == null

                Progress(
                    value = if (isMissing) 0f else (vramUsageReading?.Value ?: 0f) / 100f,
                    label = if (isMissing) "--" else String.format("%02.1f", (totalVramUsedReading?.Value ?: 0f) / 1000, Locale.US),
                    unit = "GB",
                    progressType = overlaySettings.progressType,
                    boundaries = overlaySettings.sensors.vramUsage.boundaries,
                )
            }

            if (overlaySettings.sensors.gpuConsumption.isValid()) {
                val reading = data.getReading(overlaySettings.sensors.gpuConsumption.customReadingId)
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.widthIn(min = 35.dp).padding(bottom = 2.dp)) {
                    ProgressLabel(if (reading == null) "--" else "${reading.Value.toInt()}")
                    ProgressUnit("W")
                }
            }
        }
    }
}