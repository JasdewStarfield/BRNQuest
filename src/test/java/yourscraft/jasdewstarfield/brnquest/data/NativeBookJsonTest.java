package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;

import static org.junit.jupiter.api.Assertions.*;

class NativeBookJsonTest {
    @Test void roundTripIsDeterministicAndImmutable() {
        ResourceLocation bookId = ResourceLocation.parse("test:main");
        ResourceLocation chapterId = ResourceLocation.parse("test:intro");
        TaskDefinition task = new TaskDefinition(bookId, ResourceLocation.parse("test:task"), ResourceLocation.parse("brnquest:checkmark"), Map.of(), false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Quest", "Subtitle", "Description", "{id:\"minecraft:book\",count:1}",
                1, 2, List.of(), List.of(task), List.of(), "ABCDEF0123456789");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book", List.of(), List.of(new ChapterDefinition(bookId, chapterId, ResourceLocation.parse("test:group"), "Intro", "", 0, List.of(quest))), Map.of("ABCDEF0123456789", quest.id()));
        String encoded = NativeBookJson.encode(book);
        QuestBookDefinition decoded = NativeBookJson.decode(JsonParser.parseString(encoded).getAsJsonObject());
        assertEquals(encoded, NativeBookJson.encode(decoded));
        assertEquals("Subtitle", decoded.quests().getFirst().subtitle());
        assertEquals("Description", decoded.quests().getFirst().description());
        assertEquals(QuestBookSnapshot.of(book).revision(), QuestBookSnapshot.of(decoded).revision());
        var codecJson = QuestDefinition.CODEC.encodeStart(JsonOps.INSTANCE, quest).getOrThrow();
        assertEquals(quest, QuestDefinition.CODEC.parse(JsonOps.INSTANCE, codecJson).getOrThrow());
        assertThrows(UnsupportedOperationException.class, () -> decoded.chapters().add(null));
    }

    @Test void preservesAuthorVisibleTaskAndRewardOrder() {
        ResourceLocation bookId = ResourceLocation.parse("test:main");
        ResourceLocation chapterId = ResourceLocation.parse("test:intro");
        TaskDefinition firstTask = new TaskDefinition(bookId, ResourceLocation.parse("test:z_task"), ResourceLocation.parse("brnquest:checkmark"), Map.of(), false);
        TaskDefinition secondTask = new TaskDefinition(bookId, ResourceLocation.parse("test:a_task"), ResourceLocation.parse("brnquest:custom"), Map.of(), false);
        RewardDefinition firstReward = new RewardDefinition(bookId, ResourceLocation.parse("test:z_reward"), ResourceLocation.parse("brnquest:custom"), Map.of(), "manual", false);
        RewardDefinition secondReward = new RewardDefinition(bookId, ResourceLocation.parse("test:a_reward"), ResourceLocation.parse("brnquest:custom"), Map.of(), "manual", false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Quest", "", "", "", 0, 0, List.of(), List.of(firstTask, secondTask),
                List.of(firstReward, secondReward), "LEGACY");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book", List.of(),
                List.of(new ChapterDefinition(bookId, chapterId, ResourceLocation.parse("test:group"), "Intro", "", 0, List.of(quest))), Map.of());

        QuestDefinition decoded = NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject()).quests().getFirst();
        assertEquals(List.of(firstTask.id(), secondTask.id()), decoded.tasks().stream().map(TaskDefinition::id).toList());
        assertEquals(List.of(firstReward.id(), secondReward.id()), decoded.rewards().stream().map(RewardDefinition::id).toList());
    }

    @Test void oldDescriptionsStayPlainWhileKnownAndUnknownFormatsRoundTrip() {
        var oldJson = JsonParser.parseString("""
                {"schema_version":1,"id":"test:book","title":"Book","chapter_groups":[],
                 "chapters":[{"id":"test:chapter","group_id":"test:group","title":"Chapter","icon":"","order":0,
                 "extensions":{},"quests":[{"id":"test:quest","title":"Quest","subtitle":"",
                 "description":"*literal* [not a link](text)","icon":"","x":0,"y":0,"legacy_id":"",
                 "appearance":{},"extensions":{},"dependencies":[],"tasks":[],"rewards":[]}]}],"legacy_ids":{}}
                """).getAsJsonObject();
        QuestDefinition oldQuest = NativeBookJson.decode(oldJson).quests().getFirst();
        assertEquals(DocumentFormat.PLAIN, oldQuest.descriptionFormat());
        assertFalse(NativeBookJson.encode(NativeBookJson.decode(oldJson)).contains("description_format"));

        QuestDefinition markdown = new QuestDefinition(oldQuest.bookId(), oldQuest.id(), oldQuest.chapterId(),
                oldQuest.title(), oldQuest.subtitle(), oldQuest.description(), DocumentFormat.MARKDOWN_V1,
                oldQuest.icon(), oldQuest.x(), oldQuest.y(), oldQuest.dependencies(), oldQuest.tasks(),
                oldQuest.rewards(), oldQuest.legacyId(), oldQuest.appearance(), oldQuest.behavior(), oldQuest.extensions());
        var markdownBook = new QuestBookDefinition(oldQuest.bookId(), 1, "Book", List.of(),
                List.of(new ChapterDefinition(oldQuest.bookId(), oldQuest.chapterId(), ResourceLocation.parse("test:group"),
                        "Chapter", "", 0, List.of(markdown))), Map.of());
        assertEquals(DocumentFormat.MARKDOWN_V1, NativeBookJson.decode(JsonParser.parseString(
                NativeBookJson.encode(markdownBook)).getAsJsonObject()).quests().getFirst().descriptionFormat());

        oldJson.getAsJsonArray("chapters").get(0).getAsJsonObject().getAsJsonArray("quests").get(0)
                .getAsJsonObject().addProperty("description_format", "future_v7");
        QuestBookDefinition unknown = NativeBookJson.decode(oldJson);
        assertEquals("future_v7", unknown.quests().getFirst().descriptionFormat().serializedName());
        assertTrue(NativeBookJson.encode(unknown).contains("\"description_format\": \"future_v7\""));
    }

    @Test void roundTripsLocalizationAppearanceExtensionsAndRewardPolicies() {
        ResourceLocation bookId = ResourceLocation.parse("test:localized");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        RewardDefinition reward = new RewardDefinition(bookId, ResourceLocation.parse("test:reward"),
                ResourceLocation.parse("brnquest:custom"), Map.of(), "auto_hidden", false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Fallback", "", "line one\nline two", "", 0, 0, List.of(), List.of(), List.of(reward), "ABC",
                new QuestAppearance("circle", 1.5, 0.75, 2.0),
                new QuestBehavior(true, false, true, 2, true, true, false,
                        DependencyRequirement.ONE_COMPLETED, 1, true, true, 30, false),
                Map.of("ftb.hide", "true"));
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book", List.of(),
                List.of(new ChapterDefinition(bookId, chapterId, ResourceLocation.parse("test:group"), "Chapter", "",
                        0, List.of(quest), Map.of("ftb.filename", "\"legacy\""))), Map.of(),
                new BookLocalization("zh_cn", Map.of("zh_cn", Map.of("quest.ABC.title", "中文标题"))),
                Map.of("ftb.default_reward_team", "false"));

        QuestBookDefinition decoded = NativeBookJson.decode(
                JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject());
        QuestDefinition decodedQuest = decoded.quests().getFirst();
        assertEquals("中文标题", decoded.localization().resolve("zh_cn", "quest.ABC.title", "Fallback"));
        assertEquals("line one\nline two", decodedQuest.description());
        assertEquals(new QuestAppearance("circle", 1.5, 0.75, 2.0), decodedQuest.appearance());
        assertEquals(DependencyRequirement.ONE_COMPLETED, decodedQuest.behavior().dependencyRequirement());
        assertTrue(decodedQuest.behavior().repeatable());
        assertEquals(30, decodedQuest.behavior().repeatCooldownSeconds());
        assertEquals("true", decodedQuest.extensions().get("ftb.hide"));
        assertEquals(RewardClaimPolicy.AUTO_HIDDEN, decodedQuest.rewards().getFirst().policy());
        assertFalse(decodedQuest.rewards().getFirst().policy().visible());
    }
}
