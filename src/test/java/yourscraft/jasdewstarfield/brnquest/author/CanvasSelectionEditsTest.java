package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Mixed edits preserve geometry, identity separation, snapshot isolation and all-or-nothing failure. */
class CanvasSelectionEditsTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:"+path); }
    private static QuestBookDefinition book(boolean locked) {
        var q = new QuestDefinition(id("book"), id("same"), id("c"), "Q", "", "", "", 2, 3, List.of(), List.of(), List.of(), "");
        var dependent = new QuestDefinition(id("book"), id("dependent"), id("c"), "D", "", "", "", 4, 5, List.of(q.id()), List.of(), List.of(), "");
        var art = new CanvasScene.Decoration(id("art"), "brnquest_local:textures/imported/test.png", -2, -1, 2, 1, true, 7, locked);
        var scene = new CanvasScene(List.of(art), new CanvasScene.Background("", CanvasScene.Fit.TILE, 1), null, true);
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("g"), "G", 0)),
                List.of(new ChapterDefinition(id("book"), id("c"), id("g"), "C", "", 0, List.of(q, dependent), scene.write(Map.of("private", "keep"))),
                        new ChapterDefinition(id("book"), id("dest"), id("g"), "Dest", "", 1, List.of())), Map.of());
    }
    private static Map<ResourceLocation, DraftBookEditor.Position> selected() {
        return Map.of(CanvasSelectionKey.quest(id("same")), new DraftBookEditor.Position(5, 7),
                CanvasSelectionKey.decoration(id("art")), new DraftBookEditor.Position(1, 3));
    }
    @Test void mixedMoveAndDeleteRespectTypedIdentityAndPreserveChapterMetadata() {
        var original = book(false);
        var moved = CanvasSelectionEdits.edit(original, id("c"), "MOVE_CANVAS_SELECTION", selected(), 0, 0);
        assertTrue(moved.success(), moved.message());
        var c = moved.value().book().chapters().getFirst();
        assertEquals(5, c.quests().getFirst().x()); assertEquals(1, c.canvasScene().decorations().getFirst().x());
        assertEquals(2, c.canvasScene().decorations().getFirst().width()); assertEquals(7, c.canvasScene().decorations().getFirst().layer());
        assertEquals("keep", c.extensions().get("private")); assertEquals(original.chapters().getFirst().canvasScene().canvas(), c.canvasScene().canvas());
        var deleted = CanvasSelectionEdits.edit(original, id("c"), "DELETE_CANVAS_SELECTION", selected(), 0, 0).value().book();
        assertTrue(deleted.chapters().getFirst().canvasScene().decorations().isEmpty()); assertEquals(1, deleted.quests().size());
        assertTrue(deleted.quests().getFirst().dependencies().isEmpty()); assertEquals(2, original.quests().size());
    }
    @Test void frozenMixedAndArtworkOnlyClipboardSurviveSourceDeletionAndPreserveOffsets() {
        var original = book(false);
        var frozen = QuestClipboardSnapshot.decode(QuestClipboardSnapshot.capture(original, id("c"), Set.of(id("same")), Set.of(id("art"))).encode());
        var deleted = CanvasSelectionEdits.edit(original, id("c"), "DELETE_CANVAS_SELECTION", selected(), 0, 0).value().book();
        var pasted = frozen.paste(deleted, id("dest"), 10, 20).value().book().chapters().get(1);
        assertEquals(14, pasted.quests().getFirst().x()); assertEquals(24, pasted.quests().getFirst().y());
        var art = pasted.canvasScene().decorations().getFirst();
        assertEquals(10, art.x()); assertEquals(20, art.y()); assertEquals(2, art.width());
        assertNotEquals(id("same"), art.id()); assertNotEquals(id("same"), pasted.quests().getFirst().id());
        assertNull(pasted.canvasScene().canvas()); assertNull(pasted.canvasScene().screenAbove());
        var only = QuestClipboardSnapshot.decode(QuestClipboardSnapshot.capture(original, id("c"), Set.of(), Set.of(id("art"))).encode());
        var result = only.paste(deleted, id("dest"), 0, 0).value().book();
        assertEquals(1, result.quests().size()); assertEquals(1, result.chapters().get(1).canvasScene().decorations().size());
    }
    @Test void lockedMissingCrossChapterAndInvalidCoordinateRequestsDoNotPartiallyEdit() {
        var locked = book(true);
        for (String action : List.of("MOVE_CANVAS_SELECTION", "DELETE_CANVAS_SELECTION"))
            assertThrows(IllegalArgumentException.class, () -> CanvasSelectionEdits.edit(locked, id("c"), action, selected(), 0, 0));
        assertTrue(CanvasSelectionEdits.edit(locked, id("c"), "COPY_CANVAS_SELECTION", selected(), 1, 1).success());
        assertThrows(IllegalArgumentException.class, () -> CanvasSelectionEdits.edit(book(false), id("dest"), "MOVE_CANVAS_SELECTION", selected(), 0, 0));
        var invalid = new HashMap<>(selected()); invalid.put(CanvasSelectionKey.decoration(id("art")), new DraftBookEditor.Position(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> CanvasSelectionEdits.edit(book(false), id("c"), "MOVE_CANVAS_SELECTION", invalid, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> CanvasSelectionKey.id(id("same")));
        assertEquals(2, locked.quests().getFirst().x());
    }
    @Test void capacityFailureDoesNotPublishPartialQuestCopies() {
        var original = book(false); var scene = original.chapters().get(1).canvasScene();
        var art = original.chapters().getFirst().canvasScene().decorations().getFirst();
        var full = java.util.stream.IntStream.range(0, CanvasScene.MAX_DECORATIONS).mapToObj(i -> art.placed(id("art_"+i), 0, 0, 2, 1)).toList();
        original = CanvasEdits.replace(original, id("dest"), new CanvasScene(full, scene.canvas(), scene.screen())).value().book();
        var copy = QuestClipboardSnapshot.capture(original, id("c"), Set.of(id("same")), Set.of(id("art")));
        assertFalse(copy.paste(original, id("dest"), 0, 0).success());
        assertTrue(original.chapters().get(1).quests().isEmpty());
    }
}
