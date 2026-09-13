package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Every action family is tested with minimal legal input and adversarial JSON boundaries. */
class AuthoringMutationRequestTest {
    static JsonObject request(AuthoringMutationAction action) {
        var json = new JsonObject();
        json.addProperty("sessionId", "00000000-0000-0000-0000-000000000001");
        json.addProperty("bookId", "test:book"); json.addProperty("draftRevision", "revision");
        json.addProperty("action", action.wireName());
        switch (action) {
            case UNDO, REDO, REVIEW, MOVE_QUESTS -> { }
            default -> json.addProperty("targetId", "test:target");
        }
        switch (action) {
            case ADD_CHAPTER, UPDATE_CHAPTER, ADD_QUEST, ADD_TASK, UPDATE_TASK, COPY_TASK, MOVE_TASK, DELETE_TASK,
                 ADD_REWARD, UPDATE_REWARD, COPY_REWARD, MOVE_REWARD, DELETE_REWARD -> json.addProperty("parentId", "test:parent");
            default -> { }
        }
        switch (action) {
            case COPY_QUEST, ADD_DEPENDENCY, REMOVE_DEPENDENCY, ADD_TASK, UPDATE_TASK, COPY_TASK,
                 ADD_REWARD, UPDATE_REWARD, COPY_REWARD -> json.addProperty("sourceId", "test:source");
            default -> { }
        }
        json.addProperty("title", action == AuthoringMutationAction.UPDATE_REWARD ? "auto_hidden"
                : action == AuthoringMutationAction.UPDATE_QUEST_TRANSLATION ? "zh-CN" : "Title");
        if (action == AuthoringMutationAction.MOVE_QUESTS) {
            var positions = new JsonArray(); positions.add(position("test:a", 1)); positions.add(position("test:b", 2));
            json.add("positions", positions);
        }
        return json;
    }

    private static JsonObject position(String id, double x) {
        var json = new JsonObject(); json.addProperty("questId", id); json.addProperty("x", x); json.addProperty("y", -x);
        return json;
    }

    @Test void allTwentySevenMutationsAndReviewHaveMinimalDecodedRequests() {
        for (var action : AuthoringMutationAction.values()) {
            var result = AuthoringRequestDecoder.mutation(request(action).toString());
            assertTrue(result.success(), action + ": " + result.failure());
            assertEquals(action, result.value().action());
        }
    }

    @Test void requiredActionIdentifiersAreRejectedWhenMissing() {
        for (var action : AuthoringMutationAction.values()) {
            var json = request(action);
            for (String field : List.of("targetId", "parentId", "sourceId")) {
                if (!json.has(field)) continue;
                var invalid = json.deepCopy(); invalid.remove(field);
                var result = AuthoringRequestDecoder.mutation(invalid.toString());
                assertFalse(result.success(), action + " " + field);
                assertEquals(field, result.failure().path());
            }
        }
    }

    @Test void unknownActionAndInvalidJsonKeepStableFailureCodes() {
        var json = request(AuthoringMutationAction.UNDO); json.addProperty("action", "UNKNOWN");
        assertEquals("UNKNOWN_EDITOR_MUTATION", AuthoringRequestDecoder.mutation(json.toString()).failure().code());
        for (String invalid : List.of("null", "[]", "true", "{}", "{", "{\"action\":42}"))
            assertFalse(AuthoringRequestDecoder.mutation(invalid).success());
        json = request(AuthoringMutationAction.ADD_TASK); json.addProperty("targetIndex", 1.5);
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
    }

