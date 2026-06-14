package app.dku.embededapp.testdata;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.io.IOException;

import app.dku.embededapp.data.LaundryRecordStore;

public final class TestLaundrySeeder {
    static final String PREFERENCES_NAME = "test_laundry_seed_preferences";
    static final String KEY_SEEDED = "test_laundry_seeded_v1";

    private TestLaundrySeeder() {
    }

    public static void seedIfNeeded(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        if (preferences.getBoolean(KEY_SEEDED, false)) {
            return;
        }

        LaundryRecordStore store = new LaundryRecordStore(appContext);
        try {
            for (TestLaundrySeedTemplate.Sample sample
                    : TestLaundrySeedTemplate.samples(System.currentTimeMillis())) {
                Bitmap bitmap = sample.createBitmap();
                try {
                    store.saveRecord(bitmap, sample.record);
                } finally {
                    bitmap.recycle();
                }
            }
            preferences.edit().putBoolean(KEY_SEEDED, true).apply();
        } finally {
            store.close();
        }
    }
}
