package yourscraft.jasdewstarfield.brnquest.author;

/** Current in-memory draft and the revision most recently saved to disk. */
public record DraftSessionState(DraftSnapshot snapshot, String savedRevision) {
    public boolean dirty() {
        return !snapshot.draftRevision().equals(savedRevision);
    }
}
