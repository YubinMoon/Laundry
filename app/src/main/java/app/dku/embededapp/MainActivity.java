package app.dku.embededapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int PAGE_HOME = 0;
    private static final int PAGE_REGISTER = 1;
    private static final int PAGE_GROUPS = 2;
    private static final int PAGE_TIPS = 3;

    private View[] pages;
    private TextView screenTitle;
    private TextView screenSubtitle;
    private BottomNavigationView bottomNavigation;
    private PreviewView cameraPreview;
    private LifecycleCameraController cameraController;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

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
        cameraPreview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        cameraController = new LifecycleCameraController(this);
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
        try {
            cameraController.bindToLifecycle(this);
        } catch (RuntimeException exception) {
            Toast.makeText(this, R.string.camera_start_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopCameraPreview() {
        cameraController.unbind();
    }
}
