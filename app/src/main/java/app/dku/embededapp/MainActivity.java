package app.dku.embededapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import java.io.IOException;

import app.dku.embededapp.detection.AnalyzedDetection;
import app.dku.embededapp.detection.DetectionController;
import app.dku.embededapp.detection.StableDetection;
import app.dku.embededapp.ui.compose.LaundryComposeHost;
import app.dku.embededapp.ui.detection.DetectionOverlayView;
import app.dku.embededapp.ui.detection.DetectionResultController;

public class MainActivity extends AppCompatActivity implements DetectionController.Listener {
    private LaundryComposeHost.Handles uiHandles;
    private PreviewView cameraPreview;
    private DetectionOverlayView detectionOverlay;
    private LifecycleCameraController cameraController;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private DetectionController detectionController;
    private DetectionResultController detectionResultController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        uiHandles = LaundryComposeHost.install(this, createUiCallbacks());
        setupCamera();
        setupDetection();

        showPage(LaundryComposeHost.PAGE_HOME);
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
        if (uiHandles != null) {
            uiHandles.close();
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

    private void setupCamera() {
        cameraPreview = uiHandles.getCameraPreview();
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

    private void setupDetection() {
        detectionOverlay = uiHandles.getDetectionOverlay();

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
                uiHandles.getRegisterPage(),
                uiHandles.getDetectionResultViews(),
                topDetailTypes,
                bottomDetailTypes,
                () -> {
                    if (uiHandles != null) {
                        uiHandles.refreshLaundryData();
                    }
                    if (isRegisterPageVisible()) {
                        startCameraPreview();
                    }
                });
    }

    private LaundryComposeHost.Callbacks createUiCallbacks() {
        return new LaundryComposeHost.Callbacks() {
            @Override
            public void onPageSelected(int page) {
                showPage(page);
            }

            @Override
            public void onStartRegisterClicked() {
                showPage(LaundryComposeHost.PAGE_REGISTER);
            }

            @Override
            public void onViewGroupsClicked() {
                showPage(LaundryComposeHost.PAGE_GROUPS);
            }

            @Override
            public void onStartCameraClicked() {
                ensureCameraPreview();
            }
        };
    }

    private void showPage(int page) {
        if (page == LaundryComposeHost.PAGE_REGISTER) {
            uiHandles.showPage(page);
            ensureCameraPreview();
            return;
        }

        stopCameraPreview();
        uiHandles.showPage(page);
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
        return uiHandles != null && uiHandles.isRegisterPageVisible();
    }
}
