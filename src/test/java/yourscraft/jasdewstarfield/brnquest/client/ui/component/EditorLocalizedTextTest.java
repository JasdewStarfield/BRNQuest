package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EditorLocalizedTextTest {
    @Test void switchingAndAddingLanguagesRetainsOnlyActualEdits() {
        var source = new BookLocalization("en_us", Map.of("en_us", Map.of("title", "English")));
        var model = new EditorLocalizedText(source, "title", "Native", "zh_cn");
        assertEquals("zh_cn", model.locale());
        assertEquals("", model.value());
        assertEquals("English", model.placeholder());
        assertEquals("en_us", model.source());
        model.remember("Chinese");
        model.select("en_us");
        model.remember("New English");
        model.select("ja-JP");
        model.remember("Japanese");
        model.select("zh_cn");
        assertEquals("Chinese", model.value());
        assertEquals(Map.of("zh_cn", "Chinese", "en_us", "New English", "ja_jp", "Japanese"), model.changes());
        assertEquals("English", source.resolve("en_us", "title", ""), "Unconfirmed drafts do not mutate the source");
    }
    @Test void browsingAndRevertingDoNotCreateOverrides() {
        var model = new EditorLocalizedText(BookLocalization.EMPTY, "title", "Native", "zh_cn");
        model.remember(model.value());
        model.select("fr_fr");
        model.remember("Changed");
        model.remember("");
        assertTrue(model.changes().isEmpty());
        assertEquals("", model.source());
    }
    @Test void untranslatedLanguagesPreviewThePendingDefaultWithoutCreatingCopies() {
        var model = new EditorLocalizedText(BookLocalization.EMPTY, "title", "Native", "en_us");
        model.remember("New default");
        model.select("zh_cn");
        assertEquals("", model.value());
        assertEquals("New default", model.placeholder());
        assertEquals("en_us", model.source());
        model.remember(model.value());
        assertEquals(Map.of("en_us", "New default"), model.changes());
    }
    @Test void explicitTranslationMatchingFallbackStillCreatesItsOwnText() {
        var model = new EditorLocalizedText(BookLocalization.EMPTY, "title", "Native", "zh_cn");
        model.remember("Native");
        assertEquals(Map.of("zh_cn", "Native"), model.changes());
        model.select("en_us");
        assertEquals("Native", model.value(), "The canonical default remains editable in its own locale");
        assertEquals("", model.placeholder());
        model.select("zh_cn");
        assertEquals("Native", model.value());
    }

    @Test void localeCodesAreNormalizedWithoutAcceptingPaths() {
        var model = new EditorLocalizedText(BookLocalization.EMPTY, "title", "", "en_us");
        model.select(" ZH-CN ");
        assertEquals("zh_cn", model.locale());
        assertFalse(EditorLocalizedText.validLocale("../en_us"));
        assertFalse(EditorLocalizedText.validLocale(""));
        assertThrows(IllegalArgumentException.class, () -> model.select("quest.title"));
    }
    @Test void languageButtonAndTextHaveSeparateHitAreas() {
        var row = new UiRect(100, 40, 380, 60);
        assertEquals(64, EditorLocalizedText.languageBounds(row).width());
        assertTrue(EditorLocalizedText.inputBounds(row).right() < EditorLocalizedText.languageBounds(row).left());
    }
}
