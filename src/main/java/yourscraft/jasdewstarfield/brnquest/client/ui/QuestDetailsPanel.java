package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Detail section composition. The caller supplies row renderers; this panel owns text flow and scrolling. */
final class QuestDetailsPanel {
    record Layout(UiRect content, UiRect clip, int trackX) {}
    record Model(QuestDefinition quest, String title, String subtitle, String description,
                 QuestStatus status, Component statusText,
                 int statusColor, boolean editing, boolean gameplay, boolean ready) {}
    interface Rows {
        int task(TaskDefinition task, int x, int y, int width);
        void reward(RewardDefinition reward, int x, int y);
    }
    record Result(Map<String, UiRect> textAreas, UiRect completeAction, UiRect trackAction,
                  Component hint, boolean lockedStatusHovered) {}
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private int contentHeight;
    private double drawnScroll;
    EditorSmoothScroll scroll() { return scroll; }
    int contentHeight() { return contentHeight; }
    void reset() { scroll.snap(0); contentHeight = 0; }

    Result render(GuiGraphics graphics, Font font, Layout layout, Model model, Rows rows,
                  int mouseX, int mouseY, double seconds, double speed) {
        QuestDefinition quest = model.quest();
        QuestStatus status = model.status();
        boolean editing = model.editing();
        Map<String, UiRect> textAreas = new LinkedHashMap<>();
        Component hint = null;
        int contentLeft = layout.content().left();
        int contentWidth = layout.content().width();
        int viewportHeight = Math.max(1, layout.content().height());
        drawnScroll = scroll.frameAndRender(graphics, layout.trackX(), layout.content().top(),
                layout.content().bottom(), contentHeight, viewportHeight,
                seconds, speed);
        int y = layout.content().top() - (int) Math.round(drawnScroll);
        UiRect clip = layout.clip();
        graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        int titleTop = y;
        y = EditorTextRenderer.drawWrapped(graphics, font, model.title(), contentLeft, y, contentWidth - 14, 0xFFFFFF);
        if (editing) addTextArea(textAreas, layout.content(), "TITLE", contentLeft, titleTop, contentWidth - 14, y);
        y += 3;

        if (editing || !model.subtitle().isBlank()) {
            int subtitleTop = y;
            String subtitle = model.subtitle().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_subtitle").getString()
                    : model.subtitle();
            y = EditorTextRenderer.drawWrapped(graphics, font, subtitle, contentLeft, y, contentWidth,
                    model.subtitle().isBlank() ? 0xFF718096 : 0xFFB7C5D8);
            if (editing) addTextArea(textAreas, layout.content(), "SUBTITLE", contentLeft, subtitleTop, contentWidth, y);
            y += 4;
        }

        boolean ready = model.ready();
        Component statusText = model.statusText();
        graphics.drawString(font, statusText, contentLeft, y, model.statusColor(), false);
        int statusTop = y;
        int statusWidth = font.width(statusText);
        if (model.gameplay() && status == QuestStatus.LOCKED && !quest.behavior().hideLockIcon()) {
            // Cyan underline and info glyph advertise that the locked reason is inspectable.
            graphics.fill(contentLeft, y + font.lineHeight, contentLeft + statusWidth, y + font.lineHeight + 1, 0xFF68BDE8);
            graphics.drawString(font, Component.literal("ⓘ"), contentLeft + statusWidth + 4, y, 0xFF68BDE8, false);
            statusWidth += 4 + font.width("ⓘ");
        }
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
            String pin = status == QuestStatus.ACTIVE ? "★" : "☆";
            int pinX = contentLeft + contentWidth - font.width(pin) - 18;
            graphics.drawString(font, Component.literal(pin), pinX, y, status == QuestStatus.ACTIVE ? 0xFF57C7F2 : 0xFFB7C5D8, false);
            if (mouseX >= pinX - 2 && mouseX <= pinX + font.width(pin) + 2 && mouseY >= y && mouseY <= y + font.lineHeight) {
                hint = Component.translatable(status == QuestStatus.ACTIVE
                        ? "screen.brnquest.untrack" : "screen.brnquest.track");
            }
        }
        UiRect trackAction = null;
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
            String pin = status == QuestStatus.ACTIVE ? "★" : "☆";
            int pinX = contentLeft + contentWidth - font.width(pin) - 18;
            trackAction = visiblePart(new UiRect(pinX - 2, y, pinX + font.width(pin) + 2,
                    y + font.lineHeight), layout.content());
        }
        UiRect completeAction = null;
        if (model.gameplay() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && ready) {
            Component readyText = Component.translatable("screen.brnquest.ready");
            int readyX = contentLeft + font.width(statusText) + 6;
            graphics.drawString(font, readyText, readyX, y, 0xFF72D88D, false);
            completeAction = visiblePart(new UiRect(readyX, y, readyX + font.width(readyText),
                    y + font.lineHeight), layout.content());
        }
        y += 16;

        if (editing || !model.description().isBlank()) {
            int descriptionTop = y;
            String description = model.description().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_description").getString()
                    : model.description();
            y = EditorTextRenderer.drawWrapped(graphics, font, description, contentLeft, y, contentWidth,
                    model.description().isBlank() ? 0xFF718096 : 0xFFE1E6EE);
            if (editing) addTextArea(textAreas, layout.content(), "DESCRIPTION",
                    contentLeft, descriptionTop, contentWidth, y);
            y += 8;
        }

        if (editing) {
            for (Map.Entry<String, UiRect> entry : textAreas.entrySet()) {
                if (entry.getValue().containsExclusive(mouseX, mouseY)) {
                    hint = Component.translatable(entry.getKey().equals("DESCRIPTION")
                            ? "screen.brnquest.editor.quick_edit.open_editor_hint"
                            : "screen.brnquest.editor.quick_edit.hint");
                    break;
                }
            }
        }

        graphics.drawString(font, Component.translatable("screen.brnquest.requirements"), contentLeft, y, 0xFF8FB8E2, false);
        y += 13;
        if (quest.tasks().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.brnquest.no_requirements"), contentLeft, y, 0xFF9AA6B5, false);
            y += 18;
        } else {
            for (TaskDefinition task : quest.tasks()) y = rows.task(task, contentLeft, y, contentWidth);
        }

        List<RewardDefinition> visibleRewards = quest.rewards().stream()
                .filter(reward -> reward.policy().visible() || editing).toList();
        if (!visibleRewards.isEmpty()) {
            y += 4;
            graphics.drawString(font, Component.translatable("screen.brnquest.rewards"), contentLeft, y, 0xFFE6B55B, false);
            y += 13;
            int rewardTop = y;
            int rewardColumns = Math.max(1, contentWidth / QuestViewportMath.REWARD_ROW_HEIGHT);
            int column = 0;
            for (RewardDefinition reward : visibleRewards) {
                int rewardX = contentLeft + column * QuestViewportMath.REWARD_ROW_HEIGHT;
                rows.reward(reward, rewardX, y);
                column++;
                if (column >= rewardColumns) {
                    column = 0;
                    y += QuestViewportMath.REWARD_ROW_HEIGHT;
                }
            }
            // Compute from row count so a partially populated final row is never clipped.
            y = rewardTop + QuestViewportMath.rewardGridHeight(visibleRewards.size(), rewardColumns);
        }
        contentHeight = Math.max(0, y + (int) Math.round(drawnScroll) - layout.content().top() + 8);
        scroll.constrain(contentHeight, viewportHeight);
        graphics.disableScissor();


        boolean locked = model.gameplay() && status == QuestStatus.LOCKED && !quest.behavior().hideLockIcon()
                && mouseX >= contentLeft && mouseX <= contentLeft + statusWidth
                && mouseY >= statusTop && mouseY <= statusTop + font.lineHeight
                && statusTop >= layout.content().top() && statusTop < layout.content().bottom();
        return new Result(Map.copyOf(textAreas), completeAction, trackAction, hint, locked);
    }

    private static void addTextArea(Map<String, UiRect> areas, UiRect viewport, String key,
                                    int left, int top, int width, int bottom) {
        UiRect visible = new UiRect(left, top, left + width, bottom).intersection(viewport);
        if (visible.height() > 0 && visible.width() > 0) areas.put(key, visible);
    }

    private static UiRect visiblePart(UiRect bounds, UiRect viewport) {
        UiRect visible = bounds.intersection(viewport);
        return visible.width() > 0 && visible.height() > 0 ? visible : null;
    }
}
