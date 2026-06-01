package app.dku.embededapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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

    private View[] pages;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private BottomNavigationView bottomNavigation;
    private PreviewView cameraPreview;
    private ImageView frozenFrame;
    private DetectionOverlayView detectionOverlay;
    private View cameraFlash;
    private LifecycleCameraController cameraController;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ExecutorService inferenceExecutor;
    private LaundryDetector laundryDetector;
    private volatile boolean analysisEnabled;
    private volatile boolean detectionLocked;
    private String lastDetectedLabel;
    private int stableLabelCount;
    private Bitmap frozenFrameBitmap;

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
        screenTitle = findViewById(R.id.screen_title);
        screenSubtitle = findViewById(R.id.screen_subtitle);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        cameraPreview = findViewById(R.id.camera_preview);
        frozenFrame = findViewById(R.id.frozen_frame);
        detectionOverlay = findViewById(R.id.detection_overlay);
        cameraFlash = findViewById(R.id.camera_flash);

        inferenceExecutor = Executors.newSingleThreadExecutor();
        try {
            laundryDetector = new LaundryDetector(this);
        } catch (IOException | RuntimeException exception) {
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
        if (inferenceExecutor != null) {
            inferenceExecutor.shutdown();
        }
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
        if (!analysisEnabled || detectionLocked || detector == null) {
            imageProxy.close();
            return;
        }

        LaundryDetector.DetectionResult result = null;
        try {
            result = detector.detect(imageProxy, DETECTION_CONFIDENCE_THRESHOLD);
        } catch (RuntimeException exception) {
            // Drop malformed camera frames without interrupting the preview.
        } finally {
            imageProxy.close();
        }

        if (result != null) {
            LaundryDetector.DetectionResult finalResult = result;
            runOnUiThread(() -> handleDetectionResult(finalResult));
        }
    }

    private void handleDetectionResult(LaundryDetector.DetectionResult result) {
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
                result.label,
                result.confidence,
                result.normalizedBox,
                result.frameWidth,
                result.frameHeight);

        if (result.label.equals(lastDetectedLabel)) {
            stableLabelCount++;
        } else {
            lastDetectedLabel = result.label;
            stableLabelCount = 1;
        }

        if (stableLabelCount >= REQUIRED_STABLE_LABEL_COUNT) {
            detectionLocked = true;
            analysisEnabled = false;
            freezeFrame(result.frameBitmap);
            flashAndShowDetectedDialog(laundryCategory);
        } else {
            recycleFrame(result.frameBitmap);
        }
    }

    private void flashAndShowDetectedDialog(String laundryCategory) {
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
                                    showDetectedDialog(laundryCategory);
                                }
                            }, MODAL_DELAY_AFTER_FLASH_MS);
                        })
                        .start())
                .start();
    }

    private void showDetectedDialog(String laundryCategory) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detected_laundry_title)
                .setMessage(getString(R.string.detected_laundry_message, laundryCategory))
                .setPositiveButton(R.string.detected_laundry_confirm, (dialog, which) -> {
                    resetDetectionState();
                    if (pages[PAGE_REGISTER].getVisibility() == View.VISIBLE) {
                        startCameraPreview();
                    }
                })
                .setCancelable(false)
                .show();
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
        clearFrozenFrame();
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
}
