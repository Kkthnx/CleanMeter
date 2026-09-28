package app.cleanmeter.core.os.win32

import app.cleanmeter.core.os.util.isDev
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinUser
import java.awt.Component
import java.io.File
import java.nio.file.Path
import kotlin.system.exitProcess

class WindowsService {

    var lastError: Int = 0
        private set

    fun openMemoryMapFile(filename: String): WinNT.HANDLE? {
        val memMapFile = Kernel32Impl.INSTANCE.OpenFileMapping(WinNT.SECTION_MAP_READ, false, filename)
        lastError = Kernel32Impl.INSTANCE.GetLastError()

        return memMapFile
    }

    fun openEventFile(filename: String): WinNT.HANDLE? {
        val memMapFile = Kernel32Impl.INSTANCE.OpenEvent(WinNT.SYNCHRONIZE, false, filename)
        lastError = Kernel32Impl.INSTANCE.GetLastError()
        return memMapFile
    }

    fun waitForEvent(handle: HANDLE): Boolean {
        return Kernel32Impl.INSTANCE.WaitForSingleObject(handle, 500) == WinBase.WAIT_OBJECT_0
    }

    fun closeHandle(handle: WinNT.HANDLE) {
        Kernel32Impl.INSTANCE.CloseHandle(handle)
    }

    fun mapViewOfFile(handle: WinNT.HANDLE?): Pointer? {
        handle ?: return null

        return Kernel32.INSTANCE.MapViewOfFile(handle, WinNT.SECTION_MAP_READ, 0, 0, 0)
    }

    fun unmapViewOfFile(pointer: Pointer) {
        Kernel32Impl.INSTANCE.UnmapViewOfFile(pointer)
        lastError = Kernel32Impl.INSTANCE.GetLastError()
    }

    companion object {
        // WS_EX_NOACTIVATE keeps the overlay from stealing focus from the game;
        // JNA's WinUser does not expose this constant, so define it here.
        private const val WS_EX_NOACTIVATE = 0x08000000
        private val HWND_TOPMOST = HWND(Pointer.createConstant(-1L))

        fun changeWindowTransparency(w: Component, isTransparent: Boolean) {
            val hwnd = HWND().apply { pointer = Native.getComponentPointer(w) }
            val wl = if (isTransparent) {
                User32.INSTANCE.GetWindowLong(
                    hwnd,
                    WinUser.GWL_EXSTYLE
                ) or WinUser.WS_EX_LAYERED or WinUser.WS_EX_TRANSPARENT or WS_EX_NOACTIVATE
            } else {
                User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE) or WinUser.WS_EX_LAYERED and WinUser.WS_EX_TRANSPARENT.inv()
            }
            User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, wl)
        }

        // Re-assert top-most z-order. A game entering fullscreen bumps other
        // top-most windows below it, so overlays re-assert periodically to stay
        // visible over borderless / windowed-fullscreen. This cannot draw over
        // true exclusive fullscreen, which owns the display surface.
        fun reassertTopmost(w: Component) {
            val hwnd = HWND().apply { pointer = Native.getComponentPointer(w) }
            User32.INSTANCE.SetWindowPos(
                hwnd,
                HWND_TOPMOST,
                0, 0, 0, 0,
                WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or WinUser.SWP_NOACTIVATE,
            )
        }

        fun isProcessElevated(): Boolean {
            try {
                File.createTempFile("cleanmeter", ".lock", File("C:/")).delete()
            } catch (ex: Exception) {
                return false
            }
            return true
        }

        fun tryElevateProcess(isAutostart: Boolean) {
            if (isAutostart) return
            if (!isDev() && !WindowsService.isProcessElevated()) {
                elevateProcess()
            }
        }

        fun elevateProcess() {
            val currentDir = Path.of("").toAbsolutePath().toString()
            Shell32Impl.INSTANCE.ShellExecuteW(null, "runas", "$currentDir\\cleanmeter.exe", "", "", 10)
            exitProcess(0)
        }
    }
}
