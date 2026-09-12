package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextRenderer;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;

import java.util.List;

/** Grouped navigation owns ordering, scrolling, rendered hit geometry and protocol-free intents. */
final class QuestNavigationPanel {
    static final int GROUP_HEIGHT = 13;
    static final int CHAPTER_HEIGHT = 20;

    enum Action { TOGGLE_DRAWER, ADD_GROUP, ADD_CHAPTER, OPEN_GROUP_CONTEXT, OPEN_CHAPTER_CONTEXT, SELECT_CHAPTER }
    record Intent(Action action, ResourceLocation targetId, int pointerX, int pointerY) {}
    record ClickResult(boolean consumed, Intent intent) {
        static ClickResult ignored() { return new ClickResult(false, null); }
        static ClickResult consumed(Intent intent) { return new ClickResult(true, intent); }
    }
    record Layout(int width, int top, int bottom, int listBottom, int visibleRight, int offset,
                  int handleWidth, int centerY, boolean collapsed) {
        UiRect groupButton() {
            int buttonTop = bottom - 18;
            return new UiRect(2, buttonTop, width / 2, buttonTop + 16);
        }
        UiRect chapterButton() {
            int buttonTop = bottom - 18;
            return new UiRect(width / 2 + 2, buttonTop, width - 2, buttonTop + 16);
        }
    }
    record Model(QuestScreenFrameIdentity identity, QuestBookDefinition book,
                 ChapterDefinition selected, boolean editing, boolean canAddChapter) {}
    record RenderResult(List<Component> tooltip) {
        RenderResult { tooltip = tooltip == null ? List.of() : List.copyOf(tooltip); }
    }
    private record Frame(QuestScreenFrameIdentity identity, Layout layout,
                         boolean editing, boolean canAddChapter,
                         List<QuestPresentation.NavigationEntry> entries) {}

    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private QuestBookDefinition book;
    private List<QuestPresentation.NavigationEntry> entries = List.of();
    private Frame frame;
    private double drawnScroll;
    private int contentHeight;

    void invalidate() { frame = null; }
    void resetScroll() { scroll.snap(0); }

    RenderResult render(GuiGraphics graphics, Font font, Model model, Layout layout,
                        double seconds, double speed, int mouseX, int mouseY, String locale,
                        java.util.function.BiConsumer<ChapterDefinition, UiRect> drawIcon) {
        advance(model, layout);
        List<Component> tooltip = List.of();
        if (layout.visibleRight() > 0) {
            graphics.enableScissor(0, layout.top(), layout.visibleRight(), layout.bottom());
            graphics.pose().pushPose();
            graphics.pose().translate(layout.offset(), 0, 0);
            try {
                graphics.fill(0, layout.top(), layout.width() + 4, layout.bottom(), 0xF02C2F29);
                drawnScroll = scroll.frameAndRender(graphics, layout.width() + 2, layout.top(), layout.listBottom(),
                        contentHeight, Math.max(1, layout.listBottom() - layout.top()), seconds, speed);
                graphics.enableScissor(Math.max(0, layout.offset()), layout.top(),
                        Math.min(layout.visibleRight(), layout.width() + 6 + layout.offset()), layout.listBottom());
                int y = layout.top() - (int) Math.round(drawnScroll);
                for (var entry : entries) {
                    if (entry.group() != null) {
                        graphics.fill(0, y, layout.width(), y + GROUP_HEIGHT, 0xFF252821);
                        EditorTextRenderer.drawFittedString(graphics, font,
                                Component.literal("▾ " + BookText.structureTitle(
                                        model.book(), "chapter_group", entry.group().id(), locale, entry.group().title())),
                                4, y + 2, layout.width() - 8, 0xFFB7C5D8, 0.75F);
                        y += GROUP_HEIGHT;
                    } else {
                        ChapterDefinition chapter = entry.chapter();
                        if (y + CHAPTER_HEIGHT <= layout.top() || y >= layout.listBottom()) {
                            y += CHAPTER_HEIGHT; continue;
                        }
                        int color = model.selected() != null && chapter.id().equals(model.selected().id())
                                ? 0xFF62604A : 0xFF363A32;
                        graphics.fill(4, y, layout.width(), y + CHAPTER_HEIGHT, color);
                        // A narrow brass marker keeps selection recognizable beyond a background color change.
                        if (model.selected() != null && chapter.id().equals(model.selected().id()))
                            graphics.fill(4, y + 1, 6, y + CHAPTER_HEIGHT - 1, 0xFFE4D29A);
                        drawIcon.accept(chapter, new UiRect(9, y + 2, 25, y + 18));
                        EditorTextRenderer.drawFittedString(graphics, font, Component.literal(
                                BookText.structureTitle(
                                        model.book(), "chapter", chapter.id(), locale, chapter.title())),
                                30, y + 6, Math.max(1, layout.width() - 34), 0xFFFFFFFF, 0.75F);
                        y += CHAPTER_HEIGHT;
                    }
                }
                graphics.disableScissor();
                if (model.editing()) tooltip = renderFooter(graphics, font, layout,
                        model.canAddChapter(), mouseX, mouseY);
            } finally {
                graphics.pose().popPose();
                graphics.disableScissor();
            }
        }
        graphics.fill(layout.visibleRight(), layout.top(), layout.visibleRight() + layout.handleWidth(),
                layout.bottom(), 0xFF292C25);
        graphics.drawCenteredString(font, layout.collapsed() ? "›" : "‹",
                layout.visibleRight() + layout.handleWidth() / 2, layout.centerY() - 4, 0xFFB7C5D8);
        // Resolve hover from the same clipped, translated list geometry used for chapter selection.
        if (layout.offset() == 0 && !layout.collapsed() && mouseX >= 0 && mouseX < layout.visibleRight()
                && mouseY >= layout.top() && mouseY < layout.listBottom()) {
            var hovered = entryAt(mouseX, mouseY);
            if (hovered != null) {
                String title = hovered.group() != null
                        ? BookText.structureTitle(model.book(), "chapter_group", hovered.group().id(), locale, hovered.group().title())
                        : BookText.structureTitle(model.book(), "chapter", hovered.chapter().id(), locale, hovered.chapter().title());
                tooltip = List.of(Component.literal(title));
            }
        }
        return new RenderResult(tooltip);
    }

