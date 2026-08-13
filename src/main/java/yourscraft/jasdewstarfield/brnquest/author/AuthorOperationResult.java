package yourscraft.jasdewstarfield.brnquest.author;

import java.util.Optional;

/** Internal structured result shared by commands and the future editor protocol. */
public record AuthorOperationResult<T>(Status status, String code, String message, T value) {
    public enum Status { SUCCESS, NO_CHANGE, FORBIDDEN, CONFLICT, NOT_FOUND, INVALID_REQUEST, EXPIRED, IO_FAILURE }

    public boolean success() {
        return status == Status.SUCCESS || status == Status.NO_CHANGE;
    }

    public Optional<T> optionalValue() {
        return Optional.ofNullable(value);
    }

    public static <T> AuthorOperationResult<T> success(String code, String message, T value) {
        return new AuthorOperationResult<>(Status.SUCCESS, code, message, value);
    }

    public static <T> AuthorOperationResult<T> noChange(String code, String message, T value) {
        return new AuthorOperationResult<>(Status.NO_CHANGE, code, message, value);
    }

    public static <T> AuthorOperationResult<T> failure(Status status, String code, String message) {
        return failure(status, code, message, null);
    }

    public static <T> AuthorOperationResult<T> failure(Status status, String code, String message, T value) {
        if (status == Status.SUCCESS || status == Status.NO_CHANGE) {
            throw new IllegalArgumentException("Failure result cannot use a success status");
        }
        return new AuthorOperationResult<>(status, code, message, value);
    }
}
