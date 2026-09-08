package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.*;
import java.util.function.Consumer;

/** Shared server-backed field picker: delayed search, raw-value editing, and stale-response isolation. */
public final class ServerFieldScreen extends Screen {
    private final Screen parent;
    private final String source;
    private final Consumer<String> commit;
    private String value;
    private EditBox input, search;
    private Button useCurrent;
    private String filter = "";
    private String requestId = "";
    private int delay;
    private final EditorListPanel<ServerFieldSources.Entry> list = new EditorListPanel<>();
    private long lastFrame;
    private String currentRequest = "";
    private ServerFieldSources.Result result;
    public ServerFieldScreen(Screen parent, String source, String value, Consumer<String> commit) {
        super(Component.translatable("screen.brnquest.field.title"));
        this.parent = parent; this.source = source; this.value = value; this.commit = commit;
    }
    protected void init() {
        int left = width / 2 - 150;
        list.invalidate();
        lastFrame = 0;
        input = new EditBox(font,left,42,300,20,Component.translatable("screen.brnquest.field.value"));
        input.setMaxLength(256); input.setValue(value); input.setResponder(text -> { value = text; currentRequest = ""; delay = 6; }); addRenderableWidget(input);
        search = new EditBox(font,left,72,220,20,Component.translatable("screen.brnquest.field.search"));
        search.setMaxLength(128); search.setValue(filter); search.setHint(Component.translatable("screen.brnquest.field.search")); search.setResponder(text -> { filter = text; list.reset(); delay = 6; }); addRenderableWidget(search);
        useCurrent = addRenderableWidget(Button.builder(Component.translatable("screen.brnquest.field.current"), b -> { query(); currentRequest = requestId; }).bounds(left+224,72,76,20).build());
        useCurrent.active = result != null && !result.current().isBlank();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> { commit.accept(input.getValue()); onClose(); }).bounds(left,height-30,146,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(left+154,height-30,146,20).build());
        query();
    }
    private void query() {
        requestId = UUID.randomUUID().toString();
        ServerFieldNetwork.send(new ServerFieldNetwork.Query(requestId,source,search.getValue(),input.getValue()));
    }
    public void tick() { if (delay > 0 && --delay == 0) query(); }
    public void receive(ServerFieldNetwork.Reply reply) {
        if (reply == null || !requestId.equals(reply.id())) return;
        result = reply.result();
        useCurrent.active = result != null && !result.current().isBlank();
        // Replace geometry along with data, so a click cannot select an entry from an obsolete reply.
        list.invalidate();
        if (requestId.equals(currentRequest) && result != null && !result.current().isBlank()) {
            currentRequest = "";
            input.setValue(result.current());
        }
    }
    public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics,x,y,partial); super.render(graphics,x,y,partial);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFFFF);
        graphics.drawString(font,Component.translatable("screen.brnquest.field.status",result == null ? 0 : result.total(),result == null ? 0 : result.selectedCount()),width/2-150,100,0xFFCCCCCC,false);
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        var entries = result == null ? List.<ServerFieldSources.Entry>of() : result.entries();
        UiRect bounds = new UiRect(width/2-150, 128, width/2+144, Math.max(128,height-36));
        list.advance(bounds, new UiRect(0,0,width,height), width/2+147,20,2,entries.size(),entries::get,
                elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        list.render(graphics, row -> {
            var rect = row.bounds();
            boolean hovered = row.visible().containsExclusive(x,y);
            graphics.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),hovered ? 0xDD385A72 : 0xAA263646);
            graphics.drawString(font,font.plainSubstrByWidth(row.key().value()+" ("+row.key().count()+")",rect.width()-6),
                    rect.left()+3,rect.top()+5,0xFFFFFFFF,false);
        }, () -> {});
        if (result != null && !result.error().isEmpty()) {
            graphics.drawString(font,Component.translatable("screen.brnquest.field.error."+result.error()),width/2-150,112,0xFFFF7777,false);
            // Hover surfaces are drawn last, after the shared list's scissor is closed.
            if (!result.detail().isBlank() && y>=112 && y<125 && x>=width/2-150 && x<width/2+150)
                graphics.renderTooltip(font,font.split(Component.literal(result.detail()),280),x,y);
        }
        list.rowAt(x,y).ifPresent(row -> graphics.renderTooltip(font,font.split(Component.literal(row.key().value()),280),x,y));
    }
    public boolean mouseClicked(double x,double y,int button) {
        if (list.mouseClicked(x,y,button)) return true;
        var row = list.rowAt(x,y);
        if (button==0 && row.isPresent()) { input.setValue(row.get().key().value()); return true; }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseScrolled(double x,double y,double dx,double dy) {
        return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy);
    }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return false; }
}
