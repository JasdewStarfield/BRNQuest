package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

/** Immutable author draft snapshot, always distinct from the active runtime pointer. */
public record DraftSnapshot(QuestBookDefinition book, DraftOrigin origin, String baseRevision, String draftRevision) {
    public DraftSnapshot {
        if (book == null) throw new IllegalArgumentException("book is required");
        if (origin == null) origin = DraftOrigin.UNKNOWN;
        baseRevision = baseRevision == null ? "" : baseRevision;
        String calculated = QuestBookSnapshot.of(book).revision();
        if (!calculated.equals(draftRevision)) throw new IllegalArgumentException("Draft revision does not match content");
    }

    public static DraftSnapshot of(QuestBookDefinition book, String baseRevision) {
        return from(book, DraftOrigin.UNKNOWN, baseRevision);
    }

    public static DraftSnapshot from(QuestBookDefinition book, DraftOrigin origin, String baseRevision) {
        return new DraftSnapshot(book, origin, baseRevision, QuestBookSnapshot.of(book).revision());
    }

    DraftManifest manifest() {
        return new DraftManifest(DraftManifest.CURRENT_FORMAT, book.id(), origin, baseRevision, draftRevision);
    }
}
