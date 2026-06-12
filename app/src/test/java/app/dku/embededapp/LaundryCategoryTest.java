package app.dku.embededapp;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import app.dku.embededapp.data.LaundryRecord;
import app.dku.embededapp.detection.LaundryCategory;

import org.junit.Test;

public class LaundryCategoryTest {
    @Test
    public void fromModelLabelMapsCategoryModelLabelsToStorageCategory() {
        assertSame(LaundryCategory.TOP, LaundryCategory.fromModelLabel("top"));
        assertEquals(LaundryRecord.CATEGORY_TOP, LaundryCategory.TOP.recordCode);

        assertSame(LaundryCategory.BOTTOM, LaundryCategory.fromModelLabel("bottom"));
        assertEquals(LaundryRecord.CATEGORY_BOTTOM, LaundryCategory.BOTTOM.recordCode);

        assertSame(LaundryCategory.TOWEL, LaundryCategory.fromModelLabel("towel"));
        assertEquals(LaundryRecord.CATEGORY_TOWEL, LaundryCategory.TOWEL.recordCode);

        assertSame(LaundryCategory.SOCK, LaundryCategory.fromModelLabel("socks"));
        assertEquals(LaundryRecord.CATEGORY_SOCK, LaundryCategory.SOCK.recordCode);
    }

    @Test
    public void unknownOrEmptyModelLabelsDoNotMatch() {
        assertNull(LaundryCategory.fromModelLabel(null));
        assertNull(LaundryCategory.fromModelLabel(""));
        assertNull(LaundryCategory.fromModelLabel("short_sleeved_shirt"));
        assertNull(LaundryCategory.fromModelLabel("dress"));
        assertFalse(LaundryCategory.TOP.matchesModelLabel(null));
    }

    @Test
    public void displayNamesAndDetailSupportStayAlignedWithUiOptions() {
        assertArrayEquals(
                new String[] {"Tops", "Bottoms", "Towels", "Socks"},
                LaundryCategory.displayNames());

        assertSame(LaundryCategory.TOP, LaundryCategory.fromDisplayName("Tops"));
        assertSame(LaundryCategory.BOTTOM, LaundryCategory.fromDisplayName("Bottoms"));
        assertNull(LaundryCategory.fromDisplayName(null));
        assertNull(LaundryCategory.fromDisplayName("Shoes"));

        assertTrue(LaundryCategory.TOP.supportsDetailTypes());
        assertTrue(LaundryCategory.BOTTOM.supportsDetailTypes());
        assertFalse(LaundryCategory.TOWEL.supportsDetailTypes());
        assertFalse(LaundryCategory.SOCK.supportsDetailTypes());
    }

    @Test
    public void displayColorNormalizesKoreanColorNamesAndKeepsUnknownValues() {
        assertEquals("White", LaundryCategory.displayColor("\ud770\uc0c9"));
        assertEquals("Black", LaundryCategory.displayColor("\uac80\uc740\uc0c9"));
        assertEquals("Light", LaundryCategory.displayColor("\ubc1d\uc740\uc0c9"));
        assertEquals("Dark", LaundryCategory.displayColor("\uc5b4\ub450\uc6b4\uc0c9"));
        assertEquals("Mixed", LaundryCategory.displayColor("\ud63c\ud569"));
        assertEquals("Blue", LaundryCategory.displayColor("Blue"));
        assertNull(LaundryCategory.displayColor(null));
    }
}
