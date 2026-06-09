package app.dku.embededapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class LaundryRecordStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "laundry_records.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_RECORDS = "laundry_records";
    private static final String IMAGE_DIRECTORY = "detections";
    private static final int JPEG_QUALITY = 90;

    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_IMAGE_PATH = "image_path";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_DETAIL_TYPE = "detail_type";
    private static final String COLUMN_COLOR = "color";
    private static final String COLUMN_DETECTED_LABEL = "detected_label";
    private static final String COLUMN_DETECTED_CONFIDENCE = "detected_confidence";
    private static final String COLUMN_DETAIL_CONFIDENCE = "detail_confidence";
    private static final String COLUMN_CREATED_AT = "created_at";

    private final Context appContext;

    public LaundryRecordStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE_RECORDS + " ("
                        + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COLUMN_IMAGE_PATH + " TEXT NOT NULL, "
                        + COLUMN_CATEGORY + " TEXT NOT NULL, "
                        + COLUMN_DETAIL_TYPE + " TEXT, "
                        + COLUMN_COLOR + " TEXT NOT NULL, "
                        + COLUMN_DETECTED_LABEL + " TEXT, "
                        + COLUMN_DETECTED_CONFIDENCE + " REAL NOT NULL, "
                        + COLUMN_DETAIL_CONFIDENCE + " REAL, "
                        + COLUMN_CREATED_AT + " INTEGER NOT NULL"
                        + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        // Version 1 is the initial schema.
    }

    public long saveRecord(Bitmap cropBitmap, LaundryRecord record) throws IOException {
        validate(cropBitmap, record);

        File imageFile = createImageFile(record.createdAt);
        String relativeImagePath = IMAGE_DIRECTORY + "/" + imageFile.getName();
        try {
            writeBitmap(cropBitmap, imageFile);
            return insertRecord(relativeImagePath, record);
        } catch (IOException exception) {
            deleteQuietly(imageFile);
            throw exception;
        } catch (RuntimeException exception) {
            deleteQuietly(imageFile);
            throw new IOException("Unable to save laundry record.", exception);
        }
    }

    private void validate(Bitmap cropBitmap, LaundryRecord record) throws IOException {
        if (cropBitmap == null || cropBitmap.isRecycled()) {
            throw new IOException("Crop image is unavailable.");
        }
        if (record == null || isEmpty(record.category) || isEmpty(record.color)) {
            throw new IOException("Laundry record is incomplete.");
        }
    }

    private File createImageFile(long createdAt) throws IOException {
        File directory = new File(appContext.getFilesDir(), IMAGE_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create detection image directory.");
        }
        return new File(directory, createdAt + "_" + UUID.randomUUID() + ".jpg");
    }

    private void writeBitmap(Bitmap cropBitmap, File imageFile) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            boolean compressed = cropBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream);
            outputStream.flush();
            if (!compressed) {
                throw new IOException("Unable to encode crop image.");
            }
        }
    }

    private long insertRecord(String relativeImagePath, LaundryRecord record) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_IMAGE_PATH, relativeImagePath);
        values.put(COLUMN_CATEGORY, record.category);
        values.put(COLUMN_DETAIL_TYPE, record.detailType);
        values.put(COLUMN_COLOR, record.color);
        values.put(COLUMN_DETECTED_LABEL, record.detectedLabel);
        values.put(COLUMN_DETECTED_CONFIDENCE, record.detectedConfidence);
        if (record.detailConfidence == null) {
            values.putNull(COLUMN_DETAIL_CONFIDENCE);
        } else {
            values.put(COLUMN_DETAIL_CONFIDENCE, record.detailConfidence);
        }
        values.put(COLUMN_CREATED_AT, record.createdAt);
        return getWritableDatabase().insertOrThrow(TABLE_RECORDS, null, values);
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
