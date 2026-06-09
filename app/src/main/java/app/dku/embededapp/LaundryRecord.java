package app.dku.embededapp;

final class LaundryRecord {
    static final String CATEGORY_TOP = "TOP";
    static final String CATEGORY_BOTTOM = "BOTTOM";
    static final String CATEGORY_TOWEL = "TOWEL";
    static final String CATEGORY_SOCK = "SOCK";

    final String category;
    final String detailType;
    final String color;
    final String detectedLabel;
    final float detectedConfidence;
    final Float detailConfidence;
    final long createdAt;

    LaundryRecord(
            String category,
            String detailType,
            String color,
            String detectedLabel,
            float detectedConfidence,
            Float detailConfidence,
            long createdAt) {
        this.category = category;
        this.detailType = detailType;
        this.color = color;
        this.detectedLabel = detectedLabel;
        this.detectedConfidence = detectedConfidence;
        this.detailConfidence = detailConfidence;
        this.createdAt = createdAt;
    }
}
