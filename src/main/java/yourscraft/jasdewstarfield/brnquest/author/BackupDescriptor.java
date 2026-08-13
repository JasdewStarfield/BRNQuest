package yourscraft.jasdewstarfield.brnquest.author;

/** Safe backup listing entry returned to commands and future clients. */
public record BackupDescriptor(BackupKind kind, String id, String revision, long fileCount) {}
