package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Quest-book UI with grouped navigation, a scalable directed graph, and intent-only details. */
public final class QuestScreen extends Screen {
    private static final int NAV_LEFT = 0;
    private static final int NAV_RIGHT = 150;
    private static final int NAV_HANDLE_WIDTH = 12;
    private static final int CANVAS_MARGIN = 0;
    private static final int DETAIL_WIDTH = 250;
    private static final int NODE_BASE_SIZE = 18;
    private static final int NAV_TOP = 0;
    private static final int NAV_BOTTOM_MARGIN = 0;
    private static final int DETAIL_CONTENT_TOP = 8;
    private static final int DETAIL_CONTENT_BOTTOM_MARGIN = 8;

    private double panX;
    private double panY;
    private double zoom = 1.0;
    private double navigationScroll;
    private double detailScroll;
    private int navigationContentHeight;
    private int detailContentHeight;
    private int chapterIndex;
    private double dragX;
    private double dragY;
    private boolean dragging;
    private boolean detailsOpen;
    private boolean navigationCollapsed;
    private final Map<ResourceLocation, ItemStack> itemCache = new HashMap<>();
    private final List<RewardHitbox> rewardHitboxes = new ArrayList<>();
    private final List<TaskHitbox> taskHitboxes = new ArrayList<>();
    private ItemStack hoveredDetailStack = ItemStack.EMPTY;
    private Component hoveredDetailText;

    public QuestScreen() {
        super(Component.translatable("screen.brnquest.title"));
    }

