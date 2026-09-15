package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalizedQuestTextDraftsTest {
    @Test void localeSwitchingRetainsIndependentUnsavedTextAndFormat() {
        ResourceLocation book = ResourceLocation.parse("test:book");
        QuestDefinition quest = new QuestDefinition(book, ResourceLocation.parse("test:quest"),
                ResourceLocation.parse("test:chapter"), "Native", "Sub", "Native body",
                DocumentFormat.PLAIN, "", 0, 0, List.of(), List.of(), List.of(), "");
        BookLocalization localization = new BookLocalization("zh_cn", Map.of("en_us", Map.of(
                "quest.test:quest.title", "English",
                "quest.test:quest.quest_desc", "**English body**",
                "quest.test:quest.quest_desc_format", "markdown_v1")));
        var drafts = new LocalizedQuestTextDrafts(localization, quest);

        drafts.remember("zh_cn", "未保存标题", "副标题", "# 未保存正文", DocumentFormat.MARKDOWN_V1);

        assertEquals("English", drafts.get("en-US").title());
        assertEquals(DocumentFormat.MARKDOWN_V1, drafts.get("en_us").format());
        assertEquals("# 未保存正文", drafts.get("zh_cn").description());
        assertEquals(DocumentFormat.PLAIN, drafts.get("ja_jp").format());
    }

    @Test void rejectedSubmissionCanSeedTheExactLocaleBufferForReopening() {
        ResourceLocation book = ResourceLocation.parse("test:book");
        QuestDefinition quest = new QuestDefinition(book, ResourceLocation.parse("test:quest"),
                ResourceLocation.parse("test:chapter"), "Native", "", "Body", "",
                0, 0, List.of(), List.of(), List.of(), "");
        var drafts = new LocalizedQuestTextDrafts(BookLocalization.EMPTY, quest);
        var recovery = new EditorLocalizedQuestTextScreen.Value("fr_fr", "Titre", "Sous-titre",
                "**Corps**", DocumentFormat.MARKDOWN_V1);

        drafts.seed(recovery);

        assertEquals("Titre", drafts.get("fr-FR").title());
        assertEquals("**Corps**", drafts.get("fr_fr").description());
        assertEquals(DocumentFormat.MARKDOWN_V1, drafts.get("fr_fr").format());
    }
}
