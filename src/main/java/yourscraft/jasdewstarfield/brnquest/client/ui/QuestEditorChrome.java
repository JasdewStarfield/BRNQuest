package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonFeedback;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.List;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import java.util.Optional;

/**
 * Owns the editor toolbar layout, presentation, focus and pointer frame. It returns semantic intents;
 * the parent screen remains the sole owner of editor state transitions and network requests.
 */
final class QuestEditorChrome {
    private static final int EXIT_WIDTH = 96;
    private static final int SAVE_WIDTH = 72;
    private static final int PUBLISH_WIDTH = 92;
    private static final int HISTORY_WIDTH = 48;

    enum Action { OPEN_CATALOG, OPEN_LIVE, OPEN_ADVANCED, SAVE, REVIEW_PUBLISH, UNDO, REDO, TOGGLE_GRID_SNAP, OPEN_CLIENT_SETTINGS, EXIT }

    record Intent(Action action) {}

    record Model(QuestScreenFrameIdentity identity, String title, ResourceLocation bookId,
                 boolean allowed, boolean editing, boolean hasLease, boolean live, boolean busy, boolean dirty,
                 boolean canUndo, boolean canRedo, int undoSteps, int redoSteps,
                 boolean publishSurfaceReady, boolean historySurfaceReady,
                 Component status, boolean error, List<Component> errorTooltip, boolean snapToGrid) {
        Model {
            title = title == null ? "" : title;
            errorTooltip = errorTooltip == null ? List.of() : List.copyOf(errorTooltip);
        }
    }

    record Layout(UiRect topToolbar, UiRect bottomToolbar, UiRect title, UiRect exit,
                  UiRect save, UiRect publish, UiRect redo, UiRect undo, UiRect status, UiRect shortcuts, UiRect settings) {}

    record RenderResult(Component hoveredDetail, List<Component> tooltip) {
        RenderResult { tooltip = tooltip == null ? List.of() : List.copyOf(tooltip); }
    }

    record ClickResult(boolean consumed, Intent intent) {
        static ClickResult ignored() { return new ClickResult(false, null); }
        static ClickResult consumed(Intent intent) { return new ClickResult(true, intent); }
    }

    private record Frame(Model model, Layout layout) {}

    private Frame frame;
    private Action focused;

    Layout advance(QuestScreenLayout screen, Model model) {
        Layout layout = layout(screen, model.live(), model.hasLease(), model.allowed());
        frame = new Frame(model, layout);
        if (focused != null && !focusOrder(model).contains(focused)) focused = null;
        return layout;
    }

