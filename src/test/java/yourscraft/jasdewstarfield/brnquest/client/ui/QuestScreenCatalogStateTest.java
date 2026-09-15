package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestScreenCatalogStateTest {
    @Test void inFlightCatalogAuthorizationDoesNotCloseRequestedOverlay() {
        assertFalse(QuestScreen.catalogAuthorizationDenied(ClientEditorState.Mode.CATALOG_LOADING, false));
        assertTrue(QuestScreen.catalogAuthorizationDenied(ClientEditorState.Mode.VIEW, false));
        assertFalse(QuestScreen.catalogAuthorizationDenied(ClientEditorState.Mode.VIEW, true));
    }
}
