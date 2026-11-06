package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Receives one manifest-bound draft and exposes content only after complete verification. */
final class EditorDraftReceiver {
    record Result(boolean accepted, QuestBookSnapshot snapshot) {}
    private UUID session;
    private ResourceLocation bookId;
    private String revision = "";
    private String[] chunks = new String[0];
    private int expectedBytes;
    private int receivedChunks;

    void begin(UUID session, ResourceLocation bookId, String revision, int count, int bytes) {
        this.session = session;
        this.bookId = bookId;
        this.revision = revision;
        chunks = new String[count];
        expectedBytes = bytes;
        receivedChunks = 0;
    }

    Result accept(String rawSession, String revision, int index, String data) {
        if (session == null || !session.toString().equals(rawSession) || !this.revision.equals(revision)
                || index < 0 || index >= chunks.length || data == null
                || data.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_BOOK_CHUNK_BYTES) {
            return new Result(false, null);
        }
        // Repeated chunks acknowledge the same slot without advancing completion twice.
        if (chunks[index] == null) {
            chunks[index] = data;
            receivedChunks++;
        }
        if (receivedChunks != chunks.length) return new Result(true, null);
        String json = String.join("", chunks);
        if (json.getBytes(StandardCharsets.UTF_8).length != expectedBytes) {
            throw new IllegalArgumentException("Draft byte count does not match its manifest");
        }
        var book = NativeBookJson.decode(JsonParser.parseString(json).getAsJsonObject());
        var candidate = QuestBookSnapshot.of(book);
        if (!book.id().equals(bookId) || !candidate.revision().equals(revision)
                || candidate.quests().size() > BrnQuestConstants.MAX_QUESTS) {
            throw new IllegalArgumentException("Draft identity or revision does not match its manifest");
        }
        clear();
        return new Result(true, candidate);
    }

    void clear() {
        session = null;
        bookId = null;
        revision = "";
        chunks = new String[0];
        expectedBytes = 0;
        receivedChunks = 0;
    }
}
