package app.dku.embededapp.detection;

public final class ClassificationDetail {
    public final String label;
    public final float confidence;

    public ClassificationDetail(String label, float confidence) {
        this.label = label;
        this.confidence = confidence;
    }
}
