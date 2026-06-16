package app.dku.embededapp.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dku.embededapp.R

// Renders the standalone laundry tips page.
@Composable
fun TipsPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 22.dp),
    ) {
        Text(
            text = stringResource(R.string.tip_header),
            color = colorResource(R.color.laundry_text),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        TipCard(
            title = stringResource(R.string.tip_towel_title),
            body = stringResource(R.string.tip_towel_body),
            modifier = Modifier.padding(top = 14.dp),
        )
        TipCard(
            title = stringResource(R.string.tip_black_title),
            body = stringResource(R.string.tip_black_body),
            modifier = Modifier.padding(top = 12.dp),
        )
        TipCard(
            title = stringResource(R.string.tip_denim_title),
            body = stringResource(R.string.tip_denim_body),
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

// Displays one static care tip in the tips page.
@Composable
private fun TipCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.laundry_surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = colorResource(R.color.laundry_primary),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                modifier = Modifier.padding(top = 8.dp),
                color = colorResource(R.color.laundry_text),
                fontSize = 14.sp,
            )
        }
    }
}

// Provides a design-time preview for the tips page.
@Preview(showBackground = true)
@Composable
private fun TipsPagePreview() {
    LaundryTheme {
        TipsPage()
    }
}
