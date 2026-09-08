package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Supplier;

import static yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.*;

/** Untrusted wire input stops here: no players, services, side effects or packet delivery. */
final class AuthoringRequestDecoder {
    private static final Gson GSON = new Gson();
    private AuthoringRequestDecoder() {}

    record Failure(String code, String path, String message) {}
    record Result<T>(T value, Failure failure) {
        boolean success() { return failure == null; }
    }
    record OpenRequest(ResourceLocation bookId, String expectedDraftRevision) {}
    record CurrentRequest(ResourceLocation bookId, String activeRevision, String draftRevision, boolean replaceDraft) {}
    record LeaseRequest(UUID sessionId, String draftRevision) {}
    enum RecoveryAction { ABANDON, REFRESH, SAVE_AS }
    record RecoveryRequest(UUID sessionId, ResourceLocation bookId, RecoveryAction action, ResourceLocation targetBookId) {}

    static Result<OpenRequest> open(String bookId, String expectedRevision) {
        return boundary("INVALID_BOOK_ID", "Invalid draft book ID", () -> new OpenRequest(
                id(bookId, "bookId"), text(expectedRevision, "draftRevision", 32767, true)));
    }

    static Result<CurrentRequest> current(OpenCurrentSessionPayload wire) {
        return boundary("ACTIVE_BOOK_CHANGED", "The displayed task book is no longer active on this server",
                () -> new CurrentRequest(id(wire.bookId(), "bookId"),
                        text(wire.activeRevision(), "activeRevision", 32767, false),
                        text(wire.draftRevision(), "draftRevision", 32767, true), wire.replaceDraft()));
    }

    static Result<LeaseRequest> lease(String sessionId, String revision) {
        return boundary("INVALID_SESSION_ID", "Invalid edit-session ID", () -> new LeaseRequest(
                uuid(sessionId), text(revision, "draftRevision", 32767, false)));
    }

    static Result<RecoveryRequest> recovery(String json) {
        return boundary("INVALID_RECOVERY_REQUEST", "Invalid conflict recovery request", () -> {
            var wire = json(json, RecoveryWire.class);
            UUID session = uuid(wire.sessionId());
            ResourceLocation book = id(wire.bookId(), "bookId");
            RecoveryAction action;
            try { action = RecoveryAction.valueOf(wire.action()); }
            catch (RuntimeException invalid) {
                throw invalid("INVALID_RECOVERY_ACTION", "action", "Unknown conflict recovery action");
            }
            ResourceLocation target = null;
            if (action == RecoveryAction.SAVE_AS) {
                try { target = id(wire.targetBookId(), "targetBookId"); }
                catch (RuntimeException invalid) {
                    throw invalid("INVALID_RECOVERY_ACTION", "targetBookId", "Unknown conflict recovery action");
                }
            }
            return new RecoveryRequest(session, book, action, target);
        });
    }

    /** Bounds both UTF-8 bytes and JSON shape before Gson can coerce a null/non-object envelope. */
    private static <T> T json(String json, Class<T> type) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_EDITOR_METADATA_BYTES)
            throw invalid(null, "", "Request exceeds the editor metadata limit");
        var tree = JsonParser.parseString(json);
        if (!tree.isJsonObject()) throw invalid(null, "", "Request must be a JSON object");
        return GSON.fromJson(tree, type);
    }

    private static ResourceLocation id(String raw, String path) {
        ResourceLocation parsed = raw == null || raw.length() > 32767 ? null : ResourceLocation.tryParse(raw);
        if (parsed == null) throw invalid(null, path, "A valid namespaced ID is required");
        return parsed;
    }

    private static UUID uuid(String raw) {
        try {
            // Reject UUID.fromString's abbreviated groups while retaining ordinary uppercase UUIDs.
            UUID value = UUID.fromString(raw);
            if (!value.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException invalid) {
            throw invalid(null, "sessionId", "Invalid edit-session ID");
        }
    }

    private static String text(String value, String path, int maximum, boolean allowEmpty) {
        if (value == null || value.length() > maximum || (!allowEmpty && value.isBlank()))
            throw invalid(null, path, "Missing or oversized " + path);
        return value;
    }

    private static InvalidInput invalid(String code, String path, String message) {
        return new InvalidInput(new Failure(code, path, message));
    }

    /** Carries a stable code and field path without coupling decoding to service statuses. */
    private static final class InvalidInput extends IllegalArgumentException {
        private final Failure failure;
        private InvalidInput(Failure failure) { super(failure.message()); this.failure = failure; }
    }

    private static <T> Result<T> boundary(String code, String message, Supplier<T> decode) {
        try { return new Result<>(decode.get(), null); }
        catch (InvalidInput invalid) {
            Failure failure = invalid.failure;
            return new Result<>(null, new Failure(failure.code() == null ? code : failure.code(), failure.path(),
                    failure.code() == null ? message : failure.message()));
        } catch (RuntimeException invalid) {
            return new Result<>(null, new Failure(code, "", message));
        }
    }
}
