package yourscraft.jasdewstarfield.brnquest.author;

/** Successful restore and the safety backup made from the overwritten target. */
public record BackupRestoreResult(BackupDescriptor restored, String previousRevision, String currentRevision,
                                  java.nio.file.Path overwrittenBackup) {}
