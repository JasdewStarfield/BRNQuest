package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Shared screen-space geometry lets the tracking HUD avoid the complete notification stack. */
final class QuestNotificationLayout {
    private QuestNotificationLayout() {}

    static int logicalWidth(int screenWidth, int configuredWidth, double scale) {
        return Math.min(configuredWidth, Math.max(1,(int)Math.floor((screenWidth-24)/scale)));
    }

    /** Text wrapping uses unscaled dimensions while the card and its lips remain inside the physical viewport. */
    static int logicalHeightBudget(int screenHeight, int visibleCount, double scale) {
        int lips = Math.max(0,Math.min(3,visibleCount)-1)*6;
        return Math.max(1,(int)Math.floor((screenHeight-18)/scale)-lips);
    }

    /** Extremely short windows reduce the effective scale uniformly, including the folded lip spacing. */
    static double fittingScale(int screenHeight, int logicalHeight, int visibleCount, double requestedScale) {
        int span = logicalHeight+Math.max(0,Math.min(3,visibleCount)-1)*6;
        return Math.min(requestedScale,Math.max(0,(screenHeight-18.0)/Math.max(1,span)));
    }

    static UiRect front(int screenWidth, int configuredWidth, int height, int visibleCount) {
        return scaledFront(screenWidth,logicalWidth(screenWidth,configuredWidth,1),height,visibleCount,1);
    }

    static UiRect scaledFront(int screenWidth, int logicalWidth, int height, int visibleCount, double scale) {
        int width = (int)Math.ceil(logicalWidth*scale);
        int left = (screenWidth-width)/2;
        int top = 8+(int)Math.ceil(Math.max(0,Math.min(3,visibleCount)-1)*6*scale);
        return new UiRect(left,top,left+width,top+(int)Math.ceil(height*scale));
    }

    static UiRect folded(UiRect front, int depth) { return folded(front,depth,1); }

    /** Each older card exposes a scaled lip and narrows symmetrically, using the same units as the front card. */
    static UiRect folded(UiRect front, int depth, double scale) {
        int inset = Math.min((int)Math.ceil(depth*8*scale),Math.max(0,(front.width()-8)/2));
        int lift = (int)Math.ceil(depth*6*scale);
        return new UiRect(front.left()+inset,front.top()-lift,front.right()-inset,
                front.top()-lift+Math.max((int)Math.ceil(8*scale),front.height()-lift));
    }

    static UiRect avoid(UiRect hud, UiRect obstruction, int screenHeight) {
        if (obstruction == null || hud.intersection(obstruction).width() == 0
                || hud.intersection(obstruction).height() == 0) return hud;
        UiRect moved = hud.translated(0, obstruction.bottom() + 8 - hud.top());
        // A very small viewport gives the notification priority until enough HUD space returns.
        return moved.bottom() <= screenHeight - 8 ? moved : null;
    }
}
