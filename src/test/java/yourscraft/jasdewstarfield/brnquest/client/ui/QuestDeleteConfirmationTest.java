package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises confirmation with the same click geometry and target data used by the screen. */
class QuestDeleteConfirmationTest {
    private static final ResourceLocation BOOK = id("book");
    private static final ResourceLocation GROUP = id("group");
    private static final ResourceLocation CHAPTER = id("chapter");
    private static final ResourceLocation QUEST = id("quest");
    private static final ResourceLocation LOCKED = CanvasSelectionKey.decoration(id("locked"));
    private static final ResourceLocation FREE = CanvasSelectionKey.decoration(id("free"));
    private static final ResourceLocation NODE = CanvasSelectionKey.quest(QUEST);
    private static final EditorSessionLifecycle.Scope SCOPE = new EditorSessionLifecycle.Scope(UUID.randomUUID(), BOOK, true);
    private static final QuestScreenLayout LAYOUT = new QuestScreenLayout(1280, 720, false, false);
    private static final String REVISION = "draft-one";

    @Test
    void mixedSelectionFreezesIdsAndChapterAndExcludesLockedDecorations() {
        var dialog = new QuestDeleteConfirmation();
        var book = book();
        Set<ResourceLocation> selection = new HashSet<>(Set.of(LOCKED, FREE, NODE));
        assertTrue(dialog.openSelection(SCOPE, REVISION, book, book.chapters().getFirst(), selection));
        selection.clear();

        var confirm = EditorConfirmDialog.layout(LAYOUT).confirm();
        var intent = dialog.click(LAYOUT, SCOPE, REVISION, confirm.left() + 1, confirm.top() + 1, 0).orElseThrow();

        assertEquals(QuestDeleteConfirmation.Kind.CANVAS_SELECTION, intent.kind());
        assertEquals(CHAPTER, intent.target());
        assertEquals(Set.of(FREE, NODE), intent.selection());
        assertFalse(dialog.isOpen());
        assertTrue(dialog.click(LAYOUT, SCOPE, REVISION, confirm.left() + 1, confirm.top() + 1, 0).isEmpty());
    }

    @Test
    void lockedOnlySelectionDoesNotOpenAConfirmation() {
        var dialog = new QuestDeleteConfirmation();
        var book = book();
        assertFalse(dialog.openSelection(SCOPE, REVISION, book, book.chapters().getFirst(), Set.of(LOCKED)));
        assertFalse(dialog.isOpen());
    }

    @Test
    void singleTargetKeepsItsKindAndCancelNeverReturnsAnIntent() {
        var dialog = new QuestDeleteConfirmation();
        var confirm = EditorConfirmDialog.layout(LAYOUT).confirm();
        var cancel = EditorConfirmDialog.layout(LAYOUT).cancel();

        assertTrue(dialog.openTarget(SCOPE, REVISION, book(), QuestDeleteConfirmation.Kind.GROUP, GROUP));
        var intent = dialog.click(LAYOUT, SCOPE, REVISION, confirm.left() + 1, confirm.top() + 1, 0).orElseThrow();
        assertEquals(QuestDeleteConfirmation.Kind.GROUP, intent.kind());
        assertEquals(GROUP, intent.target());
        assertTrue(intent.selection().isEmpty());

        assertTrue(dialog.openTarget(SCOPE, REVISION, book(), QuestDeleteConfirmation.Kind.QUEST, QUEST));
        assertTrue(dialog.click(LAYOUT, SCOPE, REVISION, cancel.left() + 1, cancel.top() + 1, 0).isEmpty());
        assertFalse(dialog.isOpen());
    }

    @Test
    void changedSessionBookPermissionOrRevisionInvalidatesPendingConfirmation() {
        var dialog = new QuestDeleteConfirmation();
        var confirm = EditorConfirmDialog.layout(LAYOUT).confirm();
        for (var current : List.of(
                new EditorSessionLifecycle.Scope(UUID.randomUUID(), BOOK, true),
                new EditorSessionLifecycle.Scope(SCOPE.session(), id("other_book"), true),
                new EditorSessionLifecycle.Scope(SCOPE.session(), BOOK, false))) {
            assertTrue(dialog.openTarget(SCOPE, REVISION, book(), QuestDeleteConfirmation.Kind.QUEST, QUEST));
            assertTrue(dialog.click(LAYOUT, current, REVISION, confirm.left() + 1, confirm.top() + 1, 0).isEmpty());
            assertFalse(dialog.isOpen());
        }
        assertTrue(dialog.openTarget(SCOPE, REVISION, book(), QuestDeleteConfirmation.Kind.QUEST, QUEST));
        assertTrue(dialog.click(LAYOUT, SCOPE, "draft-two", confirm.left() + 1, confirm.top() + 1, 0).isEmpty());
        assertFalse(dialog.isOpen());
    }

    private static QuestBookDefinition book() {
        var quest = new QuestDefinition(BOOK, QUEST, CHAPTER, "Quest", "", "", "", 0, 0,
                List.of(), List.of(), List.of(), "");
        var dependent = new QuestDefinition(BOOK, id("dependent"), CHAPTER, "Dependent", "", "", "", 1, 0,
                List.of(QUEST), List.of(), List.of(), "");
        var locked = new CanvasScene.Decoration(id("locked"), "minecraft:textures/block/stone.png", 0, 0, 1, 1, true, 0, true);
        var free = new CanvasScene.Decoration(id("free"), "minecraft:textures/block/stone.png", 0, 0, 1, 1, true, 0, false);
        var scene = new CanvasScene(List.of(locked, free), null, null);
        var chapter = new ChapterDefinition(BOOK, CHAPTER, GROUP, "Chapter", "", 0,
                List.of(quest, dependent), scene.write(Map.of()));
        return new QuestBookDefinition(BOOK, 1, "Book", List.of(new ChapterGroupDefinition(BOOK, GROUP, "Group", 0)),
                List.of(chapter), Map.of());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
