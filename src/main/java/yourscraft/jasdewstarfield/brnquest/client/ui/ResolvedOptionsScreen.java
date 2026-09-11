package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListNavigationWidget;

import net.minecraft.client.gui.GuiGraphics;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.*;
import java.util.function.Supplier;

/** Read-only names browser reusing the shared clipped, smoothly scrolling list without a row cap. */
final class ResolvedOptionsScreen extends Screen {
    private final Screen parent;
    private final Supplier<List<Component>> source;
    private final EditorListPanel<Integer> list = new EditorListPanel<>();
    private EditorListNavigationWidget<Integer> navigation;
    private String filter = "";
    private long lastFrame;
    ResolvedOptionsScreen(Screen parent, Supplier<List<Component>> source) {
        super(Component.translatable("screen.brnquest.options.title"));
        this.parent = parent; this.source = source;
    }
    protected void init() {
        list.invalidate(); lastFrame = 0;
        int left = Math.max(12, width / 2 - 180), panelWidth = Math.min(360, width - 24);
        var search = new EditBox(font, left, 36, panelWidth, 20, Component.translatable("screen.brnquest.field.search"));
        search.setValue(filter); search.setHint(Component.translatable("screen.brnquest.field.search"));
        search.setResponder(value -> { filter = value; list.reset(); }); addRenderableWidget(search);
        navigation = addRenderableWidget(new EditorListNavigationWidget<>(title, list, row -> {}));
        addRenderableWidget(new EditorButtonWidget(left, height-30, panelWidth, 20,
                Component.translatable("gui.done"), button -> onClose()));
    }
    /** The child blurs the parent once, before drawing its own controls, like the item picker. */
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}
    public void render(GuiGraphics graphics,int x,int y,float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        super.renderBackground(graphics,x,y,partial);
        graphics.fill(0,0,width,height,0x70151820);
        int left = Math.max(12,width/2-180), right = left + Math.min(360,width-24);
        // Decorate the existing search/list/footer layout without replacing its native input widgets.
        GraystoneSurface.raised(graphics, new UiRect(left-4, 8, right+4, Math.max(8,height-6)), 0xFF30332E, true);
        super.render(graphics,x,y,partial);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFFFF);
        var members = source.get().stream().filter(value -> value.getString().toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();
        long now = System.nanoTime(); double elapsed = lastFrame == 0 ? 0 : Math.min(.1,(now-lastFrame)/1_000_000_000.0); lastFrame=now;
        var frame = list.advance(new UiRect(left,64,right-6,Math.max(64,height-36)),new UiRect(0,0,width,height),right-3,22,2,members.size(),i->i,elapsed,BrnQuestClientConfig.VALUES.smoothSpeed.get());
        navigation.update(frame);
        list.render(graphics,row->{
            // Read-only rows use an inset surface rather than the bevel of a selectable button.
            var rect=row.bounds(); graphics.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),
                    row.visible().containsExclusive(x,y) ? 0xFF454940 : 0xFF252822);
            graphics.drawString(font,font.plainSubstrByWidth(members.get(row.key()).getString(),rect.width()-8),rect.left()+4,rect.top()+7,0xFFFFFFFF,false);
        },()->graphics.drawString(font,Component.translatable("screen.brnquest.options.empty"),left+4,68,0xFFAAAAAA,false));
        list.rowAt(x,y).ifPresent(row->{ if(row.key()<members.size()) graphics.renderTooltip(font,font.split(ResolvedOptions.hoverText(members.get(row.key())),Math.max(80,width-40)),x,y); });
    }
    public boolean mouseClicked(double x,double y,int button) { return list.mouseClicked(x,y,button) || super.mouseClicked(x,y,button); }
    public boolean mouseScrolled(double x,double y,double dx,double dy) { return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy); }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
