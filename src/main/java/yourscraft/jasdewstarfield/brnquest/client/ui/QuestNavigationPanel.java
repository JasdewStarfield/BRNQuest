package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Grouped navigation owns its ordered entries and viewport. It has no access to editor/network state. */
final class QuestNavigationPanel {
    static final int GROUP_HEIGHT = 13;
    static final int CHAPTER_HEIGHT = 15;
    record Layout(int width, int top, int bottom, int listBottom, int visibleRight, int offset,
                  int handleWidth, int centerY, boolean collapsed) {}
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private QuestBookDefinition book;
    private List<QuestPresentation.NavigationEntry> entries = List.of();
    private Layout drawnLayout;
    private double drawnScroll;
    private int contentHeight;

    EditorSmoothScroll scroll() { return scroll; }
    int contentHeight(QuestBookDefinition next) {
        if (book != next) {
            book = next;
            entries = QuestPresentation.navigation(next);
            contentHeight = entries.stream().mapToInt(e -> e.group() != null ? GROUP_HEIGHT : CHAPTER_HEIGHT).sum();
            drawnLayout = null;
        }
        return contentHeight;
    }
    void invalidate() { drawnLayout = null; }

    void render(GuiGraphics graphics, Font font, QuestBookDefinition book, ChapterDefinition selected,
                Layout layout, double seconds, double speed, Runnable footer) {
        contentHeight(book);
        drawnLayout = layout;
        if (layout.visibleRight() > 0) {
            graphics.enableScissor(0, layout.top(), layout.visibleRight(), layout.bottom());
            graphics.pose().pushPose();
            graphics.pose().translate(layout.offset(), 0, 0);
            try {
                graphics.fill(0, layout.top(), layout.width() + 4, layout.bottom(), 0xB8181E27);
                drawnScroll = scroll.frameAndRender(graphics, layout.width() + 2, layout.top(), layout.listBottom(),
                        contentHeight, Math.max(1, layout.listBottom() - layout.top()), seconds, speed);
                graphics.enableScissor(Math.max(0, layout.offset()), layout.top(),
                        Math.min(layout.visibleRight(), layout.width() + 6 + layout.offset()), layout.listBottom());
                int y = layout.top() - (int) Math.round(drawnScroll);
                for (var entry : entries) {
                    if (entry.group() != null) {
                        graphics.fill(0, y, layout.width(), y + GROUP_HEIGHT, 0xE01B222C);
                        EditorTextRenderer.drawFittedString(graphics, font, Component.literal("▾ " + entry.group().title()),
                                4, y + 2, layout.width() - 8, 0xFFB7C5D8, 0.75F);
                        y += GROUP_HEIGHT;
                    } else {
                        ChapterDefinition chapter = entry.chapter();
                        int color = selected != null && chapter.id().equals(selected.id()) ? 0xFF4A6A88 : 0xE0262D38;
                        graphics.fill(4, y, layout.width(), y + CHAPTER_HEIGHT, color);
                        EditorTextRenderer.drawFittedString(graphics, font, Component.literal(chapter.title()),
                                9, y + 3, layout.width() - 13, 0xFFFFFFFF, 0.75F);
                        y += CHAPTER_HEIGHT;
                    }
                }
                graphics.disableScissor();
                footer.run();
            } finally {
                graphics.pose().popPose();
                graphics.disableScissor();
            }
        }
        graphics.fill(layout.visibleRight(), layout.top(), layout.visibleRight() + layout.handleWidth(),
                layout.bottom(), 0xD01B222C);
        graphics.drawCenteredString(font, layout.collapsed() ? "›" : "‹",
                layout.visibleRight() + layout.handleWidth() / 2, layout.centerY() - 4, 0xFFB7C5D8);
    }

    /** Ignore obsolete frames and the clipped header/footer; selection uses the same ordered data as draw. */
    QuestPresentation.NavigationEntry entryAt(QuestBookDefinition currentBook, double x, double y) {
        if (book != currentBook || drawnLayout == null || drawnLayout.offset() != 0 || drawnLayout.collapsed()
                || x < 0 || x >= drawnLayout.width() || y < drawnLayout.top() || y >= drawnLayout.listBottom()) return null;
        int rowY = drawnLayout.top() - (int) Math.round(drawnScroll);
        for (var entry : entries) {
            int height = entry.group() != null ? GROUP_HEIGHT : CHAPTER_HEIGHT;
            if (y >= rowY && y < rowY + height) return entry;
            rowY += height;
        }
        return null;
    }
}
