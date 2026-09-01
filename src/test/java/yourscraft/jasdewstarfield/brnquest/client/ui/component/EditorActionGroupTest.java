package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorActionGroupTest {
    private final List<String> invoked = new ArrayList<>();
    private static final UiRect CLIP = new UiRect(0, 0, 100, 40);

    private EditorActionGroup.Action<String> action(String key, boolean enabled) {
        return new EditorActionGroup.Action<>(key, EditorButton.Definition.iconOnly(Component.literal(key),
                Component.literal(key), EditorIcon.glyph(Component.literal("+"))), enabled,
                EditorButton.Tone.PRIMARY, (x, y) -> invoked.add(key));
    }

    @Test void visualAndTabOrderShareOneOrderedActionList() {
        EditorActionGroup<String> group = new EditorActionGroup<>();
        var actions = EditorActionGroup.trailing(CLIP, CLIP, 18, 2,
                List.of(action("edit", true), action("more", true)));
        assertEquals(62, actions.getFirst().bounds().left());
        assertEquals(82, actions.getLast().bounds().left());
        group.setActions(actions);
        assertTrue(group.focusNext(false));
        assertEquals("edit", group.focusedKey().orElseThrow());
        assertEquals("edit", group.narration().orElseThrow().getString());
        group.focusNext(false);
        assertEquals("more", group.focusedKey().orElseThrow());
        group.activateFocused();
        group.focusNext(true);
        group.activateFocused();
        assertEquals(List.of("more", "edit"), invoked);
    }

    @Test void hiddenAndDisabledButtonsAreNotFocusableButDisabledClicksAreConsumed() {
        EditorActionGroup<String> group = new EditorActionGroup<>();
        group.setActions(List.of(
                new EditorActionGroup.Placed<>(action("hidden", true), new UiRect(0, 50, 20, 70), CLIP),
                new EditorActionGroup.Placed<>(action("disabled", false), new UiRect(20, 0, 40, 20), CLIP),
                new EditorActionGroup.Placed<>(action("done", true), new UiRect(40, 0, 60, 20), CLIP)));
        group.focusNext(false);
        assertEquals("done", group.focusedKey().orElseThrow());
        assertTrue(group.mouseClicked(25, 5, 0));
        assertTrue(invoked.isEmpty());
        assertFalse(group.mouseClicked(5, 55, 0));
        assertFalse(group.mouseClicked(45, 5, 1));
    }

    @Test void clippedButtonsUseExclusiveHitEdges() {
        EditorActionGroup<String> group = new EditorActionGroup<>();
        group.setActions(List.of(new EditorActionGroup.Placed<>(action("half", true),
                new UiRect(10, 30, 30, 50), CLIP)));
        assertTrue(group.mouseClicked(10, 39.99, 0));
        assertEquals("half", group.tooltipAt(10, 39.99).getFirst().getString());
        assertFalse(group.mouseClicked(10, 40, 0));
        assertTrue(group.tooltipAt(10, 40).isEmpty());
        assertFalse(group.mouseClicked(30, 35, 0));
        assertEquals(List.of("half"), invoked);
    }

    @Test void stableKeysRetainFocusAfterReorderAndUseReplacementCallbacks() {
        EditorActionGroup<String> group = new EditorActionGroup<>();
        group.setActions(EditorActionGroup.trailing(CLIP, CLIP, 18, 2,
                List.of(action("alpha", true), action("beta", true))));
        group.focusNext(false);
        var replacement = new EditorActionGroup.Action<>("alpha", EditorButton.Definition.text(Component.literal("new"), null),
                true, EditorButton.Tone.NEUTRAL, (Double x, Double y) -> invoked.add("new alpha"));
        group.setActions(EditorActionGroup.trailing(CLIP, CLIP, 18, 2, List.of(action("beta", true), replacement)));
        group.activateFocused();
        assertEquals(List.of("new alpha"), invoked);
        group.setActions(List.of());
        assertTrue(group.focusedKey().isEmpty());
        assertFalse(group.activateFocused());
    }

    @Test void reverseTraversalStartsAtLastActionAndClearDropsCallbacks() {
        EditorActionGroup<String> group = new EditorActionGroup<>();
        group.setActions(EditorActionGroup.trailing(CLIP, CLIP, 18, 2,
                List.of(action("a", true), action("b", true))));
        group.focusNext(true);
        assertEquals("b", group.focusedKey().orElseThrow());
        group.clear();
        assertFalse(group.activateFocused());
        assertFalse(group.focusNext(false));
        assertFalse(group.mouseClicked(85, 5, 0));
    }

    @Test void duplicateKeysAreRejectedInsteadOfInvokingAnAmbiguousAction() {
        EditorActionGroup<String> group = new EditorActionGroup<>();
        assertThrows(IllegalArgumentException.class, () -> group.setActions(EditorActionGroup.trailing(CLIP, CLIP, 18, 2,
                List.of(action("same", true), action("same", true)))));
    }
}