    RenderResult render(GuiGraphics graphics, Font font, QuestScreenLayout screen, Model model,
                        int mouseX, int mouseY) {
        Layout layout = advance(screen, model);
        graphics.fill(layout.topToolbar().left(), layout.topToolbar().top(),
                layout.topToolbar().right(), layout.topToolbar().bottom(), GraystonePalette.HEADER);
        graphics.fill(layout.bottomToolbar().left(), layout.bottomToolbar().top(),
                layout.bottomToolbar().right(), layout.bottomToolbar().bottom(), GraystonePalette.FOOTER);
        // A light beveled header and dark footer recover the prototype's hierarchy without moving controls.
        var top = layout.topToolbar();
        var bottom = layout.bottomToolbar();
        graphics.fill(top.left(), top.top(), top.right(), top.top()+1, GraystonePalette.HEADER_LIGHT);
        graphics.fill(top.left(), top.top(), top.left()+1, top.bottom()-1, GraystonePalette.HEADER_LIGHT);
        graphics.fill(top.right()-1, top.top()+1, top.right(), top.bottom()-1, GraystonePalette.HEADER_DARK);
        graphics.fill(top.left(), top.bottom()-2, top.right(), top.bottom()-1, GraystonePalette.HEADER_DARK);
        graphics.fill(top.left(), top.bottom()-1, top.right(), top.bottom(), GraystonePalette.SEAM);
        graphics.fill(bottom.left(), bottom.top(), bottom.right(), bottom.top()+1, GraystonePalette.SEAM);
        graphics.fill(bottom.left(), bottom.top()+1, bottom.right(), bottom.top()+2, GraystonePalette.LIP);
        String title = model.title().isBlank() ? model.bookId().toString() : model.title();
        String suffix = model.allowed() ? " ▾" : "";
        String visibleTitle = font.plainSubstrByWidth(title,
                Math.max(1, layout.title().width() - 16 - font.width(suffix)));
        // Dark lettering on light stone stays crisp without the default dark text shadow.
        String titleText = visibleTitle + suffix;
        graphics.drawString(font, titleText, layout.title().centerX() - font.width(titleText) / 2,
                layout.title().top() + 4, GraystonePalette.HEADER_TEXT, false);

        List<Component> tooltip = List.of();
        if (model.allowed() || model.hasLease() || model.busy()) {
            boolean active = model.editing() || model.hasLease();
            Component label = Component.translatable(active
                    ? "screen.brnquest.editor.exit" : "screen.brnquest.editor.edit_current");
            tooltip = renderButton(graphics, font, layout.exit(), EditorButton.Definition.text(label, active
                            ? Component.translatable("screen.brnquest.editor.exit.tooltip") : null),
                    true, active ? Action.EXIT : null, active ? EditorButton.Tone.PRIMARY : EditorButton.Tone.NEUTRAL,
                    mouseX, mouseY, tooltip);
        }
        if (!model.hasLease() && model.allowed()) {
            tooltip = renderButton(graphics, font, layout.save(), EditorButton.Definition.text(
                            Component.translatable("screen.brnquest.editor.live.advanced"),
                            Component.translatable("screen.brnquest.editor.live.advanced_hint")),
                    !model.busy(), Action.OPEN_ADVANCED, EditorButton.Tone.NEUTRAL, mouseX, mouseY, tooltip);
        }
        if (model.hasLease()) {
            if (!model.live()) {
                Component saveLabel = Component.translatable(model.dirty()
                        ? "screen.brnquest.editor.save" : "screen.brnquest.editor.saved");
                tooltip = renderButton(graphics, font, layout.save(), EditorButton.Definition.text(saveLabel,
                                Component.translatable("screen.brnquest.editor.save.tooltip")),
                        model.dirty() && !model.busy(), Action.SAVE, EditorButton.Tone.SUCCESS,
                        mouseX, mouseY, tooltip);
                tooltip = renderButton(graphics, font, layout.publish(), EditorButton.Definition.text(
                                Component.translatable("screen.brnquest.editor.publish"),
                                Component.translatable("screen.brnquest.editor.publish.tooltip")),
                        model.publishSurfaceReady(), Action.REVIEW_PUBLISH, EditorButton.Tone.WARNING,
                        mouseX, mouseY, tooltip);
            }
            tooltip = renderButton(graphics, font, layout.redo(), EditorButton.Definition.text(
                            Component.translatable("screen.brnquest.editor.redo", model.redoSteps()),
                            Component.translatable("screen.brnquest.editor.redo.tooltip")),
                    model.historySurfaceReady() && model.canRedo(), Action.REDO, EditorButton.Tone.PRIMARY,
                    mouseX, mouseY, tooltip);
            tooltip = renderButton(graphics, font, layout.undo(), EditorButton.Definition.text(
                            Component.translatable("screen.brnquest.editor.undo", model.undoSteps()),
                            Component.translatable("screen.brnquest.editor.undo.tooltip")),
                    model.historySurfaceReady() && model.canUndo(), Action.UNDO, EditorButton.Tone.PRIMARY,
                    mouseX, mouseY, tooltip);
        }

        // Compact shortcuts share one ordered strip; full configuration stays in the context menu.
        if (model.editing()) {
            Component snapLabel = snapLabel(model);
            tooltip = renderButton(graphics, font, shortcutBounds(layout, 0),
                    EditorButton.Definition.iconOnly(snapLabel,
                            snapLabel,
                            gridIcon(model.snapToGrid())),
                    !model.busy(), Action.TOGGLE_GRID_SNAP,
                    model.snapToGrid() ? EditorButton.Tone.PRIMARY : EditorButton.Tone.NEUTRAL,
                    mouseX, mouseY, tooltip);
        }

        Component settingsLabel = Component.translatable("screen.brnquest.client_settings");
        tooltip = renderButton(graphics, font, layout.settings(),
                EditorButton.Definition.iconOnly(settingsLabel, settingsLabel, gearIcon()),
                !model.busy(), Action.OPEN_CLIENT_SETTINGS, EditorButton.Tone.NEUTRAL,
                mouseX, mouseY, tooltip);

        if (model.status() != null && layout.status().width() > 0) {
            String statusText = font.plainSubstrByWidth(model.status().getString(), layout.status().width() - 10);
            int statusWidth = Math.min(layout.status().width(), font.width(statusText) + 10);
            UiRect visibleStatus = new UiRect(layout.status().left(), layout.status().top(),
                    layout.status().left() + statusWidth, layout.status().bottom());
            graphics.fill(visibleStatus.left(), visibleStatus.top(), visibleStatus.right(),
                    visibleStatus.bottom(), GraystonePalette.FOOTER);
            graphics.drawString(font, Component.literal(statusText), visibleStatus.left() + 5,
                    visibleStatus.top() + 4, model.error() ? 0xFFFF8B8B : GraystonePalette.SECONDARY, false);
            if (model.error() && visibleStatus.contains(mouseX, mouseY)) tooltip = model.errorTooltip();
        }
        Component hoveredDetail = layout.title().contains(mouseX, mouseY)
                ? Component.literal(model.bookId().toString()) : null;
        return new RenderResult(hoveredDetail, tooltip);
    }