    @Test void positionsRejectDuplicatesMissingCoordinatesNonFiniteValuesAndOverLimit() {
        var json = request(AuthoringMutationAction.MOVE_QUESTS);
        var result = AuthoringRequestDecoder.mutation(json.toString());
        assertEquals(List.of("test:a", "test:b"), result.value().positions().keySet().stream().map(Object::toString).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.value().positions().clear());
        var positions = json.getAsJsonArray("positions"); positions.add(position("test:a", 3));
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        positions.remove(2); positions.get(0).getAsJsonObject().remove("x");
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        json = request(AuthoringMutationAction.MOVE_QUESTS);
        assertFalse(AuthoringRequestDecoder.mutation(json.toString().replace("\"x\":1.0", "\"x\":1e999")).success());
        assertFalse(AuthoringRequestDecoder.mutation(json.toString().replace("\"x\":1.0", "\"x\":\"NaN\"")).success());
        positions = new JsonArray();
        for (int i = 0; i < 4097; i++) positions.add(position("t:q" + i, 0));
        json.add("positions", positions);
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
    }

    @Test void opaqueConfigAndClaimSemanticsSurviveWithoutCoercionOrMutation() {
        var json = request(AuthoringMutationAction.UPDATE_REWARD);
        var config = new JsonObject(); config.addProperty("extension", "原样 {json:1}"); json.add("config", config);
        var decoded = AuthoringRequestDecoder.mutation(json.toString()).value();
        assertEquals("auto_hidden", decoded.claimPolicy());
        assertEquals(Map.of("extension", "原样 {json:1}"), decoded.config());
        assertThrows(UnsupportedOperationException.class, () -> decoded.config().put("other", "x"));
        config.addProperty("numeric", 123);
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        config.remove("numeric"); config.addProperty("", "bad");
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        config.remove(""); config.addProperty("huge", "x".repeat(65537));
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        config.remove("huge"); for (int i = 0; i < 65; i++) config.addProperty("field" + i, "x");
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
    }

    @Test void translationsKeepFullTextAndNormalizeOnlyLocale() {
        var json = request(AuthoringMutationAction.UPDATE_QUEST_TRANSLATION);
        var config = new JsonObject(); config.addProperty("description", "中".repeat(32768)); json.add("config", config);
        var decoded = AuthoringRequestDecoder.mutation(json.toString());
        assertTrue(decoded.success()); assertEquals("zh_cn", decoded.value().locale());
        assertEquals(32768, decoded.value().config().get("description").length());
        config.addProperty("description", "x".repeat(32769));
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
    }
    @Test void malformedTypedConfigRetainsOriginalObjectAndFieldDiagnostic() {
        var json = request(AuthoringMutationAction.UPDATE_TASK);
        var config = new JsonObject(); config.addProperty("", "invalid"); json.add("config", config);
        var result = AuthoringRequestDecoder.mutation(json.toString());
        assertEquals("test:source", result.failure().objectId());
        assertEquals("config", result.failure().path());
        var packets = new java.util.ArrayList<net.minecraft.network.protocol.common.custom.CustomPacketPayload>();
        new AuthoringResponseSender(packets::add, () -> 0).sendDecodeFailure("MUTATE", result.failure());
        var response = new com.google.gson.Gson().fromJson(((AuthoringNetwork.SessionPayload) packets.getFirst()).json(),
                AuthoringNetwork.SessionResponseWire.class);
        assertEquals("test:source", response.diagnostics().getFirst().objectId());
        assertEquals("config", response.diagnostics().getFirst().path());
    }

    @Test void localizedSingleLinePatchRejectsUnsupportedFieldsAndInvalidLocales() {
        var json = request(AuthoringMutationAction.UPDATE_CHAPTER);
        var config = new JsonObject();
        config.addProperty("text_field", "title");
        config.addProperty("text_locale.zh_cn", "Chinese chapter");
        json.add("config", config);
        assertTrue(AuthoringRequestDecoder.mutation(json.toString()).success());
        config.addProperty("text_field", "quest_subtitle");
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        config.addProperty("text_field", "title");
        config.addProperty("text_locale../path", "Invalid");
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
        config.remove("text_locale../path");
        config.addProperty("text_locale.zh_cn", "x".repeat(257));
        assertFalse(AuthoringRequestDecoder.mutation(json.toString()).success());
    }

}
