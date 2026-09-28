// See https://aka.ms/new-console-template for more information

using HardwareMonitor.Monitor;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Serilog;

var builder = Host.CreateDefaultBuilder(args)
    .ConfigureServices(services => services.AddHostedService<MonitorPoller>())
    .UseWindowsService()
    .UseSerilog((context, services, loggerConfiguration) => loggerConfiguration
        .ReadFrom.Configuration(context.Configuration)
        .ReadFrom.Services(services)
        .Enrich.FromLogContext()
        .WriteTo.File(
            Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "LogFiles", "Log.txt"),
            // A new dated file each day, letting Serilog manage naming and
            // pruning itself. The previous setup built its own dated folder
            // per day and rolled nothing (Infinite), so Serilog never
            // recognized those files as related and never cleaned any of
            // them up: after months of use that is one folder per day,
            // forever, on a service meant to run continuously.
            rollingInterval: RollingInterval.Day,
            retainedFileCountLimit: 14,
            outputTemplate: "[{Timestamp:yyyy-MM-dd HH:mm:ss.fff}] [{Level}] {Message}{NewLine}{Exception}")
        .WriteTo.Console()
    );

var host = builder.Build();
host.Run();