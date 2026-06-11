package app.dku.embededapp.ui.detection;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;

import app.dku.embededapp.R;
import app.dku.embededapp.data.LaundryRecord;
import app.dku.embededapp.data.LaundryRecordStore;
import app.dku.embededapp.detection.ClassificationDetail;
import app.dku.embededapp.detection.DetectionCrop;
import app.dku.embededapp.detection.LaundryCategory;
import app.dku.embededapp.detection.StableDetection;

public final class DetectionResultController implements AutoCloseable {
    private static final long FLASH_HALF_DURATION_MS = 150L;
    private static final long MODAL_DELAY_AFTER_FLASH_MS = 200L;
    private static final long CROP_MODAL_ANIMATION_MS = 360L;

    private final Activity activity;
    private final View registerPage;
    private final Runnable onResultCanceled;
    private final Runnable onRecordSaved;
    private final LaundryRecordStore laundryRecordStore;
    private final String[] topDetailTypes;
    private final String[] bottomDetailTypes;

    private final ImageView frozenFrame;
    private final View cameraFlash;
    private final View detectionModalScrim;
    private final View detectionResultModal;
    private final ImageView detectionResultImage;
    private final ImageView detectionTransitionImage;
    private final MaterialButton detectionCategoryDropdown;
    private final View detectionDetailDropdownContainer;
    private final MaterialButton detectionDetailDropdown;
    private final MaterialButton detectionColorDropdown;
    private final MaterialButton detectionResultCancel;
    private final MaterialButton detectionResultConfirm;

    private Bitmap frozenFrameBitmap;
    private Bitmap detectionCropBitmap;
    private RectF detectionCropBox;
    private String pendingDetectedLabel;
    private float pendingDetectedConfidence;
    private String pendingClassificationCategoryCode;
    private ClassificationDetail pendingClassificationDetail;
    private ValueAnimator cropModalAnimator;

    public DetectionResultController(
            Activity activity,
            View registerPage,
            Views views,
            String[] topDetailTypes,
            String[] bottomDetailTypes,
            Runnable onResultCanceled,
            Runnable onRecordSaved) {
        this.activity = activity;
        this.registerPage = registerPage;
        this.topDetailTypes = topDetailTypes.clone();
        this.bottomDetailTypes = bottomDetailTypes.clone();
        this.onResultCanceled = onResultCanceled;
        this.onRecordSaved = onRecordSaved;
        laundryRecordStore = new LaundryRecordStore(activity);

        frozenFrame = views.frozenFrame;
        cameraFlash = views.cameraFlash;
        detectionModalScrim = views.detectionModalScrim;
        detectionResultModal = views.detectionResultModal;
        detectionResultImage = views.detectionResultImage;
        detectionTransitionImage = views.detectionTransitionImage;
        detectionCategoryDropdown = views.detectionCategoryDropdown;
        detectionDetailDropdownContainer = views.detectionDetailDropdownContainer;
        detectionDetailDropdown = views.detectionDetailDropdown;
        detectionColorDropdown = views.detectionColorDropdown;
        detectionResultCancel = views.detectionResultCancel;
        detectionResultConfirm = views.detectionResultConfirm;

        setupDetectionDropdowns();
        detectionResultCancel.setOnClickListener(view -> cancelDetectionResultAndResume());
        detectionResultConfirm.setOnClickListener(view -> saveDetectionResultAndResume());
    }

    public static final class Views {
        public final ImageView frozenFrame;
        public final View cameraFlash;
        public final View detectionModalScrim;
        public final View detectionResultModal;
        public final ImageView detectionResultImage;
        public final ImageView detectionTransitionImage;
        public final MaterialButton detectionCategoryDropdown;
        public final View detectionDetailDropdownContainer;
        public final MaterialButton detectionDetailDropdown;
        public final MaterialButton detectionColorDropdown;
        public final MaterialButton detectionResultCancel;
        public final MaterialButton detectionResultConfirm;

        public Views(
                ImageView frozenFrame,
                View cameraFlash,
                View detectionModalScrim,
                View detectionResultModal,
                ImageView detectionResultImage,
                ImageView detectionTransitionImage,
                MaterialButton detectionCategoryDropdown,
                View detectionDetailDropdownContainer,
                MaterialButton detectionDetailDropdown,
                MaterialButton detectionColorDropdown,
                MaterialButton detectionResultCancel,
                MaterialButton detectionResultConfirm) {
            this.frozenFrame = frozenFrame;
            this.cameraFlash = cameraFlash;
            this.detectionModalScrim = detectionModalScrim;
            this.detectionResultModal = detectionResultModal;
            this.detectionResultImage = detectionResultImage;
            this.detectionTransitionImage = detectionTransitionImage;
            this.detectionCategoryDropdown = detectionCategoryDropdown;
            this.detectionDetailDropdownContainer = detectionDetailDropdownContainer;
            this.detectionDetailDropdown = detectionDetailDropdown;
            this.detectionColorDropdown = detectionColorDropdown;
            this.detectionResultCancel = detectionResultCancel;
            this.detectionResultConfirm = detectionResultConfirm;
        }
    }

