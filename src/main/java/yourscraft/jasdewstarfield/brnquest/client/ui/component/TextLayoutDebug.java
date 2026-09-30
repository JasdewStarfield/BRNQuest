package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Frame-local text probes. The disabled path collects nothing and ordinary rendering stays intact. */
public final class TextLayoutDebug {
    private record Hint(String original, double width, int limit, boolean truncated, boolean wrapped, String key) {}
    private record Slot(UiRect bounds, String source) {}
    private static final List<TextLayoutMeasurement> ENTRIES = new ArrayList<>();
    private static final Map<Object, Hint> HINTS = new IdentityHashMap<>();
    private static final Map<TextLayoutMeasurement, UiRect> EMPTY_LABEL_TARGETS = new IdentityHashMap<>();
    private static final Deque<Slot> SLOTS = new ArrayDeque<>();
    private static final Deque<UiRect> CLIPS = new ArrayDeque<>();
    private static Screen screen;
    private static boolean active, collecting, tooltip;
    private static Object trimmedResult;
    private static Hint trimmedHint;
    private static int muted;
    private record TooltipMeasurement(int originalWidth, int width, int height, int availableWidth,
                                      int availableHeight, int originalRows, int rows) {}
    private static TooltipMeasurement normalTooltip;

    private TextLayoutDebug() {}

