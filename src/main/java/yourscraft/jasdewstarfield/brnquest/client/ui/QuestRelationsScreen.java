package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Compact modal relation browser, with clipped scrolling and stable IDs for live navigation. */
final class QuestRelationsScreen extends Screen {
    record Entry(ResourceLocation id, Component title, Component chapter, Component status, int statusColor) {}
    private final Screen parent;
    private final Supplier<List<Entry>> source;
    private final Consumer<ResourceLocation> jump;
    private final EditorListPanel<ResourceLocation> list = new EditorListPanel<>();
    private EditorListNavigationWidget<ResourceLocation> navigation;
    private List<Entry> entries = List.of();
    private UiRect panel;
    private long lastFrame;
    private final int color;
    QuestRelationsScreen(Screen parent, boolean upstream, Supplier<List<Entry>> source, Consumer<ResourceLocation> jump) {
        super(Component.translatable("screen.brnquest.relations." + (upstream ? "upstream.title" : "downstream.title")));
        this.parent = parent; this.source = source; this.jump = jump;
        color = upstream ? QuestRelations.UPSTREAM_COLOR : QuestRelations.DOWNSTREAM_COLOR;
    }
    @Override protected void init() {
        int w = Math.min(330, width - 24), h = Math.min(240, height - 24);
        panel = new UiRect((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2);
        list.invalidate(); lastFrame = 0;
        navigation = addRenderableWidget(new EditorListNavigationWidget<>(title, list, row -> activate(row.key())));
        addRenderableWidget(new EditorButtonWidget(panel.left()+10, panel.bottom()-28, panel.width()-20, 20,
                Component.translatable("gui.done"), button -> onClose()));
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, width/2, panel.top()+10, color);
        var current = source.get();
        if (!current.equals(entries)) { list.invalidate(); entries = List.copyOf(current); }
        long now = System.nanoTime(); double elapsed = lastFrame == 0 ? 0 : Math.min(.1, (now-lastFrame)/1e9); lastFrame=now;
        var frame = list.advance(new UiRect(panel.left()+10, panel.top()+30, panel.right()-16, panel.bottom()-34),
                panel, panel.right()-10, 36, 3, entries.size(), i -> entries.get(i).id(), elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        navigation.update(frame);
        list.render(graphics, row -> {
            var entry = entries.stream().filter(e -> e.id().equals(row.key())).findFirst().orElseThrow();
            var b = row.bounds();
            graphics.fill(b.left(), b.top(), b.right(), b.bottom(), row.visible().containsExclusive(x,y) ? GraystonePalette.HOVER : GraystonePalette.ROW);
            graphics.fill(b.left(), b.top(), b.left()+2, b.bottom(), color);
            EditorTextRenderer.drawFittedString(graphics,font,entry.title(),b.left()+7,b.top()+4,b.width()-13,GraystonePalette.TEXT,.75F);
            int statusWidth = Math.min(font.width(entry.status()), b.width()/2);
            EditorTextRenderer.drawFittedStringRight(graphics,font,entry.status(),b.right()-6,b.top()+19,statusWidth,entry.statusColor(),.75F);
            EditorTextRenderer.drawFittedString(graphics,font,entry.chapter(),b.left()+7,b.top()+19,Math.max(1,b.width()-statusWidth-23),GraystonePalette.SECONDARY,.75F);
        }, () -> graphics.drawCenteredString(font, Component.translatable("screen.brnquest.relations.empty"), width/2, panel.top()+45, GraystonePalette.MUTED));
        super.render(graphics,x,y,partial);
        list.rowAt(x,y).ifPresent(row -> entries.stream().filter(e -> e.id().equals(row.key())).findFirst().ifPresent(e ->
                graphics.renderTooltip(font,List.of(e.title().getVisualOrderText(),e.chapter().getVisualOrderText(),e.status().getVisualOrderText()),x,y)));
    }
    private void activate(ResourceLocation id) {
        // Re-resolve after a live reload so a stale row cannot navigate to an unrelated object.
        if (source.get().stream().noneMatch(e -> e.id().equals(id))) return;
        onClose(); jump.accept(id);
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if (button == 0) {
            var row = list.rowAt(x,y);
            if (row.isPresent()) { activate(row.get().key()); return true; }
        }
        return list.mouseClicked(x,y,button) || super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return list.mouseDragged(y,button) || super.mouseDragged(x,y,button,dx,dy); }
    @Override public boolean mouseReleased(double x,double y,int button) { return list.mouseReleased(button) || super.mouseReleased(x,y,button); }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy);
    }
    @Override public void tick() { parent.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
