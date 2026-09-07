package yourscraft.jasdewstarfield.brnquest.author;

/** Identifies the server-side revision that must remain stable before a draft replaces its target. */
public enum DraftOrigin {
    ACTIVE,
    WORKSPACE,
    EMPTY,
    IMPORT,
    UNKNOWN
}
