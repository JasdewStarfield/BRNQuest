package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthoringNetworkTest {
    private static final Gson GSON = new Gson();

    @Test void unchangedItemRewardUsesRewardSchemaDespiteSharingTheItemTaskId() {
        var bookId = ResourceLocation.parse("test:reward_book");
        var questId = ResourceLocation.parse("test:quest");
        var chapterId = ResourceLocation.parse("test:chapter");
        var groupId = ResourceLocation.parse("test:group");
        var rewardId = ResourceLocation.parse("test:reward");
        Map<String, String> config = Map.of("item", "{count:1,id:\"minecraft:jungle_door\"}",
                "count", "2", "extension_field", "keep me");
        var reward = new yourscraft.jasdewstarfield.brnquest.data.RewardDefinition(bookId, rewardId,
                ResourceLocation.parse("brnquest:item"), config, "manual", false);
        var quest = new QuestDefinition(bookId, questId, chapterId, "Quest", "", "", "", 0, 0,
                List.of(), List.of(), List.of(reward), "");
        var book = new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0, List.of(quest))), Map.of());

        // Exercise the same replacement path as UPDATE_REWARD, not just the bounding helper.
        var replacement = AuthoringMutationHandler.rewardReplacement(book, questId, rewardId, rewardId,
                config, "manual", false);
        assertEquals(reward, replacement);
        assertEquals(config, AuthoringMutationHandler.rewardMutationConfig(config), "ADD_REWARD preserves the same schema");
        var error = yourscraft.jasdewstarfield.brnquest.reward.RewardTypeExecutor.configError(
                yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry.get(reward.typeId()),
                yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(replacement));
        assertTrue(error.isEmpty(), error.toString());
    }

    @Test void typedMutationConfigIsCopiedBeforeUse() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("item", "{id:\"minecraft:stone\",count:1}");

        Map<String, String> decoded = AuthoringRequestDecoder.boundedConfig(source);
        source.put("count", "64");

        assertEquals(Map.of("item", "{id:\"minecraft:stone\",count:1}"), decoded);
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("count", "1"));
    }

    @Test void typedMutationConfigRejectsOversizedOrMalformedFields() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthoringRequestDecoder.boundedConfig(Map.of("", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> AuthoringRequestDecoder.boundedConfig(Map.of("item", "x".repeat(65_537))));

        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < 65; index++) tooMany.put("field_" + index, "value");
        assertThrows(IllegalArgumentException.class, () -> AuthoringRequestDecoder.boundedConfig(tooMany));
    }

    @Test void typedCodecFailuresPointBackToTheCompleteRawConfig() {
        var wire = new AuthoringNetwork.EditorMutationWire("session", "test:book", "revision",
                "UPDATE_TASK", "test:task", "test:quest", "test:task", "", 0, 0, 0,
                List.of(), Map.of());
        var codecFailure = new Diagnostic(Diagnostic.Severity.ERROR, "BQV-119", "", "",
                "test:task", "Invalid task config");

        var mapped = AuthoringResponseSender.mutationDiagnosticWires(wire.action(), List.of(codecFailure));

        assertEquals("config", mapped.getFirst().path());
    }

    @Test void maximumChapterAndLongTextStayInsideVerifiedChunkTransport() {
        ResourceLocation bookId = ResourceLocation.parse("test:limit");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        List<QuestDefinition> quests = new ArrayList<>(BrnQuestConstants.MAX_QUESTS);
        String description = "Long UTF-8 description 长文本 ".repeat(32);
        for (int index = 0; index < BrnQuestConstants.MAX_QUESTS; index++) {
            quests.add(new QuestDefinition(bookId, ResourceLocation.parse("test:q_" + index), chapterId,
                    "Quest " + index, "", description, "", index % 128, index / 128,
                    List.of(), List.of(), List.of(), ""));
        }
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Limit",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0, quests)), Map.of());
        String json = NativeBookJson.encode(book);
        List<String> chunks = BrnQuestNetwork.split(json, BrnQuestNetwork.BOOK_CHUNK_CHARACTERS);

        assertTrue(json.getBytes(StandardCharsets.UTF_8).length <= BrnQuestConstants.MAX_BOOK_BYTES);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getBytes(StandardCharsets.UTF_8).length
                <= BrnQuestConstants.MAX_BOOK_CHUNK_BYTES));
        assertEquals(BrnQuestConstants.MAX_QUESTS,
                NativeBookJson.decode(com.google.gson.JsonParser.parseString(String.join("", chunks))
                        .getAsJsonObject()).quests().size());
    }

    @Test void maximumPositionAndDiagnosticMetadataRemainBounded() {
        List<AuthoringNetwork.PositionWire> positions = new ArrayList<>(BrnQuestConstants.MAX_QUESTS);
        for (int index = 0; index < BrnQuestConstants.MAX_QUESTS; index++) {
            positions.add(new AuthoringNetwork.PositionWire("test:q_" + index, index, -index));
        }
        List<AuthoringNetwork.EditorDiagnosticWire> diagnostics = new ArrayList<>();
        for (int index = 0; index < 128; index++) {
            diagnostics.add(new AuthoringNetwork.EditorDiagnosticWire("WARN", "BQR-" + index,
                    "test:q_" + index, "description", "x".repeat(240)));
        }
        var patch = new AuthoringNetwork.SessionResponseWire("PATCH", "SUCCESS", "QUESTS_MOVED", "ok",
                "00000000-0000-0000-0000-000000000000", "test:limit", "base", "draft", "saved",
                36_000, 0, 0, 1, 0, List.of(), null, positions);
        var review = new AuthoringNetwork.SessionResponseWire("REVIEW", "SUCCESS", "READY", "ok",
                "00000000-0000-0000-0000-000000000000", "test:limit", "base", "draft", "saved",
                36_000, 0, 0, 0, 0, List.of(), new AuthoringNetwork.PublishReviewWire(
                true, "WORKSPACE", "base", "draft", "BACKUP_AND_REPLACE", diagnostics.size(), 0,
                false, diagnostics, List.of()), List.of());

        assertTrue(GSON.toJson(patch).getBytes(StandardCharsets.UTF_8).length
                <= BrnQuestConstants.MAX_EDITOR_METADATA_BYTES);
        assertTrue(GSON.toJson(review).getBytes(StandardCharsets.UTF_8).length
                <= BrnQuestConstants.MAX_EDITOR_METADATA_BYTES);
    }
}
