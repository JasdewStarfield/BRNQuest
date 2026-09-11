package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared editor button presentation; actions and authority remain owned by the calling screen. */
public final class EditorButton {
    public static final int CONTENT_GAP = 4;

    /** Determines which visual slots are drawn without removing the semantic label. */
    public enum ContentMode { TEXT, ICON_AND_TEXT, ICON_ONLY }

    /**
     * Stable button semantics shared by rendering, Tooltip, and future narrator-backed widgets.
     * An icon-only button still requires a label so it never becomes inaccessible.
     */
    public record Definition(Component label, List<Component> tooltip,
                             EditorIcon icon, ContentMode contentMode) {
        public Definition {
            if (label == null || contentMode == null) {
                throw new IllegalArgumentException("Button label and content mode are required");
            }
            if (contentMode != ContentMode.TEXT && icon == null) {
                throw new IllegalArgumentException("Icon content modes require an icon");
            }
            tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        }

        public static Definition text(Component label, Component tooltip) {
            return new Definition(label, tooltip == null ? List.of() : List.of(tooltip),
                    null, ContentMode.TEXT);
        }

        public static Definition iconAndText(Component label, Component tooltip, EditorIcon icon) {
            return new Definition(label, tooltip == null ? List.of() : List.of(tooltip),
                    icon, ContentMode.ICON_AND_TEXT);
        }

        public static Definition iconOnly(Component accessibleLabel, Component tooltip, EditorIcon icon) {
            return new Definition(accessibleLabel, tooltip == null ? List.of() : List.of(tooltip),
                    icon, ContentMode.ICON_ONLY);
        }

        /** The visible label remains the narrator fallback even when only the icon is drawn. */
        public Component narration() {
            return label;
        }
    }

    /** Runtime visual state kept separate from stable button meaning. */
    public record State(boolean enabled, boolean hovered, boolean focused) {}

    /** Colors are supplied by each semantic surface instead of being hard-coded into the component. */
    public record Palette(int background, int hoveredBackground, int disabledBackground,
                          int text, int disabledText, int focus) {}

    /**
     * Shared semantic tones keep primary, secondary, and dangerous actions visually consistent.
     * Screens may still provide a custom palette when a specialized surface needs one.
     */
    public enum Tone {
        NEUTRAL(new Palette(0xFF575B51, 0xFF6B7064, 0xFF383B35,
                0xFFFFFFFF, 0xFF8793A1, 0xFFFFFFFF)),
        PRIMARY(new Palette(0xFF68634A, 0xFF807957, 0xFF383B35,
                0xFFFFFFFF, 0xFF8793A1, 0xFFFFFFFF)),
        SUCCESS(new Palette(0xFF3E735A, 0xFF4B8A6C, 0xFF2A323E,
                0xFFFFFFFF, 0xFF8793A1, 0xFFFFFFFF)),
        WARNING(new Palette(0xFFA06432, 0xFFB2743A, 0xFF2A323E,
                0xFFFFFFFF, 0xFF8793A1, 0xFFFFFFFF)),
        DANGER(new Palette(0xFF723E46, 0xFF8B4B56, 0xFF2A323E,
                0xFFFFFFFF, 0xFF8793A1, 0xFFFFFFFF));

        private final Palette palette;

        Tone(Palette palette) {
            this.palette = palette;
        }

        public Palette palette() {
            return palette;
        }
    }

    /** Pure geometry used by rendering and unit tests. A zero-width slot is not rendered. */
    public record ContentLayout(UiRect icon, UiRect label) {}

    private EditorButton() {}

