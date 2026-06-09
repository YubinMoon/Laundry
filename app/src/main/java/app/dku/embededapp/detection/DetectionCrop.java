package app.dku.embededapp.detection;

import android.graphics.Bitmap;
import android.graphics.RectF;

public final class DetectionCrop {
    public final Bitmap bitmap;
    public final RectF normalizedBox;

    DetectionCrop(Bitmap bitmap, RectF normalizedBox) {
        this.bitmap = bitmap;
        this.normalizedBox = normalizedBox;
    }
}
