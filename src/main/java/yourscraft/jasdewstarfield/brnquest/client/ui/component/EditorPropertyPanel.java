package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * A form surface composed of rows and footer actions. It knows neither quests nor mutations.
 * Inputs are owned by EditorFormFields; custom rows can combine icons, toggles or selectors.
 */
public final class EditorPropertyPanel {
    @FunctionalInterface
    public interface RowContent {
        void render(GuiGraphics graphics, int left, int top, int width);
    }
    @FunctionalInterface
    public interface ButtonRenderer {
        void render(GuiGraphics graphics, UiRect bounds, Component text, boolean enabled, EditorButton.Tone tone);
    }
    public record Footer(UiRect cancel, UiRect done, Component doneLabel, boolean enabled, EditorButton.Tone tone) {}
    public record Layout(UiRect panel, int left, int width, int headingY, int firstRowY, int pitch) {
        public UiRect row(int index) {
            int top = firstRowY + Math.max(0, index) * pitch;
            return new UiRect(left, top, left + Math.max(0, width), top + EditorPropertyFormLayout.FIELD_HEIGHT);
        }
    }

    private EditorPropertyPanel() {}

    public static RowContent text(Font font, EditorTextField field, String label, int labelWidth,
                                  String issue, boolean enabled) {
        return (graphics, left, top, width) -> EditorPropertyRow.text(graphics, font,
                EditorPropertyFormLayout.row(left, top, width, labelWidth),
                Component.translatable(label), issue, field, enabled);
    }

    /** A lightweight section row shares the form's scroll and clipping instead of creating another panel. */
    public static RowContent section(Font font, String key) {
        return (g, x, y, w) -> {
            g.fill(x, y + 17, x + w, y + 18, 0xFF535647);
            g.drawString(font, Component.translatable(key), x, y + 4, GraystonePalette.ACCENT, false);
        };
    }

    public static RowContent readOnly(Font font, String label, Component value, int labelWidth) {
        return (graphics, left, top, width) -> EditorPropertyRow.readOnly(graphics, font,
                EditorPropertyFormLayout.row(left, top, width, labelWidth), Component.translatable(label), value);
    }

    public static void render(GuiGraphics graphics, Font font, Layout layout, Component heading,
                              int headingColor, List<RowContent> rows, Footer footer, ButtonRenderer buttons) {
        render(graphics, font, layout, heading, headingColor, rows, footer, buttons, false, GraystonePalette.PANEL, false);
    }

    /** Centered forms use the common graystone panel while retaining their existing geometry. */
    public static void renderCentered(GuiGraphics graphics, Font font, Layout layout, Component heading,
                                      int headingColor, List<RowContent> rows, Footer footer, ButtonRenderer buttons) {
        render(graphics, font, layout, heading, headingColor, rows, footer, buttons, true, GraystonePalette.PANEL, true);
    }

    /** New modal forms share the PNG graystone surface without changing older panel layouts. */
    public static void renderGraystone(GuiGraphics graphics, Font font, Layout layout, Component heading,
                                      int headingColor, List<RowContent> rows, Footer footer, ButtonRenderer buttons) {
        render(graphics, font, layout, heading, headingColor, rows, footer, buttons, true, GraystonePalette.PANEL, true);
    }

    private static void render(GuiGraphics graphics, Font font, Layout layout, Component heading,
                               int headingColor, List<RowContent> rows, Footer footer, ButtonRenderer buttons,
                               boolean centered, int backgroundColor, boolean graystone) {
        UiRect panel = layout.panel();
        if (graystone) GraystoneSurface.raised(graphics, panel, backgroundColor, true);
        else graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), backgroundColor);
        Component title = Component.literal(font.plainSubstrByWidth(heading.getString(), layout.width()));
        if (centered) graphics.drawCenteredString(font, title, panel.centerX(), layout.headingY(), headingColor);
        else graphics.drawString(font, title, layout.left(), layout.headingY(), headingColor, false);
        for (int index = 0; index < rows.size(); index++) {
            UiRect row = layout.row(index);
            rows.get(index).render(graphics, row.left(), row.top(), row.width());
        }
        buttons.render(graphics, footer.cancel(), Component.translatable("gui.cancel"), true, EditorButton.Tone.NEUTRAL);
        buttons.render(graphics, footer.done(), footer.doneLabel(), footer.enabled(), footer.tone());
    }
}