    public void showStableDetection(StableDetection detection) {
        setDetectionCrop(detection.crop);
        setPendingDetectionRecord(detection);
        freezeFrame(detection.detectionResult.frameBitmap);
        flashAndShowDetectedResult(
                detection.category.displayName,
                detection.colorType,
                detection.classificationDetail);
    }

    public void clear() {
        cameraFlash.animate().cancel();
        cameraFlash.setAlpha(0f);
        cameraFlash.setVisibility(View.GONE);
        clearDetectionResultModal();
        clearFrozenFrame();
    }

    @Override
    public void close() {
        laundryRecordStore.close();
        clear();
    }

    private void flashAndShowDetectedResult(
            String laundryCategory,
            String colorType,
            ClassificationDetail classificationDetail) {
        cameraFlash.animate().cancel();
        cameraFlash.setAlpha(0f);
        cameraFlash.setVisibility(View.VISIBLE);
        cameraFlash.animate()
                .alpha(1f)
                .setDuration(FLASH_HALF_DURATION_MS)
                .withEndAction(() -> cameraFlash.animate()
                        .alpha(0f)
                        .setDuration(FLASH_HALF_DURATION_MS)
                        .withEndAction(() -> {
                            cameraFlash.setVisibility(View.GONE);
                            cameraFlash.postDelayed(() -> {
                                if (registerPage.getVisibility() == View.VISIBLE) {
                                    showDetectedResultModal(
                                            laundryCategory,
                                            colorType,
                                            classificationDetail);
                                }
                            }, MODAL_DELAY_AFTER_FLASH_MS);
                        })
                        .start())
                .start();
    }

    private void showDetectedResultModal(
            String laundryCategory,
            String colorType,
            ClassificationDetail classificationDetail) {
        if (detectionCropBitmap == null || detectionCropBox == null || frozenFrameBitmap == null) {
            return;
        }

        bindDetectionResultDropdowns(laundryCategory, colorType, classificationDetail);
        detectionResultImage.setImageBitmap(detectionCropBitmap);
        detectionResultImage.setVisibility(View.INVISIBLE);
        detectionModalScrim.setAlpha(0f);
        detectionResultModal.setAlpha(0f);
        detectionModalScrim.setVisibility(View.VISIBLE);
        detectionResultModal.setVisibility(View.VISIBLE);

        detectionResultModal.post(() -> {
            if (detectionCropBitmap == null || registerPage.getVisibility() != View.VISIBLE) {
                return;
            }
            RectF startRect = getCropRectInRegisterPage();
            RectF endRect = getViewRectInRegisterPage(detectionResultImage);
            if (startRect == null || endRect.width() <= 0f || endRect.height() <= 0f) {
                detectionResultImage.setVisibility(View.VISIBLE);
                detectionModalScrim.setAlpha(1f);
                detectionResultModal.setAlpha(1f);
                return;
            }
            animateCropIntoModal(startRect, endRect);
        });
    }

    private void setupDetectionDropdowns() {
        setDropdownItems(detectionCategoryDropdown, LaundryCategory.displayNames(), selectedCategory ->
                updateDetailDropdownForCategory(selectedCategory, null));
        setDropdownItems(detectionColorDropdown, LaundryCategory.colorTypes(), null);
    }

    private void bindDetectionResultDropdowns(
            String laundryCategory,
            String colorType,
            ClassificationDetail classificationDetail) {
        String[] categories = LaundryCategory.displayNames();
        String category = selectionOrDefault(categories, laundryCategory);
        String detailType = classificationDetail != null ? classificationDetail.label : null;
        setDropdownSelection(detectionCategoryDropdown, categories, category);
        updateDetailDropdownForCategory(category, detailType);
        setDropdownSelection(detectionColorDropdown, LaundryCategory.colorTypes(), colorType);
    }

    private void updateDetailDropdownForCategory(String category, String preferredDetailType) {
        String[] detailTypes = detailTypesForCategory(category);
        if (detailTypes.length == 0) {
            detectionDetailDropdown.setText("");
            detectionDetailDropdownContainer.setVisibility(View.GONE);
            return;
        }

        detectionDetailDropdownContainer.setVisibility(View.VISIBLE);
        setDropdownItems(detectionDetailDropdown, detailTypes, null);
        setDropdownSelection(detectionDetailDropdown, detailTypes, preferredDetailType);
    }

