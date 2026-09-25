package yourscraft.jasdewstarfield.brnquest.builtin.client;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.builtin.network.RewardTableChoiceNetwork;
import java.util.*;

/** Explicit selection and confirmation, with suspended attempts on close and a shared scrolling list. */
public final class RewardTableChoiceScreen extends Screen implements RecipeLookupSource {
    private record Expected(String revision, String reward, Screen parent) {}
    private static Expected expected;
    private final Screen parent;
    private final ResourceLocation bookId;
    private final String revision, reward, attempt;
    private final List<JsonObject> entries = new ArrayList<>();
    private final Map<Integer, RewardEntryDetails> detailsCache = new HashMap<>();
    private String displayLocale = "";
    private Object displayLevel;
    private final EditorListPanel<Integer> list = new EditorListPanel<>();
    private String selected = "", error = "";
    private int total;
    private String occurrence="";
    private int version;
    private boolean waiting, selectionSent;
    private long lastFrame, sentAt;
    private Button confirm, more;
    private RewardTableChoiceScreen(Expected source, String attempt) {
        super(Component.translatable("screen.brnquest.choice.title"));
        // Capture display identity once: disconnects or later book switches must not change frozen candidates.
        bookId = ClientQuestState.get().book().map(snapshot -> snapshot.book().id())
                .orElse(ResourceLocation.fromNamespaceAndPath("brnquest", "display_preview"));
        parent = source.parent(); revision = source.revision(); reward = source.reward(); this.attempt = attempt;
    }
    /** Mark only explicit root clicks; delayed packets must not reopen a screen the player already left. */
    public static void expect(String revision, String reward) {
        expected = new Expected(revision,reward,Minecraft.getInstance().screen);
    }
    public static void clearExpected() { expected = null; }
    public static void receive(RewardTableChoiceNetwork.Page page) {
        Minecraft.getInstance().execute(() -> {
            var minecraft = Minecraft.getInstance();
            if (!ClientQuestState.get().revision().equals(page.revision())) return;
            RewardTableChoiceScreen screen;
            if (minecraft.screen instanceof RewardTableChoiceScreen current && current.attempt.equals(page.attempt())
                    && current.reward.equals(page.reward()) && current.revision.equals(page.revision())) screen = current;
            else {
                if (expected == null || minecraft.screen != expected.parent() || !expected.reward().equals(page.reward())
                        || !expected.revision().equals(page.revision()) || !page.state().equals("AWAITING_CHOICE")) return;
                screen = new RewardTableChoiceScreen(expected,page.attempt()); expected = null;
                minecraft.setScreen(screen);
            }
            if (screen.occurrence.equals(page.occurrence()) && page.version() < screen.version) return;
            // Duplicate or out-of-order pages must not unlock a newer request's waiting state.
            if (page.state().equals("AWAITING_CHOICE")) {
                if (!screen.occurrence.equals(page.occurrence()) && page.offset() != 0) return;
                if (screen.occurrence.equals(page.occurrence()) && page.offset() != screen.entries.size()
                        && !(page.offset() == 0 && screen.selectionSent)) return;
            }
            screen.waiting = false;
            if (page.state().equals("CONFIRMED")) { screen.onClose(); return; }
            if (!page.state().equals("AWAITING_CHOICE")) {
                screen.error = "screen.brnquest.choice.rejected"; return;
            }
            if(!screen.occurrence.equals(page.occurrence())) {
                if(page.offset()!=0)return;
                screen.entries.clear();screen.detailsCache.clear();screen.selected="";screen.selectionSent=false;screen.error="";
                screen.occurrence=page.occurrence();screen.version=page.version();
            } else if(page.offset()==0 && screen.selectionSent) {
                screen.entries.clear();screen.detailsCache.clear();screen.selected="";screen.selectionSent=false;
            }
            if (page.offset() != screen.entries.size()) return;
            var values = JsonParser.parseString(page.entries()).getAsJsonArray();
            values.forEach(value -> screen.entries.add(value.getAsJsonObject()));
            screen.version = page.version();
            screen.total = page.total(); screen.list.invalidate();
        });
    }
    protected void init() {
        list.invalidate(); lastFrame = 0;
        int left = Math.max(12,width/2-180), w = Math.min(360,width-24);
        more = addRenderableWidget(new EditorButtonWidget(left,height-58,w,20,Component.translatable("screen.brnquest.choice.more"), b -> request("",entries.size())));
        // Keep the explicit confirmation text next to the gift so selecting a row never implies a claim.
        addRenderableWidget(new EditorButtonWidget(left,height-30,w/2-3,20,
                EditorButton.Definition.iconAndText(Component.translatable("screen.brnquest.choice.later"), null,
                        QuestActionIcons.named("back")), b -> onClose()));
        confirm = addRenderableWidget(new EditorButtonWidget(left+w/2+3,height-30,w/2-3,20,
                EditorButton.Definition.iconAndText(Component.translatable("screen.brnquest.choice.confirm"), null,
                        QuestActionIcons.named("gift")), b -> request(selected,0)));
    }
    private void request(String entry, int offset) {
        if (waiting || !ClientQuestState.get().revision().equals(revision)) return;
        if (!entry.isEmpty() && entries.stream().noneMatch(value -> entry.equals(value.get("id").getAsString()))) return;
        waiting = true; sentAt = System.nanoTime(); error = "";
        if (!entry.isEmpty()) selectionSent = true; // Timeout retries keep the same decision; changing it requires reopening.
        PacketDistributor.sendToServer(new RewardTableChoiceNetwork.Request(revision,reward,attempt,occurrence,version,entry,offset));
    }
    public void tick() {
        if (!ClientQuestState.get().revision().equals(revision)) { error="screen.brnquest.choice.stale"; waiting=false; }
        else if (waiting && System.nanoTime()-sentAt > 5_000_000_000L) { waiting=false; error="screen.brnquest.choice.retry"; }
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial) {}
    public void render(GuiGraphics g,int x,int y,float partial) {
        if (parent != null) ChildScreenBackground.render(parent,g,width,height,partial);
        super.renderBackground(g,x,y,partial);
        g.fill(0,0,width,height,GraystonePalette.BACKDROP);
        boolean current = ClientQuestState.get().revision().equals(revision);
        confirm.active = current && !waiting && !selected.isEmpty();
        more.visible = entries.size()<total; more.active=current && !waiting;
        super.render(g,x,y,partial);
        g.drawCenteredString(font,title,width/2,14,0xFFFFFFFF);
        g.drawCenteredString(font,Component.translatable(error.isEmpty() ? "screen.brnquest.choice.hint" : error),width/2,32,
                error.isEmpty()?GraystonePalette.SECONDARY:0xFFFF9999);
        g.drawCenteredString(font,Component.translatable(waiting ? "screen.brnquest.choice.waiting" : "screen.brnquest.choice.loaded", entries.size(), total),width/2,46,0xFFB7C5A7);
        int left=Math.max(12,width/2-180), right=left+Math.min(360,width-24);
        long now=System.nanoTime(); double elapsed=lastFrame==0?0:Math.min(.1,(now-lastFrame)/1_000_000_000.0); lastFrame=now;
        list.advance(new UiRect(left,60,right-6,Math.max(60,height-64)),new UiRect(0,0,width,height),right-3,32,2,
                entries.size(),i->i,elapsed,BrnQuestClientConfig.VALUES.smoothSpeed.get());
        list.render(g,row->{
            var entry=entries.get(row.key()); var rect=row.bounds();
            g.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),selected.equals(entry.get("id").getAsString())?0xFF62604A:0xFF363A32);
            var details=details(row.key());
            // Shared details own decoration precedence and stack counts in every reward entry surface.
            details.icon().render(g,font,new UiRect(rect.left()+5,rect.top()+7,rect.left()+21,rect.top()+23),0xFFFFFFFF);
            g.drawString(font,font.plainSubstrByWidth(details.summary().getString(),rect.width()-32),rect.left()+28,rect.top()+11,0xFFFFFFFF,false);
        },()->{});
        list.rowAt(x,y).ifPresent(row -> {
            var details = details(row.key());
            // An item candidate uses one native tooltip throughout the row, including its text and edges.
            if (!details.lookupItem().isEmpty()) g.renderTooltip(font,details.lookupItem(),x,y);
            else g.renderTooltip(font,font.split(details.summary(),Math.max(80,width-40)),x,y);
        });
    }
    /** Frozen candidate content is decoded once per row; locale/world changes invalidate display-only values. */
    private RewardEntryDetails details(int index) {
        String locale = minecraft.getLanguageManager().getSelected();
        if (displayLevel != minecraft.level || !displayLocale.equals(locale)) {
            detailsCache.clear(); displayLevel = minecraft.level; displayLocale = locale;
        }
        return detailsCache.computeIfAbsent(index, key -> RewardEntryDetails.resolve(minecraft, view(entries.get(key))));
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // Tab remains native button navigation; arrows browse rows and Enter selects without claiming.
        if (key == 258) list.clearFocus();
        if (key >= 262 && key <= 269 && !waiting && !selectionSent && list.navigate(key, hasShiftDown())) {
            setFocused(null); return true;
        }
        if ((key == 257 || key == 335) && getFocused() == null && !waiting && !selectionSent) {
            var row = list.focusedRow();
            if (row.isPresent()) { selected = entries.get(row.get().key()).get("id").getAsString(); return true; }
        }
        return super.keyPressed(key, scan, modifiers);
    }
    private RewardView view(JsonObject entry) {
        var type=ResourceLocation.parse(entry.get("type").getAsString());
        Map<String,String> config=new LinkedHashMap<>(); entry.getAsJsonObject("config").entrySet().forEach(e->config.put(e.getKey(),e.getValue().getAsString()));
        // Keep the real root identity, but use only the frozen page configuration for content.
        return new RewardView(bookId,ResourceLocation.parse(reward),type,config,"manual",false);
    }
    public boolean mouseClicked(double x,double y,int button) {
        if(list.mouseClicked(x,y,button)) return true;
        var row=list.rowAt(x,y);
        if(button==0 && row.isPresent() && !waiting && !selectionSent) { selected=entries.get(row.get().key()).get("id").getAsString(); return true; }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return list.mouseDragged(y,button) || super.mouseDragged(x,y,button,dx,dy); }
    public boolean mouseReleased(double x,double y,int button) { return list.mouseReleased(button) || super.mouseReleased(x,y,button); }
    public boolean mouseScrolled(double x,double y,double dx,double dy) {
        return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy);
    }
    @Override public Optional<RecipeLookupTarget> recipeLookupTargetAt(double x, double y) {
        // Native item help spans the visible row, so optional JEI lookup must use that same area.
        return list.rowAt(x, y).flatMap(row -> RecipeLookupTarget.clipped(
                details(row.key()).lookupItem(), row.bounds(), row.visible()));
    }
    public void onClose() { ClientQuestState.get().finishRewardChoice(reward); expected=null; minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return parent!=null && parent.isPauseScreen(); }
}
