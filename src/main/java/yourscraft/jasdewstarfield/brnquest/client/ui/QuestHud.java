package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.ContentAwareCache;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextRenderer;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

/** Compact, read-only tracked objectives; presentation is shared with the detail rows. */
public final class QuestHud {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("brnquest", "tracked_quest");
    private static final ContentAwareCache<ResourceLocation, String, ItemStack> ITEMS = new ContentAwareCache<>();
    private static Object cachedLevel;
    private static String cachedRevision = "";
    private static ResourceLocation cachedQuest;
    private QuestHud() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        var state = ClientQuestState.get();
        var tracked = state.trackedQuest().orElse(null);
        // Bound the cache to one tracked quest, and never retain another world's registry-backed stacks.
        if (cachedLevel != minecraft.level || !cachedRevision.equals(state.revision())
                || !java.util.Objects.equals(cachedQuest, tracked)) {
            ITEMS.clear(); cachedLevel = minecraft.level; cachedRevision = state.revision(); cachedQuest = tracked;
        }
        if (minecraft.options.hideGui || minecraft.level == null || tracked == null) return;
        var snapshot = state.book().orElse(null);
        var quest = snapshot == null ? null : snapshot.quests().get(tracked);
        if (quest == null || !state.visible(tracked)) return;
        String localized = BookText.quest(snapshot.book(), quest, minecraft.getLanguageManager().getSelected(), "title", quest.title());
        String title = QuestPresentation.questTitle(localized, () -> quest.tasks().isEmpty()
                ? Component.translatable("screen.brnquest.quest.untitled").getString()
                : presentationTitle(minecraft, quest.tasks().getFirst()).getString());
        if (quest.behavior().hideTextUntilComplete()) title = "???";
        int count = Math.min(3, quest.tasks().size());
        int width = Math.min(320, Math.max(1, graphics.guiWidth() - 16));
        int left = graphics.guiWidth() - width - 8;
        graphics.fill(left, 10, left + width, 34 + count * 18, 0xDC252821);
        graphics.fill(left, 10, left + 2, 34 + count * 18, GraystonePalette.ACCENT);
        graphics.blitSprite(ResourceLocation.parse("brnquest:quest/status/tracked"), left + 6, 15, 10, 10);
        EditorTextRenderer.drawFittedString(graphics, minecraft.font, Component.literal(title),
                left + 21, 16, Math.max(1, width - 29), GraystonePalette.ACCENT, 0.75F);
        for (int index = 0; index < count; index++) {
            TaskDefinition task = quest.tasks().get(index);
            var presentation = ClientTaskPresentationRegistry.get(task.typeId());
            var context = context(minecraft, task);
            boolean done = presentation.confirmed(context.task(), context.storedProgress());
            int y = 31 + index * 18;
            var icon = presentation.icon(context.task());
            if (icon.isPresent()) icon.orElseThrow().render(graphics, minecraft.font, new UiRect(left + 6,y,left + 22,y + 16),0xFFFFFFFF);
            else if (!context.displayedItem().isEmpty()) graphics.renderItem(context.displayedItem(), left + 6,y);
            else ClientTaskPresentationRegistry.typeIcon(task.typeId()).render(graphics, minecraft.font,
                    new UiRect(left + 6,y,left + 22,y + 16),0xFFFFFFFF);
            int color = done ? 0xFF80D49B : GraystonePalette.TEXT;
            EditorTextRenderer.drawFittedString(graphics,minecraft.font,presentation.objectiveTitle(context),
                    left + 27,y,Math.max(1,width - 35),color,0.75F);
            EditorTextRenderer.drawFittedString(graphics,minecraft.font,presentation.progressText(context,done),
                    left + 27,y + 9,Math.max(1,width - 35),color,0.75F);
        }
    }

    private static Component presentationTitle(Minecraft minecraft, TaskDefinition task) {
        return ClientTaskPresentationRegistry.get(task.typeId()).title(context(minecraft, task));
    }

    private static TaskPresentationContext context(Minecraft minecraft, TaskDefinition task) {
        var presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        ItemStack parsed = ITEMS.get(task.id(), presentation.itemSnbt(view), snbt -> {
            try { return snbt.isBlank() ? ItemStack.EMPTY : ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(snbt)); }
            catch (Exception ignored) { return ItemStack.EMPTY; } // Missing addon items retain a readable type fallback.
        });
        long progress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        var initial = new TaskPresentationContext(minecraft,view,QuestStatus.ACTIVE,progress,parsed);
        // Dynamic representatives still follow the live inventory, without reparsing configured SNBT each frame.
        return new TaskPresentationContext(minecraft,view,QuestStatus.ACTIVE,progress,presentation.displayedItem(initial));
    }
}
