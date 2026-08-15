package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPopupMenu;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorScrollbar;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Quest-book UI with grouped navigation, a scalable directed graph, and intent-only details. */
public final class QuestScreen extends Screen {
    private static final int NAV_LEFT = 0;
    private static final int NAV_RIGHT = QuestScreenLayout.NAVIGATION_WIDTH;
    private static final int NAV_HANDLE_WIDTH = QuestScreenLayout.NAVIGATION_HANDLE_WIDTH;
    private static final int NAV_GROUP_HEIGHT = 13;
    private static final int NAV_CHAPTER_HEIGHT = 15;
    private static final int TOP_TOOLBAR_HEIGHT = QuestScreenLayout.TOP_TOOLBAR_HEIGHT;
    private static final int BOTTOM_TOOLBAR_HEIGHT = QuestScreenLayout.BOTTOM_TOOLBAR_HEIGHT;
    private static final int CANVAS_MARGIN = 0;
    private static final int DETAIL_WIDTH = QuestScreenLayout.DETAILS_WIDTH;
    private static final int NODE_BASE_SIZE = 18;
    private static final int NAV_TOP = TOP_TOOLBAR_HEIGHT;
    private static final int NAV_BOTTOM_MARGIN = BOTTOM_TOOLBAR_HEIGHT;
    private static final int DETAIL_CONTENT_TOP = TOP_TOOLBAR_HEIGHT + 8;
    private static final int DETAIL_CONTENT_BOTTOM_MARGIN = BOTTOM_TOOLBAR_HEIGHT + 8;
    private static final int EDITOR_CHROME_HEIGHT = QuestScreenLayout.EDITOR_CONTROL_HEIGHT;
    private static final int EDITOR_CATALOG_ROW_HEIGHT = 30;
    private static final int EDITOR_CATALOG_SEARCH_HEIGHT = 18;
    private static final int EDITOR_BUTTON_WIDTH = 96;
    private static final int EDITOR_SAVE_BUTTON_WIDTH = 72;

    private double panX;
    private double panY;
    private double zoom;
    private double navigationScroll;
    private double detailScroll;
    private int navigationContentHeight;
    private int detailContentHeight;
    private int chapterIndex;
    private ResourceLocation rememberedChapterId;
    private boolean rememberedChapterResolved;
    private double dragX;
    private double dragY;
    private boolean dragging;
    private boolean detailsOpen;
    private boolean navigationCollapsed;
    private String serverContextId = "unknown";
    private ResourceLocation viewportBookId;
    private ResourceLocation editorSelectedQuest;
    private boolean catalogRequested;
    private int catalogScroll;
    private String catalogFilter = "";
    private EditorTextField questTitleField;
    private EditorTextField questSubtitleField;
    private EditorTextField questDescriptionField;
    private EditorTextField structureIdField;
    private EditorTextField structureTitleField;
    private boolean questEditorOpen;
    private ResourceLocation questEditorQuestId;
    private ResourceLocation discardSwitchTarget;
    private boolean discardClosesScreen;
    private final EditorOverlayHost editorOverlays = new EditorOverlayHost();
    private ContextKind editContextKind = ContextKind.NONE;
    private ResourceLocation editContextTarget;
    private int editContextX;
    private int editContextY;
    private double editContextGraphX;
    private double editContextGraphY;
    private StructureFormKind structureFormKind = StructureFormKind.NONE;
    private ResourceLocation structureFormTarget;
    private ResourceLocation structureFormParent;
    private double structureFormX;
    private double structureFormY;
    private DeleteKind deleteKind = DeleteKind.NONE;
    private ResourceLocation deleteTarget;
    private String deleteImpact = "";
    private final Set<ResourceLocation> editorSelection = new LinkedHashSet<>();
    private final Map<ResourceLocation, DraftBookEditor.Position> dragPreview = new LinkedHashMap<>();
    private Map<ResourceLocation, DraftBookEditor.Position> dragOrigins = Map.of();
    private boolean nodeDragging;
    private double nodeDragStartX;
    private double nodeDragStartY;
    private final Map<ResourceLocation, ItemStack> itemCache = new HashMap<>();
    private final List<RewardHitbox> rewardHitboxes = new ArrayList<>();
    private final List<TaskHitbox> taskHitboxes = new ArrayList<>();
    private ItemStack hoveredDetailStack = ItemStack.EMPTY;
    private Component hoveredDetailText;
    private List<Component> hoveredComponentTooltip = List.of();

    public QuestScreen() {
        super(Component.translatable("screen.brnquest.title"));
        QuestScreenSessionState.Snapshot defaults = QuestScreenSessionState.Snapshot.defaults();
        zoom = defaults.zoom();
        navigationCollapsed = defaults.navigationCollapsed();
    }

    @Override
    protected void init() {
        super.init();
        questTitleField = editorField("screen.brnquest.editor.quest.title", 256);
        questSubtitleField = editorField("screen.brnquest.editor.quest.subtitle", 256);
        questDescriptionField = editorField("screen.brnquest.editor.quest.description", 32_768);
        structureIdField = editorField("screen.brnquest.editor.structure.id", 256);
        structureTitleField = editorField("screen.brnquest.editor.structure.title", 256);
        serverContextId = currentServerContext();
        if (!catalogRequested && !ClientEditorState.get().editing()) {
            catalogRequested = true;
            ClientEditorState.get().beginCatalogRequest();
            AuthoringNetwork.requestCatalog();
        }
    }

    @Override
    public void removed() {
        // Save graph-space center coordinates rather than raw screen pixels, making
        // restoration independent of resolution and either side panel's width.
        saveViewport(currentChapterId());
        ClientEditorState editor = ClientEditorState.get();
        if (editor.dirty()) {
            // A forced screen replacement must not silently discard the only
            // authoritative copy of an unsaved in-memory draft. Reopening the
            // quest screen can resume it while the server lease remains valid.
            super.removed();
            return;
        }
        editor.beginClose(null).ifPresent(request ->
                AuthoringNetwork.closeSession(request.sessionId(), request.draftRevision()));
        editor.abandonLocalSession();
        super.removed();
    }

