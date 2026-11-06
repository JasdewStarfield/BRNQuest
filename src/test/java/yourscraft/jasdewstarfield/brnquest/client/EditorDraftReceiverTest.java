package yourscraft.jasdewstarfield.brnquest.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EditorDraftReceiverTest {
    @Test void outOfOrderAndDuplicateChunksInstallOnlyOneVerifiedSnapshot() {
        var snapshot = QuestBookSnapshot.of(new QuestBookDefinition(ResourceLocation.parse("test:receiver"),
                1, "接收顺序", List.of(), List.of(), Map.of()));
        var receiver = new EditorDraftReceiver();
        UUID session = UUID.randomUUID();
        String json = NativeBookJson.encode(snapshot.book());
        int split = json.length() / 2;
        receiver.begin(session, snapshot.book().id(), snapshot.revision(), 2,
                json.getBytes(StandardCharsets.UTF_8).length);
        assertFalse(receiver.accept(UUID.randomUUID().toString(), snapshot.revision(), 0, "{}").accepted());
        assertNull(receiver.accept(session.toString(), snapshot.revision(), 1, json.substring(split)).snapshot());
        assertNull(receiver.accept(session.toString(), snapshot.revision(), 1, json.substring(split)).snapshot());
        assertEquals(snapshot.revision(), receiver.accept(session.toString(), snapshot.revision(), 0,
                json.substring(0, split)).snapshot().revision());
        assertFalse(receiver.accept(session.toString(), snapshot.revision(), 0, json).accepted());
    }

    @Test void clearDiscardsPartialTransferBeforeAnotherSessionStarts() {
        var receiver = new EditorDraftReceiver();
        UUID session = UUID.randomUUID();
        receiver.begin(session, ResourceLocation.parse("test:receiver"), "revision", 2, 4);
        assertTrue(receiver.accept(session.toString(), "revision", 0, "ab").accepted());
        receiver.clear();
        assertFalse(receiver.accept(session.toString(), "revision", 1, "cd").accepted());
    }
}
