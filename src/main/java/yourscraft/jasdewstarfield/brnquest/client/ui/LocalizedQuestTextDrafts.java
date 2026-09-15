package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Keeps unsaved locale buffers independent from widgets so resize and locale switching cannot erase input. */
final class LocalizedQuestTextDrafts {
    record Draft(String title, String subtitle, String description, DocumentFormat format) {}

    private final Map<String, Draft> drafts = new LinkedHashMap<>();

    LocalizedQuestTextDrafts(BookLocalization localization, QuestDefinition quest) {
        String prefix = BookText.questPrefix(quest);
        for (String locale : localization.translations().keySet()) {
            Map<String, String> values = localization.translations().getOrDefault(locale, Map.of());
            drafts.put(BookLocalization.normalizeLocale(locale), new Draft(
                    values.getOrDefault(prefix + "title", ""),
                    values.getOrDefault(prefix + "quest_subtitle", ""),
                    values.getOrDefault(prefix + "quest_desc", ""),
                    DocumentFormat.parse(values.get(prefix + "quest_desc_format"))));
        }
        drafts.put(localization.fallbackLocale(), new Draft(quest.title(), quest.subtitle(),
                quest.description(), quest.descriptionFormat()));
    }

    Draft get(String locale) {
        return drafts.getOrDefault(BookLocalization.normalizeLocale(locale),
                new Draft("", "", "", DocumentFormat.PLAIN));
    }

    void remember(String locale, String title, String subtitle, String description, DocumentFormat format) {
        drafts.put(BookLocalization.normalizeLocale(locale), new Draft(title, subtitle, description, format));
    }

    void seed(EditorLocalizedQuestTextScreen.Value value) {
        if (value != null) remember(value.locale(), value.title(), value.subtitle(), value.description(),
                value.descriptionFormat());
    }
}
