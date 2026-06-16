package app.dku.embededapp.ui.compose

import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.dku.embededapp.R
import app.dku.embededapp.data.LaundryRecord
import app.dku.embededapp.data.LaundryRecordStore
import app.dku.embededapp.detection.LaundryCategory
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

object LaundryComposeHost {
    const val PAGE_HOME = 0
    const val PAGE_REGISTER = 1
    const val PAGE_GROUPS = 2
    const val PAGE_TIPS = 3

    interface Callbacks {
        fun onPageSelected(page: Int)
        fun onStartRegisterClicked()
        fun onViewGroupsClicked()
        fun onStartCameraClicked()
    }

    @JvmStatic
    fun install(activity: ComponentActivity, callbacks: Callbacks): Handles {
        val selectedPage = mutableIntStateOf(PAGE_HOME)
        val registerViews = RegisterInteropViews(activity)
        val homeController = LaundryHomeController(activity)
        val groupsController = LaundryGroupsController(activity)
        val handles = Handles(selectedPage, registerViews, homeController, groupsController)

        activity.setContent {
            LaundryTheme {
                LaundryApp(
                    selectedPage = selectedPage.intValue,
                    registerViews = registerViews,
                    homeController = homeController,
                    groupsController = groupsController,
                    callbacks = callbacks,
                )
            }
        }

        return handles
    }

    class Handles internal constructor(
        private val selectedPage: androidx.compose.runtime.MutableIntState,
        private val registerViews: RegisterInteropViews,
        private val homeController: LaundryHomeController,
        private val groupsController: LaundryGroupsController,
    ) : AutoCloseable {
        fun showPage(page: Int) {
            selectedPage.intValue = page
            registerViews.root.visibility = if (page == PAGE_REGISTER) View.VISIBLE else View.GONE
            if (page == PAGE_HOME) {
                homeController.refresh()
            }
            if (page == PAGE_GROUPS) {
                groupsController.refresh()
            }
        }

        fun isRegisterPageVisible(): Boolean = selectedPage.intValue == PAGE_REGISTER

        fun getRegisterPage(): View = registerViews.root

        fun getCameraPreview() = registerViews.cameraPreview

        fun getDetectionOverlay() = registerViews.detectionOverlay

        fun getDetectionResultViews() = registerViews.detectionResultViews

        fun refreshGroups() {
            groupsController.refresh()
        }

        fun refreshLaundryData() {
            homeController.refresh()
            groupsController.refresh()
        }

        override fun close() {
            homeController.close()
            groupsController.close()
        }
    }
}

@Composable
fun LaundryApp(
    selectedPage: Int,
    registerViews: RegisterInteropViews,
    homeController: LaundryHomeController,
    groupsController: LaundryGroupsController,
    callbacks: LaundryComposeHost.Callbacks,
) {
    val background = colorResource(R.color.laundry_background)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        containerColor = background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            LaundryNavigationBar(
                selectedPage = selectedPage,
                onPageSelected = callbacks::onPageSelected,
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Header(selectedPage = selectedPage)
            Box(modifier = Modifier.weight(1f)) {
                when (selectedPage) {
                    LaundryComposeHost.PAGE_REGISTER -> RegisterPage(
                        registerViews = registerViews,
                        onStartCameraClicked = callbacks::onStartCameraClicked,
                    )

                    LaundryComposeHost.PAGE_GROUPS -> LaundryGroupsScreen(groupsController)
                    LaundryComposeHost.PAGE_TIPS -> TipsPage()
                    else -> HomePage(
                        state = homeController.state,
                        onRegisterClick = callbacks::onStartRegisterClicked,
                        onViewGroupsClick = callbacks::onViewGroupsClicked,
                        onRecommendationClick = { groupId ->
                            groupsController.showRecommendedGroupDetails(groupId)
                        },
                    )
                }
            }
        }
    }
    if (selectedPage != LaundryComposeHost.PAGE_GROUPS) {
        LaundryGroupDialogs(
            controller = groupsController,
            onDataChanged = homeController::refresh,
        )
    }
}

