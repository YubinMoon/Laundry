package app.dku.embededapp.testdata;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import app.dku.embededapp.data.LaundryRecord;
import app.dku.embededapp.detection.LaundryCategory;

final class TestLaundrySeedTemplate {
    static final int SAMPLE_COUNT = 10;

    private TestLaundrySeedTemplate() {
    }

    static List<Sample> samples(long now) {
        return Arrays.asList(
                sample(
                        LaundryRecord.CATEGORY_TOP,
                        "T-shirts",
                        LaundryCategory.COLOR_WHITE,
                        "top",
                        0.96f,
                        0.88f,
                        daysAgo(now, 8),
                        Color.rgb(248, 248, 244),
                        Color.rgb(52, 152, 219)),
                sample(
                        LaundryRecord.CATEGORY_TOP,
                        "Shirts",
                        LaundryCategory.COLOR_BLACK,
                        "top",
                        0.93f,
                        0.84f,
                        daysAgo(now, 5),
                        Color.rgb(24, 28, 35),
                        Color.rgb(241, 196, 15)),
                sample(
                        LaundryRecord.CATEGORY_TOP,
                        "Activewear",
                        LaundryCategory.COLOR_BRIGHT,
                        "top",
                        0.94f,
                        0.86f,
                        daysAgo(now, 6),
                        Color.rgb(225, 241, 255),
                        Color.rgb(22, 124, 117)),
                sample(
                        LaundryRecord.CATEGORY_TOP,
                        "Sweaters",
                        LaundryCategory.COLOR_DARK,
                        "top",
                        0.90f,
                        0.80f,
                        daysAgo(now, 3),
                        Color.rgb(58, 52, 64),
                        Color.rgb(204, 174, 98)),
                sample(
                        LaundryRecord.CATEGORY_BOTTOM,
                        "Jeans",
                        LaundryCategory.COLOR_BRIGHT,
                        "bottom",
                        0.95f,
                        0.87f,
                        daysAgo(now, 7),
                        Color.rgb(172, 203, 230),
                        Color.rgb(31, 93, 142)),
                sample(
                        LaundryRecord.CATEGORY_BOTTOM,
                        "Jeans",
                        LaundryCategory.COLOR_BLACK,
                        "bottom",
                        0.92f,
                        0.83f,
                        daysAgo(now, 4),
                        Color.rgb(19, 31, 46),
                        Color.rgb(108, 142, 191)),
                sample(
                        LaundryRecord.CATEGORY_BOTTOM,
                        "Skirts",
                        LaundryCategory.COLOR_MIXED,
                        "bottom",
                        0.91f,
                        0.81f,
                        daysAgo(now, 2),
                        Color.rgb(247, 230, 230),
                        Color.rgb(157, 75, 130)),
                sample(
                        LaundryRecord.CATEGORY_BOTTOM,
                        "Chinos",
                        LaundryCategory.COLOR_MIXED,
                        "bottom",
                        0.89f,
                        0.78f,
                        daysAgo(now, 9),
                        Color.rgb(212, 196, 166),
                        Color.rgb(68, 117, 90)),
                sample(
                        LaundryRecord.CATEGORY_TOWEL,
                        null,
                        LaundryCategory.COLOR_WHITE,
                        "towel",
                        0.97f,
                        null,
                        daysAgo(now, 10),
                        Color.rgb(252, 252, 247),
                        Color.rgb(255, 182, 107)),
                sample(
                        LaundryRecord.CATEGORY_SOCK,
                        null,
                        LaundryCategory.COLOR_DARK,
                        "socks",
                        0.90f,
                        null,
                        daysAgo(now, 1),
                        Color.rgb(47, 51, 58),
                        Color.rgb(225, 232, 240)));
    }

    private static Sample sample(
            String category,
            String detailType,
            String color,
            String detectedLabel,
            float detectedConfidence,
            Float detailConfidence,
            long createdAt,
            int backgroundColor,
            int accentColor) {
        return new Sample(
                new LaundryRecord(
                        category,
                        detailType,
                        color,
                        detectedLabel,
                        detectedConfidence,
                        detailConfidence,
                        createdAt),
                backgroundColor,
                accentColor);
    }

    private static long daysAgo(long now, long days) {
        return now - TimeUnit.DAYS.toMillis(days);
    }

    static final class Sample {
        final LaundryRecord record;
        private final int backgroundColor;
        private final int accentColor;

        Sample(LaundryRecord record, int backgroundColor, int accentColor) {
            this.record = record;
            this.backgroundColor = backgroundColor;
            this.accentColor = accentColor;
        }

        Bitmap createBitmap() {
            Bitmap bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            paint.setColor(backgroundColor);
            canvas.drawRect(0, 0, 320, 320, paint);

            paint.setColor(accentColor);
            canvas.drawRoundRect(new RectF(54, 54, 266, 266), 28, 28, paint);

            paint.setColor(Color.argb(72, 255, 255, 255));
            canvas.drawRect(86, 88, 234, 124, paint);
            canvas.drawRect(86, 152, 234, 188, paint);
            canvas.drawRect(86, 216, 234, 238, paint);

            return bitmap;
        }
    }
}
