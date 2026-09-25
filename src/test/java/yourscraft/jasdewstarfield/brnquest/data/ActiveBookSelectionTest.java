package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActiveBookSelectionTest {
    @Test void publishedChoiceWinsOverLexicalOrderAndMissingChoiceFallsBack() {
        ResourceLocation alpha = ResourceLocation.parse("test:alpha");
        ResourceLocation zeta = ResourceLocation.parse("test:zeta");
        var books = Map.of(alpha, JsonParser.parseString("{}"), zeta, JsonParser.parseString("{}"));

        assertEquals(zeta, ActiveBookSelection.decode(JsonParser.parseString(ActiveBookSelection.encode(zeta))));
        assertEquals(zeta, QuestBookReloadListener.selectBook(books, zeta).getKey());
        assertNull(QuestBookReloadListener.selectBook(books, ResourceLocation.parse("test:missing")));
        assertEquals(alpha, QuestBookReloadListener.selectBook(books, null).getKey());
    }
}