    /** Prevents Screen.render from applying a second blur pass over the completed UI. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Blur the world once, then render every BRNQuest layer above it.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xC8151820);
        var snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.brnquest.loading"), width / 2, height / 2, 0xFFFFFF);
            return;
        }

        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
        if (chapters.isEmpty()) return;
        chapterIndex = Math.min(chapterIndex, chapters.size() - 1);
        renderNavigation(graphics, snapshot.book(), chapters.get(chapterIndex));
        renderCanvas(graphics, chapters.get(chapterIndex), mouseX, mouseY);
        if (detailsOpen) renderDetails(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderNavigation(GuiGraphics graphics, QuestBookDefinition book, ChapterDefinition selectedChapter) {
        if (navigationCollapsed) {
            graphics.fill(0, 0, NAV_HANDLE_WIDTH, height, 0xD01B222C);
            graphics.drawCenteredString(font, "›", NAV_HANDLE_WIDTH / 2, height / 2 - 4, 0xFFB7C5D8);
            return;
        }
        int viewportHeight = navigationViewportHeight();
        navigationContentHeight = navigationContentHeight(book);
        navigationScroll = QuestViewportMath.clampScroll(navigationScroll, navigationContentHeight, viewportHeight);
        int y = NAV_TOP - (int) Math.round(navigationScroll);
        graphics.enableScissor(NAV_LEFT, NAV_TOP, NAV_RIGHT + 6, height - NAV_BOTTOM_MARGIN);
        for (QuestPresentation.NavigationEntry entry : QuestPresentation.navigation(book)) {
            if (entry.group() != null) {
                graphics.fill(NAV_LEFT, y, NAV_RIGHT, y + 14, 0xD01B222C);
                graphics.drawString(font, Component.literal("▾ " + entry.group().title()), NAV_LEFT + 5, y + 3, 0xFFB7C5D8, false);
                y += 16;
                continue;
            }
            ChapterDefinition chapter = entry.chapter();
            int color = chapter.id().equals(selectedChapter.id()) ? 0xFF4A6A88 : 0xE0262D38;
            graphics.fill(NAV_LEFT + 8, y, NAV_RIGHT, y + 16, color);
            graphics.drawString(font, Component.literal(chapter.title()), NAV_LEFT + 14, y + 4, 0xFFFFFF, false);
            y += 18;
        }
        graphics.disableScissor();
        renderScrollbar(graphics, NAV_RIGHT + 2, NAV_TOP, height - NAV_BOTTOM_MARGIN,
                navigationContentHeight, viewportHeight, navigationScroll);
        graphics.fill(NAV_RIGHT + 4, 0, NAV_RIGHT + 4 + NAV_HANDLE_WIDTH, height, 0xD01B222C);
        graphics.drawCenteredString(font, "‹", NAV_RIGHT + 4 + NAV_HANDLE_WIDTH / 2, height / 2 - 4, 0xFFB7C5D8);
    }

    private void renderCanvas(GuiGraphics graphics, ChapterDefinition chapter, int mouseX, int mouseY) {
        int right = detailsOpen ? detailLeft() - 6 : width - CANVAS_MARGIN;
        int top = 0;
        int bottom = height;
        graphics.enableScissor(canvasLeft(), top, right, bottom);
        renderGrid(graphics, right, top, bottom);

        Map<ResourceLocation, QuestDefinition> chapterQuests = new HashMap<>();
        chapter.quests().forEach(quest -> chapterQuests.put(quest.id(), quest));
        for (QuestDefinition quest : chapter.quests()) {
            for (ResourceLocation dependency : quest.dependencies()) {
                QuestDefinition parent = chapterQuests.get(dependency);
                if (parent != null) renderDependency(graphics, parent, quest);
            }
        }
        for (QuestDefinition quest : chapter.quests()) renderNode(graphics, quest, right, top, bottom, mouseX, mouseY);
        graphics.disableScissor();
    }

    private void renderGrid(GuiGraphics graphics, int right, int top, int bottom) {
        int spacing = Math.max(12, (int) Math.round(34 * zoom));
        int originX = screenX(0);
        int originY = screenY(0);
        int firstX = canvasLeft() + Math.floorMod(originX - canvasLeft(), spacing);
        int firstY = top + Math.floorMod(originY - top, spacing);
        for (int x = firstX; x < right; x += spacing) graphics.fill(x, top, x + 1, bottom, 0x243C4655);
        for (int y = firstY; y < bottom; y += spacing) graphics.fill(canvasLeft(), y, right, y + 1, 0x243C4655);
    }

    /** Draws an orthogonal dependency path whose arrow always points at the dependent node. */
    private void renderDependency(GuiGraphics graphics, QuestDefinition parent, QuestDefinition child) {
        int x1 = screenX(parent.x());
        int y1 = screenY(parent.y());
        int x2 = screenX(child.x());
        int y2 = screenY(child.y());
        int radius = nodeSize() / 2;
        int thickness = Math.max(1, (int) Math.round(zoom));
        int color = 0xC0798799;

        if (Math.abs(x2 - x1) < radius * 2) {
            int direction = y2 >= y1 ? 1 : -1;
            int startY = y1 + direction * radius;
            int endY = y2 - direction * radius;
            vertical(graphics, x1, startY, endY, thickness, color);
            arrowVertical(graphics, x1, endY, direction, thickness, color);
            return;
        }

        int direction = x2 >= x1 ? 1 : -1;
        int startX = x1 + direction * radius;
        int endX = x2 - direction * radius;
        int middleX = (startX + endX) / 2;
        horizontal(graphics, startX, middleX, y1, thickness, color);
        vertical(graphics, middleX, y1, y2, thickness, color);
        horizontal(graphics, middleX, endX, y2, thickness, color);
        arrowHorizontal(graphics, endX, y2, direction, thickness, color);
    }

    private void renderNode(GuiGraphics graphics, QuestDefinition quest, int right, int top, int bottom, int mouseX, int mouseY) {
        int x = screenX(quest.x());
        int y = screenY(quest.y());
        int size = nodeSize();
        int radius = size / 2;
        if (x - radius < canvasLeft() || x + radius > right || y - radius < top || y + radius > bottom) return;

        QuestStatus status = status(quest);
        int color = switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF4C9A66;
            case AVAILABLE, ACTIVE -> 0xFFCF9F42;
            default -> 0xFF59606B;
        };
        boolean selected = quest.id().equals(ClientQuestState.get().selected());
        boolean tracked = status == QuestStatus.ACTIVE;
        if (tracked) fillChamfer(graphics, x, y, size + 7, 0xFF57C7F2);
        fillChamfer(graphics, x, y, size + (selected ? 4 : 2), selected ? 0xFF91C9F4 : 0xFF222936);
        fillChamfer(graphics, x, y, size, color);
        renderQuestVisual(graphics, quest, x, y, size);

