package app.dku.embededapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class DetectionOverlayView extends View {

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF viewBox = new RectF();
    private final RectF labelBackground = new RectF();
    private RectF normalizedBox;
    private String labelText;
    private int frameWidth;
    private int frameHeight;

    public DetectionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int primaryColor = ContextCompat.getColor(context, R.color.laundry_primary);
        boxPaint.setColor(primaryColor);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dpToPx(3f));

        labelBackgroundPaint.setColor(primaryColor);
        labelBackgroundPaint.setStyle(Paint.Style.FILL);

        labelTextPaint.setColor(0xffffffff);
        labelTextPaint.setTextSize(spToPx(14f));
        labelTextPaint.setFakeBoldText(true);
    }

    void showDetection(
            String label,
            float confidence,
            RectF box,
            int frameWidth,
            int frameHeight) {
        this.normalizedBox = new RectF(box);
        this.labelText = label.replace('_', ' ') + " " + Math.round(confidence * 100f) + "%";
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        setVisibility(VISIBLE);
        invalidate();
    }

    void clearDetection() {
        normalizedBox = null;
        labelText = null;
        setVisibility(GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (normalizedBox == null || labelText == null || frameWidth <= 0 || frameHeight <= 0) {
            return;
        }

        updateViewBox();
        canvas.drawRect(viewBox, boxPaint);
        drawLabel(canvas);
    }

    private void updateViewBox() {
        float scale = Math.max(getWidth() / (float) frameWidth, getHeight() / (float) frameHeight);
        float scaledFrameWidth = frameWidth * scale;
        float scaledFrameHeight = frameHeight * scale;
        float offsetX = (getWidth() - scaledFrameWidth) / 2f;
        float offsetY = (getHeight() - scaledFrameHeight) / 2f;

        viewBox.set(
                offsetX + normalizedBox.left * scaledFrameWidth,
                offsetY + normalizedBox.top * scaledFrameHeight,
                offsetX + normalizedBox.right * scaledFrameWidth,
                offsetY + normalizedBox.bottom * scaledFrameHeight);
        viewBox.intersect(0f, 0f, getWidth(), getHeight());
    }

    private void drawLabel(Canvas canvas) {
        float paddingHorizontal = dpToPx(8f);
        float paddingVertical = dpToPx(5f);
        Paint.FontMetrics fontMetrics = labelTextPaint.getFontMetrics();
        float labelWidth = labelTextPaint.measureText(labelText) + paddingHorizontal * 2f;
        float labelHeight = (fontMetrics.descent - fontMetrics.ascent) + paddingVertical * 2f;
        float labelLeft = Math.min(viewBox.left, Math.max(0f, getWidth() - labelWidth));
        float labelTop = Math.max(0f, viewBox.top - labelHeight);

        labelBackground.set(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight);
        canvas.drawRoundRect(labelBackground, dpToPx(6f), dpToPx(6f), labelBackgroundPaint);
        canvas.drawText(
                labelText,
                labelLeft + paddingHorizontal,
                labelTop + paddingVertical - fontMetrics.ascent,
                labelTextPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}
