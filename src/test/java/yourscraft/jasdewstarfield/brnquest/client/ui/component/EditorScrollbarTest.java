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
}
