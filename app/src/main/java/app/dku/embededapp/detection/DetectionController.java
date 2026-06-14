package app.dku.embededapp.detection;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.LifecycleCameraController;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.dku.embededapp.ml.BottomClassifier;
import app.dku.embededapp.ml.LaundryDetector;
import app.dku.embededapp.ml.TopClassifier;

public final class DetectionController implements AutoCloseable {
    private static final float DETECTION_CONFIDENCE_THRESHOLD = 0.40f;
    private static final long DETECTION_ANALYSIS_INTERVAL_MS = 300L;
    private static final int REQUIRED_STABLE_LABEL_COUNT = 3;
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

    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService inferenceExecutor;
    private final LaundryDetector laundryDetector;
    private final TopClassifier topClassifier;
    private final BottomClassifier bottomClassifier;
    private final String[] topDetailTypes;
    private final String[] bottomDetailTypes;

    private volatile boolean analysisEnabled;
    private volatile boolean detectionLocked;
    private volatile long lastAnalysisUptimeMillis;
    private String lastDetectedClassLabel;
    private int stableLabelCount;

    public DetectionController(
            Context context,
            LifecycleCameraController cameraController,
            Listener listener) throws IOException {
        this.listener = listener;

        ExecutorService loadedExecutor = Executors.newSingleThreadExecutor();
        LaundryDetector loadedLaundryDetector = null;
        TopClassifier loadedTopClassifier = null;
        BottomClassifier loadedBottomClassifier = null;
        try {
            loadedLaundryDetector = new LaundryDetector(context);
            loadedTopClassifier = new TopClassifier(context);
            loadedBottomClassifier = new BottomClassifier(context);
        } catch (IOException | RuntimeException exception) {
            closeQuietly(loadedBottomClassifier);
            closeQuietly(loadedTopClassifier);
            closeQuietly(loadedLaundryDetector);
            loadedExecutor.shutdown();
            throw exception;
        }

        inferenceExecutor = loadedExecutor;
        laundryDetector = loadedLaundryDetector;
        topClassifier = loadedTopClassifier;
        bottomClassifier = loadedBottomClassifier;
        topDetailTypes = nonEmptyOrDefault(loadedTopClassifier.getLabels(), DEFAULT_TOP_DETAIL_TYPES);
        bottomDetailTypes = nonEmptyOrDefault(loadedBottomClassifier.getLabels(), DEFAULT_BOTTOM_DETAIL_TYPES);

        cameraController.setImageAnalysisBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST);
        cameraController.setImageAnalysisAnalyzer(inferenceExecutor, this::analyzeFrame);
    }

    public void startAnalysis() {
        resetLabelStreak();
        lastAnalysisUptimeMillis = 0L;
        detectionLocked = false;
        analysisEnabled = true;
        listener.onDetectionCleared();
    }

    public void stopAnalysis() {
        analysisEnabled = false;
        detectionLocked = false;
        lastAnalysisUptimeMillis = 0L;
        resetLabelStreak();
        listener.onDetectionCleared();
    }

    public String[] getTopDetailTypes() {
        return topDetailTypes.clone();
    }

    public String[] getBottomDetailTypes() {
        return bottomDetailTypes.clone();
    }

    public static String[] defaultTopDetailTypes() {
        return DEFAULT_TOP_DETAIL_TYPES.clone();
    }

    public static String[] defaultBottomDetailTypes() {
        return DEFAULT_BOTTOM_DETAIL_TYPES.clone();
    }

    @Override
    public void close() {
        analysisEnabled = false;
        detectionLocked = true;
        closeQuietly(bottomClassifier);
        closeQuietly(topClassifier);
        closeQuietly(laundryDetector);
        inferenceExecutor.shutdown();
    }

    private void analyzeFrame(ImageProxy imageProxy) {
        if (!analysisEnabled || detectionLocked) {
            imageProxy.close();
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long elapsedMillis = now - lastAnalysisUptimeMillis;
        if (lastAnalysisUptimeMillis > 0L && elapsedMillis < DETECTION_ANALYSIS_INTERVAL_MS) {
            imageProxy.close();
            return;
        }
        lastAnalysisUptimeMillis = now;

        LaundryDetector.DetectionResult result = null;
        AnalyzedDetection analyzedDetection = null;
        try {
            result = laundryDetector.detect(imageProxy, DETECTION_CONFIDENCE_THRESHOLD);
            if (result != null) {
                analyzedDetection = createAnalyzedDetection(result);
                result = null;
            }
        } catch (RuntimeException exception) {
            if (result != null) {
                DetectionCropper.recycle(result.frameBitmap);
            }
        } finally {
            imageProxy.close();
        }

        if (analyzedDetection != null) {
            AnalyzedDetection finalResult = analyzedDetection;
            mainHandler.post(() -> handleAnalyzedDetection(finalResult));
        } else {
            mainHandler.post(this::handleNoDetection);
        }
    }

    private AnalyzedDetection createAnalyzedDetection(LaundryDetector.DetectionResult result) {
        ClassificationDetail classificationDetail = createClassificationDetail(result);
        return new AnalyzedDetection(
                result,
                formatDisplayLabel(result.label, classificationDetail),
                classificationDetail);
    }

    private ClassificationDetail createClassificationDetail(LaundryDetector.DetectionResult result) {
        if (result.normalizedBox == null
                || !(LaundryCategory.TOP.matchesModelLabel(result.label)
                || LaundryCategory.BOTTOM.matchesModelLabel(result.label))) {
            return null;
        }

        DetectionCrop crop = null;
        try {
            crop = DetectionCropper.createCrop(result.frameBitmap, result.normalizedBox);
            if (crop == null) {
                return null;
            }
            if (LaundryCategory.TOP.matchesModelLabel(result.label)) {
                TopClassifier.Result topResult = topClassifier.classify(crop.bitmap);
                return topResult == null ? null : new ClassificationDetail(
                        topResult.label,
                        topResult.confidence);
            }
            BottomClassifier.Result bottomResult = bottomClassifier.classify(crop.bitmap);
            return bottomResult == null ? null : new ClassificationDetail(
                    bottomResult.label,
                    bottomResult.confidence);
        } finally {
            if (crop != null) {
                DetectionCropper.recycle(crop.bitmap);
            }
        }
    }

    private void handleAnalyzedDetection(AnalyzedDetection analyzedDetection) {
        LaundryDetector.DetectionResult result = analyzedDetection.detectionResult;
        if (!analysisEnabled || detectionLocked || !listener.isDetectionPageVisible()) {
            DetectionCropper.recycle(result.frameBitmap);
            return;
        }

        LaundryCategory laundryCategory = LaundryCategory.fromModelLabel(result.label);
        if (result.label == null || laundryCategory == null) {
            listener.onDetectionCleared();
            resetLabelStreak();
            DetectionCropper.recycle(result.frameBitmap);
            return;
        }

        listener.onDetectionChanged(analyzedDetection);

        if (result.label.equals(lastDetectedClassLabel)) {
            stableLabelCount++;
        } else {
            lastDetectedClassLabel = result.label;
            stableLabelCount = 1;
        }

        if (stableLabelCount >= REQUIRED_STABLE_LABEL_COUNT) {
            detectionLocked = true;
            analysisEnabled = false;
            String colorType = LaundryColorAnalyzer.detectColorType(result.frameBitmap, result.normalizedBox);
            DetectionCrop crop = DetectionCropper.createCrop(result.frameBitmap, result.normalizedBox);
            listener.onStableDetection(new StableDetection(
                    analyzedDetection,
                    laundryCategory,
                    colorType,
                    crop));
        } else {
            DetectionCropper.recycle(result.frameBitmap);
        }
    }

    private void handleNoDetection() {
        if (!analysisEnabled || detectionLocked || !listener.isDetectionPageVisible()) {
            return;
        }
        listener.onDetectionCleared();
        resetLabelStreak();
    }

    private void resetLabelStreak() {
        lastDetectedClassLabel = null;
        stableLabelCount = 0;
    }

    private static String formatDisplayLabel(String label, ClassificationDetail classificationDetail) {
        if (label == null || classificationDetail == null) {
            return label;
        }
        return label + " -> " + classificationDetail.label;
    }

    private static String[] nonEmptyOrDefault(String[] items, String[] defaultItems) {
        return (items != null && items.length > 0 ? items : defaultItems).clone();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Ignore cleanup failures while releasing model resources.
        }
    }

    public interface Listener {
        boolean isDetectionPageVisible();

        void onDetectionChanged(AnalyzedDetection detection);

        void onDetectionCleared();

        void onStableDetection(StableDetection detection);
    }
}
