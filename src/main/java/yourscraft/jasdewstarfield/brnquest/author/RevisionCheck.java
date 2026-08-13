package yourscraft.jasdewstarfield.brnquest.author;

import java.util.List;

/** Four-way revision snapshot and every conflict discovered from it. */
public record RevisionCheck(RevisionVector revisions, List<RevisionConflict> conflicts) {
    public RevisionCheck { conflicts = List.copyOf(conflicts); }
    public boolean hasConflicts() { return !conflicts.isEmpty(); }
}
