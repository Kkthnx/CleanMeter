package app.cleanmeter.core.os.hardwaremonitor

import app.cleanmeter.core.os.PREFERENCE_PERMISSION_CONSENT
import app.cleanmeter.core.os.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val COMMAND_SIZE = 2
private const val LENGTH_SIZE = 4

sealed class Packet {
    open fun toByteArray(): ByteArray = ByteArray(0)

    data class Data(val data: ByteArray) : Packet()
    data class PresentMonApps(val data: ByteArray) : Packet()
    data class SelectPresentMonApp(val name: String) : Packet() {
        override fun toByteArray(): ByteArray {
            val nameBytes = name.toByteArray()
            val buffer = ByteBuffer.allocate(2 + 2 + nameBytes.count()).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(Command.SelectPresentMonApp.value)
                putShort(nameBytes.size.toShort())
                put(nameBytes)
            }.array()
            return buffer
        }
    }

    data class SelectPollingRate(val interval: Short) : Packet() {
        override fun toByteArray(): ByteArray {
            val buffer = ByteBuffer.allocate(2 + 2).order(ByteOrder.LITTLE_ENDIAN).apply {
                putShort(Command.SelectPollingRate.value)
                putShort(interval)
            }.array()
            return buffer
        }
    }
}

object PipeClient {

    private val pipeName = "\\\\.\\pipe\\HardwareMonitor_31337"

    // A single bidirectional connection, not one FileInputStream plus a
    // separately opened FileOutputStream. Two separate opens meant the
    // server (which treats every connected pipe instance as a broadcast
    // target) was also pushing Data packets into the write-only connection,
    // which this client never read. That connection's buffer filled within
    // seconds of the first sendPacket() call, and the server's next
    // broadcast write to it then blocked forever inside Task.WhenAll,
    // freezing every client's updates with no exception and no crash.
    // Verified against a real named pipe server: RandomAccessFile("rw")
    // opens one handle that can both read and write.
    private var pipeFile: RandomAccessFile? = null
    private var pollingRate = 500L

    private val packetChannel = Channel<Packet>(Channel.CONFLATED)
    val packetFlow: Flow<Packet> = packetChannel.receiveAsFlow()

    init {
        if (PreferencesRepository.getPreferenceBoolean(PREFERENCE_PERMISSION_CONSENT, false)) {
            connect()
        }
    }

    private fun connect() = CoroutineScope(Dispatchers.IO).launch {
        // Only log connect state changes, otherwise a missing backend floods the
        // console with a line every polling interval.
        var reportedWaiting = false
        while (true) {
            if (!isConnected()) {
                try {
                    close()
                    pipeFile = RandomAccessFile(pipeName, "rw")
                    println("Connected to the HardwareMonitor pipe")
                    reportedWaiting = false
                } catch (ex: Exception) {
                    if (!reportedWaiting) {
                        println("Waiting for the HardwareMonitor service to start...")
                        reportedWaiting = true
                    }
                    close()
                    delay(pollingRate)
                    continue
                }
            }

            pipeFile?.let { file ->
                try {
                    while (isConnected()) {

                        // Read command (2 bytes)
                        val commandBytes = readExactly(file, COMMAND_SIZE)
                        val command = Command.fromValue(ByteBuffer.wrap(commandBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).short)

                        // Read size (4 bytes)
                        val sizeBytes = readExactly(file, LENGTH_SIZE)
                        val size = ByteBuffer.wrap(sizeBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).int

                        if (size < 0) { // Sanity check
                            break
                        }

                        // Read payload
                        val payload = readExactly(file, size)
                        when (command) {
                            Command.Data -> packetChannel.trySend(Packet.Data(payload))
                            Command.PresentMonApps -> packetChannel.trySend(Packet.PresentMonApps(payload))
                            Command.RefreshPresentMonApps -> Unit
                            Command.SelectPresentMonApp -> Unit
                            Command.SelectPollingRate -> Unit
                        }
                    }
                } catch (e: Exception) {
                    println("Error while listening for packets: ${e.message}")
                    e.printStackTrace()
                    close()
                    // Without this, a pipe that keeps opening but immediately
                    // failing to read (the backend restarting, a stale pipe
                    // instance) retries with no backoff at all, since this
                    // path skips the delay the initial-connect failure below
                    // already has.
                    delay(pollingRate)
                }
            }
        }
    }

    private fun isConnected(): Boolean {
        return pipeFile != null
    }

    // Helper function to read exactly n bytes from the pipe
    private fun readExactly(file: RandomAccessFile, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var totalRead = 0

        while (totalRead < count) {
            val bytesRead = file.read(buffer, totalRead, count - totalRead)
            if (bytesRead == -1) {
                throw IOException("Pipe closed while reading")
            }
            totalRead += bytesRead
        }

        return buffer
    }

    fun setPollingRate(pollingRate: Long) {
        println("Setting PollingRate to $pollingRate")
        this.pollingRate = pollingRate
    }

    fun sendPacket(packet: Packet) {
        pipeFile?.let { file ->
            try {
                val data = packet.toByteArray()
                file.write(data)
            } catch (e: Exception) {
                println("Error sending packet: ${e.message}")
                close()
            }
        }
    }

    fun close() {
        try {
            pipeFile?.close()
        } catch (e: Exception) {
            println("Error closing pipe: ${e.message}")
        } finally {
            pipeFile = null
        }
    }
}
