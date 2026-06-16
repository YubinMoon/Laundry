package app.dku.embededapp.ui.compose

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf

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
        private val selectedPage: MutableIntState,
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
