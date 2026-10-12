package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Shared screen-space geometry lets the tracking HUD avoid the complete notification stack. */
final class QuestNotificationLayout {
    private QuestNotificationLayout() {}

    static UiRect front(int screenWidth, int configuredWidth, int height, int visibleCount) {
        int width = Math.min(configuredWidth, Math.max(1, screenWidth - 24));
        int left = (screenWidth - width) / 2;
        int top = 8 + Math.max(0, Math.min(3, visibleCount) - 1) * 6;
        return new UiRect(left, top, left + width, top + height);
    }

    /** Each older card exposes a six-pixel lip above the next card and narrows symmetrically. */
    static UiRect folded(UiRect front, int depth) {
        int inset = Math.min(depth * 8, Math.max(0, (front.width() - 8) / 2));
        return new UiRect(front.left() + inset, front.top() - depth * 6,
                front.right() - inset, front.top() - depth * 6 + Math.max(8, front.height() - depth * 6));
    }

    static UiRect avoid(UiRect hud, UiRect obstruction, int screenHeight) {
        if (obstruction == null || hud.intersection(obstruction).width() == 0
                || hud.intersection(obstruction).height() == 0) return hud;
        UiRect moved = hud.translated(0, obstruction.bottom() + 8 - hud.top());
        // A very small viewport gives the notification priority until enough HUD space returns.
        return moved.bottom() <= screenHeight - 8 ? moved : null;
    }
}
