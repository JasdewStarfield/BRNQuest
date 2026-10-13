package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import java.util.List;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import static org.junit.jupiter.api.Assertions.*;

/** Geometry regression covers actual scaled occupancy, inline/wrapped rows, omitted objectives and notice avoidance. */
class QuestHudLayoutTest {
    @Test void defaultScaleAndNaturalWidthReduceThePreviousFixedPanelFootprint() {
        var rows = List.of(new QuestHudLayout.Measure(80,30),new QuestHudLayout.Measure(60,30));
        int width = QuestHudLayout.width(428,.75,240,70,rows);
        assertEquals(156,width);
        var plan = QuestHudLayout.plan(428,240,.75,width,1,9,rows,List.of(1,1),2);
        assertTrue(plan.rows().stream().allMatch(QuestHudLayout.Row::inline));
        assertEquals(117,plan.screenBounds().width());
        assertEquals(51,plan.screenBounds().height());
        assertEquals(420,plan.screenBounds().right());
        assertEquals(0,plan.hidden());
    }
    @Test void longRequirementWrapsAndAdditionalObjectivesAreCounted() {
        var rows = List.of(new QuestHudLayout.Measure(300,60),new QuestHudLayout.Measure(20,30),new QuestHudLayout.Measure(20,30));
        int width = QuestHudLayout.width(428,.75,240,300,rows);
        var plan = QuestHudLayout.plan(428,240,.75,width,2,9,rows,List.of(2,1,1),8);
        assertEquals(240,width);
        assertFalse(plan.rows().getFirst().inline());
        assertEquals(31,plan.rows().getFirst().height());
        assertEquals(5,plan.hidden());
        assertTrue(plan.screenBounds().bottom()<=232);
    }
    @Test void allConfiguredScalesClampToTheViewportAndNoticeCollisionUsesScaledPixels() {
        var rows = List.of(new QuestHudLayout.Measure(300,40));
        for(double scale : new double[]{.5,.75,1,1.5}) {
            int width = QuestHudLayout.width(200,scale,400,300,rows);
            var plan = QuestHudLayout.plan(200,180,scale,width,2,9,rows,List.of(2),1);
            assertNotNull(plan.screenBounds());
            assertTrue(plan.screenBounds().left()>=8);
            assertTrue(plan.screenBounds().right()<=192);
            var obstruction = new UiRect(12,8,188,58);
            var moved = QuestNotificationLayout.avoid(plan.screenBounds(),obstruction,300);
            assertEquals(66,moved.top());
            assertEquals(plan.screenBounds().width(),moved.width());
            assertEquals(0,moved.intersection(obstruction).height());
        }
    }
    @Test void tinyWindowsDropRowsWithACountOrHideTheWholePanel() {
        var rows = List.of(new QuestHudLayout.Measure(300,50),new QuestHudLayout.Measure(300,50),new QuestHudLayout.Measure(300,50));
        var plan = QuestHudLayout.plan(320,100,1,240,1,9,rows,List.of(2,2,2),5);
        assertEquals(1,plan.rows().size());
        assertEquals(4,plan.hidden());
        assertNotNull(plan.screenBounds());
        assertNull(QuestHudLayout.plan(320,30,1,240,1,9,rows,List.of(2,2,2),5).screenBounds());
    }
}
