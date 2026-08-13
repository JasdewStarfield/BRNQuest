package yourscraft.jasdewstarfield.brnquest.api;

/** Compatibility level promised for a public BRNQuest API surface. */
public enum ApiStability {
    /** Backward-compatible within the documented API major version. */
    STABLE,
    /** Available for integration trials, but may still change before stabilization. */
    EXPERIMENTAL,
    /** Implementation detail that external integrations must not depend on. */
    INTERNAL
}
