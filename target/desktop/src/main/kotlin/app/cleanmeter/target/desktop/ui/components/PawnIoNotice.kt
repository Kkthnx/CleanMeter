package app.cleanmeter.target.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.Icon
import androidx.compose.material.LocalUriHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.cleanmeter.core.designsystem.LocalColorScheme
import app.cleanmeter.core.designsystem.LocalTypography

// Shown when the backend cannot find the PawnIO driver it relies on, since
// LibreHardwareMonitor 0.9.6+ (that this app upgraded to for Intel Arc
// support) reads CPU and motherboard sensors through it. Without PawnIO
// installed, those sensors silently read 0, which otherwise looks like a
// plain broken sensor with no clue why.
@Composable
internal fun PawnIoNotice() {
    val uriHandler = LocalUriHandler.current
    val text = remember {
        buildAnnotatedString {
            append("CPU and motherboard sensors need the PawnIO driver, which is not installed. ")
            pushStringAnnotation("click", "https://pawnio.eu/")
            withStyle(SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                append("Install it from pawnio.eu")
            }
            pop()
            append(", then restart CleanMeter.")
        }
    }

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalColorScheme.current.background.warningSubtle, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = "Warning",
            tint = LocalColorScheme.current.icon.warning,
            modifier = Modifier.size(18.dp)
        )
        ClickableText(
            text = text,
            style = LocalTypography.current.labelSMedium.copy(color = LocalColorScheme.current.text.warning),
            onClick = { offset ->
                text.getStringAnnotations("click", offset, offset).firstOrNull()?.let {
                    uriHandler.openUri(it.item)
                }
            }
        )
    }
}