        if (Math.abs(mouseX - x) <= radius && Math.abs(mouseY - y) <= radius) {
            graphics.renderTooltip(font, Component.literal(questTitle(quest)), mouseX, mouseY);
        }
    }

    private void renderQuestVisual(GuiGraphics graphics, QuestDefinition quest, int x, int y, int size) {
        QuestPresentation.QuestVisual visual = QuestPresentation.visual(quest);
        if (visual.kind() == QuestPresentation.VisualKind.ITEM) {
            ItemStack stack = item(quest.id(), visual.itemSnbt());
            if (!stack.isEmpty()) {
                float scale = Math.max(0.25F, size / 18.0F);
                graphics.pose().pushPose();
                graphics.pose().translate(x - 8.0F * scale, y - 8.0F * scale, 0);
                graphics.pose().scale(scale, scale, 1.0F);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popPose();
                return;
            }
        }
        String symbol = switch (visual.kind()) {
            case CHECKMARK -> "✓";
            case CUSTOM -> "◆";
            default -> "?";
        };
        float scale = Math.max(0.5F, size / 18.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, symbol, 0, -font.lineHeight / 2, 0xFFFFFFFF);
        graphics.pose().popPose();
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        rewardHitboxes.clear();
        taskHitboxes.clear();
        hoveredDetailStack = ItemStack.EMPTY;
        hoveredDetailText = null;
        int left = detailLeft();
        graphics.fill(left, 0, width, height, 0xF0202632);
        graphics.drawString(font, Component.literal("×"), width - 14, 4, 0xFFFFFF, false);

        QuestDefinition quest = selectedQuest();
        if (quest == null) return;
        QuestStatus status = status(quest);
        int contentLeft = left + 10;
        int contentWidth = DETAIL_WIDTH - 24;
        int viewportHeight = detailViewportHeight();
        int y = DETAIL_CONTENT_TOP - (int) Math.round(detailScroll);
        graphics.enableScissor(left + 1, DETAIL_CONTENT_TOP, width - 10, height - DETAIL_CONTENT_BOTTOM_MARGIN);
        y = drawWrapped(graphics, questTitle(quest), contentLeft, y, contentWidth - 14, 0xFFFFFF) + 3;
        if (!quest.subtitle().isBlank()) y = drawWrapped(graphics, quest.subtitle(), contentLeft, y, contentWidth, 0xFFB7C5D8) + 4;

        boolean ready = canSubmit(quest, status);
        Component statusText = Component.translatable("screen.brnquest.status." + status.name().toLowerCase(java.util.Locale.ROOT));
        graphics.drawString(font, statusText, contentLeft, y, statusColor(status), false);
        int statusTop = y;
        int statusWidth = font.width(statusText);
        if (status == QuestStatus.LOCKED) {
            // Cyan underline and info glyph advertise that the locked reason is inspectable.
            graphics.fill(contentLeft, y + font.lineHeight, contentLeft + statusWidth, y + font.lineHeight + 1, 0xFF68BDE8);
            graphics.drawString(font, Component.literal("ⓘ"), contentLeft + statusWidth + 4, y, 0xFF68BDE8, false);
            statusWidth += 4 + font.width("ⓘ");
        }
        if (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) {
            String pin = status == QuestStatus.ACTIVE ? "★" : "☆";
            int pinX = detailTrackX(pin);
            graphics.drawString(font, Component.literal(pin), pinX, y, status == QuestStatus.ACTIVE ? 0xFF57C7F2 : 0xFFB7C5D8, false);
            if (mouseX >= pinX - 2 && mouseX <= pinX + font.width(pin) + 2 && mouseY >= y && mouseY <= y + font.lineHeight) {
                hoveredDetailText = Component.translatable(status == QuestStatus.ACTIVE
                        ? "screen.brnquest.untrack" : "screen.brnquest.track");
            }
        }
        if ((status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && ready) {
            graphics.drawString(font, Component.translatable("screen.brnquest.ready"), contentLeft + font.width(statusText) + 6, y, 0xFF72D88D, false);
        }
        y += 16;

        if (!quest.description().isBlank()) {
            y = drawWrapped(graphics, quest.description(), contentLeft, y, contentWidth, 0xFFE1E6EE) + 8;
        }

        graphics.drawString(font, Component.translatable("screen.brnquest.requirements"), contentLeft, y, 0xFF8FB8E2, false);
        y += 13;
        if (quest.tasks().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.brnquest.no_requirements"), contentLeft, y, 0xFF9AA6B5, false);
            y += 18;
        } else {
            for (TaskDefinition task : quest.tasks()) y = renderTask(graphics, quest, task, status, contentLeft, y, contentWidth, mouseX, mouseY);
        }

        if (!quest.rewards().isEmpty()) {
            y += 4;
            graphics.drawString(font, Component.translatable("screen.brnquest.rewards"), contentLeft, y, 0xFFE6B55B, false);
            y += 13;
            int rewardTop = y;
            int rewardColumns = Math.max(1, contentWidth / QuestViewportMath.REWARD_ROW_HEIGHT);
            int column = 0;
            for (RewardDefinition reward : quest.rewards()) {
                int rewardX = contentLeft + column * QuestViewportMath.REWARD_ROW_HEIGHT;
                renderReward(graphics, reward, rewardX, y, status, mouseX, mouseY);
                column++;
                if (column >= rewardColumns) {
                    column = 0;
                    y += QuestViewportMath.REWARD_ROW_HEIGHT;
                }
            }
            // Compute from row count so a partially populated final row is never clipped.
            y = rewardTop + QuestViewportMath.rewardGridHeight(quest.rewards().size(), rewardColumns);
        }
        detailContentHeight = Math.max(0, y + (int) Math.round(detailScroll) - DETAIL_CONTENT_TOP + 8);
        detailScroll = QuestViewportMath.clampScroll(detailScroll, detailContentHeight, viewportHeight);
        graphics.disableScissor();
        renderScrollbar(graphics, width - 8, DETAIL_CONTENT_TOP, height - DETAIL_CONTENT_BOTTOM_MARGIN,
                detailContentHeight, viewportHeight, detailScroll);

        int visibleStatusY = statusTop;
        if (status == QuestStatus.LOCKED && mouseX >= contentLeft && mouseX <= contentLeft + statusWidth
                && mouseY >= visibleStatusY && mouseY <= visibleStatusY + font.lineHeight
                && visibleStatusY >= DETAIL_CONTENT_TOP && visibleStatusY < height - DETAIL_CONTENT_BOTTOM_MARGIN) {
            graphics.renderComponentTooltip(font, dependencyTooltip(quest), mouseX, mouseY);
        } else if (!hoveredDetailStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredDetailStack, mouseX, mouseY);
        } else if (hoveredDetailText != null) {
            graphics.renderTooltip(font, hoveredDetailText, mouseX, mouseY);
        }

    }

    private int renderTask(GuiGraphics graphics, QuestDefinition quest, TaskDefinition task, QuestStatus status,
                           int x, int y, int width, int mouseX, int mouseY) {
        boolean satisfied = taskSatisfied(task, status);
        graphics.fill(x, y, x + width, y + 24, satisfied ? 0x663B6749 : 0x66343D49);
        ItemStack stack = task.typeId().getPath().equals("item") ? item(task.id(), task.config().getOrDefault("item", "")) : ItemStack.EMPTY;
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 3, y + 4);
        else graphics.drawCenteredString(font, task.typeId().getPath().equals("checkmark") ? "✓" : "?", x + 11, y + 8, 0xFFFFFFFF);

        String title = task.config().getOrDefault("title", "");
        if (title.isBlank()) title = !stack.isEmpty() ? stack.getHoverName().getString() : task.typeId().getPath();
        graphics.drawString(font, Component.literal(title), x + 24, y + 3, satisfied ? 0xFF8BE2A0 : 0xFFFFFFFF, false);
        String progress = taskProgressText(task, stack, satisfied);
        graphics.drawString(font, Component.literal(progress), x + 24, y + 13, 0xFFABB7C6, false);
        if (task.optional()) graphics.drawString(font, Component.translatable("screen.brnquest.optional"), x + width - 38, y + 13, 0xFF9AA6B5, false);
        boolean visible = y >= DETAIL_CONTENT_TOP && y + 24 <= height - DETAIL_CONTENT_BOTTOM_MARGIN;
        boolean interactive = (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && !satisfied
                && (task.typeId().getPath().equals("checkmark") || task.typeId().getPath().equals("item"));
        if (interactive && visible) taskHitboxes.add(new TaskHitbox(x, y, x + width, y + 24, quest, task));
        if (visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 24) {
            if (!stack.isEmpty()) hoveredDetailStack = stack;
            else hoveredDetailText = Component.literal(title);
        }
        return y + 28;
    }

    private void renderReward(GuiGraphics graphics, RewardDefinition reward, int x, int y, QuestStatus status, int mouseX, int mouseY) {
        boolean claimed = ClientQuestState.get().claimed().contains(reward.id().toString());
        boolean claimable = isCompleted(status) && !claimed;
        ItemStack stack = reward.typeId().getPath().equals("item") ? item(reward.id(), reward.config().getOrDefault("item", "")) : ItemStack.EMPTY;
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 4, y + 4);
        else graphics.drawCenteredString(font, "?", x + 12, y + 8, 0xFFFFFFFF);
        if (claimable) {
            graphics.fill(x + 2, y + 2, x + 22, y + 3, 0xFFE6B55B);
            graphics.fill(x + 2, y + 21, x + 22, y + 22, 0xFFE6B55B);
        }
        if (claimed) graphics.drawString(font, "✓", x + 15, y + 14, 0xFF8BE2A0, true);
        boolean visible = y >= DETAIL_CONTENT_TOP && y + 24 <= height - DETAIL_CONTENT_BOTTOM_MARGIN;
        if (claimable && visible) rewardHitboxes.add(new RewardHitbox(x, y, x + 24, y + 24, reward));
        if (visible && mouseX >= x && mouseX <= x + 24 && mouseY >= y && mouseY <= y + 24) {
            String title = reward.config().getOrDefault("title", "");
            if (title.isBlank()) title = !stack.isEmpty() ? stack.getHoverName().getString() : reward.typeId().getPath();
            if (!stack.isEmpty()) hoveredDetailStack = stack;
            else hoveredDetailText = Component.literal(title);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null) return super.mouseClicked(mouseX, mouseY, button);

        int navigationHandleLeft = navigationCollapsed ? 0 : NAV_RIGHT + 4;
        if (mouseX >= navigationHandleLeft && mouseX <= navigationHandleLeft + NAV_HANDLE_WIDTH) {
            navigationCollapsed = !navigationCollapsed;
            return true;
        }

        if (!navigationCollapsed && mouseX >= NAV_RIGHT && mouseX <= NAV_RIGHT + 4 && navigationContentHeight > navigationViewportHeight()) {
            navigationScroll = scrollFromTrack(mouseY, NAV_TOP, height - NAV_BOTTOM_MARGIN,
                    navigationContentHeight, navigationViewportHeight());
            return true;
        }
        if (detailsOpen && mouseX >= width - 12 && mouseX <= width && detailContentHeight > detailViewportHeight()
                && mouseY >= DETAIL_CONTENT_TOP && mouseY <= height - DETAIL_CONTENT_BOTTOM_MARGIN) {
            detailScroll = scrollFromTrack(mouseY, DETAIL_CONTENT_TOP, height - DETAIL_CONTENT_BOTTOM_MARGIN,
                    detailContentHeight, detailViewportHeight());
            return true;
        }

        ChapterDefinition navigationChoice = navigationChoice(snapshot.book(), mouseX, mouseY);
        if (navigationChoice != null) {
            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            chapterIndex = chapters.indexOf(navigationChoice);
            panX = 0;
            panY = 0;
            detailsOpen = false;
            detailScroll = 0;
            return true;
        }

        QuestDefinition selected = selectedQuest();
        int left = detailLeft();
        if (detailsOpen && mouseX >= width - 28 && mouseX <= width && mouseY >= 0 && mouseY <= 28) {
            detailsOpen = false;
            detailScroll = 0;
            return true;
        }
        if (detailsOpen && selected != null) {
            QuestStatus status = status(selected);
            Component statusText = Component.translatable("screen.brnquest.status." + status.name().toLowerCase(java.util.Locale.ROOT));
            int statusY = detailStatusY(selected);
            String pin = status == QuestStatus.ACTIVE ? "★" : "☆";
            int trackX = detailTrackX(pin);
            if ((status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)
                    && mouseX >= trackX && mouseX <= trackX + 14 && mouseY >= statusY && mouseY <= statusY + font.lineHeight) {
                BrnQuestNetwork.toggleTracked(ClientQuestState.get().revision(), selected.id().toString());
                return true;
            }
            for (TaskHitbox hitbox : taskHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    BrnQuestNetwork.completeTask(ClientQuestState.get().revision(), hitbox.quest().id().toString(), hitbox.task().id().toString());
                    return true;
                }
            }
            for (RewardHitbox hitbox : rewardHitboxes) {
                if (hitbox.contains(mouseX, mouseY)) {
                    BrnQuestNetwork.claimReward(ClientQuestState.get().revision(), hitbox.reward().id().toString());
                    return true;
                }
            }
            if (mouseX >= left) return true;
        }

        int canvasRight = detailsOpen ? left - 6 : width - CANVAS_MARGIN;
        if (mouseX > canvasLeft() && mouseX < canvasRight) {
            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            ChapterDefinition chapter = chapters.get(chapterIndex);
            int radius = nodeSize() / 2 + 2;
            for (QuestDefinition quest : chapter.quests()) {
                if (Math.abs(mouseX - screenX(quest.x())) <= radius && Math.abs(mouseY - screenY(quest.y())) <= radius) {
                    ClientQuestState.get().selected(quest.id());
                    detailsOpen = true;
                    detailScroll = 0;
                    BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), quest.id().toString());
                    return true;
                }
            }
            dragging = true;
            dragX = mouseX;
            dragY = mouseY;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) {
            panX += x - dragX;
            panY += y - dragY;
            dragX = x;
            dragY = y;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!navigationCollapsed && x >= NAV_LEFT && x <= NAV_RIGHT + NAV_HANDLE_WIDTH + 4) {
            navigationScroll = QuestViewportMath.clampScroll(navigationScroll - vertical * 24,
                    navigationContentHeight, navigationViewportHeight());
            return true;
        }
        if (detailsOpen && x >= detailLeft()) {
            detailScroll = QuestViewportMath.clampScroll(detailScroll - vertical * 24,
                    detailContentHeight, detailViewportHeight());
            return true;
        }

        double oldZoom = zoom;
        double nextZoom = QuestViewportMath.clampZoom(zoom + vertical * 0.10);
        if (nextZoom == oldZoom) return true;
        // Preserve the task coordinate at the visible canvas center while zooming.
        int canvasRight = detailsOpen ? detailLeft() - 6 : width - CANVAS_MARGIN;
        double anchorX = (canvasLeft() + canvasRight) / 2.0;
        double anchorY = height / 2.0;
        panX = QuestViewportMath.panForStableAnchor(anchorX, screenOriginX(), panX, oldZoom, nextZoom);
        panY = QuestViewportMath.panForStableAnchor(anchorY, height / 2.0, panY, oldZoom, nextZoom);
        zoom = nextZoom;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && detailsOpen) {
            detailsOpen = false;
            detailScroll = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private ChapterDefinition navigationChoice(QuestBookDefinition book, double mouseX, double mouseY) {
        if (navigationCollapsed) return null;
        if (mouseX < NAV_LEFT || mouseX > NAV_RIGHT) return null;
        int y = NAV_TOP - (int) Math.round(navigationScroll);
        for (QuestPresentation.NavigationEntry entry : QuestPresentation.navigation(book)) {
            if (entry.group() != null) {
                y += 16;
            } else {
                if (mouseY >= y && mouseY <= y + 16) return entry.chapter();
                y += 18;
            }
        }
        return null;
    }

    private boolean canSubmit(QuestDefinition quest, QuestStatus status) {
        if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) return false;
        return quest.tasks().stream().filter(task -> !task.optional()).allMatch(task ->
                task.typeId().getPath().equals("checkmark") || taskSatisfied(task, status));
    }

    private boolean taskSatisfied(TaskDefinition task, QuestStatus status) {
        if (isCompleted(status)) return true;
        if (ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L) >= 1) return true;
        if (!task.typeId().getPath().equals("item")) return false;
        ItemStack expected = item(task.id(), task.config().getOrDefault("item", ""));
        if (expected.isEmpty() || minecraft.player == null) return false;
        int present = minecraft.player.getInventory().items.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                .mapToInt(ItemStack::getCount).sum();
        return present >= QuestPresentation.requiredCount(task);
    }

    private String taskProgressText(TaskDefinition task, ItemStack expected, boolean satisfied) {
        if (task.typeId().getPath().equals("checkmark")) {
            return Component.translatable(satisfied ? "screen.brnquest.task.checked" : "screen.brnquest.task.manual").getString();
        }
        if (task.typeId().getPath().equals("item") && !expected.isEmpty() && minecraft.player != null) {
            int present = minecraft.player.getInventory().items.stream()
                    .filter(stack -> ItemStack.isSameItemSameComponents(stack, expected))
                    .mapToInt(ItemStack::getCount).sum();
            return present + " / " + QuestPresentation.requiredCount(task);
        }
        long progress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        return progress + " / 1";
    }

    private QuestStatus status(QuestDefinition quest) {
        return ClientQuestState.get().statuses().getOrDefault(quest.id().toString(), QuestStatus.LOCKED);
    }

    /** Resolves cross-chapter prerequisites from the immutable book already synchronized to the client. */
    private List<Component> dependencyTooltip(QuestDefinition quest) {
        var snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null) return List.of(Component.translatable("screen.brnquest.dependencies.none"));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.brnquest.dependencies").withStyle(net.minecraft.ChatFormatting.AQUA));
        for (ResourceLocation dependencyId : quest.dependencies()) {
            QuestDefinition dependency = snapshot.quests().get(dependencyId);
            if (dependency == null) {
                lines.add(Component.literal("• " + dependencyId).withStyle(net.minecraft.ChatFormatting.RED));
                continue;
            }
            ChapterDefinition chapter = snapshot.book().chapters().stream()
                    .filter(candidate -> candidate.id().equals(dependency.chapterId())).findFirst().orElse(null);
            String chapterTitle = chapter == null ? dependency.chapterId().toString() : chapter.title();
            lines.add(Component.translatable("screen.brnquest.dependency.entry", chapterTitle, dependency.title()));
        }
        if (quest.dependencies().isEmpty()) lines.add(Component.translatable("screen.brnquest.dependencies.none"));
        return lines;
    }

    private boolean isCompleted(QuestStatus status) {
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED;
    }

    private int statusColor(QuestStatus status) {
        return switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF72D88D;
            case AVAILABLE, ACTIVE -> 0xFFE4B75A;
            default -> 0xFF9AA6B5;
        };
    }

    private QuestDefinition selectedQuest() {
        return ClientQuestState.get().book().map(snapshot -> snapshot.quests().get(ClientQuestState.get().selected())).orElse(null);
    }

    private String questTitle(QuestDefinition quest) {
        if (quest.tasks().isEmpty()) return quest.title();
        TaskDefinition task = quest.tasks().getFirst();
        boolean generated = quest.title().isBlank() || quest.title().equals(quest.legacyId())
                || quest.title().equals(quest.id().getPath()) || ResourceLocation.tryParse(quest.title()) != null;
        if (!generated) return quest.title();
        String custom = task.config().getOrDefault("title", "");
        if (!custom.isBlank()) return custom;
        ItemStack stack = task.typeId().getPath().equals("item") ? item(task.id(), task.config().getOrDefault("item", "")) : ItemStack.EMPTY;
        if (!stack.isEmpty()) return stack.getHoverName().getString();
        return task.typeId().getPath().equals("checkmark") ? Component.translatable("screen.brnquest.task.checkmark").getString() : quest.title();
    }

    private int detailStatusY(QuestDefinition quest) {
        int width = DETAIL_WIDTH - 38;
        int y = DETAIL_CONTENT_TOP - (int) Math.round(detailScroll);
        y += font.split(Component.literal(questTitle(quest)), width).size() * font.lineHeight + 3;
        if (!quest.subtitle().isBlank()) y += font.split(Component.literal(quest.subtitle()), width).size() * font.lineHeight + 4;
        return y;
    }

    private int detailTrackX(String pin) {
        return detailLeft() + 10 + (DETAIL_WIDTH - 24) - font.width(pin);
    }

    private ItemStack item(ResourceLocation cacheId, String snbt) {
        return itemCache.computeIfAbsent(cacheId, ignored -> {
            if (snbt.isBlank() || minecraft.level == null) return ItemStack.EMPTY;
            try {
                return ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(snbt));
            } catch (Exception exception) {
                return ItemStack.EMPTY;
            }
        });
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        Component component = Component.literal(text);
        graphics.drawWordWrap(font, component, x, y, width, color);
        return y + font.split(component, width).size() * font.lineHeight;
    }

    private void fillChamfer(GuiGraphics graphics, int x, int y, int size, int color) {
        int radius = size / 2;
        int cut = Math.max(1, size / 6);
        graphics.fill(x - radius + cut, y - radius, x + radius - cut + 1, y + radius + 1, color);
        graphics.fill(x - radius, y - radius + cut, x + radius + 1, y + radius - cut + 1, color);
    }

    private void horizontal(GuiGraphics graphics, int x1, int x2, int y, int thickness, int color) {
        graphics.fill(Math.min(x1, x2), y - thickness / 2, Math.max(x1, x2) + 1, y + (thickness + 1) / 2, color);
    }

    private void vertical(GuiGraphics graphics, int x, int y1, int y2, int thickness, int color) {
        graphics.fill(x - thickness / 2, Math.min(y1, y2), x + (thickness + 1) / 2, Math.max(y1, y2) + 1, color);
    }

    private void arrowHorizontal(GuiGraphics graphics, int x, int y, int direction, int thickness, int color) {
        int length = Math.max(3, 4 * thickness);
        for (int offset = 0; offset <= length; offset++) {
            // A single-pixel tip at the child expands toward the trailing base.
            int half = Math.max(0, offset / 2);
            int px = x - direction * offset;
            graphics.fill(px, y - half, px + 1, y + half + 1, color);
        }
    }

    private void arrowVertical(GuiGraphics graphics, int x, int y, int direction, int thickness, int color) {
        int length = Math.max(3, 4 * thickness);
        for (int offset = 0; offset <= length; offset++) {
            int half = Math.max(0, offset / 2);
            int py = y - direction * offset;
            graphics.fill(x - half, py, x + half + 1, py + 1, color);
        }
    }

    private int nodeSize() {
        return Math.max(6, Math.min(40, (int) Math.round(NODE_BASE_SIZE * zoom)));
    }

    private int screenX(double x) {
        return (int) (screenOriginX() + panX + x * 34 * zoom);
    }

    private int screenY(double y) {
        return (int) (height / 2.0 + panY + y * 34 * zoom);
    }

    private int detailLeft() {
        return width - DETAIL_WIDTH;
    }

    private int canvasLeft() {
        return navigationCollapsed ? NAV_HANDLE_WIDTH + 4 : NAV_RIGHT + NAV_HANDLE_WIDTH + 8;
    }

    private int screenOriginX() {
        return canvasLeft() + 96;
    }

    private int navigationContentHeight(QuestBookDefinition book) {
        return QuestPresentation.navigation(book).stream()
                .mapToInt(entry -> entry.group() != null ? 16 : 18).sum();
    }

    private int navigationViewportHeight() {
        return Math.max(1, height - NAV_TOP - NAV_BOTTOM_MARGIN);
    }

    private int detailViewportHeight() {
        return Math.max(1, height - DETAIL_CONTENT_TOP - DETAIL_CONTENT_BOTTOM_MARGIN);
    }

    private void renderScrollbar(GuiGraphics graphics, int x, int top, int bottom,
                                 int contentHeight, int viewportHeight, double scroll) {
        if (contentHeight <= viewportHeight) return;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(16, (int) Math.round(trackHeight * (viewportHeight / (double) contentHeight)));
        int travel = trackHeight - thumbHeight;
        int maxScroll = contentHeight - viewportHeight;
        int thumbTop = top + (int) Math.round(travel * (scroll / maxScroll));
        graphics.fill(x, top, x + 3, bottom, 0x66343D49);
        graphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, 0xFF7C8CA0);
    }

    private double scrollFromTrack(double mouseY, int top, int bottom, int contentHeight, int viewportHeight) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - top) / Math.max(1.0, bottom - top)));
        return ratio * Math.max(0, contentHeight - viewportHeight);
    }

    private record RewardHitbox(int left, int top, int right, int bottom, RewardDefinition reward) {
        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    private record TaskHitbox(int left, int top, int right, int bottom, QuestDefinition quest, TaskDefinition task) {
        boolean contains(double x, double y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }
}
