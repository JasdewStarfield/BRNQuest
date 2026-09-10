package yourscraft.jasdewstarfield.brnquest.client.ui;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.network.RewardTableChoiceNetwork;
import yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableChoice;
import java.util.*;

/** Explicit selection and confirmation, with suspended attempts on close and a shared scrolling list. */
public final class RewardTableChoiceScreen extends Screen {
    private record Expected(String revision, String reward, Screen parent) {}
    private static Expected expected;
    private final Screen parent;
    private final String revision, reward, attempt;
    private final List<JsonObject> entries = new ArrayList<>();
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
            screen.waiting = false;
            if (page.state().equals("CONFIRMED")) { screen.onClose(); return; }
            if (!page.state().equals("AWAITING_CHOICE")) {
                screen.error = "screen.brnquest.choice.rejected"; return;
            }
            if(!screen.occurrence.equals(page.occurrence())) {
                if(page.offset()!=0)return;
                screen.entries.clear();screen.selected="";screen.selectionSent=false;screen.error="";
                screen.occurrence=page.occurrence();screen.version=page.version();
            } else if(page.offset()==0 && screen.selectionSent) {
                screen.entries.clear();screen.selected="";screen.selectionSent=false;
            }
            if (page.offset() != screen.entries.size()) return;
            var values = JsonParser.parseString(page.entries()).getAsJsonArray();
            values.forEach(value -> screen.entries.add(value.getAsJsonObject()));
            screen.total = page.total(); screen.list.invalidate();
        });
    }
    protected void init() {
        list.invalidate(); lastFrame = 0;
        int left = Math.max(12,width/2-180), w = Math.min(360,width-24);
        more = addRenderableWidget(Button.builder(Component.translatable("screen.brnquest.choice.more"), b -> request("",entries.size()))
                .bounds(left,height-58,w,20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.brnquest.choice.later"), b -> onClose())
                .bounds(left,height-30,w/2-3,20).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.brnquest.choice.confirm"), b -> request(selected,0))
                .bounds(left+w/2+3,height-30,w/2-3,20).build());
    }
    private void request(String entry, int offset) {
        if (waiting) return;
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
        g.fill(0,0,width,height,0x70151820);
        boolean current = ClientQuestState.get().revision().equals(revision);
        confirm.active = current && !waiting && !selected.isEmpty();
        more.visible = entries.size()<total; more.active=current && !waiting;
        super.render(g,x,y,partial);
        g.drawCenteredString(font,title,width/2,14,0xFFFFFFFF);
        g.drawCenteredString(font,Component.translatable(error.isEmpty() ? "screen.brnquest.choice.hint" : error),width/2,32,
                error.isEmpty()?0xFFBFCBDC:0xFFFF9999);
        g.drawCenteredString(font,font.plainSubstrByWidth(occurrence,width-24),width/2,46,0xFF9FB0C2);
        int left=Math.max(12,width/2-180), right=left+Math.min(360,width-24);
        long now=System.nanoTime(); double elapsed=lastFrame==0?0:Math.min(.1,(now-lastFrame)/1_000_000_000.0); lastFrame=now;
        list.advance(new UiRect(left,60,right-6,Math.max(60,height-64)),new UiRect(0,0,width,height),right-3,32,2,
                entries.size(),i->i,elapsed,BrnQuestClientConfig.VALUES.smoothSpeed.get());
        list.render(g,row->{
            var entry=entries.get(row.key()); var rect=row.bounds();
            g.fill(rect.left(),rect.top(),rect.right(),rect.bottom(),selected.equals(entry.get("id").getAsString())?0xDD456780:0xAA263646);
            var stack=stack(entry); if(!stack.isEmpty()) {
                g.renderItem(stack,rect.left()+5,rect.top()+7);
                g.renderItemDecorations(font,stack,rect.left()+5,rect.top()+7);
            }
            g.drawString(font,font.plainSubstrByWidth(label(entry).getString(),rect.width()-32),rect.left()+28,rect.top()+11,0xFFFFFFFF,false);
        },()->{});
        list.rowAt(x,y).ifPresent(row -> g.renderTooltip(font,font.split(label(entries.get(row.key())),Math.max(80,width-40)),x,y));
    }
    private RewardView view(JsonObject entry) {
        var type=ResourceLocation.parse(entry.get("type").getAsString());
        Map<String,String> config=new LinkedHashMap<>(); entry.getAsJsonObject("config").entrySet().forEach(e->config.put(e.getKey(),e.getValue().getAsString()));
        return new RewardView(type,type,type,config,"manual",false);
    }
    private ItemStack stack(JsonObject entry) {
        try { var view=view(entry); String snbt=ClientRewardPresentationRegistry.get(view.typeId()).itemSnbt(view);
            if(!snbt.isBlank() && minecraft.level!=null) return ClientRewardPresentationRegistry.get(view.typeId()).displayedItem(view,
                    ItemStack.parseOptional(minecraft.level.registryAccess(),net.minecraft.nbt.TagParser.parseTag(snbt)));
        } catch(Exception ignored) { /* Display-only data may omit an oversized icon; the option stays selectable. */ }
        return ItemStack.EMPTY;
    }
    private Component label(JsonObject entry) {
        var view=view(entry); String title=view.config().getOrDefault("title","");
        if(!title.isBlank()) return Component.literal(title);
        var stack=stack(entry); if(!stack.isEmpty()) return stack.getHoverName().copy().append(" × "+stack.getCount());
        var label=ClientRewardPresentationRegistry.get(view.typeId()).typeName(view);
        String amount=view.config().getOrDefault("xp",view.config().getOrDefault("xp_levels",""));
        return amount.isBlank()?label:label.copy().append(" × "+amount);
    }
    public boolean mouseClicked(double x,double y,int button) {
        if(list.mouseClicked(x,y,button)) return true;
        var row=list.rowAt(x,y);
        if(button==0 && row.isPresent() && !waiting && !selectionSent) { selected=entries.get(row.get().key()).get("id").getAsString(); return true; }
        return super.mouseClicked(x,y,button);
    }
    public boolean mouseScrolled(double x,double y,double dx,double dy) {
        return list.mouseScrolled(x,y,dy,BrnQuestClientConfig.VALUES.scrollStep.get()) || super.mouseScrolled(x,y,dx,dy);
    }
    public void onClose() { expected=null; minecraft.setScreen(parent); }
    public boolean isPauseScreen() { return parent!=null && parent.isPauseScreen(); }
}
