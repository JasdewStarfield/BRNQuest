package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorLocalizedQuestTextScreenTest {
    @Test
    void acceptsMinecraftLocaleCodesAndRejectsPathsOrTranslationKeys() {
        assertTrue(EditorLocalizedQuestTextScreen.validLocaleCode("zh_cn"));
        assertTrue(EditorLocalizedQuestTextScreen.validLocaleCode("en-US"));
        assertTrue(EditorLocalizedQuestTextScreen.validLocaleCode("tok"));
        assertFalse(EditorLocalizedQuestTextScreen.validLocaleCode(""));
        assertFalse(EditorLocalizedQuestTextScreen.validLocaleCode("../zh_cn"));
        assertFalse(EditorLocalizedQuestTextScreen.validLocaleCode("quest.title"));
    }

    @Test
    void mapsParserOffsetsToOneBasedLineAndColumn() {
        assertArrayEquals(new int[]{2, 3},
                EditorLocalizedQuestTextScreen.diagnosticPosition("first\n中文", 8));
        assertArrayEquals(new int[]{1, 1},
                EditorLocalizedQuestTextScreen.diagnosticPosition("text", -20));
    }
}
