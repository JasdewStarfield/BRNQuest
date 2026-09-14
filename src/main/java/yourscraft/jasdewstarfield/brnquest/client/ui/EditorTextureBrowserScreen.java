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
    private Button importButton;
    private boolean importing;
    private volatile boolean closed;
    private Component importStatus = text("import_help");
    private long generation = -1;
    private List<ResourceLocation> filtered = List.of();
    public EditorTextureBrowserScreen(Screen parent, Consumer<String> consumer) {
        super(Component.translatable("screen.brnquest.canvas.browse")); this.parent = parent; this.consumer = consumer;
    }
    private static Component text(String key) { return Component.translatable("screen.brnquest.canvas." + key); }
    private int rows() { return Math.max(1, (height - 122) / 28); }
    @Override protected void init() {
        if (filter != null) query = filter.getValue();
        filter = new EditBox(font, 20, 30, Math.max(60, width - 156), 20, title);
        filter.setMaxLength(512); filter.setValue(query);
        filter.setResponder(value -> { query = value; page = 0; refresh(); });
        addRenderableWidget(filter);
        importButton = addRenderableWidget(Button.builder(text("import_png"), button -> importLocal(null))
                .bounds(width - 130, 30, 110, 20).build());
        importButton.active = !importing;
        importButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(text("import_help")));
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
                new yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect(16, 54, width - 16, height - 64), 0xFF3B4035, true);
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
        g.drawString(font, font.plainSubstrByWidth(importStatus.getString(), width - 40), 20, height - 53, 0xDDDDDD, false);
        super.render(g, x, y, dt);
        if (x >= 20 && x < width - 20 && y >= height - 56 && y < height - 40)
            g.renderTooltip(font, font.split(importStatus, Math.max(100, width - 40)), x, y);
        if (x >= 20 && x < width - 20 && y >= 58 && y < 58 + rows() * 28) {
            int index = page * rows() + (y - 58) / 28;
            if (index < filtered.size()) {
                var id = filtered.get(index); var size = LoadedTextures.size(id);
                g.renderTooltip(font, Component.literal(id + " · " + size.width() + "×" + size.height()), x, y);
            }
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!importing && button == 0 && x >= 20 && x < width - 20 && y >= 58 && y < 58 + rows() * 28) {
            int index = page * rows() + (int)((y - 58) / 28);
            if (index < filtered.size()) { consumer.accept(filtered.get(index).toString()); onClose(); }
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    /** Native dialog and PNG decoding run off the render thread so the suspended editor can keep its lease alive. */
    private void importLocal(java.nio.file.Path dropped) {
        if (importing) return;
        importing = true; importButton.active = false; importStatus = text("import_busy");
        var client = minecraft;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                java.nio.file.Path source = dropped;
                if (source == null) {
                    try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                        String chosen = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                                text("import_png").getString(), "", stack.pointers(stack.UTF8("*.png")), "PNG", false);
                        if (chosen == null) return null;
                        source = java.nio.file.Path.of(chosen);
                    }
                }
                if (closed) return null;
                return yourscraft.jasdewstarfield.brnquest.client.assets.LocalAssetPack.store().importPng(source);
            } catch (java.io.IOException failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, net.minecraft.Util.ioPool()).whenComplete((result, failure) -> client.execute(() -> {
            if (closed || client.screen != this) return;
            if (failure != null) {
                Throwable cause = failure;
                while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                String key = cause instanceof yourscraft.jasdewstarfield.brnquest.client.assets.LocalAssetStore.InvalidAsset invalid
                        ? "import_" + invalid.problem().name().toLowerCase(java.util.Locale.ROOT) : "import_failed";
                importStatus = text(key); importing = false; importButton.active = true;
                com.mojang.logging.LogUtils.getLogger().warn("BRNQuest local image import failed", cause);
                return;
            }
            if (result == null) {
                importStatus = text("import_help"); importing = false; importButton.active = true; return;
            }
            // The resource manager reads new files directly. Rebuild it only if the pack was unavailable at startup.
            if (client.getResourceManager().getResource(result.id()).isEmpty()) {
                client.reloadResourcePacks().whenComplete((unused, reloadFailure) -> client.execute(() -> {
                    if (!closed && client.screen == this) finishImport(result, reloadFailure == null);
                }));
            } else finishImport(result, true);
        }));
    }
    private void finishImport(yourscraft.jasdewstarfield.brnquest.client.assets.LocalAssetStore.Imported result, boolean loaded) {
        importing = false; importButton.active = true;
        LoadedTextures.invalidate();
        // A previously missing reference may already have a cached placeholder texture.
        minecraft.getTextureManager().release(result.id());
        if (!loaded || minecraft.getResourceManager().getResource(result.id()).isEmpty()) {
            importStatus = text("import_reload_failed"); return;
        }
        filter.setValue(result.id().toString());
        refresh();
        importStatus = text(result.reused() ? "import_reused" : "import_success");
        // Import only adds to the local library. The user still clicks a resource to apply it to the outer form.
    }
    @Override public void onFilesDrop(List<java.nio.file.Path> paths) {
        if (paths.size() == 1) importLocal(paths.getFirst());
        else importStatus = text("import_single");
    }
    @Override public void removed() { closed = true; super.removed(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
