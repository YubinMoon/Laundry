package app.dku.embededapp.detection;

import app.dku.embededapp.ml.LaundryDetector;

public final class AnalyzedDetection {
    public final LaundryDetector.DetectionResult detectionResult;
    public final String displayLabel;
    public final float displayConfidence;
    public final ClassificationDetail classificationDetail;

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
