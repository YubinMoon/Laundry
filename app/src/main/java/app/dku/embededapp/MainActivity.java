package app.dku.embededapp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int PAGE_HOME = 0;
    private static final int PAGE_REGISTER = 1;
    private static final int PAGE_GROUPS = 2;
    private static final int PAGE_TIPS = 3;
    private static final float DETECTION_CONFIDENCE_THRESHOLD = 0.40f;
    private static final int REQUIRED_STABLE_LABEL_COUNT = 3;
    private static final long FLASH_HALF_DURATION_MS = 150L;
    private static final long MODAL_DELAY_AFTER_FLASH_MS = 200L;
    private static final long CROP_MODAL_ANIMATION_MS = 360L;
    private static final String CATEGORY_TOP = "상의";
    private static final String CATEGORY_BOTTOM = "하의";
    private static final String CATEGORY_TOWEL = "수건";
    private static final String CATEGORY_SOCK = "양말";
    private static final String[] LAUNDRY_CATEGORIES = {
            CATEGORY_TOP,
            CATEGORY_BOTTOM,
            CATEGORY_TOWEL,
            CATEGORY_SOCK
    };
    private static final String[] COLOR_TYPES = {
            "흰색",
            "검은색",
            "밝은색",
            "어두운색",
            "혼합"
    };
    private static final String[] DEFAULT_TOP_DETAIL_TYPES = {
            "Activewear",
            "Denim",
            "Hoodies",
            "Shirts",
            "Sweaters",
            "T-shirts"
    };
    private static final String[] DEFAULT_BOTTOM_DETAIL_TYPES = {
            "Activewear",
            "Chinos",
            "Jeans",
            "Joggers",
            "Skirts",
            "Slacks"
    };

    private View[] pages;
    private View registerPage;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private BottomNavigationView bottomNavigation;
    private PreviewView cameraPreview;
    private ImageView frozenFrame;
    private DetectionOverlayView detectionOverlay;
    private View cameraFlash;
    private View detectionModalScrim;
    private View detectionResultModal;
    private ImageView detectionResultImage;
    private ImageView detectionTransitionImage;
    private MaterialButton detectionCategoryDropdown;
    private View detectionDetailDropdownContainer;
    private MaterialButton detectionDetailDropdown;
    private MaterialButton detectionColorDropdown;
    private MaterialButton detectionResultConfirm;
    private LifecycleCameraController cameraController;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ExecutorService inferenceExecutor;
    private LaundryRecordStore laundryRecordStore;
    private LaundryDetector laundryDetector;
    private TopClassifier topClassifier;
    private BottomClassifier bottomClassifier;
    private String[] topDetailTypes = DEFAULT_TOP_DETAIL_TYPES;
    private String[] bottomDetailTypes = DEFAULT_BOTTOM_DETAIL_TYPES;
    private volatile boolean analysisEnabled;
    private volatile boolean detectionLocked;
    private String lastDetectedLabel;
    private int stableLabelCount;
    private Bitmap frozenFrameBitmap;
    private Bitmap detectionCropBitmap;
    private RectF detectionCropBox;
    private String pendingDetectedLabel;
    private float pendingDetectedConfidence;
    private String pendingClassificationCategoryCode;
    private ClassificationDetail pendingClassificationDetail;
    private ValueAnimator cropModalAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pages = new View[] {
                findViewById(R.id.page_home),
                findViewById(R.id.page_register),
                findViewById(R.id.page_groups),
                findViewById(R.id.page_tips)
        };
        registerPage = pages[PAGE_REGISTER];
        screenTitle = findViewById(R.id.screen_title);
        screenSubtitle = findViewById(R.id.screen_subtitle);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        cameraPreview = findViewById(R.id.camera_preview);
        frozenFrame = findViewById(R.id.frozen_frame);
        detectionOverlay = findViewById(R.id.detection_overlay);
        cameraFlash = findViewById(R.id.camera_flash);
        detectionModalScrim = findViewById(R.id.detection_modal_scrim);
        detectionResultModal = findViewById(R.id.detection_result_modal);
        detectionResultImage = findViewById(R.id.detection_result_image);
        detectionTransitionImage = findViewById(R.id.detection_transition_image);
        detectionCategoryDropdown = findViewById(R.id.detection_category_dropdown);
        detectionDetailDropdownContainer = findViewById(R.id.detection_detail_group);
        detectionDetailDropdown = findViewById(R.id.detection_detail_dropdown);
        detectionColorDropdown = findViewById(R.id.detection_color_dropdown);
        detectionResultConfirm = findViewById(R.id.detection_result_confirm);

        inferenceExecutor = Executors.newSingleThreadExecutor();
        laundryRecordStore = new LaundryRecordStore(this);
        LaundryDetector loadedLaundryDetector = null;
        TopClassifier loadedTopClassifier = null;
        BottomClassifier loadedBottomClassifier = null;
        try {
            loadedLaundryDetector = new LaundryDetector(this);
            loadedTopClassifier = new TopClassifier(this);
            loadedBottomClassifier = new BottomClassifier(this);
            laundryDetector = loadedLaundryDetector;
            topClassifier = loadedTopClassifier;
            bottomClassifier = loadedBottomClassifier;
            topDetailTypes = nonEmptyOrDefault(loadedTopClassifier.getLabels(), DEFAULT_TOP_DETAIL_TYPES);
            bottomDetailTypes = nonEmptyOrDefault(loadedBottomClassifier.getLabels(), DEFAULT_BOTTOM_DETAIL_TYPES);
        } catch (IOException | RuntimeException exception) {
            if (loadedBottomClassifier != null) {
                loadedBottomClassifier.close();
            }
            if (loadedTopClassifier != null) {
                loadedTopClassifier.close();
            }
            if (loadedLaundryDetector != null) {
                loadedLaundryDetector.close();
            }
            Toast.makeText(this, R.string.model_load_failed, Toast.LENGTH_SHORT).show();
        }
        setupDetectionDropdowns();

        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraController = new LifecycleCameraController(this);
        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        cameraController.setImageAnalysisAnalyzer(inferenceExecutor, this::analyzeFrame);
        cameraPreview.setController(cameraController);

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && pages[PAGE_REGISTER].getVisibility() == View.VISIBLE) {
                        startCameraPreview();
                    } else if (!granted) {
                        Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                stopCameraPreview();
                showPage(PAGE_HOME, R.string.home_title, R.string.home_subtitle);
            } else if (itemId == R.id.navigation_register) {
                showPage(PAGE_REGISTER, R.string.register_title, R.string.register_subtitle);
                ensureCameraPreview();
            } else if (itemId == R.id.navigation_groups) {
                stopCameraPreview();
                showPage(PAGE_GROUPS, R.string.groups_title, R.string.groups_subtitle);
            } else if (itemId == R.id.navigation_tips) {
                stopCameraPreview();
                showPage(PAGE_TIPS, R.string.tips_title, R.string.tips_subtitle);
            } else {
                return false;
            }
            return true;
        });

        findViewById(R.id.button_register).setOnClickListener(
                view -> bottomNavigation.setSelectedItemId(R.id.navigation_register));
        findViewById(R.id.button_view_groups).setOnClickListener(
                view -> bottomNavigation.setSelectedItemId(R.id.navigation_groups));
        findViewById(R.id.button_capture).setOnClickListener(view -> ensureCameraPreview());
        detectionResultConfirm.setOnClickListener(view -> saveDetectionResultAndResume());

        bottomNavigation.setSelectedItemId(R.id.navigation_home);
    }

    @Override
    protected void onDestroy() {
        analysisEnabled = false;
        if (cameraController != null) {
            cameraController.unbind();
        }
        if (laundryDetector != null) {
            laundryDetector.close();
        }
        if (topClassifier != null) {
            topClassifier.close();
        }
        if (bottomClassifier != null) {
            bottomClassifier.close();
        }
        if (inferenceExecutor != null) {
            inferenceExecutor.shutdown();
        }
        if (laundryRecordStore != null) {
            laundryRecordStore.close();
        }
        clearDetectionResultModal();
        clearFrozenFrame();
        super.onDestroy();
    }

    private void showPage(int pageIndex, @StringRes int titleId, @StringRes int subtitleId) {
        for (int index = 0; index < pages.length; index++) {
            pages[index].setVisibility(index == pageIndex ? View.VISIBLE : View.GONE);
        }
        screenTitle.setText(titleId);
        screenSubtitle.setText(subtitleId);
    }

    private void ensureCameraPreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCameraPreview() {
        resetDetectionState();
        try {
            cameraController.bindToLifecycle(this);
            analysisEnabled = laundryDetector != null;
        } catch (RuntimeException exception) {
            analysisEnabled = false;
            Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopCameraPreview() {
        analysisEnabled = false;
        detectionLocked = false;
        resetDetectionState();
        cameraController.unbind();
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        LaundryDetector detector = laundryDetector;
        TopClassifier topClassifier = this.topClassifier;
        BottomClassifier bottomClassifier = this.bottomClassifier;
        if (!analysisEnabled || detectionLocked || detector == null) {
            imageProxy.close();
            return;
        }

        LaundryDetector.DetectionResult result = null;
        AnalyzedDetection analyzedDetection = null;
        try {
            result = detector.detect(imageProxy, DETECTION_CONFIDENCE_THRESHOLD);
            if (result != null) {
                analyzedDetection = createAnalyzedDetection(result, topClassifier, bottomClassifier);
                result = null;
            }
        } catch (RuntimeException exception) {
            if (result != null) {
                recycleFrame(result.frameBitmap);
            }
            // Drop malformed camera frames without interrupting the preview.
        } finally {
            imageProxy.close();
        }

        if (analyzedDetection != null) {
            AnalyzedDetection finalResult = analyzedDetection;
            runOnUiThread(() -> handleDetectionResult(finalResult));
        }
    }

    private AnalyzedDetection createAnalyzedDetection(
            LaundryDetector.DetectionResult result,
            TopClassifier topClassifier,
            BottomClassifier bottomClassifier) {
        ClassificationDetail classificationDetail = null;
        if (result.normalizedBox != null && (isTopLabel(result.label) || isBottomLabel(result.label))) {
            DetectionCrop crop = null;
            try {
                crop = createDetectionCrop(result.frameBitmap, result.normalizedBox);
                if (crop != null && isTopLabel(result.label) && topClassifier != null) {
                    TopClassifier.Result topResult = topClassifier.classify(crop.bitmap);
                    if (topResult != null) {
                        classificationDetail = new ClassificationDetail(
                                topResult.label,
                                topResult.confidence);
                    }
                } else if (crop != null && isBottomLabel(result.label) && bottomClassifier != null) {
                    BottomClassifier.Result bottomResult = bottomClassifier.classify(crop.bitmap);
                    if (bottomResult != null) {
                        classificationDetail = new ClassificationDetail(
                                bottomResult.label,
                                bottomResult.confidence);
                    }
                }
            } finally {
                if (crop != null) {
                    recycleFrame(crop.bitmap);
                }
            }
        }
        return new AnalyzedDetection(
                result,
                formatDisplayLabel(result.label, classificationDetail),
                classificationDetail);
    }

    private void handleDetectionResult(AnalyzedDetection analyzedDetection) {
        LaundryDetector.DetectionResult result = analyzedDetection.detectionResult;
        if (!analysisEnabled
                || detectionLocked
                || pages[PAGE_REGISTER].getVisibility() != View.VISIBLE) {
            recycleFrame(result.frameBitmap);
            return;
        }

        String laundryCategory = mapLabelToLaundryCategory(result.label);
        if (result.label == null || laundryCategory == null) {
            detectionOverlay.clearDetection();
            resetLabelStreak();
            recycleFrame(result.frameBitmap);
            return;
        }

        detectionOverlay.showDetection(
                analyzedDetection.displayLabel,
                analyzedDetection.displayConfidence,
                result.normalizedBox,
                result.frameWidth,
                result.frameHeight);

        if (analyzedDetection.displayLabel.equals(lastDetectedLabel)) {
            stableLabelCount++;
        } else {
            lastDetectedLabel = analyzedDetection.displayLabel;
            stableLabelCount = 1;
        }

        if (stableLabelCount >= REQUIRED_STABLE_LABEL_COUNT) {
            detectionLocked = true;
            analysisEnabled = false;
            String colorType = LaundryColorAnalyzer.detectColorType(result.frameBitmap, result.normalizedBox);
            setDetectionCrop(createDetectionCrop(result.frameBitmap, result.normalizedBox));
            setPendingDetectionRecord(
                    result.label,
                    result.confidence,
                    categoryCodeForCategory(laundryCategory),
                    analyzedDetection.classificationDetail);
            freezeFrame(result.frameBitmap);
            flashAndShowDetectedResult(laundryCategory, colorType, analyzedDetection.classificationDetail);
        } else {
            recycleFrame(result.frameBitmap);
        }
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
                                if (detectionLocked
                                        && pages[PAGE_REGISTER].getVisibility() == View.VISIBLE) {
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
            if (!detectionLocked
                    || detectionCropBitmap == null
                    || pages[PAGE_REGISTER].getVisibility() != View.VISIBLE) {
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
        setDropdownItems(detectionCategoryDropdown, LAUNDRY_CATEGORIES, selectedCategory ->
                updateDetailDropdownForCategory(selectedCategory, null));
        setDropdownItems(detectionColorDropdown, COLOR_TYPES, null);
    }

    private void bindDetectionResultDropdowns(
            String laundryCategory,
            String colorType,
            ClassificationDetail classificationDetail) {
        String category = selectionOrDefault(LAUNDRY_CATEGORIES, laundryCategory);
        String detailType = classificationDetail != null ? classificationDetail.label : null;
        setDropdownSelection(detectionCategoryDropdown, LAUNDRY_CATEGORIES, category);
        updateDetailDropdownForCategory(category, detailType);
        setDropdownSelection(detectionColorDropdown, COLOR_TYPES, colorType);
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

    private String[] detailTypesForCategory(String category) {
        if (CATEGORY_TOP.equals(category)) {
            return topDetailTypes;
        }
        if (CATEGORY_BOTTOM.equals(category)) {
            return bottomDetailTypes;
        }
        return new String[0];
    }

    private void setDropdownItems(
            MaterialButton dropdown,
            String[] items,
            DropdownSelectionListener selectionListener) {
        dropdown.setOnClickListener(view -> {
            PopupMenu popupMenu = new PopupMenu(this, dropdown);
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

    private String[] nonEmptyOrDefault(String[] items, String[] defaultItems) {
        return items.length > 0 ? items : defaultItems;
    }

    private interface DropdownSelectionListener {
        void onSelected(String value);
    }

    private void saveDetectionResultAndResume() {
        if (detectionCropBitmap == null || detectionCropBitmap.isRecycled() || laundryRecordStore == null) {
            Toast.makeText(this, R.string.detected_laundry_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        LaundryRecord record = createLaundryRecordFromModal();
        if (record == null) {
            Toast.makeText(this, R.string.detected_laundry_save_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        detectionResultConfirm.setEnabled(false);
        try {
            laundryRecordStore.saveRecord(detectionCropBitmap, record);
            Toast.makeText(this, R.string.detected_laundry_save_success, Toast.LENGTH_SHORT).show();
            resetDetectionState();
            if (pages[PAGE_REGISTER].getVisibility() == View.VISIBLE) {
                startCameraPreview();
            }
        } catch (IOException | RuntimeException exception) {
            detectionResultConfirm.setEnabled(true);
            Toast.makeText(this, R.string.detected_laundry_save_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private LaundryRecord createLaundryRecordFromModal() {
        String categoryCode = selectedCategoryCode();
        String colorType = selectedText(detectionColorDropdown);
        if (categoryCode == null || colorType.isEmpty()) {
            return null;
        }

        String detailType = null;
        Float detailConfidence = null;
        if (LaundryRecord.CATEGORY_TOP.equals(categoryCode)
                || LaundryRecord.CATEGORY_BOTTOM.equals(categoryCode)) {
            detailType = selectedText(detectionDetailDropdown);
            if (detailType.isEmpty()) {
                detailType = null;
            }
            if (pendingClassificationDetail != null
                    && categoryCode.equals(pendingClassificationCategoryCode)
                    && pendingClassificationDetail.label.equals(detailType)) {
                detailConfidence = pendingClassificationDetail.confidence;
            }
        }

        return new LaundryRecord(
                categoryCode,
                detailType,
                colorType,
                pendingDetectedLabel,
                pendingDetectedConfidence,
                detailConfidence,
                System.currentTimeMillis());
    }

    private String selectedCategoryCode() {
        return categoryCodeForCategory(selectedText(detectionCategoryDropdown));
    }

    private String categoryCodeForCategory(String category) {
        if (CATEGORY_TOP.equals(category)) {
            return LaundryRecord.CATEGORY_TOP;
        }
        if (CATEGORY_BOTTOM.equals(category)) {
            return LaundryRecord.CATEGORY_BOTTOM;
        }
        if (CATEGORY_TOWEL.equals(category)) {
            return LaundryRecord.CATEGORY_TOWEL;
        }
        if (CATEGORY_SOCK.equals(category)) {
            return LaundryRecord.CATEGORY_SOCK;
        }
        return null;
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

    private DetectionCrop createDetectionCrop(Bitmap frameBitmap, RectF normalizedBox) {
        if (frameBitmap == null || normalizedBox == null) {
            return null;
        }

        int imageWidth = frameBitmap.getWidth();
        int imageHeight = frameBitmap.getHeight();
        float boxLeft = normalizedBox.left * imageWidth;
        float boxTop = normalizedBox.top * imageHeight;
        float boxRight = normalizedBox.right * imageWidth;
        float boxBottom = normalizedBox.bottom * imageHeight;
        float boxWidth = Math.max(1f, boxRight - boxLeft);
        float boxHeight = Math.max(1f, boxBottom - boxTop);
        int side = Math.round(Math.min(Math.max(boxWidth, boxHeight), Math.min(imageWidth, imageHeight)));
        side = Math.max(1, side);

        float centerX = (boxLeft + boxRight) / 2f;
        float centerY = (boxTop + boxBottom) / 2f;
        int left = Math.round(clamp(centerX - side / 2f, 0f, imageWidth - side));
        int top = Math.round(clamp(centerY - side / 2f, 0f, imageHeight - side));
        if (left + side > imageWidth) {
            left = imageWidth - side;
        }
        if (top + side > imageHeight) {
            top = imageHeight - side;
        }

        Bitmap cropBitmap = Bitmap.createBitmap(frameBitmap, left, top, side, side);
        RectF cropBox = new RectF(
                left / (float) imageWidth,
                top / (float) imageHeight,
                (left + side) / (float) imageWidth,
                (top + side) / (float) imageHeight);
        return new DetectionCrop(cropBitmap, cropBox);
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

    private void resetDetectionState() {
        resetLabelStreak();
        detectionLocked = false;
        cameraFlash.animate().cancel();
        cameraFlash.setAlpha(0f);
        cameraFlash.setVisibility(View.GONE);
        detectionOverlay.clearDetection();
        clearDetectionResultModal();
        clearFrozenFrame();
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

    private void setPendingDetectionRecord(
            String detectedLabel,
            float detectedConfidence,
            String classificationCategoryCode,
            ClassificationDetail classificationDetail) {
        pendingDetectedLabel = detectedLabel;
        pendingDetectedConfidence = detectedConfidence;
        pendingClassificationCategoryCode = classificationCategoryCode;
        pendingClassificationDetail = classificationDetail;
    }

    private void clearPendingDetectionRecord() {
        pendingDetectedLabel = null;
        pendingDetectedConfidence = 0f;
        pendingClassificationCategoryCode = null;
        pendingClassificationDetail = null;
    }

    private void resetLabelStreak() {
        lastDetectedLabel = null;
        stableLabelCount = 0;
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

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private String formatDisplayLabel(String label, ClassificationDetail classificationDetail) {
        if (label == null || classificationDetail == null) {
            return label;
        }
        return label + " -> " + classificationDetail.label;
    }

    private boolean isTopLabel(String label) {
        if (label == null) {
            return false;
        }
        switch (label) {
            case "short_sleeved_shirt":
            case "long_sleeved_shirt":
            case "outerwear":
            case "vest":
            case "sling":
                return true;
            default:
                return false;
        }
    }

    private boolean isBottomLabel(String label) {
        if (label == null) {
            return false;
        }
        switch (label) {
            case "shorts":
            case "trousers":
            case "skirt":
                return true;
            default:
                return false;
        }
    }

    private String mapLabelToLaundryCategory(String label) {
        if (label == null) {
            return null;
        }
        switch (label) {
            case "short_sleeved_shirt":
            case "long_sleeved_shirt":
            case "outerwear":
            case "vest":
            case "sling":
                return "상의";
            case "shorts":
            case "trousers":
            case "skirt":
                return "하의";
            case "towel":
                return "수건";
            case "sock":
                return "양말";
            default:
                return null;
        }
    }

    private static final class AnalyzedDetection {
        final LaundryDetector.DetectionResult detectionResult;
        final String displayLabel;
        final float displayConfidence;
        final ClassificationDetail classificationDetail;

        AnalyzedDetection(
                LaundryDetector.DetectionResult detectionResult,
                String displayLabel,
                ClassificationDetail classificationDetail) {
            this.detectionResult = detectionResult;
            this.displayLabel = displayLabel;
            this.classificationDetail = classificationDetail;
            this.displayConfidence = classificationDetail != null
                    ? classificationDetail.confidence
                    : detectionResult.confidence;
        }
    }

    private static final class ClassificationDetail {
        final String label;
        final float confidence;

        ClassificationDetail(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    private static final class DetectionCrop {
        final Bitmap bitmap;
        final RectF normalizedBox;

        DetectionCrop(Bitmap bitmap, RectF normalizedBox) {
            this.bitmap = bitmap;
            this.normalizedBox = normalizedBox;
        }
    }
}
