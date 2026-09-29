package app.cleanmeter.target.desktop.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
data class OverlaySettings(
    val isDarkTheme: Boolean = false,
    val isHorizontal: Boolean = true,
    val positionIndex: Int = 0,
    val selectedDisplayIndex: Int = 0,
    // Windows does not guarantee display enumeration order is stable between
    // launches, especially at boot before every monitor has finished waking
    // up, so selectedDisplayIndex alone can point at the wrong monitor on a
    // later run. This id (GraphicsDevice.getIDstring) is matched first, with
    // the index as a fallback for a fresh install or a monitor that is no
    // longer connected.
    val selectedDisplayId: String = "",
    val netGraph: Boolean = false,
    val progressType: ProgressType = ProgressType.Circular,
    val positionX: Int = 0,
    val positionY: Int = 0,
    val isPositionLocked: Boolean = true,
    val opacity: Float = 1f,
    val fontScale: Float = 1f,
    val pollingRate: Long = 500,
    val isLoggingEnabled: Boolean = false,
    val showOnlyOnGame: Boolean = false,
    val showFrameLows: Boolean = false,
    // Persisted so the Monitored app dropdown can show what is actually
    // selected instead of always defaulting to index 0, and so a restart
    // can restore it: the backend itself has no memory of this across a
    // relaunch and always starts back in Auto mode.
    val selectedPresentMonApp: String = "Auto",
    val sensors: Sensors = Sensors(),
) {
    @Serializable
    @Immutable
    enum class ProgressType {
        Circular, Bar, None
    }

    @Serializable
    @Immutable
    data class Sensors(
        val framerate: Sensor.Framerate = Sensor.Framerate(),
        val frametime: Sensor.Frametime = Sensor.Frametime(),
        val cpuTemp: Sensor.CpuTemp = Sensor.CpuTemp(),
        val cpuUsage: Sensor.CpuUsage = Sensor.CpuUsage(),
        val cpuConsumption: Sensor.CpuConsumption = Sensor.CpuConsumption(),
        val gpuTemp: Sensor.GpuTemp = Sensor.GpuTemp(),
        val gpuUsage: Sensor.GpuUsage = Sensor.GpuUsage(),
        val vramUsage: Sensor.VramUsage = Sensor.VramUsage(),
        val gpuConsumption: Sensor.GpuConsumption = Sensor.GpuConsumption(),
        val totalVramUsed: Sensor.TotalVramUsed = Sensor.TotalVramUsed(),
        val ramUsage: Sensor.RamUsage = Sensor.RamUsage(),
        val upRate: Sensor.UpRate = Sensor.UpRate(),
        val downRate: Sensor.DownRate = Sensor.DownRate(),
    )

    @Serializable
    @Immutable
    sealed class Sensor {
        abstract val isEnabled: Boolean
        abstract val customReadingId: String

        fun isValid(): Boolean {
            return isEnabled && customReadingId.isNotBlank()
        }

        @Serializable
        @Immutable
        abstract class GraphSensor : Sensor() {
            abstract val boundaries: Boundaries

            @Serializable
            @Immutable
            data class Boundaries(
                val low: Int = 60,
                val medium: Int = 80,
                val high: Int = 90,
            )
        }

        @Serializable
        @Immutable
        data class Framerate(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = ""
        ) : Sensor()

        @Serializable
        @Immutable
        data class Frametime(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = ""
        ) : Sensor()

        @Serializable
        @Immutable
        data class CpuTemp(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
            override val boundaries: Boundaries = Boundaries(),
        ) : GraphSensor()

        @Serializable
        @Immutable
        data class CpuUsage(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
            override val boundaries: Boundaries = Boundaries(),
        ) : GraphSensor()

        @Serializable
        @Immutable
        data class CpuConsumption(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
        ) : Sensor()

        @Serializable
        @Immutable
        data class GpuTemp(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
            override val boundaries: Boundaries = Boundaries(),
        ) : GraphSensor()

        @Serializable
        @Immutable
        data class GpuUsage(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
            override val boundaries: Boundaries = Boundaries(),
        ) : GraphSensor()

        @Serializable
        @Immutable
        data class VramUsage(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
            override val boundaries: Boundaries = Boundaries(),
        ) : GraphSensor()

        @Serializable
        @Immutable
        data class TotalVramUsed(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
        ) : Sensor()

        @Serializable
        @Immutable
        data class GpuConsumption(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
        ) : Sensor()

        @Serializable
        @Immutable
        data class RamUsage(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = "",
            override val boundaries: Boundaries = Boundaries(),
        ) : GraphSensor()

        @Serializable
        @Immutable
        data class UpRate(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = ""
        ) : Sensor()

        @Serializable
        @Immutable
        data class DownRate(
            override val isEnabled: Boolean = true,
            override val customReadingId: String = ""
        ) : Sensor()
    }
}