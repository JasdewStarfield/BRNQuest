package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestCreationDefaultsTest {
    private static ResourceLocation id(String value) { return ResourceLocation.parse("test:" + value); }
    private static QuestCreationDefaults defaults(Map<String, String> map) { return new QuestCreationDefaults(map); }
    private static QuestDefinition quest(String name) {
        return new QuestDefinition(id("b"), id(name), id("c"), name, "", "", "", 0, 0, List.of(), List.of(), List.of(), "");
    }
    private static QuestBookDefinition book() {
        var chapter = new ChapterDefinition(id("b"), id("c"), id("g"), "C", "", 0, List.of(quest("old")), Map.of("addon", "kept"),
                defaults(Map.of("min_width", "0", "repeatable", "false")));
        return new QuestBookDefinition(id("b"), 1, "Book", List.of(new ChapterGroupDefinition(id("b"), id("g"), "G", 0)),
                List.of(chapter), Map.of(), BookLocalization.EMPTY, Map.of("addon", "kept"),
                defaults(Map.of("shape", "circle", "size", "2", "min_width", "5", "repeatable", "true")));
    }
    @Test void creationResolvesBookChapterAndExplicitValuesExactlyOnce() {
        var source = book();
        var result = DraftBookEditor.createQuest(source, id("c"), quest("new"), defaults(Map.of("size", "3"))).value().book();
        var created = result.quests().getLast();
        assertEquals(new QuestAppearance("circle", 3, 1, 0), created.appearance());
        assertFalse(created.behavior().repeatable());
        assertEquals(source.quests().getFirst(), result.quests().getFirst());
        var changedDefaults = DraftBookEditor.updateBookProperties(result, defaults(Map.of("size", "8")), "en_us", Map.of()).value().book();
        assertEquals(result.quests(), changedDefaults.quests());
        var copy = DraftBookEditor.copyQuest(changedDefaults, id("new"), new QuestDefinition(id("b"), id("copy"), id("c"), "Copy", "", "", "", 0, 0,
                List.of(), List.of(), List.of(), "", created.appearance(), created.behavior(), Map.of())).value().book();
        assertEquals(created.appearance(), copy.quests().getLast().appearance());
    }
    @Test void templatesSurviveEncodingProjectionRecoveryAndOtherEdits() {
        var source = book();
        var decoded = NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(source)).getAsJsonObject());
        assertEquals(source, decoded);
        var moved = DraftBookEditor.updateQuestPositions(source, Map.of(id("old"), new DraftBookEditor.Position(2, 3))).value().book();
        moved = DraftBookEditor.moveChapterOrder(moved, id("c"), 0).value().book();
        moved = LocalizedSingleLineEdits.apply(moved, "chapter", id("c"), "title", Map.of("zh_cn", "Chinese"));
        moved = DraftService.recoveryCopy(moved, id("recovery"));
        assertEquals(source.questDefaults(), moved.questDefaults());
        assertEquals(source.chapters().getFirst().questDefaults(), moved.chapters().getFirst().questDefaults());
        assertEquals(source.questDefaults().values(), ApiViews.book(QuestBookSnapshot.of(moved), "zh_cn").questDefaults());
        assertEquals(source.chapters().getFirst().questDefaults().values(), ApiViews.chapter(moved.chapters().getFirst()).questDefaults());
    }
    @Test void changingAnUpstreamPresetOnlyChangesSubsequentlyCreatedQuests() {
        var source = book();
        var created = DraftBookEditor.createQuest(source, id("c"), quest("before"), QuestCreationDefaults.EMPTY).value().book();
        assertEquals(2, created.quests().getLast().appearance().size());
        var changed = DraftBookEditor.updateBookProperties(created, defaults(Map.of("size", "4")), "en_us", Map.of()).value().book();
        var next = DraftBookEditor.createQuest(changed, id("c"), quest("after"), QuestCreationDefaults.EMPTY).value().book();
        assertEquals(2, next.quests().stream().filter(q -> q.id().equals(id("before"))).findFirst().orElseThrow().appearance().size());
        assertEquals(4, next.quests().getLast().appearance().size());

        // An explicit chapter preset survives later book changes; clearing it resumes upstream resolution.
        var chapter = next.chapters().getFirst();
        var override = new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                chapter.order(), chapter.quests(), chapter.extensions(), defaults(Map.of("size", "1")),
                chapter.consumeItems(), chapter.autofocusQuestId(), chapter.defaultHideDependencyLines());
        var overridden = DraftBookEditor.updateChapter(next, id("c"), override).value().book();
        overridden = DraftBookEditor.updateBookProperties(overridden, defaults(Map.of("size", "8")), "en_us", Map.of()).value().book();
        overridden = DraftBookEditor.createQuest(overridden, id("c"), quest("override"), QuestCreationDefaults.EMPTY).value().book();
        assertEquals(1, overridden.quests().getLast().appearance().size());
        chapter = overridden.chapters().getFirst();
        var clear = new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                chapter.order(), chapter.quests(), chapter.extensions(), QuestCreationDefaults.EMPTY,
                chapter.consumeItems(), chapter.autofocusQuestId(), chapter.defaultHideDependencyLines());
        var cleared = DraftBookEditor.updateChapter(overridden, id("c"), clear).value().book();
        var finalBook = DraftBookEditor.createQuest(cleared, id("c"), quest("resumed"), QuestCreationDefaults.EMPTY).value().book();
        assertEquals(8, finalBook.quests().getLast().appearance().size());
        assertEquals(cleared.quests(), finalBook.quests().subList(0, cleared.quests().size()));
    }
    @Test void defaultChangesHaveReviewPathsAndEmptyTemplatesKeepLegacyEncoding() {
        var source = book();
        var changed = DraftBookEditor.updateBookProperties(source, QuestCreationDefaults.EMPTY, "en_us", Map.of()).value().book();
        assertTrue(QuestBookDiffer.diff(source, changed).entries().stream().anyMatch(e -> e.path().equals("quest_defaults.size")));
        var old = new QuestBookDefinition(id("b"), 1, "B", List.of(), List.of(), Map.of());
        assertFalse(NativeBookJson.encode(old).contains("quest_defaults"));
    }
    @Test void invalidDefaultsAndWrongJsonTypesAreRejected() {
        for (var values : List.of(Map.of("size", "0"), Map.of("size", "NaN"), Map.of("min_width", "-1"), Map.of("repeatable", "0"), Map.of("unknown", "1")))
            assertThrows(IllegalArgumentException.class, () -> defaults(values));
        assertThrows(IllegalArgumentException.class, () -> QuestCreationDefaults.fromJson(JsonParser.parseString("{\"repeatable\":\"false\"}")));
        assertEquals(defaults(Map.of("repeatable", "false", "min_width", "0")), QuestCreationDefaults.fromJson(JsonParser.parseString("{\"repeatable\":false,\"min_width\":0}")));
    }
    @Test void changingFallbackPreservesFormerDefaultLanguage() {
        var changed = DraftBookEditor.updateBookProperties(book(), book().questDefaults(), "zh_cn", Map.of("zh_cn", "Chinese book")).value().book();
        assertEquals("Book", BookText.title(changed, "en_us"));
        assertEquals("Chinese book", BookText.title(changed, "zh_cn"));
        assertEquals("C", BookText.structureTitle(changed, "chapter", id("c"), "en_us", ""));
    }
    @Test void additionalBehaviorDefaultsResolveAndZeroOverridesArePreserved() {
        var parent = defaults(Map.of("invisible_until_complete", "true", "visible_after_tasks", "2", "hide_lock_icon", "true",
                "dependency_requirement", "one_started", "repeat_cooldown_seconds", "120", "ignore_reward_blocking", "true"));
        var child = parent.overlay(defaults(Map.of("visible_after_tasks", "0", "repeat_cooldown_seconds", "0", "hide_lock_icon", "false")));
        var roundTrip = QuestCreationDefaults.fromJson(child.toJson());
        assertEquals(child, roundTrip);
        var behavior = child.behavior();
        assertTrue(behavior.invisibleUntilComplete());
        assertEquals(0, behavior.visibleAfterTasks());
        assertFalse(behavior.hideLockIcon());
        assertEquals(DependencyRequirement.ONE_STARTED, behavior.dependencyRequirement());
        assertEquals(0, behavior.repeatCooldownSeconds());
        assertTrue(behavior.ignoreRewardBlocking());
        var configured = DraftBookEditor.updateBookProperties(book(), parent, "en_us", Map.of()).value().book();
        var created = DraftBookEditor.createQuest(configured, id("c"), quest("new"), QuestCreationDefaults.EMPTY).value().book().quests().getLast();
        assertEquals(120, created.behavior().repeatCooldownSeconds());
        assertEquals(DependencyRequirement.ONE_STARTED, created.behavior().dependencyRequirement());
    }
    @Test void numericAndEnumDefaultsNeverSilentlyClampOrFallback() {
        for (var values : List.of(Map.of("visible_after_tasks", "-1"), Map.of("visible_after_tasks", "1.5"),
                Map.of("repeat_cooldown_seconds", "2147483648"), Map.of("dependency_requirement", "typo")))
            assertThrows(IllegalArgumentException.class, () -> defaults(values));
        assertThrows(IllegalArgumentException.class, () -> QuestCreationDefaults.fromJson(JsonParser.parseString("{\"visible_after_tasks\":true}")));
    }

    @Test void notificationDisplayAndDependencyDefaultsResolveAcrossBookChapterAndExplicitCreation() {
        var source = book();
        var parent = source.questDefaults().overlay(defaults(Map.of(
                QuestNotificationPolicy.TASK, "false", QuestNotificationPolicy.QUEST, "false",
                QuestNotificationPolicy.REWARD, "true", "quest_name_position", "above", "minimum_required_dependencies", "3")));
        var configured = DraftBookEditor.updateBookProperties(source, parent, "en_us", Map.of()).value().book();
        var chapter = configured.chapters().getFirst();
        configured = DraftBookEditor.updateChapter(configured, chapter.id(),
                new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                        chapter.order(), chapter.quests(), chapter.extensions(),
                        chapter.questDefaults().overlay(defaults(Map.of(QuestNotificationPolicy.TASK, "true", "minimum_required_dependencies", "2")))))
                .value().book();
        var createdBook = DraftBookEditor.createQuest(configured, id("c"), quest("new"), QuestCreationDefaults.EMPTY).value().book();
        var created = createdBook.quests().getLast();
        assertEquals(new QuestNotificationPolicy(true, false, true), QuestNotificationPolicy.read(created.extensions()));
        assertEquals(DisplayOverrides.NamePosition.ABOVE, DisplayOverrides.name(created.extensions()));
        assertEquals(2, created.behavior().minimumRequiredDependencies());
        var explicit = defaults(Map.of(QuestNotificationPolicy.TASK, "false", QuestNotificationPolicy.QUEST, "true",
                "quest_name_position", "inherit", "minimum_required_dependencies", "0"));
        var nextBook = DraftBookEditor.createQuest(createdBook, id("c"), quest("explicit"), explicit).value().book();
        assertEquals(new QuestNotificationPolicy(false, true, true), QuestNotificationPolicy.read(nextBook.quests().getLast().extensions()));
        assertEquals(DisplayOverrides.NamePosition.INHERIT, DisplayOverrides.name(nextBook.quests().getLast().extensions()));
        assertEquals(0, nextBook.quests().getLast().behavior().minimumRequiredDependencies());
        assertEquals(nextBook, NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(nextBook)).getAsJsonObject()));
        var changed = DraftBookEditor.updateBookProperties(nextBook, QuestCreationDefaults.EMPTY, "en_us", Map.of()).value().book();
        assertEquals(nextBook.quests(), changed.quests(), "Changing defaults must not rewrite existing quests");
        assertEquals(source.quests().getFirst(), changed.quests().getFirst());
    }

    @Test void defaultExtensionsPreserveExplicitInstancePropertiesAndOpaqueAddonData() {
        var template = defaults(Map.of(QuestNotificationPolicy.QUEST, "false", "quest_name_position", "above"));
        var explicit = Map.of("addon:custom", "kept", DisplayOverrides.QUEST_NAME_KEY, "below",
                "brnquest:" + QuestNotificationPolicy.QUEST, "true");
        var merged = template.extensions(explicit);
        assertEquals("kept", merged.get("addon:custom"));
        assertEquals(DisplayOverrides.NamePosition.BELOW, DisplayOverrides.name(merged));
        assertTrue(QuestNotificationPolicy.read(merged).questCompleted());
        assertEquals(template, QuestCreationDefaults.fromJson(template.toJson()));
    }

    @Test void newDefaultFieldsRejectWrongTypesAndInvalidEnumOrThresholdValues() {
        assertThrows(IllegalArgumentException.class, () -> defaults(Map.of("quest_name_position", "invalid")));
        assertThrows(IllegalArgumentException.class, () -> defaults(Map.of("minimum_required_dependencies", "-1")));
        assertThrows(IllegalArgumentException.class, () -> QuestCreationDefaults.fromJson(JsonParser.parseString("{\"notify_task_complete\":\"true\"}")));
        assertThrows(IllegalArgumentException.class, () -> QuestCreationDefaults.fromJson(JsonParser.parseString("{\"quest_name_position\":false}")));
    }

    @Test void everyBehaviorPropertyHasACreationDefault() {
        // Reflect the owning record so a newly added behavior cannot silently miss both default forms.
        for (var field : QuestBehavior.class.getRecordComponents()) {
            String key = field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            assertTrue(QuestCreationDefaults.FIELDS.contains(key), () -> "Missing behavior default: " + key);
        }
    }

}
