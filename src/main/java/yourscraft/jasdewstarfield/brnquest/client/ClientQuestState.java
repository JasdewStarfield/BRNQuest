package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client cache accepts a book only after manifest limits, order, size, and revision all match. */
public final class ClientQuestState {
    private static final ClientQuestState INSTANCE = new ClientQuestState();
    private static final Gson GSON = new Gson();
    private QuestBookSnapshot book;
    private String expectedRevision = "";
    private int expectedChunks;
    private int expectedBytes;
    private final Map<Integer, String> chunks = new HashMap<>();
    private Map<String, QuestStatus> statuses = Map.of();
    private Set<String> claimed = Set.of();
    private ResourceLocation selected;

    private ClientQuestState() {}
    public static ClientQuestState get() { return INSTANCE; }
    public Optional<QuestBookSnapshot> book() { return Optional.ofNullable(book); }
    public Map<String, QuestStatus> statuses() { return statuses; }
    public Set<String> claimed() { return claimed; }
    public ResourceLocation selected() { return selected; }
    public void selected(ResourceLocation selected) { this.selected = selected; }
    public String revision() { return book == null ? "" : book.revision(); }

    public void begin(String revision, int chunkCount, int decodedBytes) {
        if (chunkCount < 1 || decodedBytes < 0 || decodedBytes > BrnQuestConstants.MAX_BOOK_BYTES) throw new IllegalArgumentException("Unsafe book manifest");
        expectedRevision = revision;
        expectedChunks = chunkCount;
        expectedBytes = decodedBytes;
        chunks.clear();
    }

    public boolean acceptChunk(String revision, int index, String data) {
        if (!expectedRevision.equals(revision) || index < 0 || index >= expectedChunks || data.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_BOOK_CHUNK_BYTES) return false;
        chunks.putIfAbsent(index, data);
        if (chunks.size() != expectedChunks) return false;
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < expectedChunks; i++) json.append(chunks.get(i));
        if (json.toString().getBytes(StandardCharsets.UTF_8).length != expectedBytes) return false;
        QuestBookSnapshot candidate = QuestBookSnapshot.of(NativeBookJson.decode(JsonParser.parseString(json.toString()).getAsJsonObject()));
        if (!candidate.revision().equals(expectedRevision) || candidate.book().quests().size() > BrnQuestConstants.MAX_QUESTS) return false;
        book = candidate;
        chunks.clear();
        return true;
    }

    public void progress(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_PROGRESS_BYTES) return;
        BrnQuestNetwork.ProgressWire wire = GSON.fromJson(json, BrnQuestNetwork.ProgressWire.class);
        statuses = wire.quests() == null ? Map.of() : Map.copyOf(wire.quests());
        claimed = wire.claimed() == null ? Set.of() : Set.copyOf(wire.claimed());
    }

    public Optional<ResourceLocation> trackedQuest() {
        return statuses.entrySet().stream().filter(e -> e.getValue() == QuestStatus.ACTIVE).map(e -> ResourceLocation.tryParse(e.getKey())).filter(Objects::nonNull).findFirst();
    }
}
