package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Consumer;

/** Paged loaded-resource picker. Typed IDs may remain missing until the required resource pack is restored. */
public final class EditorTextureBrowserScreen extends Screen {
    private final Screen parent;
    private final Consumer<String> consumer;
    private EditBox filter;
    private String query = "";
    private int page;
    private long generation = -1;
    private List<ResourceLocation> filtered = List.of();
    public EditorTextureBrowserScreen(Screen parent, Consumer<String> consumer) {
        super(Component.translatable("screen.brnquest.canvas.browse")); this.parent = parent; this.consumer = consumer;
    }
    private int rows() { return Math.max(1, (height - 100) / 28); }
    @Override protected void init() {
        if (filter != null) query = filter.getValue();
        filter = new EditBox(font, 20, 30, Math.max(80, width - 40), 20, title);
        filter.setMaxLength(512); filter.setValue(query);
        filter.setResponder(value -> { query = value; page = 0; refresh(); });
        addRenderableWidget(filter);
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); }).bounds(20, height - 28, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = Math.min(maxPage(), page + 1); rebuildWidgets(); }).bounds(55, height - 28, 30, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width - 100, height - 28, 80, 20).build());
        refresh();
    }
    private int maxPage() { return Math.max(0, (filtered.size() - 1) / rows()); }
    private void refresh() {
        filtered = LoadedTextures.all().stream().filter(id -> id.toString().contains(query.toLowerCase(Locale.ROOT))).toList();
        page = Math.min(page, maxPage()); generation = LoadedTextures.generation();
    }
    @Override public void tick() { parent.tick(); if (generation != LoadedTextures.generation()) refresh(); }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float dt) {}
    @Override public void render(GuiGraphics g, int x, int y, float dt) {
        g.fill(0, 0, width, height, 0xFF262A25);
        yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface.raised(g,
                new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(16, 54, width - 16, height - 34), 0xFF3B4035, true);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        for (int row = 0; row < rows(); row++) {
            int i = page * rows() + row; if (i >= filtered.size()) break;
            int top = 58 + row * 28;
            var id = filtered.get(i);
            if (x >= 20 && x < width - 20 && y >= top && y < top + 26) g.fill(20, top, width - 20, top + 26, 0xFF505647);
            var size = LoadedTextures.size(id);
            double scale = 22.0 / Math.max(size.width(), size.height());
            LoadedTextures.draw(g, id.toString(), 22, top + 2, Math.max(1, (int)(size.width()*scale)), Math.max(1, (int)(size.height()*scale)), 1);
            g.drawString(font, font.plainSubstrByWidth(id.toString(), width - 80), 50, top + 9, 0xFFFFFF, false);
        }
        g.drawCenteredString(font, (page + 1) + " / " + (maxPage() + 1) + " · " + filtered.size(), width / 2, height - 23, 0xCCCCCC);
        super.render(g, x, y, dt);
        if (x >= 20 && x < width - 20 && y >= 58 && y < 58 + rows() * 28) {
            int index = page * rows() + (y - 58) / 28;
            if (index < filtered.size()) {
                var id = filtered.get(index); var size = LoadedTextures.size(id);
                g.renderTooltip(font, Component.literal(id + " · " + size.width() + "×" + size.height()), x, y);
            }
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && x >= 20 && x < width - 20 && y >= 58 && y < 58 + rows() * 28) {
            int index = page * rows() + (int)((y - 58) / 28);
            if (index < filtered.size()) { consumer.accept(filtered.get(index).toString()); onClose(); }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
