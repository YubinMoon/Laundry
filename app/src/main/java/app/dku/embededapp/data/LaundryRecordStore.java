package app.dku.embededapp.data;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LaundryRecordStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "laundry_records.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_GROUPS = "laundry_groups";
    private static final String TABLE_RECORDS = "laundry_records";
    private static final String IMAGE_DIRECTORY = "detections";
    private static final int JPEG_QUALITY = 90;
    private static final String PREFERENCES_NAME = "laundry_records_preferences";
    private static final String KEY_DATABASE_RESET_DONE = "database_reset_done_v1";

    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_GROUP_ID = "group_id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_DONE = "is_done";
    private static final String COLUMN_IMAGE_PATH = "image_path";
    private static final String COLUMN_CATEGORY = "category";
    private static final String COLUMN_DETAIL_TYPE = "detail_type";
    private static final String COLUMN_COLOR = "color";
    private static final String COLUMN_DETECTED_LABEL = "detected_label";
    private static final String COLUMN_DETECTED_CONFIDENCE = "detected_confidence";
    private static final String COLUMN_DETAIL_CONFIDENCE = "detail_confidence";
    private static final String COLUMN_CREATED_AT = "created_at";

    private static final String GROUP_LIGHT_GENERAL = "Light General Clothes";
    private static final String GROUP_DARK_GENERAL = "Dark General Clothes";
    private static final String GROUP_ACTIVEWEAR = "Activewear";
    private static final String GROUP_DELICATES = "Delicates";
    private static final String GROUP_LIGHT_DENIM = "Light Denim";
    private static final String GROUP_DARK_DENIM = "Dark Denim";
    private static final String GROUP_TOWELS = "Towels";
    private static final String GROUP_MIXED_GENERAL = "Mixed General Clothes";

    private final Context appContext;

    public LaundryRecordStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        appContext = context.getApplicationContext();
        resetExistingDatabaseIfNeeded();
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE_GROUPS + " ("
                        + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COLUMN_NAME + " TEXT NOT NULL, "
                        + COLUMN_DONE + " INTEGER NOT NULL DEFAULT 0, "
                        + COLUMN_CREATED_AT + " INTEGER NOT NULL"
                        + ")");
        database.execSQL(
                "CREATE TABLE " + TABLE_RECORDS + " ("
                        + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + COLUMN_GROUP_ID + " INTEGER NOT NULL, "
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
        // Version 1 is recreated by uninstalling the app before installing this schema.
    }

    public long saveRecord(Bitmap cropBitmap, LaundryRecord record) throws IOException {
        validate(cropBitmap, record);

        File imageFile = createImageFile(record.createdAt);
        String relativeImagePath = IMAGE_DIRECTORY + "/" + imageFile.getName();
        try {
            writeBitmap(cropBitmap, imageFile);
            SQLiteDatabase database = getWritableDatabase();
            database.beginTransaction();
            try {
                String groupName = groupNameFor(record);
                long groupId = findOpenGroupId(database, groupName);
                if (groupId < 0L) {
                    groupId = insertGroup(database, groupName, record.createdAt);
                }
                long rowId = insertRecord(database, relativeImagePath, groupId, record);
                database.setTransactionSuccessful();
                return rowId;
            } finally {
                database.endTransaction();
            }
        } catch (IOException exception) {
            deleteQuietly(imageFile);
            throw exception;
        } catch (RuntimeException exception) {
            deleteQuietly(imageFile);
            throw new IOException("Unable to save laundry record.", exception);
        }
    }

    public List<StoredGroup> getGroups() {
        List<StoredGroup> groups = new ArrayList<>();
        SQLiteDatabase database = getReadableDatabase();
        loadGroups(database, false, groups);
        loadGroups(database, true, groups);
        return groups;
    }

    public List<StoredRecord> getStoredRecords() {
        List<StoredRecord> records = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLE_RECORDS,
                recordColumns(),
                null,
                null,
                null,
                null,
                COLUMN_CREATED_AT + " DESC, " + COLUMN_ID + " DESC")) {
            while (cursor.moveToNext()) {
                records.add(readStoredRecord(cursor));
            }
        }
        return records;
    }

    public boolean markGroupDone(long groupId) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_DONE, 1);
        return getWritableDatabase().update(
                TABLE_GROUPS,
                values,
                COLUMN_ID + " = ?",
                new String[] {String.valueOf(groupId)}) > 0;
    }

    public boolean deleteRecord(long recordId) {
        SQLiteDatabase database = getWritableDatabase();
        String imagePath = null;
        long groupId = -1L;
        try (Cursor cursor = database.query(
                TABLE_RECORDS,
                new String[] {COLUMN_IMAGE_PATH, COLUMN_GROUP_ID},
                COLUMN_ID + " = ?",
                new String[] {String.valueOf(recordId)},
                null,
                null,
                null)) {
            if (cursor.moveToFirst()) {
                imagePath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH));
                groupId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GROUP_ID));
            }
        }
        if (imagePath == null || groupId < 0L || isGroupDone(database, groupId)) {
            return false;
        }

        boolean deleted = false;
        database.beginTransaction();
        try {
            int deletedRows = database.delete(
                    TABLE_RECORDS,
                    COLUMN_ID + " = ?",
                    new String[] {String.valueOf(recordId)});
            if (deletedRows > 0) {
                if (countRecordsForGroup(database, groupId) == 0) {
                    database.delete(
                            TABLE_GROUPS,
                            COLUMN_ID + " = ?",
                            new String[] {String.valueOf(groupId)});
                }
                deleted = true;
                database.setTransactionSuccessful();
            }
        } finally {
            database.endTransaction();
        }

        if (deleted) {
            deleteQuietly(new File(appContext.getFilesDir(), imagePath));
        }
        return deleted;
    }

    public File getImageFile(StoredRecord record) {
        return new File(appContext.getFilesDir(), record.imagePath);
    }

    private void loadGroups(SQLiteDatabase database, boolean done, List<StoredGroup> groups) {
        try (Cursor cursor = database.query(
                TABLE_GROUPS,
                new String[] {
                        COLUMN_ID,
                        COLUMN_NAME,
                        COLUMN_DONE,
                        COLUMN_CREATED_AT
                },
                COLUMN_DONE + " = ?",
                new String[] {done ? "1" : "0"},
                null,
                null,
                null)) {
            while (cursor.moveToNext()) {
                long groupId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
                List<StoredRecord> records = getStoredRecordsForGroup(database, groupId);
                if (!records.isEmpty()) {
                    groups.add(new StoredGroup(
                            groupId,
                            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
                            cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DONE)) != 0,
                            cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                            records));
                }
            }
        }
    }

    private List<StoredRecord> getStoredRecordsForGroup(SQLiteDatabase database, long groupId) {
        List<StoredRecord> records = new ArrayList<>();
        try (Cursor cursor = database.query(
                TABLE_RECORDS,
                recordColumns(),
                COLUMN_GROUP_ID + " = ?",
                new String[] {String.valueOf(groupId)},
                null,
                null,
                COLUMN_CREATED_AT + " DESC, " + COLUMN_ID + " DESC")) {
            while (cursor.moveToNext()) {
                records.add(readStoredRecord(cursor));
            }
        }
        return records;
    }

    private StoredRecord readStoredRecord(Cursor cursor) {
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
        return new StoredRecord(
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_GROUP_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                record);
    }

    private String[] recordColumns() {
        return new String[] {
                COLUMN_ID,
                COLUMN_GROUP_ID,
                COLUMN_IMAGE_PATH,
                COLUMN_CATEGORY,
                COLUMN_DETAIL_TYPE,
                COLUMN_COLOR,
                COLUMN_DETECTED_LABEL,
                COLUMN_DETECTED_CONFIDENCE,
                COLUMN_DETAIL_CONFIDENCE,
                COLUMN_CREATED_AT
        };
    }

    private boolean isGroupDone(SQLiteDatabase database, long groupId) {
        try (Cursor cursor = database.query(
                TABLE_GROUPS,
                new String[] {COLUMN_DONE},
                COLUMN_ID + " = ?",
                new String[] {String.valueOf(groupId)},
                null,
                null,
                null)) {
            return cursor.moveToFirst()
                    && cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DONE)) != 0;
        }
    }

    private int countRecordsForGroup(SQLiteDatabase database, long groupId) {
        try (Cursor cursor = database.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_RECORDS + " WHERE " + COLUMN_GROUP_ID + " = ?",
                new String[] {String.valueOf(groupId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private long findOpenGroupId(SQLiteDatabase database, String groupName) {
        try (Cursor cursor = database.query(
                TABLE_GROUPS,
                new String[] {COLUMN_ID},
                COLUMN_NAME + " = ? AND " + COLUMN_DONE + " = 0",
                new String[] {groupName},
                null,
                null,
                null)) {
            return cursor.moveToFirst()
                    ? cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                    : -1L;
        }
    }

    private long insertGroup(SQLiteDatabase database, String groupName, long createdAt) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, groupName);
        values.put(COLUMN_DONE, 0);
        values.put(COLUMN_CREATED_AT, createdAt);
        return database.insertOrThrow(TABLE_GROUPS, null, values);
    }

    private long insertRecord(
            SQLiteDatabase database,
            String relativeImagePath,
            long groupId,
            LaundryRecord record) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_GROUP_ID, groupId);
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
        return database.insertOrThrow(TABLE_RECORDS, null, values);
    }

    private String groupNameFor(LaundryRecord record) {
        if (LaundryRecord.CATEGORY_TOWEL.equals(record.category)
                || matches(record.detectedLabel, "towel")) {
            return GROUP_TOWELS;
        }
        if (matches(record.detailType, "Activewear")) {
            return GROUP_ACTIVEWEAR;
        }
        if (matches(record.detailType, "Sweaters")
                || matches(record.detailType, "Skirts")
                || matches(record.detectedLabel, "skirt")) {
            return GROUP_DELICATES;
        }
        if (matches(record.detailType, "Denim") || matches(record.detailType, "Jeans")) {
            return isLightColor(record.color) ? GROUP_LIGHT_DENIM : GROUP_DARK_DENIM;
        }
        return generalGroupNameForColor(record.color);
    }

    private String generalGroupNameForColor(String color) {
        if (isLightColor(color)) {
            return GROUP_LIGHT_GENERAL;
        }
        if (isDarkColor(color)) {
            return GROUP_DARK_GENERAL;
        }
        return GROUP_MIXED_GENERAL;
    }

    private boolean isLightColor(String color) {
        return matches(color, "White") || matches(color, "Light");
    }

    private boolean isDarkColor(String color) {
        return matches(color, "Black") || matches(color, "Dark");
    }

    private boolean matches(String value, String expected) {
        return value != null && value.trim().equalsIgnoreCase(expected);
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

    public static final class StoredGroup {
        public final long id;
        public final String name;
        public final boolean done;
        public final long createdAt;
        public final List<StoredRecord> records;

        public StoredGroup(long id, String name, boolean done, long createdAt, List<StoredRecord> records) {
            this.id = id;
            this.name = name;
            this.done = done;
            this.createdAt = createdAt;
            this.records = records;
        }
    }

    public static final class StoredRecord {
        public final long id;
        public final long groupId;
        public final String imagePath;
        public final LaundryRecord record;

        public StoredRecord(long id, long groupId, String imagePath, LaundryRecord record) {
            this.id = id;
            this.groupId = groupId;
            this.imagePath = imagePath;
            this.record = record;
        }
    }
}
