package app.dku.embededapp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;

import androidx.camera.core.ImageProxy;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class LaundryDetector implements AutoCloseable {

    private static final String MODEL_ASSET = "model.tflite";
    private static final String LABEL_ASSET = "label.yaml";

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int inputWidth;
    private final int inputHeight;
    private final int inputChannels;
    private final int detectionCount;
    private final int detectionAttributes;
    private final ByteBuffer inputBuffer;
    private final float[][][] outputBuffer;
    private final int[] pixels;

    LaundryDetector(Context context) throws IOException {
        interpreter = new Interpreter(loadModel(context, MODEL_ASSET));
        labels = loadLabels(context);

        int[] inputShape = interpreter.getInputTensor(0).shape();
        if (inputShape.length != 4 || inputShape[0] != 1 || inputShape[3] != 3) {
            throw new IllegalStateException("Unsupported model input shape");
        }
        inputHeight = inputShape[1];
        inputWidth = inputShape[2];
        inputChannels = inputShape[3];

        int[] outputShape = interpreter.getOutputTensor(0).shape();
        if (outputShape.length != 3 || outputShape[0] != 1 || outputShape[2] < 6) {
            throw new IllegalStateException("Unsupported model output shape");
        }
        detectionCount = outputShape[1];
        detectionAttributes = outputShape[2];

        inputBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels * Float.BYTES);
        inputBuffer.order(ByteOrder.nativeOrder());
        outputBuffer = new float[1][detectionCount][detectionAttributes];
        pixels = new int[inputWidth * inputHeight];
    }

    DetectionResult detect(ImageProxy imageProxy, float confidenceThreshold) {
        Bitmap frameBitmap = imageProxyToBitmap(imageProxy);
        Bitmap inputBitmap = Bitmap.createScaledBitmap(frameBitmap, inputWidth, inputHeight, true);
        fillInputBuffer(inputBitmap);

        interpreter.run(inputBuffer, outputBuffer);

        if (inputBitmap != frameBitmap) {
            inputBitmap.recycle();
        }

        int bestClassIndex = -1;
        float bestConfidence = 0f;
        RectF bestBox = null;
        for (int index = 0; index < detectionCount; index++) {
            float confidence = outputBuffer[0][index][4];
            int classIndex = Math.round(outputBuffer[0][index][5]);
            if (confidence > bestConfidence && classIndex >= 0 && classIndex < labels.size()) {
                bestConfidence = confidence;
                bestClassIndex = classIndex;
                bestBox = readNormalizedBox(outputBuffer[0][index]);
            }
        }

        if (bestClassIndex < 0 || bestConfidence < confidenceThreshold || bestBox == null) {
            frameBitmap.recycle();
            return new DetectionResult(null, bestConfidence, null, null, 0, 0);
        }
        return new DetectionResult(
                labels.get(bestClassIndex),
                bestConfidence,
                bestBox,
                frameBitmap,
                frameBitmap.getWidth(),
                frameBitmap.getHeight());
    }

    @Override
    public void close() {
        interpreter.close();
    }

    private void fillInputBuffer(Bitmap bitmap) {
        bitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight);
        inputBuffer.rewind();
        for (int pixel : pixels) {
            inputBuffer.putFloat(((pixel >> 16) & 0xff) / 255f);
            inputBuffer.putFloat(((pixel >> 8) & 0xff) / 255f);
            inputBuffer.putFloat((pixel & 0xff) / 255f);
        }
    }

    private RectF readNormalizedBox(float[] detection) {
        float left = detection[0];
        float top = detection[1];
        float right = detection[2];
        float bottom = detection[3];

        if (Math.max(Math.max(left, right), Math.max(top, bottom)) > 1.5f) {
            left /= inputWidth;
            right /= inputWidth;
            top /= inputHeight;
            bottom /= inputHeight;
        }

        return new RectF(
                clamp01(Math.min(left, right)),
                clamp01(Math.min(top, bottom)),
                clamp01(Math.max(left, right)),
                clamp01(Math.max(top, bottom)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        byte[] nv21 = yuv420888ToNv21(imageProxy);
        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.getWidth(),
                imageProxy.getHeight(),
                null);
        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                90,
                jpegStream);
        byte[] jpegBytes = jpegStream.toByteArray();
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        if (rotationDegrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        Bitmap rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true);
        bitmap.recycle();
        return rotatedBitmap;
    }

    private static byte[] yuv420888ToNv21(ImageProxy imageProxy) {
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        int ySize = width * height;
        byte[] nv21 = new byte[ySize + (ySize / 2)];

        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        copyYPlane(planes[0], width, height, nv21);
        copyChromaPlanes(planes[1], planes[2], width, height, nv21, ySize);
        return nv21;
    }

    private static void copyYPlane(
            ImageProxy.PlaneProxy plane,
            int width,
            int height,
            byte[] output) {
        ByteBuffer buffer = plane.getBuffer().duplicate();
        buffer.rewind();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        int outputOffset = 0;
        for (int row = 0; row < height; row++) {
            int rowOffset = row * rowStride;
            for (int col = 0; col < width; col++) {
                output[outputOffset++] = buffer.get(rowOffset + col * pixelStride);
            }
        }
    }

    private static void copyChromaPlanes(
            ImageProxy.PlaneProxy uPlane,
            ImageProxy.PlaneProxy vPlane,
            int width,
            int height,
            byte[] output,
            int outputOffset) {
        ByteBuffer uBuffer = uPlane.getBuffer().duplicate();
        ByteBuffer vBuffer = vPlane.getBuffer().duplicate();
        uBuffer.rewind();
        vBuffer.rewind();
        int uRowStride = uPlane.getRowStride();
        int vRowStride = vPlane.getRowStride();
        int uPixelStride = uPlane.getPixelStride();
        int vPixelStride = vPlane.getPixelStride();
        int chromaWidth = width / 2;
        int chromaHeight = height / 2;

        for (int row = 0; row < chromaHeight; row++) {
            for (int col = 0; col < chromaWidth; col++) {
                output[outputOffset++] = vBuffer.get(row * vRowStride + col * vPixelStride);
                output[outputOffset++] = uBuffer.get(row * uRowStride + col * uPixelStride);
            }
        }
    }

    private static MappedByteBuffer loadModel(Context context, String assetName) throws IOException {
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(assetName);
             FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {
            return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.getStartOffset(),
                    descriptor.getDeclaredLength());
        }
    }

    private static List<String> loadLabels(Context context) throws IOException {
        List<String> loadedLabels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(LABEL_ASSET),
                StandardCharsets.UTF_8))) {
            boolean readingNames = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if ("names:".equals(line.trim())) {
                    readingNames = true;
                    continue;
                }
                if (!readingNames) {
                    continue;
                }
                if (!line.startsWith("  ")) {
                    break;
                }
                String trimmedLine = line.trim();
                int separator = trimmedLine.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                int index = Integer.parseInt(trimmedLine.substring(0, separator).trim());
                String label = trimmedLine.substring(separator + 1).trim();
                while (loadedLabels.size() <= index) {
                    loadedLabels.add("");
                }
                loadedLabels.set(index, stripQuotes(label));
            }
        }
        return loadedLabels;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    static final class DetectionResult {
        final String label;
        final float confidence;
        final RectF normalizedBox;
        final Bitmap frameBitmap;
        final int frameWidth;
        final int frameHeight;

        DetectionResult(
                String label,
                float confidence,
                RectF normalizedBox,
                Bitmap frameBitmap,
                int frameWidth,
                int frameHeight) {
            this.label = label;
            this.confidence = confidence;
            this.normalizedBox = normalizedBox;
            this.frameBitmap = frameBitmap;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }
    }
}
