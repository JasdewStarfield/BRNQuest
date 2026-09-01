package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
