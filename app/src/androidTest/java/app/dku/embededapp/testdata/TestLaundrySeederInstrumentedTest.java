package app.dku.embededapp.testdata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import app.dku.embededapp.data.LaundryRecordStore;

@RunWith(AndroidJUnit4.class)
public class TestLaundrySeederInstrumentedTest {
    private static final String DATABASE_NAME = "laundry_records.db";
    private static final String IMAGE_DIRECTORY = "detections";
    private static final String RECORD_PREFERENCES_NAME = "laundry_records_preferences";

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
    public void seedIfNeededCreatesRecordsGroupsAndImagesOnce() throws Exception {
        TestLaundrySeeder.seedIfNeeded(context);
        TestLaundrySeeder.seedIfNeeded(context);

        LaundryRecordStore store = new LaundryRecordStore(context);
        try {
            List<LaundryRecordStore.StoredRecord> records = store.getStoredRecords();
            List<LaundryRecordStore.StoredGroup> groups = store.getGroups();

            assertEquals(TestLaundrySeedTemplate.SAMPLE_COUNT, records.size());
            assertEquals(8, groups.size());
            assertTrue(containsGroup(groups, "Light General Clothes"));
            assertTrue(containsGroup(groups, "Dark General Clothes"));
            assertTrue(containsGroup(groups, "Activewear"));
            assertTrue(containsGroup(groups, "Delicates"));
            assertTrue(containsGroup(groups, "Light Denim"));
            assertTrue(containsGroup(groups, "Dark Denim"));
            assertTrue(containsGroup(groups, "Mixed General Clothes"));
            assertTrue(containsGroup(groups, "Towels"));

            for (LaundryRecordStore.StoredRecord record : records) {
                File imageFile = store.getImageFile(record);
                assertTrue(imageFile.exists());
                assertTrue(imageFile.length() > 0L);
            }
        } finally {
            store.close();
        }
    }

    private boolean containsGroup(List<LaundryRecordStore.StoredGroup> groups, String name) {
        for (LaundryRecordStore.StoredGroup group : groups) {
            if (name.equals(group.name)) {
                return true;
            }
        }
        return false;
    }

    private void clearStoredRecords() {
        context.deleteDatabase(DATABASE_NAME);
        context.getSharedPreferences(RECORD_PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TestLaundrySeeder.PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit();
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
        assertFalse(file.exists() && !file.delete());
    }
}
