package app.dku.embededapp.detection;

import app.dku.embededapp.ml.LaundryDetector;

public final class StableDetection {
    public final LaundryDetector.DetectionResult detectionResult;
    public final LaundryCategory category;
    public final String colorType;
    public final DetectionCrop crop;
    public final ClassificationDetail classificationDetail;

    StableDetection(
            AnalyzedDetection analyzedDetection,
            LaundryCategory category,
            String colorType,
            DetectionCrop crop) {
        this.detectionResult = analyzedDetection.detectionResult;
        this.category = category;
        this.colorType = colorType;
        this.crop = crop;
        this.classificationDetail = analyzedDetection.classificationDetail;
    }
}
