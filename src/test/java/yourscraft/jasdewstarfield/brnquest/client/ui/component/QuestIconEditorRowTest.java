package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestIconEditorRowTest {
    @Test
    void everyControlMovesWithItsOwningRow() {
        QuestIconEditorRow.Layout original = QuestIconEditorRow.layout(200, 100, 300, 48);
        QuestIconEditorRow.Layout shifted = QuestIconEditorRow.layout(200, 166, 300, 48);

        assertEquals(66, shifted.mode().top() - original.mode().top());
        assertEquals(66, shifted.input().top() - original.input().top());
        assertEquals(66, shifted.picker().top() - original.picker().top());
        assertTrue(shifted.mode().right() < shifted.input().left());
        assertTrue(shifted.input().right() < shifted.picker().left());
    }
}
