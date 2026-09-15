package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestScreenCatalogStateTest {
    @Test void inFlightCatalogAuthorizationDoesNotCloseRequestedOverlay() {
        assertFalse(QuestScreen.catalogAuthorizationDenied(ClientEditorState.Mode.CATALOG_LOADING, false));
        assertTrue(QuestScreen.catalogAuthorizationDenied(ClientEditorState.Mode.VIEW, false));
        assertFalse(QuestScreen.catalogAuthorizationDenied(ClientEditorState.Mode.VIEW, true));
    }

    @Test void catalogDismissesOnTitleOrBackgroundButKeepsInsideClicks() {
        UiRect popup = new UiRect(100, 30, 300, 180);

        assertTrue(QuestScreen.catalogClickDismisses(popup, 200, 15, 0), "top title is outside");
        assertTrue(QuestScreen.catalogClickDismisses(popup, 20, 200, 0), "background is outside");
        assertFalse(QuestScreen.catalogClickDismisses(popup, 200, 80, 0), "popup owns inside clicks");
        assertFalse(QuestScreen.catalogClickDismisses(popup, 20, 200, 1), "right click does not dismiss");
    }
}
