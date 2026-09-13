package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot;
import java.util.Optional;

/** Process-local application clipboard, scoped to the server/world and book; never touches OS text. */
final class EditorTypedClipboard {
    private static String context;
    private static TypedEntrySnapshot value;
    private EditorTypedClipboard() {}
    static void copy(String serverContext, TypedEntrySnapshot snapshot) {
        snapshot.encode(); // Reject oversized copies before replacing the previous usable clipboard.
        context = serverContext;
        value = snapshot;
    }
    static Optional<TypedEntrySnapshot> get(String serverContext, ResourceLocation book, boolean task) {
        return value != null && java.util.Objects.equals(context, serverContext)
                && value.bookId().equals(book) && value.task() == task ? Optional.of(value) : Optional.empty();
    }
}
