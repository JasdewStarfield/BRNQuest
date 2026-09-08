package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
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
    private int delay, offset;
    private ServerFieldSources.Result result;
    public ServerFieldScreen(Screen parent, String source, String value, Consumer<String> commit) {
        super(Component.translatable("screen.brnquest.field.title"));
        this.parent = parent; this.source = source; this.value = value; this.commit = commit;
    }
    protected void init() {
        int left = width / 2 - 150;
        input = new EditBox(font,left,42,300,20,Component.translatable("screen.brnquest.field.value"));
        input.setMaxLength(256); input.setValue(value); input.setResponder(text -> { value = text; delay = 6; }); addRenderableWidget(input);
        search = new EditBox(font,left,72,220,20,Component.translatable("screen.brnquest.field.search"));
        search.setMaxLength(128); search.setValue(filter); search.setHint(Component.translatable("screen.brnquest.field.search")); search.setResponder(text -> { filter = text; offset = 0; delay = 6; }); addRenderableWidget(search);
        useCurrent = addRenderableWidget(Button.builder(Component.translatable("screen.brnquest.field.current"), b -> { if (result != null && !result.current().isBlank()) input.setValue(result.current()); }).bounds(left+224,72,76,20).build());
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
        offset = Math.min(offset, Math.max(0, result == null ? 0 : result.entries().size() - visibleRows()));
    }
    private int visibleRows() { return Math.max(0, (height - 164) / 20); }
    public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics,x,y,partial); super.render(graphics,x,y,partial);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFFFF);
        graphics.drawString(font,Component.translatable("screen.brnquest.field.status",result == null ? 0 : result.total(),result == null ? 0 : result.selectedCount()),width/2-150,100,0xFFCCCCCC,false);
        if (result != null) {
            if (!result.error().isEmpty()) graphics.drawString(font,Component.translatable("screen.brnquest.field.error." + result.error()),width/2-150,112,0xFFFF7777,false);
            // Detailed resolution failures stay available without overflowing the narrow status row.
            if (!result.detail().isBlank() && y >= 112 && y < 125)
                graphics.renderTooltip(font, font.split(Component.literal(result.detail()), 280), x, y);
            int count = visibleRows();
            for (int i=0;i<count && i+offset<result.entries().size();i++) {
                var entry=result.entries().get(i+offset); int top=128+i*20;
                graphics.fill(width/2-150,top,width/2+150,top+18,0xAA263646);
                graphics.drawString(font,font.plainSubstrByWidth(entry.value()+" ("+entry.count()+")",294),width/2-147,top+5,0xFFFFFFFF,false);
            }
        }
    }
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && result != null && x>=width/2-150 && x<width/2+150 && y>=128 && y<128+visibleRows()*20 && (y-128)%20<18) {
            int index=(int)(y-128)/20+offset;
            if(index<result.entries().size()) {input.setValue(result.entries().get(index).value());return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if (result == null) return false;
        offset=Math.max(0,Math.min(Math.max(0,result.entries().size()-Math.max(1,visibleRows())),offset-(int)dy));return true;
    }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return false; }
}
