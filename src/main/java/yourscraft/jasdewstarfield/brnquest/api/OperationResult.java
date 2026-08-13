package yourscraft.jasdewstarfield.brnquest.api;

import java.util.Objects;

/** Structured write-operation result used by integrations and command adapters. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record OperationResult(OperationStatus status, String code, String message) {
    public OperationResult {
        Objects.requireNonNull(status, "status");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }

    /** Preserves the existing boolean convenience contract for current integrations. */
    public boolean success() {
        return status.success();
    }

    public boolean changed() {
        return status == OperationStatus.SUCCESS;
    }

    public static OperationResult success(String message) {
        return new OperationResult(OperationStatus.SUCCESS, "OK", message);
    }

    public static OperationResult noChange(String code, String message) {
        return new OperationResult(OperationStatus.NO_CHANGE, code, message);
    }

    public static OperationResult rejected(String code, String message) {
        return new OperationResult(OperationStatus.REJECTED, code, message);
    }

    public static OperationResult invalid(String code, String message) {
        return new OperationResult(OperationStatus.INVALID_REQUEST, code, message);
    }

    public static OperationResult notReady(String code, String message) {
        return new OperationResult(OperationStatus.NOT_READY, code, message);
    }

    public static OperationResult forbidden(String code, String message) {
        return new OperationResult(OperationStatus.FORBIDDEN, code, message);
    }

    public static OperationResult staleRevision(String code, String message) {
        return new OperationResult(OperationStatus.STALE_REVISION, code, message);
    }

    /**
     * Compatibility factory for the stage-2 engine. New public entry points should use a
     * specific category so callers do not need to interpret free-form messages.
     */
    public static OperationResult failure(String code, String message) {
        return switch (code) {
            case "INVALID_AMOUNT", "INVALID_ID", "INVALID_PLAYER", "WRONG_THREAD" -> invalid(code, message);
            case "NO_BOOK", "PLAYER_OFFLINE" -> notReady(code, message);
            case "FORBIDDEN" -> forbidden(code, message);
            case "STALE_REVISION" -> staleRevision(code, message);
            default -> rejected(code, message);
        };
    }
}
