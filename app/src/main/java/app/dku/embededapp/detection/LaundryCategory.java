package app.dku.embededapp.detection;

import app.dku.embededapp.data.LaundryRecord;

public enum LaundryCategory {
    TOP(
            "\uc0c1\uc758",
            LaundryRecord.CATEGORY_TOP,
            new String[] {
                    "short_sleeved_shirt",
                    "long_sleeved_shirt",
                    "outerwear",
                    "vest",
                    "sling"
            }),
    BOTTOM(
            "\ud558\uc758",
            LaundryRecord.CATEGORY_BOTTOM,
            new String[] {
                    "shorts",
                    "trousers",
                    "skirt"
            }),
    TOWEL(
            "\uc218\uac74",
            LaundryRecord.CATEGORY_TOWEL,
            new String[] {"towel"}),
    SOCK(
            "\uc591\ub9d0",
            LaundryRecord.CATEGORY_SOCK,
            new String[] {"sock"});

    public static final String COLOR_WHITE = "\ud770\uc0c9";
    public static final String COLOR_BLACK = "\uac80\uc740\uc0c9";
    public static final String COLOR_BRIGHT = "\ubc1d\uc740\uc0c9";
    public static final String COLOR_DARK = "\uc5b4\ub450\uc6b4\uc0c9";
    public static final String COLOR_MIXED = "\ud63c\ud569";

    public final String displayName;
    public final String recordCode;

    private final String[] modelLabels;

    LaundryCategory(String displayName, String recordCode, String[] modelLabels) {
        this.displayName = displayName;
        this.recordCode = recordCode;
        this.modelLabels = modelLabels;
    }

    public boolean matchesModelLabel(String label) {
        if (label == null) {
            return false;
        }
        for (String modelLabel : modelLabels) {
            if (modelLabel.equals(label)) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsDetailTypes() {
        return this == TOP || this == BOTTOM;
    }

    public static LaundryCategory fromModelLabel(String label) {
        for (LaundryCategory category : values()) {
            if (category.matchesModelLabel(label)) {
                return category;
            }
        }
        return null;
    }

    public static LaundryCategory fromDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        for (LaundryCategory category : values()) {
            if (category.displayName.equals(displayName)) {
                return category;
            }
        }
        return null;
    }

    public static String[] displayNames() {
        LaundryCategory[] categories = values();
        String[] displayNames = new String[categories.length];
        for (int index = 0; index < categories.length; index++) {
            displayNames[index] = categories[index].displayName;
        }
        return displayNames;
    }

    public static String[] colorTypes() {
        return new String[] {
                COLOR_WHITE,
                COLOR_BLACK,
                COLOR_BRIGHT,
                COLOR_DARK,
                COLOR_MIXED
        };
    }
}
