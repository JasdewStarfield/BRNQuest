package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditorComponentGeometryTest {
    @Test void popupStaysInsideTheStationaryContentBars() {
        UiRect popup = EditorPopupMenu.layout(1260, 700, 1280, 24, 696, 142, 5);

        assertEquals(new UiRect(1134, 606, 1276, 696), popup);
        assertEquals(0, EditorPopupMenu.rowAt(popup, 5, 1140, 610));
        assertEquals(4, EditorPopupMenu.rowAt(popup, 5, 1140, 695));
        assertEquals(-1, EditorPopupMenu.rowAt(popup, 5, 100, 100));
    }

    @Test void cascadingPopupOpensInwardAndReturnsOnlyLeafActions() {
        var entries = EditorPopupMenu.menu(menu -> menu
                .action("RENAME", Component.literal("Rename"), false)
                .submenu(Component.literal("Move"), move -> move
                        .action("UP", Component.literal("Up"), false)
                        .action("DOWN", Component.literal("Down"), false))
                .action("DELETE", Component.literal("Delete"), true));
        UiRect root = EditorPopupMenu.layout(1260, 650, 1280, 24, 696, 142, entries.size());
        EditorPopupMenu.CascadeLayout closed = EditorPopupMenu.cascadeLayout(
                root, entries, -1, 1280, 24, 696, 142);
        int openIndex = EditorPopupMenu.resolveSubmenu(root, entries, closed,
                root.left() + 10, root.top() + EditorPopupMenu.ROW_HEIGHT + 5);
        EditorPopupMenu.CascadeLayout open = EditorPopupMenu.cascadeLayout(
                root, entries, openIndex, 1280, 24, 696, 142);

        assertEquals(1, openIndex);
        assertEquals(root.left() - 141, open.submenu().left(),
                "A right-edge submenu must open toward available screen space");
        assertEquals("", EditorPopupMenu.actionAt(open, entries,
                root.left() + 10, root.top() + EditorPopupMenu.ROW_HEIGHT + 5));
        assertEquals("UP", EditorPopupMenu.actionAt(open, entries,
                open.submenu().left() + 10, open.submenu().top() + 5));
        assertEquals(1, EditorPopupMenu.resolveSubmenu(root, entries, open,
                open.submenu().left() + 10, open.submenu().top() + 5));
    }

    @Test void disabledPopupActionsRemainVisibleButDoNotDispatch() {
        var entries = EditorPopupMenu.menu(menu -> menu.submenu(Component.literal("Move"), move -> move
                .action("UP", Component.literal("Up"), false, false)
                .action("DOWN", Component.literal("Down"), false, true)));
        UiRect root = EditorPopupMenu.layout(100, 50, 400, 20, 300, 142, entries.size());
        EditorPopupMenu.CascadeLayout open = EditorPopupMenu.cascadeLayout(
                root, entries, 0, 400, 20, 300, 142);

        assertEquals("", EditorPopupMenu.actionAt(open, entries,
                open.submenu().left() + 5, open.submenu().top() + 5));
        assertEquals("DOWN", EditorPopupMenu.actionAt(open, entries,
                open.submenu().left() + 5, open.submenu().top() + EditorPopupMenu.ROW_HEIGHT + 5));
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

    @Test void buttonContentModesShareCenteredGeometry() {
        UiRect bounds = new UiRect(0, 0, 100, 20);

        EditorButton.ContentLayout text = EditorButton.contentLayout(
                bounds, EditorButton.ContentMode.TEXT, 10, 40, 4);
        EditorButton.ContentLayout iconAndText = EditorButton.contentLayout(
                bounds, EditorButton.ContentMode.ICON_AND_TEXT, 10, 40, 4);
        EditorButton.ContentLayout iconOnly = EditorButton.contentLayout(
                bounds, EditorButton.ContentMode.ICON_ONLY, 10, 40, 4);

        assertEquals(new UiRect(30, 0, 30, 20), text.icon());
        assertEquals(new UiRect(30, 0, 70, 20), text.label());
        assertEquals(new UiRect(23, 0, 33, 20), iconAndText.icon());
        assertEquals(new UiRect(37, 0, 77, 20), iconAndText.label());
        assertEquals(new UiRect(45, 0, 55, 20), iconOnly.icon());
        assertEquals(new UiRect(55, 0, 55, 20), iconOnly.label());
    }

    @Test void iconOnlyButtonsKeepAccessibleMeaningAndRequireAnIcon() {
        Component label = Component.literal("Edit");
        EditorButton.Definition definition = EditorButton.Definition.iconOnly(
                label, label, EditorIcon.glyph(Component.literal("E")));

        assertEquals(label, definition.narration());
        assertEquals(label, definition.tooltip().getFirst());
        assertThrows(IllegalArgumentException.class, () -> new EditorButton.Definition(
                label, java.util.List.of(label), null, EditorButton.ContentMode.ICON_ONLY));
    }

    @Test void oversizedButtonContentNeverEscapesItsBounds() {
        UiRect bounds = new UiRect(10, 0, 20, 20);

        EditorButton.ContentLayout layout = EditorButton.contentLayout(
                bounds, EditorButton.ContentMode.ICON_AND_TEXT, 10, 40, 4);

        assertEquals(new UiRect(10, 0, 20, 20), layout.icon());
        assertEquals(new UiRect(20, 0, 20, 20), layout.label());
    }

    @Test void compactIconButtonsShrinkLabelsWithoutShrinkingTheirHitbox() {
        UiRect compactTab = new UiRect(0, 0, 38, 20);

        assertEquals(0.75F, EditorButton.labelScale(
                compactTab, EditorButton.ContentMode.ICON_AND_TEXT, 9, 36, 4));
        assertEquals(1.0F, EditorButton.labelScale(
                compactTab, EditorButton.ContentMode.ICON_ONLY, 9, 36, 4));
        assertEquals(20, compactTab.height());
    }

    @Test void semanticButtonTonesKeepMeaningDistinctButShareDisabledPresentation() {
        EditorButton.Palette neutral = EditorButton.Tone.NEUTRAL.palette();
        EditorButton.Palette primary = EditorButton.Tone.PRIMARY.palette();
        EditorButton.Palette danger = EditorButton.Tone.DANGER.palette();

        assertNotEquals(neutral.background(), primary.background());
        assertNotEquals(primary.background(), danger.background());
        assertEquals(neutral.disabledBackground(), primary.disabledBackground());
        assertEquals(primary.disabledText(), danger.disabledText());
    }

    @Test void quickTextDialogKeepsInputAndActionsInsideItsPanel() {
        QuestScreenLayout screen = new QuestScreenLayout(1280, 720, false, true);
        EditorQuickTextDialog.Layout dialog = EditorQuickTextDialog.layout(screen);

        assertEquals(new UiRect(430, 314, 850, 406), dialog.dialog());
        assertEquals(new UiRect(442, 345, 838, 365), dialog.input());
        assertEquals(EditorQuickTextDialog.Action.CANCEL,
                EditorQuickTextDialog.actionAt(screen, 500, 388));
        assertEquals(EditorQuickTextDialog.Action.APPLY,
                EditorQuickTextDialog.actionAt(screen, 760, 388));
    }

    @Test void compactTextOnlyShrinksWhenItsMeasuredWidthRequiresIt() {
        assertEquals(1.0F, EditorTextLayout.fittedScale(80, 100, 0.75F));
        assertEquals(0.8F, EditorTextLayout.fittedScale(100, 80, 0.75F));
        assertEquals(0.75F, EditorTextLayout.fittedScale(200, 80, 0.75F));
    }

    @Test void previewCacheReplacesAReusedStableIdWhenItsSerializedContentChanges() {
        ContentAwareCache<String, String, String> cache = new ContentAwareCache<>();
        AtomicInteger loads = new AtomicInteger();

        assertEquals("acacia_door", cache.get("reward_1", "acacia_door", value -> {
            loads.incrementAndGet();
            return value;
        }));
        assertEquals("acacia_door", cache.get("reward_1", "acacia_door", value -> {
            loads.incrementAndGet();
            return value;
        }));
        assertEquals("oak_log", cache.get("reward_1", "oak_log", value -> {
            loads.incrementAndGet();
            return value;
        }));
        assertEquals(2, loads.get(), "A stable ID must not make changed preview content stale");
    }
}
