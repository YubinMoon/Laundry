package app.dku.embededapp;

import static org.junit.Assert.assertEquals;
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

import app.dku.embededapp.data.LaundryRecord;
import app.dku.embededapp.data.LaundryRecordStore;

@RunWith(AndroidJUnit4.class)
public class LaundryRecordStoreInstrumentedTest {
    private static final String DATABASE_NAME = "laundry_records.db";
    private static final String IMAGE_DIRECTORY = "detections";
    private static final String TABLE_RECORDS = "laundry_records";

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

        LaundryRecord record = new LaundryRecord(
                LaundryRecord.CATEGORY_TOP,
                "T-shirts",
                "WHITE",
                "short_sleeved_shirt",
                0.91f,
                0.82f,
                123456789L);

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

    private void clearStoredRecords() {
        context.deleteDatabase(DATABASE_NAME);
        deleteRecursively(new File(context.getFilesDir(), IMAGE_DIRECTORY));
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
