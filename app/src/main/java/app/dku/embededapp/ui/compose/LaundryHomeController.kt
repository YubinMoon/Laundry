package app.dku.embededapp.ui.compose

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.dku.embededapp.data.LaundryRecordStore
import java.util.Calendar
import java.util.concurrent.TimeUnit

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
