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
    @Test void notificationScalingKeepsCenterAndFoldedLipsInPhysicalPixels() {
        int width=QuestNotificationLayout.logicalWidth(400,280,.75);
        var front=QuestNotificationLayout.scaledFront(400,width,60,3,.75);
        assertEquals(new UiRect(95,17,305,62),front);
        var middle=QuestNotificationLayout.folded(front,1,.75);
        assertEquals(12,middle.top());
        assertEquals(198,middle.width());
        var hud=new UiRect(200,10,392,70);
        assertEquals(70,QuestNotificationLayout.avoid(hud,front,240).top());
    }
    @Test void allConfiguredScalesAndExtremeWindowsClampBothDimensions() {
        for(double requested:new double[]{.5,.75,1,1.5}) for(int height:new int[]{80,100,160,240}) {
            int width=QuestNotificationLayout.logicalWidth(160,600,requested);
            double scale=QuestNotificationLayout.fittingScale(height,90,3,requested);
            var front=QuestNotificationLayout.scaledFront(160,width,90,3,scale);
            assertTrue(scale<=requested);
            assertTrue(front.left()>=12);
            assertTrue(front.right()<=148);
            assertTrue(front.bottom()<=height-8);
            assertTrue(QuestNotificationLayout.folded(front,2,scale).top()>=8);
        }
        assertEquals(0,QuestNotificationLayout.fittingScale(16,90,3,1));
        assertEquals(210,QuestNotificationLayout.logicalHeightBudget(240,3,1));
    }
}
