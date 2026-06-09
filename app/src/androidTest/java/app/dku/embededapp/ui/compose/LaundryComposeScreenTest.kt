package app.dku.embededapp.ui.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaundryComposeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homePageShowsPrimaryActionAndRecommendation() {
        composeRule.setContent {
            LaundryTheme {
                HomePage(
                    onRegisterClick = {},
                    onViewGroupsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Add Laundry").assertIsDisplayed()
        composeRule.onNodeWithText("1  Black Cotton T-shirts").assertIsDisplayed()
    }
}
