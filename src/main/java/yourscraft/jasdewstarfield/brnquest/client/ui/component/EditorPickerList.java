package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Search/header plus two-line choices, composed from the same list panel as other screens.
 * Filtering stays with the caller; selection returns the rendered key, never an index into new data.
 */
public final class EditorPickerList<K> {
    public static final int SEARCH_HEIGHT = 18;
    public static final int ROW_HEIGHT = 30;
    private static final int COLUMN_GAP = 4;

    /** Each column reserves room for the icon and a useful localized name; narrow windows stay single-column. */
    public static int iconColumns(int width) { return width >= 284 ? 2 : 1; }
    public enum Tone { NORMAL, WARNING, DISABLED }

    public record Entry(Component primary, Component secondary, Tone tone,
                        boolean selected, List<Component> tooltip) {
        public Entry { tooltip = List.copyOf(tooltip); }
        public Entry(Component primary, Component secondary, Tone tone) {
            this(primary, secondary, tone, false, List.of());
        }
    }

    private final EditorListPanel<K> list = new EditorListPanel<>();
    private UiRect bounds;

    /** Use whole rows where possible, but never force one row through a tiny window's footer. */
    public static UiRect rowsBounds(UiRect bounds) {
        int bottom = Math.max(bounds.top(), bounds.bottom() - 2);
        int top = Math.min(bottom, bounds.top() + SEARCH_HEIGHT + 2);
        int available = bottom - top;
        int height = available >= ROW_HEIGHT ? available / ROW_HEIGHT * ROW_HEIGHT : available;
        int left = Math.min(bounds.right(), bounds.left() + 2);
        return new UiRect(left, top, Math.max(left, bounds.right() - 6), top + height);
    }

    public EditorListPanel.Frame<K> advance(UiRect bounds, UiRect screenClip, int count,
                                             IntFunction<K> keyAt, double seconds, double speed) {
        // Fit the entire picker before calculating scroll extent, so a clipped bottom cannot hide
        // the final entries beyond the maximum scroll position on small GUI sizes.
        this.bounds = bounds.intersection(screenClip);
        return list.advance(rowsBounds(this.bounds), this.bounds, this.bounds.right() - 4,
                ROW_HEIGHT, 0, count, keyAt, seconds, speed);
    }

    /** Only visible choices are presented; returned hover text is drawn by the host above all panels. */
    public EditorListPanel.Frame<K> advanceIconChoices(UiRect bounds, UiRect screenClip, int count,
            IntFunction<K> keyAt, double seconds, double speed) {
        this.bounds = bounds.intersection(screenClip);
        UiRect rows = rowsBounds(this.bounds);
        return list.advanceGrid(rows, this.bounds, this.bounds.right() - 4, ROW_HEIGHT, 2,
                iconColumns(rows.width()), COLUMN_GAP, count, keyAt, seconds, speed);
    }

    /** Only visible choices are presented; returned hover text is drawn by the host above all panels. */
    public List<Component> render(GuiGraphics graphics, Font font, Component searchText, boolean showingHint,
                                   Function<K, Entry> presentation, Component emptyText, int mouseX, int mouseY) {
        return renderChoices(graphics, font, searchText, showingHint, presentation, null, emptyText, mouseX, mouseY);
    }

    /** Type pickers opt into icon rows without changing legacy entries or other selector layouts. */
    public List<Component> renderIconChoices(GuiGraphics graphics, Font font, Component searchText,
            Function<K, Entry> presentation, Function<K, EditorIcon> icons, Component emptyText, int mouseX, int mouseY) {
        return renderChoices(graphics, font, searchText, false, presentation, icons, emptyText, mouseX, mouseY);
    }

    private List<Component> renderChoices(GuiGraphics graphics, Font font, Component searchText, boolean showingHint,
            Function<K, Entry> presentation, Function<K, EditorIcon> icons, Component emptyText, int mouseX, int mouseY) {
        if (bounds == null || bounds.width() == 0 || bounds.height() == 0) return List.of();
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        try {
            if (icons != null) GraystoneSurface.raised(graphics, bounds, 0xFF383B35, true);
            else graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFA202832);
            graphics.fill(bounds.left() + 2, bounds.top() + 2, bounds.right() - 2,
                    Math.min(bounds.bottom(), bounds.top() + SEARCH_HEIGHT), 0xFF151A22);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            searchText.getString(), Math.max(0, bounds.width() - 12))),
                    bounds.left() + 6, bounds.top() + 6, showingHint ? 0xFF7F8B99 : 0xFFFFFFFF, false);
        } finally {
            graphics.disableScissor();
        }
        list.render(graphics, row -> {
            Entry entry = presentation.apply(row.key());
            UiRect rect = row.bounds();
            boolean hovered = row.visible().containsExclusive(mouseX, mouseY);
            if (icons != null) GraystoneSurface.raised(graphics, rect,
                    entry.selected() ? 0xFF68634A : hovered ? 0xFF5C6056 : 0xFF454940, entry.tone() != Tone.DISABLED);
            else graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(),
                    entry.selected() ? 0xFF385A72 : hovered ? 0xE0343D49 : 0xA02A323E);
            int primaryColor = switch (entry.tone()) {
                case NORMAL -> 0xFFFFFFFF;
                case WARNING -> 0xFFFFA070;
                case DISABLED -> 0xFF7F8B99;
            };
            if (icons != null) {
                int inset = rect.width() >= 36 ? 28 : 4;
                if (inset == 28) icons.apply(row.key()).render(graphics, font,
                        new UiRect(rect.left()+6, rect.top()+7, rect.left()+22, rect.top()+23), primaryColor);
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(entry.primary().getString(),
                        Math.max(0, rect.width()-inset-4))), rect.left()+inset, rect.top()+11, primaryColor, false);
                return;
            }
            int textWidth = Math.max(0, rect.width() - 8);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(entry.primary().getString(), textWidth)),
                    rect.left() + 4, rect.top() + 4, primaryColor, false);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(entry.secondary().getString(), textWidth)),
                    rect.left() + 4, rect.top() + 16, 0xFF9FB0C2, false);
        }, () -> {
            if (emptyText != null) graphics.drawCenteredString(font, emptyText,
                    bounds.centerX(), rowsBounds(bounds).top() + 7, 0xFF9AA6B5);
        });
        return entryAt(mouseX, mouseY).map(key -> presentation.apply(key).tooltip()).orElse(List.of());
    }

    public Optional<K> entryAt(double x, double y) { return list.rowAt(x, y).map(EditorListPanel.Row::key); }
    public boolean mouseClicked(double x, double y, int button) { return list.mouseClicked(x, y, button); }
    public boolean mouseScrolled(double x, double y, double delta, double step) { return list.mouseScrolled(x, y, delta, step); }
    public void reset() { list.reset(); bounds = null; }
    public void invalidate() { list.invalidate(); bounds = null; }
}
