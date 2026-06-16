package app.dku.embededapp.ui.compose

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dku.embededapp.R
import app.dku.embededapp.data.LaundryRecord
import app.dku.embededapp.data.LaundryRecordStore
import app.dku.embededapp.detection.LaundryCategory
import java.io.File

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
