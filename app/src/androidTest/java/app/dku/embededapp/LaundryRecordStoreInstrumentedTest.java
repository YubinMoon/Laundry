package app.dku.embededapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import app.dku.embededapp.data.LaundryRecord;
import app.dku.embededapp.data.LaundryRecordStore;

@RunWith(AndroidJUnit4.class)
public class LaundryRecordStoreInstrumentedTest {
    private static final String DATABASE_NAME = "laundry_records.db";
    private static final String IMAGE_DIRECTORY = "detections";
    private static final String TABLE_RECORDS = "laundry_records";
    private static final String PREFERENCES_NAME = "laundry_records_preferences";

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clearStoredRecords();
    }

    @After
    public void tearDown() {
        clearStoredRecords();
    }

    @Test
    public void saveRecordWritesDatabaseRowAndImageFile() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);

        LaundryRecord record = createRecord(123456789L);

        LaundryRecordStore store = new LaundryRecordStore(context);
        long rowId = store.saveRecord(bitmap, record);

        SQLiteDatabase database = store.getReadableDatabase();
        try (Cursor cursor = database.query(
                TABLE_RECORDS,
                null,
                "_id = ?",
                new String[] {String.valueOf(rowId)},
                null,
                null,
                null)) {
            assertTrue(cursor.moveToFirst());
            String imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path"));
            assertEquals(LaundryRecord.CATEGORY_TOP, cursor.getString(cursor.getColumnIndexOrThrow("category")));
            assertEquals("T-shirts", cursor.getString(cursor.getColumnIndexOrThrow("detail_type")));
            assertEquals("WHITE", cursor.getString(cursor.getColumnIndexOrThrow("color")));
            assertEquals(
                    "short_sleeved_shirt",
                    cursor.getString(cursor.getColumnIndexOrThrow("detected_label")));
            assertEquals(0.91f, cursor.getFloat(cursor.getColumnIndexOrThrow("detected_confidence")), 0.001f);
            assertEquals(0.82f, cursor.getFloat(cursor.getColumnIndexOrThrow("detail_confidence")), 0.001f);
            assertEquals(123456789L, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));

            File imageFile = new File(context.getFilesDir(), imagePath);
            assertTrue(imageFile.exists());
            assertTrue(imageFile.length() > 0L);
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void storeInitializationDeletesExistingDatabaseAndImages() throws Exception {
        SQLiteDatabase legacyDatabase = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        legacyDatabase.execSQL("CREATE TABLE legacy_records (_id INTEGER PRIMARY KEY)");
        legacyDatabase.close();

        File imageDirectory = new File(context.getFilesDir(), IMAGE_DIRECTORY);
        assertTrue(imageDirectory.mkdirs() || imageDirectory.exists());
        File legacyImage = new File(imageDirectory, "legacy.jpg");
        assertTrue(legacyImage.createNewFile());

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            assertTrue(store.getStoredRecords().isEmpty());
            assertFalse(legacyImage.exists());
        } finally {
            store.close();
        }
    }

    @Test
    public void getStoredRecordsReturnsLatestFirst() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, createRecord(1000L));
            store.saveRecord(bitmap, createRecord(3000L));
            store.saveRecord(bitmap, createRecord(2000L));

            List<LaundryRecordStore.StoredRecord> records = store.getStoredRecords();

            assertEquals(3, records.size());
            assertEquals(3000L, records.get(0).record.createdAt);
            assertEquals(2000L, records.get(1).record.createdAt);
            assertEquals(1000L, records.get(2).record.createdAt);
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void deleteRecordRemovesDatabaseRowAndImageFile() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.GREEN);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, createRecord(2000L));
            LaundryRecordStore.StoredRecord storedRecord = store.getStoredRecords().get(0);
            File imageFile = store.getImageFile(storedRecord);
            assertTrue(imageFile.exists());

            assertTrue(store.deleteRecord(storedRecord.id));

            assertTrue(store.getStoredRecords().isEmpty());
            assertFalse(imageFile.exists());
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void singleGroupStatusDefaultsToPendingAndPersistsDone() {
        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            assertFalse(store.isSingleGroupDone());
            store.setSingleGroupDone(true);
        } finally {
            store.close();
        }

        LaundryRecordStore reopenedStore = new LaundryRecordStore(context);
        try {
            assertTrue(reopenedStore.isSingleGroupDone());
        } finally {
            reopenedStore.close();
        }
    }

    private void clearStoredRecords() {
        context.deleteDatabase(DATABASE_NAME);
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        deleteRecursively(new File(context.getFilesDir(), IMAGE_DIRECTORY));
    }

    private LaundryRecord createRecord(long createdAt) {
        return new LaundryRecord(
                LaundryRecord.CATEGORY_TOP,
                "T-shirts",
                "WHITE",
                "short_sleeved_shirt",
                0.91f,
                0.82f,
                createdAt);
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
        file.delete();
    }
}
