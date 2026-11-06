package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the bounded-payload regressions that made the original publish list unreadable. */
class QuestPublishReviewContentTest {
    private static final String PREFIX = "screen.brnquest.editor.publish.review.content.";
    private static final ResourceLocation BOOK = id("book"), GROUP = id("group"), CHAPTER = id("chapter"), QUEST = id("quest");
    private static final String LEGACY = "legacy:quest.with.dots";
    private static final String FULL_VALUE = "Complete extension config " + "x".repeat(400);

    @Test void addedNumericQuestUsesCompleteDraftCountsInsteadOfRepeatingItsTitleOrBrokenJson() {
        var content = new QuestPublishReviewContent(snapshot(), "zh_cn");
        var view = content.change(change("ADDED", "QUEST", QUEST, "", "", "{\"tasks\":[{\"type\":\"test:task\"…"), ignored -> null);
        assertTrue(view.heading().getString().startsWith("1 · "));
        assertFalse(view.detail().getString().contains("+ 1"));
        assertFalse(view.detail().getString().contains("\"tasks\""));
        var counts = translated(view.fields().getLast(), PREFIX + "quest_counts");
        assertArrayEquals(new Object[]{1, 1, 1}, counts.getArgs());
        assertEquals(QUEST, view.target());
    }

    @Test void structureSummariesResolveTranslatedParentsAndChildCounts() {
        var content = new QuestPublishReviewContent(snapshot(), "zh_cn");
        var chapter = content.change(change("ADDED", "CHAPTER", CHAPTER, "", "", "…"), ignored -> null);
        assertEquals("译名分组", ((Component) translated(chapter.fields().getFirst(), PREFIX + "parent").getArgs()[0]).getString());
        assertArrayEquals(new Object[]{1}, translated(chapter.fields().getLast(), PREFIX + "quests").getArgs());
        var group = content.change(change("ADDED", "CHAPTER_GROUP", GROUP, "", "", "…"), ignored -> null);
        assertArrayEquals(new Object[]{1L}, translated(group.fields().getLast(), PREFIX + "chapters").getArgs());
    }

    @Test void typedEntriesIncludeOwnerFlagsAndLosslessExtensionFieldsInDetails() {
        var content = new QuestPublishReviewContent(snapshot(), "zh_cn");
        var task = content.change(change("ADDED", "TASK", id("task"), "", "", "{\"config\":…"), ignored -> null);
        assertEquals("1", ((Component) translated(task.fields().getFirst(), PREFIX + "parent").getArgs()[0]).getString());
        assertTrue(task.fields().stream().anyMatch(field -> field.getString().contains(FULL_VALUE)));
        assertTrue(task.fields().stream().anyMatch(field -> key(field).equals(PREFIX + "optional")));
        assertFalse(task.detail().getString().contains(FULL_VALUE));
        var reward = content.change(change("ADDED", "REWARD", id("reward"), "", "", "{}"), ignored -> null);
        assertTrue(reward.fields().stream().anyMatch(field -> key(field).equals(PREFIX + "team")));
    }

    @Test void localizationResolvesLegacyAndNativeIdsWithoutSplittingResourceIdOrDots() {
        var content = new QuestPublishReviewContent(snapshot(), "zh_cn");
        for (String alias : List.of(LEGACY, QUEST.toString())) {
            var view = content.change(change("CONFIG_CHANGED", "BOOK", BOOK,
                    "localization.zh_cn.quest." + alias + ".quest_subtitle", "旧副标题", ""), ignored -> null);
            assertEquals(QUEST, view.target());
            assertTrue(view.heading().getString().startsWith("1 · "));
            assertEquals("zh_cn", translated(view.fields().getFirst(), PREFIX + "locale").getArgs()[0]);
            translated(view.fields().get(1), "screen.brnquest.editor.publish.review.diff.path.subtitle");
            var after = translated(view.fields().getLast(), PREFIX + "after");
            translated((Component) after.getArgs()[0], "screen.brnquest.editor.publish.review.diff.empty");
        }
        var diagnostic = content.diagnostic(new EditorPublishReviewModel.Diagnostic("WARN", "BQA-301", QUEST.toString(),
                "localization.ja_jp.title", "Missing localized title"), ignored -> null);
        assertEquals(QUEST, diagnostic.target());
        assertEquals("ja_jp", translated(diagnostic.fields().get(1), PREFIX + "locale").getArgs()[0]);
    }

