package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Field validation is exercised without a player, world, session lookup or transport. */
class AuthoringRequestDecoderTest {
    private static final String SESSION = "00000000-0000-0000-0000-000000000001";
    private static final Gson GSON = new Gson();

    @Test void sessionRequestsCarryParsedIdentifiersAndPreserveRevisionChoices() {
        var open = AuthoringRequestDecoder.open("test:book", "");
        assertTrue(open.success());
        assertEquals("test:book", open.value().bookId().toString());
        assertEquals("", open.value().expectedDraftRevision());
        var current = AuthoringRequestDecoder.current(new AuthoringNetwork.OpenCurrentSessionPayload(
                "test:book", "active", "draft", true));
        assertTrue(current.success());
        assertTrue(current.value().replaceDraft());
        assertEquals("active", current.value().activeRevision());
        assertEquals("draft", current.value().draftRevision());
        assertEquals(SESSION, AuthoringRequestDecoder.lease(SESSION, "revision").value().sessionId().toString());
    }

    @Test void malformedSessionInputReturnsStableCodesAndFieldPaths() {
        assertEquals("INVALID_BOOK_ID", AuthoringRequestDecoder.open(null, "").failure().code());
        assertEquals("bookId", AuthoringRequestDecoder.open("Bad ID", "").failure().path());
        assertEquals("INVALID_SESSION_ID", AuthoringRequestDecoder.lease("0-0-0-0-1", "r").failure().code());
        assertEquals("draftRevision", AuthoringRequestDecoder.lease(SESSION, null).failure().path());
        assertFalse(AuthoringRequestDecoder.current(new AuthoringNetwork.OpenCurrentSessionPayload(
                "test:book", null, "", false)).success());
    }

    @Test void recoveryNormalizesAllThreeActionsBeforeAnyServiceCanRun() {
        for (String action : List.of("ABANDON", "REFRESH", "SAVE_AS")) {
            var decoded = AuthoringRequestDecoder.recovery(GSON.toJson(new AuthoringNetwork.RecoveryWire(
                    SESSION, "test:book", action, "test:copy")));
            assertTrue(decoded.success());
            assertEquals(action, decoded.value().action().name());
            if (action.equals("SAVE_AS")) assertEquals("test:copy", decoded.value().targetBookId().toString());
            else assertNull(decoded.value().targetBookId());
        }
        var invalid = AuthoringRequestDecoder.recovery(GSON.toJson(new AuthoringNetwork.RecoveryWire(
                SESSION, "test:book", "SAVE_AS", "Bad ID")));
        assertEquals("INVALID_RECOVERY_ACTION", invalid.failure().code());
        assertEquals("targetBookId", invalid.failure().path());
    }

    @Test void recoveryRejectsNullJsonWrongShapeUnknownActionAndOversizedMetadata() {
        for (String json : List.of("null", "[]", "1", "{", "{}", " ".repeat(262145)))
            assertFalse(AuthoringRequestDecoder.recovery(json).success(), json.substring(0, Math.min(20, json.length())));
        assertEquals("INVALID_RECOVERY_ACTION", AuthoringRequestDecoder.recovery(GSON.toJson(
                new AuthoringNetwork.RecoveryWire(SESSION, "test:book", "UNKNOWN", ""))).failure().code());
    }
    @Test void publicationEnvelopeRequiresTheCompleteRevisionBoundIdentity() {
        assertTrue(AuthoringRequestDecoder.publication(SESSION, "test:book", "revision", false).success());
        assertEquals("INVALID_SAVE_REQUEST", AuthoringRequestDecoder.publication(SESSION, "test:book", "", false).failure().code());
        assertEquals("INVALID_PUBLISH_REQUEST", AuthoringRequestDecoder.publication(null, "test:book", "r", true).failure().code());
        assertEquals("bookId", AuthoringRequestDecoder.publication(SESSION, "Bad ID", "r", true).failure().path());
    }
    @Test void invalidLiveBookPreservesTheOriginalConflictResponse() {
        var decoded = AuthoringRequestDecoder.live("Bad ID");
        assertEquals("LIVE_BOOK_CHANGED", decoded.failure().code());
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.common.custom.CustomPacketPayload>();
        new AuthoringResponseSender(packets::add, () -> 0).sendDecodeFailure("OPEN", decoded.failure());
        var response = GSON.fromJson(((AuthoringNetwork.SessionPayload) packets.getFirst()).json(), AuthoringNetwork.SessionResponseWire.class);
        assertEquals("CONFLICT", response.status());
        assertEquals("LIVE_BOOK_CHANGED", response.code());
    }
}
