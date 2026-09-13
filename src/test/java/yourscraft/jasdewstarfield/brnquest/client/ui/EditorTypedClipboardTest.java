package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EditorTypedClipboardTest {
    @Test void clipboardIsReusableAndScopedByWorldBookAndKind() {
        var book = ResourceLocation.parse("test:book");
        var snapshot = new TypedEntrySnapshot(book, true, ResourceLocation.parse("test:type"), Map.of("extra", "keep"), true, "manual", false);
        EditorTypedClipboard.copy("server-a", snapshot);
        assertEquals(snapshot, EditorTypedClipboard.get("server-a", book, true).orElseThrow());
        assertEquals(snapshot, EditorTypedClipboard.get("server-a", book, true).orElseThrow());
        assertTrue(EditorTypedClipboard.get("server-b", book, true).isEmpty());
        assertTrue(EditorTypedClipboard.get("server-a", ResourceLocation.parse("test:other"), true).isEmpty());
        assertTrue(EditorTypedClipboard.get("server-a", book, false).isEmpty());
    }
}
