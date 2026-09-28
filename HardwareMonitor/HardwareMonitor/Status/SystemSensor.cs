#pragma warning disable CS8618 // Non-nullable field must contain a non-null value when exiting constructor.
#pragma warning disable CS0067 // Never used
#pragma warning disable CS8625 // Cannot convert null literal to non-nullable reference type
#pragma warning disable CA1416 // This call site is reachable on all platforms (this app only ships for Windows)
using LibreHardwareMonitor.Hardware;
using Microsoft.Win32;

namespace HardwareMonitor.Status;

// A tiny synthetic hardware/sensor pair, following the same pattern as
// PresentMonHardware/PresentMonSensor, used to carry values that do not come
// from LibreHardwareMonitor itself (currently: whether the PawnIO driver it
// depends on for MSR-based sensors is installed).
public class SystemHardware : IHardware
{
    public void Accept(IVisitor visitor) { }
    public void Traverse(IVisitor visitor) { }
    public string GetReport() => "system n/a";
    public void Update() => throw new NotImplementedException();

    public HardwareType HardwareType { get; } = HardwareType.EmbeddedController;
    public Identifier Identifier { get; } = new("system");
    public string Name { get; set; } = "system";
    public IHardware Parent { get; } = null;
    public ISensor[] Sensors { get; }
    public IHardware[] SubHardware { get; }
    public IDictionary<string, string> Properties { get; }
    public event SensorEventHandler SensorAdded;
    public event SensorEventHandler SensorRemoved;
}

public class SystemSensor(IHardware hardware, string identifier, int index, string name) : ISensor
{
    public void Accept(IVisitor visitor) { }
    public void Traverse(IVisitor visitor) { }
    public void ResetMin() { }
    public void ResetMax() { }
    public void ClearValues() { }

    public IControl Control { get; }
    public IHardware Hardware { get; } = hardware;
    public Identifier Identifier { get; } = new(hardware.Identifier, identifier);
    public int Index { get; } = index;
    public bool IsDefaultHidden { get; }
    public float? Max { get; }
    public float? Min { get; }
    public string Name { get; set; } = name;
    public IReadOnlyList<IParameter> Parameters { get; }
    public SensorType SensorType { get; } = SensorType.SmallData;
    public float? Value { get; set; }
    public IEnumerable<SensorValue> Values { get; }
    public TimeSpan ValuesTimeWindow { get; set; }
}

public static class DriverCheck
{
    // Since LibreHardwareMonitor 0.9.5-pre454 / 0.9.6, MSR-based sensors
    // (CPU temperature, power, clocks, and most motherboard SuperIO sensors)
    // are read through the PawnIO kernel driver, which is not bundled and
    // must be installed separately: https://pawnio.eu/. Without it, LHM
    // silently returns 0 for everything it drives, which reads as a broken
    // CPU temperature sensor with no indication why. A service key under
    // this path exists once PawnIO's installer has run.
    public static bool IsPawnIoInstalled()
    {
        try
        {
            using var key = Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Services\PawnIO");
            return key != null;
        }
        catch
        {
            return false;
        }
    }
}
