package app.dku.embededapp.data;

public final class LaundryRecord {
    public static final String CATEGORY_TOP = "TOP";
    public static final String CATEGORY_BOTTOM = "BOTTOM";
    public static final String CATEGORY_TOWEL = "TOWEL";
    public static final String CATEGORY_SOCK = "SOCK";

    public final String category;
    public final String detailType;
    public final String color;
    public final String detectedLabel;
    public final float detectedConfidence;
    public final Float detailConfidence;
    public final long createdAt;

    public LaundryRecord(
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
