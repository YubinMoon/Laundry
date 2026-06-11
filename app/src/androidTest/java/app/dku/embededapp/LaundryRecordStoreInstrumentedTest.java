package app.dku.embededapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
    private static final String TABLE_GROUPS = "laundry_groups";
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
    public void saveRecordWritesDatabaseRowImageFileAndGroup() throws Exception {
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
            long groupId = cursor.getLong(cursor.getColumnIndexOrThrow("group_id"));
            String imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path"));
            assertEquals(LaundryRecord.CATEGORY_TOP, cursor.getString(cursor.getColumnIndexOrThrow("category")));
            assertEquals("T-shirts", cursor.getString(cursor.getColumnIndexOrThrow("detail_type")));
            assertEquals("White", cursor.getString(cursor.getColumnIndexOrThrow("color")));
            assertEquals(
                    "short_sleeved_shirt",
                    cursor.getString(cursor.getColumnIndexOrThrow("detected_label")));
            assertEquals(0.91f, cursor.getFloat(cursor.getColumnIndexOrThrow("detected_confidence")), 0.001f);
            assertEquals(0.82f, cursor.getFloat(cursor.getColumnIndexOrThrow("detail_confidence")), 0.001f);
            assertEquals(123456789L, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));

            assertGroup(database, groupId, "Light General Clothes", false);

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
            assertTrue(store.getGroups().isEmpty());
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
    public void saveRecordReusesOpenGroupWithSameName() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, createRecord(1000L));
            store.saveRecord(bitmap, createRecord(
                    LaundryRecord.CATEGORY_TOP,
                    "Shirts",
                    "Light",
                    "long_sleeved_shirt",
                    2000L));

            List<LaundryRecordStore.StoredGroup> groups = store.getGroups();

            assertEquals(1, groups.size());
            assertEquals("Light General Clothes", groups.get(0).name);
            assertFalse(groups.get(0).done);
            assertEquals(2, groups.get(0).records.size());
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void saveRecordCreatesNewGroupWhenSameNameGroupIsDone() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, createRecord(1000L));
            LaundryRecordStore.StoredGroup doneGroup = store.getGroups().get(0);
            assertTrue(store.markGroupDone(doneGroup.id));

            store.saveRecord(bitmap, createRecord(2000L));

            List<LaundryRecordStore.StoredGroup> groups = store.getGroups();

            assertEquals(2, groups.size());
            assertEquals("Light General Clothes", groups.get(0).name);
            assertEquals("Light General Clothes", groups.get(1).name);
            assertFalse(groups.get(0).done);
            assertTrue(groups.get(1).done);
            assertNotEquals(groups.get(0).id, groups.get(1).id);
            assertEquals(1, groups.get(0).records.size());
            assertEquals(1, groups.get(1).records.size());
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void saveRecordUsesConfirmedFieldsWhenDetectedLabelWasTowel() throws Exception {
        assertSavedGroupName(createRecord(
                LaundryRecord.CATEGORY_TOP,
                "T-shirts",
                "White",
                "towel",
                1000L), "Light General Clothes");
    }

    @Test
    public void saveRecordUsesConfirmedFieldsWhenDetectedLabelWasSkirt() throws Exception {
        assertSavedGroupName(createRecord(
                LaundryRecord.CATEGORY_TOP,
                "T-shirts",
                "Black",
                "skirt",
                1000L), "Dark General Clothes");
    }

    @Test
    public void saveRecordKeepsConfirmedTowelsInTowelsGroup() throws Exception {
        assertSavedGroupName(createRecord(
                LaundryRecord.CATEGORY_TOWEL,
                null,
                "White",
                "short_sleeved_shirt",
                1000L), "Towels");
    }

    @Test
    public void saveRecordKeepsConfirmedSkirtsInDelicatesGroup() throws Exception {
        assertSavedGroupName(createRecord(
                LaundryRecord.CATEGORY_BOTTOM,
                "Skirts",
                "Black",
                "trousers",
                1000L), "Delicates");
    }

    private void assertSavedGroupName(LaundryRecord record, String expectedGroupName) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, record);

            List<LaundryRecordStore.StoredGroup> groups = store.getGroups();

            assertEquals(1, groups.size());
            assertEquals(expectedGroupName, groups.get(0).name);
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void deleteRecordRemovesDatabaseRowImageFileAndEmptyOpenGroup() throws Exception {
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
            assertTrue(store.getGroups().isEmpty());
            assertFalse(imageFile.exists());
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void deleteRecordFailsForDoneGroupAndKeepsImage() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, createRecord(2000L));
            LaundryRecordStore.StoredGroup group = store.getGroups().get(0);
            assertTrue(store.markGroupDone(group.id));

            LaundryRecordStore.StoredRecord storedRecord = store.getStoredRecords().get(0);
            File imageFile = store.getImageFile(storedRecord);
            assertTrue(imageFile.exists());

            assertFalse(store.deleteRecord(storedRecord.id));

            assertEquals(1, store.getStoredRecords().size());
            assertEquals(1, store.getGroups().size());
            assertTrue(store.getGroups().get(0).done);
            assertTrue(imageFile.exists());
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void getGroupsReturnsOpenGroupsBeforeDoneGroups() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.GRAY);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            store.saveRecord(bitmap, createRecord(1000L));
            assertTrue(store.markGroupDone(store.getGroups().get(0).id));
            store.saveRecord(bitmap, createRecord(
                    LaundryRecord.CATEGORY_TOP,
                    "T-shirts",
                    "Black",
                    "short_sleeved_shirt",
                    2000L));

            List<LaundryRecordStore.StoredGroup> groups = store.getGroups();

            assertEquals(2, groups.size());
            assertEquals("Dark General Clothes", groups.get(0).name);
            assertFalse(groups.get(0).done);
            assertEquals("Light General Clothes", groups.get(1).name);
            assertTrue(groups.get(1).done);
        } finally {
            store.close();
            bitmap.recycle();
        }
    }

    @Test
    public void groupStatusPersistsDone() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.YELLOW);

        LaundryRecordStore store = new LaundryRecordStore(context);
        long groupId;
        try {
            store.saveRecord(bitmap, createRecord(1000L));
            groupId = store.getGroups().get(0).id;
            assertTrue(store.markGroupDone(groupId));
        } finally {
            store.close();
            bitmap.recycle();
        }

        LaundryRecordStore reopenedStore = new LaundryRecordStore(context);
        try {
            List<LaundryRecordStore.StoredGroup> groups = reopenedStore.getGroups();

            assertEquals(1, groups.size());
            assertEquals(groupId, groups.get(0).id);
            assertTrue(groups.get(0).done);
        } finally {
            reopenedStore.close();
        }
    }

    private void assertGroup(SQLiteDatabase database, long groupId, String name, boolean done) {
        try (Cursor cursor = database.query(
                TABLE_GROUPS,
                null,
                "_id = ?",
                new String[] {String.valueOf(groupId)},
                null,
                null,
                null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(name, cursor.getString(cursor.getColumnIndexOrThrow("name")));
            assertEquals(done ? 1 : 0, cursor.getInt(cursor.getColumnIndexOrThrow("is_done")));
        }
    }

    private void clearStoredRecords() {
        context.deleteDatabase(DATABASE_NAME);
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        deleteRecursively(new File(context.getFilesDir(), IMAGE_DIRECTORY));
    }

    private LaundryRecord createRecord(long createdAt) {
        return createRecord(
                LaundryRecord.CATEGORY_TOP,
                "T-shirts",
                "White",
                "short_sleeved_shirt",
                createdAt);
    }

    private LaundryRecord createRecord(
            String category,
            String detailType,
            String color,
            String detectedLabel,
            long createdAt) {
        return new LaundryRecord(
                category,
                detailType,
                color,
                detectedLabel,
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
