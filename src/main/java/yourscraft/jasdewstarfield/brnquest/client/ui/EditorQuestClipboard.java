package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot;
import java.util.Optional;

/** World/server and book scoped clipboard, independent of the operating system text clipboard. */
final class EditorQuestClipboard {
    private static String context;
    private static QuestClipboardSnapshot value;
    private EditorQuestClipboard() {}
    static void copy(String serverContext, QuestClipboardSnapshot snapshot) {
        snapshot.encode(); // An oversized selection must not erase the previous usable clipboard.
        context = serverContext; value = snapshot;
    }
    static Optional<QuestClipboardSnapshot> get(String serverContext, ResourceLocation book) {
        return value != null && java.util.Objects.equals(context, serverContext) && value.content().id().equals(book)
                ? Optional.of(value) : Optional.empty();
    }
}