    private String[] detailTypesForCategory(String categoryName) {
        LaundryCategory category = LaundryCategory.fromDisplayName(categoryName);
        if (category == LaundryCategory.TOP) {
            return topDetailTypes;
        }
        if (category == LaundryCategory.BOTTOM) {
            return bottomDetailTypes;
        }
        return new String[0];
    }

    private void setDropdownItems(
            MaterialButton dropdown,
            String[] items,
            DropdownSelectionListener selectionListener) {
        dropdown.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(activity, dropdown);
            Menu menu = popupMenu.getMenu();
            for (int index = 0; index < items.length; index++) {
                menu.add(Menu.NONE, index, index, items[index]);
            }
            popupMenu.setOnMenuItemClickListener(menuItem -> {
                String selectedValue = String.valueOf(menuItem.getTitle());
                dropdown.setText(selectedValue);
                if (selectionListener != null) {
                    selectionListener.onSelected(selectedValue);
                }
                return true;
            });
            popupMenu.show();
        });
    }

    private void setDropdownSelection(
            MaterialButton dropdown,
            String[] items,
            String preferredValue) {
        dropdown.setText(selectionOrDefault(items, preferredValue));
    }

    private String selectionOrDefault(String[] items, String preferredValue) {
        if (items.length == 0) {
            return "";
        }
        if (preferredValue != null) {
            for (String item : items) {
                if (preferredValue.equals(item)) {
                    return item;
                }
            }
        }
        return items[0];
    }

    private void saveDetectionResultAndResume() {
        if (detectionCropBitmap == null || detectionCropBitmap.isRecycled()) {
            Toast.makeText(activity, R.string.detected_laundry_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        LaundryRecord record = createLaundryRecordFromModal();
        if (record == null) {
            Toast.makeText(activity, R.string.detected_laundry_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        detectionResultConfirm.setEnabled(false);
        try {
            laundryRecordStore.saveRecord(detectionCropBitmap, record);
            Toast.makeText(activity, R.string.detected_laundry_save_success, Toast.LENGTH_SHORT).show();
            clear();
            onRecordSaved.run();
        } catch (IOException | RuntimeException exception) {
            detectionResultConfirm.setEnabled(true);
            Toast.makeText(activity, R.string.detected_laundry_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelDetectionResultAndResume() {
        clear();
        onResultCanceled.run();
    }

    private LaundryRecord createLaundryRecordFromModal() {
        LaundryCategory selectedCategory = LaundryCategory.fromDisplayName(selectedText(detectionCategoryDropdown));
        String colorType = selectedText(detectionColorDropdown);
        if (selectedCategory == null || colorType.isEmpty()) {
            return null;
        }

        String detailType = null;
        Float detailConfidence = null;
        if (selectedCategory.supportsDetailTypes()) {
            detailType = selectedText(detectionDetailDropdown);
            if (detailType.isEmpty()) {
                detailType = null;
            }
            if (pendingClassificationDetail != null
                    && selectedCategory.recordCode.equals(pendingClassificationCategoryCode)
                    && pendingClassificationDetail.label.equals(detailType)) {
                detailConfidence = pendingClassificationDetail.confidence;
            }
        }

        return new LaundryRecord(
                selectedCategory.recordCode,
                detailType,
                colorType,
                pendingDetectedLabel,
                pendingDetectedConfidence,
                detailConfidence,
                System.currentTimeMillis());
    }

    private String selectedText(MaterialButton button) {
        CharSequence text = button.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void animateCropIntoModal(RectF startRect, RectF endRect) {
        if (cropModalAnimator != null) {
            cropModalAnimator.cancel();
        }
        detectionTransitionImage.setImageBitmap(detectionCropBitmap);
        detectionTransitionImage.setVisibility(View.VISIBLE);
        updateTransitionImageBounds(startRect);

        detectionModalScrim.animate()
                .alpha(1f)
                .setDuration(180L)
                .start();
        detectionResultModal.animate()
                .alpha(1f)
                .setDuration(CROP_MODAL_ANIMATION_MS)
                .start();

        cropModalAnimator = ValueAnimator.ofFloat(0f, 1f);
        cropModalAnimator.setDuration(CROP_MODAL_ANIMATION_MS);
        cropModalAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        cropModalAnimator.addUpdateListener(animator -> {
            float progress = (float) animator.getAnimatedValue();
            RectF currentRect = new RectF(
                    lerp(startRect.left, endRect.left, progress),
                    lerp(startRect.top, endRect.top, progress),
                    lerp(startRect.right, endRect.right, progress),
                    lerp(startRect.bottom, endRect.bottom, progress));
            updateTransitionImageBounds(currentRect);
        });
        cropModalAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                detectionTransitionImage.setVisibility(View.GONE);
                detectionTransitionImage.setImageDrawable(null);
                detectionResultImage.setVisibility(View.VISIBLE);
                cropModalAnimator = null;
            }
        });
        cropModalAnimator.start();
    }

    private void updateTransitionImageBounds(RectF rect) {
        ViewGroup.LayoutParams layoutParams = detectionTransitionImage.getLayoutParams();
        layoutParams.width = Math.max(1, Math.round(rect.width()));
        layoutParams.height = Math.max(1, Math.round(rect.height()));
        detectionTransitionImage.setLayoutParams(layoutParams);
        detectionTransitionImage.setX(rect.left);
        detectionTransitionImage.setY(rect.top);
    }

    private RectF getCropRectInRegisterPage() {
        if (detectionCropBox == null || frozenFrameBitmap == null) {
            return null;
        }

        RectF frozenFrameRect = getViewRectInRegisterPage(frozenFrame);
        float frameWidth = frozenFrameBitmap.getWidth();
        float frameHeight = frozenFrameBitmap.getHeight();
        float scale = Math.max(frozenFrame.getWidth() / frameWidth, frozenFrame.getHeight() / frameHeight);
        float scaledFrameWidth = frameWidth * scale;
        float scaledFrameHeight = frameHeight * scale;
        float offsetX = (frozenFrame.getWidth() - scaledFrameWidth) / 2f;
        float offsetY = (frozenFrame.getHeight() - scaledFrameHeight) / 2f;

        return new RectF(
                frozenFrameRect.left + offsetX + detectionCropBox.left * scaledFrameWidth,
                frozenFrameRect.top + offsetY + detectionCropBox.top * scaledFrameHeight,
                frozenFrameRect.left + offsetX + detectionCropBox.right * scaledFrameWidth,
                frozenFrameRect.top + offsetY + detectionCropBox.bottom * scaledFrameHeight);
    }

    private RectF getViewRectInRegisterPage(View view) {
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        registerPage.getLocationOnScreen(rootLocation);
        view.getLocationOnScreen(viewLocation);
        float left = viewLocation[0] - rootLocation[0];
        float top = viewLocation[1] - rootLocation[1];
        return new RectF(left, top, left + view.getWidth(), top + view.getHeight());
    }

    private void setDetectionCrop(DetectionCrop detectionCrop) {
        recycleFrame(detectionCropBitmap);
        if (detectionCrop == null) {
            detectionCropBitmap = null;
            detectionCropBox = null;
            return;
        }
        detectionCropBitmap = detectionCrop.bitmap;
        detectionCropBox = detectionCrop.normalizedBox;
    }

    private void freezeFrame(Bitmap bitmap) {
        clearFrozenFrame();
        frozenFrameBitmap = bitmap;
        if (frozenFrameBitmap != null) {
            frozenFrame.setImageBitmap(frozenFrameBitmap);
            frozenFrame.setVisibility(View.VISIBLE);
        }
    }

    private void clearDetectionResultModal() {
        if (cropModalAnimator != null) {
            cropModalAnimator.cancel();
            cropModalAnimator = null;
        }
        detectionModalScrim.animate().cancel();
        detectionResultModal.animate().cancel();
        detectionModalScrim.setAlpha(0f);
        detectionResultModal.setAlpha(0f);
        detectionModalScrim.setVisibility(View.GONE);
        detectionResultModal.setVisibility(View.GONE);
        detectionTransitionImage.setVisibility(View.GONE);
        detectionTransitionImage.setImageDrawable(null);
        detectionResultImage.setImageDrawable(null);
        detectionResultImage.setVisibility(View.VISIBLE);
        detectionCategoryDropdown.setText("");
        detectionDetailDropdown.setText("");
        detectionDetailDropdownContainer.setVisibility(View.GONE);
        detectionColorDropdown.setText("");
        detectionResultConfirm.setEnabled(true);
        clearPendingDetectionRecord();
        recycleFrame(detectionCropBitmap);
        detectionCropBitmap = null;
        detectionCropBox = null;
    }

    private void setPendingDetectionRecord(StableDetection detection) {
        pendingDetectedLabel = detection.detectionResult.label;
        pendingDetectedConfidence = detection.detectionResult.confidence;
        pendingClassificationCategoryCode = detection.category.recordCode;
        pendingClassificationDetail = detection.classificationDetail;
    }

    private void clearPendingDetectionRecord() {
        pendingDetectedLabel = null;
        pendingDetectedConfidence = 0f;
        pendingClassificationCategoryCode = null;
        pendingClassificationDetail = null;
    }

    private void clearFrozenFrame() {
        frozenFrame.setImageDrawable(null);
        frozenFrame.setVisibility(View.GONE);
        recycleFrame(frozenFrameBitmap);
        frozenFrameBitmap = null;
    }

    private void recycleFrame(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private interface DropdownSelectionListener {
        void onSelected(String value);
    }
}
