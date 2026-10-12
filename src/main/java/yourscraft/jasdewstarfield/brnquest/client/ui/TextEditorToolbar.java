package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import java.util.ArrayList;
import java.util.List;

/** Packs measured actions without stretching More; hidden actions remain available in the menu. */
final class TextEditorToolbar {
    record Action(String key, int width) {}
    record Placed(String key, UiRect bounds) {}
    record Layout(List<Placed> actions, UiRect more) {
        boolean contains(String key) { return actions.stream().anyMatch(action -> action.key().equals(key)); }
    }
    static Layout layout(UiRect area, int targetWidth, int moreWidth, List<Action> candidates) {
        int left=area.left()+targetWidth;
        int moreSize=Math.min(Math.max(1,moreWidth),Math.max(1,area.right()-left));
        int availableRight=area.right()-moreSize-3;
        List<Placed> placed=new ArrayList<>();
        for (var action:candidates) {
            int size=Math.max(20,action.width());
            if (left+size>availableRight) continue;
            placed.add(new Placed(action.key(),new UiRect(left,area.top(),left+size,area.bottom())));
            left+=size+3;
        }
        return new Layout(List.copyOf(placed),new UiRect(left,area.top(),Math.min(area.right(),left+moreSize),area.bottom()));
    }
}
