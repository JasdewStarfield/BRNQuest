package yourscraft.jasdewstarfield.brnquest.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestIdsTest {
    @Test void normalizesLegacyIdsWithoutUsingDisplayText() {
        assertEquals("embers_of_winter:legacy/7d44928441162c4f", QuestIds.normalize("embers_of_winter", "7D44928441162C4F").toString());
        assertTrue(QuestIds.isLegacy("7D44928441162C4F"));
    }

    @Test void rejectsInvalidNativeIds() { assertThrows(IllegalArgumentException.class, () -> QuestIds.normalize("test", "Not a native id")); }
}
