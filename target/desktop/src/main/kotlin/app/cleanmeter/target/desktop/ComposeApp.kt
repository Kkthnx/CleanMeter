package app.cleanmeter.target.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.application
import androidx.lifecycle.viewmodel.compose.viewModel
import app.cleanmeter.core.common.reporting.ApplicationParams
import app.cleanmeter.core.os.ProcessManager
import app.cleanmeter.target.desktop.ui.overlay.OverlayWindow
import app.cleanmeter.target.desktop.ui.settings.SettingsWindow
import com.github.kwhat.jnativehook.GlobalScreen

fun composeApp() = application {
    val viewModel: MainViewModel = viewModel(ApplicationViewModelStoreOwner)

    val state by viewModel.state.collectAsState(MainState())

    var overlayPosition by remember {
        mutableStateOf(
            IntOffset(
                state.overlaySettings.positionX,
                state.overlaySettings.positionY
            )
        )
    }

    // jnativehook's hook thread is non-daemon, so exitApplication() alone
    // never lets the JVM actually terminate unless the hook is unregistered
    // first. Every window's close path routes through this one function so
    // none of them can skip that and leave a zombie process behind, which
    // then blocks the next launch via the single-instance port check.
    val quit: () -> Unit = {
        try {
            GlobalScreen.unregisterNativeHook()
        } catch (e: Exception) {
        }
        if (!ApplicationParams.isAutostart) {
            ProcessManager.stop()
        }
        exitApplication()
    }

    OverlayWindow(
        onPositionChanged = {
            if (!state.overlaySettings.isPositionLocked) {
                overlayPosition = it
            }
        },
        quit = quit,
    )

    SettingsWindow(
        isDarkTheme = state.overlaySettings.isDarkTheme,
        getOverlayPosition = { overlayPosition },
        quit = quit,
    )
}