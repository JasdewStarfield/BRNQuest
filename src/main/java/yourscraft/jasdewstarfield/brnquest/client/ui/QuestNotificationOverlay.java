package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

/** One top-center surface renders after gameplay or the current Screen, and never registers input handlers. */
@EventBusSubscriber(modid = "brnquest", value = Dist.CLIENT)
public final class QuestNotificationOverlay {
    private static final QuestNotificationQueue QUEUE = new QuestNotificationQueue();
    private static final QuestNotificationMotion MOTION = new QuestNotificationMotion();
    private static final ContentAwareCache<ResourceLocation, String, ItemStack> ITEMS = new ContentAwareCache<>();
    private static String revision = "";
    private static final java.util.Set<String> SOUNDED = new java.util.HashSet<>();
    private static java.util.List<String> cachedCards = java.util.List.of();
    private QuestNotificationOverlay() {}

    public static void clear() { QUEUE.clear(); MOTION.clear(); ITEMS.clear(); revision = ""; cachedCards = java.util.List.of(); SOUNDED.clear(); }
    public static void receive(BrnQuestNetwork.QuestNotificationPayload payload) {
        if (payload.kind().equals("clear")) { clear(); return; }
        var state = ClientQuestState.get();
        var book = state.book().orElse(null);
        if (!BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.completionNotifications) || book == null
                || !book.revision().equals(payload.revision()) || !book.book().id().toString().equals(payload.bookId())) return;
        var questId = ResourceLocation.tryParse(payload.questId());
        if (questId == null || !book.quests().containsKey(questId)) return;
        try {
            java.util.UUID.fromString(payload.eventId());
            var kind = QuestNotificationQueue.Kind.valueOf(payload.kind().toUpperCase(java.util.Locale.ROOT));
            if (!revision.equals(payload.revision())) { clear(); revision = payload.revision(); }
            QUEUE.add(new QuestNotificationQueue.Notice(payload.eventId(), payload.questId(), payload.targetId(), kind),
                    System.nanoTime(), durationNanos());
        } catch (IllegalArgumentException ignored) { /* Unknown event kinds never disturb the active book or queue. */ }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gui(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) render(event.getGuiGraphics());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void screen(ScreenEvent.Render.Post event) { render(event.getGuiGraphics()); }

    private record Content(Component heading, Component title,
                           java.util.List<net.minecraft.util.FormattedCharSequence> details, EditorIcon icon, int height) {}

