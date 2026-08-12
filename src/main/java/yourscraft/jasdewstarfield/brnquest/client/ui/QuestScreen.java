package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Native quest canvas with viewport culling, chapter selection, details, and intent-only controls. */
public final class QuestScreen extends Screen {
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private int chapterIndex;
    private double dragX;
    private double dragY;
    private boolean dragging;
    private final Map<ResourceLocation, ItemStack> iconCache = new HashMap<>();

    public QuestScreen() { super(Component.translatable("screen.brnquest.title")); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(10, 10, width - 10, height - 10, 0xE0151820);
        var snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.brnquest.loading"), width / 2, height / 2, 0xFFFFFF);
            return;
        }
        List<ChapterDefinition> chapters = snapshot.book().chapters();
        if (chapters.isEmpty()) return;
        chapterIndex = Math.min(chapterIndex, chapters.size() - 1);
        for (int i = 0; i < chapters.size(); i++) {
            int y = 24 + i * 18;
            graphics.fill(18, y - 2, 150, y + 14, i == chapterIndex ? 0xFF4A6A88 : 0xFF262D38);
            graphics.drawString(font, chapters.get(i).title(), 23, y + 1, 0xFFFFFF, false);
        }
        renderCanvas(graphics, chapters.get(chapterIndex), mouseX, mouseY);
        renderDetails(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCanvas(GuiGraphics graphics, ChapterDefinition chapter, int mouseX, int mouseY) {
        int left = 160, right = width - 230, top = 20, bottom = height - 20;
        graphics.enableScissor(left, top, right, bottom);
        for (QuestDefinition quest : chapter.quests()) for (ResourceLocation dependency : quest.dependencies()) {
            QuestDefinition parent = chapter.quests().stream().filter(q -> q.id().equals(dependency)).findFirst().orElse(null);
            if (parent != null) graphics.hLine(screenX(parent.x()), screenX(quest.x()), screenY((parent.y() + quest.y()) / 2), 0xFF647080);
        }
        for (QuestDefinition quest : chapter.quests()) {
            int x = screenX(quest.x()), y = screenY(quest.y());
            if (x < left - 20 || x > right + 20 || y < top - 20 || y > bottom + 20) continue;
            QuestStatus status = ClientQuestState.get().statuses().getOrDefault(quest.id().toString(), QuestStatus.LOCKED);
            int color = switch (status) { case COMPLETED, REWARD_CLAIMED -> 0xFF4C9A66; case AVAILABLE, ACTIVE -> 0xFFCF9F42; default -> 0xFF59606B; };
            if (quest.id().equals(ClientQuestState.get().selected())) color = 0xFF77B8F2;
            graphics.fill(x - 8, y - 8, x + 8, y + 8, color);
            ItemStack icon = icon(quest);
            if (!icon.isEmpty()) graphics.renderItem(icon, x - 8, y - 8);
            if (Math.abs(mouseX - x) <= 8 && Math.abs(mouseY - y) <= 8) graphics.renderTooltip(font, Component.literal(quest.title()), mouseX, mouseY);
        }
        graphics.disableScissor();
    }

    private void renderDetails(GuiGraphics graphics) {
        int left = width - 220;
        graphics.fill(left, 20, width - 20, height - 20, 0xFF202632);
        var snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null || ClientQuestState.get().selected() == null) return;
        QuestDefinition quest = snapshot.quests().get(ClientQuestState.get().selected());
        if (quest == null) return;
        graphics.drawWordWrap(font, Component.literal(quest.title()), left + 10, 34, 180, 0xFFFFFF);
        QuestStatus status = ClientQuestState.get().statuses().getOrDefault(quest.id().toString(), QuestStatus.LOCKED);
        graphics.drawString(font, status.name(), left + 10, 65, 0xBFC8D8, false);
        graphics.fill(left + 10, height - 72, left + 92, height - 52, 0xFF41698F);
        graphics.drawString(font, Component.translatable("screen.brnquest.track"), left + 18, height - 66, 0xFFFFFF, false);
        graphics.fill(left + 100, height - 72, width - 30, height - 52, 0xFF4C7F59);
        graphics.drawString(font, Component.translatable("screen.brnquest.complete"), left + 106, height - 66, 0xFFFFFF, false);
        if (!quest.rewards().isEmpty()) {
            graphics.fill(left + 10, height - 44, width - 30, height - 24, 0xFF8B663B);
            graphics.drawString(font, Component.translatable("screen.brnquest.claim"), left + 18, height - 38, 0xFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null) return super.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= 18 && mouseX <= 150) {
            int index = ((int) mouseY - 22) / 18;
            if (index >= 0 && index < snapshot.book().chapters().size()) { chapterIndex = index; return true; }
        }
        QuestDefinition selected = selectedQuest();
        int detailLeft = width - 220;
        if (selected != null && mouseX >= detailLeft + 10 && mouseY >= height - 72 && mouseY <= height - 52) {
            String revision = ClientQuestState.get().revision();
            if (mouseX < detailLeft + 96) BrnQuestNetwork.toggleTracked(revision, selected.id().toString()); else BrnQuestNetwork.completeCheckmark(revision, selected.id().toString());
            return true;
        }
        if (selected != null && mouseX >= detailLeft + 10 && mouseY >= height - 44 && mouseY <= height - 24 && !selected.rewards().isEmpty()) {
            selected.rewards().stream().filter(r -> !ClientQuestState.get().claimed().contains(r.id().toString())).findFirst()
                    .ifPresent(r -> BrnQuestNetwork.claimReward(ClientQuestState.get().revision(), r.id().toString()));
            return true;
        }
        if (mouseX > 160 && mouseX < width - 230) {
            ChapterDefinition chapter = snapshot.book().chapters().get(chapterIndex);
            for (QuestDefinition quest : chapter.quests()) if (Math.abs(mouseX - screenX(quest.x())) <= 10 && Math.abs(mouseY - screenY(quest.y())) <= 10) { ClientQuestState.get().selected(quest.id()); BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), quest.id().toString()); return true; }
            dragging = true; dragX = mouseX; dragY = mouseY;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseReleased(double x, double y, int button) { dragging = false; return super.mouseReleased(x, y, button); }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) { if (dragging) { panX += x - dragX; panY += y - dragY; dragX = x; dragY = y; return true; } return super.mouseDragged(x, y, button, dx, dy); }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { zoom = Math.max(0.35, Math.min(2.5, zoom + vertical * 0.1)); return true; }

    private QuestDefinition selectedQuest() { return ClientQuestState.get().book().map(s -> s.quests().get(ClientQuestState.get().selected())).orElse(null); }
    private ItemStack icon(QuestDefinition quest) {
        return iconCache.computeIfAbsent(quest.id(), ignored -> {
            if (quest.icon().isBlank() || minecraft.level == null) return ItemStack.EMPTY;
            try { return ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(quest.icon())); }
            catch (Exception exception) { return ItemStack.EMPTY; }
        });
    }
    private int screenX(double x) { return (int) (260 + panX + x * 34 * zoom); }
    private int screenY(double y) { return (int) (height / 2.0 + panY + y * 34 * zoom); }
}
