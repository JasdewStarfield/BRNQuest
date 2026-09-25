package yourscraft.jasdewstarfield.brnquest.author;

/** One restorable saved-draft version shown in the in-game history. */
public record DraftBackupVersion(String id, String revision, String title, long savedAtEpochMillis) {}