@Composable
private fun Header(selectedPage: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 18.dp, end = 24.dp, bottom = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_label),
            color = colorResource(R.color.laundry_primary),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.16.sp,
        )
        Text(
            text = stringResource(titleFor(selectedPage)),
            modifier = Modifier.padding(top = 10.dp),
            color = colorResource(R.color.laundry_text),
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(subtitleFor(selectedPage)),
            modifier = Modifier.padding(top = 5.dp),
            color = colorResource(R.color.laundry_text_muted),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LaundryNavigationBar(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
) {
    NavigationBar(
        containerColor = colorResource(R.color.laundry_surface),
    ) {
        navItems().forEach { item ->
            NavigationBarItem(
                selected = selectedPage == item.page,
                onClick = { onPageSelected(item.page) },
                icon = {
                    Icon(
                        painter = painterResource(item.iconResId),
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(item.labelResId)) },
            )
        }
    }
}

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

@Composable
private fun formatItemCount(count: Int): String {
    return pluralStringResource(R.plurals.summary_count_items, count, count)
}

@Composable
private fun formatNotWashedAge(days: Long?): String {
    return when (days) {
        null -> stringResource(R.string.not_washed_none)
        0L -> stringResource(R.string.not_washed_today)
        else -> pluralStringResource(R.plurals.not_washed_days, days.toInt(), days)
    }
}

/**
 * Hosts the CameraX PreviewView and detection modal as Android Views inside the
 * Compose tree. This preserves the original camera pipeline and crop animation
 * while removing the XML layout file that previously owned these views.
 */
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

@Composable
fun LaundryGroupsScreen(
    controller: LaundryGroupsController,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 22.dp),
    ) {
        Text(
            text = stringResource(R.string.filter_title),
            color = colorResource(R.color.laundry_text),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LaundryGroupFilter.values().forEach { filter ->
                FilterChip(
                    selected = controller.selectedFilter == filter,
                    onClick = { controller.selectFilter(filter) },
                    label = { Text(stringResource(filter.labelResId)) },
                )
            }
        }

        if (controller.groups.isEmpty()) {
            Text(
                text = stringResource(R.string.groups_empty_state),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
                    .padding(20.dp),
                color = colorResource(R.color.laundry_text_muted),
                fontSize = 14.sp,
            )
        } else {
            controller.groups.forEachIndexed { index, group ->
                GroupSummaryCard(
                    group = group,
                    onClick = { controller.showDetails(group) },
                    modifier = Modifier.padding(top = if (index == 0) 18.dp else 12.dp),
                )
            }
        }
    }

    LaundryGroupDialogs(controller = controller)
}

@Composable
private fun LaundryGroupDialogs(
    controller: LaundryGroupsController,
    onDataChanged: () -> Unit = {},
) {
    if (controller.detailsVisible) {
        GroupDetailsDialog(
            controller = controller,
            onMarkDone = {
                controller.markDone()
                onDataChanged()
            },
        )
    }
    controller.pendingDelete?.let { record ->
        DeleteRecordDialog(
            onDismiss = controller::clearPendingDelete,
            onConfirm = {
                controller.deleteRecord(record)
                onDataChanged()
            },
        )
    }
}

@Composable
private fun GroupSummaryCard(
    group: LaundryDisplayGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.laundry_surface)),
        border = BorderStroke(1.dp, colorResource(R.color.laundry_line)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    color = colorResource(R.color.laundry_text),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = groupDetail(group),
                    modifier = Modifier.padding(top = 6.dp),
                    color = colorResource(R.color.laundry_text_muted),
                    fontSize = 13.sp,
                )
            }
            if (!group.readOnly) {
                StatusBadge(
                    text = stringResource(if (group.done) R.string.status_done else R.string.status_pending),
                    foreground = if (group.done) R.color.laundry_success else R.color.laundry_warning,
                    background = if (group.done) R.color.laundry_success_background else R.color.laundry_warning_background,
                )
            }
        }
    }
}

