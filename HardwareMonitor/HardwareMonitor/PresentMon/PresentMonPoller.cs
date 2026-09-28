using System.Diagnostics;
using System.Globalization;
using LibreHardwareMonitor.Hardware;
using Microsoft.Extensions.Logging;

// ReSharper disable FieldCanBeMadeReadOnly.Local
#pragma warning disable CS8618 // Non-nullable field must contain a non-null value when exiting constructor. Consider adding the 'required' modifier or declaring as nullable.

namespace HardwareMonitor.PresentMon;

public class PresentMonPoller(ILogger logger)
{
    private const string NO_SELECTED_APP = "NONE";

    private IHardware _hardware = new PresentMonHardware();
    public PresentMonSensor Displayed { get; private set; }
    public PresentMonSensor Presented { get; private set; }
    public PresentMonSensor Frametime { get; private set; }
    public PresentMonSensor FpsAverage { get; private set; }
    public PresentMonSensor Fps1PercentLow { get; private set; }
    public PresentMonSensor Fps01PercentLow { get; private set; }
    public HashSet<string> CurrentApps { get; private set; }

    // Rolling window of recent frame times used to compute average and low
    // percentiles. Fixed capacity keeps it a few seconds long at typical frame
    // rates without unbounded growth.
    private const int FrametimeWindow = 1200;
    private readonly object _frametimeLock = new();
    private readonly Queue<float> _frametimes = new();

    public Action OnUpdateApps;

    private Process _process;
    private CultureInfo _cultureInfo = (CultureInfo)CultureInfo.CurrentCulture.Clone();

    private string _currentSelectedApp = NO_SELECTED_APP;

    public async void Start(CancellationToken stoppingToken)
    {
        _cultureInfo.NumberFormat.NumberDecimalSeparator = ".";

        Displayed = new PresentMonSensor(_hardware, "displayed", 0, "Displayed Frames");
        Presented = new PresentMonSensor(_hardware, "presented", 1, "Presented Frames");
        Frametime = new PresentMonSensor(_hardware, "frametime", 2, "Frametime");
        FpsAverage = new PresentMonSensor(_hardware, "fps_average", 3, "FPS Average");
        Fps1PercentLow = new PresentMonSensor(_hardware, "fps_1_low", 4, "FPS 1% Low");
        Fps01PercentLow = new PresentMonSensor(_hardware, "fps_01_low", 5, "FPS 0.1% Low");
        CurrentApps = [];

        using var reader = new StreamReader(Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "ignored-processes.txt"));
        var text = (await reader.ReadToEndAsync())
            .Split("\n", StringSplitOptions.RemoveEmptyEntries)
            .Select(x => $"--exclude {x.Trim()}");
        var filteredApps = string.Join(" ", text);

        await TerminateCurrentPresentMon();
        var processStartInfo = new ProcessStartInfo
        {
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            FileName = "presentmon.exe",
            Arguments =
                $"--stop_existing_session --no_console_stats --output_stdout --session_name HardwareMonitor {filteredApps}",
        };
        logger.LogInformation("Starting PresentMon process with {Arguments}", processStartInfo.Arguments);

        _process = new Process();
        _process.StartInfo = processStartInfo;
        _process.OutputDataReceived += (sender, args) => ParseData(args.Data);
        _process.ErrorDataReceived += (sender, args) => logger.LogError(args.Data);

        _process.Start();
        _process.BeginOutputReadLine();
        _process.BeginErrorReadLine();

        _ = ClearCurrentAppsAsync(stoppingToken);
        await _process.WaitForExitAsync(stoppingToken);
    }

    public void Stop()
    {
        _process.Kill(true);
    }

    private void ParseData(string? argsData)
    {
        string[] parts;
        if (argsData != null)
        {
            parts = argsData.Split(",");
            CurrentApps.Add(parts[0]);

            if (_currentSelectedApp != NO_SELECTED_APP && _currentSelectedApp != parts[0])
            {
                return;
            }

            if (float.TryParse(parts[9], NumberStyles.Any, _cultureInfo, out var frametime))
            {
                Frametime.Value = frametime;
                if (frametime > 0f)
                {
                    lock (_frametimeLock)
                    {
                        _frametimes.Enqueue(frametime);
                        while (_frametimes.Count > FrametimeWindow) _frametimes.Dequeue();
                    }
                }
            }

            if (float.TryParse(parts[13], NumberStyles.Any, _cultureInfo, out var gpuTime))
            {
                Presented.Value = gpuTime;
            }

            if (float.TryParse(parts[17], NumberStyles.Any, _cultureInfo, out var displayed))
            {
                Displayed.Value = displayed;
            }
        }
    }

    public void SetSelectedApp(string appName)
    {
        // Different app means the frame history no longer applies, so start fresh.
        lock (_frametimeLock) _frametimes.Clear();

        if (appName == "Auto")
        {
            _currentSelectedApp = NO_SELECTED_APP;
            return;
        }

        _currentSelectedApp = appName;
    }

    // Recompute average and low-percentile FPS from the recent frame-time window.
    // Called once per poll from the monitor loop, not per frame.
    public void UpdateAggregates()
    {
        float[] frames;
        lock (_frametimeLock)
        {
            if (_frametimes.Count == 0)
            {
                FpsAverage.Value = 0f;
                Fps1PercentLow.Value = 0f;
                Fps01PercentLow.Value = 0f;
                return;
            }
            frames = _frametimes.ToArray();
        }

        Array.Sort(frames); // ascending frame time (slowest frames last)

        double sum = 0;
        foreach (var f in frames) sum += f;
        var mean = sum / frames.Length;

        FpsAverage.Value = mean > 0 ? (float)(1000.0 / mean) : 0f;
        Fps1PercentLow.Value = ToFps(PercentileFrametime(frames, 0.99));
        Fps01PercentLow.Value = ToFps(PercentileFrametime(frames, 0.999));
    }

    private static float ToFps(double frametimeMs) => frametimeMs > 0 ? (float)(1000.0 / frametimeMs) : 0f;

    // The frame time N% of frames stay under. p=0.99 gives the worst 1% boundary.
    private static double PercentileFrametime(float[] sortedAscending, double p)
    {
        if (sortedAscending.Length == 0) return 0;
        var index = (int)Math.Ceiling(p * sortedAscending.Length) - 1;
        index = Math.Clamp(index, 0, sortedAscending.Length - 1);
        return sortedAscending[index];
    }

    private async Task TerminateCurrentPresentMon()
    {
        var processStartInfo = new ProcessStartInfo
        {
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            FileName = "presentmon.exe",
            Arguments =
                $"--terminate_existing_session --no_console_stats --output_stdout --session_name HardwareMonitor",
        };
        logger.LogInformation("Starting PresentMon process with {Arguments}", processStartInfo.Arguments);

        var process = new Process();
        process.StartInfo = processStartInfo;
        process.Start();
        await process.WaitForExitAsync();
    }

    private async Task ClearCurrentAppsAsync(CancellationToken cancellationToken)
    {
        if (cancellationToken.IsCancellationRequested) return;
        await Task.Delay(10_000, cancellationToken);
        OnUpdateApps?.Invoke();
        CurrentApps.Clear();
        _ = ClearCurrentAppsAsync(cancellationToken);
    }
}