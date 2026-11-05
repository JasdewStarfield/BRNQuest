package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Stable service port for book workflows; wire records stay in the screen's protocol adapter. */
interface EditorBookTransport {
    void catalog();
    void open(ResourceLocation book, String revision);
    void openCurrent(ResourceLocation book, String activeRevision, String draftRevision, boolean replace);
    void version(ResourceLocation book, String revision);
    void versions(ResourceLocation book);
    void restore(ResourceLocation book, String backup, String revision);
    void close(UUID session, String revision);
    void publish(UUID session, ResourceLocation book, String revision, String workspaceRevision, String activeBook, String activeRevision);
}
