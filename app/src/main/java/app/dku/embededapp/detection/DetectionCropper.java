package app.dku.embededapp.detection;

import android.graphics.Bitmap;
import android.graphics.RectF;

final class DetectionCropper {
    private DetectionCropper() {
    }

    static DetectionCrop createCrop(Bitmap frameBitmap, RectF normalizedBox) {
        if (frameBitmap == null || normalizedBox == null) {
            return null;
        }

        int imageWidth = frameBitmap.getWidth();
        int imageHeight = frameBitmap.getHeight();
        float boxLeft = normalizedBox.left * imageWidth;
        float boxTop = normalizedBox.top * imageHeight;
        float boxRight = normalizedBox.right * imageWidth;
        float boxBottom = normalizedBox.bottom * imageHeight;
        float boxWidth = Math.max(1f, boxRight - boxLeft);
        float boxHeight = Math.max(1f, boxBottom - boxTop);
        int side = Math.round(Math.min(Math.max(boxWidth, boxHeight), Math.min(imageWidth, imageHeight)));
        side = Math.max(1, side);

        float centerX = (boxLeft + boxRight) / 2f;
        float centerY = (boxTop + boxBottom) / 2f;
        int left = Math.round(clamp(centerX - side / 2f, 0f, imageWidth - side));
        int top = Math.round(clamp(centerY - side / 2f, 0f, imageHeight - side));
        if (left + side > imageWidth) {
            left = imageWidth - side;
        }
        if (top + side > imageHeight) {
            top = imageHeight - side;
        }

        Bitmap cropBitmap = Bitmap.createBitmap(frameBitmap, left, top, side, side);
        RectF cropBox = new RectF(
                left / (float) imageWidth,
                top / (float) imageHeight,
                (left + side) / (float) imageWidth,
                (top + side) / (float) imageHeight);
        return new DetectionCrop(cropBitmap, cropBox);
    }

    static void recycle(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
