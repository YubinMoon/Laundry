package app.dku.embededapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LaundryRecordStore extends SQLiteOpenHelper {
    public static final String ALL_RECORDS_GROUP_KEY = "ALL_RECORDS";

    private static final String DATABASE_NAME = "laundry_records.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_RECORDS = "laundry_records";
    private static final String IMAGE_DIRECTORY = "detections";
    private static final int JPEG_QUALITY = 90;
    private static final String PREFERENCES_NAME = "laundry_records_preferences";
    private static final String KEY_DATABASE_RESET_DONE = "database_reset_done_v1";
    private static final String KEY_SINGLE_GROUP_DONE = "groups_single_status_done";

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
        resetExistingDatabaseIfNeeded();
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
            long rowId = insertRecord(relativeImagePath, record);
            setSingleGroupDone(false);
            return rowId;
        } catch (IOException exception) {
            deleteQuietly(imageFile);
            throw exception;
        } catch (RuntimeException exception) {
            deleteQuietly(imageFile);
            throw new IOException("Unable to save laundry record.", exception);
        }
    }

    public List<StoredRecord> getStoredRecords() {
        List<StoredRecord> records = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_RECORDS,
                new String[] {
                        COLUMN_ID,
                        COLUMN_IMAGE_PATH,
                        COLUMN_CATEGORY,
                        COLUMN_DETAIL_TYPE,
                        COLUMN_COLOR,
                        COLUMN_DETECTED_LABEL,
                        COLUMN_DETECTED_CONFIDENCE,
                        COLUMN_DETAIL_CONFIDENCE,
                        COLUMN_CREATED_AT
                },
                null,
                null,
                null,
                null,
                COLUMN_CREATED_AT + " DESC, " + COLUMN_ID + " DESC")) {
            while (cursor.moveToNext()) {
                Float detailConfidence = null;
                int detailConfidenceColumn = cursor.getColumnIndexOrThrow(COLUMN_DETAIL_CONFIDENCE);
                if (!cursor.isNull(detailConfidenceColumn)) {
                    detailConfidence = cursor.getFloat(detailConfidenceColumn);
                }
                LaundryRecord record = new LaundryRecord(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DETAIL_TYPE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COLOR)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DETECTED_LABEL)),
                        cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_DETECTED_CONFIDENCE)),
                        detailConfidence,
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
                records.add(new StoredRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                        record));
            }
        }
        return records;
    }

    public boolean deleteRecord(long recordId) {
        String imagePath = null;
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_RECORDS,
                new String[] {COLUMN_IMAGE_PATH},
                COLUMN_ID + " = ?",
                new String[] {String.valueOf(recordId)},
                null,
                null,
                null)) {
            if (cursor.moveToFirst()) {
                imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH));
            }
        }
        if (imagePath == null) {
            return false;
        }

        int deletedRows = getWritableDatabase().delete(
                TABLE_RECORDS,
                COLUMN_ID + " = ?",
                new String[] {String.valueOf(recordId)});
        if (deletedRows > 0) {
            deleteQuietly(new File(appContext.getFilesDir(), imagePath));
            if (getStoredRecords().isEmpty()) {
                setSingleGroupDone(false);
            }
            return true;
        }
        return false;
    }

    public File getImageFile(StoredRecord record) {
        return new File(appContext.getFilesDir(), record.imagePath);
    }

    public boolean isSingleGroupDone() {
        return preferences().getBoolean(KEY_SINGLE_GROUP_DONE, false);
    }

    public void setSingleGroupDone(boolean done) {
        preferences().edit().putBoolean(KEY_SINGLE_GROUP_DONE, done).apply();
    }

    private void resetExistingDatabaseIfNeeded() {
        SharedPreferences preferences = preferences();
        if (preferences.getBoolean(KEY_DATABASE_RESET_DONE, false)) {
            return;
        }

        File databaseFile = appContext.getDatabasePath(DATABASE_NAME);
        if (databaseFile.exists()) {
            appContext.deleteDatabase(DATABASE_NAME);
            deleteRecursively(new File(appContext.getFilesDir(), IMAGE_DIRECTORY));
        }
        preferences.edit()
                .putBoolean(KEY_DATABASE_RESET_DONE, true)
                .putBoolean(KEY_SINGLE_GROUP_DONE, false)
                .apply();
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

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        deleteQuietly(file);
    }

    private SharedPreferences preferences() {
        return appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class StoredRecord {
        public final long id;
        public final String imagePath;
        public final LaundryRecord record;

        public StoredRecord(long id, String imagePath, LaundryRecord record) {
            this.id = id;
            this.imagePath = imagePath;
            this.record = record;
        }
    }
}
