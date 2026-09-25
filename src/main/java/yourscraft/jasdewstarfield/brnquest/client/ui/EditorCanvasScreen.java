package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.CanvasScene;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonWidget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorEnumDropdown;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import java.util.*;
import java.util.function.Predicate;

/** Artwork working copy: apply commits the complete change once; cancel never mutates the shared book. */
final class EditorCanvasScreen extends Screen {
    private final String serverContext;
    private final Screen parent;
    private final ResourceLocation book;
    private final boolean bookScope;
    private final Predicate<CanvasScene> commit;
    private final List<CanvasScene.Decoration> decorations;
    private CanvasScene.Background canvas, screen;
    private Boolean screenAbove;
    private int selected, tab;
    private final Map<String, EditBox> fields = new LinkedHashMap<>();
    private boolean aspect = true, locked;
    private boolean linkingDimensions;
    private double aspectRatio;
    private CanvasScene.Fit fit = CanvasScene.Fit.TILE;
    private CanvasScene previewScene;
    private EditorButtonWidget modeButton, fitButton;
    private final EditorEnumDropdown enumDropdown = new EditorEnumDropdown();
    private int backgroundMode; // 0 inherits, 1 explicitly disables, 2 uses this resource.
    private String error = "";
    private int formScroll;
    private int helpScroll;
    private boolean buildingForm;
    private final Map<net.minecraft.client.gui.components.AbstractWidget, Integer> formWidgets = new LinkedHashMap<>();

