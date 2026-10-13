package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTypedEntryListSectionTest {
    @Test void twoFooterRowsKeepLabelsAndPointerRegionsSeparateFromTheList() {
        var area = new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(300, 50, 480, 220);
        // Larger wrapped labels grow upward while Done remains anchored above the main screen toolbar.
        var footer = QuestTypedEntryListSection.footer(area, 31, 20);
        org.junit.jupiter.api.Assertions.assertEquals(88, footer.add().width());
        org.junit.jupiter.api.Assertions.assertEquals(88, footer.paste().width());
        org.junit.jupiter.api.Assertions.assertEquals(180, footer.done().width());
        assertTrue(footer.add().right() < footer.paste().left());
        assertTrue(footer.add().bottom() < footer.done().top());
        assertTrue(footer.paste().bottom() < footer.done().top());
        assertTrue(footer.add().containsExclusive(footer.add().centerX(), footer.add().centerY()));
        assertFalse(footer.done().containsExclusive(footer.add().centerX(), footer.add().centerY()));
        var list = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListViewport.withHalfRowPreview(area.top(), footer.add().top() - 6, 40);
        assertTrue(list.bottom() < footer.add().top());
        assertTrue(footer.translated(24, 0).add().translated(-24, 0).equals(footer.add()));
    }

    @Test void onlyTheExactRenderedQuestFrameMayReceiveInput() {
        ResourceLocation book = ResourceLocation.parse("example:book");
        ResourceLocation quest = ResourceLocation.parse("example:quest");
        QuestScreenFrameIdentity rendered = new QuestScreenFrameIdentity(book, "rev-a", true, 800, 480);

        assertTrue(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                rendered, quest, QuestTypedEntryKind.TASK));
        assertFalse(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                new QuestScreenFrameIdentity(book, "rev-b", true, 800, 480), quest, QuestTypedEntryKind.TASK));
        assertFalse(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                rendered, ResourceLocation.parse("example:other_quest"), QuestTypedEntryKind.TASK));
        assertFalse(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                rendered, quest, QuestTypedEntryKind.REWARD));
    }
}
