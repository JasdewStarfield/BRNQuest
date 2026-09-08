package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BookTransferBudgetTest {
    @Test void budgetMatchesUtf8BodyAndActualChunkSplitting() {
        var book = new QuestBookDefinition(ResourceLocation.parse("test:budget"), 1, "中文".repeat(20000), List.of(), List.of(), Map.of());
        var snapshot = QuestBookSnapshot.of(book);
        var budget = BrnQuestNetwork.bookTransferBudget(snapshot);
        String json = NativeBookJson.encode(book);
        assertEquals(json.getBytes(StandardCharsets.UTF_8).length, budget.encodedBytes());
        assertEquals(BrnQuestNetwork.split(json, BrnQuestNetwork.BOOK_CHUNK_CHARACTERS).size(), budget.chunks());
        assertTrue(budget.withinLimits());
        assertEquals(budget, BrnQuestNetwork.bookTransferBudget(snapshot));
    }
}
