package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorSmoothValueTest {
    @Test void approachesTargetWithoutOvershooting() {
        EditorSmoothValue value = new EditorSmoothValue(0);
        value.target(100);

        double last = value.current();
        for (int frame = 0; frame < 120; frame++) {
            value.advanceFrame(1.0 / 60.0);
            assertTrue(value.current() >= last);
            assertTrue(value.current() <= 100);
            last = value.current();
        }

        assertEquals(100, value.current(), 0.01);
    }

    @Test void elapsedTimeProducesTheSameMotionAtDifferentFrameRates() {
        EditorSmoothValue sixtyFps = new EditorSmoothValue(0, 0);
        EditorSmoothValue thirtyFps = new EditorSmoothValue(0, 0);
        sixtyFps.target(100);
        thirtyFps.target(100);

        for (int frame = 0; frame < 60; frame++) sixtyFps.advanceFrame(1.0 / 60.0);
        for (int frame = 0; frame < 30; frame++) thirtyFps.advanceFrame(1.0 / 30.0);

        assertEquals(sixtyFps.current(), thirtyFps.current(), 0.000001);
    }

    @Test void snapCancelsPendingAnimation() {
        EditorSmoothValue value = new EditorSmoothValue(0);
        value.target(100);
        value.advanceFrame(1.0 / 60.0);

        value.snap(12);
        value.advanceFrame(1.0 / 60.0);

        assertEquals(12, value.current(), 0.000001);
        assertEquals(12, value.target(), 0.000001);
    }

    @Test void constrainClampsVisibleAndRequestedValuesTogether() {
        EditorSmoothValue value = new EditorSmoothValue(80);
        value.target(120);
        value.advanceFrame(1.0 / 60.0);

        value.constrain(0, 60);

        assertEquals(60, value.current(), 0.000001);
        assertEquals(60, value.target(), 0.000001);
    }

    @Test void smallZoomRemainderSettlesWithoutVisibleOnePercentJump() {
        EditorSmoothValue zoom = new EditorSmoothValue(1.0, 0.00001);
        zoom.target(1.1);

        double beforeFinalSnap = zoom.current();
        while (zoom.current() != zoom.target()) {
            beforeFinalSnap = zoom.current();
            zoom.advanceFrame(1.0 / 60.0);
        }

        assertTrue(zoom.target() - beforeFinalSnap <= 0.00001);
    }
}
