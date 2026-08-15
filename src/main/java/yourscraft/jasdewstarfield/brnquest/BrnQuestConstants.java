package yourscraft.jasdewstarfield.brnquest;

/** Frozen compatibility constants shared by persistence, import, and networking. */
public final class BrnQuestConstants {
    public static final int DATA_SCHEMA = 1;
    public static final int PROGRESS_SCHEMA = 1;
    public static final int REPORT_SCHEMA = 1;
    public static final String NETWORK_PROTOCOL = "2";
    public static final int MAX_BOOK_CHUNK_BYTES = 256 * 1024;
    public static final int MAX_BOOK_BYTES = 8 * 1024 * 1024;
    public static final int MAX_PROGRESS_BYTES = 1024 * 1024;
    public static final int MAX_EDITOR_METADATA_BYTES = 256 * 1024;
    public static final int MAX_EDITOR_CATALOG_ENTRIES = 256;
    public static final int MAX_QUESTS = 4096;

    private BrnQuestConstants() {
    }
}
