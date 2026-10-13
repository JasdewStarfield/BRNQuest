package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;

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
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextLayout;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
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
        if (quest.behavior().hideTextUntilComplete() && !isComplete(state.statuses().get(tracked.toString()))) title = "???";
        double scale = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.hudScale);
        Component heading = NativeText.component(title);
        QuestStatus questStatus = state.statuses().getOrDefault(tracked.toString(),QuestStatus.AVAILABLE);
        boolean completed = questStatus == QuestStatus.COMPLETED || questStatus == QuestStatus.REWARD_CLAIMED;
        var objectives = new ArrayList<Objective>();
        var measures = new ArrayList<QuestHudLayout.Measure>();
        for (var task : quest.tasks().stream().limit(completed ? 0 : QuestHudLayout.MAX_OBJECTIVES).toList()) {
            var presentation = ClientTaskPresentationRegistry.get(task.typeId());
            var context = context(minecraft,task);
            boolean done = presentation.confirmed(context.task(),context.storedProgress());
            Component requirement = presentation.objectiveTitle(context);
            Component progress = presentation.progressText(context,done);
            objectives.add(new Objective(task,presentation,context,requirement,progress,done));
            measures.add(new QuestHudLayout.Measure(minecraft.font.width(NativeText.sequence(requirement)),
                    minecraft.font.width(NativeText.sequence(progress))));
        }
        Component completion = Component.translatable("screen.brnquest.hud.quest_completed");
        if(completed) measures.add(new QuestHudLayout.Measure(minecraft.font.width(NativeText.sequence(completion)),0));
        int width = QuestHudLayout.width(graphics.guiWidth(),scale,
                BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.hudMaxWidth), minecraft.font.width(NativeText.sequence(heading)),measures);
        int textWidth = Math.max(1,width-QuestHudLayout.TEXT_LEFT-QuestHudLayout.RIGHT_PADDING);
        var headingLines = lines(minecraft,heading,Math.max(1,width-30));
        var requirementLines = objectives.stream().map(row->lines(minecraft,row.requirement(),textWidth)).toList();
        var completionLines = lines(minecraft,completion,textWidth);
        if(completed) requirementLines = List.of(completionLines);
        var plan = QuestHudLayout.plan(graphics.guiWidth(),graphics.guiHeight(),scale,width,headingLines.size(),
                minecraft.font.lineHeight,measures,requirementLines.stream().map(List::size).toList(),completed ? 1 : quest.tasks().size());
        if(plan.screenBounds()==null) return;
        // Avoid notifications using physical GUI pixels, so independent HUD scaling never invalidates collision checks.
        UiRect bounds = QuestNotificationLayout.avoid(plan.screenBounds(),
                QuestNotificationOverlay.obstruction(graphics.guiWidth(),graphics.guiHeight()),graphics.guiHeight());
        if(bounds==null) return;
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(bounds.left(),bounds.top(),0);
            graphics.pose().scale((float)scale,(float)scale,1);
            graphics.fill(0,0,width,plan.height(),0xDC252821);
            graphics.fill(0,0,2,plan.height(),GraystonePalette.ACCENT);
            graphics.blitSprite(ResourceLocation.parse("brnquest:quest/status/tracked"),7,6,10,10);
            for(int line=0;line<headingLines.size();line++)
                graphics.drawString(minecraft.font,headingLines.get(line),22,6+line* minecraft.font.lineHeight,GraystonePalette.ACCENT,false);
            for(int index=0;index<plan.rows().size();index++) {
                var row = plan.rows().get(index);
                if(completed) {
                    graphics.blitSprite(ResourceLocation.parse("brnquest:quest/status/completed"),7,row.top()+2,16,16);
                    for(int line=0;line<completionLines.size();line++) graphics.drawString(minecraft.font,completionLines.get(line),
                            QuestHudLayout.TEXT_LEFT,row.top()+3+line* minecraft.font.lineHeight,0xFF80D49B,false);
                    continue;
                }
                Objective objective = objectives.get(index);
                int iconY = row.top()+(row.height()-4-16)/2;
                var icon = objective.presentation().icon(objective.context().task());
                if(icon.isPresent()) icon.orElseThrow().render(graphics,minecraft.font,new UiRect(7,iconY,23,iconY+16),0xFFFFFFFF);
                else if(!objective.context().displayedItem().isEmpty()) graphics.renderItem(objective.context().displayedItem(),7,iconY);
                else ClientTaskPresentationRegistry.typeIcon(objective.task().typeId()).render(graphics,minecraft.font,
                        new UiRect(7,iconY,23,iconY+16),0xFFFFFFFF);
                if(objective.done()) {
                    // Completion remains recognizable without relying on green text alone.
                    graphics.pose().pushPose();
                    graphics.pose().translate(0,0,200);
                    graphics.fill(17,iconY+10,25,iconY+18,0xE0252821);
                    graphics.blitSprite(ResourceLocation.parse("brnquest:quest/status/completed"),17,iconY+10,8,8);
                    graphics.pose().popPose();
                }
                int color = objective.done() ? 0xFF80D49B : GraystonePalette.TEXT;
                if(row.inline()) {
                    int y = row.top()+3;
                    graphics.drawString(minecraft.font,NativeText.sequence(objective.requirement()),QuestHudLayout.TEXT_LEFT,y,color,false);
                    var progress = NativeText.sequence(objective.progress());
                    graphics.drawString(minecraft.font,progress,width-QuestHudLayout.RIGHT_PADDING-minecraft.font.width(progress),y,color,false);
                } else {
                    var wrapped = requirementLines.get(index);
                    for(int line=0;line<wrapped.size();line++) graphics.drawString(minecraft.font,wrapped.get(line),
                            QuestHudLayout.TEXT_LEFT,row.top()+line* minecraft.font.lineHeight,color,false);
                    var progress = EditorTextLayout.ellipsizeSequence(NativeText.sequence(objective.progress()),textWidth,
                            minecraft.font.getSplitter()::stringWidth,objective.progress().getStyle());
                    graphics.drawString(minecraft.font,progress,QuestHudLayout.TEXT_LEFT,
                            row.top()+wrapped.size()*minecraft.font.lineHeight,color,false);
                }
            }
            if(plan.hidden()>0) {
                Component more = Component.translatable("screen.brnquest.hud.more_objectives",plan.hidden());
                var visible = EditorTextLayout.ellipsizeSequence(NativeText.sequence(more),width-16,
                        minecraft.font.getSplitter()::stringWidth,more.getStyle());
                graphics.drawString(minecraft.font,visible,8,plan.height()-minecraft.font.lineHeight-6,GraystonePalette.SECONDARY,false);
            }
            // Flush item and sprite vertices under this transform before returning to other HUD layers.
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }

    private static boolean isComplete(QuestStatus status) {
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED;
    }

    private record Objective(TaskDefinition task, ClientTaskPresentation presentation, TaskPresentationContext context,
                             Component requirement, Component progress, boolean done) {}

    /** Long text wraps at normal font size, with explicit omission after two lines rather than a second shrinking pass. */
    private static List<FormattedCharSequence> lines(Minecraft minecraft, Component text, int width) {
        var wrapped = NativeText.split(minecraft.font,text,width);
        if(wrapped.size()<=2) return wrapped.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : wrapped;
        var last = FormattedCharSequence.composite(wrapped.get(1),FormattedCharSequence.forward("…",text.getStyle()));
        return List.of(wrapped.getFirst(),EditorTextLayout.ellipsizeSequence(last,width,
                minecraft.font.getSplitter()::stringWidth,text.getStyle()));
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
