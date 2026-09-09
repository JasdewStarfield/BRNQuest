package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

/** Minimal tracked-quest HUD rendered only from the immutable client cache. */
public final class QuestHud {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("brnquest", "tracked_quest");
    private QuestHud() {}
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;
        ClientQuestState.get().trackedQuest().flatMap(id -> ClientQuestState.get().book().map(s -> s.quests().get(id))).ifPresent(quest -> {
            if (quest == null || !ClientQuestState.get().visible(quest.id())) return;
            int taskLines = Math.min(3, quest.tasks().size());
            // Resolve from the same immutable book and client locale as the quest screen.
            String localizedTitle = ClientQuestState.get().book().map(snapshot ->
                    BookText.quest(snapshot.book(), quest,
                            minecraft.getLanguageManager().getSelected(), "title", quest.title())).orElse(quest.title());
            String questTitle = QuestPresentation.questTitle(localizedTitle, () -> quest.tasks().isEmpty()
                    ? net.minecraft.network.chat.Component.translatable("screen.brnquest.quest.untitled").getString()
                    : taskTitle(minecraft, quest.tasks().getFirst()));
            // Use each registered presentation for both receipt interpretation and live progress text.
            var lines = new java.util.ArrayList<String>();
            var completed = new java.util.ArrayList<Boolean>();
            var progressLabels = new java.util.ArrayList<String>();
            for (int index = 0; index < taskLines; index++) {
                TaskDefinition task = quest.tasks().get(index);
                var view = ApiViews.task(task);
                var presentation = ClientTaskPresentationRegistry.get(task.typeId());
                long stored = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
                boolean done = presentation.confirmed(view, stored);
                var context = new TaskPresentationContext(minecraft, view,
                        yourscraft.jasdewstarfield.brnquest.progress.QuestStatus.ACTIVE, stored, ItemStack.EMPTY);
                String progress = presentation.progressText(context, done).getString();
                lines.add((done ? "✓ " : "• ") + taskTitle(minecraft, task));
                progressLabels.add(progress);
                completed.add(done);
            }
            int width = Math.max(120, minecraft.font.width(questTitle) + 20);
            for (int index = 0; index < lines.size(); index++)
                width = Math.max(width, minecraft.font.width(lines.get(index)) + minecraft.font.width(progressLabels.get(index)) + 28);
            width = Math.min(width, Math.max(120, graphics.guiWidth() - 16));
            int left = graphics.guiWidth() - width - 8;
            int bottom = 30 + taskLines * 11;
            graphics.fill(left, 10, graphics.guiWidth() - 8, bottom, 0xC010141C);
            graphics.fill(left, 10, left + 3, bottom, 0xFF57C7F2);
            graphics.drawString(minecraft.font, "★ " + questTitle, left + 8, 16, 0xFF8DDCFA, false);
            for (int index = 0; index < taskLines; index++) {
                // Reserve the counter first so a long translated title cannot hide live progress.
                String progress = progressLabels.get(index);
                int progressWidth = minecraft.font.width(progress);
                int color = completed.get(index) ? 0xFF72D88D : 0xFFD8DEE8;
                graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(lines.get(index), Math.max(0, width - progressWidth - 24)),
                        left + 8, 28 + index * 11, color, false);
                graphics.drawString(minecraft.font, progress, left + width - progressWidth - 8, 28 + index * 11, color, false);
            }
        });
    }

    /** Resolves an untitled objective into the same localized item label shown by the detail screen. */
    private static String taskTitle(Minecraft minecraft, TaskDefinition task) {
        String custom = task.config().getOrDefault("title", "");
        if (!custom.isBlank()) return custom;
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(view);
        if (!itemSnbt.isBlank() && minecraft.level != null) {
            try {
                ItemStack stack = ItemStack.parseOptional(minecraft.level.registryAccess(),
                        TagParser.parseTag(itemSnbt));
                if (!stack.isEmpty()) return stack.getHoverName().getString();
            } catch (Exception ignored) {
                // Unknown optional-mod items remain readable through the type fallback below.
            }
        }
        long stored = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        return presentation.title(new TaskPresentationContext(minecraft, view,
                yourscraft.jasdewstarfield.brnquest.progress.QuestStatus.LOCKED, stored, ItemStack.EMPTY)).getString();
    }
}
