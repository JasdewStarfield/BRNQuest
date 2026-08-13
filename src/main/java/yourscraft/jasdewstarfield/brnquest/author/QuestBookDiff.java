package yourscraft.jasdewstarfield.brnquest.author;

import java.util.List;

/** Revision-labelled structured preview suitable for commands or a future editor panel. */
public record QuestBookDiff(String fromRevision, String toRevision, List<SemanticDiffEntry> entries) {
    public QuestBookDiff { entries = List.copyOf(entries); }
    public boolean empty() { return entries.isEmpty(); }
}
