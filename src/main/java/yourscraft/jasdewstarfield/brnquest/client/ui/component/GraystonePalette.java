package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Shared neutral graystone colors. Error, warning, completion and tracking colors retain their meaning. */
public final class GraystonePalette {
    private GraystonePalette() {}
    public static final int PANEL = 0xFF30332E;
    public static final int ROW = 0xFF383B35;
    public static final int HOVER = 0xFF454940;
    public static final int SELECTED = 0xFF68634A;
    public static final int INSET = 0xFF20231E;
    // Solid cool stone keeps inline-code runs legible against both row and inset document surfaces.
    public static final int INLINE_CODE = 0xFF505A60;
    public static final int TEXT = 0xFFE4E5DF;
    public static final int SECONDARY = 0xFFB7BBAE;
    public static final int MUTED = 0xFF979C8D;
    public static final int DISABLED = 0xFF7D8375;
    public static final int BACKDROP = 0x70171915;
    public static final int CANVAS = 0xC8171915;
    public static final int DRAWER = 0xD0252822;
    // The prototype's light stone header is a distinct material, not a drawer background.
    public static final int HEADER = 0xFF999991;
    public static final int HEADER_LIGHT = 0xFFE0DED2;
    public static final int HEADER_DARK = 0xFF45463F;
    public static final int HEADER_TEXT = 0xFF20251E;
    public static final int GRID = 0x1C555B4F;
    public static final int EDGE = 0xC08B917F;
    public static final int TRACK = 0x66383B35;
    public static final int THUMB = 0xFF929783;
    // Structural boundaries use a dark seam and a lighter lip, independent of semantic status colors.
    public static final int NAVIGATION = 0xFF272B25;
    public static final int FOOTER = 0xFF252822;
    public static final int SEAM = 0xFF11140F;
    public static final int LIP = 0xFF626957;
    public static final int NODE_RIM = 0xFF939B83;
    public static final int ACCENT = 0xFFE4D29A;
}
