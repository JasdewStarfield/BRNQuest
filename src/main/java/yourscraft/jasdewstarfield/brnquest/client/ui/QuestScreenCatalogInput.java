package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
/** Catalog backdrop clicks are consumed once, without falling through to the toolbar. */
final class QuestScreenCatalogInput {
    static boolean dismisses(UiRect catalogBounds, double mouseX, double mouseY, int button) {
        return button == 0 && !catalogBounds.contains(mouseX, mouseY);
    }
}