@Composable
private fun GroupDetailsDialog(
    controller: LaundryGroupsController,
    onMarkDone: () -> Unit,
) {
    val group = controller.selectedGroup ?: return
    AlertDialog(
        onDismissRequest = controller::hideDetails,
        title = {
            Text(
                text = group.name,
                color = colorResource(R.color.laundry_text),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = groupDetail(group),
                    color = colorResource(R.color.laundry_text_muted),
                    fontSize = 14.sp,
                )
                LazyColumn(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(group.records, key = { it.id }) { record ->
                        GroupRecordCard(
                            storedRecord = record,
                            imageFile = controller.imageFile(record),
                            canDelete = group.canModify,
                            onDelete = { controller.requestDelete(record) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!group.readOnly) {
                Button(
                    onClick = onMarkDone,
                    enabled = !group.done,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(if (group.done) R.string.group_done_disabled else R.string.group_done_button))
                }
            }
        },
    )
}

@Composable
private fun GroupRecordCard(
    storedRecord: LaundryRecordStore.StoredRecord,
    imageFile: File,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val imageBitmap = remember(imageFile.path, imageFile.lastModified()) {
        BitmapFactory.decodeFile(imageFile.path)?.asImageBitmap()
    }
    val record = storedRecord.record
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorResource(R.color.laundry_surface)),
        border = BorderStroke(2.dp, colorResource(R.color.laundry_line)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colorResource(R.color.laundry_line)),
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = displayCategory(record.category) + " - " + valueOrNone(LaundryCategory.displayColor(record.color)),
                    color = colorResource(R.color.laundry_text),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                InfoText(R.string.group_record_detail_type, valueOrNone(record.detailType))
                InfoText(R.string.group_record_detected_label, valueOrNone(record.detectedLabel))
            }
            if (canDelete) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.wrapContentWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.group_record_delete),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoText(@StringRes labelResId: Int, value: String) {
    Text(
        text = stringResource(labelResId) + ": " + value,
        color = colorResource(R.color.laundry_text_muted),
        fontSize = 12.sp,
    )
}

@Composable
private fun DeleteRecordDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_record_delete_title)) },
        text = { Text(stringResource(R.string.group_record_delete_message)) },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.group_record_delete_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.group_record_delete))
            }
        },
    )
}

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

