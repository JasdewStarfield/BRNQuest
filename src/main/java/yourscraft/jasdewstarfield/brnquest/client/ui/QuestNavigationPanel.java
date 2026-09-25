package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.client.Minecraft;
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
    private static final int DISCLOSURE_SIZE = 10;
    private static final ResourceLocation FOLD_ICON = ResourceLocation.parse("brnquest:editor/action/fold");
    private static final ResourceLocation UNFOLD_ICON = ResourceLocation.parse("brnquest:editor/action/unfold");

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
    private final java.util.Set<ResourceLocation> folded = new java.util.HashSet<>();
    private ResourceLocation foldedBookId;
    private ResourceLocation pressedChapter;
    private QuestScreenFrameIdentity pressIdentity;
    private double pressX, pressY;
    private boolean dragging;
    record Drop(ResourceLocation chapter, ResourceLocation group, int index, int lineY) {}
    private double drawnScroll;
    private int contentHeight;

    java.util.Set<ResourceLocation> foldedGroups() { return java.util.Set.copyOf(folded); }

    /** Restore before the first render of a book; do not let refresh erase the restored preferences. */
    void restoreFoldedGroups(ResourceLocation bookId, java.util.Set<ResourceLocation> groups) {
        foldedBookId = bookId;
        folded.clear(); folded.addAll(groups);
        if (book != null && book.id().equals(bookId)) rebuildEntries();
        invalidate();
    }

    void invalidate() { frame = null; cancelDrag(); }
    void resetScroll() { scroll.snap(0); }

    RenderResult render(GuiGraphics graphics, Font font, Model model, Layout layout,
                        double seconds, double speed, int mouseX, int mouseY, String locale,
                        java.util.function.BiConsumer<ChapterDefinition, UiRect> drawIcon,
                        java.util.function.BiConsumer<yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition, UiRect> drawGroupIcon) {
        advance(model, layout);
        List<Component> tooltip = List.of();
        if (layout.visibleRight() > 0) {
            graphics.enableScissor(0, layout.top(), layout.visibleRight(), layout.bottom());
            graphics.pose().pushPose();
            graphics.pose().translate(layout.offset(), 0, 0);
            try {
                graphics.fill(0, layout.top(), layout.width() + 4, layout.bottom(), GraystonePalette.NAVIGATION);
                if (dragging && mouseX >= 0 && mouseX < layout.visibleRight()) {
                    if (mouseY < layout.top() + 16) mouseScrolled(1, 180 * seconds);
                    else if (mouseY > layout.listBottom() - 16) mouseScrolled(-1, 180 * seconds);
                }
                drawnScroll = scroll.frameAndRender(graphics, layout.width() + 2, layout.top(), layout.listBottom(),
                        contentHeight, Math.max(1, layout.listBottom() - layout.top()), seconds, speed);
                graphics.enableScissor(Math.max(0, layout.offset()), layout.top(),
                        Math.min(layout.visibleRight(), layout.width() + 6 + layout.offset()), layout.listBottom());
                int y = layout.top() - (int) Math.round(drawnScroll);
                for (var entry : entries) {
                    if (entry.group() != null) {
                        graphics.fill(0, y, layout.width(), y + GROUP_HEIGHT, 0xFF252821);
                        boolean hasIcon = !entry.group().icon().isBlank();
                        if (hasIcon) drawGroupIcon.accept(entry.group(), new UiRect(4, y + 1, 15, y + 12));
                        int titleX = hasIcon ? 18 : 4;
                        // The transparent PNG replaces only the inline glyph; the entire heading stays clickable.
                        drawDisclosure(graphics, folded.contains(entry.group().id()) ? UNFOLD_ICON : FOLD_ICON,
                                titleX - 2, y + 1, false);
                        EditorTextRenderer.drawFittedString(graphics, font,
                                Component.literal(BookText.structureTitle(
                                        model.book(), "chapter_group", entry.group().id(), locale, entry.group().title())),
                                titleX + 9, y + 2, layout.width() - titleX - 13, GraystonePalette.SECONDARY, 0.75F);
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
                            graphics.fill(4, y + 1, 6, y + CHAPTER_HEIGHT - 1, GraystonePalette.ACCENT);
                        drawIcon.accept(chapter, new UiRect(9, y + 2, 25, y + 18));
                        EditorTextRenderer.drawFittedString(graphics, font, Component.literal(
                                BookText.structureTitle(
                                        model.book(), "chapter", chapter.id(), locale, chapter.title())),
                                30, y + 6, Math.max(1, layout.width() - 34), 0xFFFFFFFF, 0.75F);
                        y += CHAPTER_HEIGHT;
                    }
                }
                if (dragging) {
                    Drop drop = dropAt(mouseX, mouseY);
                    if (drop != null) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(0, 0, 350);
                        // The insertion line and translucent row use the exact destination returned on release.
                        graphics.fill(4, drop.lineY() - 1, layout.width(), drop.lineY() + 1, GraystonePalette.ACCENT);
                        int ghostY = Math.max(layout.top(), Math.min(mouseY + 8, layout.listBottom() - CHAPTER_HEIGHT));
                        graphics.fill(8, ghostY, layout.width() - 4, ghostY + CHAPTER_HEIGHT, 0xB062604A);
                        var source = book.chapters().stream().filter(c -> c.id().equals(pressedChapter)).findFirst().orElse(null);
                        if (source != null) EditorTextRenderer.drawFittedString(graphics, font,
                                Component.literal(BookText.structureTitle(book, "chapter", source.id(), locale, source.title())),
                                12, ghostY + 6, layout.width() - 20, 0xFFD8CEAF, 0.75F);
                        graphics.pose().popPose();
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
        // Reuse the compact disclosure triangle in the plain handle; turn it left when the drawer is open.
        drawDisclosure(graphics, UNFOLD_ICON,
                layout.visibleRight() + (layout.handleWidth() - DISCLOSURE_SIZE) / 2,
                layout.centerY() - DISCLOSURE_SIZE / 2, !layout.collapsed());
        // Resolve hover from the same clipped, translated list geometry used for chapter selection.
        if (layout.offset() == 0 && !layout.collapsed() && mouseX >= 0 && mouseX < layout.visibleRight()
                && mouseY >= layout.top() && mouseY < layout.listBottom()) {
            var hovered = entryAt(mouseX, mouseY);
            if (hovered != null) {
                String title = hovered.group() != null
                        ? BookText.structureTitle(model.book(), "chapter_group", hovered.group().id(), locale, hovered.group().title())
                        : BookText.structureTitle(model.book(), "chapter", hovered.chapter().id(), locale, hovered.chapter().title());
                tooltip = new java.util.ArrayList<>();
                tooltip.add(Component.literal(title));
                if (hovered.group() != null && !hovered.group().description().isBlank()) {
                    // Separate tooltip entries preserve authored newlines instead of embedding them in one line.
                    for (String line : hovered.group().description().split("\\R")) tooltip.add(Component.literal(line));
                }
            }
        }
        return new RenderResult(dragging ? List.of() : tooltip);
    }

    private static void drawDisclosure(GuiGraphics graphics, ResourceLocation icon, int x, int y, boolean faceLeft) {
        var sprite = Minecraft.getInstance().getGuiSprites().getSprite(icon);
        graphics.pose().pushPose();
        try {
            if (faceLeft) {
                // A half-turn mirrors this vertically symmetric triangle without reversing quad winding.
                graphics.pose().translate(2 * x + DISCLOSURE_SIZE, 2 * y + DISCLOSURE_SIZE, 0);
                graphics.pose().scale(-1, -1, 1);
            }
            graphics.blit(x, y, 0, DISCLOSURE_SIZE, DISCLOSURE_SIZE, sprite,
                    (GraystonePalette.SECONDARY >> 16 & 255) / 255F,
                    (GraystonePalette.SECONDARY >> 8 & 255) / 255F,
                    (GraystonePalette.SECONDARY & 255) / 255F, 1F);
        } finally {
            graphics.pose().popPose();
        }
    }

    /** Builds the immutable input frame independently so geometry tests do not need a rendering runtime. */
    Layout advance(Model model, Layout layout) {
        if (pressIdentity != null && !pressIdentity.equals(model.identity())) cancelDrag();
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
        if (button == 0 && scroll.handleTrackClick(x, y, layout.width() + 2, layout.top(), layout.listBottom(),
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
        if (button == 0 && entry != null && entry.group() != null) {
            if (!folded.add(entry.group().id())) folded.remove(entry.group().id());
            rebuildEntries();
            frame = null;
            return ClickResult.consumed(null);
        }
        if (entry != null && entry.chapter() != null) {
            if (button == 0 && frame.editing()) {
                pressedChapter = entry.chapter().id(); pressIdentity = identity;
                pressX = x; pressY = y; dragging = false;
            }
            return ClickResult.consumed(new Intent(Action.SELECT_CHAPTER, entry.chapter().id(), (int) x, (int) y));
        }
        return ClickResult.consumed(null);
    }

    boolean drag(QuestScreenFrameIdentity identity, double x, double y, int button) {
        if (pressedChapter == null || button != 0) return false;
        if (!accepts(identity) || !identity.equals(pressIdentity)) { cancelDrag(); return true; }
        if (Math.hypot(x - pressX, y - pressY) >= 4) dragging = true;
        return true;
    }

    boolean dragScroll(double y, int button) { return scroll.handleDrag(y, button); }
    boolean releaseScroll(int button) { return scroll.handleRelease(button); }

    boolean hasDrag() { return pressedChapter != null; }
    boolean cancelDrag() {
        boolean active = pressedChapter != null;
        pressedChapter = null; pressIdentity = null; dragging = false;
        return active;
    }

    Drop release(QuestScreenFrameIdentity identity, double x, double y) {
        Drop result = dragging && accepts(identity) && identity.equals(pressIdentity) ? dropAt(x, y) : null;
        cancelDrag();
        return result;
    }

    /** Indices exclude the moving chapter; group headings expose start/end even when folded or empty. */
    private Drop dropAt(double x, double y) {
        if (frame == null || pressedChapter == null) return null;
        var layout = frame.layout();
        if (x < 0 || x >= layout.width() || y < layout.top() || y >= layout.listBottom()) return null;
        int rowY = layout.top() - (int) Math.round(drawnScroll);
        Drop last = null;
        for (var entry : entries) {
            if (entry.group() != null) {
                var group = entry.group().id();
                int count = (int) book.chapters().stream().filter(c -> c.groupId().equals(group) && !c.id().equals(pressedChapter)).count();
                if (y < rowY + GROUP_HEIGHT) {
                    boolean start = y < rowY + GROUP_HEIGHT / 2.0;
                    int visibleCount = (int) entries.stream().filter(e -> e.chapter() != null && e.chapter().groupId().equals(group)).count();
                    return new Drop(pressedChapter, group, start ? 0 : count,
                            rowY + GROUP_HEIGHT + (start ? 0 : visibleCount * CHAPTER_HEIGHT));
                }
                last = new Drop(pressedChapter, group, count, rowY + GROUP_HEIGHT);
                rowY += GROUP_HEIGHT;
            } else {
                var chapter = entry.chapter();
                var siblings = QuestPresentation.orderedChapters(book).stream()
                        .filter(c -> c.groupId().equals(chapter.groupId()) && !c.id().equals(pressedChapter)).toList();
                int index = 0;
                while (index < siblings.size() && !siblings.get(index).id().equals(chapter.id())) index++;
                if (y < rowY + CHAPTER_HEIGHT && chapter.id().equals(pressedChapter)) return null;
                if (y < rowY + CHAPTER_HEIGHT) {
                    boolean after = y >= rowY + CHAPTER_HEIGHT / 2.0;
                    return new Drop(pressedChapter, chapter.groupId(), index + (after ? 1 : 0), rowY + (after ? CHAPTER_HEIGHT : 0));
                }
                rowY += CHAPTER_HEIGHT;
                last = new Drop(pressedChapter, chapter.groupId(), siblings.size(), rowY);
            }
        }
        return last;
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
        if (!next.id().equals(foldedBookId)) { folded.clear(); foldedBookId = next.id(); }
        book = next;
        rebuildEntries();
        frame = null;
    }

    private void rebuildEntries() {
        entries = QuestPresentation.navigation(book).stream()
                .filter(entry -> entry.group() != null || !folded.contains(entry.chapter().groupId())).toList();
        contentHeight = entries.stream().mapToInt(entry -> entry.group() != null
                ? GROUP_HEIGHT : CHAPTER_HEIGHT).sum();
        frame = null;
    }

    private static List<Component> renderFooter(GuiGraphics graphics, Font font, Layout layout,
                                                 boolean canAddChapter, int mouseX, int mouseY) {
        Component addGroup = Component.translatable("screen.brnquest.editor.group.add");
        Component addChapter = Component.translatable("screen.brnquest.editor.chapter.add");
        boolean groupHovered = EditorButton.renderInteractive(graphics, font, layout.groupButton(),
                EditorButton.Definition.iconAndText(addGroup, addGroup, QuestActionIcons.named("plus")),
                true, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        boolean chapterHovered = EditorButton.renderInteractive(graphics, font, layout.chapterButton(),
                EditorButton.Definition.iconAndText(addChapter, addChapter, QuestActionIcons.named("plus")),
                canAddChapter, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        if (chapterHovered) return List.of(addChapter);
        return groupHovered ? List.of(addGroup) : List.of();
    }

    private boolean accepts(QuestScreenFrameIdentity identity) {
        return frame != null && frame.identity().equals(identity);
    }
}
