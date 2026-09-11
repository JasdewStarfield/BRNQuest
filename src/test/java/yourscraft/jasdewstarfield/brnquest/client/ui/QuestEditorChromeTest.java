package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Locks the editor chrome to immutable frame geometry and semantic intents. */
class QuestEditorChromeTest {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath("brnquest", "test");

    @Test void layoutKeepsFullWidthToolbarsAndCollapsesLiveHistoryGap() {
        QuestScreenLayout screen = new QuestScreenLayout(900, 600, false, false);
        QuestEditorChrome.Layout draft = QuestEditorChrome.layout(screen, false, true, true);
        QuestEditorChrome.Layout live = QuestEditorChrome.layout(screen, true, true, true);

        assertEquals(screen.topToolbar(), draft.topToolbar());
        assertEquals(screen.bottomToolbar(), draft.bottomToolbar());
        assertEquals(draft.publish().left() - 4, draft.redo().right());
        assertEquals(live.exit().left() - 4, live.redo().right());
        assertTrue(draft.status().right() <= draft.undo().left() - 4);
    }

    @Test void clickRejectsGeometryFromAnotherRevisionOrSize() {
        QuestEditorChrome chrome = new QuestEditorChrome();
        QuestScreenLayout screen = new QuestScreenLayout(900, 600, false, false);
        QuestScreenFrameIdentity first = identity("r1", 900, 600);
        QuestEditorChrome.Layout layout = chrome.advance(screen, model(first, false, true, false));
        double x = layout.save().centerX();
        double y = layout.save().centerY();

        assertEquals(QuestEditorChrome.Action.SAVE,
                chrome.click(first, x, y).intent().action());
        assertFalse(chrome.click(identity("r2", 900, 600), x, y).consumed());
        assertFalse(chrome.click(identity("r1", 901, 600), x, y).consumed());
    }

    @Test void busyChromeConsumesButtonsWithoutEmittingActions() {
        QuestEditorChrome chrome = new QuestEditorChrome();
        QuestScreenLayout screen = new QuestScreenLayout(900, 600, false, false);
        QuestScreenFrameIdentity identity = identity("r1", 900, 600);
        QuestEditorChrome.Layout layout = chrome.advance(screen, model(identity, true, true, false));

        QuestEditorChrome.ClickResult result = chrome.click(identity,
                layout.save().centerX(), layout.save().centerY());
        assertTrue(result.consumed());
        assertNull(result.intent());
        assertFalse(chrome.focusNext(identity, false));
    }

    @Test void keyboardOrderMatchesVisibleDraftAndLiveControls() {
        QuestEditorChrome chrome = new QuestEditorChrome();
        QuestScreenLayout screen = new QuestScreenLayout(900, 600, false, false);
        QuestScreenFrameIdentity identity = identity("r1", 900, 600);
        chrome.advance(screen, model(identity, false, true, false));

        assertTrue(chrome.focusNext(identity, false));
        assertEquals(QuestEditorChrome.Action.UNDO, chrome.activateFocused(identity).orElseThrow().action());
        assertTrue(chrome.focusNext(identity, true));
        assertEquals(QuestEditorChrome.Action.EXIT, chrome.activateFocused(identity).orElseThrow().action());

        chrome.advance(screen, model(identity, false, true, true));
        assertTrue(chrome.focusNext(identity, false));
        assertEquals(QuestEditorChrome.Action.UNDO, chrome.activateFocused(identity).orElseThrow().action());
    }

    @Test void keyboardCannotActivateDisabledSave() {
        var chrome = new QuestEditorChrome();
        var identity = identity("r1", 900, 600);
        chrome.advance(new QuestScreenLayout(900, 600, false, false), model(identity, false, false, false));
        // Undo, redo, publish, save: a clean draft's save button must remain inert.
        for (int i = 0; i < 4; i++) assertTrue(chrome.focusNext(identity, false));
        assertTrue(chrome.activateFocused(identity).isEmpty());
    }

    private static QuestScreenFrameIdentity identity(String revision, int width, int height) {
        return new QuestScreenFrameIdentity(BOOK, revision, true, width, height);
    }

    private static QuestEditorChrome.Model model(QuestScreenFrameIdentity identity, boolean busy,
                                                  boolean dirty, boolean live) {
        return new QuestEditorChrome.Model(identity, "Test", BOOK, true, true, true, live, busy, dirty,
                true, true, 1, 1, !busy, !busy, Component.literal("Ready"), false, List.of());
    }
}
