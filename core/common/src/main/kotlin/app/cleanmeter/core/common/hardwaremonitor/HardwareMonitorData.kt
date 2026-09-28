package app.cleanmeter.core.common.hardwaremonitor

import kotlinx.serialization.Serializable

@Serializable
data class HardwareMonitorData(
    val LastPollTime: Long,
    val Hardwares: List<Hardware>,
    val Sensors: List<Sensor>,
    val PresentMonApps: List<String>,
) {
    // Built once per data frame so the overlay's many per-frame reads are O(1)
    // lookups instead of a linear scan over every sensor each time. Keeps the
    // first sensor for a given identifier to match the old firstOrNull lookup,
    // since some identifiers (e.g. a couple of GPU load sensors) are not unique.
    val sensorsByIdentifier: Map<String, Sensor> by lazy {
        buildMap { for (sensor in Sensors) putIfAbsent(sensor.Identifier, sensor) }
    }

    // Same idea for the handful of lookups that key by sensor name instead of
    // identifier (RAM usage below).
    val sensorsByName: Map<String, Sensor> by lazy {
        buildMap { for (sensor in Sensors) putIfAbsent(sensor.Name, sensor) }
    }

    @Serializable
    data class Hardware(
        val Name: String,
        val Identifier: String,
        val HardwareType: HardwareType
    )

    @Serializable
    data class Sensor(
        val Name: String,
        val Identifier: String,
        val HardwareIdentifier: String,
        val SensorType: SensorType,
        val Value: Float
    )

    enum class HardwareType(val value: Int) {
        Motherboard(0),
        SuperIO(1),
        Cpu(2),
        Memory(3),
        GpuNvidia(4),
        GpuAmd(5),
        GpuIntel(6),
        Storage(7),
        Network(8),
        Cooler(9),
        EmbeddedController(10),
        Psu(11),
        Battery(12),
        Unknown(13);

        companion object {
            fun fromValue(value: Int): HardwareType = entries.firstOrNull { it.value == value } ?: Unknown
        }
    }

    enum class SensorType(val value: Int) {
        Voltage(0),
        Current(1),
        Power(2),
        Clock(3),
        Temperature(4),
        Load(5),
        Frequency(6),
        Fan(7),
        Flow(8),
        Control(9),
        Level(10),
        Factor(11),
        Data(12),
        SmallData(13),
        Throughput(14),
        TimeSpan(15),
        Energy(16),
        Noise(17),
        Unknown(18)
        ;

        companion object {
            fun fromValue(value: Int): SensorType = entries.firstOrNull { it.value == value } ?: Unknown
        }
    }
}

fun HardwareMonitorData.gpuReadings() = readings("GPU")
fun HardwareMonitorData.cpuReadings() = readings("CPU")
fun HardwareMonitorData.networkReadings() = readings("/nic/")
fun HardwareMonitorData.readings(namePart: String): List<HardwareMonitorData.Sensor> {
    return Sensors.filter { it.Identifier.contains(namePart, true) || it.Name.contains(namePart, true) }
        .sortedBy { it.SensorType }
}
fun HardwareMonitorData.getReading(identifier: String) = sensorsByIdentifier[identifier]
fun HardwareMonitorData.getReading(identifier: String, namePart: String) = Sensors.firstOrNull { it.Identifier == identifier && it.Name.contains(namePart, true) }

val HardwareMonitorData.FPS: Int
    get() {
        val frametime = getReading("/presentmon/frametime")?.Value ?: 0f
        // No frame data (nothing presenting, or not started yet) divides by
        // zero into Infinity, which toInt() turns into Int.MAX_VALUE rather
        // than throwing, so it shows as a nonsense number instead of 0.
        return if (frametime > 0f) (1000f / frametime).toInt() else 0
    }

// Computed in the backend from every frame, so these are real percentiles and
// not something the sampled overlay could derive on its own.
val HardwareMonitorData.FPSAverage: Int
    get() = (getReading("/presentmon/fps_average")?.Value ?: 0f).toInt()

// True when the backend could not find the PawnIO driver LibreHardwareMonitor
// 0.9.6+ needs for CPU/motherboard sensors, so those are known to be reading 0.
val HardwareMonitorData.isPawnIoMissing: Boolean
    get() = getReading("/system/pawnio_missing")?.Value == 1f

val HardwareMonitorData.FPS1PercentLow: Int
    get() = (getReading("/presentmon/fps_1_low")?.Value ?: 0f).toInt()

val HardwareMonitorData.FPS01PercentLow: Int
    get() = (getReading("/presentmon/fps_01_low")?.Value ?: 0f).toInt()

val HardwareMonitorData.Frametime: Float
    get() = (getReading("/presentmon/frametime")?.Value ?: 0f).coerceAtLeast(0f)

val HardwareMonitorData.RamUsage: Float
    get() = sensorsByName["Memory Used"]?.Value?.coerceAtLeast(1f) ?: 1f

val HardwareMonitorData.RamUsagePercent: Float
    get() = RamUsage / (RamUsage + (sensorsByName["Memory Available"]?.Value?.coerceAtLeast(1f) ?: 1f))