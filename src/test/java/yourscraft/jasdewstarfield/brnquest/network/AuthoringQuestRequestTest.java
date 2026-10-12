package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Quest-specific optional fields are normalized once while rename decisions remain server-owned. */
class AuthoringQuestRequestTest {
    @Test void notificationSwitchesAreValidatedWithoutChangingLegacyRequests() {
        var json = request();
        assertTrue(AuthoringRequestDecoder.quest(json.toString()).value().notifications().isEmpty());
        var values = new JsonObject(); json.add("behavior", values);
        values.addProperty("notify_task_complete", "false"); values.addProperty("notify_reward_claimed", "true");
        var parsed = AuthoringRequestDecoder.quest(json.toString());
        assertTrue(parsed.success());
        assertEquals("false", parsed.value().notifications().get("notify_task_complete"));
        assertEquals("true", parsed.value().notifications().get("notify_reward_claimed"));
        values.addProperty("notify_quest_complete", "invalid");
        assertFalse(AuthoringRequestDecoder.quest(json.toString()).success());
    }
    @Test void nameOverridesAreValidatedAndMissingFieldsKeepLegacyRequestsCompatible() {
        var json = request();
        assertNull(AuthoringRequestDecoder.quest(json.toString()).value().namePosition());
        var behavior = new JsonObject(); json.add("behavior", behavior);
        for (String value : List.of("inherit", "off", "above", "below")) {
            behavior.addProperty("quest_name_position", value);
            var decoded = AuthoringRequestDecoder.quest(json.toString());
            assertTrue(decoded.success());
            assertEquals(value, decoded.value().namePosition());
        }
        behavior.addProperty("quest_name_position", "sideways");
        assertEquals("INVALID_QUEST_APPEARANCE", AuthoringRequestDecoder.quest(json.toString()).failure().code());
    }
    static JsonObject request() {
        var json = new JsonObject();
        json.addProperty("sessionId", "00000000-0000-0000-0000-000000000001");
        json.addProperty("bookId", "test:book"); json.addProperty("draftRevision", "revision");
        json.addProperty("questId", "test:quest"); json.addProperty("replacementQuestId", "test:quest");
        json.addProperty("title", "Title"); json.addProperty("subtitle", ""); json.addProperty("description", "");
        json.addProperty("iconKind", "ITEM"); json.addProperty("iconValue", "minecraft:stone");
        json.addProperty("preserveIcon", true);
        return json;
    }

    @Test void quickTextRequestsPreserveOptionalProperties() {
        var decoded = AuthoringRequestDecoder.quest(request().toString());
        assertTrue(decoded.success());
        assertNull(decoded.value().x()); assertNull(decoded.value().appearance()); assertNull(decoded.value().behavior());
        assertTrue(decoded.value().preserveIcon());
        assertEquals("test:quest", decoded.value().questId().toString());
    }

    @Test void requiredFieldsAndScalarTypesAreCheckedBeforeServiceExecution() {
        for (String field : List.of("sessionId", "bookId", "questId", "replacementQuestId", "draftRevision", "title", "subtitle", "description", "iconKind", "iconValue")) {
            var json = request(); json.remove(field);
            assertFalse(AuthoringRequestDecoder.quest(json.toString()).success(), field);
        }
        var json = request(); json.addProperty("title", 12);
        assertFalse(AuthoringRequestDecoder.quest(json.toString()).success());
        json = request(); json.addProperty("title", "x".repeat(257));
        assertFalse(AuthoringRequestDecoder.quest(json.toString()).success());
    }

    @Test void positionsMustBePairedAndFinite() {
        var json = request(); json.addProperty("x", 3);
        assertEquals("INVALID_QUEST_POSITION", AuthoringRequestDecoder.quest(json.toString()).failure().code());
        json.addProperty("y", -5);
        assertTrue(AuthoringRequestDecoder.quest(json.toString()).success());
        json.addProperty("replacementQuestId", "test:renamed");
        assertTrue(AuthoringRequestDecoder.quest(json.toString()).success(), "handler must compare existing coordinates for renames");
        assertFalse(AuthoringRequestDecoder.quest(json.toString().replace("\"x\":3", "\"x\":1e999")).success());
    }

