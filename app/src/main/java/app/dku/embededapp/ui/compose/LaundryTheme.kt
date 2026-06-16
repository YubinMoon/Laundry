package app.dku.embededapp.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import app.dku.embededapp.R

// Applies the Laundry Mate color scheme to all Compose screens.
@Composable
fun LaundryTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = colorResource(R.color.laundry_primary),
        onPrimary = colorResource(R.color.white),
        secondary = colorResource(R.color.laundry_secondary),
        background = colorResource(R.color.laundry_background),
        onBackground = colorResource(R.color.laundry_text),
        surface = colorResource(R.color.laundry_surface),
        onSurface = colorResource(R.color.laundry_text),
        surfaceVariant = colorResource(R.color.laundry_line),
        onSurfaceVariant = colorResource(R.color.laundry_text_muted),
        outline = colorResource(R.color.laundry_line),
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
