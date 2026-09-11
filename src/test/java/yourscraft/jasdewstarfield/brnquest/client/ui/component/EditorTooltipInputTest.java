package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorTooltipInputTest {
    @Test void stationaryPointerCannotOverrideTabButMovementAndClickCan() {
        var input = new EditorTooltipInput();
        input.pointer(40, 60, false);
        input.keyPressed(258);
        input.pointer(40, 60, false);
        assertTrue(input.suppressTooltip());
        input.pointer(41, 60, false);
        assertFalse(input.suppressTooltip());
        input.keyPressed(264);
        input.pointer(41, 60, true);
        assertFalse(input.suppressTooltip());
    }

    @Test void FirstFrameAfterKeyboardDoesNotPretendTheMouseMoved() {
        var input = new EditorTooltipInput();
        input.keyPressed(258);
        input.pointer(40, 60, false);
        assertTrue(input.suppressTooltip());
        input.keyPressed(65);
        assertTrue(input.suppressTooltip());
    }
}
