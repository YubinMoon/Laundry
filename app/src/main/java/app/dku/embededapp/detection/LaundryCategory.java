package app.dku.embededapp.detection;

import app.dku.embededapp.data.LaundryRecord;

public enum LaundryCategory {
    TOP(
            "Tops",
            LaundryRecord.CATEGORY_TOP,
            new String[] {
                    "short_sleeved_shirt",
                    "long_sleeved_shirt",
                    "outerwear",
                    "vest",
                    "sling"
            }),
    BOTTOM(
            "Bottoms",
            LaundryRecord.CATEGORY_BOTTOM,
            new String[] {
                    "shorts",
                    "trousers",
                    "skirt"
            }),
    TOWEL(
            "Towels",
            LaundryRecord.CATEGORY_TOWEL,
            new String[] {"towel"}),
    SOCK(
            "Socks",
            LaundryRecord.CATEGORY_SOCK,
            new String[] {"sock"});

    public static final String COLOR_WHITE = "White";
    public static final String COLOR_BLACK = "Black";
    public static final String COLOR_BRIGHT = "Light";
    public static final String COLOR_DARK = "Dark";
    public static final String COLOR_MIXED = "Mixed";

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

    public static String displayColor(String color) {
        if (color == null) {
            return null;
        }
        switch (color) {
            case "\ud770\uc0c9":
                return COLOR_WHITE;
            case "\uac80\uc740\uc0c9":
                return COLOR_BLACK;
            case "\ubc1d\uc740\uc0c9":
                return COLOR_BRIGHT;
            case "\uc5b4\ub450\uc6b4\uc0c9":
                return COLOR_DARK;
            case "\ud63c\ud569":
                return COLOR_MIXED;
            default:
                return color;
        }
    }
}
