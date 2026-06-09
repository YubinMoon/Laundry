package app.dku.embededapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;

import app.dku.embededapp.detection.AnalyzedDetection;
import app.dku.embededapp.detection.DetectionController;
import app.dku.embededapp.detection.StableDetection;
import app.dku.embededapp.ui.detection.DetectionOverlayView;
import app.dku.embededapp.ui.detection.DetectionResultController;
import app.dku.embededapp.ui.groups.LaundryGroupController;
import app.dku.embededapp.ui.navigation.PageNavigator;

public class MainActivity extends AppCompatActivity implements DetectionController.Listener {
    private PageNavigator pageNavigator;
    private BottomNavigationView bottomNavigation;
    private PreviewView cameraPreview;
    private DetectionOverlayView detectionOverlay;
    private LifecycleCameraController cameraController;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private DetectionController detectionController;
    private DetectionResultController detectionResultController;
    private LaundryGroupController laundryGroupController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        setupSystemInsets();
        setupNavigation();
        setupGroups();
        setupCamera();
        setupDetection();
        setupBottomNavigation();
        setupButtons();

        bottomNavigation.setSelectedItemId(R.id.navigation_home);
    }

    @Override
    protected void onDestroy() {
        if (cameraController != null) {
            cameraController.unbind();
        }
        if (detectionController != null) {
            detectionController.close();
        }
        if (detectionResultController != null) {
            detectionResultController.close();
        }
        if (laundryGroupController != null) {
            laundryGroupController.close();
        }
        super.onDestroy();
    }

    @Override
    public boolean isDetectionPageVisible() {
        return isRegisterPageVisible();
    }

    @Override
    public void onDetectionChanged(AnalyzedDetection detection) {
        detectionOverlay.showDetection(
                detection.displayLabel,
                detection.displayConfidence,
                detection.detectionResult.normalizedBox,
                detection.detectionResult.frameWidth,
                detection.detectionResult.frameHeight);
    }

    @Override
    public void onDetectionCleared() {
        detectionOverlay.clearDetection();
    }

    @Override
    public void onStableDetection(StableDetection detection) {
        detectionResultController.showStableDetection(detection);
    }

    private void setupSystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupNavigation() {
        View[] pages = new View[] {
                findViewById(R.id.page_home),
                findViewById(R.id.page_register),
                findViewById(R.id.page_groups),
                findViewById(R.id.page_tips)
        };
        pageNavigator = new PageNavigator(
                pages,
                findViewById(R.id.screen_title),
                findViewById(R.id.screen_subtitle));
        bottomNavigation = findViewById(R.id.bottom_navigation);
    }

    private void setupCamera() {
        cameraPreview = findViewById(R.id.camera_preview);
        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraController = new LifecycleCameraController(this);
        cameraPreview.setController(cameraController);

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && isRegisterPageVisible()) {
                        startCameraPreview();
                    } else if (!granted) {
                        Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupGroups() {
        laundryGroupController = new LaundryGroupController(
                this,
                pageNavigator.getPage(PageNavigator.PAGE_GROUPS));
    }

    private void setupDetection() {
        detectionOverlay = findViewById(R.id.detection_overlay);

        String[] topDetailTypes = DetectionController.defaultTopDetailTypes();
        String[] bottomDetailTypes = DetectionController.defaultBottomDetailTypes();
        try {
            detectionController = new DetectionController(this, cameraController, this);
            topDetailTypes = detectionController.getTopDetailTypes();
            bottomDetailTypes = detectionController.getBottomDetailTypes();
        } catch (IOException | RuntimeException exception) {
            Toast.makeText(this, R.string.model_load_failed, Toast.LENGTH_SHORT).show();
        }

        detectionResultController = new DetectionResultController(
                this,
                pageNavigator.getPage(PageNavigator.PAGE_REGISTER),
                topDetailTypes,
                bottomDetailTypes,
                () -> {
                    if (laundryGroupController != null) {
                        laundryGroupController.refresh();
                    }
                    if (isRegisterPageVisible()) {
                        startCameraPreview();
                    }
                });
    }

    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_home) {
                stopCameraPreview();
                pageNavigator.showPage(
                        PageNavigator.PAGE_HOME,
                        R.string.home_title,
                        R.string.home_subtitle);
            } else if (itemId == R.id.navigation_register) {
                pageNavigator.showPage(
                        PageNavigator.PAGE_REGISTER,
                        R.string.register_title,
                        R.string.register_subtitle);
                ensureCameraPreview();
            } else if (itemId == R.id.navigation_groups) {
                stopCameraPreview();
                pageNavigator.showPage(
                        PageNavigator.PAGE_GROUPS,
                        R.string.groups_title,
                        R.string.groups_subtitle);
                laundryGroupController.refresh();
            } else if (itemId == R.id.navigation_tips) {
                stopCameraPreview();
                pageNavigator.showPage(
                        PageNavigator.PAGE_TIPS,
                        R.string.tips_title,
                        R.string.tips_subtitle);
            } else {
                return false;
            }
            return true;
        });
    }

    private void setupButtons() {
        findViewById(R.id.button_register).setOnClickListener(
                view -> bottomNavigation.setSelectedItemId(R.id.navigation_register));
        findViewById(R.id.button_view_groups).setOnClickListener(
                view -> bottomNavigation.setSelectedItemId(R.id.navigation_groups));
        findViewById(R.id.button_capture).setOnClickListener(view -> ensureCameraPreview());
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
        detectionResultController.clear();
        if (detectionController != null) {
            detectionController.startAnalysis();
        }
        try {
            cameraController.bindToLifecycle(this);
        } catch (RuntimeException exception) {
            if (detectionController != null) {
                detectionController.stopAnalysis();
            }
            Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopCameraPreview() {
        if (detectionController != null) {
            detectionController.stopAnalysis();
        }
        if (detectionResultController != null) {
            detectionResultController.clear();
        }
        if (cameraController != null) {
            cameraController.unbind();
        }
    }

    private boolean isRegisterPageVisible() {
        return pageNavigator.isPageVisible(PageNavigator.PAGE_REGISTER);
    }
}
