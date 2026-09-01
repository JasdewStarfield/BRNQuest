package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A second, non-quest consumer assembles a preset picker without importing QuestScreen or editor state. */
class EditorListCompositionTest {
    private record Preset(String id, String label) {}
    private record ActionKey(String presetId, String intent) {}

    @Test void independentPresetPickerReusesListRowsActionsAndStableIds() {
        List<Preset> presets = List.of(new Preset("day", "Day layout"), new Preset("night", "Night layout"));
        List<String> applied = new ArrayList<>();
        EditorListPanel<String> panel = new EditorListPanel<>();
        EditorActionGroup<ActionKey> actions = new EditorActionGroup<>();
        var frame = panel.advance(new UiRect(20, 30, 300, 87), new UiRect(0, 0, 320, 240),
                302, 38, 2, presets.size(), i -> presets.get(i).id(), 0, 12);
        List<EditorActionGroup.Placed<ActionKey>> controls = new ArrayList<>();
        for (var row : frame.rows()) {
            var layout = EditorEntryRow.layout(row.bounds(), 1, 50, 2);
            var preset = presets.get(row.index());
            var content = new EditorEntryRow.Content(EditorIcon.glyph(Component.literal("☀")),
                    Component.literal(preset.label()), Component.literal("Local preview"), 0xFF9FB0C2);
            assertEquals(preset.label(), content.title().getString());
            assertTrue(layout.title().right() < layout.actions().left());
            var apply = new EditorActionGroup.Action<>(new ActionKey(row.key(), "apply"),
                    EditorButton.Definition.text(Component.literal("Apply"), null), true, EditorButton.Tone.SUCCESS,
                    (Double x, Double y) -> applied.add(row.key()));
            controls.addAll(EditorActionGroup.trailing(layout.actions(), row.visible(), 50, 2, List.of(apply)));
        }
        actions.setActions(controls);
        actions.focusNext(false);
        actions.activateFocused();
        assertTrue(actions.mouseClicked(260, 80, 0));
        assertEquals(List.of("day", "night"), applied);
        assertFalse(actions.mouseClicked(260, 87, 0));
        assertTrue(panel.rowAt(30, 90).isEmpty());
        // An icon's shared clipped rectangle can feed native Tooltip or any optional lookup adapter.
        var half = frame.rows().getLast();
        UiRect icon = EditorEntryRow.layout(half.bounds(), 1, 50, 2).icon();
        assertEquals(new UiRect(24, 78, 40, 87), half.clip(icon));
    }

    @Test void narrowRowsReserveActionsWithoutLettingTextRunUnderThem() {
        var layout = EditorEntryRow.layout(new UiRect(10, 10, 60, 46), 2, 18, 2);
        assertEquals(0, layout.title().width());
        assertEquals(0, layout.summary().width());
        assertEquals(0, layout.icon().width());
        assertEquals(38, layout.actions().width());
    }
}
