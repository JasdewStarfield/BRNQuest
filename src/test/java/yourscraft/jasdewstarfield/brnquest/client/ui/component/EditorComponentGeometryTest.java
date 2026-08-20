package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorComponentGeometryTest {
    @Test void popupStaysInsideTheStationaryContentBars() {
        UiRect popup = EditorPopupMenu.layout(1260, 700, 1280, 24, 696, 142, 5);

        assertEquals(new UiRect(1134, 606, 1276, 696), popup);
        assertEquals(0, EditorPopupMenu.rowAt(popup, 5, 1140, 610));
        assertEquals(4, EditorPopupMenu.rowAt(popup, 5, 1140, 695));
        assertEquals(-1, EditorPopupMenu.rowAt(popup, 5, 100, 100));
    }

    @Test void bothConfirmationTypesShareTheEstablishedButtonGeometry() {
        QuestScreenLayout screen = new QuestScreenLayout(1280, 720, false, false);
        EditorConfirmDialog.Layout dialog = EditorConfirmDialog.layout(screen);

        assertEquals(new UiRect(490, 324, 790, 396), dialog.dialog());
        assertEquals(new UiRect(500, 370, 636, 388), dialog.cancel());
        assertEquals(new UiRect(644, 370, 780, 388), dialog.confirm());
        assertEquals(EditorConfirmDialog.Action.CANCEL,
                EditorConfirmDialog.actionAt(screen, 520, 380));
        assertEquals(EditorConfirmDialog.Action.CONFIRM,
                EditorConfirmDialog.actionAt(screen, 700, 380));
    }

    @Test void overlayHostAllowsOnlyOneInputCapturingSurface() {
        EditorOverlayHost host = new EditorOverlayHost();

        host.show(EditorOverlayHost.Kind.CATALOG);
        host.show(EditorOverlayHost.Kind.DELETE_CONFIRMATION);
        assertEquals(EditorOverlayHost.Kind.DELETE_CONFIRMATION, host.active());

        host.close();
        assertEquals(EditorOverlayHost.Kind.NONE, host.active());
    }

    @Test void pickerMapsOnlyVisibleTwoLineRowsToAbsoluteEntries() {
        UiRect bounds = new UiRect(100, 50, 500, 252);

        assertEquals(6, EditorPickerList.visibleRows(bounds));
        assertEquals(-1, EditorPickerList.entryAt(bounds, 3, 20, 120, 60));
        assertEquals(3, EditorPickerList.entryAt(bounds, 3, 20, 120, 72));
        assertEquals(8, EditorPickerList.entryAt(bounds, 3, 20, 120, 221));
        assertEquals(-1, EditorPickerList.entryAt(bounds, 3, 5, 120, 221));
    }

    @Test void compactPropertyRowsReserveStableLabelAndFieldColumns() {
        EditorPropertyFormLayout.Row first = EditorPropertyFormLayout.row(177, 48, 226, 78);
        EditorPropertyFormLayout.Row fifth = EditorPropertyFormLayout.row(177, 136, 226, 78);

        assertEquals(new UiRect(177, 48, 255, 66), first.label());
        assertEquals(new UiRect(255, 48, 403, 66), first.field());
        assertEquals(154, fifth.field().bottom(), "Five compact rows must stay above the action buttons at 720p scale");
    }

    @Test void bottomStatusUsesTheFixedToolbarRatherThanTheOpenNavigationDrawer() {
        QuestScreenLayout screen = new QuestScreenLayout(1280, 720, false, false);

        assertEquals(892, screen.bottomStatusMaximumWidth(900));
    }
}
