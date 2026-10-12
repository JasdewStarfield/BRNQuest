package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** More has its measured width on every window; hidden controls remain reachable through the menu. */
class TextEditorToolbarTest {
    private final List<TextEditorToolbar.Action> actions=List.of(
            new TextEditorToolbar.Action("BOLD",23),new TextEditorToolbar.Action("ITALIC",23),
            new TextEditorToolbar.Action("COLOR",23),new TextEditorToolbar.Action("UNDERLINE",23),
            new TextEditorToolbar.Action("STRIKETHROUGH",23),new TextEditorToolbar.Action("RESET",46));
    @Test void wideToolbarPromotesCommonActionsWithoutStretchingMore() {
        var layout=TextEditorToolbar.layout(new UiRect(10,20,610,40),100,40,actions);
        assertEquals(6,layout.actions().size());
        assertTrue(layout.contains("RESET"));
        assertEquals(40,layout.more().width());
        assertTrue(layout.more().right()<610);
    }
    @Test void narrowToolbarReservesMoreAndDoesNotOverlapActions() {
        var layout=TextEditorToolbar.layout(new UiRect(10,20,290,40),90,40,actions);
        assertTrue(layout.contains("BOLD")); assertTrue(layout.contains("COLOR"));
        assertFalse(layout.contains("RESET"));
        assertEquals(40,layout.more().width());
        int right=100;
        for(var action:layout.actions()) { assertTrue(action.bounds().left()>=right); right=action.bounds().right()+3; }
        assertTrue(layout.more().left()>=right);
        assertTrue(layout.more().right()<=290);
    }
    @Test void aLongLabelFallsBackWithoutHidingLaterShortActions() {
        var layout=TextEditorToolbar.layout(new UiRect(0,0,300,20),80,48,List.of(
                new TextEditorToolbar.Action("LONG",200),new TextEditorToolbar.Action("LINK",26)));
        assertFalse(layout.contains("LONG")); assertTrue(layout.contains("LINK"));
        assertEquals(48,layout.more().width());
    }
}