    @Override
    public void onClose() {
        if (ClientEditorState.get().dirty()) {
            requestDiscardConfirmation(null, true);
            return;
        }
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        ClientEditorState editor = ClientEditorState.get();
        editor.tick();
        if (questEditorOpen && !editor.editing()) closeQuestEditor();
        if ((!editor.editing() && switch (editorOverlays.active()) {
                case CONTEXT_MENU, STRUCTURE_FORM, DELETE_CONFIRMATION -> true;
                default -> false;
            }) || (!editor.allowed() && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG))) {
            closeActiveEditorOverlay();
        }
        editor.pollRenewRequest().ifPresent(request ->
                AuthoringNetwork.renewSession(request.sessionId(), request.draftRevision()));
        reconcileDragPreview();
    }

    /** Prevents Screen.render from applying a second blur pass over the completed UI. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Tooltips are collected by content layers and rendered only after every opaque panel.
        hoveredDetailStack = ItemStack.EMPTY;
        hoveredDetailText = null;
        hoveredComponentTooltip = List.of();
        // Blur the world once, then render every BRNQuest layer above it.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xC8151820);
        var snapshot = displaySnapshot();
        if (snapshot == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.brnquest.loading"), width / 2, height / 2, 0xFFFFFF);
            return;
        }

        ensureViewportBook(snapshot.book().id());

        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
        ChapterDefinition selectedChapter = null;
        if (!chapters.isEmpty()) {
            resolveRememberedChapter(chapters);
            chapterIndex = Math.min(chapterIndex, chapters.size() - 1);
            selectedChapter = chapters.get(chapterIndex);
        }
        renderNavigation(graphics, snapshot.book(), selectedChapter);
        if (!structureFormOpen()) renderCanvas(graphics, selectedChapter, mouseX, mouseY);
        else graphics.fill(canvasLeft(), TOP_TOOLBAR_HEIGHT, detailsOpen ? detailLeft() : width,
                height - BOTTOM_TOOLBAR_HEIGHT, 0xD0151820);
        if (detailsOpen && !structureFormOpen()) renderDetails(graphics, mouseX, mouseY);
        renderEditorChrome(graphics, snapshot.book(), mouseX, mouseY);
        if (structureFormOpen()) renderStructureForm(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderDeferredTooltip(graphics, mouseX, mouseY);
    }

    private void renderNavigation(GuiGraphics graphics, QuestBookDefinition book, ChapterDefinition selectedChapter) {
        if (navigationCollapsed) {
            graphics.fill(0, NAV_TOP, NAV_HANDLE_WIDTH, height - NAV_BOTTOM_MARGIN, 0xD01B222C);
            graphics.drawCenteredString(font, "›", NAV_HANDLE_WIDTH / 2, contentCenterY() - 4, 0xFFB7C5D8);
            return;
        }
        int viewportHeight = navigationViewportHeight();
        navigationContentHeight = navigationContentHeight(book);
        navigationScroll = QuestViewportMath.clampScroll(navigationScroll, navigationContentHeight, viewportHeight);
        int y = NAV_TOP - (int) Math.round(navigationScroll);
        // A continuous panel makes the compact rows read as one navigation surface.
        graphics.fill(NAV_LEFT, NAV_TOP, NAV_RIGHT + 4, height - NAV_BOTTOM_MARGIN, 0xB8181E27);
        graphics.enableScissor(NAV_LEFT, NAV_TOP, NAV_RIGHT + 6, navigationListBottom());
        for (QuestPresentation.NavigationEntry entry : QuestPresentation.navigation(book)) {
            if (entry.group() != null) {
                graphics.fill(NAV_LEFT, y, NAV_RIGHT, y + NAV_GROUP_HEIGHT, 0xE01B222C);
                graphics.drawString(font, Component.literal("▾ " + entry.group().title()), NAV_LEFT + 4, y + 2, 0xFFB7C5D8, false);
                y += NAV_GROUP_HEIGHT;
                continue;
            }
            ChapterDefinition chapter = entry.chapter();
            int color = selectedChapter != null && chapter.id().equals(selectedChapter.id()) ? 0xFF4A6A88 : 0xE0262D38;
            graphics.fill(NAV_LEFT + 4, y, NAV_RIGHT, y + NAV_CHAPTER_HEIGHT, color);
            graphics.drawString(font, Component.literal(chapter.title()), NAV_LEFT + 9, y + 3, 0xFFFFFF, false);
            y += NAV_CHAPTER_HEIGHT;
        }
        graphics.disableScissor();
        EditorScrollbar.render(graphics, NAV_RIGHT + 2, NAV_TOP, navigationListBottom(),
                navigationContentHeight, viewportHeight, navigationScroll);
        if (ClientEditorState.get().editing()) renderNavigationEditorButtons(graphics);
        graphics.fill(NAV_RIGHT + 4, NAV_TOP, NAV_RIGHT + 4 + NAV_HANDLE_WIDTH,
                height - NAV_BOTTOM_MARGIN, 0xD01B222C);
        graphics.drawCenteredString(font, "‹", NAV_RIGHT + 4 + NAV_HANDLE_WIDTH / 2,
                contentCenterY() - 4, 0xFFB7C5D8);
    }

    private void renderCanvas(GuiGraphics graphics, ChapterDefinition chapter, int mouseX, int mouseY) {
        int right = detailsOpen ? detailLeft() : width - CANVAS_MARGIN;
        int top = TOP_TOOLBAR_HEIGHT;
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT;
        graphics.enableScissor(canvasLeft(), top, right, bottom);
        // Keep the graph in one fixed-resolution coordinate system. A single pose
        // transform scales nodes, icons, lines and grid pixels together, avoiding the
        // independent integer rounding that previously made elements wobble while zooming.
        double graphLeft = graphX(canvasLeft());
        double graphRight = graphX(right);
        double graphTop = graphY(top);
        double graphBottom = graphY(bottom);
        double graphMouseX = graphX(mouseX);
        double graphMouseY = graphY(mouseY);
        graphics.pose().pushPose();
        graphics.pose().translate((float) graphOriginX(), (float) graphOriginY(), 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0F);
        renderGrid(graphics, graphLeft, graphRight, graphTop, graphBottom);

        if (chapter != null) {
            Map<ResourceLocation, QuestDefinition> chapterQuests = new HashMap<>();
            chapter.quests().forEach(quest -> chapterQuests.put(quest.id(), quest));
            for (QuestDefinition quest : chapter.quests()) {
                for (ResourceLocation dependency : quest.dependencies()) {
                    QuestDefinition parent = chapterQuests.get(dependency);
                    if (parent != null) renderDependency(graphics, parent, quest);
                }
            }
            for (QuestDefinition quest : chapter.quests()) {
                renderNode(graphics, quest, graphLeft, graphRight, graphTop, graphBottom, graphMouseX, graphMouseY);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    private void renderGrid(GuiGraphics graphics, double left, double right, double top, double bottom) {
        int firstX = (int) Math.floor(left / QuestViewportMath.GRID_SCALE) * (int) QuestViewportMath.GRID_SCALE;
        int firstY = (int) Math.floor(top / QuestViewportMath.GRID_SCALE) * (int) QuestViewportMath.GRID_SCALE;
        int drawLeft = (int) Math.floor(left) - 1;
        int drawRight = (int) Math.ceil(right) + 1;
        int drawTop = (int) Math.floor(top) - 1;
        int drawBottom = (int) Math.ceil(bottom) + 1;
        for (int x = firstX; x <= drawRight; x += (int) QuestViewportMath.GRID_SCALE) {
            graphics.fill(x, drawTop, x + 1, drawBottom, 0x243C4655);
        }
        for (int y = firstY; y <= drawBottom; y += (int) QuestViewportMath.GRID_SCALE) {
            graphics.fill(drawLeft, y, drawRight, y + 1, 0x243C4655);
        }
    }

    /** Draws an orthogonal dependency path whose arrow always points at the dependent node. */
    private void renderDependency(GuiGraphics graphics, QuestDefinition parent, QuestDefinition child) {
        int x1 = nodeGraphX(parent);
        int y1 = nodeGraphY(parent);
        int x2 = nodeGraphX(child);
        int y2 = nodeGraphY(child);
        int radius = NODE_BASE_SIZE / 2;
        int thickness = 1;
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

    private void renderNode(GuiGraphics graphics, QuestDefinition quest, double left, double right,
                            double top, double bottom, double mouseX, double mouseY) {
        int x = nodeGraphX(quest);
        int y = nodeGraphY(quest);
        int size = NODE_BASE_SIZE;
        int radius = size / 2;
        // Keep partially visible nodes; cull only after their entire bounds leave the canvas.
        if (!QuestViewportMath.intersectsViewport(x, y, radius, left, right, top, bottom)) return;

        boolean editing = ClientEditorState.get().editing();
        QuestStatus status = status(quest);
        int color = editing ? 0xFF4A6A88 : switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF4C9A66;
            case AVAILABLE, ACTIVE -> 0xFFCF9F42;
            default -> 0xFF59606B;
        };
        boolean selected = editing ? editorSelection.contains(quest.id()) : quest.id().equals(selectedQuestId());
        boolean tracked = !editing && status == QuestStatus.ACTIVE;
        if (tracked) fillChamfer(graphics, x, y, size + 7, 0xFF57C7F2);
        fillChamfer(graphics, x, y, size + (selected ? 4 : 2), selected ? 0xFF91C9F4 : 0xFF222936);
        fillChamfer(graphics, x, y, size, color);
        renderQuestVisual(graphics, quest, x, y, size);
        if (!editing && QuestPresentation.hasPendingReward(quest, status, ClientQuestState.get().claimed())) {
            renderPendingRewardBadge(graphics, x, y, size);
        }

        if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom
                && Math.abs(mouseX - x) <= radius && Math.abs(mouseY - y) <= radius) {
            hoveredDetailText = Component.literal(questTitle(quest));
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
        int left = detailLeft();
        graphics.fill(left, TOP_TOOLBAR_HEIGHT, width, height - BOTTOM_TOOLBAR_HEIGHT, 0xF0202632);
        graphics.drawString(font, Component.literal("×"), width - 14, TOP_TOOLBAR_HEIGHT + 4, 0xFFFFFF, false);

        QuestDefinition quest = selectedQuest();
        if (quest == null) return;
        QuestStatus status = status(quest);
        boolean editing = ClientEditorState.get().editing();
        if (editing && questEditorOpen && quest.id().equals(questEditorQuestId)) {
            detailContentHeight = 0;
            detailScroll = 0;
            renderQuestPropertyEditor(graphics, quest);
            return;
        }
        int contentLeft = left + 10;
        int contentWidth = DETAIL_WIDTH - 24;
        int viewportHeight = detailViewportHeight();
        int y = DETAIL_CONTENT_TOP - (int) Math.round(detailScroll);
        graphics.enableScissor(left + 1, DETAIL_CONTENT_TOP, width - 10, height - DETAIL_CONTENT_BOTTOM_MARGIN);
        y = drawWrapped(graphics, questTitle(quest), contentLeft, y, contentWidth - 14, 0xFFFFFF) + 3;
        if (!quest.subtitle().isBlank()) y = drawWrapped(graphics, quest.subtitle(), contentLeft, y, contentWidth, 0xFFB7C5D8) + 4;

        boolean ready = canSubmit(quest, status);
        Component statusText = editing
                ? Component.translatable("screen.brnquest.editor.preview")
                : Component.translatable(QuestPresentation.statusTranslationKey(
                        quest, status, ClientQuestState.get().claimed()));
        graphics.drawString(font, statusText, contentLeft, y, statusColor(status), false);
        int statusTop = y;
        int statusWidth = font.width(statusText);
        if (!editing && status == QuestStatus.LOCKED) {
            // Cyan underline and info glyph advertise that the locked reason is inspectable.
            graphics.fill(contentLeft, y + font.lineHeight, contentLeft + statusWidth, y + font.lineHeight + 1, 0xFF68BDE8);
            graphics.drawString(font, Component.literal("ⓘ"), contentLeft + statusWidth + 4, y, 0xFF68BDE8, false);
            statusWidth += 4 + font.width("ⓘ");
        }
        if (!editing && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
            String pin = status == QuestStatus.ACTIVE ? "★" : "☆";
            int pinX = detailTrackX(pin);
            graphics.drawString(font, Component.literal(pin), pinX, y, status == QuestStatus.ACTIVE ? 0xFF57C7F2 : 0xFFB7C5D8, false);
            if (mouseX >= pinX - 2 && mouseX <= pinX + font.width(pin) + 2 && mouseY >= y && mouseY <= y + font.lineHeight) {
                hoveredDetailText = Component.translatable(status == QuestStatus.ACTIVE
                        ? "screen.brnquest.untrack" : "screen.brnquest.track");
            }
        }
        if (!editing && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && ready) {
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
        EditorScrollbar.render(graphics, width - 8, DETAIL_CONTENT_TOP, height - DETAIL_CONTENT_BOTTOM_MARGIN,
                detailContentHeight, viewportHeight, detailScroll);

        if (editing) {
            UiRect edit = questPropertyButtonBounds();
            EditorButton.render(graphics, font, edit,
                    Component.translatable("screen.brnquest.editor.quest.edit"),
                    0xFF385A72, 0xFFFFFFFF, 5);
        }

        int visibleStatusY = statusTop;
        if (!editing && status == QuestStatus.LOCKED && mouseX >= contentLeft && mouseX <= contentLeft + statusWidth
                && mouseY >= visibleStatusY && mouseY <= visibleStatusY + font.lineHeight
                && visibleStatusY >= DETAIL_CONTENT_TOP && visibleStatusY < height - DETAIL_CONTENT_BOTTOM_MARGIN) {
            hoveredComponentTooltip = dependencyTooltip(quest);
        }

    }

    private int renderTask(GuiGraphics graphics, QuestDefinition quest, TaskDefinition task, QuestStatus status,
                           int x, int y, int width, int mouseX, int mouseY) {
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var taskView = ApiViews.task(task);
        boolean satisfied = taskSatisfied(task, status);
        graphics.fill(x, y, x + width, y + 24, satisfied ? 0x663B6749 : 0x66343D49);
        String itemSnbt = presentation.itemSnbt(taskView);
        ItemStack stack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 3, y + 4);
        else graphics.drawCenteredString(font, presentation.symbol(taskView), x + 11, y + 8, 0xFFFFFFFF);

        String title = task.config().getOrDefault("title", "");
        long storedProgress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        TaskPresentationContext presentationContext = new TaskPresentationContext(minecraft, taskView, status,
                storedProgress, stack);
        if (title.isBlank()) title = presentation.title(presentationContext).getString();
        graphics.drawString(font, Component.literal(title), x + 24, y + 3, satisfied ? 0xFF8BE2A0 : 0xFFFFFFFF, false);
        String progress = taskProgressText(task, status, stack, satisfied);
        graphics.drawString(font, Component.literal(progress), x + 24, y + 13, 0xFFABB7C6, false);
        if (task.optional()) graphics.drawString(font, Component.translatable("screen.brnquest.optional"), x + width - 38, y + 13, 0xFF9AA6B5, false);
        boolean visible = y >= DETAIL_CONTENT_TOP && y + 24 <= height - DETAIL_CONTENT_BOTTOM_MARGIN;
        boolean submitted = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L) >= 1;
        boolean pending = ClientQuestState.get().isTaskSubmissionPending(task.id().toString());
        boolean interactive = !ClientEditorState.get().editing()
                && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) && !submitted && !pending
                && presentation.interactive(taskView);
        if (interactive && visible) taskHitboxes.add(new TaskHitbox(x, y, x + width, y + 24, quest, task));
        if (visible && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + 24) {
            // Always delegate item tooltips to Minecraft so component data and tooltip
            // lines injected by other mods remain intact. Text hints cover non-item rows.
            if (!stack.isEmpty()) hoveredDetailStack = stack;
            else hoveredDetailText = presentation.interactionHint(presentationContext, interactive);
        }
        return y + 28;
    }

    private void renderReward(GuiGraphics graphics, RewardDefinition reward, int x, int y, QuestStatus status, int mouseX, int mouseY) {
        boolean claimed = ClientQuestState.get().claimed().contains(reward.id().toString());
        boolean claimable = !ClientEditorState.get().editing() && isCompleted(status) && !claimed;
        ClientRewardPresentation presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        var rewardView = ApiViews.reward(reward);
        String itemSnbt = presentation.itemSnbt(rewardView);
        ItemStack stack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), itemSnbt);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 4, y + 4);
        else graphics.drawCenteredString(font, presentation.symbol(rewardView), x + 12, y + 8, 0xFFFFFFFF);
        if (claimable) {
            graphics.fill(x + 2, y + 2, x + 22, y + 3, 0xFFE6B55B);
            graphics.fill(x + 2, y + 21, x + 22, y + 22, 0xFFE6B55B);
        }
        if (claimed) graphics.drawString(font, "✓", x + 15, y + 14, 0xFF8BE2A0, true);
        boolean visible = y >= DETAIL_CONTENT_TOP && y + 24 <= height - DETAIL_CONTENT_BOTTOM_MARGIN;
        if (claimable && visible) rewardHitboxes.add(new RewardHitbox(x, y, x + 24, y + 24, reward));
        if (visible && mouseX >= x && mouseX <= x + 24 && mouseY >= y && mouseY <= y + 24) {
            RewardPresentationContext presentationContext = new RewardPresentationContext(minecraft, rewardView,
                    claimable, claimed, stack);
            // Item rewards retain the complete vanilla/modded tooltip pipeline.
            if (!stack.isEmpty()) hoveredDetailStack = stack;
            else hoveredDetailText = presentation.interactionHint(presentationContext);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var snapshot = displaySnapshot();
        if (snapshot == null) return super.mouseClicked(mouseX, mouseY, button);
        switch (editorOverlays.active()) {
            case STRUCTURE_FORM -> {
                return handleStructureFormClick(mouseX, mouseY, button);
            }
            case DELETE_CONFIRMATION -> {
                return handleDeleteConfirmationClick(mouseX, mouseY, button);
            }
            case DISCARD_CONFIRMATION -> {
                return handleDiscardConfirmationClick(mouseX, mouseY, button);
            }
            case CONTEXT_MENU -> {
                return handleEditContextClick(mouseX, mouseY, button, snapshot.book());
            }
            default -> { }
        }
        if (handleEditorChromeClick(mouseX, mouseY, button, snapshot.book())) return true;

        if (questEditorOpen && mouseX >= detailLeft()) {
            if (button == 0 && questEditorSaveBounds().contains(mouseX, mouseY)) {
                submitQuestPropertyEdit();
                return true;
            }
            if (button == 0 && questEditorCancelBounds().contains(mouseX, mouseY)) {
                closeQuestEditor();
                return true;
            }
            super.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        int navigationHandleLeft = navigationCollapsed ? 0 : NAV_RIGHT + 4;
        if (mouseX >= navigationHandleLeft && mouseX <= navigationHandleLeft + NAV_HANDLE_WIDTH
                && isContentY(mouseY)) {
            navigationCollapsed = !navigationCollapsed;
            return true;
        }

        if (!navigationCollapsed && mouseX >= NAV_RIGHT && mouseX <= NAV_RIGHT + 4 && navigationContentHeight > navigationViewportHeight()) {
            navigationScroll = EditorScrollbar.scrollFromTrack(mouseY, NAV_TOP, navigationListBottom(),
                    navigationContentHeight, navigationViewportHeight());
            return true;
        }
        if (detailsOpen && mouseX >= width - 12 && mouseX <= width && detailContentHeight > detailViewportHeight()
                && mouseY >= DETAIL_CONTENT_TOP && mouseY <= height - DETAIL_CONTENT_BOTTOM_MARGIN) {
            detailScroll = EditorScrollbar.scrollFromTrack(mouseY, DETAIL_CONTENT_TOP, height - DETAIL_CONTENT_BOTTOM_MARGIN,
                    detailContentHeight, detailViewportHeight());
            return true;
        }

        if (ClientEditorState.get().editing() && !navigationCollapsed) {
            if (navigationAddGroupBounds().contains(mouseX, mouseY)) {
                openStructureForm(StructureFormKind.ADD_GROUP, null, null, 0, 0);
                return true;
            }
            if (navigationAddChapterBounds().contains(mouseX, mouseY)) {
                ResourceLocation groupId = defaultGroupId(snapshot.book());
                if (groupId != null) openStructureForm(StructureFormKind.ADD_CHAPTER, null, groupId, 0, 0);
                return true;
            }
            QuestPresentation.NavigationEntry navigationEntry = navigationEntry(snapshot.book(), mouseX, mouseY);
            if (button == 1 && navigationEntry != null) {
                openEditContext(navigationEntry.group() != null ? ContextKind.GROUP : ContextKind.CHAPTER,
                        navigationEntry.group() != null ? navigationEntry.group().id() : navigationEntry.chapter().id(),
                        (int) mouseX, (int) mouseY, 0, 0);
                return true;
            }
        }

        ChapterDefinition navigationChoice = navigationChoice(snapshot.book(), mouseX, mouseY);
        if (navigationChoice != null) {
            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            chapterIndex = chapters.indexOf(navigationChoice);
            rememberedChapterId = navigationChoice.id();
            rememberedChapterResolved = true;
            editorSelection.clear();
            dragPreview.clear();
            panX = 0;
            panY = 0;
            detailsOpen = false;
            detailScroll = 0;
            closeQuestEditor();
            return true;
        }
        // Group headings and empty navigation space belong to the left panel and
        // must not fall through into quest selection or canvas interaction.
        if (!navigationCollapsed && mouseX >= NAV_LEFT && mouseX <= NAV_RIGHT) return true;

        QuestDefinition selected = selectedQuest();
        int left = detailLeft();
        if (detailsOpen && mouseX >= width - 18 && mouseX <= width
                && mouseY >= TOP_TOOLBAR_HEIGHT && mouseY <= TOP_TOOLBAR_HEIGHT + 16) {
            detailsOpen = false;
            detailScroll = 0;
            closeQuestEditor();
            return true;
        }
        if (detailsOpen && selected != null) {
            if (ClientEditorState.get().editing() && questPropertyButtonBounds().contains(mouseX, mouseY)) {
                openQuestEditor(selected);
                return true;
            }
            if (ClientEditorState.get().editing() && mouseX >= left) return true;
            QuestStatus status = status(selected);
            Component statusText = Component.translatable(
                    QuestPresentation.statusTranslationKey(selected, status, ClientQuestState.get().claimed()));
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
                    String taskId = hitbox.task().id().toString();
                    // Disable the row until the authoritative response arrives, preventing a
                    // fast double click from enqueueing the same consumption intent twice.
                    if (ClientQuestState.get().beginTaskSubmission(taskId)) {
                        BrnQuestNetwork.completeTask(ClientQuestState.get().revision(), hitbox.quest().id().toString(), taskId);
                    }
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

        int canvasRight = detailsOpen ? left : width - CANVAS_MARGIN;
        if (mouseX > canvasLeft() && mouseX < canvasRight && isContentY(mouseY)) {
            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            ChapterDefinition chapter = chapters.isEmpty() ? null : chapters.get(Math.min(chapterIndex, chapters.size() - 1));
            double graphMouseX = graphX(mouseX);
            double graphMouseY = graphY(mouseY);
            QuestDefinition hit = chapter == null ? null : nodeAt(chapter, graphMouseX, graphMouseY);
            if (hit != null) {
                if (ClientEditorState.get().editing()) {
                    if (button == 1) {
                        if (!editorSelection.contains(hit.id())) selectOnly(hit.id());
                        openEditContext(ContextKind.NODE, hit.id(), (int) mouseX, (int) mouseY,
                                hit.x(), hit.y());
                        return true;
                    }
                    if (button == 0) {
                        if (hasControlDown()) {
                            if (!editorSelection.add(hit.id())) editorSelection.remove(hit.id());
                            if (editorSelection.isEmpty()) editorSelection.add(hit.id());
                        } else if (!editorSelection.contains(hit.id())) {
                            selectOnly(hit.id());
                        }
                        editorSelectedQuest = hit.id();
                        beginNodeDrag(graphMouseX, graphMouseY, snapshot.book());
                    }
                } else if (button == 0) {
                    ClientQuestState.get().selected(hit.id());
                }
                if (button == 0) {
                    closeQuestEditor();
                    detailsOpen = true;
                    detailScroll = 0;
                    if (!ClientEditorState.get().editing()) {
                        BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), hit.id().toString());
                    }
                    return true;
                }
            }
            if (ClientEditorState.get().editing() && button == 1 && chapter != null) {
                openEditContext(ContextKind.CANVAS, null, (int) mouseX, (int) mouseY,
                        graphMouseX / QuestViewportMath.GRID_SCALE, graphMouseY / QuestViewportMath.GRID_SCALE);
                return true;
            }
            if (button != 0) return true;
            if (ClientEditorState.get().editing() && !hasControlDown()) editorSelection.clear();
            dragging = true;
            dragX = mouseX;
            dragY = mouseY;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE) {
            return editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)
                    ? super.mouseReleased(x, y, button) : true;
        }
        if (nodeDragging && button == 0) {
            commitNodeDrag();
            return true;
        }
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE) {
            return editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)
                    ? super.mouseDragged(x, y, button, dx, dy) : true;
        }
        if (nodeDragging && button == 0) {
            updateNodeDrag(graphX(x), graphY(y));
            return true;
        }
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
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG) && editorCatalogBounds().contains(x, y)) {
            int visibleRows = editorCatalogVisibleRows();
            int maximum = Math.max(0, editorCatalogEntries().size() - visibleRows);
            catalogScroll = Math.max(0, Math.min(maximum, catalogScroll - (int) Math.signum(vertical)));
            return true;
        }
        // Any open editor overlay owns wheel input, even outside its visible bounds.
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE) return true;
        if (!navigationCollapsed && x >= NAV_LEFT && x <= NAV_RIGHT + NAV_HANDLE_WIDTH + 4 && isContentY(y)) {
            navigationScroll = QuestViewportMath.clampScroll(navigationScroll - vertical * 24,
                    navigationContentHeight, navigationViewportHeight());
            return true;
        }
        if (detailsOpen && x >= detailLeft() && isContentY(y)) {
            detailScroll = QuestViewportMath.clampScroll(detailScroll - vertical * 24,
                    detailContentHeight, detailViewportHeight());
            return true;
        }

        if (!isContentY(y)) return true;
        double oldZoom = zoom;
        double nextZoom = QuestViewportMath.clampZoom(zoom + vertical * 0.10);
        if (nextZoom == oldZoom) return true;
        // Side panels only clip the graph; the camera always zooms about the physical
        // center of the whole Screen, regardless of their open state or width.
        double anchorX = width / 2.0;
        double anchorY = height / 2.0;
        panX = QuestViewportMath.panForStableAnchor(anchorX, screenOriginX(), panX, oldZoom, nextZoom);
        panY = QuestViewportMath.panForStableAnchor(anchorY, height / 2.0, panY, oldZoom, nextZoom);
        zoom = nextZoom;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && editorOverlays.active() != EditorOverlayHost.Kind.NONE) {
            closeActiveEditorOverlay();
            return true;
        }
        if (keyCode == 256 && questEditorOpen) {
            closeQuestEditor();
            return true;
        }
        if (keyCode == 259 && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)
                && !catalogFilter.isEmpty()) {
            catalogFilter = catalogFilter.substring(0, catalogFilter.length() - 1);
            catalogScroll = 0;
            return true;
        }
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)) return true;
        if (keyCode == 256 && detailsOpen) {
            detailsOpen = false;
            detailScroll = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)
                && !Character.isISOControl(codePoint) && catalogFilter.length() < 48) {
            catalogFilter += codePoint;
            catalogScroll = 0;
            return true;
        }
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    private ChapterDefinition navigationChoice(QuestBookDefinition book, double mouseX, double mouseY) {
        if (navigationCollapsed) return null;
        if (mouseX < NAV_LEFT || mouseX > NAV_RIGHT || mouseY >= navigationListBottom()) return null;
        int y = NAV_TOP - (int) Math.round(navigationScroll);
        for (QuestPresentation.NavigationEntry entry : QuestPresentation.navigation(book)) {
            if (entry.group() != null) {
                y += NAV_GROUP_HEIGHT;
            } else {
                if (mouseY >= y && mouseY <= y + NAV_CHAPTER_HEIGHT) return entry.chapter();
                y += NAV_CHAPTER_HEIGHT;
            }
        }
        return null;
    }

    private QuestPresentation.NavigationEntry navigationEntry(QuestBookDefinition book, double mouseX, double mouseY) {
        if (navigationCollapsed || mouseX < NAV_LEFT || mouseX > NAV_RIGHT
                || mouseY >= navigationListBottom()) return null;
        int y = NAV_TOP - (int) Math.round(navigationScroll);
        for (QuestPresentation.NavigationEntry entry : QuestPresentation.navigation(book)) {
            int rowHeight = entry.group() != null ? NAV_GROUP_HEIGHT : NAV_CHAPTER_HEIGHT;
            if (mouseY >= y && mouseY <= y + rowHeight) return entry;
            y += rowHeight;
        }
        return null;
    }

    /** Adds editor controls as overlays so switching modes never resizes the established three regions. */
    private void renderEditorChrome(GuiGraphics graphics, QuestBookDefinition book, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        // Item rendering uses a raised GUI depth; keep opaque editor popovers above
        // every background node/task icon without changing their screen geometry.
        graphics.pose().translate(0, 0, 300);
        ClientEditorState editor = ClientEditorState.get();
        // Both toolbars own the full screen width. Side drawers only resize the
        // middle content area and can never push these controls horizontally.
        UiRect topToolbar = layout().topToolbar();
        UiRect bottomToolbar = layout().bottomToolbar();
        graphics.fill(topToolbar.left(), topToolbar.top(), topToolbar.right(), topToolbar.bottom(), 0xF0202632);
        graphics.fill(bottomToolbar.left(), bottomToolbar.top(), bottomToolbar.right(), bottomToolbar.bottom(),
                0xF0202632);
        UiRect titleBounds = editorTitleBounds();
        graphics.fill(titleBounds.left(), titleBounds.top(), titleBounds.right(), titleBounds.bottom(), 0xE0202632);
        String title = book.title().isBlank() ? book.id().toString() : book.title();
        String suffix = editor.allowed() ? " ▾" : "";
        String visibleTitle = font.plainSubstrByWidth(title, Math.max(1, titleBounds.width() - 16 - font.width(suffix)));
        graphics.drawCenteredString(font, Component.literal(visibleTitle + suffix), titleBounds.centerX(),
                titleBounds.top() + 4, 0xFFFFFFFF);
        if (titleBounds.contains(mouseX, mouseY)) {
            hoveredDetailText = Component.literal(book.id().toString());
        }

        if (editor.allowed() || editor.hasLease() || editor.busy()) {
            UiRect button = editorButtonBounds();
            boolean active = editor.editing() || editor.hasLease();
            Component label = Component.translatable(active
                    ? "screen.brnquest.editor.exit" : "screen.brnquest.editor.edit_current");
            EditorButton.render(graphics, font, button, label,
                    active ? 0xEF385A72 : 0xE02A323E, 0xFFFFFFFF, 4);
        }

        if (editor.hasLease()) {
            UiRect save = editorSaveButtonBounds();
            boolean enabled = editor.dirty() && !editor.busy();
            Component label = Component.translatable(editor.dirty()
                    ? "screen.brnquest.editor.save" : "screen.brnquest.editor.saved");
            EditorButton.render(graphics, font, save, label,
                    enabled ? 0xEF3E735A : 0xD02A323E, enabled ? 0xFFFFFFFF : 0xFF8793A1, 4);
        }

        Component status = editorStatus();
        if (status != null) {
            UiRect leadingButton = editor.hasLease() ? editorSaveButtonBounds() : editorButtonBounds();
            int maximumWidth = Math.max(40, leadingButton.left() - canvasLeft() - 8);
            String statusText = font.plainSubstrByWidth(status.getString(), maximumWidth - 10);
            int statusWidth = Math.min(maximumWidth, font.width(statusText) + 10);
            int left = 4;
            int top = height - EDITOR_CHROME_HEIGHT - 4;
            graphics.fill(left, top, left + statusWidth, top + EDITOR_CHROME_HEIGHT, 0xE0202632);
            graphics.drawString(font, Component.literal(statusText), left + 5, top + 4,
                    editor.mode() == ClientEditorState.Mode.ERROR ? 0xFFFF8B8B : 0xFFB7C5D8, false);
        }

        switch (editorOverlays.active()) {
            case CATALOG -> {
                if (editor.allowed()) renderEditorCatalog(graphics, mouseX, mouseY);
            }
            case CONTEXT_MENU -> renderEditContextMenu(graphics);
            case DISCARD_CONFIRMATION -> renderDiscardConfirmation(graphics);
            case DELETE_CONFIRMATION -> renderDeleteConfirmation(graphics);
            default -> { }
        }
        graphics.pose().popPose();
    }

    private void renderNavigationEditorButtons(GuiGraphics graphics) {
        UiRect group = navigationAddGroupBounds();
        UiRect chapter = navigationAddChapterBounds();
        EditorButton.render(graphics, font, group,
                Component.translatable("screen.brnquest.editor.group.add"), 0xEF385A72, 0xFFFFFFFF, 4);
        EditorButton.render(graphics, font, chapter,
                Component.translatable("screen.brnquest.editor.chapter.add"),
                defaultGroupId(displaySnapshot().book()) == null ? 0xD02A323E : 0xEF385A72,
                0xFFFFFFFF, 4);
    }

    private void renderEditContextMenu(GuiGraphics graphics) {
        List<ContextAction> actions = contextActions();
        UiRect menu = editContextBounds(actions.size());
        List<EditorPopupMenu.Entry> entries = actions.stream()
                .map(action -> new EditorPopupMenu.Entry(Component.translatable(action.translationKey()),
                        action.dangerous()))
                .toList();
        EditorPopupMenu.render(graphics, font, menu, entries);
    }

    private boolean handleEditContextClick(double mouseX, double mouseY, int button, QuestBookDefinition book) {
        if (button != 0) {
            closeEditContext();
            return true;
        }
        List<ContextAction> actions = contextActions();
        UiRect menu = editContextBounds(actions.size());
        if (!menu.contains(mouseX, mouseY)) {
            closeEditContext();
            return true;
        }
        int index = EditorPopupMenu.rowAt(menu, actions.size(), mouseX, mouseY);
        if (index < 0 || index >= actions.size()) return true;
        String action = actions.get(index).action();
        ResourceLocation target = editContextTarget;
        double graphX = editContextGraphX;
        double graphY = editContextGraphY;
        closeEditContext();
        switch (action) {
            case "ADD_QUEST" -> {
                ResourceLocation chapterId = currentChapterId();
                if (chapterId != null) openStructureForm(StructureFormKind.ADD_QUEST, null, chapterId, graphX, graphY);
            }
            case "COPY_QUEST" -> openStructureForm(StructureFormKind.COPY_QUEST, target, null, graphX + 1, graphY + 1);
            case "DELETE_QUEST" -> requestDelete(DeleteKind.QUEST, target, book);
            case "ADD_CHAPTER" -> openStructureForm(StructureFormKind.ADD_CHAPTER, null, target, 0, 0);
            case "RENAME_GROUP" -> openStructureForm(StructureFormKind.RENAME_GROUP, target, null, 0, 0);
            case "MOVE_GROUP_UP" -> moveGroup(book, target, -1);
            case "MOVE_GROUP_DOWN" -> moveGroup(book, target, 1);
            case "DELETE_GROUP" -> requestDelete(DeleteKind.GROUP, target, book);
            case "RENAME_CHAPTER" -> openStructureForm(StructureFormKind.RENAME_CHAPTER, target, null, 0, 0);
            case "MOVE_CHAPTER_UP" -> moveChapter(book, target, -1);
            case "MOVE_CHAPTER_DOWN" -> moveChapter(book, target, 1);
            case "DELETE_CHAPTER" -> requestDelete(DeleteKind.CHAPTER, target, book);
            default -> { }
        }
        return true;
    }

    private List<ContextAction> contextActions() {
        return switch (editContextKind) {
            case CANVAS -> List.of(new ContextAction("ADD_QUEST", "screen.brnquest.editor.context.add_quest", false));
            case NODE -> List.of(
                    new ContextAction("COPY_QUEST", "screen.brnquest.editor.context.copy_quest", false),
                    new ContextAction("DELETE_QUEST", "screen.brnquest.editor.context.delete_quest", true));
            case GROUP -> List.of(
                    new ContextAction("ADD_CHAPTER", "screen.brnquest.editor.context.add_chapter", false),
                    new ContextAction("RENAME_GROUP", "screen.brnquest.editor.context.rename", false),
                    new ContextAction("MOVE_GROUP_UP", "screen.brnquest.editor.context.move_up", false),
                    new ContextAction("MOVE_GROUP_DOWN", "screen.brnquest.editor.context.move_down", false),
                    new ContextAction("DELETE_GROUP", "screen.brnquest.editor.context.delete", true));
            case CHAPTER -> List.of(
                    new ContextAction("RENAME_CHAPTER", "screen.brnquest.editor.context.rename", false),
                    new ContextAction("MOVE_CHAPTER_UP", "screen.brnquest.editor.context.move_up", false),
                    new ContextAction("MOVE_CHAPTER_DOWN", "screen.brnquest.editor.context.move_down", false),
                    new ContextAction("DELETE_CHAPTER", "screen.brnquest.editor.context.delete", true));
            case NONE -> List.of();
        };
    }

    private UiRect editContextBounds(int rows) {
        return EditorPopupMenu.layout(editContextX, editContextY, width,
                TOP_TOOLBAR_HEIGHT, height - BOTTOM_TOOLBAR_HEIGHT, 142, rows);
    }

    private void openEditContext(ContextKind kind, ResourceLocation target, int x, int y,
                                 double graphX, double graphY) {
        closeActiveEditorOverlay();
        editContextKind = kind;
        editContextTarget = target;
        editContextX = x;
        editContextY = y;
        editContextGraphX = graphX;
        editContextGraphY = graphY;
        editorOverlays.show(EditorOverlayHost.Kind.CONTEXT_MENU);
    }

    private void closeEditContext() {
        editContextKind = ContextKind.NONE;
        editContextTarget = null;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CONTEXT_MENU)) editorOverlays.close();
    }

    /** Closes the sole input-capturing overlay and clears its associated payload. */
    private void closeActiveEditorOverlay() {
        switch (editorOverlays.active()) {
            case CATALOG -> {
                catalogFilter = "";
                catalogScroll = 0;
                editorOverlays.close();
            }
            case CONTEXT_MENU -> closeEditContext();
            case STRUCTURE_FORM -> closeStructureForm();
            case DELETE_CONFIRMATION -> {
                deleteKind = DeleteKind.NONE;
                deleteTarget = null;
                deleteImpact = "";
                editorOverlays.close();
            }
            case DISCARD_CONFIRMATION -> {
                discardSwitchTarget = null;
                discardClosesScreen = false;
                editorOverlays.close();
            }
            case NONE -> { }
        }
    }

    private void renderStructureForm(GuiGraphics graphics) {
        UiRect form = structureFormBounds();
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(form.left(), form.top(), form.right(), form.bottom(), 0xFF202832);
        graphics.drawCenteredString(font, Component.translatable(structureFormKind.translationKey()),
                form.centerX(), form.top() + 10, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.structure.id"),
                form.left() + 12, form.top() + 30, 0xFF9FB0C2, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.structure.title"),
                form.left() + 12, form.top() + 62, 0xFF9FB0C2, false);
        positionEditorField(structureIdField, form.left() + 82, form.top() + 25, form.width() - 94);
        positionEditorField(structureTitleField, form.left() + 82, form.top() + 57, form.width() - 94);
        structureIdField.active = structureFormKind.createsStableId();
        EditorButton.render(graphics, font, structureFormCancelBounds(), Component.translatable("gui.cancel"),
                0xFF343D49, 0xFFFFFFFF, 5);
        EditorButton.render(graphics, font, structureFormDoneBounds(), Component.translatable("gui.done"),
                0xFF385A72, 0xFFFFFFFF, 5);
    }

    private boolean handleStructureFormClick(double mouseX, double mouseY, int button) {
        if (button == 0 && structureFormCancelBounds().contains(mouseX, mouseY)) {
            closeStructureForm();
            return true;
        }
        if (button == 0 && structureFormDoneBounds().contains(mouseX, mouseY)) {
            submitStructureForm();
            return true;
        }
        super.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    private void openStructureForm(StructureFormKind kind, ResourceLocation target, ResourceLocation parent,
                                   double x, double y) {
        closeQuestEditor();
        closeActiveEditorOverlay();
        QuestBookDefinition book = displaySnapshot().book();
        structureFormKind = kind;
        structureFormTarget = target;
        structureFormParent = parent;
        structureFormX = x;
        structureFormY = y;
        ResourceLocation id = target;
        String title = "";
        if (kind == StructureFormKind.RENAME_GROUP) {
            ChapterGroupDefinition group = book.chapterGroups().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (group != null) title = group.title();
        } else if (kind == StructureFormKind.RENAME_CHAPTER) {
            ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (chapter != null) {
                title = chapter.title();
                structureFormParent = chapter.groupId();
            }
        } else {
            id = suggestId(book, kind.idStem());
            if (kind == StructureFormKind.COPY_QUEST && target != null) {
                QuestDefinition source = book.quests().stream().filter(value -> value.id().equals(target))
                        .findFirst().orElse(null);
                if (source != null) {
                    title = source.title() + " Copy";
                    structureFormParent = source.chapterId();
                }
            }
        }
        structureIdField.setValue(id == null ? "" : id.toString());
        structureTitleField.setValue(title);
        structureIdField.setVisible(true);
        structureTitleField.setVisible(true);
        setFocused(kind.createsStableId() ? structureIdField : structureTitleField);
        editorOverlays.show(EditorOverlayHost.Kind.STRUCTURE_FORM);
    }

    private void submitStructureForm() {
        ClientEditorState editor = ClientEditorState.get();
        QuestBookDefinition book = displaySnapshot().book();
        ResourceLocation id = ResourceLocation.tryParse(structureIdField.getValue());
        if (id == null || structureTitleField.getValue().isBlank()) return;
        String title = structureTitleField.getValue();
        switch (structureFormKind) {
            case ADD_GROUP -> sendMutation("ADD_GROUP", id, null, null, title,
                    book.chapterGroups().size(), 0, 0, List.of());
            case RENAME_GROUP -> {
                ChapterGroupDefinition group = book.chapterGroups().stream()
                        .filter(value -> value.id().equals(structureFormTarget)).findFirst().orElse(null);
                if (group != null) sendMutation("UPDATE_GROUP", group.id(), null, null, title,
                        group.order(), 0, 0, List.of());
            }
            case ADD_CHAPTER -> sendMutation("ADD_CHAPTER", id, structureFormParent, null, title,
                    (int) book.chapters().stream().filter(value -> value.groupId().equals(structureFormParent)).count(),
                    0, 0, List.of());
            case RENAME_CHAPTER -> {
                ChapterDefinition chapter = book.chapters().stream()
                        .filter(value -> value.id().equals(structureFormTarget)).findFirst().orElse(null);
                if (chapter != null) sendMutation("UPDATE_CHAPTER", chapter.id(), chapter.groupId(), null, title,
                        chapter.order(), 0, 0, List.of());
            }
            case ADD_QUEST -> sendMutation("ADD_QUEST", id, structureFormParent, null, title, 0,
                    structureFormX, structureFormY, List.of());
            case COPY_QUEST -> sendMutation("COPY_QUEST", id, structureFormParent, structureFormTarget, title, 0,
                    structureFormX, structureFormY, List.of());
            case NONE -> { }
        }
        closeStructureForm();
    }

    private void closeStructureForm() {
        structureFormKind = StructureFormKind.NONE;
        structureFormTarget = null;
        structureFormParent = null;
        setFocused(null);
        for (EditorTextField field : List.of(structureIdField, structureTitleField)) {
            if (field == null) continue;
            field.hide();
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)) editorOverlays.close();
    }

    private boolean structureFormOpen() {
        return editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM);
    }

    private UiRect structureFormBounds() {
        return layout().centeredDialog(380, 260, 20, 126);
    }

    private UiRect structureFormCancelBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, form.bottom() - 28, form.centerX() - 4, form.bottom() - 8);
    }

    private UiRect structureFormDoneBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, form.bottom() - 28, form.right() - 12, form.bottom() - 8);
    }

    private void requestDelete(DeleteKind kind, ResourceLocation target, QuestBookDefinition book) {
        closeActiveEditorOverlay();
        deleteKind = kind;
        deleteTarget = target;
        deleteImpact = switch (kind) {
            case GROUP -> {
                Set<ResourceLocation> chapters = book.chapters().stream()
                        .filter(chapter -> chapter.groupId().equals(target)).map(ChapterDefinition::id)
                        .collect(java.util.stream.Collectors.toSet());
                long quests = book.chapters().stream().filter(chapter -> chapters.contains(chapter.id()))
                        .mapToLong(chapter -> chapter.quests().size()).sum();
                yield Component.translatable("screen.brnquest.editor.delete.group_impact", chapters.size(), quests).getString();
            }
            case CHAPTER -> {
                ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(target))
                        .findFirst().orElse(null);
                int quests = chapter == null ? 0 : chapter.quests().size();
                yield Component.translatable("screen.brnquest.editor.delete.chapter_impact", quests).getString();
            }
            case QUEST -> {
                long references = book.quests().stream().filter(quest -> quest.dependencies().contains(target)).count();
                yield Component.translatable("screen.brnquest.editor.delete.quest_impact", references).getString();
            }
            case NONE -> "";
        };
        editorOverlays.show(EditorOverlayHost.Kind.DELETE_CONFIRMATION);
    }

    private void renderDeleteConfirmation(GuiGraphics graphics) {
        EditorConfirmDialog.render(graphics, font, layout(),
                Component.translatable("screen.brnquest.editor.delete.warning"), Component.literal(deleteImpact),
                0xFFFFC07A, Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.delete.confirm"));
    }

    private boolean handleDeleteConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        EditorConfirmDialog.Action dialogAction = EditorConfirmDialog.actionAt(layout(), mouseX, mouseY);
        if (dialogAction == EditorConfirmDialog.Action.CANCEL) {
            deleteKind = DeleteKind.NONE;
            deleteTarget = null;
            editorOverlays.close();
            return true;
        }
        if (dialogAction == EditorConfirmDialog.Action.CONFIRM) {
            String action = switch (deleteKind) {
                case GROUP -> "DELETE_GROUP";
                case CHAPTER -> "DELETE_CHAPTER";
                case QUEST -> "DELETE_QUEST";
                case NONE -> "";
            };
            ResourceLocation target = deleteTarget;
            deleteKind = DeleteKind.NONE;
            deleteTarget = null;
            editorOverlays.close();
            if (!action.isEmpty()) {
                sendMutation(action, target, null, null, "", 0, 0, 0, List.of());
                if (action.equals("DELETE_QUEST")) {
                    editorSelection.remove(target);
                    if (target.equals(editorSelectedQuest)) editorSelectedQuest = null;
                    detailsOpen = false;
                }
            }
        }
        return true;
    }

    private void moveGroup(QuestBookDefinition book, ResourceLocation target, int delta) {
        List<ChapterGroupDefinition> groups = book.chapterGroups().stream()
                .sorted(java.util.Comparator.comparingInt(ChapterGroupDefinition::order)
                        .thenComparing(group -> group.id().toString())).toList();
        int index = java.util.stream.IntStream.range(0, groups.size())
                .filter(value -> groups.get(value).id().equals(target)).findFirst().orElse(-1);
        if (index >= 0) sendMutation("MOVE_GROUP", target, null, null, "",
                Math.max(0, Math.min(groups.size() - 1, index + delta)), 0, 0, List.of());
    }

    private void moveChapter(QuestBookDefinition book, ResourceLocation target, int delta) {
        ChapterDefinition source = book.chapters().stream().filter(value -> value.id().equals(target))
                .findFirst().orElse(null);
        if (source == null) return;
        List<ChapterDefinition> chapters = book.chapters().stream()
                .filter(value -> value.groupId().equals(source.groupId()))
                .sorted(java.util.Comparator.comparingInt(ChapterDefinition::order)
                        .thenComparing(chapter -> chapter.id().toString())).toList();
        int index = java.util.stream.IntStream.range(0, chapters.size())
                .filter(value -> chapters.get(value).id().equals(target)).findFirst().orElse(-1);
        if (index >= 0) sendMutation("MOVE_CHAPTER", target, null, null, "",
                Math.max(0, Math.min(chapters.size() - 1, index + delta)), 0, 0, List.of());
    }

    private void sendMutation(String action, ResourceLocation target, ResourceLocation parent,
                              ResourceLocation source, String title, int targetIndex,
                              double x, double y, List<AuthoringNetwork.PositionWire> positions) {
        ClientEditorState editor = ClientEditorState.get();
        if (!editor.editing() || editor.sessionId() == null || editor.bookId() == null
                || !editor.beginMutation()) return;
        AuthoringNetwork.mutate(new AuthoringNetwork.EditorMutationWire(editor.sessionId().toString(),
                editor.bookId().toString(), editor.draftRevision(), action,
                target == null ? "" : target.toString(), parent == null ? "" : parent.toString(),
                source == null ? "" : source.toString(), title, targetIndex, x, y, positions));
    }

    private ResourceLocation suggestId(QuestBookDefinition book, String stem) {
        String namespace = book.id().getNamespace();
        Set<ResourceLocation> ids = new java.util.HashSet<>();
        book.chapterGroups().forEach(value -> ids.add(value.id()));
        book.chapters().forEach(value -> ids.add(value.id()));
        book.quests().forEach(value -> ids.add(value.id()));
        for (int suffix = 1; suffix < 10_000; suffix++) {
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(namespace, stem + "_" + suffix);
            if (!ids.contains(candidate)) return candidate;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, stem + "_new");
    }

    private ResourceLocation defaultGroupId(QuestBookDefinition book) {
        ResourceLocation chapterId = currentChapterId();
        if (chapterId != null) {
            ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(chapterId))
                    .findFirst().orElse(null);
            if (chapter != null) return chapter.groupId();
        }
        return book.chapterGroups().stream().sorted(java.util.Comparator.comparingInt(ChapterGroupDefinition::order))
                .map(ChapterGroupDefinition::id).findFirst().orElse(null);
    }

    private UiRect navigationAddGroupBounds() {
        int top = height - BOTTOM_TOOLBAR_HEIGHT - 18;
        return new UiRect(2, top, NAV_RIGHT / 2, top + 16);
    }

    private UiRect navigationAddChapterBounds() {
        int top = height - BOTTOM_TOOLBAR_HEIGHT - 18;
        return new UiRect(NAV_RIGHT / 2 + 2, top, NAV_RIGHT - 2, top + 16);
    }

    private void renderEditorCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ClientEditorState.CatalogEntry> entries = editorCatalogEntries();
        UiRect bounds = editorCatalogBounds();
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xF0202632);
        int searchBottom = bounds.top() + EDITOR_CATALOG_SEARCH_HEIGHT;
        graphics.fill(bounds.left() + 2, bounds.top() + 2, bounds.right() - 2, searchBottom, 0xFF151A22);
        Component searchText = catalogFilter.isBlank()
                ? Component.translatable("screen.brnquest.editor.catalog.search_hint")
                : Component.literal("⌕ " + catalogFilter);
        graphics.drawString(font, searchText, bounds.left() + 6, bounds.top() + 6,
                catalogFilter.isBlank() ? 0xFF7F8B99 : 0xFFFFFFFF, false);
        if (entries.isEmpty()) {
            Component empty = Component.translatable(catalogFilter.isBlank()
                    ? "screen.brnquest.editor.catalog.current_missing" : "screen.brnquest.editor.catalog.no_match");
            graphics.drawCenteredString(font, empty, bounds.centerX(), searchBottom + 7, 0xFF9AA6B5);
            return;
        }
        int visibleRows = editorCatalogVisibleRows();
        int maximumScroll = Math.max(0, entries.size() - visibleRows);
        catalogScroll = Math.max(0, Math.min(maximumScroll, catalogScroll));
        for (int row = 0; row < visibleRows && row + catalogScroll < entries.size(); row++) {
            ClientEditorState.CatalogEntry entry = entries.get(row + catalogScroll);
            int top = searchBottom + 2 + row * EDITOR_CATALOG_ROW_HEIGHT;
            boolean selected = entry.bookId().equals(ClientEditorState.get().bookId());
            boolean hovered = mouseX >= bounds.left() + 2 && mouseX <= bounds.right() - 2
                    && mouseY >= top && mouseY < top + EDITOR_CATALOG_ROW_HEIGHT;
            graphics.fill(bounds.left() + 2, top, bounds.right() - 2, top + EDITOR_CATALOG_ROW_HEIGHT,
                    selected ? 0xFF385A72 : hovered ? 0xE0343D49 : 0xA02A323E);
            String title = entry.title().isBlank() ? entry.bookId().toString() : entry.title();
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(title, bounds.width() - 12)),
                    bounds.left() + 6, top + 4, 0xFFFFFFFF, false);
            String fullId = entry.bookId().toString();
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(fullId, bounds.width() - 12)),
                    bounds.left() + 6, top + 16, 0xFF9FB0C2, false);
            if (hovered) {
                hoveredComponentTooltip = List.of(Component.literal(title), Component.literal(fullId),
                        Component.translatable("screen.brnquest.editor.catalog.origin." +
                                entry.origin().name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
    }

    private boolean handleEditorChromeClick(double mouseX, double mouseY, int button,
                                            QuestBookDefinition displayedBook) {
        if (button != 0) return false;
        ClientEditorState editor = ClientEditorState.get();
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)) {
            UiRect catalogBounds = editorCatalogBounds();
            if (catalogBounds.contains(mouseX, mouseY)) {
                int rowsTop = catalogBounds.top() + EDITOR_CATALOG_SEARCH_HEIGHT + 2;
                if (mouseY < rowsTop) return true;
                int row = ((int) mouseY - rowsTop) / EDITOR_CATALOG_ROW_HEIGHT;
                int index = catalogScroll + row;
                // Hit testing consumes the exact filtered list that was rendered above.
                List<ClientEditorState.CatalogEntry> entries = editorCatalogEntries();
                if (row >= 0 && row < editorCatalogVisibleRows() && index >= 0 && index < entries.size()) {
                    ResourceLocation target = entries.get(index).bookId();
                    editorOverlays.close();
                    catalogFilter = "";
                    if (editor.hasLease()) {
                        if (!target.equals(editor.bookId())) {
                            if (editor.dirty()) requestDiscardConfirmation(target, false);
                            else closeEditorSession(target);
                        }
                    } else if (editor.beginOpen(target)) {
                        AuthoringNetwork.openSession(target);
                    }
                }
                return true;
            }
            editorOverlays.close();
            if (!editorTitleBounds().contains(mouseX, mouseY)) return true;
        }
        if (editor.allowed() && editorTitleBounds().contains(mouseX, mouseY)) {
            if (editor.busy()) return true;
            boolean opening = !editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG);
            closeActiveEditorOverlay();
            if (opening) {
                editorOverlays.show(EditorOverlayHost.Kind.CATALOG);
                catalogFilter = "";
                catalogScroll = 0;
            }
            return true;
        }
        if ((editor.allowed() || editor.hasLease() || editor.busy()) && editorButtonBounds().contains(mouseX, mouseY)) {
            if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)) editorOverlays.close();
            if (editor.busy()) return true;
            if (editor.hasLease()) {
                if (editor.dirty()) requestDiscardConfirmation(null, false);
                else closeEditorSession(null);
                return true;
            }
            ResourceLocation target = displayedBook.id();
            if (editor.beginOpen(target)) {
                AuthoringNetwork.openSession(target);
            } else if (editor.beginOpenCurrent(target)) {
                AuthoringNetwork.openCurrentSession(target);
            }
            return true;
        }
        if (editor.hasLease() && editorSaveButtonBounds().contains(mouseX, mouseY)) {
            editor.beginSave().ifPresent(request ->
                    AuthoringNetwork.saveSession(request.sessionId(), editor.bookId(), request.draftRevision()));
            return true;
        }
        return false;
    }

    private Component editorStatus() {
        ClientEditorState editor = ClientEditorState.get();
        return switch (editor.mode()) {
            case CATALOG_LOADING -> Component.translatable("screen.brnquest.editor.catalog.loading");
            case OPENING -> Component.translatable("screen.brnquest.editor.session.opening");
            case RECEIVING_DRAFT -> Component.translatable("screen.brnquest.editor.draft.loading");
            case MUTATING -> Component.translatable("screen.brnquest.editor.draft.mutating");
            case SAVING -> Component.translatable("screen.brnquest.editor.draft.saving");
            case EDITING -> {
                long seconds = editor.remainingLeaseTicks() / 20L;
                String time = "%d:%02d".formatted(seconds / 60L, seconds % 60L);
                yield Component.translatable(editor.dirty()
                        ? "screen.brnquest.editor.status.dirty" : "screen.brnquest.editor.status.saved", time);
            }
            case CLOSING -> Component.translatable("screen.brnquest.editor.session.closing");
            case ERROR -> editor.allowed()
                    ? Component.translatable("screen.brnquest.editor.status.error", editor.statusCode()) : null;
            case VIEW -> null;
        };
    }

    private EditorTextField editorField(String translationKey, int maximumLength) {
        EditorTextField field = new EditorTextField(font, Component.translatable(translationKey), maximumLength);
        return addRenderableWidget(field);
    }

    /** Uses the middle section of the existing detail drawer as a property form. */
    private void renderQuestPropertyEditor(GuiGraphics graphics, QuestDefinition quest) {
        int left = detailLeft() + 10;
        int fieldWidth = DETAIL_WIDTH - 24;
        int top = TOP_TOOLBAR_HEIGHT + 30;
        graphics.fill(detailLeft() + 4, TOP_TOOLBAR_HEIGHT + 4, width - 4,
                height - BOTTOM_TOOLBAR_HEIGHT - 4, 0xFF202632);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.heading"), left, top - 10,
                0xFFFFFFFF, false);
        positionEditorField(questTitleField, left, top + 10, fieldWidth);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.title"), left, top,
                0xFF9FB0C2, false);
        positionEditorField(questSubtitleField, left, top + 42, fieldWidth);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.subtitle"), left, top + 32,
                0xFF9FB0C2, false);
        positionEditorField(questDescriptionField, left, top + 74, fieldWidth);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.description"), left, top + 64,
                0xFF9FB0C2, false);
        graphics.drawString(font, Component.literal(quest.id().toString()), left, top + 100, 0xFF7F8B99, false);

        EditorButton.render(graphics, font, questEditorCancelBounds(), Component.translatable("gui.cancel"),
                0xFF343D49, 0xFFFFFFFF, 5);
        EditorButton.render(graphics, font, questEditorSaveBounds(), Component.translatable("gui.done"),
                ClientEditorState.get().busy() ? 0xFF343D49 : 0xFF385A72, 0xFFFFFFFF, 5);
    }

    private void positionEditorField(EditorTextField field, int x, int y, int fieldWidth) {
        field.show(new UiRect(x, y, x + fieldWidth, y + 18), !ClientEditorState.get().busy());
    }

    private void openQuestEditor(QuestDefinition quest) {
        if (ClientEditorState.get().busy()) return;
        questEditorOpen = true;
        questEditorQuestId = quest.id();
        questTitleField.setValue(quest.title());
        questSubtitleField.setValue(quest.subtitle());
        questDescriptionField.setValue(quest.description());
        setFocused(questTitleField);
    }

    private void submitQuestPropertyEdit() {
        ClientEditorState editor = ClientEditorState.get();
        if (editor.busy() || editor.sessionId() == null || editor.bookId() == null || questEditorQuestId == null) return;
        if (!editor.beginMutation()) return;
        AuthoringNetwork.updateQuest(editor.sessionId(), editor.bookId(), editor.draftRevision(), questEditorQuestId,
                questTitleField.getValue(), questSubtitleField.getValue(), questDescriptionField.getValue());
        closeQuestEditor();
    }

    private void closeQuestEditor() {
        questEditorOpen = false;
        questEditorQuestId = null;
        setFocused(null);
        for (EditorTextField field : List.of(questTitleField, questSubtitleField, questDescriptionField)) {
            if (field == null) continue;
            field.hide();
        }
    }

    private UiRect questPropertyButtonBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return new UiRect(detailLeft() + 10, bottom - 20, width - 10, bottom);
    }

    private UiRect questEditorCancelBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return new UiRect(detailLeft() + 10, bottom - 20, detailLeft() + 116, bottom);
    }

    private UiRect questEditorSaveBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return new UiRect(detailLeft() + 124, bottom - 20, width - 10, bottom);
    }

    private UiRect editorTitleBounds() {
        int available = Math.max(40, width - 8);
        int titleWidth = Math.min(260, available);
        int center = width / 2;
        return new UiRect(center - titleWidth / 2, 4, center + (titleWidth + 1) / 2,
                4 + EDITOR_CHROME_HEIGHT);
    }

    private UiRect editorButtonBounds() {
        int right = width - 4;
        return new UiRect(right - EDITOR_BUTTON_WIDTH, height - EDITOR_CHROME_HEIGHT - 4,
                right, height - 4);
    }

    private UiRect editorSaveButtonBounds() {
        UiRect exit = editorButtonBounds();
        return new UiRect(exit.left() - EDITOR_SAVE_BUTTON_WIDTH - 4, exit.top(), exit.left() - 4, exit.bottom());
    }

    private void requestDiscardConfirmation(ResourceLocation switchTarget, boolean closeScreen) {
        closeQuestEditor();
        closeActiveEditorOverlay();
        discardSwitchTarget = switchTarget;
        discardClosesScreen = closeScreen;
        editorOverlays.show(EditorOverlayHost.Kind.DISCARD_CONFIRMATION);
    }

    private void renderDiscardConfirmation(GuiGraphics graphics) {
        EditorConfirmDialog.render(graphics, font, layout(),
                Component.translatable("screen.brnquest.editor.discard.warning"), null, 0,
                Component.translatable("screen.brnquest.editor.discard.cancel"),
                Component.translatable("screen.brnquest.editor.discard.confirm"));
    }

    private boolean handleDiscardConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        EditorConfirmDialog.Action dialogAction = EditorConfirmDialog.actionAt(layout(), mouseX, mouseY);
        if (dialogAction == EditorConfirmDialog.Action.CANCEL) {
            editorOverlays.close();
            discardSwitchTarget = null;
            discardClosesScreen = false;
            return true;
        }
        if (dialogAction == EditorConfirmDialog.Action.CONFIRM) {
            ResourceLocation target = discardSwitchTarget;
            boolean closeScreen = discardClosesScreen;
            editorOverlays.close();
            discardSwitchTarget = null;
            discardClosesScreen = false;
            closeEditorSession(target);
            if (closeScreen) super.onClose();
        }
        return true;
    }

    private void closeEditorSession(ResourceLocation openAfterClose) {
        ClientEditorState.get().beginClose(openAfterClose).ifPresent(request ->
                AuthoringNetwork.closeSession(request.sessionId(), request.draftRevision()));
    }

    private UiRect editorCatalogBounds() {
        UiRect title = editorTitleBounds();
        int visibleRows = editorCatalogVisibleRows();
        int height = EDITOR_CATALOG_SEARCH_HEIGHT + Math.max(EDITOR_CATALOG_ROW_HEIGHT + 4,
                visibleRows * EDITOR_CATALOG_ROW_HEIGHT + 4);
        int availableWidth = Math.max(title.width(), width - 8);
        int catalogWidth = Math.min(420, availableWidth);
        int left = Math.max(4, title.centerX() - catalogWidth / 2);
        int right = Math.min(width - 4, left + catalogWidth);
        left = Math.max(4, right - catalogWidth);
        return new UiRect(left, title.bottom() + 2, right, title.bottom() + 2 + height);
    }

    private int editorCatalogVisibleRows() {
        int availableHeight = Math.max(EDITOR_CATALOG_ROW_HEIGHT,
                height - BOTTOM_TOOLBAR_HEIGHT - editorTitleBounds().bottom()
                        - EDITOR_CATALOG_SEARCH_HEIGHT - 6);
        int possible = Math.max(1, availableHeight / EDITOR_CATALOG_ROW_HEIGHT);
        return Math.min(8, Math.min(Math.max(1, editorCatalogEntries().size()), possible));
    }

    private boolean canSubmit(QuestDefinition quest, QuestStatus status) {
        if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) return false;
        return quest.tasks().stream().filter(task -> !task.optional()).allMatch(task ->
                ClientTaskPresentationRegistry.get(task.typeId()).acceptsQuestCompletionIntent(ApiViews.task(task))
                        || taskSatisfied(task, status));
    }

    private boolean taskSatisfied(TaskDefinition task, QuestStatus status) {
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(view);
        ItemStack displayedItem = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        long storedProgress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        return presentation.satisfied(new TaskPresentationContext(minecraft, view, status, storedProgress, displayedItem));
    }

    private String taskProgressText(TaskDefinition task, QuestStatus status, ItemStack expected, boolean satisfied) {
        long progress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        return ClientTaskPresentationRegistry.get(task.typeId()).progressText(
                new TaskPresentationContext(minecraft, ApiViews.task(task), status, progress, expected),
                satisfied).getString();
    }

    private QuestStatus status(QuestDefinition quest) {
        if (ClientEditorState.get().draft().isPresent()) return QuestStatus.LOCKED;
        return ClientQuestState.get().statuses().getOrDefault(quest.id().toString(), QuestStatus.LOCKED);
    }

    /** Resolves cross-chapter prerequisites from the immutable book already synchronized to the client. */
    private List<Component> dependencyTooltip(QuestDefinition quest) {
        var snapshot = displaySnapshot();
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

    /** Draws a small red notification dot above the node visual so pending rewards remain visible at every zoom level. */
    private void renderPendingRewardBadge(GuiGraphics graphics, int nodeX, int nodeY, int nodeSize) {
        int nodeRadius = nodeSize / 2;
        int badgeRadius = Math.max(2, Math.min(5, (nodeSize + 3) / 5));
        int centerX = nodeX + nodeRadius - 1;
        int centerY = nodeY - nodeRadius + 1;
        fillCircle(graphics, centerX, centerY, badgeRadius + 1, 0xFFFFFFFF);
        fillCircle(graphics, centerX, centerY, badgeRadius, 0xFFFF3038);
    }

    /** Rasterizes a compact filled circle without adding a texture dependency for one badge. */
    private void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int offsetY = -radius; offsetY <= radius; offsetY++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - offsetY * offsetY));
            graphics.fill(centerX - halfWidth, centerY + offsetY,
                    centerX + halfWidth + 1, centerY + offsetY + 1, color);
        }
    }

    private int statusColor(QuestStatus status) {
        return switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF72D88D;
            case AVAILABLE, ACTIVE -> 0xFFE4B75A;
            default -> 0xFF9AA6B5;
        };
    }

    private QuestDefinition selectedQuest() {
        QuestBookSnapshot snapshot = displaySnapshot();
        ResourceLocation selectedId = selectedQuestId();
        // Immutable snapshot maps reject null keys, which is a normal state before
        // the player has selected the first quest in view or editor mode.
        return snapshot == null || selectedId == null ? null : snapshot.quests().get(selectedId);
    }

    private ResourceLocation selectedQuestId() {
        return ClientEditorState.get().draft().isPresent() ? editorSelectedQuest : ClientQuestState.get().selected();
    }

    /** Draws the one winning hover surface at the final z-order, above details and navigation chrome. */
    private void renderDeferredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!hoveredComponentTooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, hoveredComponentTooltip, mouseX, mouseY);
        } else if (!hoveredDetailStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredDetailStack, mouseX, mouseY);
        } else if (hoveredDetailText != null) {
            graphics.renderTooltip(font, hoveredDetailText, mouseX, mouseY);
        }
    }

    private String questTitle(QuestDefinition quest) {
        if (quest.tasks().isEmpty()) return quest.title();
        TaskDefinition task = quest.tasks().getFirst();
        boolean generated = quest.title().isBlank() || quest.title().equals(quest.legacyId())
                || quest.title().equals(quest.id().getPath()) || ResourceLocation.tryParse(quest.title()) != null;
        if (!generated) return quest.title();
        String custom = task.config().getOrDefault("title", "");
        if (!custom.isBlank()) return custom;
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(view);
        ItemStack stack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        long stored = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        String fallback = presentation.title(new TaskPresentationContext(minecraft, view, status(quest), stored, stack)).getString();
        return fallback.equals(task.typeId().toString()) ? quest.title() : fallback;
    }

    private int detailStatusY(QuestDefinition quest) {
        int width = DETAIL_WIDTH - 38;
        int y = DETAIL_CONTENT_TOP - (int) Math.round(detailScroll);
        y += font.split(Component.literal(questTitle(quest)), width).size() * font.lineHeight + 3;
        if (!quest.subtitle().isBlank()) y += font.split(Component.literal(quest.subtitle()), width).size() * font.lineHeight + 4;
        return y;
    }

    private int detailTrackX(String pin) {
        // Reserve a clear gap from the close glyph so their hitboxes can never overlap.
        return detailLeft() + 10 + (DETAIL_WIDTH - 24) - font.width(pin) - 18;
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

    private int graphCoordinate(double coordinate) {
        return (int) Math.round(coordinate * QuestViewportMath.GRID_SCALE);
    }

    private int nodeGraphX(QuestDefinition quest) {
        DraftBookEditor.Position preview = dragPreview.get(quest.id());
        return graphCoordinate(preview == null ? quest.x() : preview.x());
    }

    private int nodeGraphY(QuestDefinition quest) {
        DraftBookEditor.Position preview = dragPreview.get(quest.id());
        return graphCoordinate(preview == null ? quest.y() : preview.y());
    }

    private QuestDefinition nodeAt(ChapterDefinition chapter, double graphMouseX, double graphMouseY) {
        int radius = NODE_BASE_SIZE / 2 + 2;
        for (int index = chapter.quests().size() - 1; index >= 0; index--) {
            QuestDefinition quest = chapter.quests().get(index);
            if (Math.abs(graphMouseX - nodeGraphX(quest)) <= radius
                    && Math.abs(graphMouseY - nodeGraphY(quest)) <= radius) return quest;
        }
        return null;
    }

    private void selectOnly(ResourceLocation questId) {
        editorSelection.clear();
        editorSelection.add(questId);
    }

    private void beginNodeDrag(double graphMouseX, double graphMouseY, QuestBookDefinition book) {
        Map<ResourceLocation, DraftBookEditor.Position> origins = new LinkedHashMap<>();
        for (QuestDefinition quest : book.quests()) {
            if (editorSelection.contains(quest.id())) {
                origins.put(quest.id(), new DraftBookEditor.Position(quest.x(), quest.y()));
            }
        }
        if (origins.isEmpty()) return;
        dragOrigins = Map.copyOf(origins);
        dragPreview.clear();
        dragPreview.putAll(origins);
        nodeDragStartX = graphMouseX;
        nodeDragStartY = graphMouseY;
        nodeDragging = true;
        dragging = false;
    }

    private void updateNodeDrag(double graphMouseX, double graphMouseY) {
        double deltaX = (graphMouseX - nodeDragStartX) / QuestViewportMath.GRID_SCALE;
        double deltaY = (graphMouseY - nodeDragStartY) / QuestViewportMath.GRID_SCALE;
        dragPreview.clear();
        dragOrigins.forEach((id, position) -> dragPreview.put(id,
                new DraftBookEditor.Position(position.x() + deltaX, position.y() + deltaY)));
    }

    private void commitNodeDrag() {
        nodeDragging = false;
        if (dragPreview.isEmpty() || dragPreview.equals(dragOrigins)) {
            dragPreview.clear();
            dragOrigins = Map.of();
            return;
        }
        List<AuthoringNetwork.PositionWire> positions = dragPreview.entrySet().stream()
                .map(entry -> new AuthoringNetwork.PositionWire(entry.getKey().toString(),
                        entry.getValue().x(), entry.getValue().y())).toList();
        sendMutation("MOVE_QUESTS", null, null, null, "", 0, 0, 0, positions);
        dragOrigins = Map.of();
    }

    private void reconcileDragPreview() {
        if (nodeDragging || dragPreview.isEmpty()) return;
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return;
        boolean synchronizedPositions = dragPreview.entrySet().stream().allMatch(entry -> {
            QuestDefinition quest = snapshot.quests().get(entry.getKey());
            return quest != null && Double.compare(quest.x(), entry.getValue().x()) == 0
                    && Double.compare(quest.y(), entry.getValue().y()) == 0;
        });
        if (synchronizedPositions || ClientEditorState.get().mode() == ClientEditorState.Mode.ERROR) {
            dragPreview.clear();
        }
    }

    private double graphX(double screenX) {
        return (screenX - graphOriginX()) / zoom;
    }

    private double graphY(double screenY) {
        return (screenY - graphOriginY()) / zoom;
    }

    private double graphOriginX() {
        return screenOriginX() + panX;
    }

    private double graphOriginY() {
        return contentCenterY() + panY;
    }

    private int contentCenterY() {
        return layout().contentCenterY();
    }

    private boolean isContentY(double y) {
        return layout().isContentY(y);
    }

    private int detailLeft() {
        return layout().detailLeft();
    }

    private int canvasLeft() {
        return layout().canvasLeft();
    }

    private QuestScreenLayout layout() {
        return new QuestScreenLayout(width, height, navigationCollapsed, detailsOpen);
    }

    private int screenOriginX() {
        return width / 2;
    }

    private void resolveRememberedChapter(List<ChapterDefinition> chapters) {
        if (rememberedChapterResolved) return;
        if (rememberedChapterId != null) {
            for (int index = 0; index < chapters.size(); index++) {
                if (chapters.get(index).id().equals(rememberedChapterId)) {
                    chapterIndex = index;
                    break;
                }
            }
        }
        rememberedChapterResolved = true;
    }

    private ResourceLocation currentChapterId() {
        var snapshot = displaySnapshot();
        if (snapshot == null) return rememberedChapterId;
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
        if (chapters.isEmpty()) return rememberedChapterId;
        return chapters.get(Math.min(chapterIndex, chapters.size() - 1)).id();
    }

    private QuestBookSnapshot displaySnapshot() {
        return ClientEditorState.get().draft().orElseGet(() -> ClientQuestState.get().book().orElse(null));
    }

    private List<ClientEditorState.CatalogEntry> editorCatalogEntries() {
        QuestBookSnapshot snapshot = displaySnapshot();
        ResourceLocation displayedBookId = snapshot == null ? null : snapshot.book().id();
        List<ClientEditorState.CatalogEntry> entries = ClientEditorState.get()
                .filteredCatalog(displayedBookId, catalogFilter);
        if (!catalogFilter.isBlank() || displayedBookId == null) return entries;
        // An unopened menu answers the common question first: which draft matches
        // the book currently on screen? Historical drafts remain searchable above.
        return entries.stream().filter(entry -> displayedBookId.equals(entry.bookId())).toList();
    }

    private void ensureViewportBook(ResourceLocation bookId) {
        if (bookId.equals(viewportBookId)) return;
        if (viewportBookId != null) saveViewport(rememberedChapterId);
        viewportBookId = bookId;
        QuestScreenSessionState.Snapshot remembered = QuestScreenSessionState.load(serverContextId, bookId);
        zoom = remembered.zoom();
        panX = QuestViewportMath.panForGraphCenter(remembered.centerX(), zoom);
        panY = QuestViewportMath.panForGraphCenter(remembered.centerY(), zoom);
        rememberedChapterId = remembered.chapterId();
        navigationCollapsed = remembered.navigationCollapsed();
        rememberedChapterResolved = false;
        navigationScroll = 0;
        detailScroll = 0;
        detailsOpen = false;
        editorSelectedQuest = null;
    }

    private void saveViewport(ResourceLocation chapterId) {
        QuestScreenSessionState.save(serverContextId, viewportBookId, chapterId,
                QuestViewportMath.graphCenterForPan(panX, zoom),
                QuestViewportMath.graphCenterForPan(panY, zoom), zoom, navigationCollapsed);
    }

    private String currentServerContext() {
        if (minecraft == null) return "unknown";
        var remote = minecraft.getCurrentServer();
        if (remote != null) return "remote:" + remote.ip;
        var integrated = minecraft.getSingleplayerServer();
        return integrated == null ? "unknown" : "integrated:" + integrated.getWorldData().getLevelName();
    }

    private int navigationContentHeight(QuestBookDefinition book) {
        return QuestPresentation.navigation(book).stream()
                .mapToInt(entry -> entry.group() != null ? NAV_GROUP_HEIGHT : NAV_CHAPTER_HEIGHT).sum();
    }

    private int navigationViewportHeight() {
        return Math.max(1, navigationListBottom() - NAV_TOP);
    }

    private int navigationListBottom() {
        return height - NAV_BOTTOM_MARGIN - (ClientEditorState.get().editing() ? 20 : 0);
    }

    private int detailViewportHeight() {
        return Math.max(1, height - DETAIL_CONTENT_TOP - DETAIL_CONTENT_BOTTOM_MARGIN);
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

    private record ContextAction(String action, String translationKey, boolean dangerous) {}

    private enum ContextKind { NONE, CANVAS, NODE, GROUP, CHAPTER }

    private enum DeleteKind { NONE, GROUP, CHAPTER, QUEST }

    private enum StructureFormKind {
        NONE("screen.brnquest.editor.structure.none", "item", false),
        ADD_GROUP("screen.brnquest.editor.structure.add_group", "group", true),
        RENAME_GROUP("screen.brnquest.editor.structure.rename_group", "group", false),
        ADD_CHAPTER("screen.brnquest.editor.structure.add_chapter", "chapter", true),
        RENAME_CHAPTER("screen.brnquest.editor.structure.rename_chapter", "chapter", false),
        ADD_QUEST("screen.brnquest.editor.structure.add_quest", "quest", true),
        COPY_QUEST("screen.brnquest.editor.structure.copy_quest", "quest_copy", true);

        private final String translationKey;
        private final String idStem;
        private final boolean createsStableId;

        StructureFormKind(String translationKey, String idStem, boolean createsStableId) {
            this.translationKey = translationKey;
            this.idStem = idStem;
            this.createsStableId = createsStableId;
        }

        String translationKey() { return translationKey; }
        String idStem() { return idStem; }
        boolean createsStableId() { return createsStableId; }
    }
}
