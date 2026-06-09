package app.dku.embededapp.ui.groups;

import android.app.Activity;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;

import app.dku.embededapp.R;
import app.dku.embededapp.data.LaundryRecord;
import app.dku.embededapp.data.LaundryRecordStore;
import app.dku.embededapp.detection.LaundryCategory;

public final class LaundryGroupController implements AutoCloseable {
    private final Activity activity;
    private final LaundryRecordStore recordStore;
    private final LinearLayout groupsContainer;
    private final TextView emptyState;
    private AlertDialog detailsDialog;
    private TextView detailsSubtitle;
    private LinearLayout detailsRecordList;
    private MaterialButton detailsDoneButton;

    public LaundryGroupController(Activity activity, View groupsPage) {
        this.activity = activity;
        recordStore = new LaundryRecordStore(activity);
        groupsContainer = groupsPage.findViewById(R.id.groups_container);
        emptyState = groupsPage.findViewById(R.id.groups_empty_state);

        ChipGroup filterChips = groupsPage.findViewById(R.id.groups_filter_chips);
        filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> refresh());
        refresh();
    }

    public void refresh() {
        List<LaundryRecordStore.StoredRecord> records = recordStore.getStoredRecords();
        groupsContainer.removeAllViews();

        if (records.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            groupsContainer.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        groupsContainer.setVisibility(View.VISIBLE);
        groupsContainer.addView(createGroupCard(records.size(), recordStore.isSingleGroupDone()));
    }

    @Override
    public void close() {
        if (detailsDialog != null) {
            detailsDialog.dismiss();
            detailsDialog = null;
        }
        recordStore.close();
    }

    private MaterialCardView createGroupCard(int recordCount, boolean done) {
        MaterialCardView card = new MaterialCardView(activity);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(color(R.color.laundry_surface));
        card.setRadius(dp(20));
        card.setCardElevation(0f);
        card.setStrokeColor(color(R.color.laundry_line));
        card.setStrokeWidth(dp(1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> showGroupDetails());

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(row);

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        row.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView title = new TextView(activity);
        title.setText(R.string.group_all_records_title);
        title.setTextColor(color(R.color.laundry_text));
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textColumn.addView(title);

        TextView detail = new TextView(activity);
        detail.setText(groupDetail(recordCount, done));
        detail.setTextColor(color(R.color.laundry_text_muted));
        detail.setTextSize(13);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(6);
        textColumn.addView(detail, detailParams);

        TextView badge = new TextView(activity);
        bindStatusBadge(badge, done);
        row.addView(badge);
        return card;
    }

    private void showGroupDetails() {
        List<LaundryRecordStore.StoredRecord> records = recordStore.getStoredRecords();
        if (records.isEmpty()) {
            refresh();
            return;
        }

        View content = createDetailsContent();
        detailsDialog = new MaterialAlertDialogBuilder(activity)
                .setView(content)
                .create();
        detailsDialog.setOnDismissListener(dialog -> {
            detailsDialog = null;
            detailsSubtitle = null;
            detailsRecordList = null;
            detailsDoneButton = null;
        });
        detailsDialog.show();
        bindDetails(records);
    }

    private View createDetailsContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(16));
        content.setBackgroundColor(color(R.color.laundry_surface));

        TextView title = new TextView(activity);
        title.setText(R.string.group_all_records_title);
        title.setTextColor(color(R.color.laundry_text));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        detailsSubtitle = new TextView(activity);
        detailsSubtitle.setTextColor(color(R.color.laundry_text_muted));
        detailsSubtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        content.addView(detailsSubtitle, subtitleParams);

        ScrollView recordsScroll = new ScrollView(activity);
        recordsScroll.setClipToPadding(false);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(360));
        scrollParams.topMargin = dp(14);
        content.addView(recordsScroll, scrollParams);

        detailsRecordList = new LinearLayout(activity);
        detailsRecordList.setOrientation(LinearLayout.VERTICAL);
        recordsScroll.addView(detailsRecordList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        detailsDoneButton = new MaterialButton(activity);
        detailsDoneButton.setAllCaps(false);
        detailsDoneButton.setMinHeight(0);
        detailsDoneButton.setInsetTop(0);
        detailsDoneButton.setInsetBottom(0);
        detailsDoneButton.setBackgroundTintList(ContextCompat.getColorStateList(activity, R.color.laundry_primary));
        detailsDoneButton.setCornerRadius(dp(14));
        detailsDoneButton.setOnClickListener(view -> {
            recordStore.setSingleGroupDone(true);
            if (detailsDialog != null) {
                detailsDialog.dismiss();
            }
            refresh();
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46));
        doneParams.topMargin = dp(14);
        content.addView(detailsDoneButton, doneParams);

        return content;
    }

    private void bindDetails(List<LaundryRecordStore.StoredRecord> records) {
        if (detailsSubtitle == null || detailsRecordList == null || detailsDoneButton == null) {
            return;
        }

        boolean done = recordStore.isSingleGroupDone();
        detailsSubtitle.setText(groupDetail(records.size(), done));
        detailsRecordList.removeAllViews();
        for (LaundryRecordStore.StoredRecord record : records) {
            detailsRecordList.addView(createRecordCard(record));
        }

        detailsDoneButton.setText(done ? R.string.group_done_disabled : R.string.group_done_button);
        detailsDoneButton.setEnabled(!done);
    }

    private MaterialCardView createRecordCard(LaundryRecordStore.StoredRecord storedRecord) {
        MaterialCardView card = new MaterialCardView(activity);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        card.setCardBackgroundColor(color(R.color.laundry_surface));
        card.setRadius(dp(16));
        card.setCardElevation(0f);
        card.setStrokeColor(color(R.color.laundry_line));
        card.setStrokeWidth(dp(2));

        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.TOP);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.addView(row);

        ImageView image = new ImageView(activity);
        image.setBackgroundColor(color(R.color.laundry_line));
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        File imageFile = recordStore.getImageFile(storedRecord);
        if (imageFile.exists()) {
            image.setImageURI(Uri.fromFile(imageFile));
        }
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        row.addView(image, imageParams);

        LinearLayout textColumn = new LinearLayout(activity);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        textParams.leftMargin = dp(12);
        row.addView(textColumn, textParams);

        LaundryRecord record = storedRecord.record;
        TextView title = createText(
                displayCategory(record.category) + " · " + valueOrNone(record.color),
                R.color.laundry_text,
                15,
                true);
        textColumn.addView(title);

        textColumn.addView(createInfoText(R.string.group_record_detail_type, valueOrNone(record.detailType)));
        textColumn.addView(createInfoText(R.string.group_record_detected_label, valueOrNone(record.detectedLabel)));

        TextView deleteButton = createText(
                activity.getString(R.string.group_record_delete),
                R.color.laundry_primary,
                13,
                true);
        deleteButton.setGravity(Gravity.CENTER);
        deleteButton.setPadding(dp(8), dp(4), dp(2), dp(4));
        deleteButton.setOnClickListener(view -> confirmDelete(storedRecord));
        row.addView(deleteButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return card;
    }

    private TextView createInfoText(int labelResId, String value) {
        return createText(
                activity.getString(labelResId) + ": " + value,
                R.color.laundry_text_muted,
                12,
                false);
    }

    private TextView createText(String value, int colorResId, int textSizeSp, boolean bold) {
        TextView textView = new TextView(activity);
        textView.setText(value);
        textView.setTextColor(color(colorResId));
        textView.setTextSize(textSizeSp);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return textView;
    }

    private void confirmDelete(LaundryRecordStore.StoredRecord storedRecord) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.group_record_delete_title)
                .setMessage(R.string.group_record_delete_message)
                .setNegativeButton(R.string.group_record_delete_cancel, null)
                .setPositiveButton(R.string.group_record_delete, (dialog, which) -> {
                    recordStore.deleteRecord(storedRecord.id);
                    List<LaundryRecordStore.StoredRecord> records = recordStore.getStoredRecords();
                    if (records.isEmpty() && detailsDialog != null) {
                        detailsDialog.dismiss();
                    } else {
                        bindDetails(records);
                    }
                    refresh();
                })
                .show();
    }

    private void bindStatusBadge(TextView badge, boolean done) {
        badge.setText(done ? R.string.status_done : R.string.status_pending);
        badge.setTextColor(color(done ? R.color.laundry_success : R.color.laundry_warning));
        badge.setTextSize(12);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setBackgroundResource(done ? R.drawable.bg_status_success : R.drawable.bg_status_warning);
    }

    private String groupDetail(int recordCount, boolean done) {
        return activity.getString(
                R.string.group_detail_format,
                recordCount,
                activity.getString(done ? R.string.status_done : R.string.status_pending));
    }

    private String displayCategory(String categoryCode) {
        for (LaundryCategory category : LaundryCategory.values()) {
            if (category.recordCode.equals(categoryCode)) {
                return category.displayName;
            }
        }
        return valueOrNone(categoryCode);
    }

    private String valueOrNone(String value) {
        if (value == null || value.trim().isEmpty()) {
            return activity.getString(R.string.group_record_none);
        }
        return value;
    }

    private int color(int colorResId) {
        return ContextCompat.getColor(activity, colorResId);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
