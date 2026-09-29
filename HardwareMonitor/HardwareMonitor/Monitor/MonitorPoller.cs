#pragma warning disable CS8601 // Possible null

using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using HardwareMonitor.PresentMon;
using HardwareMonitor.SharedMemory;
using HardwareMonitor.Sockets;
using HardwareMonitor.Status;
using LibreHardwareMonitor.Hardware;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace HardwareMonitor.Monitor;

public class MonitorPoller(
    IHostApplicationLifetime hostApplicationLifetime,
    ILogger<MonitorPoller> logger
) : BackgroundService
{
    private readonly Computer _computer = new()
    {
        IsCpuEnabled = true,
        IsGpuEnabled = true,
        IsMemoryEnabled = true,
        IsMotherboardEnabled = true,
        IsControllerEnabled = true,
        IsNetworkEnabled = true,
        IsPsuEnabled = true,
        IsBatteryEnabled = true,
        IsStorageEnabled = true,
    };

    private PipeHost _socketHost = new(logger);
    private readonly PresentMonPoller _presentMonPoller = new(logger);
    private readonly IHardware _systemHardware = new SystemHardware();
    private SystemSensor _pawnIoMissingSensor = null!; // set at the start of ExecuteAsync

    private short _pollingRate = 500;
    private const short MinimalPollingRate = 33;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation("Starting monitor");

        _pawnIoMissingSensor = new SystemSensor(_systemHardware, "pawnio_missing", 0, "PawnIO Missing")
        {
            Value = DriverCheck.IsPawnIoInstalled() ? 0f : 1f
        };
        if (_pawnIoMissingSensor.Value == 1f)
        {
            logger.LogWarning(
                "PawnIO is not installed. LibreHardwareMonitor 0.9.6+ needs it for CPU/motherboard " +
                "sensors and will report 0 for them without it. Install it from https://pawnio.eu/");
        }

        _computer.Open();
        _computer.Accept(new UpdateVisitor());
        _presentMonPoller.Start(stoppingToken);
        _presentMonPoller.OnUpdateApps += SendPresentMonAppsToClients;
        _socketHost.StartServer();
        _socketHost.OnClientData += OnClientData;
        _socketHost.OnClientConnected += OnClientConnected;

        var sharedMemoryData = QueryHardwareData();

        using var memoryStream = new MemoryStream();
        using var writer = new BinaryWriter(memoryStream);

        WriteDataToStream(writer, sharedMemoryData);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                if (!_socketHost.HasConnections())
                {
                    //logger.LogInformation("No clients connected, waiting for connections...");
                    await Task.Delay(1000, stoppingToken);
                    continue;
                }

                foreach (var hardware in sharedMemoryData.Hardwares)
                {
                    try
                    {
                        hardware.Update();
                    }
                    catch
                    {
                        hardware.StopUpdates();
                        logger.LogError("Stopping updates of {HardwareName} - {HardwareIdentifier}", hardware.Name, hardware.Identifier);
                    }
                }

                _presentMonPoller.UpdateAggregates();
                WriteDataToStream(writer, sharedMemoryData);

                if (_socketHost.HasConnections())
                {
                    await _socketHost.SendToAllAsync(memoryStream.ToArray());
                } else
                {
                    //logger.LogInformation("No clients connected, not sending data");
                }

                await Task.Delay(_pollingRate, stoppingToken);
            }
            catch (OperationCanceledException)
            {
                throw;
            }
            catch (Exception ex)
            {
                // BackgroundService does not restart or crash the process on
                // an unhandled exception here by default, it just silently
                // stops this loop forever while the app keeps running, which
                // looks exactly like every stat freezing with no crash and
                // no way to recover short of restarting the whole app. One
                // bad iteration must not be allowed to end the loop.
                logger.LogError(ex, "Unhandled error in the monitor poll loop, continuing");
                await Task.Delay(_pollingRate, stoppingToken);
            }
        }

        Stop();
        hostApplicationLifetime.StopApplication();
    }

    private static void WriteDataToStream(BinaryWriter writer, SharedMemoryData sharedMemoryData)
    {
        writer.Seek(0, SeekOrigin.Begin);
        writer.Write((short)MonitorPacketCommand.Data);
        writer.Write(sharedMemoryData.Hardwares.Count);
        writer.Write(sharedMemoryData.Sensors.Count);

        foreach (var hardware in sharedMemoryData.Hardwares)
        {
            writer.Write((short)hardware.Name.Length);
            writer.Write((short)hardware.Identifier.Length);
            writer.Write(Encoding.UTF8.GetBytes(hardware.Name));
            writer.Write(Encoding.UTF8.GetBytes(hardware.Identifier));
            writer.Write((int)hardware.HardwareType);
        }

        foreach (var sensor in sharedMemoryData.Sensors)
        {
            var value = sensor.HardwareSensor.Value ?? 0f;
            var floatValue = (IsNaN(value) ? 0f : value).ToString(CultureInfo.InvariantCulture);
            sensor.Value = float.Parse(floatValue, CultureInfo.InvariantCulture);

            writer.Write((short)sensor.Name.Length);
            writer.Write((short)sensor.Identifier.Length);
            writer.Write((short)sensor.HardwareIdentifier.Length);
            writer.Write(Encoding.UTF8.GetBytes(sensor.Name));
            writer.Write(Encoding.UTF8.GetBytes(sensor.Identifier));
            writer.Write(Encoding.UTF8.GetBytes(sensor.HardwareIdentifier));
            writer.Write((int)sensor.SensorType);
            writer.Write((float)sensor.Value);
        }
    }

    private void OnClientConnected()
    {
        SendPresentMonAppsToClients();
    }

    private void OnClientData(byte[] data)
    {
        // A truncated or corrupted packet here would otherwise throw out of
        // PipeHost's read loop and force a reconnect. Not fatal to the
        // service the way the PresentMon parsing was, but still cheap to
        // guard: drop the bad packet and keep the connection.
        if (data.Length < 2)
        {
            logger.LogWarning("Received a packet too short to contain a command, ignoring it");
            return;
        }

        var cmd = (MonitorPacketCommand)BitConverter.ToInt16(data, 0);
        logger.LogInformation("Received command from client: {Command}", cmd);
        switch (cmd)
        {
            case MonitorPacketCommand.RefreshPresentMonApps:
                SendPresentMonAppsToClients();
                break;
            case MonitorPacketCommand.SelectPresentMonApp:
                SelectPresentMonApp(data);
                break;
            case MonitorPacketCommand.SelectPollingRate:
                SelectPollingRate(data);
                break;

            // server -> client cases
            case MonitorPacketCommand.Data:
            case MonitorPacketCommand.PresentMonApps:
                break;
            default:
                logger.LogWarning("Received an unrecognized command value {Command}, ignoring it", (short)cmd);
                break;
        }
    }

    private void SelectPollingRate(byte[] data)
    {
        if (data.Length < 4)
        {
            logger.LogWarning("Received a SelectPollingRate packet too short to read, ignoring it");
            return;
        }

        // start at 2 because the first 2 were the command
        var pollingRate = BitConverter.ToInt16(data, 2);
        _pollingRate = Math.Max(pollingRate, MinimalPollingRate);
        logger.LogInformation("Selected polling rate of {PollingRate}", _pollingRate);
    }

    private void SelectPresentMonApp(byte[] data)
    {
        if (data.Length < 4)
        {
            logger.LogWarning("Received a SelectPresentMonApp packet too short to read, ignoring it");
            return;
        }

        // start at 2 because the first 2 were the command
        var size = BitConverter.ToInt16(data, 2);
        if (size < 0 || data.Length < 4 + size)
        {
            logger.LogWarning("Received a SelectPresentMonApp packet whose declared size does not fit, ignoring it");
            return;
        }

        var appName = Encoding.UTF8.GetString(data, 4, size);
        _presentMonPoller.SetSelectedApp(appName);
    }

    private void SendPresentMonAppsToClients()
    {
        using var memoryStream = new MemoryStream();
        using var writer = new BinaryWriter(memoryStream);

        // A single snapshot rather than reading CurrentApps.Count and then
        // separately enumerating CurrentApps: HashSet<string> is not
        // thread-safe, and this runs on whatever thread accepted the pipe
        // connection while PresentMon's output thread can be adding to it
        // at the same moment. Reading Count then enumerating separately
        // could disagree with each other and corrupt this packet's framing.
        var apps = _presentMonPoller.SnapshotCurrentApps();
        writer.Write((short)MonitorPacketCommand.PresentMonApps);
        writer.Write((short)apps.Length);
        foreach (var app in apps)
        {
            writer.Write(GetBytes(app, SharedMemoryConsts.NameSize));
        }

        if (_socketHost.HasConnections())
        {
            _socketHost.SendToAll(memoryStream.ToArray());
        }
    }

    private SharedMemoryData QueryHardwareData()
    {
        var hardwareList = new List<SharedMemoryHardware>();
        var sensorList = new List<SharedMemorySensor>();
        var sharedMemoryData = new SharedMemoryData();

        foreach (var hardware in _computer.Hardware)
        {
            hardwareList.Add(MapHardware(hardware));
            foreach (var subHardware in hardware.SubHardware)
            {
                hardwareList.Add(MapHardware(subHardware));
            }
        }

        foreach (var hardware in _computer.Hardware)
        {
            foreach (var sensor in hardware.Sensors)
            {
                sensor.ValuesTimeWindow = TimeSpan.Zero;
                sensorList.Add(MapSensor(sensor));
            }

            foreach (var subHardware in hardware.SubHardware)
            {
                foreach (var sensor in subHardware.Sensors)
                {
                    sensor.ValuesTimeWindow = TimeSpan.Zero;
                    sensorList.Add(MapSensor(sensor));
                }
            }
        }

        sensorList.Add(MapSensor(_presentMonPoller.Displayed));
        sensorList.Add(MapSensor(_presentMonPoller.Presented));
        sensorList.Add(MapSensor(_presentMonPoller.Frametime));
        sensorList.Add(MapSensor(_presentMonPoller.FpsAverage));
        sensorList.Add(MapSensor(_presentMonPoller.Fps1PercentLow));
        sensorList.Add(MapSensor(_presentMonPoller.Fps01PercentLow));
        sensorList.Add(MapSensor(_pawnIoMissingSensor));

        sharedMemoryData.Sensors = sensorList;
        sharedMemoryData.Hardwares = hardwareList;

        return sharedMemoryData;
    }

    private void Stop()
    {
        _computer.Close();
        _presentMonPoller.Stop();
        _socketHost.Close();
        _socketHost.OnClientData -= OnClientData;
    }

    private static SharedMemoryHardware MapHardware(IHardware hardware) => new()
    {
        Name = RemoveSpecialCharacters(hardware.Name),
        Identifier = hardware.Identifier.ToString(),
        HardwareType = hardware.HardwareType,
        Hardware = hardware
    };

    private static SharedMemorySensor MapSensor(ISensor sensor) => new()
    {
        Name = RemoveSpecialCharacters(sensor.Name),
        Identifier = sensor.Identifier.ToString(),
        SensorType = sensor.SensorType,
        Value = float.IsNaN(sensor.Value ?? 0f) ? 0f : (sensor.Value ?? 0f),
        HardwareIdentifier = sensor.Hardware.Identifier.ToString(),
        HardwareSensor = sensor
    };

    private static byte[] GetBytes(string str, int length)
    {
        return Encoding.UTF8.GetBytes(str.Length > length ? str[..length] : str.PadRight(length, '\0'));
    }

    public static string RemoveSpecialCharacters(string str)
    {
        return Regex.Replace(str, "[^a-zA-Z0-9_ .]+", "_", RegexOptions.Compiled);
    }

    public static unsafe bool IsNaN(float f)
    {
        int binary = *(int*)(&f);
        return ((binary & 0x7F800000) == 0x7F800000) && ((binary & 0x007FFFFF) != 0);
    }
}