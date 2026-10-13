package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Opacity belongs to background vertices; existing RGB and material transparency are retained. */
class GraystoneSurfaceOpacityTest {
    @Test void transparentAndOpaqueEndpointsPreserveRgb() {
        assertEquals(0x0030332E,GraystoneSurface.withOpacity(0xFF30332E,0));
        assertEquals(0xFF30332E,GraystoneSurface.withOpacity(0xFF30332E,1));
        assertEquals(0x8030332E,GraystoneSurface.withOpacity(0xFF30332E,.5));
        assertEquals(0xD9252821,GraystoneSurface.withOpacity(0xFF252821,.85));
    }
    @Test void existingAlphaAndRangeBoundsRemainDefined() {
        assertEquals(0x40252821,GraystoneSurface.withOpacity(0x80252821,.5));
        assertEquals(0x80252821,GraystoneSurface.withOpacity(0x80252821,3));
        assertEquals(0x00252821,GraystoneSurface.withOpacity(0x80252821,-1));
        assertEquals(0x00252821,GraystoneSurface.withOpacity(0x00252821,1));
    }
}
