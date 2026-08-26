package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorSmoothScrollTest {
    @Test void wheelUsesPixelStepAndAccumulatesAtTheTarget() {
        EditorSmoothScroll scroll = new EditorSmoothScroll();

        scroll.scrollWheel(-1, 24, 500, 100);
        scroll.scrollWheel(-1, 24, 500, 100);

        assertEquals(48, scroll.target(), 0.000001);
        assertEquals(0, scroll.visual(), 0.000001);
    }

    @Test void pixelOffsetAndHitTestingShareOneVisualPosition() {
        EditorSmoothScroll scroll = new EditorSmoothScroll();
        scroll.snap(45);

        assertEquals(1, scroll.firstIndex(30));
        assertEquals(-15, scroll.rowOffset(30));
        assertEquals(1, scroll.rowAt(70, 60, 180, 30, 10));
        assertEquals(2, scroll.rowAt(76, 60, 180, 30, 10));
    }

    @Test void contentShrinkClampsCurrentAndTargetTogether() {
        EditorSmoothScroll scroll = new EditorSmoothScroll();
        scroll.snap(240);

        scroll.constrain(180, 120);

        assertEquals(60, scroll.visual(), 0.000001);
        assertEquals(5, scroll.rowAt(150, 60, 180, 30, 10));
    }
}
