package yourscraft.jasdewstarfield.brnquest.api;

/** Stable high-level category for a server-authoritative API operation result. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public enum OperationStatus {
    SUCCESS(true),
    NO_CHANGE(true),
    REJECTED(false),
    INVALID_REQUEST(false),
    NOT_READY(false),
    FORBIDDEN(false),
    STALE_REVISION(false);

    private final boolean success;

    OperationStatus(boolean success) {
        this.success = success;
    }

    /** A successful no-op is true so retransmitted idempotent requests remain compatible. */
    public boolean success() {
        return success;
    }
}