    @Test void iconParsingLeavesRegistryExistenceToTheHandler() {
        var json = request(); json.addProperty("preserveIcon", false);
        json.addProperty("iconValue", "test:unregistered_item");
        assertTrue(AuthoringRequestDecoder.quest(json.toString()).success());
        json.addProperty("iconValue", "Bad ID");
        assertEquals("INVALID_ICON_ITEM", AuthoringRequestDecoder.quest(json.toString()).failure().code());
        json.addProperty("iconKind", "TEXTURE");
        assertEquals("INVALID_ICON_TEXTURE", AuthoringRequestDecoder.quest(json.toString()).failure().code());
        json.addProperty("iconKind", "UNKNOWN");
        assertEquals("INVALID_ICON_KIND", AuthoringRequestDecoder.quest(json.toString()).failure().code());
    }

    @Test void appearanceRequiresACompletePositiveFiniteTuple() {
        var json = request(); json.addProperty("shape", "square");
        assertEquals("INVALID_QUEST_APPEARANCE", AuthoringRequestDecoder.quest(json.toString()).failure().code());
        json.addProperty("size", 1); json.addProperty("iconScale", 1); json.addProperty("minWidth", 0);
        assertTrue(AuthoringRequestDecoder.quest(json.toString()).success());
        json.addProperty("size", 0);
        assertEquals("INVALID_QUEST_APPEARANCE", AuthoringRequestDecoder.quest(json.toString()).failure().code());
    }

    @Test void behaviorNumbersEnumsAndLegacyBooleanSuffixesAreDecoded() {
        var json = request(); var behavior = new JsonObject(); json.add("behavior", behavior);
        behavior.addProperty("repeatable", "1b"); behavior.addProperty("repeat_cooldown_seconds", "20s");
        behavior.addProperty("dependency_requirement", "one_completed");
        var decoded = AuthoringRequestDecoder.quest(json.toString());
        assertTrue(decoded.success()); assertTrue(decoded.value().behavior().repeatable());
        assertEquals(20, decoded.value().behavior().repeatCooldownSeconds());
        behavior.addProperty("dependency_requirement", "unknown");
        assertEquals("INVALID_QUEST_BEHAVIOR", AuthoringRequestDecoder.quest(json.toString()).failure().code());
        behavior.remove("dependency_requirement"); behavior.addProperty("repeat_cooldown_seconds", "99999999999999999");
        assertEquals("INVALID_QUEST_BEHAVIOR", AuthoringRequestDecoder.quest(json.toString()).failure().code());
    }

    @Test void registeredExtensionOnlyUpdatesDoNotResetAppearanceOrBehavior() {
        var json = request(); var properties = new JsonObject(); json.add("behavior", properties);
        properties.addProperty("notify_task_complete", "false");
        properties.addProperty("quest_name_position", "below");
        var decoded = AuthoringRequestDecoder.quest(json.toString());
        assertTrue(decoded.success());
        assertNull(decoded.value().appearance());
        assertNull(decoded.value().behavior());
        assertEquals("below", decoded.value().properties().get("quest_name_position"));
        assertEquals(java.util.Map.of("notify_task_complete", "false"), decoded.value().notifications());
    }

    @Test void sparseRegisteredNativePatchesKeepOnlySuppliedKeys() {
        var json = request(); var properties = new JsonObject(); json.add("behavior", properties);
        properties.addProperty("repeatable", "true");
        properties.addProperty("size", "2");
        var decoded = AuthoringRequestDecoder.quest(json.toString());
        assertTrue(decoded.success());
        assertEquals(java.util.Map.of("repeatable", "true"), decoded.value().behavior().values());
        assertEquals(java.util.Map.of("size", "2.0"), decoded.value().appearance().values());
    }
}
