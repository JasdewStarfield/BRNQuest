package yourscraft.jasdewstarfield.brnquest.data.text;

import java.util.Locale;

/** Canonical vanilla legacy palette shared by plain parsing, Markdown parsing, and author UI. */
public enum MinecraftTextColor {
    BLACK('0', 0x000000), DARK_BLUE('1', 0x0000AA), DARK_GREEN('2', 0x00AA00), DARK_AQUA('3', 0x00AAAA),
    DARK_RED('4', 0xAA0000), DARK_PURPLE('5', 0xAA00AA), GOLD('6', 0xFFAA00), GRAY('7', 0xAAAAAA),
    DARK_GRAY('8', 0x555555), BLUE('9', 0x5555FF), GREEN('a', 0x55FF55), AQUA('b', 0x55FFFF),
    RED('c', 0xFF5555), LIGHT_PURPLE('d', 0xFF55FF), YELLOW('e', 0xFFFF55), WHITE('f', 0xFFFFFF);

    private final char legacyCode;
    private final int rgb;

    MinecraftTextColor(char legacyCode, int rgb) {
        this.legacyCode = legacyCode;
        this.rgb = rgb;
    }

    public char legacyCode() { return legacyCode; }
    public int rgb() { return rgb; }
    public String serializedName() { return name().toLowerCase(Locale.ROOT); }

    public static MinecraftTextColor byLegacyCode(char code) {
        char normalized = Character.toLowerCase(code);
        for (MinecraftTextColor color : values()) if (color.legacyCode == normalized) return color;
        return null;
    }

    public static MinecraftTextColor byName(String name) {
        if (name == null) return null;
        String normalized = name.toLowerCase(Locale.ROOT);
        for (MinecraftTextColor color : values()) if (color.serializedName().equals(normalized)) return color;
        return null;
    }
}
