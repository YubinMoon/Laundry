package app.dku.embededapp.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dku.embededapp.R

// Renders the dashboard with priority recommendation, summary, and wash order.
@Composable
fun HomePage(
    state: LaundryHomeState,
    onRegisterClick: () -> Unit,
    onViewGroupsClick: () -> Unit,
    onRecommendationClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 22.dp),
    ) {
        PriorityCard(
            recommendation = state.priorityRecommendation,
            onRegisterClick = onRegisterClick,
            onRecommendationClick = onRecommendationClick,
        )
        Text(
            text = stringResource(R.string.summary_title),
            modifier = Modifier.padding(top = 25.dp),
            color = colorResource(R.color.laundry_text),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        SummaryStatsCard(
            summary = state.summary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recommend_title),
                modifier = Modifier.weight(1f),
                color = colorResource(R.color.laundry_text),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onViewGroupsClick) {
                Text(stringResource(R.string.view_all))
            }
        }
        RecommendedOrderCard(
            recommendations = state.recommendations,
            onRecommendationClick = onRecommendationClick,
        )
    }
}

// Highlights the best group to wash next and links to registration or details.
@Composable
private fun PriorityCard(
    recommendation: LaundryHomeRecommendation?,
    onRegisterClick: () -> Unit,
    onRecommendationClick: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.laundry_primary)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(
                    text = stringResource(if (recommendation == null) R.string.priority_empty_badge else R.string.priority_badge),
                    foreground = R.color.laundry_success,
                    background = R.color.laundry_success_background,
                )
                recommendation?.groupId?.let { groupId ->
                    Button(
                        onClick = { onRecommendationClick(groupId) },
                        modifier = Modifier.padding(start = 10.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.white),
                            contentColor = colorResource(R.color.laundry_primary_dark),
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.view_details),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                text = recommendation?.name ?: stringResource(R.string.priority_empty_title),
                modifier = Modifier.padding(top = 17.dp),
                color = colorResource(R.color.white),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = priorityReason(recommendation),
                modifier = Modifier.padding(top = 6.dp),
                color = colorResource(R.color.white).copy(alpha = 0.85f),
                fontSize = 14.sp,
            )
            Button(
                onClick = onRegisterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.white),
                    contentColor = colorResource(R.color.laundry_primary_dark),
                ),
            ) {
                Text(stringResource(R.string.start_register))
            }
        }
    }
}

// Summarizes saved laundry and pending/done group counts.
@Composable
private fun SummaryStatsCard(
    summary: LaundryHomeSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.laundry_surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.summary_total_laundry),
                    color = colorResource(R.color.laundry_text_muted),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = summary.totalLaundryCount.toString(),
                    modifier = Modifier.padding(top = 6.dp),
                    color = colorResource(R.color.laundry_primary),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.summary_items_saved),
                    color = colorResource(R.color.laundry_text_muted),
                    fontSize = 13.sp,
                )
            }
            Box(
                modifier = Modifier
                    .height(82.dp)
                    .width(1.dp)
                    .background(colorResource(R.color.laundry_line)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SummaryGroupMetric(
                    label = stringResource(R.string.summary_pending_groups),
                    value = summary.pendingGroupCount,
                )
                SummaryGroupMetric(
                    label = stringResource(R.string.summary_done_groups),
                    value = summary.doneGroupCount,
                )
            }
        }
    }
}

// Displays a single named metric inside the summary stats card.
@Composable
private fun SummaryGroupMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = colorResource(R.color.laundry_text_muted),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value.toString(),
            modifier = Modifier.padding(start = 8.dp),
            color = colorResource(R.color.laundry_text),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Lists recommended groups in ranked order and opens details on row tap.
@Composable
private fun RecommendedOrderCard(
    recommendations: List<LaundryHomeRecommendation>,
    onRecommendationClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.laundry_surface)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            if (recommendations.isEmpty()) {
                Text(
                    text = stringResource(R.string.recommend_empty_state),
                    color = colorResource(R.color.laundry_text_muted),
                    fontSize = 14.sp,
                )
            } else {
                recommendations.forEachIndexed { index, recommendation ->
                    val rowModifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 0.dp else 17.dp)
                        .then(
                            recommendation.groupId?.let { groupId ->
                                Modifier.clickable { onRecommendationClick(groupId) }
                            } ?: Modifier,
                        )
                        .padding(vertical = 4.dp)
                    Column(modifier = rowModifier) {
                        Text(
                            text = stringResource(R.string.recommend_item_format, index + 1, recommendation.name),
                            color = colorResource(R.color.laundry_text),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(
                                R.string.recommend_detail_format,
                                formatItemCount(recommendation.itemCount),
                                formatNotWashedAge(recommendation.notWashedDays),
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                            color = colorResource(R.color.laundry_text_muted),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

// Formats the headline reason shown in the priority recommendation card.
@Composable
private fun priorityReason(recommendation: LaundryHomeRecommendation?): String {
    if (recommendation == null) {
        return stringResource(R.string.priority_empty_reason)
    }
    return stringResource(
        R.string.priority_reason_format,
        formatItemCount(recommendation.itemCount),
        formatNotWashedAge(recommendation.notWashedDays),
    )
}

// Converts an item count into the localized singular/plural label.
@Composable
private fun formatItemCount(count: Int): String {
    return pluralStringResource(R.plurals.summary_count_items, count, count)
}

// Converts group age into a localized not-washed label.
@Composable
private fun formatNotWashedAge(days: Long?): String {
    return when (days) {
        null -> stringResource(R.string.not_washed_none)
        0L -> stringResource(R.string.not_washed_today)
        else -> pluralStringResource(R.plurals.not_washed_days, days.toInt(), days)
    }
}

// Provides a design-time preview for the home dashboard.
@Preview(showBackground = true)
@Composable
private fun HomePagePreview() {
    LaundryTheme {
        HomePage(
            state = LaundryHomeState(),
            onRegisterClick = {},
            onViewGroupsClick = {},
        )
    }
}
