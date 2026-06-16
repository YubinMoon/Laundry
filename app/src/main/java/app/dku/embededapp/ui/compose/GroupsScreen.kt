package app.dku.embededapp.ui.compose

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.dku.embededapp.R
import app.dku.embededapp.data.LaundryRecord
import app.dku.embededapp.data.LaundryRecordStore
import app.dku.embededapp.detection.LaundryCategory
import java.io.File
import kotlinx.coroutines.delay

// Renders the group filter chips and group summary list.
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

// Hosts the group detail and delete confirmation dialogs.
@Composable
fun LaundryGroupDialogs(
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

// Shows one group row with item count and status.
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

// Presents all records in a group and exposes Done/Delete actions.
@Composable
private fun GroupDetailsDialog(
    controller: LaundryGroupsController,
    onMarkDone: () -> Unit,
) {
    val group = controller.selectedGroup ?: return
    var tipVisible by remember(group.id) { mutableStateOf(false) }
    var tipRequestCount by remember(group.id) { mutableIntStateOf(0) }
    val tipText = groupTip(group)
    val tipPopupOffsetY = with(LocalDensity.current) { 48.dp.roundToPx() }

    LaunchedEffect(tipRequestCount) {
        if (tipRequestCount > 0) {
            tipVisible = true
            delay(3000)
            tipVisible = false
        }
    }

    AlertDialog(
        onDismissRequest = controller::hideDetails,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = group.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    color = colorResource(R.color.laundry_text),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(contentAlignment = Alignment.TopEnd) {
                    Button(
                        onClick = { tipRequestCount += 1 },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorResource(R.color.laundry_primary),
                            contentColor = colorResource(R.color.white),
                        ),
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_info_details),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.group_tip_button),
                            modifier = Modifier.padding(start = 6.dp),
                            fontSize = 13.sp,
                        )
                    }
                    Popup(
                        alignment = Alignment.TopEnd,
                        offset = IntOffset(0, tipPopupOffsetY),
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = false,
                            dismissOnClickOutside = false,
                        ),
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = tipVisible,
                            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 450)),
                        ) {
                            TipBubble(text = tipText)
                        }
                    }
                }
            }
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

// Draws a floating laundry tip bubble without changing the dialog layout.
@Composable
private fun TipBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bubbleColor = colorResource(R.color.laundry_warning_background)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 28.dp)
                .size(10.dp)
                .rotate(45f)
                .background(bubbleColor),
        )
        Box(
            modifier = Modifier
                .width(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = text,
                color = colorResource(R.color.laundry_text),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

// Displays a saved laundry record with thumbnail, metadata, and optional delete action.
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

// Shows one label/value line inside a group record card.
@Composable
private fun InfoText(@StringRes labelResId: Int, value: String) {
    Text(
        text = stringResource(labelResId) + ": " + value,
        color = colorResource(R.color.laundry_text_muted),
        fontSize = 12.sp,
    )
}

// Confirms deletion before removing a saved laundry record.
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

// Formats the group item count and status line.
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

// Chooses the most relevant laundry tip for the selected group.
@Composable
private fun groupTip(group: LaundryDisplayGroup): String {
    val groupName = group.name.lowercase()
    val records = group.records.map { storedRecord -> storedRecord.record }
    val detailTypes = records.mapNotNull { record -> record.detailType?.trim()?.lowercase() }
    val colors = records.mapNotNull { record -> LaundryCategory.displayColor(record.color)?.lowercase() }
    val categories = records.map { record -> record.category }
    val tipResId = when {
        categories.any { category -> category == LaundryRecord.CATEGORY_TOWEL }
                || groupName.contains("towel") -> R.string.group_tip_towels
        detailTypes.any { detailType -> detailType.contains("denim") || detailType.contains("jeans") }
                || groupName.contains("denim") -> R.string.group_tip_denim
        detailTypes.any { detailType -> detailType.contains("activewear") }
                || groupName.contains("activewear") -> R.string.group_tip_activewear
        detailTypes.any { detailType -> detailType.contains("sweater") || detailType.contains("skirt") }
                || groupName.contains("delicate") -> R.string.group_tip_delicates
        categories.any { category -> category == LaundryRecord.CATEGORY_SOCK }
                || groupName.contains("sock") -> R.string.group_tip_socks
        colors.any { color -> color == LaundryCategory.COLOR_BLACK.lowercase() || color == LaundryCategory.COLOR_DARK.lowercase() }
                || groupName.contains("dark") -> R.string.group_tip_dark
        colors.any { color -> color == LaundryCategory.COLOR_WHITE.lowercase() || color == LaundryCategory.COLOR_BRIGHT.lowercase() }
                || groupName.contains("light") -> R.string.group_tip_light
        colors.any { color -> color == LaundryCategory.COLOR_MIXED.lowercase() }
                || groupName.contains("mixed") -> R.string.group_tip_mixed
        else -> R.string.group_tip_default
    }
    return stringResource(tipResId)
}

// Converts a stored category code into the visible category name.
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

// Shows a placeholder when optional record metadata is missing.
@Composable
private fun valueOrNone(value: String?): String {
    return value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.group_record_none)
}
