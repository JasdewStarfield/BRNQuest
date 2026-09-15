package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;

/** Pure acknowledgement check for deciding when a rejected-edit recovery buffer may be discarded. */
final class LocalizedQuestTextRecovery {
    private LocalizedQuestTextRecovery() {}

    static boolean matches(QuestBookDefinition book, QuestDefinition quest,
                           EditorLocalizedQuestTextScreen.Value value) {
        String locale = BookLocalization.normalizeLocale(value.locale());
        var description = BookText.questDescriptionForEditing(book, quest, locale);
        return value.title().equals(BookText.quest(book, quest, locale, "title", quest.title()))
                && value.subtitle().equals(BookText.quest(book, quest, locale, "quest_subtitle", quest.subtitle()))
                && value.description().equals(description.text())
                && value.descriptionFormat().equals(description.format());
    }
}
