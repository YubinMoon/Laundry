package app.dku.embededapp.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Embeds the camera preview and detection result Android Views in Compose.
@Composable
fun RegisterPage(
    registerViews: RegisterInteropViews,
    onStartCameraClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { registerViews.root },
        modifier = modifier.fillMaxSize(),
        update = {
            registerViews.captureButton.setOnClickListener {
                onStartCameraClicked()
            }
        },
    )
}
