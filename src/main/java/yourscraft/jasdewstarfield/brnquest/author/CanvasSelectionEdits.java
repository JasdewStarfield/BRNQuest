package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;

/** Builds one immutable candidate for mixed operations, then lets the session validate and commit it atomically. */
public final class CanvasSelectionEdits {
    private CanvasSelectionEdits() {}
    public static AuthorOperationResult<DraftChange> edit(QuestBookDefinition book, ResourceLocation chapterId,
            String action, Map<ResourceLocation, DraftBookEditor.Position> positions, double dx, double dy) {
        var chapter = book.chapters().stream().filter(c -> c.id().equals(chapterId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Chapter no longer exists"));
        var quests = CanvasSelectionKey.ids(positions.keySet(), false);
        var decorations = CanvasSelectionKey.ids(positions.keySet(), true);
        // Resolve every ID before changing anything, including the decoration-only case.
        var captured = QuestClipboardSnapshot.capture(book, chapterId, quests, decorations);
        if (action.equals("COPY_CANVAS_SELECTION")) return captured.paste(book, chapterId, captured.minX()+dx, captured.minY()+dy);
        if (!action.equals("MOVE_CANVAS_SELECTION") && !action.equals("DELETE_CANVAS_SELECTION"))
            throw new IllegalArgumentException("Unknown canvas selection operation");
        if (chapter.canvasScene().decorations().stream().anyMatch(d -> decorations.contains(d.id()) && d.locked()))
            throw new IllegalArgumentException("Locked artwork cannot be moved or deleted");
        boolean move = action.equals("MOVE_CANVAS_SELECTION");
        var result = book;
        var affected = new ArrayList<ResourceLocation>();
        if (!quests.isEmpty()) {
            Map<ResourceLocation, DraftBookEditor.Position> nodes = new LinkedHashMap<>();
            quests.forEach(id -> nodes.put(id, positions.get(CanvasSelectionKey.quest(id))));
            var changed = move ? DraftBookEditor.updateQuestPositions(result, nodes) : DraftBookEditor.removeQuestSelection(result, quests);
            if (!changed.success()) return changed;
            result = changed.value().book(); affected.addAll(changed.value().affectedObjects());
        }
        if (!decorations.isEmpty()) {
            var scene = chapter.canvasScene();
            var artwork = new ArrayList<CanvasScene.Decoration>();
            for (var d : scene.decorations()) {
                if (!decorations.contains(d.id())) artwork.add(d);
                else if (move) {
                    var point = positions.get(CanvasSelectionKey.decoration(d.id()));
                    artwork.add(d.placed(d.id(), point.x(), point.y(), d.width(), d.height()));
                }
            }
            var changed = CanvasEdits.replace(result, chapterId, new CanvasScene(artwork, scene.canvas(), scene.screen(), scene.screenAbove()));
            if (!changed.success()) return changed;
            result = changed.value().book(); affected.addAll(changed.value().affectedObjects());
        }
        return AuthorOperationResult.success("CANVAS_SELECTION_UPDATED", "Canvas selection updated", new DraftChange(result, affected));
    }
}
