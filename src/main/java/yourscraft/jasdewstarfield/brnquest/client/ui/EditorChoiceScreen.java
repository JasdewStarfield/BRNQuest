package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListNavigationWidget;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
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

/** Searchable authoring choices with stable keys. Selection updates the parent form, never the server draft. */
final class EditorChoiceScreen extends Screen {
    private final Screen parent;
    record Choice(String id, Component label) {}
    private final List<Choice> choices;
    private List<Choice> visibleChoices = List.of();
    private final java.util.function.Consumer<String> selection;
    private final String current;
    private final EditorListPanel<Integer> list = new EditorListPanel<>();
    private EditorListNavigationWidget<Integer> navigation;
    private String filter = "";
    private long lastFrame;
    EditorChoiceScreen(Screen parent, Component title, List<Choice> choices, String current,
                       java.util.function.Consumer<String> selection) {
        super(title);
        this.parent = parent; this.choices = List.copyOf(choices); this.current = current; this.selection = selection;
    }
    protected void init() {
        list.invalidate(); lastFrame = 0;
        int left = Math.max(12, width / 2 - 180), panelWidth = Math.min(360, width - 24);
        var search = new EditBox(font, left, 36, panelWidth, 20, Component.translatable("screen.brnquest.field.search"));
        search.setValue(filter); search.setHint(Component.translatable("screen.brnquest.field.search"));
        search.setResponder(value -> { filter = value; list.reset(); }); addRenderableWidget(search);
        navigation = addRenderableWidget(new EditorListNavigationWidget<>(title, list, row -> choose(row.key())));
        addRenderableWidget(new EditorButtonWidget(left, height-30, panelWidth, 20,
                Component.translatable("gui.cancel"), button -> onClose()));
    }
    /** The child blurs the parent once, before drawing its own controls, like the item picker. */
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}
    public void render(GuiGraphics graphics,int x,int y,float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        super.renderBackground(graphics,x,y,partial);
        graphics.pose().pushPose();
        // Parent chapter properties already occupy the first modal layer.
        graphics.pose().translate(0,0,1000);
        try {
        graphics.fill(0,0,width,height,GraystonePalette.BACKDROP);
        int left = Math.max(12,width/2-180), right = left + Math.min(360,width-24);
        // Decorate the existing search/list/footer layout without replacing its native input widgets.
        GraystoneSurface.raised(graphics, new UiRect(left-4, 8, right+4, Math.max(8,height-6)), GraystonePalette.PANEL, true);
        super.render(graphics,x,y,partial);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFFFF);
        var members = choices.stream().filter(value -> (value.label().getString() + " " + value.id())
                .toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();
        visibleChoices = members;
        long now = System.nanoTime(); double elapsed = lastFrame == 0 ? 0 : Math.min(.1,(now-lastFrame)/1_000_000_000.0); lastFrame=now;
        var frame = list.advance(new UiRect(left,64,right-6,Math.max(64,height-36)),new UiRect(0,0,width,height),right-3,22,2,members.size(),i->i,elapsed,BrnQuestClientConfig.VALUES.smoothSpeed.get());
        navigation.update(frame);
        list.render(graphics,row->{
            // The selected stable ID remains visible even when labels collide or a filter changes.
            var rect=row.bounds(); graphics.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),
                    members.get(row.key()).id().equals(current) ? 0xFF656148 : row.visible().containsExclusive(x,y) ? GraystonePalette.HOVER : 0xFF252822);
            graphics.drawString(font,font.plainSubstrByWidth(members.get(row.key()).label().getString(),rect.width()-8),rect.left()+4,rect.top()+7,0xFFFFFFFF,false);
        },()->graphics.drawString(font,Component.translatable("screen.brnquest.options.empty"),left+4,68,0xFFAAAAAA,false));
        if (!navigation.isFocused()) list.rowAt(x,y).ifPresent(row->{ if(row.key()<members.size()) graphics.renderTooltip(font,font.split(Component.literal(members.get(row.key()).label().getString() + "\n" + members.get(row.key()).id()),Math.max(80,width-40)),x,y); });
        graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
    private void choose(int index) {
        if (index < 0 || index >= visibleChoices.size()) return;
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        selection.accept(visibleChoices.get(index).id());
        onClose();
    }
    /** The parent retains its editing lease while this ordinary child screen owns input. */
    @Override public void tick() { parent.tick(); super.tick(); }
    public boolean mouseClicked(double x,double y,int button) {
        if (list.mouseClicked(x,y,button)) return true;
        var row = list.rowAt(x,y);
        if (button == 0 && row.isPresent()) { choose(row.get().key()); return true; }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseScrolled(double x,double y,double dx,double dy) { return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy); }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
