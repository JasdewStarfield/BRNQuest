package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizedQuestTextRecoveryTest {
    @Test void clearsOnlyAfterTheVerifiedLocaleTextAndFormatBothMatch() {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Native", "", "Body", DocumentFormat.PLAIN, "", 0, 0,
                List.of(), List.of(), List.of(), "");
        BookLocalization localization = new BookLocalization("zh_cn", Map.of("en_us", Map.of(
                "quest.test:quest.title", "English",
                "quest.test:quest.quest_subtitle", "Subtitle",
                "quest.test:quest.quest_desc", "**Body**",
                "quest.test:quest.quest_desc_format", "markdown_v1")));
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book", List.of(),
                List.of(new ChapterDefinition(bookId, chapterId, ResourceLocation.parse("test:group"),
                        "Chapter", "", 0, List.of(quest))), Map.of(), localization, Map.of());
        var accepted = new EditorLocalizedQuestTextScreen.Value("en-US", "English", "Subtitle",
                "**Body**", DocumentFormat.MARKDOWN_V1);

        assertTrue(LocalizedQuestTextRecovery.matches(book, quest, accepted));
        assertFalse(LocalizedQuestTextRecovery.matches(book, quest,
                new EditorLocalizedQuestTextScreen.Value("en_us", "English", "Subtitle",
                        "**Body**", DocumentFormat.PLAIN)));
    }
}
