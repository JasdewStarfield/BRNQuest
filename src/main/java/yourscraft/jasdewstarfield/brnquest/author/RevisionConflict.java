package yourscraft.jasdewstarfield.brnquest.author;

/** Stable machine-readable explanation for one author revision mismatch. */
public record RevisionConflict(String code, String expectedRevision, String actualRevision, String message) {}
