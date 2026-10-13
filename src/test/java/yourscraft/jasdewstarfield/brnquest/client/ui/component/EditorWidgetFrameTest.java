package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Scrolling clips pixels continuously and shares that same intersection with pointer input. */
class EditorWidgetFrameTest {
    private final UiRect viewport = new UiRect(100, 54, 300, 200);

    @Test void crossingTheTopRetainsTheVisiblePartWithoutMovingItsBaseline() {
        var frame = EditorWidgetFrame.scrolled(new UiRect(110,57,290,77), viewport, 12);
        assertEquals(new UiRect(110,45,290,65), frame.bounds());
        assertEquals(new UiRect(110,54,290,65), frame.visible());
        assertTrue(frame.shown());
        assertTrue(frame.contains(115,54));
        assertFalse(frame.contains(115,53));
        assertFalse(frame.contains(115,65));
    }
    @Test void bottomAndSideClippingExcludeFixedControlsAndHiddenHover() {
        var frame = EditorWidgetFrame.scrolled(new UiRect(90,190,310,210), viewport, 0);
        assertEquals(new UiRect(100,190,300,200), frame.visible());
        assertTrue(frame.contains(100,199));
        assertFalse(frame.contains(300,195));
        assertFalse(frame.contains(110,200));
    }
    @Test void invisibleControlsHaveNoHitAreaAndFocusRevealSurvivesResize() {
        var authored = new UiRect(110,100,290,120);
        assertFalse(EditorWidgetFrame.scrolled(authored, viewport, 80).shown());
        assertFalse(EditorWidgetFrame.scrolled(authored, viewport, 80).contains(110,54));
        int reveal = EditorWidgetFrame.reveal(80,authored,viewport);
        assertEquals(46,reveal);
        assertEquals(new UiRect(110,54,290,74),EditorWidgetFrame.scrolled(authored,viewport,reveal).visible());
        var shortViewport = new UiRect(100,54,300,80);
        assertEquals(40,EditorWidgetFrame.reveal(0,authored,shortViewport));
    }
}
