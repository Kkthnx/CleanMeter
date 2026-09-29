package app.cleanmeter.core.os.hardwaremonitor

import app.cleanmeter.core.os.util.isDev
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.*
import java.util.concurrent.TimeUnit

object HardwareMonitorProcessManager {
    private var process: Process? = null

    fun start() {
        // Windows named pipes allow multiple server instances of the same
        // name to coexist, and a client connecting does not get to pick
        // which one it lands on. A stale HardwareMonitor.exe left over from
        // an earlier session (a crash, or a prior CleanMeter build that
        // could leave its own process behind on exit) is still a live pipe
        // server, so a fresh launch here can silently end up talking to
        // that dead instance instead of its own, which looks exactly like
        // every stat freezing right after a clean restart. Only one
        // instance of this app should ever be running (SingleInstance),
        // so any HardwareMonitor.exe still alive at this point is stale.
        ProcessHandle.allProcesses()
            .filter { it.info().command().map { cmd -> cmd.endsWith("HardwareMonitor.exe") }.orElse(false) }
            .forEach { it.destroyForcibly() }

        val currentDir = Path.of("").toAbsolutePath().toString()
        val file = if (isDev()) {
            "$currentDir\\HardwareMonitor\\HardwareMonitor\\bin\\Release\\net8.0\\win-x64\\native\\HardwareMonitor.exe"
        } else {
            "$currentDir\\app\\resources\\HardwareMonitor.exe"
        }

        // Launched directly rather than through "cmd.exe /c file", since cmd
        // splits an unquoted path on its first space and this path lives under
        // Program Files on a default install, which breaks the launch there.
        process = ProcessBuilder(file).start()

        val scannerIn = Scanner(process!!.inputStream)
        val scannerErr = Scanner(process!!.errorStream)

        CoroutineScope(Dispatchers.IO).launch {
            while (scannerIn.hasNextLine()) {
                System.out.println(scannerIn.nextLine())
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            while (scannerErr.hasNextLine()) {
                System.err.println(scannerErr.nextLine())
            }
        }
    }

    fun stop() {
        val proc = process ?: return
        // destroy() only requests termination, it does not wait for it, so
        // an update applied right after this call could still find these
        // exe files locked. Callers that need the backend genuinely gone
        // (an update about to overwrite its own files) rely on this
        // actually blocking rather than firing and forgetting.
        val descendants = proc.descendants().toList()
        descendants.forEach(ProcessHandle::destroy)
        proc.destroy()

        descendants.forEach { runCatching { it.onExit().get(5, TimeUnit.SECONDS) } }
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            descendants.forEach { if (it.isAlive) it.destroyForcibly() }
            proc.destroyForcibly()
        }

        process = null
    }

    fun createService() {
        val currentDir = Path.of("").toAbsolutePath().toString()
        val file = "$currentDir\\app\\resources\\HardwareMonitor.exe"
        val command = listOf(
            "cmd.exe",
            "/c",
            "sc create svcleanmeter displayname= \"CleanMeter Service\" binPath= \"$file\" start= auto group= LocalServiceNoNetworkFirewall"
        )
        ProcessBuilder().apply {
            command(command)
        }.start()
    }

    fun stopService() {
        // "sc stop" returns as soon as the stop request is accepted, not
        // once the service actually stops, which matters to any caller that
        // needs HardwareMonitor.exe genuinely gone (an update about to
        // overwrite it). Polling "sc query" until it reports STOPPED closes
        // that gap; the request itself is still given a moment to land
        // first.
        ProcessBuilder("cmd.exe", "/c", "sc stop svcleanmeter").start().waitFor(5, TimeUnit.SECONDS)

        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val query = ProcessBuilder("cmd.exe", "/c", "sc query svcleanmeter").start()
            val output = query.inputStream.bufferedReader().readText()
            query.waitFor(2, TimeUnit.SECONDS)
            if ("STOPPED" in output) return
            Thread.sleep(500)
        }
    }

    fun deleteService() {
        ProcessBuilder().apply {
            command(
                "cmd.exe",
                "/c",
                "sc delete svcleanmeter"
            )
        }.start()
    }

}