@Composable
private fun StatusBadge(
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

class LaundryHomeController(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    private val recordStore = LaundryRecordStore(context.applicationContext)

    var state by mutableStateOf(LaundryHomeState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        val now = currentTimeMillis()
        val groups = recordStore.getGroups()
        val records = recordStore.getStoredRecords()
        val pendingGroups = groups.filter { group -> !group.done }
        val pendingRecords = pendingGroups.flatMap { group -> group.records }
        val oldestNotWashedDays = pendingRecords
            .minOfOrNull { record -> record.record.createdAt }
            ?.let { createdAt -> daysSince(createdAt, now) }

        val recommendations = pendingGroups
            .mapNotNull { group -> group.toRecommendation(now) }
            .sortedWith(
                compareByDescending<LaundryHomeRecommendation> { recommendation -> recommendation.score }
                    .thenBy { recommendation -> recommendation.oldestCreatedAt }
                    .thenByDescending { recommendation -> recommendation.itemCount }
                    .thenBy { recommendation -> recommendation.name.lowercase() },
            )
            .take(MAX_HOME_RECOMMENDATIONS)

        state = LaundryHomeState(
            summary = LaundryHomeSummary(
                totalLaundryCount = records.size,
                pendingLaundryCount = pendingRecords.size,
                totalGroupCount = groups.size,
                pendingGroupCount = pendingGroups.size,
                doneGroupCount = groups.count { group -> group.done },
                oldestNotWashedDays = oldestNotWashedDays,
            ),
            recommendations = recommendations,
        )
    }

    override fun close() {
        recordStore.close()
    }

    private fun LaundryRecordStore.StoredGroup.toRecommendation(now: Long): LaundryHomeRecommendation? {
        val oldestCreatedAt = records.minOfOrNull { record -> record.record.createdAt } ?: return null
        return LaundryHomeRecommendation(
            groupId = id,
            name = name,
            itemCount = records.size,
            oldestCreatedAt = oldestCreatedAt,
            notWashedDays = daysSince(oldestCreatedAt, now),
        )
    }

    private fun daysSince(createdAt: Long, now: Long): Long {
        val createdDayStart = startOfDay(createdAt)
        val nowDayStart = startOfDay(now)
        return TimeUnit.MILLISECONDS.toDays((nowDayStart - createdDayStart).coerceAtLeast(0L))
    }

    private fun startOfDay(timeMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private companion object {
        const val MAX_HOME_RECOMMENDATIONS = 3
    }
}

data class LaundryHomeState(
    val summary: LaundryHomeSummary = LaundryHomeSummary(),
    val recommendations: List<LaundryHomeRecommendation> = emptyList(),
) {
    val priorityRecommendation: LaundryHomeRecommendation?
        get() = recommendations.firstOrNull()
}

data class LaundryHomeSummary(
    val totalLaundryCount: Int = 0,
    val pendingLaundryCount: Int = 0,
    val totalGroupCount: Int = 0,
    val pendingGroupCount: Int = 0,
    val doneGroupCount: Int = 0,
    val oldestNotWashedDays: Long? = null,
)

data class LaundryHomeRecommendation(
    val groupId: Long? = null,
    val name: String,
    val itemCount: Int,
    val oldestCreatedAt: Long,
    val notWashedDays: Long,
) {
    val score: Long
        get() = itemCount.toLong() * (notWashedDays + 1L)
}

class LaundryGroupsController(context: Context) : AutoCloseable {
    private val recordStore = LaundryRecordStore(context.applicationContext)

    var selectedFilter by mutableStateOf(LaundryGroupFilter.RECOMMENDED)
        private set
    var groups by mutableStateOf<List<LaundryDisplayGroup>>(emptyList())
        private set
    var selectedGroup by mutableStateOf<LaundryDisplayGroup?>(null)
        private set
    var detailsVisible by mutableStateOf(false)
        private set
    var pendingDelete by mutableStateOf<LaundryRecordStore.StoredRecord?>(null)
        private set

    init {
        refresh()
    }

    fun selectFilter(filter: LaundryGroupFilter) {
        if (selectedFilter == filter) {
            return
        }
        selectedFilter = filter
        selectedGroup = null
        detailsVisible = false
        pendingDelete = null
        refresh()
    }

    fun refresh() {
        groups = when (selectedFilter) {
            LaundryGroupFilter.RECOMMENDED -> recommendedGroups()
            LaundryGroupFilter.TYPE -> typeGroups()
            LaundryGroupFilter.COLOR -> colorGroups()
        }
        selectedGroup = selectedGroup?.let { selected ->
            groups.firstOrNull { group -> group.id == selected.id }
        }
        if (selectedGroup == null) {
            detailsVisible = false
            pendingDelete = null
        }
    }

    fun showDetails(group: LaundryDisplayGroup) {
        refresh()
        selectedGroup = groups.firstOrNull { it.id == group.id }
        detailsVisible = selectedGroup != null
    }

    fun showRecommendedGroupDetails(groupId: Long) {
        selectedFilter = LaundryGroupFilter.RECOMMENDED
        refresh()
        selectedGroup = groups.firstOrNull { it.storeGroupId == groupId }
        detailsVisible = selectedGroup != null
    }

    fun hideDetails() {
        detailsVisible = false
        selectedGroup = null
    }

    fun markDone() {
        selectedGroup?.storeGroupId?.let { groupId ->
            recordStore.markGroupDone(groupId)
        }
        detailsVisible = false
        selectedGroup = null
        refresh()
    }

    fun requestDelete(record: LaundryRecordStore.StoredRecord) {
        if (selectedGroup?.canModify == true) {
            pendingDelete = record
        }
    }

    fun clearPendingDelete() {
        pendingDelete = null
    }

    fun deleteRecord(record: LaundryRecordStore.StoredRecord) {
        if (selectedGroup?.canModify == true) {
            recordStore.deleteRecord(record.id)
        }
        pendingDelete = null
        refresh()
    }

    fun imageFile(record: LaundryRecordStore.StoredRecord): File = recordStore.getImageFile(record)

    private fun recommendedGroups(): List<LaundryDisplayGroup> {
        return recordStore.getGroups().map { group ->
            LaundryDisplayGroup(
                id = "recommended:${group.id}",
                name = group.name,
                done = group.done,
                records = group.records,
                readOnly = false,
                storeGroupId = group.id,
            )
        }
    }

    private fun colorGroups(): List<LaundryDisplayGroup> {
        val recordsByColor = recordStore.getStoredRecords().groupBy { storedRecord ->
            LaundryCategory.displayColor(storedRecord.record.color)
        }
        return LaundryCategory.colorTypes().mapNotNull { color ->
            val records = recordsByColor[color].orEmpty()
            if (records.isEmpty()) {
                null
            } else {
                readOnlyGroup("color:$color", color, records)
            }
        }
    }

    private fun typeGroups(): List<LaundryDisplayGroup> {
        val recordsByType = linkedMapOf<String, MutableList<LaundryRecordStore.StoredRecord>>()
        recordStore.getStoredRecords().forEach { storedRecord ->
            typeGroupName(storedRecord.record)?.let { name ->
                recordsByType.getOrPut(name) { mutableListOf() }.add(storedRecord)
            }
        }
        return recordsByType.map { (name, records) ->
            readOnlyGroup("type:$name", name, records)
        }
    }

    private fun typeGroupName(record: LaundryRecord): String? {
        return when (record.category) {
            LaundryRecord.CATEGORY_TOP,
            LaundryRecord.CATEGORY_BOTTOM -> record.detailType?.trim()?.takeIf { it.isNotEmpty() }
            LaundryRecord.CATEGORY_TOWEL -> LaundryCategory.TOWEL.displayName
            LaundryRecord.CATEGORY_SOCK -> LaundryCategory.SOCK.displayName
            else -> null
        }
    }

    private fun readOnlyGroup(
        id: String,
        name: String,
        records: List<LaundryRecordStore.StoredRecord>,
    ): LaundryDisplayGroup {
        return LaundryDisplayGroup(
            id = id,
            name = name,
            done = false,
            records = records,
            readOnly = true,
            storeGroupId = null,
        )
    }

    override fun close() {
        recordStore.close()
    }
}

enum class LaundryGroupFilter(@param:StringRes val labelResId: Int) {
    RECOMMENDED(R.string.filter_recommend),
    TYPE(R.string.filter_type),
    COLOR(R.string.filter_color),
}

data class LaundryDisplayGroup(
    val id: String,
    val name: String,
    val done: Boolean,
    val records: List<LaundryRecordStore.StoredRecord>,
    val readOnly: Boolean,
    val storeGroupId: Long?,
) {
    val canModify: Boolean
        get() = !readOnly && !done && storeGroupId != null
}

@Composable
private fun groupDetail(group: LaundryDisplayGroup): String {
    val itemCount = group.records.size
    if (group.readOnly) {
        return pluralStringResource(R.plurals.group_detail_read_only_format, itemCount, itemCount)
    }
    return pluralStringResource(
        R.plurals.group_detail_format,
        itemCount,
        itemCount,
        stringResource(if (group.done) R.string.status_done else R.string.status_pending),
    )
}

@Composable
private fun displayCategory(categoryCode: String?): String {
    val none = stringResource(R.string.group_record_none)
    for (category in LaundryCategory.values()) {
        if (category.recordCode == categoryCode) {
            return category.displayName
        }
    }
    return categoryCode?.takeIf { it.isNotBlank() } ?: none
}

@Composable
private fun valueOrNone(value: String?): String {
    return value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.group_record_none)
}

@StringRes
private fun titleFor(page: Int): Int = when (page) {
    LaundryComposeHost.PAGE_REGISTER -> R.string.register_title
    LaundryComposeHost.PAGE_GROUPS -> R.string.groups_title
    LaundryComposeHost.PAGE_TIPS -> R.string.tips_title
    else -> R.string.home_title
}

@StringRes
private fun subtitleFor(page: Int): Int = when (page) {
    LaundryComposeHost.PAGE_REGISTER -> R.string.register_subtitle
    LaundryComposeHost.PAGE_GROUPS -> R.string.groups_subtitle
    LaundryComposeHost.PAGE_TIPS -> R.string.tips_subtitle
    else -> R.string.home_subtitle
}

private data class NavItem(
    val page: Int,
    val labelResId: Int,
    val iconResId: Int,
)

private fun navItems(): List<NavItem> {
    return listOf(
        NavItem(LaundryComposeHost.PAGE_HOME, R.string.nav_home, android.R.drawable.ic_menu_compass),
        NavItem(LaundryComposeHost.PAGE_REGISTER, R.string.nav_register, android.R.drawable.ic_menu_camera),
        NavItem(LaundryComposeHost.PAGE_GROUPS, R.string.nav_groups, android.R.drawable.ic_menu_agenda),
        NavItem(LaundryComposeHost.PAGE_TIPS, R.string.nav_tips, android.R.drawable.ic_menu_info_details),
    )
}

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

@Preview(showBackground = true)
@Composable
private fun TipsPagePreview() {
    LaundryTheme {
        TipsPage()
    }
}
