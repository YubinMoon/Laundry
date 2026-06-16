package app.dku.embededapp.ui.compose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.dku.embededapp.data.LaundryRecord
import app.dku.embededapp.data.LaundryRecordStore
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaundryComposeScreenTest {
    private lateinit var context: Context
    private var groupsController: LaundryGroupsController? = null

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearStoredRecords()
    }

    @After
    fun tearDown() {
        groupsController?.close()
        groupsController = null
        clearStoredRecords()
    }

    @Test
    fun homePageShowsEmptyStateAndSummary() {
        composeRule.setContent {
            LaundryTheme {
                HomePage(
                    state = LaundryHomeState(),
                    onRegisterClick = {},
                    onViewGroupsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Add Laundry").assertIsDisplayed()
        composeRule.onNodeWithText("No pending laundry").assertIsDisplayed()
        composeRule.onNodeWithText("No pending laundry groups.").assertIsDisplayed()
        composeRule.onNodeWithText("Total Laundry").assertIsDisplayed()
        composeRule.onNodeWithText("items saved").assertIsDisplayed()
        composeRule.onNodeWithText("Pending Groups").assertIsDisplayed()
        composeRule.onNodeWithText("Done Groups").assertIsDisplayed()
    }

    @Test
    fun homePageShowsDynamicSummaryAndRecommendations() {
        composeRule.setContent {
            LaundryTheme {
                HomePage(
                    state = LaundryHomeState(
                        summary = LaundryHomeSummary(
                            totalLaundryCount = 5,
                            pendingLaundryCount = 3,
                            totalGroupCount = 4,
                            pendingGroupCount = 2,
                            doneGroupCount = 2,
                            oldestNotWashedDays = 12,
                        ),
                        recommendations = listOf(
                            LaundryHomeRecommendation(
                                name = "Dark General Clothes",
                                itemCount = 3,
                                oldestCreatedAt = 1000L,
                                notWashedDays = 12,
                            ),
                        ),
                    ),
                    onRegisterClick = {},
                    onViewGroupsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Total Laundry").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("items saved").assertIsDisplayed()
        composeRule.onNodeWithText("Pending Groups").assertIsDisplayed()
        composeRule.onNodeWithText("Done Groups").assertIsDisplayed()
        composeRule.onAllNodesWithText("2").assertCountEquals(2)
        composeRule.onNodeWithText("Dark General Clothes").assertIsDisplayed()
        composeRule.onNodeWithText("3 items, not washed 12 days").assertIsDisplayed()
        composeRule.onNodeWithText("3 items / not washed 12 days").assertIsDisplayed()
    }

    @Test
    fun homePageTopPickDetailsButtonSelectsRecommendation() {
        var selectedGroupId: Long? = null
        composeRule.setContent {
            LaundryTheme {
                HomePage(
                    state = LaundryHomeState(
                        recommendations = listOf(
                            LaundryHomeRecommendation(
                                groupId = 42L,
                                name = "Dark General Clothes",
                                itemCount = 3,
                                oldestCreatedAt = 1000L,
                                notWashedDays = 12,
                            ),
                            LaundryHomeRecommendation(
                                groupId = 84L,
                                name = "Activewear",
                                itemCount = 1,
                                oldestCreatedAt = 2000L,
                                notWashedDays = 4,
                            ),
                        ),
                    ),
                    onRegisterClick = {},
                    onViewGroupsClick = {},
                    onRecommendationClick = { groupId ->
                        selectedGroupId = groupId
                    },
                )
            }
        }

        composeRule.onNodeWithText("Details").performClick()

        composeRule.runOnIdle {
            assertEquals(42L, selectedGroupId)
        }

        selectedGroupId = null
        composeRule.onNodeWithText("Activewear", substring = true).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(84L, selectedGroupId)
        }
    }

    @Test
    fun homeControllerSummarizesAllAndPendingLaundry() {
        val now = TimeUnit.DAYS.toMillis(10)
        saveRecords { bitmap ->
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "T-shirts", "White", "short_sleeved_shirt", now - TimeUnit.DAYS.toMillis(8)))
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "Shirts", "Light", "long_sleeved_shirt", now - TimeUnit.DAYS.toMillis(2)))
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "T-shirts", "Black", "short_sleeved_shirt", now - TimeUnit.DAYS.toMillis(5)))
            val doneGroup = getGroups().first { group -> group.name == "Dark General Clothes" }
            markGroupDone(doneGroup.id)
        }

        val controller = LaundryHomeController(context) { now }
        try {
            val state = controller.state

            assertEquals(3, state.summary.totalLaundryCount)
            assertEquals(2, state.summary.pendingLaundryCount)
            assertEquals(2, state.summary.totalGroupCount)
            assertEquals(1, state.summary.pendingGroupCount)
            assertEquals(1, state.summary.doneGroupCount)
            assertEquals(8L, state.summary.oldestNotWashedDays)
            assertEquals(listOf("Light General Clothes"), state.recommendations.map { recommendation -> recommendation.name })
            assertEquals(2, state.recommendations.first().itemCount)
            assertEquals(8L, state.recommendations.first().notWashedDays)
        } finally {
            controller.close()
        }
    }

    @Test
    fun homeControllerSortsRecommendationsByScore() {
        val now = TimeUnit.DAYS.toMillis(10)
        saveRecords { bitmap ->
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "Activewear", "White", "short_sleeved_shirt", now - TimeUnit.DAYS.toMillis(6)))
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "T-shirts", "Black", "short_sleeved_shirt", now - TimeUnit.DAYS.toMillis(4)))
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "Shirts", "Black", "long_sleeved_shirt", now - TimeUnit.DAYS.toMillis(1)))
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "T-shirts", "White", "short_sleeved_shirt", now - TimeUnit.DAYS.toMillis(4)))
        }

        val controller = LaundryHomeController(context) { now }
        try {
            assertEquals(
                listOf("Dark General Clothes", "Activewear", "Light General Clothes"),
                controller.state.recommendations.map { recommendation -> recommendation.name },
            )
            assertEquals(listOf(10L, 7L, 5L), controller.state.recommendations.map { recommendation -> recommendation.score })
        } finally {
            controller.close()
        }
    }

    @Test
    fun homeRecommendationOpensRecommendedGroupDetails() {
        val now = TimeUnit.DAYS.toMillis(10)
        saveRecords { bitmap ->
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "T-shirts", "White", "short_sleeved_shirt", now - TimeUnit.DAYS.toMillis(8)))
            saveRecord(bitmap, record(LaundryRecord.CATEGORY_TOP, "Shirts", "Light", "long_sleeved_shirt", now - TimeUnit.DAYS.toMillis(2)))
        }

        val homeController = LaundryHomeController(context) { now }
        val controller = LaundryGroupsController(context)
        groupsController = controller
        try {
            val groupId = homeController.state.recommendations.first().groupId

            controller.showRecommendedGroupDetails(groupId!!)

            assertEquals(true, controller.detailsVisible)
            assertEquals("Light General Clothes", controller.selectedGroup?.name)
            assertEquals(2, controller.selectedGroup?.records?.size)
        } finally {
            homeController.close()
        }
    }

    @Test
    fun groupsScreenDefaultsToRecommendedGroupsWithStatus() {
        saveRecord(record(LaundryRecord.CATEGORY_TOP, "T-shirts", "White", "short_sleeved_shirt", 1000L))

        setGroupsContent()

        composeRule.onNodeWithText("Recommended Group").assertIsDisplayed()
        composeRule.onNodeWithText("Type").assertIsDisplayed()
        composeRule.onNodeWithText("Color").assertIsDisplayed()
        composeRule.onNodeWithText("Category").assertDoesNotExist()
        composeRule.onNodeWithText("Fabric").assertDoesNotExist()
        composeRule.onNodeWithText("Light General Clothes").assertIsDisplayed()
        composeRule.onNodeWithText("Total 1 item / Pending").assertIsDisplayed()
        composeRule.onNodeWithText("Pending").assertIsDisplayed()
    }

    @Test
    fun colorFilterShowsReadOnlyColorGroups() {
        saveRecord(record(LaundryRecord.CATEGORY_TOP, "T-shirts", "White", "short_sleeved_shirt", 1000L))
        saveRecord(record(LaundryRecord.CATEGORY_BOTTOM, "Jeans", "Black", "trousers", 2000L))

        setGroupsContent()
        composeRule.onNodeWithText("Color").performClick()

        composeRule.onNodeWithText("White").assertIsDisplayed()
        composeRule.onNodeWithText("Black").assertIsDisplayed()
        composeRule.onAllNodesWithText("Total 1 item").assertCountEquals(2)
        composeRule.onNodeWithText("Pending").assertDoesNotExist()

        composeRule.onNodeWithText("White").performClick()

        composeRule.onNodeWithText("Tops - White").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        composeRule.onNodeWithText("Done").assertDoesNotExist()
        composeRule.onNodeWithText("Pending").assertDoesNotExist()
    }

    @Test
    fun typeFilterCombinesMatchingDetailsAndShowsTowelsAndSocks() {
        saveRecord(record(LaundryRecord.CATEGORY_TOP, "Activewear", "White", "short_sleeved_shirt", 1000L))
        saveRecord(record(LaundryRecord.CATEGORY_BOTTOM, "Activewear", "Black", "trousers", 2000L))
        saveRecord(record(LaundryRecord.CATEGORY_TOWEL, null, "White", "towel", 3000L))
        saveRecord(record(LaundryRecord.CATEGORY_SOCK, null, "Black", "sock", 4000L))

        setGroupsContent()
        composeRule.onNodeWithText("Type").performClick()

        composeRule.onNodeWithText("Activewear").assertIsDisplayed()
        composeRule.onNodeWithText("Total 2 items").assertIsDisplayed()
        composeRule.onNodeWithText("Towels").assertIsDisplayed()
        composeRule.onNodeWithText("Socks").assertIsDisplayed()
    }

    private fun setGroupsContent() {
        val controller = LaundryGroupsController(context)
        groupsController = controller
        composeRule.setContent {
            LaundryTheme {
                LaundryGroupsScreen(controller)
            }
        }
    }

    private fun saveRecord(record: LaundryRecord) {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val store = LaundryRecordStore(context)
        try {
            store.saveRecord(bitmap, record)
        } finally {
            store.close()
            bitmap.recycle()
        }
    }

    private fun saveRecords(block: LaundryRecordStore.(Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val store = LaundryRecordStore(context)
        try {
            store.block(bitmap)
        } finally {
            store.close()
            bitmap.recycle()
        }
    }

    private fun record(
        category: String,
        detailType: String?,
        color: String,
        detectedLabel: String,
        createdAt: Long,
    ): LaundryRecord {
        return LaundryRecord(
            category,
            detailType,
            color,
            detectedLabel,
            0.91f,
            0.82f,
            createdAt,
        )
    }

    private fun clearStoredRecords() {
        context.deleteDatabase(DATABASE_NAME)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        deleteRecursively(File(context.filesDir, IMAGE_DIRECTORY))
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return
        }
        if (file.isDirectory) {
            file.listFiles()?.forEach(::deleteRecursively)
        }
        file.delete()
    }

    private companion object {
        const val DATABASE_NAME = "laundry_records.db"
        const val IMAGE_DIRECTORY = "detections"
        const val PREFERENCES_NAME = "laundry_records_preferences"
    }
}
