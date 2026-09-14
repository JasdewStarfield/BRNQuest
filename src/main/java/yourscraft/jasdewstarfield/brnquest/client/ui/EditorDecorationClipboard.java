package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import java.util.Objects;
import java.util.Optional;

/** Same-world, same-book immutable artwork snapshot, deliberately separate from native text copy/paste. */
final class EditorDecorationClipboard {
    private static String context;
    private static ResourceLocation book;
    private static CanvasScene.Decoration value;
    static void copy(String world, ResourceLocation bookId, CanvasScene.Decoration decoration) {
        // Both the property editor and canvas shortcuts replace the same object clipboard.
        EditorQuestClipboard.copy(world, yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot.artwork(bookId, decoration));
        context = world; book = bookId; value = decoration;
    }
    static Optional<CanvasScene.Decoration> get(String world, ResourceLocation bookId) {
        return Objects.equals(context, world) && Objects.equals(book, bookId) ? Optional.ofNullable(value) : Optional.empty();
    }
    static void clear() { context = null; book = null; value = null; }
    private EditorDecorationClipboard() {}
}
