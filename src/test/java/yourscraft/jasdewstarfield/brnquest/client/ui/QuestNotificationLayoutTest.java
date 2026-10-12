package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import static org.junit.jupiter.api.Assertions.*;

/** Cover both normal placement and GUI scales where the top-right HUD intersects the notification. */
class QuestNotificationLayoutTest {
    @Test void widthIsCenteredClampedAndRearCardsNarrowTowardTheTop() {
        var front = QuestNotificationLayout.front(400, 280, 60, 3);
        assertEquals(new UiRect(60, 20, 340, 80), front);
        var middle = QuestNotificationLayout.folded(front, 1);
        var back = QuestNotificationLayout.folded(front, 2);
        assertEquals(14, middle.top());
        assertEquals(8, back.top());
        assertEquals(264, middle.width());
        assertEquals(248, back.width());
        assertEquals(136, QuestNotificationLayout.front(160, 600, 60, 1).width());
    }
    @Test void overlappingHudMovesBelowTheCompleteFrontCardAndNonoverlappingHudStaysPut() {
        var card = QuestNotificationLayout.front(400, 280, 90, 3);
        var hud = new UiRect(72, 10, 392, 88);
        var moved = QuestNotificationLayout.avoid(hud, card, 240);
        assertEquals(card.bottom() + 8, moved.top());
        assertEquals(78, moved.height());
        assertEquals(hud.left(), moved.left());
        assertEquals(0, moved.intersection(card).height());
        var wideHud = new UiRect(952, 10, 1272, 88);
        assertSame(wideHud, QuestNotificationLayout.avoid(wideHud,
                QuestNotificationLayout.front(1280, 280, 90, 3), 720));
        assertSame(hud, QuestNotificationLayout.avoid(hud, null, 240));
    }
    @Test void tinyViewportGivesPriorityToTheNotification() {
        assertNull(QuestNotificationLayout.avoid(new UiRect(8, 10, 328, 88),
                QuestNotificationLayout.front(336, 280, 90, 3), 160));
    }
}
