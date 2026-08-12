package yourscraft.jasdewstarfield.brnquest.api;

/** Stable write-operation result used by integrations and command adapters. */
public record OperationResult(boolean success, String code, String message) {
    public static OperationResult success(String message) { return new OperationResult(true, "OK", message); }
    public static OperationResult failure(String code, String message) { return new OperationResult(false, code, message); }
}
