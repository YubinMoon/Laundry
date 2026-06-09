package app.dku.embededapp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
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

final class BottomClassifier implements AutoCloseable {

    private static final String MODEL_ASSET = "bottom-model.tflite";
    private static final String LABEL_ASSET = "bottom-label.yaml";

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int inputWidth;
    private final int inputHeight;
    private final int inputChannels;
    private final int outputElementCount;
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final int[] pixels;

    BottomClassifier(Context context) throws IOException {
        interpreter = new Interpreter(loadModel(context, MODEL_ASSET));
        labels = loadLabels(context, LABEL_ASSET);

        int[] inputShape = interpreter.getInputTensor(0).shape();
        if (inputShape.length != 4 || inputShape[0] != 1 || inputShape[3] != 3) {
            throw new IllegalStateException("Unsupported bottom model input shape");
        }
        inputHeight = inputShape[1];
        inputWidth = inputShape[2];
        inputChannels = inputShape[3];

        outputElementCount = interpreter.getOutputTensor(0).numElements();
        if (outputElementCount <= 0) {
            throw new IllegalStateException("Unsupported bottom model output shape");
        }

        inputBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels * Float.BYTES);
        inputBuffer.order(ByteOrder.nativeOrder());
        outputBuffer = ByteBuffer.allocateDirect(outputElementCount * Float.BYTES);
        outputBuffer.order(ByteOrder.nativeOrder());
        pixels = new int[inputWidth * inputHeight];
    }

    Result classify(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
        fillInputBuffer(resizedBitmap);
        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle();
        }

        outputBuffer.rewind();
        interpreter.run(inputBuffer, outputBuffer);
        outputBuffer.rewind();

        int resultCount = Math.min(outputElementCount, labels.size());
        int bestIndex = -1;
        float bestConfidence = Float.NEGATIVE_INFINITY;
        for (int index = 0; index < resultCount; index++) {
            float confidence = outputBuffer.getFloat();
            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestIndex = index;
            }
        }

        if (bestIndex < 0) {
            return null;
        }
        return new Result(labels.get(bestIndex), bestConfidence);
    }

    @Override
    public void close() {
        interpreter.close();
    }

    String[] getLabels() {
        return labels.toArray(new String[0]);
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

    private static List<String> loadLabels(Context context, String assetName) throws IOException {
        List<String> loadedLabels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(assetName),
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

    static final class Result {
        final String label;
        final float confidence;

        Result(String label, float confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }
}
