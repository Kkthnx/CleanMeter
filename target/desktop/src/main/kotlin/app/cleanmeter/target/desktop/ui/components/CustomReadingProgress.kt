package app.cleanmeter.target.desktop.ui.components

import androidx.compose.runtime.Composable
import app.cleanmeter.core.common.hardwaremonitor.HardwareMonitorData
import app.cleanmeter.core.common.hardwaremonitor.getReading
import app.cleanmeter.target.desktop.model.OverlaySettings

@Composable
internal fun CustomReadingProgress(
    data: HardwareMonitorData,
    label: (Float) -> String,
    customReadingId: String,
    progressType: OverlaySettings.ProgressType,
    progressUnit: String,
    boundaries: OverlaySettings.Sensor.GraphSensor.Boundaries,
    zeroIsMissing: Boolean = false,
) {
    val reading = data.getReading(customReadingId)
    // A running CPU/GPU never reads 0 degrees, so a zero temperature means the
    // sensor is not actually reporting. Show "--" rather than a misleading value.
    val isMissing = reading == null || (zeroIsMissing && reading.Value <= 0f)
    // Real readings display as-is, including a genuine 0 (idle usage is a
    // real, common value, not a missing one, which "isMissing" already
    // covers separately).
    val value = reading?.Value ?: 0f

    Progress(
        value = if (isMissing) 0f else value / 100f,
        label = if (isMissing) "--" else label(value),
        unit = progressUnit,
        progressType = progressType,
        boundaries = boundaries
    )
}