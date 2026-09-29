// See https://aka.ms/new-console-template for more information

using System.Diagnostics;
using System.IO.Compression;

var arguments = Environment.GetCommandLineArgs();

new Updater().Update(arguments);

class Updater
{
    public void Update(string[] arguments)
    {
        var dict = ParseArguments(arguments);
        if (!dict.ContainsKey("--package") || !dict.ContainsKey("--path") || !dict.ContainsKey("--autostart")) return;
        var isAutostart = dict["--autostart"] == "true";

        // The caller (cleanmeter.exe) stops the backend itself before
        // launching this helper, since it already knows whether that means
        // stopping the svcleanmeter service or killing a plain child
        // process. What this waits for here is the caller's own process
        // exiting, since the package being extracted also contains
        // cleanmeter.exe, its jar, and the whole bundled runtime, all
        // currently loaded by that still-shutting-down process.
        if (dict.TryGetValue("--pid", out var pidStr) && int.TryParse(pidStr, out var pid))
        {
            WaitForProcessExit(pid);
        }

        RenameCurrentExecutable();
        UnzipPackageWithRetry(dict["--package"], dict["--path"]);
        LaunchApp(isAutostart, dict["--path"]);
        Environment.Exit(0);
    }

    private void WaitForProcessExit(int pid)
    {
        try
        {
            using var process = Process.GetProcessById(pid);
            process.WaitForExit(15_000);
        }
        catch (ArgumentException)
        {
            // Already exited before we even got here, which is fine.
        }
    }

    private void ChangeService(string action)
    {
        var processStartInfo = new ProcessStartInfo
        {
            CreateNoWindow = true,
            RedirectStandardOutput = true,
            UseShellExecute = false,
            FileName = "cmd.exe",
            Arguments = $"/c sc {action} svcleanmeter"
        };
        var process = new Process();
        process.StartInfo = processStartInfo;
        process.Start();
    }

    private void RenameCurrentExecutable()
    {
        var currentPath = Environment.ProcessPath!;
        var newPath = Path.ChangeExtension(currentPath, ".bak");
        File.Move(currentPath, newPath, true);
    }

    private void UnzipPackageWithRetry(string package, string destination)
    {
        // WaitForProcessExit above covers the common case, but a file can
        // stay locked briefly after a process exits (antivirus scanning it,
        // the OS finishing a delayed handle close), so this retries instead
        // of failing the whole update over a transient lock.
        const int maxAttempts = 5;
        for (var attempt = 1; attempt <= maxAttempts; attempt++)
        {
            try
            {
                ZipFile.ExtractToDirectory(package, $@"{destination}\..\", true);
                return;
            }
            catch (IOException) when (attempt < maxAttempts)
            {
                Thread.Sleep(1000);
            }
        }
    }

    private void LaunchApp(bool isAutostart, string path)
    {
        if (isAutostart)
        {
            ChangeService("start");
        }

        // Launched directly rather than through "cmd.exe /c path", since
        // cmd splits an unquoted path on its first space and this path
        // lives under Program Files on a default install.
        var processStartInfo = new ProcessStartInfo
        {
            CreateNoWindow = true,
            UseShellExecute = false,
            FileName = $@"{path}\cleanmeter.exe",
            // Autostart must carry over: without it the relaunched app does
            // not know the backend is already running as the service and
            // spawns a second one as a plain child process.
            Arguments = isAutostart ? "--autostart" : "",
        };
        Process.Start(processStartInfo);
    }

    private Dictionary<string, string> ParseArguments(string[] args)
    {
        var arguments = new Dictionary<string, string>();

        foreach (var argument in args)
        {
            var splitted = argument.Split('=');

            if (splitted.Length == 2)
            {
                arguments[splitted[0]] = splitted[1];
            }
        }

        return arguments;
    }
}
