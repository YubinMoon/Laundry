package app.dku.embededapp.ui.compose

import androidx.annotation.ColorRes
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shows a compact status label used by summary and group cards.
@Composable
fun StatusBadge(
    text: String,
    @ColorRes foreground: Int,
    @ColorRes background: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = colorResource(background),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = colorResource(foreground),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
