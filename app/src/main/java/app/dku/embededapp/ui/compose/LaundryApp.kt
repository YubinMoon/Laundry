package app.dku.embededapp.ui.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dku.embededapp.R

// Builds the shared app shell and routes the selected page to its screen.
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

// Displays the current page title and subtitle above the active screen.
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

// Renders the bottom navigation bar for the four top-level pages.
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
