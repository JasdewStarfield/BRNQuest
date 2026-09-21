package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Drawer motion must translate PNG clipping at exactly the same rate as the icon itself. */
class LocalScissorTest {
    @Test void bothDrawerDirectionsFollowEveryAnimationOffset() {
        var local = new UiRect(20, 30, 30, 40);
        for (int offset = -200; offset <= 200; offset++) {
            assertEquals(local.translated(offset, 0), LocalScissor.screenBounds(new Matrix4f().translation(offset, 0, 500), local));
        }
    }
    @Test void scaledSlotsAndFractionalOffsetsKeepTheirWholeFootprint() {
        assertEquals(new UiRect(40, 60, 61, 81), LocalScissor.screenBounds(
                new Matrix4f().translation(.5F, .5F, 0).scale(2), new UiRect(20, 30, 30, 40)));
    }
}
