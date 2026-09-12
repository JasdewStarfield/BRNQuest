package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListNavigationWidget;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonWidget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;

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
    private EditorButtonWidget useCurrent, preview;
    private final Map<String,String> context;
    private final boolean readOnly;
    private EditorListNavigationWidget<Integer> navigation;
    private String filter = "";
    private String requestId = "";
    private int delay;
    private final EditorListPanel<Integer> list = new EditorListPanel<>();
    private final ServerFieldPages pages = new ServerFieldPages();
    private long lastFrame;
    private String currentRequest = "";
    private ServerFieldSources.Result result;
    public ServerFieldScreen(Screen parent, String source, String value, Consumer<String> commit) {
        this(parent,source,value,commit,Map.of(),false);
    }
    /** The same paged list serves dependent choices and read-only source-provided previews. */
    public ServerFieldScreen(Screen parent, String source, String value, Consumer<String> commit, Map<String,String> context, boolean readOnly) {
        this(parent,source,value,commit,context,readOnly,Component.translatable(readOnly ? "screen.brnquest.field.preview" : "screen.brnquest.field.title"));
    }
    /** Declaring fields supply their own label; the shared screen never identifies concrete task types. */
    public ServerFieldScreen(Screen parent, String source, String value, Consumer<String> commit, Map<String,String> context, boolean readOnly, Component title) {
        super(title);
        this.readOnly = readOnly;
        var bounded = new TreeMap<String,String>();
        context.forEach((key,text) -> { if (key.length() <= 128 && text.length() <= 256 && bounded.size() < 64) bounded.put(key,text); });
        this.context = Map.copyOf(bounded);
        this.parent = parent; this.source = source; this.value = value; this.commit = commit;
    }
    protected void init() {
        int left = width / 2 - 150;
        list.invalidate();
        lastFrame = 0;
        input = new EditBox(font,left,42,300,20,Component.translatable("screen.brnquest.field.value"));
        input.setMaxLength(256); input.setValue(value); input.setResponder(text -> { value = text; currentRequest = ""; invalidateSelection(); delay = 6; }); addRenderableWidget(input);
        input.setEditable(!readOnly);
        search = new EditBox(font,left,72,220,20,Component.translatable("screen.brnquest.field.search"));
        search.setMaxLength(128); search.setValue(filter); search.setHint(Component.translatable("screen.brnquest.field.search")); search.setResponder(text -> { filter = text; pages.reset(); list.reset(); invalidateSelection(); delay = 6; }); addRenderableWidget(search);
        navigation = addRenderableWidget(new EditorListNavigationWidget<>(title, list, row -> {
            var entry = pages.entry(row.key());
            if (!readOnly && entry != null) input.setValue(entry.value());
        }));
        useCurrent = addRenderableWidget(new EditorButtonWidget(left+224,72,76,20,Component.translatable("screen.brnquest.field.current"), b -> { if (result == null || result.current().isBlank()) input.setValue(""); else { query(); currentRequest = requestId; } }));
        useCurrent.active = !readOnly;
        useCurrent.setMessage(Component.translatable(result != null && !result.current().isBlank() ? "screen.brnquest.field.current" : "screen.brnquest.field.clear"));
        preview = addRenderableWidget(new EditorButtonWidget(left+224,112,76,14,Component.translatable("screen.brnquest.field.preview"), b -> {
            if (result != null && !result.previewSource().isBlank())
                minecraft.setScreen(new ServerFieldScreen(this,result.previewSource(),value,ignored -> {},context,true));
        }));
        preview.visible = !readOnly && result != null && !result.previewSource().isBlank();
        addRenderableWidget(new EditorButtonWidget(left,height-30,146,20,Component.translatable("gui.done"), b -> { if (!readOnly) commit.accept(input.getValue()); onClose(); }));
        addRenderableWidget(new EditorButtonWidget(left+154,height-30,146,20,Component.translatable("gui.cancel"), b -> onClose()));
        query();
    }
    /** Discard old selection metadata immediately, before the delayed replacement query. */
    private void invalidateSelection() {
        pages.cancelPending(); result = null;
        if (preview != null) preview.visible = false;
    }
    private void query() {
        // Refresh selection metadata in place; only a changed search clears the result pages.
        requestPage(0);
    }
    private void requestPage(int offset) {
        requestId = pages.begin(offset);
        ServerFieldNetwork.send(new ServerFieldNetwork.Query(requestId,source,search.getValue(),input.getValue(),offset,context));
    }
    public void tick() { if (delay > 0 && --delay == 0) query(); }
    public void receive(ServerFieldNetwork.Reply reply) {
        if (reply == null || !pages.receive(reply.id(), reply.result())) return;
        result = reply.result();
        useCurrent.active = !readOnly;
        useCurrent.setMessage(Component.translatable(result != null && !result.current().isBlank() ? "screen.brnquest.field.current" : "screen.brnquest.field.clear"));
        preview.visible = !readOnly && result != null && !result.previewSource().isBlank();
        // Replace geometry along with data, so a click cannot select an entry from an obsolete reply.
        list.invalidate();
        if (requestId.equals(currentRequest) && result != null && !result.current().isBlank()) {
            currentRequest = "";
            input.setValue(result.current());
        }
    }
    /** The child blurs the parent once, before drawing its own controls, like the item picker. */
    @Override public void renderBackground(GuiGraphics graphics,int x,int y,float partial) {}
    public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        super.renderBackground(graphics,x,y,partial);
        graphics.fill(0,0,width,height,GraystonePalette.BACKDROP);
        // Skin the existing form; query IDs, pagination and native input focus remain unchanged.
        GraystoneSurface.raised(graphics, new UiRect(width/2-154,8,width/2+154,Math.max(8,height-6)),GraystonePalette.PANEL,true);
        super.render(graphics,x,y,partial);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFFFF);
        graphics.drawString(font,Component.translatable("screen.brnquest.field.status",result == null ? 0 : result.total(),result == null ? 0 : result.selectedCount()),width/2-150,100,0xFFCCCCCC,false);
        long now = System.nanoTime();
        double elapsed = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        UiRect bounds = new UiRect(width/2-150, 128, width/2+144, Math.max(128,height-36));
        var frame = list.advance(bounds, new UiRect(0,0,width,height), width/2+147,20,2,pages.total(),i -> i,
                elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        navigation.update(frame);
        list.render(graphics, row -> {
            var rect = row.bounds();
            boolean hovered = row.visible().containsExclusive(x,y);
            // Read-only previews remain flat; selectable values use the shared bounded bevel.
            if (readOnly) graphics.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),hovered ? GraystonePalette.HOVER : 0xFF252822);
            else GraystoneSurface.raised(graphics,rect,hovered ? 0xFF5C6056 : GraystonePalette.HOVER,true);
            var entry = pages.entry(row.key());
            String label = entry == null ? Component.translatable("screen.brnquest.field.loading").getString() : entry.value()+(readOnly ? "" : " ("+entry.count()+")");
            graphics.drawString(font,font.plainSubstrByWidth(label,rect.width()-6),
                    rect.left()+3,rect.top()+5,0xFFFFFFFF,false);
        }, () -> {});
        if (result != null && !result.error().isEmpty()) {
            graphics.drawString(font,Component.translatable("screen.brnquest.field.error."+result.error()),width/2-150,112,0xFFFF7777,false);
            // Hover surfaces are drawn last, after the shared list's scissor is closed.
            if (!result.detail().isBlank() && y>=112 && y<125 && x>=width/2-150 && x<width/2+150)
                graphics.renderTooltip(font,font.split(Component.literal(result.detail()),280),x,y);
        }
        list.rowAt(x,y).map(row -> pages.entry(row.key())).ifPresent(entry -> graphics.renderTooltip(font,font.split(Component.literal(entry.value()),280),x,y));
        // A track jump can request the last page directly; no need to fetch every preceding page.
        if (delay == 0 && !pages.waiting()) frame.rows().stream().map(EditorListPanel.Row::key).filter(pages::needs).findFirst()
                .ifPresent(index -> requestPage(index / ServerFieldSources.PAGE_SIZE * ServerFieldSources.PAGE_SIZE));
    }
    public boolean mouseClicked(double x,double y,int button) {
        if (list.mouseClicked(x,y,button)) return true;
        var entry = list.rowAt(x,y).map(row -> pages.entry(row.key()));
        if (!readOnly && button==0 && entry.isPresent()) { input.setValue(entry.get().value()); return true; }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseScrolled(double x,double y,double dx,double dy) {
        return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy);
    }
    public void onClose() { minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
