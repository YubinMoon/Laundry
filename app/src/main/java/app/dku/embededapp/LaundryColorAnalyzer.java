package app.dku.embededapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

final class LaundryColorAnalyzer {

    private static final int MAX_SAMPLE_COLUMNS = 64;
    private static final int MAX_SAMPLE_ROWS = 64;
    private static final float BOX_INSET_RATIO = 0.20f;
    private static final float WHITE_MIN_VALUE = 0.82f;
    private static final float WHITE_MAX_SATURATION = 0.22f;
    private static final float BLACK_MAX_VALUE = 0.18f;
    private static final float BRIGHT_MIN_VALUE = 0.55f;
    private static final float FAMILY_DOMINANT_RATIO = 0.55f;
    private static final float WHITE_IN_LIGHT_FAMILY_RATIO = 0.55f;
    private static final float BLACK_IN_DARK_FAMILY_RATIO = 0.35f;

    private static final int BUCKET_WHITE = 0;
    private static final int BUCKET_BLACK = 1;
    private static final int BUCKET_BRIGHT = 2;
    private static final int BUCKET_DARK = 3;
    private static final String[] COLOR_TYPE_LABELS = {"흰색", "검은색", "밝은색", "어두운색"};
    private static final String MIXED_COLOR_LABEL = "혼합";

    private LaundryColorAnalyzer() {
    }

    static String detectColorType(Bitmap bitmap, RectF normalizedBox) {
        if (bitmap == null || normalizedBox == null || bitmap.isRecycled()) {
            return MIXED_COLOR_LABEL;
        }

        RectF sampleRect = createSampleRect(bitmap, normalizedBox);
        int left = (int) Math.floor(sampleRect.left);
        int top = (int) Math.floor(sampleRect.top);
        int right = (int) Math.ceil(sampleRect.right);
        int bottom = (int) Math.ceil(sampleRect.bottom);
        if (right <= left || bottom <= top) {
            return MIXED_COLOR_LABEL;
        }

        int[] bucketCounts = countColorBuckets(bitmap, left, top, right, bottom);
        return chooseColorType(bucketCounts);
    }

    private static RectF createSampleRect(Bitmap bitmap, RectF normalizedBox) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float left = clamp(normalizedBox.left * width, 0f, width - 1f);
        float top = clamp(normalizedBox.top * height, 0f, height - 1f);
        float right = clamp(normalizedBox.right * width, left + 1f, width);
        float bottom = clamp(normalizedBox.bottom * height, top + 1f, height);

        float insetX = (right - left) * BOX_INSET_RATIO;
        float insetY = (bottom - top) * BOX_INSET_RATIO;
        if (right - left > insetX * 2f + 1f && bottom - top > insetY * 2f + 1f) {
            left += insetX;
            top += insetY;
            right -= insetX;
            bottom -= insetY;
        }
        return new RectF(left, top, right, bottom);
    }

    private static int[] countColorBuckets(
            Bitmap bitmap,
            int left,
            int top,
            int right,
            int bottom) {
        int sampleColumns = Math.min(MAX_SAMPLE_COLUMNS, right - left);
        int sampleRows = Math.min(MAX_SAMPLE_ROWS, bottom - top);
        int[] bucketCounts = new int[COLOR_TYPE_LABELS.length];
        float[] hsv = new float[3];

        for (int row = 0; row < sampleRows; row++) {
            int y = top + Math.min(bottom - top - 1, (row * (bottom - top)) / sampleRows);
            for (int column = 0; column < sampleColumns; column++) {
                int x = left + Math.min(right - left - 1, (column * (right - left)) / sampleColumns);
                Color.colorToHSV(bitmap.getPixel(x, y), hsv);
                bucketCounts[bucketFor(hsv[1], hsv[2])]++;
            }
        }
        return bucketCounts;
    }

    private static int bucketFor(float saturation, float value) {
        if (value <= BLACK_MAX_VALUE) {
            return BUCKET_BLACK;
        }
        if (value >= WHITE_MIN_VALUE && saturation <= WHITE_MAX_SATURATION) {
            return BUCKET_WHITE;
        }
        if (value >= BRIGHT_MIN_VALUE) {
            return BUCKET_BRIGHT;
        }
        return BUCKET_DARK;
    }

    private static String chooseColorType(int[] bucketCounts) {
        int whiteCount = bucketCounts[BUCKET_WHITE];
        int blackCount = bucketCounts[BUCKET_BLACK];
        int brightCount = bucketCounts[BUCKET_BRIGHT];
        int darkCount = bucketCounts[BUCKET_DARK];
        int total = whiteCount + blackCount + brightCount + darkCount;

        if (total == 0) {
            return MIXED_COLOR_LABEL;
        }

        int lightFamilyCount = whiteCount + brightCount;
        int darkFamilyCount = blackCount + darkCount;
        float lightFamilyRatio = lightFamilyCount / (float) total;
        float darkFamilyRatio = darkFamilyCount / (float) total;

        if (lightFamilyRatio >= FAMILY_DOMINANT_RATIO) {
            float whiteRatio = whiteCount / (float) lightFamilyCount;
            return whiteRatio >= WHITE_IN_LIGHT_FAMILY_RATIO ? COLOR_TYPE_LABELS[BUCKET_WHITE]
                    : COLOR_TYPE_LABELS[BUCKET_BRIGHT];
        }
        if (darkFamilyRatio >= FAMILY_DOMINANT_RATIO) {
            float blackRatio = blackCount / (float) darkFamilyCount;
            return blackRatio >= BLACK_IN_DARK_FAMILY_RATIO ? COLOR_TYPE_LABELS[BUCKET_BLACK]
                    : COLOR_TYPE_LABELS[BUCKET_DARK];
        }

        return MIXED_COLOR_LABEL;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
