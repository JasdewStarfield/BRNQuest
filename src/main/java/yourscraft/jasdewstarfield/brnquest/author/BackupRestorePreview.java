package yourscraft.jasdewstarfield.brnquest.author;

/** Non-mutating restore preview with the target concurrency token required by restore. */
public record BackupRestorePreview(BackupDescriptor backup, String currentRevision, boolean targetExists,
                                   boolean willReplace) {}
