package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Snapshots retain opaque values while limiting the untrusted clipboard envelope. */
class TypedEntrySnapshotTest {
    private static ResourceLocation id(String value) { return ResourceLocation.parse("test:" + value); }
    @Test void immutableRoundTripRetainsNestedAndLocalizedConfigAndExplicitFlags() {
        var mutable = new HashMap<>(Map.of("title.zh_cn", "奖励", "title.en_us", "Reward", "table", "{\"children\":[{\"weight\":2}]}", "private_ref", "test:old"));
        var original = new TypedEntrySnapshot(id("book"), false, id("addon"), mutable, false, "auto_hidden", true);
        mutable.clear();
        var decoded = TypedEntrySnapshot.decode(original.encode());
        assertEquals(original, decoded);
        assertEquals("test:old", decoded.config().get("private_ref"));
        assertEquals(decoded.config(), decoded.reward(id("new")).config());
        assertTrue(decoded.reward(id("new")).teamReward());
        assertEquals("auto_hidden", decoded.reward(id("new")).claimPolicy());
        assertThrows(UnsupportedOperationException.class, () -> decoded.config().clear());
        var task = new TypedEntrySnapshot(id("book"), true, id("task"), Map.of(), true, "manual", false);
        assertTrue(TypedEntrySnapshot.decode(task.encode()).task(id("copy")).optional());
    }
    @Test void rejectsCrossBookWrongKindAndMalformedValues() {
        var value = new TypedEntrySnapshot(id("book"), true, id("task"), Map.of(), false, "manual", false);
        assertThrows(IllegalArgumentException.class, () -> value.requireDestination(id("other"), true));
        assertThrows(IllegalArgumentException.class, () -> value.requireDestination(id("book"), false));
        for (String invalid : List.of("null", "[]", "{}", value.encode().replace("false", "\"false\""),
                value.encode().replace("\"config\":{}", "\"config\":{\"x\":7}"), " ".repeat(65537)))
            assertThrows(RuntimeException.class, () -> TypedEntrySnapshot.decode(invalid));
        assertThrows(IllegalArgumentException.class, () -> new TypedEntrySnapshot(id("book"), true, id("task"),
                Map.of("data", "a".repeat(65536)), false, "manual", false).encode());
    }
}