    @Test void removedAndUnknownObjectsKeepSafeNamesWithoutPuttingBoundedJsonInTheList() {
        var content = new QuestPublishReviewContent(snapshot(), "en_us");
        var removed = content.change(change("REMOVED", "QUEST", id("deleted"), "", "{\"title\":\"Old name\"}", ""), ignored -> null);
        assertTrue(removed.heading().getString().startsWith("Old name · "));
        translated(removed.fields().getFirst(), PREFIX + "removed");
        var unknown = content.change(change("ADDED", "QUEST", id("missing"), "", "", "{\"tasks\":[…"), ignored -> null);
        assertFalse(unknown.detail().getString().contains("\"tasks\""));
        translated(unknown.fields().getFirst(), PREFIX + "unavailable");
    }

    @Test void sectionUsesResolvedTranslationJumpAndDisclosesShortenedTechnicalPreview() {
        var snapshot = snapshot();
        var section = new QuestPublishReviewSection();
        section.open(new EditorPublishReviewModel(true, "r1", snapshot.revision(), 0, 1, false, List.of(),
                List.of(change("CONFIG_CHANGED", "BOOK", BOOK, "localization.zh_cn.quest." + LEGACY + ".title", "", "长文本…"))));
        section.context(snapshot, "zh_cn");
        var screen = new QuestScreenLayout(900, 600, false, false);
        var bounds = EditorPublishReviewPanel.layout(screen).list();
        assertEquals(QUEST, section.click(screen, bounds.left() + 4, bounds.top() + 4, 0).intent().objectId());
        var details = section.click(screen, bounds.right() - 10, bounds.top() + 4, 0);
        assertEquals(QuestPublishReviewSection.Action.DETAILS, details.intent().action());
        assertTrue(section.detailLines().stream().anyMatch(field -> key(field).equals(PREFIX + "shortened")));
        // A different snapshot must never supply apparently authoritative counts for this review.
        section.context(null, "zh_cn");
        assertEquals(BOOK, section.click(screen, bounds.left() + 4, bounds.top() + 4, 0).intent().objectId());
    }

    private static QuestBookSnapshot snapshot() {
        var task = new TaskDefinition(BOOK, id("task"), id("extension_task"), Map.of("count", "7", "extra", FULL_VALUE), true);
        var reward = new RewardDefinition(BOOK, id("reward"), id("extension_reward"), Map.of("count", "3"), "manual", true);
        var quest = new QuestDefinition(BOOK, QUEST, CHAPTER, "1", "", "", "", 0, 0,
                List.of(id("prerequisite")), List.of(task), List.of(reward), LEGACY);
        var group = new ChapterGroupDefinition(BOOK, GROUP, "Group", 0);
        var chapter = new ChapterDefinition(BOOK, CHAPTER, GROUP, "Chapter", "", 0, List.of(quest));
        var localization = new BookLocalization("en_us", Map.of("zh_cn", Map.of("chapter_group.old-group.title", "译名分组")));
        return QuestBookSnapshot.of(new QuestBookDefinition(BOOK, 1, "Book", List.of(group), List.of(chapter),
                Map.of("old-group", GROUP, LEGACY, QUEST), localization, Map.of()));
    }
    private static EditorPublishReviewModel.Change change(String kind, String objectKind, ResourceLocation id,
                                                           String path, String before, String after) {
        return new EditorPublishReviewModel.Change(kind, objectKind, id.toString(), path, before, after);
    }
    private static TranslatableContents translated(Component field, String expected) {
        var contents = assertInstanceOf(TranslatableContents.class, field.getContents());
        assertEquals(expected, contents.getKey());
        return contents;
    }
    private static String key(Component field) { return field.getContents() instanceof TranslatableContents text ? text.getKey() : ""; }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("review", path); }
}