    private static long durationNanos() {
        return (long) (BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationDurationSeconds) * 1_000_000_000L);
    }

    private static QuestNotificationQueue.Stack stack(long now) {
        var minecraft = Minecraft.getInstance();
        var state = ClientQuestState.get();
        if (minecraft.level == null || !BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.completionNotifications)
                || !revision.equals(state.revision()) || !state.syncHealth().advertisedRevision().equals(revision)) {
            clear();
            return new QuestNotificationQueue.Stack(java.util.List.of(), 0, false);
        }
        var stack = QUEUE.stack(now, durationNanos());
        SOUNDED.retainAll(stack.visible().stream().map(QuestNotificationQueue.Active::id).toList());
        // A task-to-quest merge keeps its animation ID but changes the content and item-cache identity.
        var cards = stack.visible().stream().map(active -> active.notices().getFirst().eventId()).toList();
        if (!cachedCards.equals(cards)) {
            // Registry-backed item components stay bounded to the three visible cards.
            ITEMS.clear();
            cachedCards = cards;
        }
        return stack;
    }

    /** Queried by the HUD before drawing; uses the resting bounds throughout the entry/exit animation. */
    static UiRect obstruction(int screenWidth, int screenHeight) {
        if (Minecraft.getInstance().options.hideGui) return null;
        var stack = stack(System.nanoTime());
        if (stack.isEmpty()) return null;
        int configuredWidth = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationWidth);
        double scale = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationScale);
        int width = QuestNotificationLayout.logicalWidth(screenWidth,configuredWidth,scale);
        var content = content(stack.visible().getFirst(),stack.foldedCount(),width,
                QuestNotificationLayout.logicalHeightBudget(screenHeight,stack.visible().size(),scale));
        if(content==null) return null;
        scale = QuestNotificationLayout.fittingScale(screenHeight,content.height(),stack.visible().size(),scale);
        return scale>0 ? QuestNotificationLayout.scaledFront(screenWidth,width,content.height(),stack.visible().size(),scale) : null;
    }

    private static void render(GuiGraphics graphics) {
        var minecraft = Minecraft.getInstance();
        long now = System.nanoTime();
        var stack = stack(now);
        if (stack.isEmpty()) {
            MOTION.frame(java.util.List.of(), now, false, 0);
            return;
        }
        if (minecraft.options.hideGui) return;
        var active = stack.visible().getFirst();
        int configuredWidth = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationWidth);
        double requestedScale = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationScale);
        double opacity = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationBackgroundOpacity);
        int logicalWidth = QuestNotificationLayout.logicalWidth(graphics.guiWidth(),configuredWidth,requestedScale);
        int heightBudget = QuestNotificationLayout.logicalHeightBudget(graphics.guiHeight(),stack.visible().size(),requestedScale);
        var contents = new java.util.ArrayList<Content>();
        for (int index = 0; index < stack.visible().size(); index++) {
            var card = content(stack.visible().get(index), index == 0 ? stack.foldedCount() : 0,
                    logicalWidth,heightBudget);
            if (card == null) return;
            contents.add(card);
        }
        // In-place merges keep the card ID, so caption updates do not repeat the sound.
        if (SOUNDED.add(active.id())) {
            if (BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.notificationSound))
                // Match Toast.Visibility's explicit volume; the two-argument helper defaults to quiet UI clicks.
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F, 1.0F));
        }
        double scale = QuestNotificationLayout.fittingScale(graphics.guiHeight(),contents.getFirst().height(),stack.visible().size(),requestedScale);
        if(scale<=0) return;
        UiRect front = QuestNotificationLayout.scaledFront(graphics.guiWidth(),logicalWidth,contents.getFirst().height(),stack.visible().size(),scale);
        var targets = new java.util.ArrayList<QuestNotificationMotion.Target>();
        for (int index = 0; index < stack.visible().size(); index++)
            targets.add(new QuestNotificationMotion.Target(stack.visible().get(index).id(),
                    index == 0 ? front : QuestNotificationLayout.folded(front,index,scale)));
        // All cards share this clock and offset, then the queue removes them together at the deadline.
        double close = stack.exitAllowed() ? QuestNotificationMotion.ease(
                (active.elapsedNanos() - durationNanos() + 200_000_000L) / 200_000_000.0) : 0;
        var poses = MOTION.frame(targets, now, BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.reduceMotion),
                -close * (front.bottom() + 8));
        graphics.flush();
        for (int index = poses.size() - 1; index >= 0; index--) {
            var pose = poses.get(index);
            var card = contents.get(index);
            graphics.pose().pushPose();
            try {
                // Separate card depths also put the front background above native items rendered on rear cards.
                graphics.pose().translate(pose.left(), pose.top(), 4000 + (poses.size() - index) * 400);
                graphics.pose().scale((float) (pose.width() / logicalWidth), (float) (pose.height() / card.height()), 1);
                drawCard(graphics,card,logicalWidth,index==0 ? stack.foldedCount() : 0,index==0,opacity);
                graphics.flush();
            } finally { graphics.pose().popPose(); }
        }
    }

    private static void drawCard(GuiGraphics graphics, Content content, int width, int foldedCount, boolean front, double opacity) {
        GraystoneSurface.raised(graphics, new UiRect(0, 0, width, content.height()),
                front ? GraystonePalette.PANEL : GraystonePalette.INSET,true,opacity);
        if(opacity>0) graphics.fill(0,0,2,content.height(),GraystoneSurface.withOpacity(GraystonePalette.ACCENT,opacity));
        // A translucent front card must not reveal duplicate rear text or item icons through its backdrop.
        if(!front && opacity<1) return;
        var font = Minecraft.getInstance().font;
        int textWidth = Math.max(1, width - 44);
        content.icon().render(graphics, font, new UiRect(8, 12, 28, 32), 0xFFFFFFFF);
        EditorTextRenderer.drawFittedString(graphics, font, content.heading(), 34, 6,
                textWidth, GraystonePalette.ACCENT, 0.75F);
        EditorTextRenderer.drawFittedString(graphics, font, content.title(), 34, 19,
                textWidth, GraystonePalette.TEXT, 0.75F);
        int y = 32;
        for (var line : content.details()) {
            graphics.drawString(font, line, 34, y, GraystonePalette.SECONDARY, false);
            y += font.lineHeight + 1;
        }
        if (foldedCount > 0) {
            // The count includes both visible rear lips and groups beyond the three-card visual limit.
            EditorTextRenderer.drawFittedStringRight(graphics, font,
                    Component.translatable("message.brnquest.notification.folded", foldedCount),
                    width - 10, content.height() - 12, Math.max(1, width - 20), GraystonePalette.MUTED, 0.75F);
        }
    }

    private static Content content(QuestNotificationQueue.Active active, int foldedCount, int width, int heightBudget) {
        var minecraft = Minecraft.getInstance();
        var state = ClientQuestState.get();
        var first = active.notices().getFirst();
        var snapshot = state.book().orElse(null);
        var quest = snapshot == null ? null : snapshot.quests().get(ResourceLocation.tryParse(first.questId()));
        if (quest == null) return null;
        String kind = first.kind().name().toLowerCase(java.util.Locale.ROOT);
        int count = active.notices().size();
        Component heading = Component.translatable("message.brnquest.notification." + kind + (count > 1 ? ".many" : ""), count);
        var status = state.statuses().getOrDefault(first.questId(), QuestStatus.AVAILABLE);
        boolean hidden = quest.behavior().hideTextUntilComplete() && status != QuestStatus.COMPLETED && status != QuestStatus.REWARD_CLAIMED;
        String title = BookText.quest(snapshot.book(), quest, minecraft.getLanguageManager().getSelected(), "title", quest.title());
        Component questTitle = hidden ? Component.literal("???") : NativeText.component(QuestPresentation.questTitle(title,
                () -> quest.tasks().isEmpty() ? Component.translatable("screen.brnquest.quest.untitled").getString()
                        : taskTitle(quest, quest.tasks().getFirst()).getString()));
        boolean hiddenDetail = hidden || quest.behavior().hideDetailsUntilStartable()
                && (status == QuestStatus.LOCKED || status == QuestStatus.UNAVAILABLE);
        Component detail = hiddenDetail ? Component.literal("???") : detail(quest, first);
        if (count > 1) detail = detail.copy().append(detail.getString().isBlank() ? "" : "\n")
                .append(Component.translatable("message.brnquest.notification.others", count - 1));
        int lineHeight = minecraft.font.lineHeight + 1;
        int footer = foldedCount > 0 ? 14 : 0;
        int maxLines = Math.max(1, Math.min(4, (heightBudget - 39 - footer) / lineHeight));
        var wrapped = detail.getString().isBlank() ? java.util.List.<net.minecraft.util.FormattedCharSequence>of()
                : minecraft.font.split(detail, Math.max(1, width - 44));
        var lines = new java.util.ArrayList<>(wrapped.subList(0, Math.min(maxLines, wrapped.size())));
        if (wrapped.size() > maxLines) {
            // Preserve the compact surface while marking a requirement longer than four lines.
            String last = detail.getString();
            var plainLines = minecraft.font.getSplitter().splitLines(detail, Math.max(1, width - 44), net.minecraft.network.chat.Style.EMPTY);
            last = plainLines.get(maxLines - 1).getString();
            last = minecraft.font.plainSubstrByWidth(last, Math.max(1, width - 44 - minecraft.font.width("…"))) + "…";
            lines.set(maxLines - 1, Component.literal(last).getVisualOrderText());
        }
        int height = Math.max(36, 32 + lines.size() * lineHeight + 7) + footer;
        EditorIcon icon = hiddenDetail ? ClientTypeIconFallback.icon() : icon(quest, first);
        return new Content(heading, questTitle, java.util.List.copyOf(lines), icon, height);
    }

    private static Component detail(QuestDefinition quest, QuestNotificationQueue.Notice notice) {
        if (notice.kind() == QuestNotificationQueue.Kind.TASK)
            return quest.tasks().stream().filter(task -> task.id().toString().equals(notice.targetId()))
                    .findFirst().map(task -> ClientTaskPresentationRegistry.get(task.typeId()).completionDescription(context(quest, task)))
                    .orElse(Component.empty());
        if (notice.kind() == QuestNotificationQueue.Kind.REWARD)
            return quest.rewards().stream().filter(reward -> reward.id().toString().equals(notice.targetId())).findFirst()
                    .map(reward -> {
                        var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
                        var view = ApiViews.reward(reward);
                        ItemStack item = presentation.displayedItem(view, item(reward.id(), presentation.itemSnbt(view)));
                        return presentation.title(new RewardPresentationContext(Minecraft.getInstance(), view, false, true, item));
                    }).orElse(Component.empty());
        return Component.empty();
    }

    private static EditorIcon icon(QuestDefinition quest, QuestNotificationQueue.Notice notice) {
        if (notice.kind() == QuestNotificationQueue.Kind.TASK) {
            var task = quest.tasks().stream().filter(value -> value.id().toString().equals(notice.targetId())).findFirst().orElse(null);
            if (task != null) {
                var presentation = ClientTaskPresentationRegistry.get(task.typeId());
                var context = context(quest, task);
                return presentation.icon(context.task()).orElseGet(() -> context.displayedItem().isEmpty()
                        ? ClientTaskPresentationRegistry.typeIcon(task.typeId()) : EditorIcon.item(context.displayedItem()));
            }
        }
        if (notice.kind() == QuestNotificationQueue.Kind.REWARD) {
            var reward = quest.rewards().stream().filter(value -> value.id().toString().equals(notice.targetId())).findFirst().orElse(null);
            if (reward != null) {
                var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
                var view = ApiViews.reward(reward);
                var displayed = presentation.displayedItem(view, item(reward.id(), presentation.itemSnbt(view)));
                return presentation.icon(view).orElseGet(() -> displayed.isEmpty()
                        ? ClientRewardPresentationRegistry.typeIcon(reward.typeId()) : EditorIcon.item(displayed));
            }
        }
        var visual = QuestPresentation.visual(quest);
        ItemStack displayed = item(quest.id(), visual.itemSnbt());
        if (!displayed.isEmpty()) return EditorIcon.item(displayed);
        if (visual.icon().isPresent()) return visual.icon().orElseThrow();
        if (visual.kind() == QuestPresentation.VisualKind.TEXTURE) {
            var texture = QuestIconValue.textureId(visual.value()).orElseThrow();
            return new EditorIcon() {
                public int width(net.minecraft.client.gui.Font font) { return 16; }
                public void render(GuiGraphics graphics, net.minecraft.client.gui.Font font, UiRect bounds, int color) {
                    graphics.blit(texture, bounds.centerX() - 8, bounds.centerY() - 8, 0F, 0F, 16, 16, 16, 16);
                }
            };
        }
        return EditorIcon.glyph(Component.literal(switch (visual.kind()) {
            case CHECKMARK -> "✓"; case CUSTOM -> "◆"; default -> "?";
        }));
    }

    private static TaskPresentationContext context(QuestDefinition quest, TaskDefinition task) {
        var presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var minecraft = Minecraft.getInstance();
        var view = ApiViews.task(task);
        ItemStack parsed = item(task.id(), presentation.itemSnbt(view));
        var state = ClientQuestState.get();
        var initial = new TaskPresentationContext(minecraft, view,
                state.statuses().getOrDefault(quest.id().toString(), QuestStatus.AVAILABLE),
                state.taskProgress().getOrDefault(task.id().toString(), 0L), parsed);
        // Dynamic item representatives follow the same native presentation as the quest detail row.
        return new TaskPresentationContext(minecraft, view, initial.questStatus(), initial.storedProgress(),
                presentation.displayedItem(initial));
    }

    private static Component taskTitle(QuestDefinition quest, TaskDefinition task) {
        return ClientTaskPresentationRegistry.get(task.typeId()).objectiveTitle(context(quest, task));
    }

    private static ItemStack item(ResourceLocation id, String snbt) {
        return ITEMS.get(id, snbt, value -> {
            if (value.isBlank()) return ItemStack.EMPTY;
            try { return ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), net.minecraft.nbt.TagParser.parseTag(value)); }
            catch (RuntimeException | com.mojang.brigadier.exceptions.CommandSyntaxException ignored) { return ItemStack.EMPTY; }
        });
    }
}
