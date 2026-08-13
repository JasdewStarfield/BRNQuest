package yourscraft.jasdewstarfield.brnquest.author;

/** Snapshot of every revision participating in an author save decision. */
public record RevisionVector(String activeRevision, String baseRevision, String draftRevision,
                             String diskDraftRevision, String workspaceRevision) {
    public RevisionVector {
        activeRevision = normalize(activeRevision);
        baseRevision = normalize(baseRevision);
        draftRevision = normalize(draftRevision);
        diskDraftRevision = normalize(diskDraftRevision);
        workspaceRevision = normalize(workspaceRevision);
    }

    private static String normalize(String value) { return value == null ? "" : value; }
}
