package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StretchedGuiSpriteTest {
    @Test void largeAndTinySurfacesHaveBoundedGeometryWithCompleteCoverage() {
        // Regression: widening a panel must not create one quad per 4x4 source tile.
        for (int width : new int[]{1, 2, 3, 4, 8, 320, 1920}) {
            for (int height : new int[]{1, 4, 30, 240, 1080}) {
                int[] x = StretchedGuiSprite.axis(width, 2, 2);
                int[] y = StretchedGuiSprite.axis(height, 2, 2);
                int area = 0, quads = 0;
                for (int row = 0; row < y.length - 1; row++) {
                    for (int col = 0; col < x.length - 1; col++) {
                        int w = x[col + 1] - x[col], h = y[row + 1] - y[row];
                        assertTrue(w >= 0 && h >= 0);
                        area += w * h;
                        if (w > 0 && h > 0) quads++;
                    }
                }
                assertEquals(width * height, area);
                assertTrue(quads <= 9);
                assertEquals(0, x[0]); assertEquals(width, x[x.length - 1]);
                assertEquals(0, y[0]); assertEquals(height, y[y.length - 1]);
            }
        }
    }

    @Test void asymmetricResourcePackBordersKeepTheirLogicalSize() {
        assertArrayEquals(new int[]{0, 3, 195, 200}, StretchedGuiSprite.axis(200, 3, 5));
        assertArrayEquals(new int[]{0, 1, 2, 3}, StretchedGuiSprite.axis(3, 3, 5));
    }
}