    /** A screen-owned frame prevents vanilla/other mods' text from entering this diagnostic mode. */
    public static void begin(Screen next) {
        clear();
        screen = next;
        String name = next.getClass().getName();
        active = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.textLayoutDebug)
                && (name.startsWith("yourscraft.jasdewstarfield.brnquest.client.ui.")
                || name.startsWith("yourscraft.jasdewstarfield.brnquest.builtin.client."));
        collecting = active;
    }

    private static void clear() {
        ENTRIES.clear(); HINTS.clear(); EMPTY_LABEL_TARGETS.clear(); SLOTS.clear(); CLIPS.clear();
        trimmedResult = null; trimmedHint = null; muted = 0;
        active = false; collecting = false; tooltip = false;
        normalTooltip = null;
    }

    public static boolean collecting() { return collecting && muted == 0; }
    public static boolean active() { return active && Minecraft.getInstance().screen == screen; }
    public static boolean drawingTooltip() { return tooltip; }

    /** Suspended parents are only a backdrop; their labels must not receive child-screen hover. */
    public static void discardBackdrop() {
        if (active) { ENTRIES.clear(); HINTS.clear(); EMPTY_LABEL_TARGETS.clear(); trimmedResult = null; trimmedHint = null; normalTooltip = null; }
    }

    public static void mute() { if (active) muted++; }
    public static void unmute() { if (active && muted > 0) muted--; }

    /** Normal tooltips are measured before Pre can suppress them, so debug mode can report their final layout. */
    public static void normalTooltip(int originalWidth, int width, int height, int availableWidth,
                                      int availableHeight, int originalRows, int rows) {
        if (active && !tooltip) normalTooltip = new TooltipMeasurement(originalWidth, width, height,
                availableWidth, availableHeight, originalRows, rows);
    }

    public static void pushSlot(GuiGraphics graphics, UiRect bounds, String source) {
        if (active) SLOTS.push(new Slot(transform(graphics.pose().last().pose(), bounds), source));
    }
    public static void popSlot() { if (active && !SLOTS.isEmpty()) SLOTS.pop(); }
    public static void pushClip(int left, int top, int right, int bottom) {
        if (active) {
            UiRect clip = new UiRect(left, top, right, bottom);
            CLIPS.push(clip.intersection(CLIPS.isEmpty() ? viewport() : CLIPS.peek()));
        }
    }
    public static void popClip() { if (active && !CLIPS.isEmpty()) CLIPS.pop(); }

    /** Preserve the source before Font removes characters, including an empty visible result. */
    public static void trimmed(Font font, String original, int limit, String result) {
        if (!collecting()) return;
        trimmedResult = result;
        trimmedHint = new Hint(original, font.width(original), Math.max(0, limit),
                !original.equals(result), false, "");
        HINTS.put(result, trimmedHint);
    }

    public static void wrapped(Font font, FormattedText original, int limit, List<FormattedCharSequence> lines) {
        if (!collecting()) return;
        // Each rendered line has the wrap width, rather than the unwrapped paragraph's width.
        for (FormattedCharSequence line : lines) HINTS.put(line,
                new Hint(original.getString(), font.width(line), Math.max(0, limit), false, true, key(original)));
    }

    /** Components often wrap a freshly truncated String; transfer its probe to the actual visual sequence. */
    public static void component(Component text) {
        if (!collecting()) return;
        String value = text.getString();
        Hint hint = !(text.getContents() instanceof TranslatableContents) && text.getSiblings().isEmpty()
                && trimmedResult instanceof String result && result.equals(value) ? trimmedHint : null;
        if (hint != null) {
            HINTS.put(text.getVisualOrderText(), hint);
            trimmedResult = null; trimmedHint = null;
        } else if (!key(text).isEmpty()) {
            HINTS.put(text.getVisualOrderText(), new Hint(value, -1, -1, false, false, key(text)));
        }
        // A different Component draw ends the pending association, even if it happens to share the same wording.
        trimmedResult = null; trimmedHint = null;
    }

    public static String plain(FormattedCharSequence sequence) {
        StringBuilder value = new StringBuilder();
        sequence.accept((index, style, codePoint) -> { value.appendCodePoint(codePoint); return true; });
        return value.toString();
    }

    /** Called at the two final GuiGraphics text overloads, avoiding duplicate Component/int delegates. */
    public static void capture(GuiGraphics graphics, Font font, Object identity, String visible,
                               double x, double y, double measured, boolean shadow) {
        if (!collecting()) return;
        Hint hint = HINTS.get(identity);
        // Centered Component drawing delegates straight to a visual sequence, bypassing the Component overload.
        if (hint == null && trimmedResult instanceof String result && result.equals(visible)) hint = trimmedHint;
        trimmedResult = null; trimmedHint = null;
        Matrix4f pose = graphics.pose().last().pose();
        double sx = Math.hypot(pose.m00(), pose.m01()), sy = Math.hypot(pose.m10(), pose.m11());
        // Compare font advances and line height consistently; a drop shadow is decoration, not label content.
        UiRect drawn = rectangle(pose, x, y, measured, font.lineHeight);
        UiRect available = SLOTS.isEmpty() ? viewport() : SLOTS.peek().bounds();
        String source = SLOTS.isEmpty() ? "boundary" : SLOTS.peek().source();
        boolean known = !SLOTS.isEmpty();
        if (hint != null && hint.limit() >= 0) {
            available = rectangle(pose, x, y, hint.limit(), font.lineHeight);
            source = hint.wrapped() ? "wrap" : "trim";
            known = true;
        }
        available = available.intersection(viewport());
        if (!CLIPS.isEmpty()) available = available.intersection(CLIPS.peek());
        String full = hint == null ? visible : hint.original();
        if (full.isEmpty()) return;
        if (hint != null && !hint.key().isEmpty()) source += " / " + hint.key();
        double required = hint == null || hint.width() < 0 ? measured : hint.width();
        ENTRIES.add(new TextLayoutMeasurement(full, source, drawn, available, required * sx,
                font.lineHeight * sy, measured * sx, font.lineHeight * sy, sx, sy,
                hint != null && hint.truncated(), hint != null && hint.wrapped(), known));
    }

    /** Explicit shared renderers know both the source and the complete text-only slot before fitting. */
    public static void fitted(GuiGraphics graphics, Font font, Component full, String visible,
                              UiRect slot, double x, double y, float scale, String source) {
        if (!collecting()) return;
        fittedMeasured(graphics, font, full, visible, font.width(visible), slot, x, y, scale, source);
    }

    /** Styled button labels use their actual visual sequence width, including bold/custom-font advances. */
    public static void fitted(GuiGraphics graphics, Font font, Component full, Component visible,
                              UiRect slot, double x, double y, float scale, String source) {
        if (!collecting()) return;
        fittedMeasured(graphics, font, full, visible.getString(), font.width(visible), slot, x, y, scale, source);
    }

    private static void fittedMeasured(GuiGraphics graphics, Font font, Component full, String visible,
                                        double visibleWidth, UiRect slot, double x, double y, float scale, String source) {
        Matrix4f pose = graphics.pose().last().pose();
        double sx = Math.hypot(pose.m00(), pose.m01()), sy = Math.hypot(pose.m10(), pose.m11());
        UiRect available = transform(pose, slot).intersection(viewport());
        if (!CLIPS.isEmpty()) available = available.intersection(CLIPS.peek());
        if (!key(full).isEmpty()) source += " / " + key(full);
        ENTRIES.add(new TextLayoutMeasurement(full.getString(), source,
                rectangle(pose, x, y, visibleWidth * scale, font.lineHeight * scale), available,
                font.width(full) * sx, font.lineHeight * sy, visibleWidth * scale * sx,
                font.lineHeight * scale * sy, scale * sx, scale * sy, !full.getString().equals(visible), false, true));
    }

    /** A button whose text slot vanished can still expose that zero-space measurement on its click target. */
    public static void emptyButton(GuiGraphics graphics, Font font, Component full, UiRect bounds) {
        if (!collecting() || full.getString().isEmpty()) return;
        fitted(graphics, font, full, "", new UiRect(bounds.right(), bounds.top(), bounds.right(), bounds.bottom()),
                bounds.right(), bounds.top(), 1, "editor_button");
        UiRect target = transform(graphics.pose().last().pose(), bounds).intersection(viewport());
        if (!CLIPS.isEmpty()) target = target.intersection(CLIPS.peek());
        EMPTY_LABEL_TARGETS.put(ENTRIES.getLast(), target);
    }

    private static String key(FormattedText text) {
        return text instanceof Component component && component.getContents() instanceof TranslatableContents contents
                ? contents.getKey() : "";
    }

    private static UiRect viewport() { return new UiRect(0, 0, screen.width, screen.height); }
    private static UiRect transform(Matrix4f pose, UiRect rect) {
        return rectangle(pose, rect.left(), rect.top(), rect.width(), rect.height());
    }

    /** Four corners keep hover geometry correct for translated/scaled (and rotated) canvas text. */
    private static UiRect rectangle(Matrix4f pose, double x, double y, double width, double height) {
        double left = Double.POSITIVE_INFINITY, top = left, right = Double.NEGATIVE_INFINITY, bottom = right;
        for (double dx : new double[] {0, width}) for (double dy : new double[] {0, height}) {
            Vector3f point = pose.transformPosition(new Vector3f((float)(x + dx), (float)(y + dy), 0));
            left = Math.min(left, point.x); right = Math.max(right, point.x);
            top = Math.min(top, point.y); bottom = Math.max(bottom, point.y);
        }
        return new UiRect((int)Math.floor(left), (int)Math.floor(top), (int)Math.ceil(right), (int)Math.ceil(bottom));
    }

    /** Render last and stop collecting first, so the diagnostic never measures its own tooltip. */
    public static void finish(GuiGraphics graphics, int mouseX, int mouseY) {
        collecting = false;
        if (!active || Screen.hasShiftDown()) return;
        TextLayoutMeasurement selected = TextLayoutMeasurement.pick(ENTRIES, mouseX, mouseY);
        for (int index = ENTRIES.size() - 1; selected == null && index >= 0; index--) {
            TextLayoutMeasurement entry = ENTRIES.get(index);
            UiRect emptyTarget = EMPTY_LABEL_TARGETS.get(entry);
            if (emptyTarget != null && emptyTarget.containsExclusive(mouseX, mouseY)) {
                selected = entry; break;
            }
        }
        if (selected == null && normalTooltip == null) return;
        var font = Minecraft.getInstance().font;
        var lines = new ArrayList<Component>();
        lines.add(label("title", screen.getClass().getSimpleName()));
        lines.add(label("context", Minecraft.getInstance().getLanguageManager().getSelected(),
                screen.width, screen.height, number(Minecraft.getInstance().getWindow().getGuiScale())));
        if (selected != null) {
            // Long quest descriptions must not push the diagnostic numbers off the screen.
            String preview = selected.text().replace("\n", " ↵ ").replace("\r", "");
            int codePoints = preview.codePointCount(0, preview.length());
            if (codePoints > 120) preview = preview.substring(0, preview.offsetByCodePoints(0, 120)) + "…";
            lines.add(label(codePoints > 120 ? "text_preview" : "text", preview));
            lines.add(label("source", selected.source()));
            lines.add(label("available", selected.available().width(), selected.available().height(),
                    selected.available().left(), selected.available().top()));
            lines.add(label("required", number(selected.requiredWidth()), number(selected.requiredHeight())));
            lines.add(label("rendered", number(selected.renderedWidth()), number(selected.renderedHeight())));
            lines.add(label("scale", number(selected.scaleX()), number(selected.scaleY())));
            if (selected.knownSlot()) {
                lines.add(label("overflow", number(selected.widthOverflow()), number(selected.heightOverflow()),
                        Double.isFinite(selected.occupancyPercent()) ? number(selected.occupancyPercent()) + "%" : "∞"));
            } else lines.add(label("unknown"));
            var states = new ArrayList<Component>();
            if (selected.overflow()) states.add(label("state.overflow"));
            if (selected.truncated()) states.add(label("state.truncated"));
            if (selected.clipped()) states.add(label("state.clipped"));
            if (selected.wrapped()) states.add(label("state.wrapped"));
            if (selected.scaleX() < 0.999 || selected.scaleY() < 0.999) states.add(label("state.scaled"));
            if (states.isEmpty()) states.add(label(selected.knownSlot() ? "state.fits" : "state.measured"));
            Component state = Component.empty();
            for (Component value : states) state = state.copy().append(value).append(" ");
            lines.add(label("state", state));
        }
        if (normalTooltip != null) {
            lines.add(label("tooltip_size", normalTooltip.originalWidth(), normalTooltip.width(), normalTooltip.height(),
                    normalTooltip.originalRows(), normalTooltip.rows()));
            lines.add(label("tooltip_overflow", Math.max(0, normalTooltip.width() - normalTooltip.availableWidth()),
                    Math.max(0, normalTooltip.height() - normalTooltip.availableHeight())));
        }
        lines.add(label("shift_hint"));
        lines.add(label("summary", ENTRIES.size(), ENTRIES.stream().filter(e -> e.overflow() || e.truncated() || e.clipped()).count()));
        List<FormattedCharSequence> wrapped = lines.stream().flatMap(line ->
                font.split(line, Math.max(40, Math.min(340, screen.width - 24))).stream()).toList();
        tooltip = true;
        try { graphics.renderTooltip(font, wrapped, mouseX, mouseY); }
        finally { tooltip = false; }
    }

    private static Component label(String suffix, Object... args) {
        return Component.translatable("screen.brnquest.text_debug." + suffix, args);
    }
    private static String number(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
