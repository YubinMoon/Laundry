package app.dku.embededapp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
    private TextView detectionResultMessage;
    private LifecycleCameraController cameraController;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ExecutorService inferenceExecutor;
    private LaundryDetector laundryDetector;
    private TopClassifier topClassifier;
    private volatile boolean analysisEnabled;
    private volatile boolean detectionLocked;
    private String lastDetectedLabel;
    private int stableLabelCount;
    private Bitmap frozenFrameBitmap;
    private Bitmap detectionCropBitmap;
    private RectF detectionCropBox;
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
        detectionResultMessage = findViewById(R.id.detection_result_message);

        inferenceExecutor = Executors.newSingleThreadExecutor();
        LaundryDetector loadedLaundryDetector = null;
        TopClassifier loadedTopClassifier = null;
        try {
            loadedLaundryDetector = new LaundryDetector(this);
            loadedTopClassifier = new TopClassifier(this);
            laundryDetector = loadedLaundryDetector;
            topClassifier = loadedTopClassifier;
        } catch (IOException | RuntimeException exception) {
            if (loadedTopClassifier != null) {
                loadedTopClassifier.close();
            }
            if (loadedLaundryDetector != null) {
                loadedLaundryDetector.close();
            }
            Toast.makeText(this, R.string.model_load_failed, Toast.LENGTH_SHORT).show();
        }

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
        findViewById(R.id.detection_result_confirm).setOnClickListener(view -> {
            resetDetectionState();
            if (pages[PAGE_REGISTER].getVisibility() == View.VISIBLE) {
                startCameraPreview();
            }
        });

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
        if (inferenceExecutor != null) {
            inferenceExecutor.shutdown();
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
        TopClassifier classifier = topClassifier;
        if (!analysisEnabled || detectionLocked || detector == null) {
            imageProxy.close();
            return;
        }

        LaundryDetector.DetectionResult result = null;
        AnalyzedDetection analyzedDetection = null;
        try {
            result = detector.detect(imageProxy, DETECTION_CONFIDENCE_THRESHOLD);
            if (result != null) {
                analyzedDetection = createAnalyzedDetection(result, classifier);
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
            TopClassifier classifier) {
        TopClassifier.Result topResult = null;
        if (classifier != null && isTopLabel(result.label) && result.normalizedBox != null) {
            DetectionCrop topCrop = null;
            try {
                topCrop = createDetectionCrop(result.frameBitmap, result.normalizedBox);
                if (topCrop != null) {
                    topResult = classifier.classify(topCrop.bitmap);
                }
            } finally {
                if (topCrop != null) {
                    recycleFrame(topCrop.bitmap);
                }
            }
        }
        return new AnalyzedDetection(result, formatDisplayLabel(result.label, topResult), topResult);
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
            freezeFrame(result.frameBitmap);
            flashAndShowDetectedResult(laundryCategory, colorType, analyzedDetection.topResult);
        } else {
            recycleFrame(result.frameBitmap);
        }
    }

    private void flashAndShowDetectedResult(
            String laundryCategory,
            String colorType,
            TopClassifier.Result topResult) {
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
                                    showDetectedResultModal(laundryCategory, colorType, topResult);
                                }
                            }, MODAL_DELAY_AFTER_FLASH_MS);
                        })
                        .start())
                .start();
    }

    private void showDetectedResultModal(
            String laundryCategory,
            String colorType,
            TopClassifier.Result topResult) {
        if (detectionCropBitmap == null || detectionCropBox == null || frozenFrameBitmap == null) {
            return;
        }

        detectionResultMessage.setText(createDetectedResultMessage(laundryCategory, colorType, topResult));
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
        recycleFrame(detectionCropBitmap);
        detectionCropBitmap = null;
        detectionCropBox = null;
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

    private String createDetectedResultMessage(
            String laundryCategory,
            String colorType,
            TopClassifier.Result topResult) {
        String message = getString(R.string.detected_laundry_message, laundryCategory, colorType);
        if (topResult == null) {
            return message;
        }
        return message
                + "\n\uC0C1\uC758 \uC885\uB958: "
                + topResult.label
                + " ("
                + Math.round(topResult.confidence * 100f)
                + "%)";
    }

    private String formatDisplayLabel(String label, TopClassifier.Result topResult) {
        if (label == null || topResult == null) {
            return label;
        }
        return label + " -> " + topResult.label;
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
        final TopClassifier.Result topResult;

        AnalyzedDetection(
                LaundryDetector.DetectionResult detectionResult,
                String displayLabel,
                TopClassifier.Result topResult) {
            this.detectionResult = detectionResult;
            this.displayLabel = displayLabel;
            this.topResult = topResult;
            this.displayConfidence = topResult != null ? topResult.confidence : detectionResult.confidence;
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