    ClickResult click(QuestScreenFrameIdentity identity, double x, double y) {
        ClickResult result = resolveClick(identity, x, y);
        // Only accepted actions receive feedback; disabled and stale frames remain silent.
        if (result.intent() != null) EditorButtonFeedback.activate(actionBounds(result.intent().action()));
        return result;
    }

    private ClickResult resolveClick(QuestScreenFrameIdentity identity, double x, double y) {
        if (!accepts(identity)) return ClickResult.ignored();
        Model model = frame.model();
        Layout layout = frame.layout();
        if (layout.settings().contains(x, y)) {
            return ClickResult.consumed(model.busy() ? null : new Intent(Action.OPEN_CLIENT_SETTINGS));
        }
        if (model.editing() && shortcutBounds(layout, 0).contains(x, y)) {
            return ClickResult.consumed(model.busy() ? null : new Intent(Action.TOGGLE_GRID_SNAP));
        }
        if (model.allowed() && layout.title().contains(x, y)) {
            return ClickResult.consumed(model.busy() ? null : new Intent(Action.OPEN_CATALOG));
        }
        if ((model.allowed() || model.hasLease() || model.busy()) && layout.exit().contains(x, y)) {
            if (model.busy()) return ClickResult.consumed(null);
            return ClickResult.consumed(new Intent(model.hasLease() ? Action.EXIT : Action.OPEN_LIVE));
        }
        if (!model.hasLease() && model.allowed() && layout.save().contains(x, y)) {
            return ClickResult.consumed(model.busy() ? null : new Intent(Action.OPEN_ADVANCED));
        }
        if (!model.hasLease()) return ClickResult.ignored();
        if (!model.live() && layout.save().contains(x, y)) {
            return ClickResult.consumed(model.dirty() && !model.busy() ? new Intent(Action.SAVE) : null);
        }
        if (!model.live() && layout.publish().contains(x, y)) {
            return ClickResult.consumed(model.publishSurfaceReady() ? new Intent(Action.REVIEW_PUBLISH) : null);
        }
        if (layout.undo().contains(x, y)) return ClickResult.consumed(
                model.historySurfaceReady() && model.canUndo() ? new Intent(Action.UNDO) : null);
        if (layout.redo().contains(x, y)) return ClickResult.consumed(
                model.historySurfaceReady() && model.canRedo() ? new Intent(Action.REDO) : null);
        return ClickResult.ignored();
    }