    /** Builds the immutable input frame independently so geometry tests do not need a rendering runtime. */
    Layout advance(Model model, Layout layout) {
        refresh(model.book());
        frame = new Frame(model.identity(), layout, model.editing(), model.canAddChapter(), entries);
        return layout;
    }

    ClickResult click(QuestScreenFrameIdentity identity, double x, double y, int button) {
        if (!accepts(identity)) return ClickResult.ignored();
        Layout layout = frame.layout();
        if (x >= layout.visibleRight() && x <= layout.visibleRight() + layout.handleWidth()
                && y >= layout.top() && y <= layout.bottom()) {
            return ClickResult.consumed(new Intent(Action.TOGGLE_DRAWER, null, (int) x, (int) y));
        }
        boolean stableOpen = layout.offset() == 0 && !layout.collapsed();
        if (!stableOpen || x < 0 || x >= layout.visibleRight() || y < layout.top() || y >= layout.bottom()) {
            return ClickResult.ignored();
        }
        if (scroll.handleTrackClick(x, y, layout.width() + 2, layout.top(), layout.listBottom(),
                contentHeight, Math.max(1, layout.listBottom() - layout.top()))) {
            return ClickResult.consumed(null);
        }
        if (frame.editing() && layout.groupButton().contains(x, y)) {
            return ClickResult.consumed(new Intent(Action.ADD_GROUP, null, (int) x, (int) y));
        }
        if (frame.editing() && layout.chapterButton().contains(x, y)) {
            return ClickResult.consumed(frame.canAddChapter()
                    ? new Intent(Action.ADD_CHAPTER, null, (int) x, (int) y) : null);
        }
        QuestPresentation.NavigationEntry entry = entryAt(x, y);
        if (frame.editing() && button == 1 && entry != null) {
            return ClickResult.consumed(new Intent(entry.group() != null
                    ? Action.OPEN_GROUP_CONTEXT : Action.OPEN_CHAPTER_CONTEXT,
                    entry.group() != null ? entry.group().id() : entry.chapter().id(), (int) x, (int) y));
        }
        if (entry != null && entry.chapter() != null) {
            return ClickResult.consumed(new Intent(Action.SELECT_CHAPTER, entry.chapter().id(), (int) x, (int) y));
        }
        return ClickResult.consumed(null);
    }

    void mouseScrolled(double amount, double step) {
        if (frame == null) return;
        Layout layout = frame.layout();
        scroll.scrollWheel(amount, step, contentHeight, Math.max(1, layout.listBottom() - layout.top()));
    }

    private QuestPresentation.NavigationEntry entryAt(double x, double y) {
        Layout layout = frame.layout();
        if (y >= layout.listBottom()) return null;
        int rowY = layout.top() - (int) Math.round(drawnScroll);
        for (var entry : frame.entries()) {
            int height = entry.group() != null ? GROUP_HEIGHT : CHAPTER_HEIGHT;
            if (y >= rowY && y < rowY + height) return entry;
            rowY += height;
        }
        return null;
    }

    private void refresh(QuestBookDefinition next) {
        if (book == next) return;
        book = next;
        entries = QuestPresentation.navigation(next);
        contentHeight = entries.stream().mapToInt(entry -> entry.group() != null
                ? GROUP_HEIGHT : CHAPTER_HEIGHT).sum();
        frame = null;
    }

    private static List<Component> renderFooter(GuiGraphics graphics, Font font, Layout layout,
                                                 boolean canAddChapter, int mouseX, int mouseY) {
        Component addGroup = Component.translatable("screen.brnquest.editor.group.add");
        Component addChapter = Component.translatable("screen.brnquest.editor.chapter.add");
        boolean groupHovered = EditorButton.renderInteractive(graphics, font, layout.groupButton(),
                EditorButton.Definition.iconAndText(addGroup, addGroup, EditorIcon.glyph(Component.literal("+"))),
                true, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        boolean chapterHovered = EditorButton.renderInteractive(graphics, font, layout.chapterButton(),
                EditorButton.Definition.iconAndText(addChapter, addChapter, EditorIcon.glyph(Component.literal("+"))),
                canAddChapter, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        if (chapterHovered) return List.of(addChapter);
        return groupHovered ? List.of(addGroup) : List.of();
    }

    private boolean accepts(QuestScreenFrameIdentity identity) {
        return frame != null && frame.identity().equals(identity);
    }
}
