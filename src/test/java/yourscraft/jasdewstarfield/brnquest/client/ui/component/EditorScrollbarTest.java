package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorScrollbarTest {
    @Test void longListTrackReachesBothEndsInsideClickablePixels() {
        int content = 10000 * 38;
        assertEquals(0, EditorScrollbar.scrollFromTrack(40, 40, 135, content, 95));
        assertEquals(content - 95, EditorScrollbar.scrollFromTrack(134, 40, 135, content, 95));
        assertEquals((content - 95) / 2.0,
                EditorScrollbar.scrollFromTrack(87.5, 40, 135, content, 95));
    }

    @Test void thumbDraggingPreservesTheGrabPointAndReachesBothEnds() {
        assertEquals(0, EditorScrollbar.scrollFromThumb(-20, 5, 40, 140, 500, 100));
        assertEquals(400, EditorScrollbar.scrollFromThumb(200, 5, 40, 140, 500, 100));
        assertEquals(200, EditorScrollbar.scrollFromThumb(90, 10, 40, 140, 500, 100));
    }
}