    boolean focusNext(QuestScreenFrameIdentity identity, boolean backwards) {
        if (!accepts(identity) || frame.model().busy()) return false;
        List<Action> actions = focusOrder(frame.model());
        if (actions.isEmpty()) return false;
        int index = focused == null ? -1 : actions.indexOf(focused);
        int next = index < 0 ? (backwards ? actions.size() - 1 : 0)
                : Math.floorMod(index + (backwards ? -1 : 1), actions.size());
        focused = actions.get(next);
        return true;
    }

    Optional<Intent> activateFocused(QuestScreenFrameIdentity identity) {
        if (!accepts(identity) || frame.model().busy() || focused == null) return Optional.empty();
        UiRect bounds = actionBounds(focused);
        // Keyboard activation shares the pointer's enabled-state guard and one sound dispatch.
        return Optional.ofNullable(click(identity, bounds.centerX(), bounds.centerY()).intent());
    }

    private UiRect actionBounds(Action action) {
        Layout layout = frame.layout();
        return switch (action) {
            case OPEN_CATALOG -> layout.title();
            case EXIT, OPEN_LIVE -> layout.exit();
            case SAVE, OPEN_ADVANCED -> layout.save();
            case REVIEW_PUBLISH -> layout.publish();
            case UNDO -> layout.undo();
            case REDO -> layout.redo();
            case TOGGLE_GRID_SNAP -> shortcutBounds(layout, 0);
            case OPEN_CLIENT_SETTINGS -> layout.settings();
        };
    }

    Optional<Component> focusedLabel(QuestScreenFrameIdentity identity) {
        return accepts(identity) && focused != null ? Optional.of(focused == Action.TOGGLE_GRID_SNAP ? snapLabel(frame.model()) : Component.translatable(actionKey(focused)))
                : Optional.empty();
    }

    void invalidate() { frame = null; }

    static Layout layout(QuestScreenLayout screen, boolean live, boolean hasLease, boolean allowed) {
        int chromeHeight = QuestScreenLayout.EDITOR_CONTROL_HEIGHT;
        int right = screen.width() - 4;
        // Reserve the two-pixel footer seam plus one pixel for the keyboard focus outline.
        int buttonTop = screen.bottomToolbar().top() + 3;
        UiRect exit = new UiRect(right - EXIT_WIDTH, buttonTop, right, buttonTop + chromeHeight);
        UiRect save = new UiRect(exit.left() - SAVE_WIDTH - 4, exit.top(), exit.left() - 4, exit.bottom());
        UiRect publish = new UiRect(save.left() - PUBLISH_WIDTH - 4, save.top(), save.left() - 4, save.bottom());
        UiRect historyAnchor = live ? exit : publish;
        UiRect redo = new UiRect(historyAnchor.left() - HISTORY_WIDTH - 4, historyAnchor.top(),
                historyAnchor.left() - 4, historyAnchor.bottom());
        UiRect undo = new UiRect(redo.left() - HISTORY_WIDTH - 4, redo.top(), redo.left() - 4, redo.bottom());
        // Reserve equal margins so the centered title never overlaps the expandable shortcut strip.
        int titleWidth = Math.min(260, Math.max(0, screen.width() - 96));
        int center = screen.width() / 2;
        UiRect title = new UiRect(center - titleWidth / 2, 4, center + (titleWidth + 1) / 2, 4 + chromeHeight);
        UiRect leading = hasLease ? undo : allowed ? save : exit;
        int maximumWidth = screen.bottomStatusMaximumWidth(leading.left());
        // Status text shares the button baseline and stays below the footer seam.
        UiRect status = new UiRect(4, exit.top(), 4 + maximumWidth, exit.bottom());
        return new Layout(screen.topToolbar(), screen.bottomToolbar(), title, exit, save, publish, redo, undo, status,
                new UiRect(4, 2, Math.max(4, title.left() - 4), 18),
                new UiRect(screen.width() - 20, 2, screen.width() - 4, 18));
    }

