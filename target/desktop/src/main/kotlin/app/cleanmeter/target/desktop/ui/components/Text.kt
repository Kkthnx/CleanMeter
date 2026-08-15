package app.cleanmeter.target.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cleanmeter.core.designsystem.LocalColorScheme
import app.cleanmeter.core.designsystem.LocalTypography

@Composable
internal fun SectionTitle(title: String, tooltip: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = LocalTypography.current.labelMSemiBold.copy(
                letterSpacing = 1.sp,
            ),
            color = LocalColorScheme.current.text.paragraph1,
        )
        if (tooltip != null) {
            InfoTooltip(text = tooltip) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "What this does",
                    tint = LocalColorScheme.current.icon.subtle,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