    public static void render(GuiGraphics graphics, Font font, UiRect bounds, Definition definition,
                              State state, Palette palette) {
        int background = state.enabled()
                ? state.hovered() ? palette.hoveredBackground() : palette.background()
                : palette.disabledBackground();
        int foreground = state.enabled() ? palette.text() : palette.disabledText();
        GraystoneSurface.raised(graphics, bounds, background, state.enabled());
        // Accepted mouse and keyboard activations share a brief inset pulse without moving the hitbox.
        if (state.enabled() && EditorButtonFeedback.pressed(bounds)) {
            graphics.fill(bounds.left()+2, bounds.top()+2, bounds.right()-2, bounds.bottom()-2, 0x40202018);
            graphics.renderOutline(bounds.left()+1, bounds.top()+1,
                    Math.max(0,bounds.width()-2), Math.max(0,bounds.height()-2), 0xFF252721);
        }

        int iconWidth = definition.icon() == null ? 0 : definition.icon().width(font);
        int labelWidth = definition.contentMode() == ContentMode.ICON_ONLY ? 0 : font.width(definition.label());
        float labelScale = labelScale(bounds, definition.contentMode(), iconWidth, labelWidth, CONTENT_GAP);
        int scaledLabelWidth = (int) Math.ceil(labelWidth * labelScale);
        ContentLayout content = contentLayout(bounds, definition.contentMode(), iconWidth, scaledLabelWidth, CONTENT_GAP);
        if (content.icon().width() > 0) {
            definition.icon().render(graphics, font, content.icon(), foreground);
        }
        if (content.label().width() > 0) {
            // Compact localized labels shrink before truncation, retaining more meaning in narrow sidebars.
            int unscaledAvailable = Math.max(1, (int) Math.floor(content.label().width() / labelScale));
            Component visibleLabel = unscaledAvailable < labelWidth
                    ? Component.literal(font.plainSubstrByWidth(definition.label().getString(), unscaledAvailable))
                    : definition.label();
            graphics.pose().pushPose();
            graphics.pose().translate(content.label().left(), content.label().centerY(), 0);
            graphics.pose().scale(labelScale, labelScale, 1.0F);
            graphics.drawString(font, visibleLabel, 0, -font.lineHeight / 2, foreground, false);
            graphics.pose().popPose();
        }
        if (state.focused()) {
            // The outline is a shape cue, so keyboard focus is visible without color perception.
            graphics.renderOutline(bounds.left() - 1, bounds.top() - 1,
                    bounds.width() + 2, bounds.height() + 2, palette.focus());
            if (definition.contentMode() != ContentMode.ICON_ONLY && bounds.width() >= 32) {
                graphics.drawString(font, "›", bounds.left() + 2,
                        bounds.top() + Math.max(0, (bounds.height() - font.lineHeight) / 2),
                        palette.focus(), false);
            }
        }
    }

    /**
     * Renders a normal interactive button and returns its hover state for deferred Tooltip handling.
     * Action dispatch deliberately remains in the owning Screen.
     */
    public static boolean renderInteractive(GuiGraphics graphics, Font font, UiRect bounds,
                                            Definition definition, boolean enabled, boolean focused,
                                            Tone tone, double mouseX, double mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        render(graphics, font, bounds, definition, new State(enabled, hovered, focused), tone.palette());
        return hovered;
    }

    public static ContentLayout contentLayout(UiRect bounds, ContentMode mode,
                                              int iconWidth, int labelWidth, int gap) {
        int safeIconWidth = mode == ContentMode.TEXT ? 0 : Math.max(0, iconWidth);
        int safeLabelWidth = mode == ContentMode.ICON_ONLY ? 0 : Math.max(0, labelWidth);
        int safeGap = safeIconWidth > 0 && safeLabelWidth > 0 ? Math.max(0, gap) : 0;
        int totalWidth = Math.min(bounds.width(), safeIconWidth + safeGap + safeLabelWidth);
        int left = bounds.left() + Math.max(0, (bounds.width() - totalWidth) / 2);
        UiRect icon = new UiRect(left, bounds.top(), left + Math.min(safeIconWidth, totalWidth), bounds.bottom());
        int visibleGap = icon.width() > 0 && totalWidth > icon.width() ? Math.min(safeGap, totalWidth - icon.width()) : 0;
        int labelLeft = icon.right() + visibleGap;
        int remaining = Math.max(0, bounds.right() - labelLeft);
        UiRect label = new UiRect(labelLeft, bounds.top(), labelLeft + Math.min(safeLabelWidth, remaining), bounds.bottom());
        return new ContentLayout(icon, label);
    }

    /** Returns the exact icon slot used by rendering so optional hover actions never claim the label area. */
    public static UiRect iconBounds(Font font, UiRect bounds, Definition definition) {
        int iconWidth = definition.icon() == null ? 0 : definition.icon().width(font);
        int labelWidth = definition.contentMode() == ContentMode.ICON_ONLY ? 0 : font.width(definition.label());
        float scale = labelScale(bounds, definition.contentMode(), iconWidth, labelWidth, CONTENT_GAP);
        int scaledLabelWidth = (int) Math.ceil(labelWidth * scale);
        return contentLayout(bounds, definition.contentMode(), iconWidth, scaledLabelWidth, CONTENT_GAP).icon();
    }

    /** Computes the text-only part of a button without shrinking icons or click targets. */
    static float labelScale(UiRect bounds, ContentMode mode, int iconWidth, int labelWidth, int gap) {
        if (mode == ContentMode.ICON_ONLY || labelWidth <= 0) return 1.0F;
        int safeIconWidth = mode == ContentMode.TEXT ? 0 : Math.max(0, iconWidth);
        int safeGap = safeIconWidth > 0 ? Math.max(0, gap) : 0;
        int available = Math.max(0, bounds.width() - safeIconWidth - safeGap);
        return EditorTextLayout.fittedScale(labelWidth, available, 0.75F);
    }

}
