package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies navigation ordering, author intents and stale-frame rejection. */
class QuestNavigationPanelTest {
    private static final ResourceLocation BOOK_ID = id("book");
    private static final ResourceLocation GROUP = id("group");
    private static final ResourceLocation CHAPTER = id("chapter");
    private static final QuestNavigationPanel.Layout OPEN =
            new QuestNavigationPanel.Layout(132, 20, 580, 560, 132, 0, 10, 300, false);

    @Test void chapterAndRightClickContextUseTheRenderedOrderedEntries() {
        QuestNavigationPanel panel = new QuestNavigationPanel();
        QuestBookDefinition book = book();
        QuestScreenFrameIdentity identity = identity("r1", 800);
        panel.advance(new QuestNavigationPanel.Model(identity, book, null, true, true), OPEN);

        QuestNavigationPanel.Intent group = panel.click(identity, 20, 25, 1).intent();
        assertEquals(QuestNavigationPanel.Action.OPEN_GROUP_CONTEXT, group.action());
        assertEquals(GROUP, group.targetId());

        QuestNavigationPanel.Intent chapter = panel.click(identity, 20,
                20 + QuestNavigationPanel.GROUP_HEIGHT + 4, 0).intent();
        assertEquals(QuestNavigationPanel.Action.SELECT_CHAPTER, chapter.action());
        assertEquals(CHAPTER, chapter.targetId());
    }

    @Test void footerAndHandleReturnDeclarativeActions() {
        QuestNavigationPanel panel = new QuestNavigationPanel();
        QuestScreenFrameIdentity identity = identity("r1", 800);
        panel.advance(new QuestNavigationPanel.Model(identity, book(), null, true, true), OPEN);

        assertEquals(QuestNavigationPanel.Action.ADD_GROUP,
                panel.click(identity, OPEN.groupButton().centerX(), OPEN.groupButton().centerY(), 0)
                        .intent().action());
        assertEquals(QuestNavigationPanel.Action.ADD_CHAPTER,
                panel.click(identity, OPEN.chapterButton().centerX(), OPEN.chapterButton().centerY(), 0)
                        .intent().action());
        assertEquals(QuestNavigationPanel.Action.TOGGLE_DRAWER,
                panel.click(identity, OPEN.visibleRight() + 2, OPEN.centerY(), 0).intent().action());
    }

    @Test void animationAndStaleIdentityCannotSelectAnObsoleteChapter() {
        QuestNavigationPanel panel = new QuestNavigationPanel();
        QuestScreenFrameIdentity identity = identity("r1", 800);
        QuestNavigationPanel.Layout moving = new QuestNavigationPanel.Layout(
                132, 20, 580, 560, 80, -52, 10, 300, false);
        panel.advance(new QuestNavigationPanel.Model(identity, book(), null, false, false), moving);

        assertFalse(panel.click(identity, 20, 40, 0).consumed());
        assertFalse(panel.click(identity("r2", 800), moving.visibleRight() + 2, 300, 0).consumed());
        assertFalse(panel.click(identity("r1", 801), moving.visibleRight() + 2, 300, 0).consumed());
        assertEquals(QuestNavigationPanel.Action.TOGGLE_DRAWER,
                panel.click(identity, moving.visibleRight() + 2, 300, 0).intent().action());
    }

    @Test void iconSizedRowsKeepAdjacentChapterHitBoundariesDistinct() {
        var first = book().chapters().getFirst();
        var second = new ChapterDefinition(BOOK_ID, id("second"), GROUP, "Second", "", 1, List.of());
        var book = new QuestBookDefinition(BOOK_ID, 1, "Book", book().chapterGroups(), List.of(first, second), Map.of());
        var panel = new QuestNavigationPanel();
        var identity = identity("r1", 800);
        panel.advance(new QuestNavigationPanel.Model(identity, book, first, false, false), OPEN);
        // Both the icon's lower pixels and the exact next-row boundary must resolve to the displayed chapter.
        int boundary = OPEN.top() + QuestNavigationPanel.GROUP_HEIGHT + QuestNavigationPanel.CHAPTER_HEIGHT;
        assertEquals(CHAPTER, panel.click(identity, 15, boundary - 1, 0).intent().targetId());
        assertEquals(second.id(), panel.click(identity, 15, boundary, 0).intent().targetId());
    }

    private static QuestBookDefinition book() {
        ChapterGroupDefinition group = new ChapterGroupDefinition(BOOK_ID, GROUP, "Group", 0);
        ChapterDefinition chapter = new ChapterDefinition(BOOK_ID, CHAPTER, GROUP,
                "Chapter", "", 0, List.of());
        return new QuestBookDefinition(BOOK_ID, 1, "Book", List.of(group), List.of(chapter), Map.of());
    }

    private static QuestScreenFrameIdentity identity(String revision, int width) {
        return new QuestScreenFrameIdentity(BOOK_ID, revision, false, width, 600);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", path);
    }
}