    private static List<Action> focusOrder(Model model) {
        if (!model.hasLease()) return List.of(Action.OPEN_CLIENT_SETTINGS);
        List<Action> actions = model.live() ? List.of(Action.UNDO, Action.REDO, Action.TOGGLE_GRID_SNAP, Action.OPEN_CLIENT_SETTINGS, Action.EXIT)
                : List.of(Action.UNDO, Action.REDO, Action.REVIEW_PUBLISH, Action.SAVE, Action.TOGGLE_GRID_SNAP, Action.OPEN_CLIENT_SETTINGS, Action.EXIT);
        return actions.stream().filter(action -> action != Action.TOGGLE_GRID_SNAP || model.editing()).toList();
    }

    /** Each future shortcut occupies the next fixed slot without changing the title or footer. */
    static UiRect shortcutBounds(Layout layout, int index) {
        int left = layout.shortcuts().left() + index * 20;
        return new UiRect(left, layout.shortcuts().top(), left + 16, layout.shortcuts().bottom());
    }

    /** Pixel gear stays legible even when the selected font has no gear glyph. */
    private static EditorIcon gearIcon() {
        return new EditorIcon() {
            public int width(Font font) { return 10; }
            public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
                int x = bounds.centerX() - 5, y = bounds.centerY() - 5;
                graphics.fill(x + 2, y + 2, x + 8, y + 8, color);
                graphics.fill(x + 4, y, x + 6, y + 10, color);
                graphics.fill(x, y + 4, x + 10, y + 6, color);
                graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
                graphics.fill(x + 7, y + 1, x + 9, y + 3, color);
                graphics.fill(x + 1, y + 7, x + 3, y + 9, color);
                graphics.fill(x + 7, y + 7, x + 9, y + 9, color);
                graphics.fill(x + 4, y + 4, x + 6, y + 6, GraystonePalette.PANEL);
            }
        };
    }

    private static Component snapLabel(Model model) {
        return Component.translatable("screen.brnquest.editor.snap_to_grid",
                Component.translatable(model.snapToGrid() ? "options.on" : "options.off"));
    }

    /** Native pixel grid avoids font-dependent glyphs; the slash also distinguishes Off without color. */
    private static EditorIcon gridIcon(boolean enabled) {
        return new EditorIcon() {
            public int width(Font font) { return 10; }
            public void render(GuiGraphics graphics, Font font, UiRect bounds, int color) {
                int left = bounds.centerX() - 5, top = bounds.centerY() - 5;
                for (int offset = 0; offset <= 8; offset += 4) {
                    graphics.fill(left + offset, top, left + offset + 1, top + 9, color);
                    graphics.fill(left, top + offset, left + 9, top + offset + 1, color);
                }
                if (!enabled) {
                    for (int offset = 0; offset < 10; offset++)
                        graphics.fill(left + offset, top + 9 - offset, left + offset + 2, top + 10 - offset, 0xFFFF9B9B);
                }
            }
        };
    }

    private boolean accepts(QuestScreenFrameIdentity identity) {
        return frame != null && frame.model().identity().equals(identity);
    }

    private List<Component> renderButton(GuiGraphics graphics, Font font, UiRect bounds,
                                         EditorButton.Definition definition, boolean enabled, Action action,
                                         EditorButton.Tone tone, int mouseX, int mouseY, List<Component> previous) {
        boolean hovered = EditorButton.renderInteractive(graphics, font, bounds, definition, enabled,
                action != null && action == focused, tone, mouseX, mouseY);
        return hovered && !definition.tooltip().isEmpty() ? definition.tooltip() : previous;
    }

    static String actionKey(Action action) {
        return switch (action) {
            case SAVE -> "screen.brnquest.editor.save";
            case REVIEW_PUBLISH -> "screen.brnquest.editor.publish";
            case REDO -> "screen.brnquest.editor.redo.action";
            case UNDO -> "screen.brnquest.editor.undo.action";
            case OPEN_ADVANCED -> "screen.brnquest.editor.live.advanced";
            case OPEN_CATALOG -> "screen.brnquest.editor.catalog.open";
            case OPEN_LIVE -> "screen.brnquest.editor.edit_current";
            case EXIT -> "screen.brnquest.editor.exit";
            case TOGGLE_GRID_SNAP -> "screen.brnquest.editor.snap_to_grid";
            case OPEN_CLIENT_SETTINGS -> "screen.brnquest.client_settings";
        };
    }
}
