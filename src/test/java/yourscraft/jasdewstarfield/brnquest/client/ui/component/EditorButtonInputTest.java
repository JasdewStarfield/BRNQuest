package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class EditorButtonInputTest {
    @Test void feedbackUsesTranslatedVisibleAreaAndExclusiveEdges() {
        var sounds = new ArrayList<UiRect>();
        var input = new EditorButtonInput(sounds::add);
        input.viewport(new UiRect(30, 10, 60, 30), 20);
        input.register(new UiRect(0, 0, 40, 40), true);
        input.clicked(25, 15, 0);
        input.clicked(35, 5, 0);
        input.clicked(60, 15, 0);
        assertTrue(sounds.isEmpty());
        input.clicked(35, 15, 0);
        assertEquals(1, sounds.size());
    }

    @Test void disabledForegroundAndRebuiltFramesCannotPlayUnderlyingButtons() {
        var sounds = new ArrayList<UiRect>();
        var input = new EditorButtonInput(sounds::add);
        var area = new UiRect(0, 0, 40, 20);
        input.register(area, true);
        input.register(area, false);
        input.clicked(10, 10, 0);
        assertTrue(sounds.isEmpty());
        input.begin();
        input.clicked(10, 10, 0);
        assertTrue(sounds.isEmpty());
        input.register(area, true);
        input.clicked(10, 10, 1);
        assertTrue(sounds.isEmpty());
        input.clicked(10, 10, 0);
        assertEquals(1, sounds.size());
    }

    @Test void keyboardCallbackReusesPointerFeedbackExactlyOnce() {
        var sounds = new ArrayList<UiRect>();
        var input = new EditorButtonInput(sounds::add);
        input.register(new UiRect(0, 0, 40, 20), true);
        assertTrue(input.keyPressed(258, false, ignored -> fail()));
        assertTrue(input.keyPressed(257, false, area -> input.clicked(area.centerX(), area.centerY(), 0)));
        assertEquals(1, sounds.size());
    }
}