    EditorCanvasScreen(Screen parent, String serverContext, ResourceLocation book, boolean bookScope, CanvasScene scene,
                       int initialTab, ResourceLocation decorationId, Predicate<CanvasScene> commit) {
        super(label(initialTab == 0 ? "properties" : "backgrounds"));
        this.parent = parent; this.serverContext = serverContext; this.book = book; this.bookScope = bookScope; this.commit = commit;
        decorations = new ArrayList<>(scene.decorations()); canvas = scene.canvas(); screen = scene.screen();
        // A property page owns exactly one object; it never exposes another decoration or background.
        selected = -1;
        for (int i = 0; i < decorations.size(); i++) if (decorations.get(i).id().equals(decorationId)) selected = i;
        tab = initialTab;
        previewScene = scene; screenAbove = scene.screenAbove();
        var background = tab == 1 ? canvas : screen;
        backgroundMode = background == null ? 0 : background.texture().isEmpty() ? 1 : 2;
        fit = background == null ? CanvasScene.Fit.TILE : background.fit();
    }
    static Component label(String key) { return Component.translatable("screen.brnquest.canvas." + key); }
    private int left() { return Math.max(140, width / 3); }
    /** Keep property labels, controls and thumbnails inset from the shared panel border. */
    private int contentLeft() { return left() + 10; }
    private Button button(String key, int x, int y, int w, Runnable action) {
        var widget = addRenderableWidget(Button.builder(label(key), b -> action.run()).bounds(x, y, w, 20).build());
        if (buildingForm && x >= left()) formWidgets.put(widget, y);
        return widget;
    }
    private EditorButtonWidget choiceButton(String key, int x, int y, int w, Runnable action) {
        var widget = addRenderableWidget(new EditorButtonWidget(x, y, w, 20,
                EditorButton.Definition.iconAndText(label(key), null, QuestActionIcons.named("unfold")), b -> action.run()));
        formWidgets.put(widget, y);
        return widget;
    }
    @Override protected void init() {
        enumDropdown.close();
        // Resize preserves raw input, including temporarily invalid numeric text.
        var retained = new LinkedHashMap<String, String>(); fields.forEach((k, v) -> retained.put(k, v.getValue()));
        fields.clear(); formWidgets.clear();
        buildingForm = true;
        if (tab == 0) initDecorations(); else initBackground();
        buildingForm = false;
        positionForm();
        retained.forEach((k, v) -> { if (fields.containsKey(k)) fields.get(k).setValue(v); });
        if (tab != 0) updateBackgroundControls();
        if (tab != 0) {
            button(tab == 1 ? "target_canvas" : "target_screen", contentLeft(), 6, width - contentLeft() - 12, () -> {
                // Switching only flushes into this screen's working copy; both targets share Apply/Cancel.
                if (!flush()) return;
                tab = tab == 1 ? 2 : 1;
                var background = tab == 1 ? canvas : screen;
                backgroundMode = background == null ? 0 : background.texture().isEmpty() ? 1 : 2;
                fit = background == null ? CanvasScene.Fit.TILE : background.fit();
                fields.clear(); formScroll = 0; helpScroll = 0; rebuildWidgets();
            });
            button(screenAbove == null ? "order_inherit" : screenAbove ? "order_screen" : "order_canvas",
                    12, height - 58, left() - 28, () -> {
                screenAbove = screenAbove == null ? Boolean.FALSE : screenAbove ? null : Boolean.TRUE;
                // Order belongs to the pair and remains visible outside both scrolling form columns.
                rebuildWidgets();
            });
        }
        button("cancel", 8, height - 26, 80, this::onClose);
        button("apply", width - 98, height - 26, 90, () -> {
            if (flush()) {
                if (commit.test(new CanvasScene(decorations, canvas, screen, screenAbove))) onClose();
                else error = label("stale").getString();
            }
        });
    }
    private void field(String key, String value, int row) {
        var box = new EditBox(font, contentLeft() + 64, 57 + row * 22, Math.max(36, width - contentLeft() - 76), 20, label(key));
        box.setMaxLength(key.equals("texture") ? 512 : 32); box.setValue(value);
        fields.put(key, box); addRenderableWidget(box); formWidgets.put(box, box.getY());
    }
    private void initDecorations() {
        if (selected < 0 || selected >= decorations.size()) return;
        var d = decorations.get(selected); aspect = d.aspectLocked(); locked = d.locked();
        field("texture", d.texture(), 0); field("x", "" + d.x(), 1); field("y", "" + d.y(), 2);
        field("width", "" + d.width(), 3); field("height", "" + d.height(), 4); field("layer", "" + d.layer(), 5);
        aspectRatio = d.width() / d.height();
        // In proportional mode either dimension drives the other immediately; there is no hidden width priority.
        fields.get("width").setResponder(value -> linkDimension("width", "height", value));
        fields.get("height").setResponder(value -> linkDimension("height", "width", value));
        int y = 57 + 6 * 22, half = (width - contentLeft() - 12) / 2;
        button(aspect ? "aspect_on" : "aspect_off", contentLeft(), y, half, () -> {
            if (flush()) { var v = decorations.get(selected); decorations.set(selected, new CanvasScene.Decoration(v.id(), v.texture(), v.x(), v.y(), v.width(), v.height(), !aspect, v.layer(), locked)); fields.clear(); rebuildWidgets(); }
        });
        button(locked ? "locked" : "unlocked", contentLeft() + half + 2, y, half, () -> {
            if (flush()) { var v = decorations.get(selected); decorations.set(selected, new CanvasScene.Decoration(v.id(), v.texture(), v.x(), v.y(), v.width(), v.height(), aspect, v.layer(), !locked)); fields.clear(); rebuildWidgets(); }
        });
        button("browse", contentLeft(), y + 22, half, () -> {
            if (flush()) minecraft.setScreen(new EditorTextureBrowserScreen(this, texture -> {
                var v = decorations.get(selected);
                decorations.set(selected, new CanvasScene.Decoration(v.id(), texture, v.x(), v.y(), v.width(), v.height(), aspect, v.layer(), locked)); fields.clear();
            }));
        });
        button("copy", contentLeft() + half + 2, y + 22, half, () -> {
            if (flush()) { EditorDecorationClipboard.copy(serverContext, book, decorations.get(selected)); fields.clear(); rebuildWidgets(); }
        });
    }
    private void linkDimension(String source, String target, String raw) {
        if (!aspect || linkingDimensions) return;
        try {
            double value = Double.parseDouble(raw);
            double linked = source.equals("width") ? value / aspectRatio : value * aspectRatio;
            if (!Double.isFinite(value) || value < 0.05 || value > 1024 || linked < 0.05 || linked > 1024) return;
            linkingDimensions = true;
            fields.get(target).setValue(Double.toString(linked));
        } catch (NumberFormatException unfinished) { /* Leave partial typing untouched until it becomes valid. */ }
        finally { linkingDimensions = false; }
    }
    private void initBackground() {
        var b = tab == 1 ? canvas : screen;
        field("texture", b == null ? "" : b.texture(), 0);
        field("opacity", b == null ? "0.5" : "" + b.opacity(), 1);
        field("scale", b == null ? "1.0" : "" + b.scale(), 2);
        fields.get("scale").setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("scale_help")));
        modeButton = choiceButton("inherit", contentLeft(), 130, width - contentLeft() - 12, () -> {
            openChoice(modeButton, List.of("inherit", "disabled", "custom"), EditorCanvasScreen::label, value -> {
                // Changing mode retains the custom candidate; only Browse opens the resource picker.
                backgroundMode = List.of("inherit", "disabled", "custom").indexOf(value);
                updateBackgroundControls();
            });
        });
        fitButton = choiceButton("fit_" + fit.name().toLowerCase(Locale.ROOT), contentLeft(), 154,
                width - contentLeft() - 12, () -> {
            openChoice(fitButton, Arrays.stream(CanvasScene.Fit.values()).map(Enum::name).toList(),
                    value -> label("fit_" + value.toLowerCase(Locale.ROOT)), value -> {
                        fit = CanvasScene.Fit.valueOf(value);
                        fitButton.setMessage(label("fit_" + value.toLowerCase(Locale.ROOT)));
                    });
        });
        button("browse", contentLeft(), 178, width - contentLeft() - 12, () -> minecraft.setScreen(new EditorTextureBrowserScreen(this, this::setBackgroundTexture)));
        // Reserve the thumbnail in the same scroll range as its controls.
        var previewSpace = button("texture_preview", contentLeft(), 204, width - contentLeft() - 12, () -> {});
        previewSpace.setHeight(112); previewSpace.active = false;
        // This widget reserves scroll geometry only; the thumbnail renders its single heading separately.
        previewSpace.setMessage(Component.empty());
    }
    private void updateBackgroundControls() {
        modeButton.setMessage(label(switch (backgroundMode) { case 0 -> "inherit"; case 1 -> "disabled"; default -> "custom"; }));
        fields.values().forEach(field -> field.active = backgroundMode == 2);
    }
    private void openChoice(EditorButtonWidget button, List<String> choices,
                            java.util.function.Function<String, Component> labels,
                            java.util.function.Consumer<String> selection) {
        enumDropdown.show(new UiRect(button.getX(), button.getY(), button.getX() + button.getWidth(),
                button.getY() + button.getHeight()), choices, labels, selection);
        button.setIcon(QuestActionIcons.named("fold"));
    }

    private void closeChoice() {
        enumDropdown.close();
        if (modeButton != null) modeButton.setIcon(null);
        if (fitButton != null) fitButton.setIcon(null);
    }
    /** Invalid in-progress input retains the last valid preview, but cannot be applied. */
    private CanvasScene.Background backgroundCandidate() {
        if (backgroundMode == 0) return null;
        if (backgroundMode == 1) return new CanvasScene.Background("", fit, 1);
        String texture = fields.get("texture").getValue().strip();
        if (texture.isEmpty()) throw new IllegalArgumentException("Choose a custom texture");
        return new CanvasScene.Background(texture, fit, number("opacity"), number("scale"));
    }
    private void setBackgroundTexture(String texture) {
        double opacity = 0.5;
        try { opacity = Math.max(0, Math.min(1, Double.parseDouble(fields.get("opacity").getValue()))); } catch (RuntimeException ignored) {}
        if (!Double.isFinite(opacity)) opacity = 0.5;
        backgroundMode = 2;
        double scale = 1;
        try { scale = number("scale"); } catch (RuntimeException ignored) {}
        if (!Double.isFinite(scale) || scale < 0.25 || scale > 8) scale = 1;
        var b = new CanvasScene.Background(texture, fit, opacity, scale);
        if (tab == 1) canvas = b; else screen = b;
        fields.clear();
    }
    /** Validate the visible working copy before its single atomic commit. */
    private boolean flush() {
        error = "";
        try {
            if (fields.isEmpty()) return true;
            if (tab == 0 && selected >= 0) {
                var d = decorations.get(selected);
                double w = number("width"), h = number("height");
                if (aspect && Math.abs(w / h - aspectRatio) > 1e-8 * Math.max(1, aspectRatio))
                    throw new IllegalArgumentException("Linked dimensions exceed the allowed range");

                decorations.set(selected, new CanvasScene.Decoration(d.id(), fields.get("texture").getValue().strip(), number("x"), number("y"),
                        w, h, aspect, Integer.parseInt(fields.get("layer").getValue()), locked));
                fields.get("width").setValue("" + w); fields.get("height").setValue("" + h);
            } else if (tab != 0) {
                var b = backgroundCandidate();
                if (tab == 1) canvas = b; else screen = b;
            }
            new CanvasScene(decorations, canvas, screen, screenAbove).encode(); // Fail locally before dispatching an oversized replacement.
            return true;
        } catch (RuntimeException invalid) { error = label("invalid").getString(); return false; }
    }
    private double number(String key) { return Double.parseDouble(fields.get(key).getValue()); }
    /** Only the property column scrolls; list navigation and apply/cancel remain reachable in small windows. */
    private void positionForm() {
        int bottom = formWidgets.entrySet().stream().mapToInt(entry -> entry.getValue() + entry.getKey().getHeight()).max().orElse(54);
        formScroll = Math.clamp(formScroll, 0, Math.max(0, bottom - (height - 36)));
        formWidgets.forEach((widget, y) -> {
            widget.setY(y - formScroll);
            widget.visible = widget.getY() >= 54 && widget.getY() + widget.getHeight() <= height - 34;
            if (!widget.visible && getFocused() == widget) setFocused(null);
        });
    }
    private List<net.minecraft.util.FormattedCharSequence> helpLines() {
        var lines = new ArrayList<>(font.split(label(tab == 1 ? "canvas_help" : "screen_help"), left() - 32));
        lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
        lines.addAll(font.split(label(bookScope ? "book_help" : "chapter_help"), left() - 32));
        return lines;
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (enumDropdown.open()) closeChoice();
        if (tab != 0 && x >= 8 && x < left() && y >= 54 && y < height - 34) {
            helpScroll = Math.clamp(helpScroll - (int)(vertical * 3), 0,
                    Math.max(0, helpLines().size() - Math.max(1, (height - 128) / 11)));
            return true;
        }
        if (x >= left() && y >= 54 && y < height - 34) {
            formScroll -= (int)(vertical * 22); positionForm(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public void tick() { parent.tick(); }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float dt) {}
    @Override public void render(GuiGraphics g, int x, int y, float dt) {
        if (tab != 0) {
            try {
                var candidate = backgroundCandidate();
                previewScene = new CanvasScene(List.of(), tab == 1 ? candidate : canvas, tab == 2 ? candidate : screen, screenAbove);
            } catch (RuntimeException invalidInput) { /* Retain the last valid frame while a number or ID is being typed. */ }
            if (parent instanceof QuestScreen quest) quest.renderBackgroundPreview(g, width, height, dt, previewScene, bookScope);
            else if (parent instanceof EditorBookPropertiesScreen properties) properties.renderBackgroundPreview(g, width, height, dt, previewScene);
            // Start a new depth layer above the preview's item icons, without clearing its color image.
            g.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, net.minecraft.client.Minecraft.ON_OSX);
        }
        g.fill(0, 0, width, height, tab == 0 ? 0xFF292D27 : 0x30292D27);
        // Separate the current object preview or background explanation from its property fields.
        yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface.raised(g,
                new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(3, 52, left() - 4, height - 32), tab == 0 ? 0xFF353A31 : 0x99353A31, true);
        yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface.raised(g,
                new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(left() - 2, 52, width - 4, height - 32), tab == 0 ? 0xFF414638 : 0x99414638, true);
        int contentBottom = formWidgets.entrySet().stream().mapToInt(entry -> entry.getValue() + entry.getKey().getHeight()).max().orElse(54);
        if (contentBottom > height - 36) {
            int trackHeight = Math.max(1, height - 92);
            int thumbY = 56 + (int)((double)formScroll / Math.max(1, contentBottom - (height - 36)) * (trackHeight - 12));
            g.fill(width - 8, 56, width - 6, 56 + trackHeight, 0xFF252820);
            g.fill(width - 8, thumbY, width - 6, thumbY + 12, 0xFFD5BF7A);
        }
        g.drawCenteredString(font, title, tab == 0 ? width / 2 : left() / 2, 10, 0xFFFFFF);
        if (tab != 0 || aspect) g.drawCenteredString(font, label(tab != 0 ? "preview_only" : "aspect_linked"), width / 2, 30, 0xFFE4C272);
        if (tab == 0 && selected >= 0) {
            var d = decorations.get(selected);
            double w = d.width(), h = d.height();
            try { w = number("width"); h = number("height"); } catch (RuntimeException ignored) {}
            drawPreview(g, fields.get("texture").getValue(), 12, 60, left() - 28, Math.max(20, height - 125), w, h);
        } else if (tab != 0) {
            // The explanation has its own scroll area so translated text never covers apply/cancel.
            var lines = helpLines();
            int rows = Math.max(1, (height - 128) / 11);
            helpScroll = Math.clamp(helpScroll, 0, Math.max(0, lines.size() - rows));
            for (int row = 0; row < rows && row + helpScroll < lines.size(); row++)
                g.drawString(font, lines.get(row + helpScroll), 12, 60 + row * 11, 0xDDDDDD, false);
            if (lines.size() > rows) {
                int track = rows * 11;
                g.fill(left() - 12, 60, left() - 10, 60 + track, 0xFF252820);
                int thumb = 60 + helpScroll * (track - 12) / (lines.size() - rows);
                g.fill(left() - 12, thumb, left() - 10, thumb + 12, 0xFFD5BF7A);
            }
        }
        fields.forEach((key, box) -> { if (box.visible) g.drawString(font, label(key), contentLeft(), box.getY() + 5, 0xDDDDDD, false); });
        g.drawString(font, font.plainSubstrByWidth(error, width - 200), 96, height - 21, 0xFF9999, false);
        super.render(g, x, y, dt);
        if (tab != 0) {
            int top = 204 - formScroll;
            if (top >= 54 && top + 112 <= height - 34) {
                String texture = fields.get("texture").getValue();
                var id = ResourceLocation.tryParse(texture);
                var size = id == null ? null : LoadedTextures.size(id);
                g.fill(contentLeft() + 2, top + 2, width - 14, top + 110, 0xFF22251F);
                g.drawCenteredString(font, label("texture_preview"), (contentLeft() + width - 12) / 2, top + 6, 0xDDDDDD);
                drawPreview(g, texture, contentLeft() + 6, top + 20, width - contentLeft() - 24, 86,
                        size == null ? 1 : size.width(), size == null ? 1 : size.height());
            }
        }
        enumDropdown.render(g, font, width, 54, height - 34, x, y);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (enumDropdown.open()) {
            enumDropdown.click(x, y, button, width, 54, height - 34);
            closeChoice();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && enumDropdown.open()) { closeChoice(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    /** Contain the authored rectangle in the preview without stretching it to the panel shape. */
    private void drawPreview(GuiGraphics g, String texture, int x, int y, int w, int h, double sourceW, double sourceH) {
        if (!Double.isFinite(sourceW) || !Double.isFinite(sourceH) || sourceW <= 0 || sourceH <= 0) return;
        double factor = Math.min(w / sourceW, h / sourceH);
        int dw = Math.max(1, (int)Math.round(sourceW * factor)), dh = Math.max(1, (int)Math.round(sourceH * factor));
        LoadedTextures.draw(g, texture, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 1);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
