package app.dku.embededapp.ui.navigation;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

public final class PageNavigator {
    public static final int PAGE_HOME = 0;
    public static final int PAGE_REGISTER = 1;
    public static final int PAGE_GROUPS = 2;
    public static final int PAGE_TIPS = 3;

    private final View[] pages;
    private final TextView screenTitle;
    private final TextView screenSubtitle;

    public PageNavigator(View[] pages, TextView screenTitle, TextView screenSubtitle) {
        this.pages = pages;
        this.screenTitle = screenTitle;
        this.screenSubtitle = screenSubtitle;
    }

    public void showPage(int pageIndex, @StringRes int titleId, @StringRes int subtitleId) {
        for (int index = 0; index < pages.length; index++) {
            pages[index].setVisibility(index == pageIndex ? View.VISIBLE : View.GONE);
        }
        screenTitle.setText(titleId);
        screenSubtitle.setText(subtitleId);
    }

    public boolean isPageVisible(int pageIndex) {
        return pages[pageIndex].getVisibility() == View.VISIBLE;
    }

    public View getPage(int pageIndex) {
        return pages[pageIndex];
    }
}
