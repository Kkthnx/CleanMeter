package app.cleanmeter.core.os

import java.io.File

// The app runs from its own install directory (Program Files on a default
// install) and, since it always relaunches itself elevated, writes there
// succeed rather than fail loudly. That made it easy to miss that
// recordings, exported logs, and downloaded updates were all landing inside
// the Program Files folder itself via bare relative File(...) paths, rather
// than a normal user-writable location, where they get lost on reinstall
// or uninstall and clutter what should be a clean program directory.
fun appDataDir(subfolder: String): File {
    val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }
        ?: (System.getProperty("user.home") + "\\AppData\\Local")
    val dir = File(base, "CleanMeter\\$subfolder")
    dir.mkdirs()
    return dir
}
