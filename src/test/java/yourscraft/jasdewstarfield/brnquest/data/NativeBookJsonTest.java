package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NativeBookJsonTest {
    @Test void roundTripIsDeterministicAndImmutable() {
        ResourceLocation bookId = ResourceLocation.parse("test:main");
        ResourceLocation chapterId = ResourceLocation.parse("test:intro");
        TaskDefinition task = new TaskDefinition(bookId, ResourceLocation.parse("test:task"), ResourceLocation.parse("brnquest:checkmark"), Map.of(), false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId, "Quest", "{id:\"minecraft:book\",count:1}", 1, 2, List.of(), List.of(task), List.of(), "ABCDEF0123456789");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book", List.of(), List.of(new ChapterDefinition(bookId, chapterId, ResourceLocation.parse("test:group"), "Intro", "", 0, List.of(quest))), Map.of("ABCDEF0123456789", quest.id()));
        String encoded = NativeBookJson.encode(book);
        QuestBookDefinition decoded = NativeBookJson.decode(JsonParser.parseString(encoded).getAsJsonObject());
        assertEquals(encoded, NativeBookJson.encode(decoded));
        assertEquals(QuestBookSnapshot.of(book).revision(), QuestBookSnapshot.of(decoded).revision());
        var codecJson = QuestDefinition.CODEC.encodeStart(JsonOps.INSTANCE, quest).getOrThrow();
        assertEquals(quest, QuestDefinition.CODEC.parse(JsonOps.INSTANCE, codecJson).getOrThrow());
        assertThrows(UnsupportedOperationException.class, () -> decoded.chapters().add(null));
    }
}
