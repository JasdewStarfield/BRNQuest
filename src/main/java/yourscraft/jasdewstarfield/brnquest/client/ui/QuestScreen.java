package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.ContentAwareCache;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPickerList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPopupMenu;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorQuickTextDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorScrollbar;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

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
    private static final int EDITOR_PUBLISH_BUTTON_WIDTH = 92;
    private static final int EDITOR_HISTORY_BUTTON_WIDTH = 48;
    // Tab follows the toolbar from Save toward the buttons immediately to its left, then Exit.
    private static final int EDITOR_ACTION_SAVE = 0;
    private static final int EDITOR_ACTION_PUBLISH = 1;
    private static final int EDITOR_ACTION_REDO = 2;
    private static final int EDITOR_ACTION_UNDO = 3;
    private static final int EDITOR_ACTION_EXIT = 4;
    private static final int EDITOR_ACTION_COUNT = 5;
    private static final int DEPENDENCY_ROW_HEIGHT = 32;
    private static final int TYPED_ROW_HEIGHT = 38;
    private static final int MAX_TYPED_CONFIG_FIELDS = 8;

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
    private EditorTextField questIdField;
    private EditorTextField questIconField;
    private String questEditorOriginalIconItemId = "";
    private IconEditorMode questEditorIconMode = IconEditorMode.ITEM;
    private IconEditorMode questEditorOriginalIconMode = IconEditorMode.ITEM;
    private String questEditorItemIconValue = "";
    private String questEditorTextureIconValue = "";
    private EditorTextField structureIdField;
    private EditorTextField structureTitleField;
    private EditorTextField quickTextField;
    private QuickTextKind quickTextKind = QuickTextKind.NONE;
    private ResourceLocation quickTextQuestId;
    private Component quickTextIssue;
    private boolean questEditorOpen;
    private ResourceLocation questEditorQuestId;
    private Component questEditorMessage;
    private boolean dependencyEditorOpen;
    private ResourceLocation dependencyEditorQuestId;
    private int dependencyScroll;
    private String dependencyFilter = "";
    private int dependencyPickerScroll;
    private Component dependencyEditorMessage;
    private boolean typedEditorOpen;
    private TypedKind typedEditorKind = TypedKind.TASK;
    private ResourceLocation typedEditorQuestId;
    private int typedEditorScroll;
    private int typedTypePickerScroll;
    private Component typedEditorMessage;
    private boolean typedPropertyOpen;
    private ResourceLocation typedPropertyOriginalId;
    private ResourceLocation typedPropertyTypeId;
    private ConfigEditorSchema typedPropertySchema;
    private Map<String, String> typedPropertyOriginalConfig = Map.of();
    private Map<String, String> typedPropertyRawConfig = Map.of();
    private EditorTextField typedPropertyIdField;
    private EditorTextField typedPropertyClaimPolicyField;
    private final List<EditorTextField> typedPropertyConfigFields = new ArrayList<>();
    private boolean typedPropertyOptional;
    private boolean typedPropertyTeamReward;
    private boolean typedPropertyRenameArmed;
    private Component typedPropertyMessage;
    private boolean typedPropertySubmissionPending;
    private final Map<String, String> typedPropertyServerIssues = new LinkedHashMap<>();
    private ResourceLocation discardSwitchTarget;
    private boolean discardClosesScreen;
    private boolean editorChildScreenOpening;
    private final EditorOverlayHost editorOverlays = new EditorOverlayHost();
    private ContextKind editContextKind = ContextKind.NONE;
    private ResourceLocation editContextTarget;
    private int editContextX;
    private int editContextY;
    private int editContextSubmenu = -1;
    private int editContextTypedIndex = -1;
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
    private final ContentAwareCache<ResourceLocation, String, ItemStack> itemCache = new ContentAwareCache<>();
    private final List<RewardHitbox> rewardHitboxes = new ArrayList<>();
    private final List<TaskHitbox> taskHitboxes = new ArrayList<>();
    private final List<DependencyHitbox> dependencyHitboxes = new ArrayList<>();
    private final List<TypedEditorHitbox> typedEditorHitboxes = new ArrayList<>();
    private final List<QuickTextHitbox> quickTextHitboxes = new ArrayList<>();
    private ItemStack hoveredDetailStack = ItemStack.EMPTY;
    private Component hoveredDetailText;
    private List<Component> hoveredComponentTooltip = List.of();
    private AuthoringNetwork.PublishReviewWire publishReview;
    private int publishReviewScroll;
    private ResourceLocation recoveryCopyBookId;
    private int editorKeyboardFocus = -1;

    public QuestScreen() {
        super(Component.translatable("screen.brnquest.title"));
        QuestScreenSessionState.Snapshot defaults = QuestScreenSessionState.Snapshot.defaults();
        zoom = defaults.zoom();
        navigationCollapsed = defaults.navigationCollapsed();
    }

    @Override
    protected void init() {
        super.init();
        // setScreen(parent) initializes this same QuestScreen again after an item/raw-config child
        // closes. Re-register existing fields instead of replacing them, preserving the complete
        // in-progress form (stable ID, semantics, config values, cursor, and selection).
        questTitleField = reinitializeEditorField(questTitleField, "screen.brnquest.editor.quest.title", 256);
        questSubtitleField = reinitializeEditorField(questSubtitleField, "screen.brnquest.editor.quest.subtitle", 256);
        questDescriptionField = reinitializeEditorField(questDescriptionField,
                "screen.brnquest.editor.quest.description", 32_768);
        questIdField = reinitializeEditorField(questIdField, "screen.brnquest.editor.quest.id", 256);
        questIconField = reinitializeEditorField(questIconField, "screen.brnquest.editor.quest.icon", 256);
        structureIdField = reinitializeEditorField(structureIdField, "screen.brnquest.editor.structure.id", 256);
        structureTitleField = reinitializeEditorField(structureTitleField,
                "screen.brnquest.editor.structure.title", 256);
        quickTextField = reinitializeOverlayEditorField(quickTextField,
                "screen.brnquest.editor.quick_edit.input", 32_768);
        typedPropertyIdField = reinitializeEditorField(typedPropertyIdField,
                "screen.brnquest.editor.typed.property.id", 256);
        typedPropertyClaimPolicyField = reinitializeEditorField(typedPropertyClaimPolicyField,
                "screen.brnquest.editor.typed.property.claim_policy", 64);
        if (typedPropertyConfigFields.isEmpty()) {
            for (int index = 0; index < MAX_TYPED_CONFIG_FIELDS; index++) {
                typedPropertyConfigFields.add(editorField("screen.brnquest.editor.typed.property.config", 65_536));
            }
        } else {
            typedPropertyConfigFields.replaceAll(field -> reinitializeEditorField(field,
                    "screen.brnquest.editor.typed.property.config", 65_536));
        }
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
        if (editorChildScreenOpening) {
            // A normal setScreen call gives JEI its full Opening/Init lifecycle. The selector owns
            // this same QuestScreen as a suspended parent, so this removal must not close its lease.
            editorChildScreenOpening = false;
            super.removed();
            return;
        }
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
        if (dependencyEditorOpen && !editor.editing()) closeDependencyEditor();
        if ((!editor.editing() && switch (editorOverlays.active()) {
                case CONTEXT_MENU, STRUCTURE_FORM, DELETE_CONFIRMATION, QUEST_RENAME_CONFIRMATION,
                        QUICK_TEXT, PUBLISH_CONFIRMATION -> true;
                default -> false;
            }) || (!editor.allowed() && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG))) {
            closeActiveEditorOverlay();
        }
        editor.pollRenewRequest().ifPresent(request ->
                AuthoringNetwork.renewSession(request.sessionId(), request.draftRevision()));
        editor.pollPublishReview().ifPresent(review -> {
            closeActiveEditorOverlay();
            publishReview = review;
            publishReviewScroll = 0;
            editorOverlays.show(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION);
        });
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
        renderNavigation(graphics, snapshot.book(), selectedChapter, mouseX, mouseY);
        if (!structureFormOpen()) renderCanvas(graphics, selectedChapter, mouseX, mouseY);
        else graphics.fill(canvasLeft(), TOP_TOOLBAR_HEIGHT, detailsOpen ? detailLeft() : width,
                height - BOTTOM_TOOLBAR_HEIGHT, 0xD0151820);
        if (detailsOpen && !structureFormOpen()) renderDetails(graphics, mouseX, mouseY);
        renderEditorChrome(graphics, snapshot.book(), mouseX, mouseY);
        if (structureFormOpen()) renderStructureForm(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderDeferredTooltip(graphics, mouseX, mouseY);
    }

    private void renderNavigation(GuiGraphics graphics, QuestBookDefinition book, ChapterDefinition selectedChapter,
                                  int mouseX, int mouseY) {
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
                drawFittedString(graphics, Component.literal("▾ " + entry.group().title()),
                        NAV_LEFT + 4, y + 2, NAV_RIGHT - NAV_LEFT - 8, 0xFFB7C5D8, 0.75F);
                y += NAV_GROUP_HEIGHT;
                continue;
            }
            ChapterDefinition chapter = entry.chapter();
            int color = selectedChapter != null && chapter.id().equals(selectedChapter.id()) ? 0xFF4A6A88 : 0xE0262D38;
            graphics.fill(NAV_LEFT + 4, y, NAV_RIGHT, y + NAV_CHAPTER_HEIGHT, color);
            drawFittedString(graphics, Component.literal(chapter.title()), NAV_LEFT + 9, y + 3,
                    NAV_RIGHT - NAV_LEFT - 13, 0xFFFFFFFF, 0.75F);
            y += NAV_CHAPTER_HEIGHT;
        }
        graphics.disableScissor();
        EditorScrollbar.render(graphics, NAV_RIGHT + 2, NAV_TOP, navigationListBottom(),
                navigationContentHeight, viewportHeight, navigationScroll);
        if (ClientEditorState.get().editing()) renderNavigationEditorButtons(graphics, mouseX, mouseY);
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
                    // Large chapters commonly contain off-screen subgraphs. Skip a
                    // dependency only when both endpoints are outside the same side;
                    // crossing lines remain visible while distant work is culled.
                    if (parent != null && dependencyMayBeVisible(parent, quest,
                            graphLeft, graphRight, graphTop, graphBottom)) {
                        renderDependency(graphics, parent, quest);
                    }
                }
            }
            for (QuestDefinition quest : chapter.quests()) {
                renderNode(graphics, quest, graphLeft, graphRight, graphTop, graphBottom, graphMouseX, graphMouseY);
            }
        }
        graphics.pose().popPose();
        graphics.disableScissor();
    }

    static boolean dependencyMayBeVisible(QuestDefinition first, QuestDefinition second,
                                          double left, double right, double top, double bottom) {
        double margin = NODE_BASE_SIZE;
        double firstX = first.x() * QuestViewportMath.GRID_SCALE;
        double firstY = first.y() * QuestViewportMath.GRID_SCALE;
        double secondX = second.x() * QuestViewportMath.GRID_SCALE;
        double secondY = second.y() * QuestViewportMath.GRID_SCALE;
        return !(firstX < left - margin && secondX < left - margin)
                && !(firstX > right + margin && secondX > right + margin)
                && !(firstY < top - margin && secondY < top - margin)
                && !(firstY > bottom + margin && secondY > bottom + margin);
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
        if (visual.kind() == QuestPresentation.VisualKind.TEXTURE) {
            QuestIconValue.textureId(visual.value()).ifPresent(texture ->
                    graphics.blit(texture, x - size / 2, y - size / 2, 0.0F, 0.0F,
                            size, size, size, size));
            return;
        }
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
        quickTextHitboxes.clear();
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
            renderQuestPropertyEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        if (editing && dependencyEditorOpen && quest.id().equals(dependencyEditorQuestId)) {
            detailContentHeight = 0;
            detailScroll = 0;
            renderDependencyEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        if (editing && typedEditorOpen && quest.id().equals(typedEditorQuestId)) {
            detailContentHeight = 0;
            detailScroll = 0;
            renderTypedEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        int contentLeft = left + 10;
        int contentWidth = DETAIL_WIDTH - 24;
        int viewportHeight = detailViewportHeight();
        int y = DETAIL_CONTENT_TOP - (int) Math.round(detailScroll);
        graphics.enableScissor(left + 1, DETAIL_CONTENT_TOP, width - 10, height - DETAIL_CONTENT_BOTTOM_MARGIN);
        int titleTop = y;
        y = drawWrapped(graphics, questTitle(quest), contentLeft, y, contentWidth - 14, 0xFFFFFF);
        if (editing) addQuickTextHitbox(QuickTextKind.TITLE, contentLeft, titleTop, contentWidth - 14, y);
        y += 3;

        if (editing || !quest.subtitle().isBlank()) {
            int subtitleTop = y;
            String subtitle = quest.subtitle().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_subtitle").getString()
                    : quest.subtitle();
            y = drawWrapped(graphics, subtitle, contentLeft, y, contentWidth,
                    quest.subtitle().isBlank() ? 0xFF718096 : 0xFFB7C5D8);
            if (editing) addQuickTextHitbox(QuickTextKind.SUBTITLE, contentLeft, subtitleTop, contentWidth, y);
            y += 4;
        }

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

        if (editing || !quest.description().isBlank()) {
            int descriptionTop = y;
            String description = quest.description().isBlank()
                    ? Component.translatable("screen.brnquest.editor.quick_edit.empty_description").getString()
                    : quest.description();
            y = drawWrapped(graphics, description, contentLeft, y, contentWidth,
                    quest.description().isBlank() ? 0xFF718096 : 0xFFE1E6EE);
            if (editing) addQuickTextHitbox(QuickTextKind.DESCRIPTION,
                    contentLeft, descriptionTop, contentWidth, y);
            y += 8;
        }

        if (editing) {
            for (QuickTextHitbox hitbox : quickTextHitboxes) {
                if (hitbox.bounds().contains(mouseX, mouseY)) {
                    hoveredComponentTooltip = List.of(Component.translatable(
                            "screen.brnquest.editor.quick_edit.hint"));
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
            renderDetailEditorEntry(graphics, questPropertyButtonBounds(), "✎",
                    "screen.brnquest.editor.quest.properties", mouseX, mouseY);
            renderDetailEditorEntry(graphics, questTaskButtonBounds(), "✓",
                    "screen.brnquest.editor.typed.tasks", mouseX, mouseY);
            renderDetailEditorEntry(graphics, questRewardButtonBounds(), "★",
                    "screen.brnquest.editor.typed.rewards", mouseX, mouseY);
            renderDetailEditorEntry(graphics, questDependencyButtonBounds(), "→",
                    "screen.brnquest.editor.dependency.edit", mouseX, mouseY);
        }

        int visibleStatusY = statusTop;
        if (!editing && status == QuestStatus.LOCKED && mouseX >= contentLeft && mouseX <= contentLeft + statusWidth
                && mouseY >= visibleStatusY && mouseY <= visibleStatusY + font.lineHeight
                && visibleStatusY >= DETAIL_CONTENT_TOP && visibleStatusY < height - DETAIL_CONTENT_BOTTOM_MARGIN) {
            hoveredComponentTooltip = dependencyTooltip(quest);
        }

    }

    /** Detail entry points keep explanatory text while adding a fast-scanning icon cue. */
    private void renderDetailEditorEntry(GuiGraphics graphics, UiRect bounds, String glyph,
                                         String translationKey, int mouseX, int mouseY) {
        Component label = Component.translatable(translationKey);
        renderEditorActionButton(graphics, bounds, EditorButton.Definition.iconAndText(
                        label, label, EditorIcon.glyph(Component.literal(glyph))),
                true, -1, EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    /** Registers only the visible portion, so scrolled-away text cannot capture a right click. */
    private void addQuickTextHitbox(QuickTextKind kind, int left, int top, int width, int bottom) {
        int visibleTop = Math.max(top, DETAIL_CONTENT_TOP);
        int visibleBottom = Math.min(bottom, height - DETAIL_CONTENT_BOTTOM_MARGIN);
        if (visibleBottom > visibleTop) {
            quickTextHitboxes.add(new QuickTextHitbox(kind,
                    new UiRect(left, visibleTop, left + width, visibleBottom)));
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
            case QUEST_RENAME_CONFIRMATION -> {
                return handleQuestRenameConfirmationClick(mouseX, mouseY, button);
            }
            case PUBLISH_CONFIRMATION -> {
                return handlePublishConfirmationClick(mouseX, mouseY, button);
            }
            case CONFLICT_RECOVERY -> {
                return handleConflictRecoveryClick(mouseX, mouseY, button);
            }
            case CONTEXT_MENU -> {
                return handleEditContextClick(mouseX, mouseY, button, snapshot.book());
            }
            case DEPENDENCY_PICKER -> {
                return handleDependencyPickerClick(mouseX, mouseY, button);
            }
            case TYPED_TYPE_PICKER -> {
                return handleTypedTypePickerClick(mouseX, mouseY, button);
            }
            case QUICK_TEXT -> {
                return handleQuickTextEditorClick(mouseX, mouseY, button);
            }
            default -> { }
        }
        if (handleEditorChromeClick(mouseX, mouseY, button, snapshot.book())) return true;

        if (dependencyEditorOpen && mouseX >= detailLeft()) {
            return handleDependencyEditorClick(mouseX, mouseY, button);
        }

        if (typedEditorOpen && mouseX >= detailLeft()) {
            return handleTypedEditorClick(mouseX, mouseY, button);
        }

        if (questEditorOpen && mouseX >= detailLeft()) {
            if (button == 0 && questIconModeBounds().contains(mouseX, mouseY)) {
                rememberCurrentIconInput();
                questEditorIconMode = questEditorIconMode == IconEditorMode.ITEM
                        ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
                questIconField.setValue(questEditorIconMode == IconEditorMode.ITEM
                        ? questEditorItemIconValue : questEditorTextureIconValue);
                setFocused(questIconField);
                return true;
            }
            if (button == 0 && questEditorIconMode == IconEditorMode.ITEM
                    && questIconPickerBounds().contains(mouseX, mouseY)
                    && !ClientEditorState.get().busy()) {
                openQuestIconItemSelector();
                return true;
            }
            if (button == 0 && questEditorSaveBounds().contains(mouseX, mouseY)) {
                prepareQuestPropertyEdit();
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
            closeQuestEditingPanels();
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
            closeQuestEditingPanels();
            return true;
        }
        if (detailsOpen && selected != null) {
            if (ClientEditorState.get().editing() && button == 1) {
                for (QuickTextHitbox hitbox : quickTextHitboxes) {
                    if (hitbox.bounds().contains(mouseX, mouseY)) {
                        openQuickTextEditor(selected, hitbox.kind());
                        return true;
                    }
                }
            }
            if (ClientEditorState.get().editing() && questDependencyButtonBounds().contains(mouseX, mouseY)) {
                openDependencyEditor(selected);
                return true;
            }
            if (ClientEditorState.get().editing() && questTaskButtonBounds().contains(mouseX, mouseY)) {
                openTypedEditor(selected, TypedKind.TASK);
                return true;
            }
            if (ClientEditorState.get().editing() && questRewardButtonBounds().contains(mouseX, mouseY)) {
                openTypedEditor(selected, TypedKind.REWARD);
                return true;
            }
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
                    closeQuestEditingPanels();
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
                    || editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)
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
                    || editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)
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
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION) && publishReview != null) {
            EditorPublishReviewPanel.Layout reviewLayout = EditorPublishReviewPanel.layout(layout());
            int maximum = EditorPublishReviewPanel.maximumScroll(reviewLayout, publishReviewRowCount());
            publishReviewScroll = Math.max(0, Math.min(maximum,
                    publishReviewScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)) {
            int maximum = Math.max(0, dependencyCandidates().size()
                    - EditorPickerList.visibleRows(dependencyPickerBounds()));
            dependencyPickerScroll = Math.max(0, Math.min(maximum,
                    dependencyPickerScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.TYPED_TYPE_PICKER)) {
            int maximum = Math.max(0, typedTypeCandidates().size()
                    - EditorPickerList.visibleRows(typedTypePickerBounds()));
            typedTypePickerScroll = Math.max(0, Math.min(maximum,
                    typedTypePickerScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG) && editorCatalogBounds().contains(x, y)) {
            int visibleRows = editorCatalogVisibleRows();
            int maximum = Math.max(0, editorCatalogEntries().size() - visibleRows);
            catalogScroll = Math.max(0, Math.min(maximum, catalogScroll - (int) Math.signum(vertical)));
            return true;
        }
        // Any open editor overlay owns wheel input, even outside its visible bounds.
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE) return true;
        if (dependencyEditorOpen && x >= detailLeft()) {
            QuestDefinition quest = selectedQuest();
            int maximum = quest == null ? 0 : Math.max(0,
                    quest.dependencies().size() - dependencyVisibleRows());
            dependencyScroll = Math.max(0, Math.min(maximum,
                    dependencyScroll - (int) Math.signum(vertical)));
            return true;
        }
        if (typedEditorOpen && x >= detailLeft()) {
            QuestDefinition quest = selectedQuest();
            int count = quest == null ? 0 : typedEditorKind.size(quest);
            int maximum = Math.max(0, count - typedEditorVisibleRows());
            typedEditorScroll = Math.max(0, Math.min(maximum,
                    typedEditorScroll - (int) Math.signum(vertical)));
            return true;
        }
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
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)
                && (keyCode == 257 || keyCode == 335)) {
            submitQuickTextEdit();
            return true;
        }
        if (keyCode == 256 && questEditorOpen) {
            closeQuestEditor();
            return true;
        }
        if (keyCode == 256 && dependencyEditorOpen) {
            closeDependencyEditor();
            return true;
        }
        if (keyCode == 256 && typedPropertyOpen) {
            closeTypedPropertyEditor();
            return true;
        }
        if (keyCode == 256 && typedEditorOpen) {
            closeTypedEditor();
            return true;
        }
        if (keyCode == 259 && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)
                && !catalogFilter.isEmpty()) {
            catalogFilter = catalogFilter.substring(0, catalogFilter.length() - 1);
            catalogScroll = 0;
            return true;
        }
        if (keyCode == 259 && editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)
                && !dependencyFilter.isEmpty()) {
            dependencyFilter = dependencyFilter.substring(0, dependencyFilter.length() - 1);
            dependencyPickerScroll = 0;
            return true;
        }
        if (editorKeyboardSurfaceReady() && keyCode == 258) {
            editorKeyboardFocus = Math.floorMod(
                    editorKeyboardFocus + (hasShiftDown() ? -1 : 1), EDITOR_ACTION_COUNT);
            return true;
        }
        if (editorKeyboardSurfaceReady() && (keyCode == 257 || keyCode == 335)
                && editorKeyboardFocus >= 0) {
            activateEditorKeyboardAction(editorKeyboardFocus);
            return true;
        }
        if (editorKeyboardSurfaceReady() && hasControlDown()) {
            if (keyCode >= 49 && keyCode <= 52) {
                openSelectedQuestEditor(keyCode - 49);
                return true;
            }
            if (keyCode == 78) {
                ResourceLocation chapterId = currentChapterId();
                if (chapterId != null) {
                    openStructureForm(StructureFormKind.ADD_QUEST, null, chapterId,
                            graphX(width / 2.0) / QuestViewportMath.GRID_SCALE,
                            graphY(contentCenterY()) / QuestViewportMath.GRID_SCALE);
                }
                return true;
            }
            if (keyCode == 83) {
                activateEditorKeyboardAction(EDITOR_ACTION_SAVE);
                return true;
            }
            if (keyCode == 90 && hasShiftDown() || keyCode == 89) {
                activateEditorKeyboardAction(EDITOR_ACTION_REDO);
                return true;
            }
            if (keyCode == 90) {
                activateEditorKeyboardAction(EDITOR_ACTION_UNDO);
                return true;
            }
            if (keyCode == 80 && hasShiftDown()) {
                activateEditorKeyboardAction(EDITOR_ACTION_PUBLISH);
                return true;
            }
        }
        if (editorKeyboardSurfaceReady() && keyCode == 261 && editorSelectedQuest != null) {
            QuestBookSnapshot snapshot = displaySnapshot();
            if (snapshot != null && snapshot.quests().containsKey(editorSelectedQuest)) {
                requestDelete(DeleteKind.QUEST, editorSelectedQuest, snapshot.book());
            }
            return true;
        }
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) return true;
        if (keyCode == 256 && detailsOpen) {
            detailsOpen = false;
            detailScroll = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public Component getNarrationMessage() {
        ClientEditorState editor = ClientEditorState.get();
        Component status = editorStatus();
        if (!editor.hasLease()) return super.getNarrationMessage();
        Component action = editorKeyboardFocus < 0 ? Component.translatable("screen.brnquest.editor.keyboard.help")
                : Component.translatable("screen.brnquest.editor.keyboard.focus",
                Component.translatable(editorKeyboardActionKey(editorKeyboardFocus)));
        return Component.translatable("screen.brnquest.editor.narration", getTitle(),
                status == null ? "" : status, action);
    }

    private boolean editorKeyboardSurfaceReady() {
        return ClientEditorState.get().hasLease() && !ClientEditorState.get().busy()
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen && !typedPropertyOpen
                && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox);
    }

    private void activateEditorKeyboardAction(int action) {
        ClientEditorState editor = ClientEditorState.get();
        switch (action) {
            case EDITOR_ACTION_SAVE -> editor.beginSave().ifPresent(request -> AuthoringNetwork.saveSession(
                    request.sessionId(), editor.bookId(), request.draftRevision()));
            case EDITOR_ACTION_PUBLISH -> editor.beginPublishReview().ifPresent(request -> AuthoringNetwork.reviewPublish(
                    request.sessionId(), editor.bookId(), request.draftRevision()));
            case EDITOR_ACTION_REDO -> editor.beginRedo().ifPresent(request -> AuthoringNetwork.history(
                    request.sessionId(), editor.bookId(), request.draftRevision(), true));
            case EDITOR_ACTION_UNDO -> editor.beginUndo().ifPresent(request -> AuthoringNetwork.history(
                    request.sessionId(), editor.bookId(), request.draftRevision(), false));
            case EDITOR_ACTION_EXIT -> {
                if (editor.dirty()) requestDiscardConfirmation(null, false);
                else closeEditorSession(null);
            }
            default -> { }
        }
    }

    private void openSelectedQuestEditor(int panel) {
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition quest = snapshot == null || editorSelectedQuest == null
                ? null : snapshot.quests().get(editorSelectedQuest);
        if (quest == null) return;
        detailsOpen = true;
        switch (panel) {
            case 0 -> openQuestEditor(quest);
            case 1 -> openTypedEditor(quest, TypedKind.TASK);
            case 2 -> openTypedEditor(quest, TypedKind.REWARD);
            case 3 -> openDependencyEditor(quest);
            default -> { }
        }
    }

    private static String editorKeyboardActionKey(int action) {
        return switch (action) {
            case EDITOR_ACTION_SAVE -> "screen.brnquest.editor.save";
            case EDITOR_ACTION_PUBLISH -> "screen.brnquest.editor.publish";
            case EDITOR_ACTION_REDO -> "screen.brnquest.editor.redo.action";
            case EDITOR_ACTION_UNDO -> "screen.brnquest.editor.undo.action";
            default -> "screen.brnquest.editor.exit";
        };
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)
                && !Character.isISOControl(codePoint) && catalogFilter.length() < 48) {
            catalogFilter += codePoint;
            catalogScroll = 0;
            return true;
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)
                && !Character.isISOControl(codePoint) && dependencyFilter.length() < 48) {
            dependencyFilter += codePoint;
            dependencyPickerScroll = 0;
            return true;
        }
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) return true;
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
            renderEditorActionButton(graphics, button, EditorButton.Definition.text(label, active
                            ? Component.translatable("screen.brnquest.editor.exit.tooltip") : null),
                    true, active ? EDITOR_ACTION_EXIT : -1,
                    active ? EditorButton.Tone.PRIMARY : EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }

        if (editor.hasLease()) {
            UiRect save = editorSaveButtonBounds();
            boolean enabled = editor.dirty() && !editor.busy();
            Component label = Component.translatable(editor.dirty()
                    ? "screen.brnquest.editor.save" : "screen.brnquest.editor.saved");
            renderEditorActionButton(graphics, save, EditorButton.Definition.text(label,
                            Component.translatable("screen.brnquest.editor.save.tooltip")),
                    enabled, EDITOR_ACTION_SAVE, EditorButton.Tone.SUCCESS, mouseX, mouseY);

            UiRect publish = editorPublishButtonBounds();
            boolean publishEnabled = !editor.busy() && !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen
                    && !structureFormOpen() && editorOverlays.active() == EditorOverlayHost.Kind.NONE;
            renderEditorActionButton(graphics, publish, EditorButton.Definition.text(
                    Component.translatable("screen.brnquest.editor.publish"),
                            Component.translatable("screen.brnquest.editor.publish.tooltip")),
                    publishEnabled, EDITOR_ACTION_PUBLISH, EditorButton.Tone.WARNING, mouseX, mouseY);

            boolean historySurfaceReady = editorHistorySurfaceReady();
            UiRect redo = editorRedoButtonBounds();
            boolean redoEnabled = historySurfaceReady && editor.canRedo();
            renderEditorActionButton(graphics, redo, EditorButton.Definition.text(
                            Component.translatable("screen.brnquest.editor.redo", editor.redoSteps()),
                            Component.translatable("screen.brnquest.editor.redo.tooltip")),
                    redoEnabled, EDITOR_ACTION_REDO, EditorButton.Tone.PRIMARY, mouseX, mouseY);

            UiRect undo = editorUndoButtonBounds();
            boolean undoEnabled = historySurfaceReady && editor.canUndo();
            renderEditorActionButton(graphics, undo, EditorButton.Definition.text(
                            Component.translatable("screen.brnquest.editor.undo", editor.undoSteps()),
                            Component.translatable("screen.brnquest.editor.undo.tooltip")),
                    undoEnabled, EDITOR_ACTION_UNDO, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        }

        Component status = editorStatus();
        if (status != null) {
            UiRect leadingButton = editor.hasLease() ? editorUndoButtonBounds() : editorButtonBounds();
            // The navigation drawer occupies only the middle region. Measuring from
            // canvasLeft incorrectly collapsed bottom-bar errors to the word "Editor".
            int maximumWidth = layout().bottomStatusMaximumWidth(leadingButton.left());
            String statusText = font.plainSubstrByWidth(status.getString(), maximumWidth - 10);
            int statusWidth = Math.min(maximumWidth, font.width(statusText) + 10);
            int left = 4;
            int top = height - EDITOR_CHROME_HEIGHT - 4;
            graphics.fill(left, top, left + statusWidth, top + EDITOR_CHROME_HEIGHT, 0xE0202632);
            graphics.drawString(font, Component.literal(statusText), left + 5, top + 4,
                    editor.mode() == ClientEditorState.Mode.ERROR ? 0xFFFF8B8B : 0xFFB7C5D8, false);
            if (editor.mode() == ClientEditorState.Mode.ERROR
                    && new UiRect(left, top, left + statusWidth, top + EDITOR_CHROME_HEIGHT).contains(mouseX, mouseY)) {
                hoveredComponentTooltip = List.of(Component.literal(editor.statusCode()),
                        Component.literal(editor.statusMessage()));
            }
        }

        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE) {
            // Opaque overlays own hover state; hidden canvas/detail rows must not
            // leak a tooltip through the picker, menu, or confirmation surface.
            hoveredDetailStack = ItemStack.EMPTY;
            hoveredDetailText = null;
            hoveredComponentTooltip = List.of();
        }
        if (editor.recoverableConflict()
                && !editorOverlays.isOpen(EditorOverlayHost.Kind.CONFLICT_RECOVERY)) {
            closeQuestEditingPanels();
            closeActiveEditorOverlay();
            recoveryCopyBookId = recoveryCopyId(editor.bookId());
            editorOverlays.show(EditorOverlayHost.Kind.CONFLICT_RECOVERY);
        }
        switch (editorOverlays.active()) {
            case CATALOG -> {
                if (editor.allowed()) renderEditorCatalog(graphics, mouseX, mouseY);
            }
            case CONTEXT_MENU -> renderEditContextMenu(graphics, mouseX, mouseY);
            case DEPENDENCY_PICKER -> renderDependencyPicker(graphics, mouseX, mouseY);
            case TYPED_TYPE_PICKER -> renderTypedTypePicker(graphics, mouseX, mouseY);
            case QUICK_TEXT -> renderQuickTextEditor(graphics, mouseX, mouseY);
            case DISCARD_CONFIRMATION -> renderDiscardConfirmation(graphics, mouseX, mouseY);
            case DELETE_CONFIRMATION -> renderDeleteConfirmation(graphics, mouseX, mouseY);
            case QUEST_RENAME_CONFIRMATION -> renderQuestRenameConfirmation(graphics, mouseX, mouseY);
            case PUBLISH_CONFIRMATION -> renderPublishConfirmation(graphics, mouseX, mouseY);
            case CONFLICT_RECOVERY -> renderConflictRecovery(graphics, mouseX, mouseY);
            default -> { }
        }
        graphics.pose().popPose();
    }

    /** One definition now owns text, Tooltip, hover, disabled, and focus presentation. */
    private void renderEditorActionButton(GuiGraphics graphics, UiRect bounds, EditorButton.Definition definition,
                                          boolean enabled, int keyboardAction, EditorButton.Tone tone,
                                          int mouseX, int mouseY) {
        boolean hovered = EditorButton.renderInteractive(graphics, font, bounds, definition, enabled,
                keyboardAction >= 0 && editorKeyboardFocus == keyboardAction, tone, mouseX, mouseY);
        if (hovered && !definition.tooltip().isEmpty()) {
            hoveredComponentTooltip = definition.tooltip();
        }
    }

    /** Text actions use the same semantic rendering path as icon-backed actions. */
    private void renderEditorTextButton(GuiGraphics graphics, UiRect bounds, Component label,
                                        Component tooltip, boolean enabled, EditorButton.Tone tone,
                                        int mouseX, int mouseY) {
        renderEditorActionButton(graphics, bounds, EditorButton.Definition.text(label, tooltip),
                enabled, -1, tone, mouseX, mouseY);
    }

    /** Compact icon buttons retain a localized semantic label for Tooltip and future narration. */
    private void renderEditorIconButton(GuiGraphics graphics, UiRect bounds, Component glyph,
                                        Component accessibleLabel, boolean enabled, boolean dangerous,
                                        int mouseX, int mouseY) {
        EditorButton.Definition definition = EditorButton.Definition.iconOnly(
                accessibleLabel, accessibleLabel, EditorIcon.glyph(glyph));
        renderEditorActionButton(graphics, bounds, definition, enabled, -1,
                dangerous ? EditorButton.Tone.DANGER : EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    private void renderNavigationEditorButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect group = navigationAddGroupBounds();
        UiRect chapter = navigationAddChapterBounds();
        Component addGroup = Component.translatable("screen.brnquest.editor.group.add");
        Component addChapter = Component.translatable("screen.brnquest.editor.chapter.add");
        renderEditorActionButton(graphics, group, EditorButton.Definition.iconAndText(
                        addGroup, addGroup, EditorIcon.glyph(Component.literal("+"))),
                true, -1, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        renderEditorActionButton(graphics, chapter, EditorButton.Definition.iconAndText(
                        addChapter, addChapter, EditorIcon.glyph(Component.literal("+"))),
                defaultGroupId(displaySnapshot().book()) != null, -1,
                EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    private void renderEditContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        List<EditorPopupMenu.Entry> entries = contextMenuEntries();
        UiRect root = editContextBounds(entries.size());
        EditorPopupMenu.CascadeLayout current = editContextLayout(entries);
        editContextSubmenu = EditorPopupMenu.resolveSubmenu(root, entries, current, mouseX, mouseY);
        EditorPopupMenu.render(graphics, font, editContextLayout(entries), entries, mouseX, mouseY);
    }

    private boolean handleEditContextClick(double mouseX, double mouseY, int button, QuestBookDefinition book) {
        if (button != 0) {
            closeEditContext();
            return true;
        }
        List<EditorPopupMenu.Entry> entries = contextMenuEntries();
        EditorPopupMenu.CascadeLayout menu = editContextLayout(entries);
        if (!menu.contains(mouseX, mouseY)) {
            closeEditContext();
            return true;
        }
        String action = EditorPopupMenu.actionAt(menu, entries, mouseX, mouseY);
        // Clicking the parent row only opens its submenu; no editor intent is dispatched yet.
        if (action.isEmpty()) return true;
        ResourceLocation target = editContextTarget;
        int typedIndex = editContextTypedIndex;
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
            case "EDIT_TYPED", "COPY_TYPED", "MOVE_TYPED_UP", "MOVE_TYPED_DOWN",
                    "COPY_TYPED_ID", "COPY_TYPED_TYPE", "DELETE_TYPED" ->
                    performTypedContextAction(action, target, typedIndex, book);
            case "COPY_DEPENDENCY_ID" -> {
                if (target != null) copyTechnicalId(target.toString());
            }
            case "REMOVE_DEPENDENCY" -> {
                if (dependencyEditorQuestId != null) {
                    sendMutation("REMOVE_DEPENDENCY", dependencyEditorQuestId, null,
                            target, "", 0, 0, 0, List.of());
                    dependencyEditorMessage = null;
                }
            }
            default -> { }
        }
        return true;
    }

    private List<EditorPopupMenu.Entry> contextMenuEntries() {
        return switch (editContextKind) {
            case CANVAS -> EditorPopupMenu.menu(menu -> menu.action("ADD_QUEST",
                    Component.translatable("screen.brnquest.editor.context.add_quest"), false));
            case NODE -> EditorPopupMenu.menu(menu -> menu
                    .action("COPY_QUEST", Component.translatable("screen.brnquest.editor.context.copy_quest"), false)
                    .action("DELETE_QUEST", Component.translatable(
                            "screen.brnquest.editor.context.delete_quest"), true));
            case GROUP -> EditorPopupMenu.menu(menu -> menu
                    .action("ADD_CHAPTER", Component.translatable(
                            "screen.brnquest.editor.context.add_chapter"), false)
                    .action("RENAME_GROUP", Component.translatable("screen.brnquest.editor.context.rename"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action("MOVE_GROUP_UP", Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false)
                            .action("MOVE_GROUP_DOWN", Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false))
                    .action("DELETE_GROUP", Component.translatable("screen.brnquest.editor.context.delete"), true));
            case CHAPTER -> EditorPopupMenu.menu(menu -> menu
                    .action("RENAME_CHAPTER", Component.translatable(
                            "screen.brnquest.editor.context.rename"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action("MOVE_CHAPTER_UP", Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false)
                            .action("MOVE_CHAPTER_DOWN", Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false))
                    .action("DELETE_CHAPTER", Component.translatable(
                            "screen.brnquest.editor.context.delete"), true));
            case TYPED_ENTRY -> EditorPopupMenu.menu(menu -> menu
                    .action("EDIT_TYPED", Component.translatable(
                            "screen.brnquest.editor.action.edit"), false)
                    .action("COPY_TYPED", Component.translatable(
                            "screen.brnquest.editor.action.copy"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action("MOVE_TYPED_UP", Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false,
                                    editContextTypedIndex > 0)
                            .action("MOVE_TYPED_DOWN", Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false,
                                    selectedQuest() != null && editContextTypedIndex >= 0
                                            && editContextTypedIndex + 1 < typedEditorKind.size(selectedQuest())))
                    .submenu(Component.translatable("screen.brnquest.editor.context.technical"), technical -> technical
                            .action("COPY_TYPED_ID", Component.translatable(
                                    "screen.brnquest.editor.context.copy_stable_id"), false)
                            .action("COPY_TYPED_TYPE", Component.translatable(
                                    "screen.brnquest.editor.context.copy_type_id"), false))
                    .action("DELETE_TYPED", Component.translatable(
                            "screen.brnquest.editor.context.delete"), true));
            case DEPENDENCY_ENTRY -> EditorPopupMenu.menu(menu -> menu
                    .submenu(Component.translatable("screen.brnquest.editor.context.technical"), technical -> technical
                            .action("COPY_DEPENDENCY_ID", Component.translatable(
                                    "screen.brnquest.editor.context.copy_quest_id"), false))
                    .action("REMOVE_DEPENDENCY", Component.translatable(
                            "screen.brnquest.editor.dependency.remove"), true));
            case NONE -> List.of();
        };
    }

    private EditorPopupMenu.CascadeLayout editContextLayout(List<EditorPopupMenu.Entry> entries) {
        UiRect root = editContextBounds(entries.size());
        return EditorPopupMenu.cascadeLayout(root, entries, editContextSubmenu, width,
                TOP_TOOLBAR_HEIGHT, height - BOTTOM_TOOLBAR_HEIGHT, 142);
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
        editContextSubmenu = -1;
        editContextTypedIndex = -1;
        editContextGraphX = graphX;
        editContextGraphY = graphY;
        editorOverlays.show(EditorOverlayHost.Kind.CONTEXT_MENU);
    }

    private void openTypedEntryContext(TypedEditorHitbox hitbox, int x, int y) {
        openEditContext(ContextKind.TYPED_ENTRY, hitbox.id(), x, y, 0, 0);
        editContextTypedIndex = hitbox.index();
    }

    private void openDependencyEntryContext(DependencyHitbox hitbox, int x, int y) {
        openEditContext(ContextKind.DEPENDENCY_ENTRY, hitbox.dependencyId(), x, y, 0, 0);
    }

    private void closeEditContext() {
        editContextKind = ContextKind.NONE;
        editContextTarget = null;
        editContextSubmenu = -1;
        editContextTypedIndex = -1;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CONTEXT_MENU)) editorOverlays.close();
    }

    private void performTypedContextAction(String action, ResourceLocation typedId,
                                           int index, QuestBookDefinition book) {
        QuestDefinition quest = selectedQuest();
        if (quest == null || typedId == null || index < 0 || index >= typedEditorKind.size(quest)) return;
        String prefix = typedEditorKind.actionPrefix();
        switch (action) {
            case "EDIT_TYPED" -> openTypedPropertyEditor(quest, typedId);
            case "COPY_TYPED" -> {
                ResourceLocation copyId = suggestId(book, typedEditorKind.idStem() + "_copy");
                sendMutation("COPY_" + prefix, copyId, quest.id(), typedId, "", 0, 0, 0, List.of());
            }
            case "MOVE_TYPED_UP" -> {
                if (index > 0) sendMutation("MOVE_" + prefix, typedId, quest.id(), null, "",
                        index - 1, 0, 0, List.of());
            }
            case "MOVE_TYPED_DOWN" -> {
                if (index + 1 < typedEditorKind.size(quest)) {
                    sendMutation("MOVE_" + prefix, typedId, quest.id(), null, "",
                            index + 1, 0, 0, List.of());
                }
            }
            case "COPY_TYPED_ID" -> copyTechnicalId(typedId.toString());
            case "COPY_TYPED_TYPE" -> {
                TypedEntry entry = typedEditorKind.entry(quest, typedId);
                if (entry != null) copyTechnicalId(entry.typeId().toString());
            }
            case "DELETE_TYPED" ->
                    sendMutation("DELETE_" + prefix, typedId, quest.id(), null, "", 0, 0, 0, List.of());
            default -> { }
        }
    }

    private void copyTechnicalId(String value) {
        if (minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(value);
        Component copied = Component.translatable("screen.brnquest.editor.context.copied_id");
        if (dependencyEditorOpen) dependencyEditorMessage = copied;
        else typedEditorMessage = copied;
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
            case DEPENDENCY_PICKER -> {
                dependencyFilter = "";
                dependencyPickerScroll = 0;
                editorOverlays.close();
            }
            case TYPED_TYPE_PICKER -> {
                // Esc, outside clicks and the visible close button share this path.
                typedTypePickerScroll = 0;
                editorOverlays.close();
            }
            case QUICK_TEXT -> closeQuickTextEditor();
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
            case QUEST_RENAME_CONFIRMATION -> editorOverlays.close();
            case PUBLISH_CONFIRMATION -> {
                publishReview = null;
                publishReviewScroll = 0;
                editorOverlays.close();
            }
            case CONFLICT_RECOVERY -> {
                recoveryCopyBookId = null;
                editorOverlays.close();
            }
            case NONE -> { }
        }
    }

    private void openQuickTextEditor(QuestDefinition quest, QuickTextKind kind) {
        if (kind == QuickTextKind.NONE || ClientEditorState.get().busy()) return;
        closeActiveEditorOverlay();
        quickTextKind = kind;
        quickTextQuestId = quest.id();
        quickTextIssue = null;
        String value = switch (kind) {
            case TITLE -> quest.title().isBlank() ? questTitle(quest) : quest.title();
            case SUBTITLE -> quest.subtitle();
            case DESCRIPTION -> quest.description();
            case NONE -> "";
        };
        quickTextField.setValue(value);
        setFocused(quickTextField);
        editorOverlays.show(EditorOverlayHost.Kind.QUICK_TEXT);
    }

    private void renderQuickTextEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        if (quickTextKind == QuickTextKind.NONE || quickTextField == null) return;
        Component fieldName = Component.translatable(quickTextKind.translationKey());
        EditorQuickTextDialog.render(graphics, font, layout(),
                Component.translatable("screen.brnquest.editor.quick_edit.heading", fieldName),
                quickTextIssue, !ClientEditorState.get().busy(), mouseX, mouseY);
        quickTextField.show(EditorQuickTextDialog.layout(layout()).input(), !ClientEditorState.get().busy());
        // The editor chrome is translated to the final overlay depth. Rendering this field through
        // Screen's ordinary renderable list would leave its border and text behind the modal panel.
        quickTextField.render(graphics, mouseX, mouseY, 0.0F);
    }

    private boolean handleQuickTextEditorClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            EditorQuickTextDialog.Action action = EditorQuickTextDialog.actionAt(layout(), mouseX, mouseY);
            if (action == EditorQuickTextDialog.Action.CANCEL) {
                closeQuickTextEditor();
                return true;
            }
            if (action == EditorQuickTextDialog.Action.APPLY) {
                submitQuickTextEdit();
                return true;
            }
        }
        super.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    /** Sends the same complete quest update as the property form while preserving identity and icon bytes. */
    private void submitQuickTextEdit() {
        ClientEditorState editor = ClientEditorState.get();
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition quest = snapshot == null || quickTextQuestId == null
                ? null : snapshot.quests().get(quickTextQuestId);
        if (editor.busy() || quest == null || quickTextKind == QuickTextKind.NONE
                || editor.sessionId() == null || editor.bookId() == null) return;
        if (!editor.beginMutation()) return;
        String value = quickTextField.getValue();
        String title = quickTextKind == QuickTextKind.TITLE ? value : quest.title();
        String subtitle = quickTextKind == QuickTextKind.SUBTITLE ? value : quest.subtitle();
        String description = quickTextKind == QuickTextKind.DESCRIPTION ? value : quest.description();
        AuthoringNetwork.updateQuest(editor.sessionId(), editor.bookId(), editor.draftRevision(),
                quest.id(), quest.id(), title, subtitle, description, "ITEM", "", true);
        editorSelectedQuest = quest.id();
        closeQuickTextEditor();
    }

    private void closeQuickTextEditor() {
        quickTextKind = QuickTextKind.NONE;
        quickTextQuestId = null;
        quickTextIssue = null;
        setFocused(null);
        if (quickTextField != null) quickTextField.hide();
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) editorOverlays.close();
    }

    private void renderConflictRecovery(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect panel = conflictRecoveryBounds();
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xFF202832);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.recovery.title"),
                panel.centerX(), panel.top() + 10, 0xFFFF8B8B);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.recovery.detail"),
                panel.centerX(), panel.top() + 26, 0xFFB7C5D8);
        Component refresh = Component.translatable("screen.brnquest.editor.recovery.refresh");
        Component saveAs = Component.translatable("screen.brnquest.editor.recovery.save_as");
        Component abandon = Component.translatable("screen.brnquest.editor.recovery.abandon");
        renderEditorTextButton(graphics, conflictRecoveryRefreshBounds(), refresh, refresh,
                true, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        renderEditorTextButton(graphics, conflictRecoverySaveAsBounds(), saveAs, saveAs,
                true, EditorButton.Tone.SUCCESS, mouseX, mouseY);
        renderEditorTextButton(graphics, conflictRecoveryAbandonBounds(), abandon, abandon,
                true, EditorButton.Tone.DANGER, mouseX, mouseY);
        if (recoveryCopyBookId != null) {
            graphics.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(
                            recoveryCopyBookId.toString(), panel.width() - 24)),
                    panel.centerX(), panel.bottom() - 16, 0xFF8FA4B8);
        }
    }

    private boolean handleConflictRecoveryClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        ClientEditorState editor = ClientEditorState.get();
        String action = conflictRecoveryRefreshBounds().contains(mouseX, mouseY) ? "REFRESH"
                : conflictRecoverySaveAsBounds().contains(mouseX, mouseY) ? "SAVE_AS"
                : conflictRecoveryAbandonBounds().contains(mouseX, mouseY) ? "ABANDON" : "";
        if (action.isEmpty() || !editor.beginRecovery()) return true;
        ResourceLocation target = "SAVE_AS".equals(action) ? recoveryCopyBookId : null;
        editorOverlays.close();
        AuthoringNetwork.recoverSession(editor.sessionId(), editor.bookId(), action, target);
        recoveryCopyBookId = null;
        return true;
    }

    private UiRect conflictRecoveryBounds() {
        int panelWidth = Math.min(420, Math.max(260, width - 40));
        int panelHeight = 142;
        int left = (width - panelWidth) / 2;
        int top = Math.max(TOP_TOOLBAR_HEIGHT + 4, (height - panelHeight) / 2);
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect conflictRecoveryRefreshBounds() { return conflictRecoveryButtonBounds(0); }
    private UiRect conflictRecoverySaveAsBounds() { return conflictRecoveryButtonBounds(1); }
    private UiRect conflictRecoveryAbandonBounds() { return conflictRecoveryButtonBounds(2); }

    private UiRect conflictRecoveryButtonBounds(int index) {
        UiRect panel = conflictRecoveryBounds();
        int gap = 6;
        int width = (panel.width() - 24 - gap * 2) / 3;
        int left = panel.left() + 12 + index * (width + gap);
        return new UiRect(left, panel.top() + 52, left + width, panel.top() + 74);
    }

    private static ResourceLocation recoveryCopyId(ResourceLocation source) {
        if (source == null) return null;
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), source.getPath()
                + "_recovered_" + Long.toString(System.currentTimeMillis(), 36));
    }

    private void renderStructureForm(GuiGraphics graphics, int mouseX, int mouseY) {
        // This modal owns the pointer, so navigation and canvas hover surfaces must stay hidden.
        hoveredDetailStack = ItemStack.EMPTY;
        hoveredDetailText = null;
        hoveredComponentTooltip = List.of();
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
        renderEditorTextButton(graphics, structureFormCancelBounds(), Component.translatable("gui.cancel"),
                null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        renderEditorTextButton(graphics, structureFormDoneBounds(), Component.translatable("gui.done"),
                null, true, EditorButton.Tone.PRIMARY, mouseX, mouseY);
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
        closeQuestEditingPanels();
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

    private void renderDeleteConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        EditorConfirmDialog.render(graphics, font, layout(),
                Component.translatable("screen.brnquest.editor.delete.warning"), Component.literal(deleteImpact),
                0xFFFFC07A, Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.delete.confirm"), mouseX, mouseY);
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
                    if (target.equals(editorSelectedQuest)) {
                        editorSelectedQuest = null;
                        closeQuestEditingPanels();
                    }
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

    private boolean sendMutation(String action, ResourceLocation target, ResourceLocation parent,
                                 ResourceLocation source, String title, int targetIndex,
                                 double x, double y, List<AuthoringNetwork.PositionWire> positions) {
        return sendMutation(action, target, parent, source, title, targetIndex, x, y, positions, Map.of());
    }

    private boolean sendMutation(String action, ResourceLocation target, ResourceLocation parent,
                                 ResourceLocation source, String title, int targetIndex,
                                 double x, double y, List<AuthoringNetwork.PositionWire> positions,
                                 Map<String, String> config) {
        ClientEditorState editor = ClientEditorState.get();
        if (!editor.editing() || editor.sessionId() == null || editor.bookId() == null
                || !editor.beginMutation()) return false;
        AuthoringNetwork.mutate(new AuthoringNetwork.EditorMutationWire(editor.sessionId().toString(),
                editor.bookId().toString(), editor.draftRevision(), action,
                target == null ? "" : target.toString(), parent == null ? "" : parent.toString(),
                source == null ? "" : source.toString(), title, targetIndex, x, y, positions, config));
        return true;
    }

    private ResourceLocation suggestId(QuestBookDefinition book, String stem) {
        String namespace = book.id().getNamespace();
        Set<ResourceLocation> ids = new java.util.HashSet<>();
        book.chapterGroups().forEach(value -> ids.add(value.id()));
        book.chapters().forEach(value -> ids.add(value.id()));
        book.quests().forEach(value -> {
            ids.add(value.id());
            value.tasks().forEach(task -> ids.add(task.id()));
            value.rewards().forEach(reward -> ids.add(reward.id()));
        });
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
        if (editor.hasLease() && editorPublishButtonBounds().contains(mouseX, mouseY)) {
            if (!editor.busy() && !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen
                    && !structureFormOpen()) {
                closeActiveEditorOverlay();
                editor.beginPublishReview().ifPresent(request -> AuthoringNetwork.reviewPublish(
                        request.sessionId(), editor.bookId(), request.draftRevision()));
            }
            return true;
        }
        if (editor.hasLease() && editorUndoButtonBounds().contains(mouseX, mouseY)) {
            if (editorHistorySurfaceReady()) {
                editor.beginUndo().ifPresent(request -> AuthoringNetwork.history(
                        request.sessionId(), editor.bookId(), request.draftRevision(), false));
            }
            return true;
        }
        if (editor.hasLease() && editorRedoButtonBounds().contains(mouseX, mouseY)) {
            if (editorHistorySurfaceReady()) {
                editor.beginRedo().ifPresent(request -> AuthoringNetwork.history(
                        request.sessionId(), editor.bookId(), request.draftRevision(), true));
            }
            return true;
        }
        return false;
    }

    /** History never discards values that are still only present in an open client-side form. */
    private boolean editorHistorySurfaceReady() {
        return !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen && !structureFormOpen()
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE;
    }

    private Component editorStatus() {
        ClientEditorState editor = ClientEditorState.get();
        return switch (editor.mode()) {
            case CATALOG_LOADING -> Component.translatable("screen.brnquest.editor.catalog.loading");
            case OPENING -> Component.translatable("screen.brnquest.editor.session.opening");
            case RECEIVING_DRAFT -> Component.translatable("screen.brnquest.editor.draft.loading");
            case MUTATING -> Component.translatable("screen.brnquest.editor.draft.mutating");
            case SAVING -> Component.translatable("screen.brnquest.editor.draft.saving");
            case PUBLISHING -> Component.translatable("screen.brnquest.editor.draft.publishing");
            case REVIEWING -> Component.translatable("screen.brnquest.editor.publish.reviewing");
            case EDITING -> {
                long seconds = editor.remainingLeaseTicks() / 20L;
                String time = "%d:%02d".formatted(seconds / 60L, seconds % 60L);
                if ("PUBLISH_APPLY_COMPLETE".equals(editor.statusCode())) {
                    yield Component.translatable("screen.brnquest.editor.status.published", time);
                }
                yield Component.translatable(editor.dirty() ? "screen.brnquest.editor.status.dirty"
                        : "screen.brnquest.editor.status.saved", time);
            }
            case CLOSING -> Component.translatable("screen.brnquest.editor.session.closing");
            case ERROR -> editor.allowed() ? Component.translatable("screen.brnquest.editor.status.error_detail",
                    editor.statusCode(), editor.statusMessage()) : null;
            case VIEW -> null;
        };
    }

    private void renderTypedEditor(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        if (typedPropertyOpen) {
            renderTypedPropertyEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        typedEditorHitboxes.clear();
        int left = detailLeft() + 10;
        int right = width - 10;
        graphics.fill(detailLeft() + 4, TOP_TOOLBAR_HEIGHT + 4, width - 4,
                height - BOTTOM_TOOLBAR_HEIGHT - 4, 0xFF202632);
        graphics.drawString(font, Component.translatable(typedEditorKind.headingKey()),
                left, TOP_TOOLBAR_HEIGHT + 12, 0xFFFFFFFF, false);
        int count = typedEditorKind.size(quest);
        int visibleRows = typedEditorVisibleRows();
        typedEditorScroll = Math.max(0, Math.min(Math.max(0, count - visibleRows), typedEditorScroll));
        int listTop = typedEditorListTop();
        int listBottom = listTop + visibleRows * TYPED_ROW_HEIGHT;
        graphics.enableScissor(detailLeft() + 4, listTop, width - 4, listBottom);
        if (count == 0) {
            graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.typed.empty"),
                    (left + right) / 2, listTop + 8, 0xFF9AA6B5);
        }
        for (int row = 0; row < visibleRows && typedEditorScroll + row < count; row++) {
            int index = typedEditorScroll + row;
            TypedValue value = typedEditorKind.value(quest, index);
            int top = listTop + row * TYPED_ROW_HEIGHT;
            UiRect rowBounds = new UiRect(left, top, right, top + TYPED_ROW_HEIGHT - 2);
            int actionWidth = 18;
            UiRect more = new UiRect(right - actionWidth, top + 9, right, top + 27);
            UiRect edit = new UiRect(more.left() - actionWidth - 2, top + 9, more.left() - 2, top + 27);
            graphics.fill(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(), 0xA02A323E);
            TypedRowPresentation rowPresentation = typedRowPresentation(quest, index);
            if (!rowPresentation.stack().isEmpty()) {
                graphics.renderItem(rowPresentation.stack(), rowBounds.left() + 4, top + 10);
            } else {
                graphics.drawCenteredString(font, rowPresentation.symbol(), rowBounds.left() + 12, top + 14,
                        0xFFFFFFFF);
            }
            int textLeft = rowBounds.left() + 26;
            int textWidth = Math.max(8, edit.left() - textLeft - 4);
            boolean known = typedEditorKind.known(value.typeId());
            drawFittedString(graphics, rowPresentation.typeName(), textLeft, top + 4,
                    textWidth, 0xFFFFFFFF, 0.75F);
            Component summary = typedRowSummary(quest, index, known);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(summary.getString(), textWidth)),
                    textLeft, top + 18, known ? 0xFF9FB0C2 : 0xFFFFA070, false);
            if (rowBounds.contains(mouseX, mouseY)) {
                hoveredComponentTooltip = List.of(Component.translatable(
                        "screen.brnquest.editor.typed.more_hint"));
            }
            renderEditorIconButton(graphics, edit, Component.literal("✎"),
                    Component.translatable("screen.brnquest.editor.action.edit"), true, false, mouseX, mouseY);
            renderEditorIconButton(graphics, more, Component.literal("⋯"),
                    Component.translatable("screen.brnquest.editor.action.more"), true, false, mouseX, mouseY);
            typedEditorHitboxes.add(new TypedEditorHitbox(value.id(), index, rowBounds, edit, more));
        }
        graphics.disableScissor();
        EditorScrollbar.render(graphics, width - 8, listTop, listBottom,
                count * TYPED_ROW_HEIGHT, visibleRows * TYPED_ROW_HEIGHT,
                typedEditorScroll * (double) TYPED_ROW_HEIGHT);
        if (typedEditorMessage != null) {
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            typedEditorMessage.getString(), DETAIL_WIDTH - 24)),
                    left, typedEditorAddBounds().top() - 12, 0xFFFFA070, false);
        }
        Component add = Component.translatable("screen.brnquest.editor.typed.add");
        renderEditorActionButton(graphics, typedEditorAddBounds(), EditorButton.Definition.iconAndText(
                        add, add, EditorIcon.glyph(Component.literal("+"))),
                !ClientEditorState.get().busy(), -1, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        renderEditorTextButton(graphics, typedEditorDoneBounds(), Component.translatable("gui.done"),
                null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private boolean handleTypedEditorClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        if (typedPropertyOpen) return handleTypedPropertyEditorClick(mouseX, mouseY);
        if (button == 0 && typedEditorDoneBounds().contains(mouseX, mouseY)) {
            closeTypedEditor();
            return true;
        }
        if (button == 0 && typedEditorAddBounds().contains(mouseX, mouseY)) {
            if (!ClientEditorState.get().busy()) {
                typedTypePickerScroll = 0;
                typedEditorMessage = null;
                editorOverlays.show(EditorOverlayHost.Kind.TYPED_TYPE_PICKER);
            }
            return true;
        }
        if (ClientEditorState.get().busy()) return true;
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition quest = selectedQuest();
        if (snapshot == null || quest == null) return true;
        for (TypedEditorHitbox hitbox : typedEditorHitboxes) {
            if (button == 0 && hitbox.edit().contains(mouseX, mouseY)) {
                openTypedPropertyEditor(quest, hitbox.id());
                return true;
            }
            if ((button == 0 && hitbox.more().contains(mouseX, mouseY))
                    || (button == 1 && hitbox.row().contains(mouseX, mouseY))) {
                openTypedEntryContext(hitbox, (int) mouseX, (int) mouseY);
                return true;
            }
            if (button == 0 && hitbox.row().contains(mouseX, mouseY)) {
                openTypedPropertyEditor(quest, hitbox.id());
                return true;
            }
        }
        return true;
    }

    private Component typedRowSummary(QuestDefinition quest, int index, boolean known) {
        if (!known) return Component.translatable("screen.brnquest.editor.typed.summary.unknown");
        if (typedEditorKind == TypedKind.TASK) {
            TaskDefinition task = quest.tasks().get(index);
            return Component.translatable(task.optional()
                            ? "screen.brnquest.editor.typed.summary.task_optional"
                            : "screen.brnquest.editor.typed.summary.config",
                    task.config().size());
        }
        RewardDefinition reward = quest.rewards().get(index);
        return Component.translatable(reward.teamReward()
                        ? "screen.brnquest.editor.typed.summary.reward_team"
                        : "screen.brnquest.editor.typed.summary.config",
                reward.config().size());
    }

    private TypedRowPresentation typedRowPresentation(QuestDefinition quest, int index) {
        if (typedEditorKind == TypedKind.TASK) {
            TaskDefinition task = quest.tasks().get(index);
            var view = ApiViews.task(task);
            var presentation = ClientTaskPresentationRegistry.get(task.typeId());
            String snbt = presentation.itemSnbt(view);
            return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view),
                    snbt.isBlank() ? ItemStack.EMPTY : item(task.id(), snbt));
        }
        RewardDefinition reward = quest.rewards().get(index);
        var view = ApiViews.reward(reward);
        var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        String snbt = presentation.itemSnbt(view);
        return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view),
                snbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), snbt));
    }

    private void openTypedPropertyEditor(QuestDefinition quest, ResourceLocation typedId) {
        TypedEntry entry = typedEditorKind.entry(quest, typedId);
        if (entry == null || ClientEditorState.get().busy()) return;
        typedPropertyOpen = true;
        typedPropertyOriginalId = entry.id();
        typedPropertyTypeId = entry.typeId();
        typedPropertyOriginalConfig = entry.config();
        typedPropertyRawConfig = entry.config();
        typedPropertyOptional = entry.optional();
        typedPropertyTeamReward = entry.teamReward();
        typedPropertyRenameArmed = false;
        typedPropertyMessage = null;
        typedPropertySubmissionPending = false;
        typedPropertyServerIssues.clear();
        typedPropertyIdField.setValue(entry.id().toString());
        typedPropertyClaimPolicyField.setValue(entry.claimPolicy());
        typedPropertySchema = typedEditorKind == TypedKind.TASK
                ? ConfigEditorSchemas.forTask(ApiViews.task(entry.task()))
                : ConfigEditorSchemas.forReward(ApiViews.reward(entry.reward()));
        for (int index = 0; index < typedPropertyConfigFields.size(); index++) {
            String value = index < typedPropertySchema.fields().size()
                    ? entry.config().getOrDefault(typedPropertySchema.fields().get(index).key(),
                    typedPropertySchema.fields().get(index).defaultValue().orElse("")) : "";
            typedPropertyConfigFields.get(index).setValue(value);
        }
        setFocused(typedPropertyIdField);
    }

    private void renderTypedPropertyEditor(GuiGraphics graphics, QuestDefinition quest,
                                           int mouseX, int mouseY) {
        refreshTypedPropertySubmission();
        if (!typedPropertyOpen) return;
        int left = detailLeft() + 10;
        int width = DETAIL_WIDTH - 24;
        graphics.fill(detailLeft() + 4, TOP_TOOLBAR_HEIGHT + 4, this.width - 4,
                height - BOTTOM_TOOLBAR_HEIGHT - 4, 0xFF202632);
        Map<String, String> localIssues = typedPropertyLocalIssues();
        Component heading = typedPropertyMessage == null ? firstTypedIssue(localIssues)
                : typedPropertyMessage;
        if (heading == null) heading = Component.translatable("screen.brnquest.editor.typed.property.heading");
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(heading.getString(), width)),
                left, TOP_TOOLBAR_HEIGHT + 7, typedPropertyMessage == null && localIssues.isEmpty()
                        && typedPropertyServerIssues.isEmpty() ? 0xFFFFFFFF : 0xFFFFA070, false);

        int top = TOP_TOOLBAR_HEIGHT + 20;
        renderTypedReadOnlyRow(graphics, "screen.brnquest.editor.typed.property.type",
                typedTypeName(quest, typedPropertyOriginalId), left, top, width);
        renderTypedTextRow(graphics, typedPropertyIdField, "screen.brnquest.editor.typed.property.id",
                left, top + 22, width, typedPropertyServerIssues.get("id"));

        typedPropertyConfigFields.forEach(EditorTextField::hide);
        typedPropertyClaimPolicyField.hide();
        List<ConfigFieldDescriptor> fields = typedPropertySchema == null ? List.of() : typedPropertySchema.fields();
        int visibleFields = Math.min(fields.size(), MAX_TYPED_CONFIG_FIELDS);
        for (int index = 0; index < visibleFields; index++) {
            ConfigFieldDescriptor descriptor = fields.get(index);
            int rowTop = top + 44 + index * 22;
            renderTypedConfigRow(graphics, descriptor, index, left, rowTop, width,
                    localIssues.getOrDefault(descriptor.key(), typedPropertyServerIssues.get(descriptor.key())),
                    mouseX, mouseY);
        }
        int semanticsTop = top + 44 + visibleFields * 22;
        if (typedPropertySchema != null && typedPropertySchema.rawFallback()) {
            if (typedPropertyRawEditable()) {
                renderTypedRawConfigRow(graphics, left, semanticsTop, width, typedPropertyRawIssue(),
                        mouseX, mouseY);
            } else {
                graphics.drawString(font, Component.translatable("screen.brnquest.editor.typed.property.raw_preserved"),
                        left, semanticsTop + 5, 0xFFFFA070, false);
            }
            semanticsTop += 22;
        }
        if (typedEditorKind == TypedKind.TASK) {
            renderTypedToggleRow(graphics, "screen.brnquest.editor.typed.property.optional",
                    typedPropertyOptional, left, semanticsTop, width, mouseX, mouseY);
        } else {
            renderTypedTextRow(graphics, typedPropertyClaimPolicyField,
                    "screen.brnquest.editor.typed.property.claim_policy", left, semanticsTop, width,
                    typedPropertyServerIssues.get("claim_policy"));
            renderTypedToggleRow(graphics, "screen.brnquest.editor.typed.property.team_reward",
                    typedPropertyTeamReward, left, semanticsTop + 22, width, mouseX, mouseY);
        }

        renderEditorTextButton(graphics, typedPropertyCancelBounds(), Component.translatable("gui.cancel"),
                null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        Component done = Component.translatable(typedPropertyRenameArmed
                ? "screen.brnquest.editor.typed.property.confirm_rename" : "gui.done");
        renderEditorTextButton(graphics, typedPropertyDoneBounds(), done, null,
                !ClientEditorState.get().busy(), typedPropertyRenameArmed
                        ? EditorButton.Tone.WARNING : EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    private Component typedTypeName(QuestDefinition quest, ResourceLocation typedId) {
        TypedEntry entry = typedEditorKind.entry(quest, typedId);
        if (entry == null) return Component.literal(typedPropertyTypeId == null ? "" : typedPropertyTypeId.toString());
        return typedEditorKind == TypedKind.TASK
                ? ClientTaskPresentationRegistry.get(entry.typeId()).typeName(ApiViews.task(entry.task()))
                : ClientRewardPresentationRegistry.get(entry.typeId()).typeName(ApiViews.reward(entry.reward()));
    }

    private void renderTypedReadOnlyRow(GuiGraphics graphics, String labelKey, Component value,
                                        int left, int top, int width) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, labelKey, row.label());
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(value.getString(), row.field().width())),
                row.field().left(), row.field().top() + 5, 0xFFFFFFFF, false);
    }

    private void renderTypedTextRow(GuiGraphics graphics, EditorTextField field, String labelKey,
                                    int left, int top, int width) {
        renderTypedTextRow(graphics, field, labelKey, left, top, width, null);
    }

    private void renderTypedTextRow(GuiGraphics graphics, EditorTextField field, String labelKey,
                                    int left, int top, int width, String issue) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, labelKey, row.label(), issue);
        field.show(row.field(), !ClientEditorState.get().busy());
    }

    private void renderTypedConfigRow(GuiGraphics graphics, ConfigFieldDescriptor descriptor, int index,
                                      int left, int top, int width, String issue, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, typedConfigLabel(descriptor.key()), row.label(), issue);
        EditorTextField field = typedPropertyConfigFields.get(index);
        if (descriptor.valueType() == ConfigValueType.BOOLEAN) {
            renderEditorTextButton(graphics, row.field(), booleanValue(field.getValue())
                            ? Component.translatable("options.on") : Component.translatable("options.off"),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ENUM) {
            renderEditorTextButton(graphics, row.field(), Component.literal(field.getValue()),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ITEM_STACK) {
            ItemStack stack = item(typedPropertyOriginalId, field.getValue());
            Component select = Component.translatable("screen.brnquest.editor.typed.property.select_item");
            EditorIcon icon = stack.isEmpty()
                    ? EditorIcon.glyph(Component.literal("+")) : EditorIcon.item(stack);
            renderEditorActionButton(graphics, row.field(), EditorButton.Definition.iconAndText(
                            select, select, icon),
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else {
            field.show(row.field(), !ClientEditorState.get().busy());
        }
    }

    private void renderTypedToggleRow(GuiGraphics graphics, String labelKey, boolean value,
                                      int left, int top, int width, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, labelKey, row.label());
        renderEditorTextButton(graphics, row.field(), value
                        ? Component.translatable("options.on") : Component.translatable("options.off"),
                null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void renderTypedRawConfigRow(GuiGraphics graphics, int left, int top, int width, String issue,
                                         int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, "screen.brnquest.editor.typed.property.raw_config", row.label(), issue);
        Component edit = Component.translatable(
                "screen.brnquest.editor.typed.property.edit_raw", typedPropertyRawConfig.size());
        renderEditorActionButton(graphics, row.field(), EditorButton.Definition.iconAndText(
                        edit, edit, EditorIcon.glyph(Component.literal("{}"))),
                !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void drawTypedLabel(GuiGraphics graphics, String labelKey, UiRect bounds) {
        drawTypedLabel(graphics, labelKey, bounds, null);
    }

    private void drawTypedLabel(GuiGraphics graphics, String labelKey, UiRect bounds, String issue) {
        String prefix = issue == null || issue.isBlank() ? "" : "! ";
        String label = font.plainSubstrByWidth(prefix + Component.translatable(labelKey).getString(),
                bounds.width() - 4);
        graphics.drawString(font, Component.literal(label), bounds.left(), bounds.top() + 5,
                prefix.isEmpty() ? 0xFF9FB0C2 : 0xFFFF7070, false);
    }

    private String typedConfigLabel(String key) {
        return switch (key) {
            case "item" -> "screen.brnquest.editor.config.item";
            case "count" -> "screen.brnquest.editor.config.count";
            case "consume_items" -> "screen.brnquest.editor.config.consume_items";
            case "title" -> "screen.brnquest.editor.config.title";
            default -> key;
        };
    }

    private boolean handleTypedPropertyEditorClick(double mouseX, double mouseY) {
        if (typedPropertyCancelBounds().contains(mouseX, mouseY)) {
            closeTypedPropertyEditor();
            return true;
        }
        if (typedPropertyDoneBounds().contains(mouseX, mouseY)) {
            prepareTypedPropertyEdit();
            return true;
        }
        List<ConfigFieldDescriptor> fields = typedPropertySchema == null ? List.of() : typedPropertySchema.fields();
        for (int index = 0; index < Math.min(fields.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            ConfigFieldDescriptor descriptor = fields.get(index);
            UiRect bounds = typedPropertyConfigBounds(index);
            if (!bounds.contains(mouseX, mouseY)) continue;
            EditorTextField field = typedPropertyConfigFields.get(index);
            if (descriptor.valueType() == ConfigValueType.BOOLEAN) {
                field.setValue(Boolean.toString(!booleanValue(field.getValue())));
                return true;
            }
            if (descriptor.valueType() == ConfigValueType.ENUM) {
                List<String> values = descriptor.allowedValues();
                int current = Math.max(0, values.indexOf(field.getValue()));
                field.setValue(values.get((current + 1) % values.size()));
                return true;
            }
            if (descriptor.valueType() == ConfigValueType.ITEM_STACK) {
                openTypedPropertyItemSelector(index);
                return true;
            }
        }
        int semanticsTop = typedPropertySemanticsTop();
        if (typedPropertySchema != null && typedPropertySchema.rawFallback() && typedPropertyRawEditable()
                && typedPropertySemanticBounds(semanticsTop - 22).contains(mouseX, mouseY)) {
            openTypedPropertyRawEditor();
            return true;
        }
        if (typedEditorKind == TypedKind.TASK
                && typedPropertySemanticBounds(semanticsTop).contains(mouseX, mouseY)) {
            typedPropertyOptional = !typedPropertyOptional;
            return true;
        }
        if (typedEditorKind == TypedKind.REWARD
                && typedPropertySemanticBounds(semanticsTop + 22).contains(mouseX, mouseY)) {
            typedPropertyTeamReward = !typedPropertyTeamReward;
            return true;
        }
        super.mouseClicked(mouseX, mouseY, 0);
        return true;
    }

    private void openTypedPropertyItemSelector(int fieldIndex) {
        if (minecraft == null) return;
        openEditorItemSelector(stack -> {
            if (minecraft.level == null || fieldIndex >= typedPropertyConfigFields.size()) return;
            typedPropertyConfigFields.get(fieldIndex).setValue(
                    stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString());
            itemCache.remove(typedPropertyOriginalId);
        });
    }

    private void openTypedPropertyRawEditor() {
        if (minecraft == null || !typedPropertyRawEditable()) return;
        editorChildScreenOpening = true;
        minecraft.setScreen(new EditorRawConfigScreen(this, typedPropertyRawConfig, config -> {
            typedPropertyRawConfig = Map.copyOf(config);
            // A corrected local value supersedes old field diagnostics; the server will return
            // fresh Codec diagnostics when the containing property form is submitted.
            typedPropertyServerIssues.keySet().removeIf(key -> !"id".equals(key) && !"claim_policy".equals(key));
            typedPropertyMessage = null;
        }));
    }

    private void prepareTypedPropertyEdit() {
        ResourceLocation replacementId = ResourceLocation.tryParse(typedPropertyIdField.getValue().strip());
        if (replacementId == null) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.invalid_id");
            return;
        }
        Map<String, String> config = currentTypedPropertyConfig();
        Map<String, String> issues = typedPropertyLocalIssues();
        if (!issues.isEmpty()) {
            String fieldKey = issues.keySet().iterator().next();
            typedPropertyMessage = Component.literal(fieldKey + ": " + issues.get(fieldKey));
            focusTypedConfigField(fieldKey);
            return;
        }
        if (!replacementId.equals(typedPropertyOriginalId) && !typedPropertyRenameArmed) {
            typedPropertyRenameArmed = true;
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.rename_warning");
            return;
        }
        if (typedEditorKind == TypedKind.REWARD && typedPropertyClaimPolicyField.getValue().isBlank()) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.claim_required");
            typedPropertyServerIssues.put("claim_policy", typedPropertyMessage.getString());
            setFocused(typedPropertyClaimPolicyField);
            return;
        }
        typedPropertyServerIssues.clear();
        if (!sendMutation("UPDATE_" + typedEditorKind.actionPrefix(), replacementId, typedEditorQuestId,
                typedPropertyOriginalId, typedEditorKind == TypedKind.REWARD
                        ? typedPropertyClaimPolicyField.getValue().strip() : "",
                typedEditorKind == TypedKind.TASK ? (typedPropertyOptional ? 1 : 0)
                        : (typedPropertyTeamReward ? 1 : 0), 0, 0, List.of(), config)) return;
        typedPropertySubmissionPending = true;
        typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.submitting");
        itemCache.remove(typedPropertyOriginalId);
        itemCache.remove(replacementId);
    }

    private void closeTypedPropertyEditor() {
        typedPropertyOpen = false;
        typedPropertyOriginalId = null;
        typedPropertyTypeId = null;
        typedPropertySchema = null;
        typedPropertyOriginalConfig = Map.of();
        typedPropertyRawConfig = Map.of();
        typedPropertyMessage = null;
        typedPropertyRenameArmed = false;
        typedPropertySubmissionPending = false;
        typedPropertyServerIssues.clear();
        setFocused(null);
        if (typedPropertyIdField != null) typedPropertyIdField.hide();
        if (typedPropertyClaimPolicyField != null) typedPropertyClaimPolicyField.hide();
        typedPropertyConfigFields.forEach(EditorTextField::hide);
    }

    private int typedPropertySemanticsTop() {
        int fields = typedPropertySchema == null ? 0
                : Math.min(typedPropertySchema.fields().size(), MAX_TYPED_CONFIG_FIELDS);
        return TOP_TOOLBAR_HEIGHT + 20 + 44 + fields * 22
                + (typedPropertySchema != null && typedPropertySchema.rawFallback() ? 22 : 0);
    }

    private UiRect typedPropertyConfigBounds(int index) {
        int left = detailLeft() + 10;
        return EditorPropertyFormLayout.row(left, TOP_TOOLBAR_HEIGHT + 20 + 44 + index * 22,
                DETAIL_WIDTH - 24, 68).field();
    }

    private UiRect typedPropertySemanticBounds(int top) {
        return EditorPropertyFormLayout.row(detailLeft() + 10, top, DETAIL_WIDTH - 24, 68).field();
    }

    private UiRect typedPropertyCancelBounds() { return questEditorCancelBounds(); }
    private UiRect typedPropertyDoneBounds() { return questEditorSaveBounds(); }

    private static boolean booleanValue(String value) {
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    private Map<String, String> currentTypedPropertyConfig() {
        if (typedPropertySchema != null && typedPropertySchema.rawFallback()) {
            return Map.copyOf(typedPropertyRawConfig);
        }
        Map<String, String> config = new LinkedHashMap<>(typedPropertyOriginalConfig);
        List<ConfigFieldDescriptor> fields = typedPropertySchema == null ? List.of() : typedPropertySchema.fields();
        for (int index = 0; index < Math.min(fields.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            String key = fields.get(index).key();
            String value = typedPropertyConfigFields.get(index).getValue().strip();
            if (value.isEmpty()) config.remove(key); else config.put(key, value);
        }
        return config;
    }

    /** Missing registrations stay read-only because there is no corresponding Codec to approve an edit. */
    private boolean typedPropertyRawEditable() {
        if (typedPropertySchema == null || !typedPropertySchema.rawFallback() || typedPropertyTypeId == null) {
            return false;
        }
        return typedEditorKind == TypedKind.TASK
                ? TaskTypeRegistry.get(typedPropertyTypeId) != null
                : RewardTypeRegistry.get(typedPropertyTypeId) != null;
    }

    private String typedPropertyRawIssue() {
        return typedPropertyServerIssues.entrySet().stream()
                .filter(entry -> !"id".equals(entry.getKey()) && !"claim_policy".equals(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /** Combines descriptor validation with the client registry check needed for an ItemStack field. */
    private Map<String, String> typedPropertyLocalIssues() {
        if (typedPropertySchema == null) return Map.of();
        Map<String, String> issues = new LinkedHashMap<>();
        Map<String, String> config = currentTypedPropertyConfig();
        for (ConfigFieldIssue issue : ConfigEditorSchemas.validate(typedPropertySchema.fields(), config)) {
            issues.putIfAbsent(issue.fieldKey(), issue.message());
        }
        if (minecraft != null && minecraft.level != null) {
            for (ConfigFieldDescriptor field : typedPropertySchema.fields()) {
                String value = config.getOrDefault(field.key(), "");
                if (field.valueType() != ConfigValueType.ITEM_STACK || value.isBlank()) continue;
                try {
                    ItemStack stack = ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(value));
                    if (stack.isEmpty()) issues.putIfAbsent(field.key(), "ItemStack does not contain a registered item");
                } catch (Exception exception) {
                    issues.putIfAbsent(field.key(), "ItemStack SNBT is invalid");
                }
            }
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(issues));
    }

    private Component firstTypedIssue(Map<String, String> localIssues) {
        Map.Entry<String, String> issue = !typedPropertyServerIssues.isEmpty()
                ? typedPropertyServerIssues.entrySet().iterator().next()
                : localIssues.isEmpty() ? null : localIssues.entrySet().iterator().next();
        return issue == null ? null : Component.literal("! " + issue.getKey() + ": " + issue.getValue());
    }

    private void focusTypedConfigField(String fieldKey) {
        List<ConfigFieldDescriptor> fields = typedPropertySchema == null ? List.of() : typedPropertySchema.fields();
        for (int index = 0; index < Math.min(fields.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            if (fields.get(index).key().equals(fieldKey)) {
                if (fields.get(index).valueType() != ConfigValueType.ITEM_STACK) {
                    setFocused(typedPropertyConfigFields.get(index));
                }
                return;
            }
        }
    }

    /** Keeps the form open on server rejection and closes it only after the verified replacement draft arrives. */
    private void refreshTypedPropertySubmission() {
        if (!typedPropertySubmissionPending) return;
        ClientEditorState editor = ClientEditorState.get();
        if (editor.busy()) return;
        if (editor.mode() == ClientEditorState.Mode.EDITING) {
            closeTypedPropertyEditor();
            return;
        }
        if (editor.mode() != ClientEditorState.Mode.ERROR) return;
        typedPropertySubmissionPending = false;
        typedPropertyMessage = Component.literal(editor.statusMessage().isBlank()
                ? editor.statusCode() : editor.statusMessage());
        typedPropertyServerIssues.clear();
        for (AuthoringNetwork.EditorDiagnosticWire diagnostic : editor.diagnostics()) {
            String path = diagnostic.path() == null ? "" : diagnostic.path();
            String fieldKey = path.startsWith("config.") ? path.substring("config.".length()) : path;
            if (!fieldKey.isBlank()) typedPropertyServerIssues.putIfAbsent(fieldKey, diagnostic.message());
        }
        if (!typedPropertyServerIssues.isEmpty()) {
            String fieldKey = typedPropertyServerIssues.keySet().iterator().next();
            if ("id".equals(fieldKey)) setFocused(typedPropertyIdField);
            else if ("claim_policy".equals(fieldKey)) setFocused(typedPropertyClaimPolicyField);
            else focusTypedConfigField(fieldKey);
        }
    }

    private void openTypedEditor(QuestDefinition quest, TypedKind kind) {
        closeQuestEditor();
        closeDependencyEditor();
        closeActiveEditorOverlay();
        typedEditorOpen = true;
        typedEditorKind = kind;
        typedEditorQuestId = quest.id();
        typedEditorScroll = 0;
        typedEditorMessage = null;
    }

    private void closeTypedEditor() {
        closeTypedPropertyEditor();
        typedEditorOpen = false;
        typedEditorQuestId = null;
        typedEditorScroll = 0;
        typedEditorMessage = null;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.TYPED_TYPE_PICKER)) closeActiveEditorOverlay();
    }

    private void renderTypedTypePicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ResourceLocation> candidates = typedTypeCandidates();
        UiRect bounds = typedTypePickerBounds();
        int visibleRows = EditorPickerList.visibleRows(bounds);
        typedTypePickerScroll = Math.max(0, Math.min(Math.max(0, candidates.size() - visibleRows),
                typedTypePickerScroll));
        List<EditorPickerList.Entry> entries = candidates.stream().map(type -> new EditorPickerList.Entry(
                Component.literal(type.toString()), Component.translatable(typedEditorKind.addable(type)
                        ? typedEditorKind.itemBacked(type)
                                ? "screen.brnquest.editor.typed.click_to_select_item"
                                : "screen.brnquest.editor.typed.click_to_add"
                        : "screen.brnquest.editor.typed.requires_config"),
                typedEditorKind.addable(type) ? EditorPickerList.Tone.NORMAL : EditorPickerList.Tone.WARNING)).toList();
        EditorPickerList.render(graphics, font, bounds,
                Component.translatable("screen.brnquest.editor.typed.type_heading"), false, entries,
                typedTypePickerScroll, mouseX, mouseY);
        renderEditorIconButton(graphics, typedTypePickerCloseBounds(), Component.literal("×"),
                Component.translatable("screen.brnquest.editor.action.close"),
                true, false, mouseX, mouseY);
    }

    private boolean handleTypedTypePickerClick(double mouseX, double mouseY, int button) {
        if (button == 0 && typedTypePickerCloseBounds().contains(mouseX, mouseY)) {
            closeActiveEditorOverlay();
            return true;
        }
        if (button != 0 || !typedTypePickerBounds().contains(mouseX, mouseY)) {
            closeActiveEditorOverlay();
            return true;
        }
        List<ResourceLocation> candidates = typedTypeCandidates();
        int index = EditorPickerList.entryAt(typedTypePickerBounds(), typedTypePickerScroll,
                candidates.size(), mouseX, mouseY);
        if (index < 0) return true;
        ResourceLocation typeId = candidates.get(index);
        if (!typedEditorKind.addable(typeId)) {
            typedEditorMessage = Component.translatable("screen.brnquest.editor.typed.requires_config");
            closeActiveEditorOverlay();
            return true;
        }
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || typedEditorQuestId == null) return true;
        if (typedEditorKind.itemBacked(typeId)) {
            closeActiveEditorOverlay();
            openItemSelector(typeId);
            return true;
        }
        ResourceLocation id = suggestId(snapshot.book(), typedEditorKind.idStem());
        sendMutation("ADD_" + typedEditorKind.actionPrefix(), id, typedEditorQuestId, typeId,
                "", 0, 0, 0, List.of());
        closeActiveEditorOverlay();
        return true;
    }

    private void openItemSelector(ResourceLocation typeId) {
        if (minecraft == null) return;
        openEditorItemSelector(stack -> addSelectedItem(typeId, stack));
    }

    /** Opens a real child Screen so JEI initializes ghost dragging before the first interaction. */
    private void openEditorItemSelector(java.util.function.Consumer<ItemStack> selectionConsumer) {
        if (minecraft == null) return;
        editorChildScreenOpening = true;
        minecraft.setScreen(new EditorItemSelectorScreen(this, selectionConsumer));
    }

    private void addSelectedItem(ResourceLocation typeId, ItemStack stack) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || typedEditorQuestId == null || minecraft == null || minecraft.level == null
                || stack.isEmpty()) return;
        ResourceLocation id = suggestId(snapshot.book(), typedEditorKind.idStem());
        // Store a count-one ItemStack; the separate count field remains the author-facing quantity.
        String itemSnbt = stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString();
        sendMutation("ADD_" + typedEditorKind.actionPrefix(), id, typedEditorQuestId, typeId,
                "", 0, 0, 0, List.of(), Map.of("item", itemSnbt, "count", "1"));
    }

    private List<ResourceLocation> typedTypeCandidates() {
        java.util.SortedSet<ResourceLocation> ids = new java.util.TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        ids.addAll(typedEditorKind.builtIns());
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot != null) snapshot.book().quests().forEach(quest -> {
            if (typedEditorKind == TypedKind.TASK) quest.tasks().forEach(task -> ids.add(task.typeId()));
            else quest.rewards().forEach(reward -> ids.add(reward.typeId()));
        });
        return List.copyOf(ids);
    }

    private int typedEditorListTop() { return TOP_TOOLBAR_HEIGHT + 34; }

    private int typedEditorVisibleRows() {
        return Math.max(1, (typedEditorAddBounds().top() - 16 - typedEditorListTop()) / TYPED_ROW_HEIGHT);
    }

    private UiRect typedEditorAddBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        int center = detailLeft() + DETAIL_WIDTH / 2;
        return new UiRect(detailLeft() + 10, bottom - 20, center - 4, bottom);
    }

    private UiRect typedEditorDoneBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        int center = detailLeft() + DETAIL_WIDTH / 2;
        return new UiRect(center + 4, bottom - 20, width - 10, bottom);
    }

    private UiRect typedTypePickerBounds() {
        int desiredHeight = EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT * 6 + 4;
        int maximumHeight = Math.max(EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT + 4,
                height - TOP_TOOLBAR_HEIGHT - BOTTOM_TOOLBAR_HEIGHT - 16);
        return layout().centeredDialog(430, 260, 20, Math.min(desiredHeight, maximumHeight));
    }

    private UiRect typedTypePickerCloseBounds() {
        UiRect picker = typedTypePickerBounds();
        return new UiRect(picker.right() - 18, picker.top() + 2, picker.right() - 2, picker.top() + 16);
    }

    private void renderDependencyEditor(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        dependencyHitboxes.clear();
        int left = detailLeft() + 10;
        int panelRight = width - 10;
        graphics.fill(detailLeft() + 4, TOP_TOOLBAR_HEIGHT + 4, width - 4,
                height - BOTTOM_TOOLBAR_HEIGHT - 4, 0xFF202632);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.heading"),
                left, TOP_TOOLBAR_HEIGHT + 12, 0xFFFFFFFF, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.direction"),
                left, TOP_TOOLBAR_HEIGHT + 27, 0xFF9FB0C2, false);

        QuestBookSnapshot snapshot = displaySnapshot();
        int visibleRows = dependencyVisibleRows();
        int maximumScroll = Math.max(0, quest.dependencies().size() - visibleRows);
        dependencyScroll = Math.max(0, Math.min(maximumScroll, dependencyScroll));
        int listTop = dependencyListTop();
        int listBottom = listTop + visibleRows * DEPENDENCY_ROW_HEIGHT;
        graphics.enableScissor(detailLeft() + 4, listTop, width - 4, listBottom);
        if (quest.dependencies().isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.brnquest.editor.dependency.empty"),
                    (left + panelRight) / 2, listTop + 8, 0xFF9AA6B5);
        }
        for (int row = 0; row < visibleRows && dependencyScroll + row < quest.dependencies().size(); row++) {
            ResourceLocation dependencyId = quest.dependencies().get(dependencyScroll + row);
            QuestDefinition dependency = snapshot == null ? null : snapshot.quests().get(dependencyId);
            int top = listTop + row * DEPENDENCY_ROW_HEIGHT;
            UiRect rowBounds = new UiRect(left, top, panelRight, top + DEPENDENCY_ROW_HEIGHT - 2);
            UiRect remove = new UiRect(panelRight - 20, top + 5, panelRight - 2, top + 23);
            graphics.fill(rowBounds.left(), rowBounds.top(), rowBounds.right(), rowBounds.bottom(), 0xA02A323E);
            String dependencyTitle = dependency == null ? dependencyId.toString() : questTitle(dependency);
            String direction = Component.translatable("screen.brnquest.editor.dependency.edge",
                    dependencyTitle, questTitle(quest)).getString();
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(direction, rowBounds.width() - 30)),
                    rowBounds.left() + 5, top + 4, dependency == null ? 0xFFFFA070 : 0xFFFFFFFF, false);
            Component secondary = dependency == null
                    ? Component.translatable("screen.brnquest.editor.dependency.missing")
                    : Component.literal(chapterTitle(snapshot.book(), dependency.chapterId()));
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            secondary.getString(), rowBounds.width() - 30)),
                    rowBounds.left() + 5, top + 16, dependency == null ? 0xFFFFA070 : 0xFF9FB0C2, false);
            renderEditorIconButton(graphics, remove, Component.literal("×"),
                    Component.translatable("screen.brnquest.editor.dependency.remove"),
                    !ClientEditorState.get().busy(), true, mouseX, mouseY);
            dependencyHitboxes.add(new DependencyHitbox(rowBounds, remove, dependencyId));
            if (rowBounds.contains(mouseX, mouseY) && !remove.contains(mouseX, mouseY)) {
                hoveredComponentTooltip = List.of(Component.translatable(
                        "screen.brnquest.editor.dependency.more_hint"));
            }
        }
        graphics.disableScissor();
        EditorScrollbar.render(graphics, width - 8, listTop, listBottom,
                quest.dependencies().size() * DEPENDENCY_ROW_HEIGHT,
                visibleRows * DEPENDENCY_ROW_HEIGHT, dependencyScroll * (double) DEPENDENCY_ROW_HEIGHT);

        if (dependencyEditorMessage != null) {
            String visible = font.plainSubstrByWidth(dependencyEditorMessage.getString(), DETAIL_WIDTH - 24);
            graphics.drawString(font, Component.literal(visible), left, dependencyAddBounds().top() - 12,
                    0xFFFFA070, false);
        }
        Component add = Component.translatable("screen.brnquest.editor.dependency.add");
        renderEditorActionButton(graphics, dependencyAddBounds(), EditorButton.Definition.iconAndText(
                        add, add, EditorIcon.glyph(Component.literal("+"))),
                !ClientEditorState.get().busy(), -1, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        renderEditorTextButton(graphics, dependencyDoneBounds(), Component.translatable("gui.done"),
                null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private boolean handleDependencyEditorClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        if (button == 0 && dependencyDoneBounds().contains(mouseX, mouseY)) {
            closeDependencyEditor();
            return true;
        }
        if (button == 0 && dependencyAddBounds().contains(mouseX, mouseY)) {
            if (!ClientEditorState.get().busy()) openDependencyPicker();
            return true;
        }
        if (!ClientEditorState.get().busy()) {
            for (DependencyHitbox hitbox : dependencyHitboxes) {
                if (button == 0 && hitbox.removeBounds().contains(mouseX, mouseY)) {
                    sendMutation("REMOVE_DEPENDENCY", dependencyEditorQuestId, null,
                            hitbox.dependencyId(), "", 0, 0, 0, List.of());
                    dependencyEditorMessage = null;
                    return true;
                }
                if (button == 1 && hitbox.rowBounds().contains(mouseX, mouseY)) {
                    openDependencyEntryContext(hitbox, (int) mouseX, (int) mouseY);
                    return true;
                }
            }
        }
        return true;
    }

    private void openDependencyEditor(QuestDefinition quest) {
        closeQuestEditor();
        closeTypedEditor();
        closeActiveEditorOverlay();
        dependencyEditorOpen = true;
        dependencyEditorQuestId = quest.id();
        dependencyScroll = 0;
        dependencyEditorMessage = null;
    }

    private void closeDependencyEditor() {
        dependencyEditorOpen = false;
        dependencyEditorQuestId = null;
        dependencyScroll = 0;
        dependencyEditorMessage = null;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)) closeActiveEditorOverlay();
    }

    private void closeQuestEditingPanels() {
        closeQuestEditor();
        closeDependencyEditor();
        closeTypedEditor();
    }

    private void openDependencyPicker() {
        if (!dependencyEditorOpen || dependencyEditorQuestId == null) return;
        closeActiveEditorOverlay();
        dependencyFilter = "";
        dependencyPickerScroll = 0;
        dependencyEditorMessage = null;
        editorOverlays.show(EditorOverlayHost.Kind.DEPENDENCY_PICKER);
    }

    private void renderDependencyPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<QuestDependencyEditorModel.Candidate> candidates = dependencyCandidates();
        UiRect bounds = dependencyPickerBounds();
        int visibleRows = EditorPickerList.visibleRows(bounds);
        dependencyPickerScroll = Math.max(0, Math.min(Math.max(0, candidates.size() - visibleRows),
                dependencyPickerScroll));
        List<EditorPickerList.Entry> entries = candidates.stream().map(candidate -> new EditorPickerList.Entry(
                Component.literal((candidate.createsCycle() ? "⚠ " : "") + candidate.title()),
                dependencyCandidateSubtitle(candidate),
                candidate.createsCycle() ? EditorPickerList.Tone.WARNING : EditorPickerList.Tone.NORMAL)).toList();
        Component searchText = dependencyFilter.isBlank()
                ? Component.translatable("screen.brnquest.editor.dependency.search_hint")
                : Component.literal("⌕ " + dependencyFilter);
        graphics.fill(0, 0, width, height, 0x66000000);
        EditorPickerList.render(graphics, font, bounds, searchText, dependencyFilter.isBlank(),
                entries, dependencyPickerScroll, mouseX, mouseY);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.brnquest.editor.dependency.no_candidates"),
                    bounds.centerX(), bounds.top() + EditorPickerList.SEARCH_HEIGHT + 10, 0xFF9AA6B5);
        }
        int hovered = EditorPickerList.entryAt(bounds, dependencyPickerScroll, entries.size(), mouseX, mouseY);
        if (hovered >= 0) {
            QuestDependencyEditorModel.Candidate candidate = candidates.get(hovered);
            hoveredComponentTooltip = List.of(Component.translatable(candidate.createsCycle()
                    ? "screen.brnquest.editor.dependency.cycle_warning"
                    : "screen.brnquest.editor.dependency.click_to_add"));
        }
    }

    static Component dependencyCandidateSubtitle(QuestDependencyEditorModel.Candidate candidate) {
        if (!candidate.createsCycle()) {
            return Component.literal(candidate.chapterTitle());
        }
        return Component.translatable("screen.brnquest.editor.dependency.cycle_warning");
    }

    private boolean handleDependencyPickerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            closeActiveEditorOverlay();
            return true;
        }
        UiRect bounds = dependencyPickerBounds();
        if (!bounds.contains(mouseX, mouseY)) {
            closeActiveEditorOverlay();
            return true;
        }
        List<QuestDependencyEditorModel.Candidate> candidates = dependencyCandidates();
        int index = EditorPickerList.entryAt(bounds, dependencyPickerScroll, candidates.size(), mouseX, mouseY);
        if (index < 0) return true;
        QuestDependencyEditorModel.Candidate candidate = candidates.get(index);
        if (candidate.createsCycle()) {
            dependencyEditorMessage = Component.translatable("screen.brnquest.editor.dependency.cycle_warning");
            return true;
        }
        if (!ClientEditorState.get().busy()) {
            sendMutation("ADD_DEPENDENCY", dependencyEditorQuestId, null,
                    candidate.questId(), "", 0, 0, 0, List.of());
            dependencyEditorMessage = null;
            closeActiveEditorOverlay();
        }
        return true;
    }

    private List<QuestDependencyEditorModel.Candidate> dependencyCandidates() {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || dependencyEditorQuestId == null) return List.of();
        return QuestDependencyEditorModel.candidates(snapshot.book(), dependencyEditorQuestId, dependencyFilter);
    }

    private UiRect dependencyPickerBounds() {
        int maximumHeight = Math.max(EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT + 4,
                height - TOP_TOOLBAR_HEIGHT - BOTTOM_TOOLBAR_HEIGHT - 16);
        int desiredHeight = EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT * 8 + 4;
        return layout().centeredDialog(480, 300, 20, Math.min(desiredHeight, maximumHeight));
    }

    private int dependencyListTop() {
        return TOP_TOOLBAR_HEIGHT + 44;
    }

    private int dependencyVisibleRows() {
        return Math.max(1, (dependencyAddBounds().top() - 16 - dependencyListTop()) / DEPENDENCY_ROW_HEIGHT);
    }

    private UiRect dependencyAddBounds() {
        return questPropertyButtonBounds();
    }

    private UiRect dependencyDoneBounds() {
        return questDependencyButtonBounds();
    }

    private EditorTextField editorField(String translationKey, int maximumLength) {
        EditorTextField field = new EditorTextField(font, Component.translatable(translationKey), maximumLength);
        return addRenderableWidget(field);
    }

    private EditorTextField reinitializeEditorField(EditorTextField field, String translationKey, int maximumLength) {
        if (field == null) return editorField(translationKey, maximumLength);
        field.hide();
        return addRenderableWidget(field);
    }

    /** Registers a focused overlay field for input/narration while its owning overlay renders it manually. */
    private EditorTextField reinitializeOverlayEditorField(EditorTextField field, String translationKey,
                                                            int maximumLength) {
        if (field == null) {
            field = new EditorTextField(font, Component.translatable(translationKey), maximumLength);
        }
        field.hide();
        return addWidget(field);
    }

    /** Uses the middle section of the existing detail drawer as a compact property form. */
    private void renderQuestPropertyEditor(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        int left = detailLeft() + 10;
        int fieldWidth = DETAIL_WIDTH - 24;
        int fieldTop = TOP_TOOLBAR_HEIGHT + 20;
        int pitch = 22;
        graphics.fill(detailLeft() + 4, TOP_TOOLBAR_HEIGHT + 4, width - 4,
                height - BOTTOM_TOOLBAR_HEIGHT - 4, 0xFF202632);
        Component heading = questEditorMessage == null
                ? Component.translatable("screen.brnquest.editor.quest.heading") : questEditorMessage;
        graphics.drawString(font, Component.literal(font.plainSubstrByWidth(heading.getString(), fieldWidth)),
                left, TOP_TOOLBAR_HEIGHT + 7, questEditorMessage == null ? 0xFFFFFFFF : 0xFFFF8B8B, false);
        renderQuestEditorField(graphics, questIdField, "screen.brnquest.editor.quest.id",
                left, fieldTop, fieldWidth);
        renderQuestEditorField(graphics, questTitleField, "screen.brnquest.editor.quest.title",
                left, fieldTop + pitch, fieldWidth);
        renderQuestEditorField(graphics, questSubtitleField, "screen.brnquest.editor.quest.subtitle",
                left, fieldTop + pitch * 2, fieldWidth);
        renderQuestEditorField(graphics, questDescriptionField, "screen.brnquest.editor.quest.description",
                left, fieldTop + pitch * 3, fieldWidth);
        renderQuestIconEditorField(graphics,
                left, fieldTop + pitch * 4, fieldWidth, mouseX, mouseY);

        renderEditorTextButton(graphics, questEditorCancelBounds(), Component.translatable("gui.cancel"),
                null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        renderEditorTextButton(graphics, questEditorSaveBounds(), Component.translatable("gui.done"),
                null, !ClientEditorState.get().busy(), EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    private void renderQuestEditorField(GuiGraphics graphics, EditorTextField field, String labelKey,
                                        int left, int top, int width) {
        int labelWidth = 78;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, labelWidth);
        String label = font.plainSubstrByWidth(Component.translatable(labelKey).getString(), row.label().width() - 4);
        graphics.drawString(font, Component.literal(label), row.label().left(), row.label().top() + 5,
                0xFF9FB0C2, false);
        field.show(row.field(), !ClientEditorState.get().busy());
    }

    /** Shows an ordinary item ID while retaining the native SNBT representation behind the form. */
    private void renderQuestIconEditorField(GuiGraphics graphics, int left, int top, int width,
                                            int mouseX, int mouseY) {
        int labelWidth = 48;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, labelWidth);
        String label = font.plainSubstrByWidth(Component.translatable("screen.brnquest.editor.quest.icon").getString(),
                row.label().width() - 4);
        graphics.drawString(font, Component.literal(label), row.label().left(), row.label().top() + 5,
                0xFF9FB0C2, false);
        UiRect mode = new UiRect(row.field().left(), row.field().top(), row.field().left() + 42, row.field().bottom());
        renderEditorTextButton(graphics, mode, Component.translatable(questEditorIconMode.translationKey),
                null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        UiRect input = new UiRect(mode.right() + 3, row.field().top(), row.field().right() - 22, row.field().bottom());
        questIconField.show(input, !ClientEditorState.get().busy());
        ResourceLocation iconId = ResourceLocation.tryParse(questIconField.getValue().strip());
        if (questEditorIconMode == IconEditorMode.ITEM
                && iconId != null && BuiltInRegistries.ITEM.containsKey(iconId)) {
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_item");
            renderEditorActionButton(graphics, questIconPickerBounds(), EditorButton.Definition.iconOnly(
                            select, select, EditorIcon.item(BuiltInRegistries.ITEM.get(iconId).getDefaultInstance())),
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (questEditorIconMode == IconEditorMode.ITEM) {
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_item");
            renderEditorActionButton(graphics, questIconPickerBounds(), EditorButton.Definition.iconOnly(
                            select, select, EditorIcon.glyph(Component.literal(
                                    questIconField.getValue().isBlank() ? "+" : "?"))),
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (questEditorIconMode == IconEditorMode.TEXTURE && iconId != null) {
            graphics.blit(iconId, row.field().right() - 18, row.field().top() + 1,
                    0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            graphics.drawCenteredString(font, questIconField.getValue().isBlank() ? "−" : "?",
                    row.field().right() - 10, row.field().top() + 5, 0xFF9FB0C2);
        }
    }

    /** Reuses the ghost inventory/JEI Screen while the quest form retains identity-only item input. */
    private void openQuestIconItemSelector() {
        openEditorItemSelector(stack -> {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) return;
            String value = itemId.toString();
            questIconField.setValue(value);
            questEditorItemIconValue = value;
            questEditorMessage = null;
        });
    }

    private void positionEditorField(EditorTextField field, int x, int y, int fieldWidth) {
        field.show(new UiRect(x, y, x + fieldWidth, y + 18), !ClientEditorState.get().busy());
    }

    private void openQuestEditor(QuestDefinition quest) {
        if (ClientEditorState.get().busy()) return;
        closeDependencyEditor();
        closeTypedEditor();
        questEditorOpen = true;
        questEditorQuestId = quest.id();
        questEditorMessage = null;
        questIdField.setValue(quest.id().toString());
        questTitleField.setValue(quest.title());
        questSubtitleField.setValue(quest.subtitle());
        questDescriptionField.setValue(quest.description());
        questEditorIconMode = QuestIconValue.isTexture(quest.icon()) ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
        questEditorOriginalIconMode = questEditorIconMode;
        questEditorOriginalIconItemId = questEditorIconMode == IconEditorMode.TEXTURE
                ? QuestIconValue.textureId(quest.icon()).map(ResourceLocation::toString).orElse("")
                : iconItemId(quest.icon());
        questEditorItemIconValue = questEditorIconMode == IconEditorMode.ITEM ? questEditorOriginalIconItemId : "";
        questEditorTextureIconValue = questEditorIconMode == IconEditorMode.TEXTURE
                ? questEditorOriginalIconItemId : "";
        questIconField.setValue(questEditorOriginalIconItemId);
        setFocused(questIdField);
    }

    private void prepareQuestPropertyEdit() {
        ResourceLocation replacementId = ResourceLocation.tryParse(questIdField.getValue());
        if (replacementId == null) {
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.invalid_id");
            return;
        }
        String iconItemId = questIconField.getValue().strip();
        ResourceLocation parsedIcon = iconItemId.isEmpty() ? null : ResourceLocation.tryParse(iconItemId);
        if (!iconItemId.isEmpty() && (parsedIcon == null || (questEditorIconMode == IconEditorMode.ITEM
                && !BuiltInRegistries.ITEM.containsKey(parsedIcon)))) {
            questEditorMessage = Component.translatable(questEditorIconMode == IconEditorMode.ITEM
                    ? "screen.brnquest.editor.quest.invalid_icon" : "screen.brnquest.editor.quest.invalid_texture");
            return;
        }
        questEditorMessage = null;
        if (!replacementId.equals(questEditorQuestId)) {
            closeActiveEditorOverlay();
            editorOverlays.show(EditorOverlayHost.Kind.QUEST_RENAME_CONFIRMATION);
            return;
        }
        submitQuestPropertyEdit(replacementId);
    }

    private void submitQuestPropertyEdit(ResourceLocation replacementId) {
        ClientEditorState editor = ClientEditorState.get();
        if (editor.busy() || editor.sessionId() == null || editor.bookId() == null || questEditorQuestId == null) return;
        if (!editor.beginMutation()) return;
        ResourceLocation oldId = questEditorQuestId;
        // Icon rendering is cached by quest ID; invalidate both sides of a rename before the new draft arrives.
        itemCache.remove(oldId);
        itemCache.remove(replacementId);
        AuthoringNetwork.updateQuest(editor.sessionId(), editor.bookId(), editor.draftRevision(), questEditorQuestId,
                replacementId, questTitleField.getValue(), questSubtitleField.getValue(),
                questDescriptionField.getValue(), questEditorIconMode.name(), questIconField.getValue().strip(),
                questEditorIconMode == questEditorOriginalIconMode
                        && questIconField.getValue().strip().equals(questEditorOriginalIconItemId));
        editorSelectedQuest = replacementId;
        closeQuestEditingPanels();
        editorOverlays.close();
    }

    private void closeQuestEditor() {
        questEditorOpen = false;
        questEditorQuestId = null;
        questEditorMessage = null;
        questEditorOriginalIconItemId = "";
        questEditorIconMode = IconEditorMode.ITEM;
        questEditorOriginalIconMode = IconEditorMode.ITEM;
        questEditorItemIconValue = "";
        questEditorTextureIconValue = "";
        setFocused(null);
        for (EditorTextField field : List.of(questIdField, questTitleField, questSubtitleField,
                questDescriptionField, questIconField)) {
            if (field == null) continue;
            field.hide();
        }
    }

    private static String iconItemId(String iconSnbt) {
        if (iconSnbt == null || iconSnbt.isBlank()) return "";
        try {
            return TagParser.parseTag(iconSnbt).getString("id");
        } catch (Exception ignored) {
            // Unknown legacy icon data stays untouched until the author selects a replacement item.
            return "";
        }
    }

    /** Retains each mode's in-progress value so comparing item and texture choices is non-destructive. */
    private void rememberCurrentIconInput() {
        if (questEditorIconMode == IconEditorMode.ITEM) {
            questEditorItemIconValue = questIconField.getValue();
        } else {
            questEditorTextureIconValue = questIconField.getValue();
        }
    }

    private void renderQuestRenameConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        long references = displaySnapshot() == null || questEditorQuestId == null ? 0L
                : displaySnapshot().book().quests().stream()
                .filter(quest -> quest.dependencies().contains(questEditorQuestId)).count();
        EditorConfirmDialog.render(graphics, font, layout(),
                Component.translatable("screen.brnquest.editor.quest.rename.warning"),
                Component.translatable("screen.brnquest.editor.quest.rename.impact", references), 0xFFFFC06A,
                Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.quest.rename.confirm"), mouseX, mouseY);
    }

    private boolean handleQuestRenameConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        EditorConfirmDialog.Action action = EditorConfirmDialog.actionAt(layout(), mouseX, mouseY);
        if (action == EditorConfirmDialog.Action.CANCEL) {
            editorOverlays.close();
            return true;
        }
        if (action == EditorConfirmDialog.Action.CONFIRM) {
            ResourceLocation replacementId = ResourceLocation.tryParse(questIdField.getValue());
            if (replacementId != null) submitQuestPropertyEdit(replacementId);
        }
        return true;
    }

    private UiRect questPropertyButtonBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return questEditorTabBounds(0, bottom);
    }

    private UiRect questTaskButtonBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return questEditorTabBounds(1, bottom);
    }

    private UiRect questRewardButtonBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return questEditorTabBounds(2, bottom);
    }

    private UiRect questDependencyButtonBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return questEditorTabBounds(3, bottom);
    }

    private UiRect questEditorTabBounds(int index, int bottom) {
        int left = detailLeft() + 10;
        int available = width - 10 - left;
        int gap = 3;
        int tabWidth = (available - gap * 3) / 4;
        int tabLeft = left + index * (tabWidth + gap);
        int tabRight = index == 3 ? width - 10 : tabLeft + tabWidth;
        return new UiRect(tabLeft, bottom - 20, tabRight, bottom);
    }

    private UiRect questEditorCancelBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return new UiRect(detailLeft() + 10, bottom - 20, detailLeft() + 116, bottom);
    }

    private UiRect questEditorSaveBounds() {
        int bottom = height - BOTTOM_TOOLBAR_HEIGHT - 6;
        return new UiRect(detailLeft() + 124, bottom - 20, width - 10, bottom);
    }

    private UiRect questIconModeBounds() {
        int left = detailLeft() + 10;
        int top = TOP_TOOLBAR_HEIGHT + 20 + 22 * 4;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, DETAIL_WIDTH - 24, 48);
        return new UiRect(row.field().left(), row.field().top(), row.field().left() + 42, row.field().bottom());
    }

    private UiRect questIconPickerBounds() {
        int left = detailLeft() + 10;
        int top = TOP_TOOLBAR_HEIGHT + 20 + 22 * 4;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, DETAIL_WIDTH - 24, 48);
        return new UiRect(row.field().right() - 20, row.field().top(), row.field().right(), row.field().bottom());
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

    private UiRect editorPublishButtonBounds() {
        UiRect save = editorSaveButtonBounds();
        return new UiRect(save.left() - EDITOR_PUBLISH_BUTTON_WIDTH - 4, save.top(), save.left() - 4, save.bottom());
    }

    private UiRect editorRedoButtonBounds() {
        UiRect publish = editorPublishButtonBounds();
        return new UiRect(publish.left() - EDITOR_HISTORY_BUTTON_WIDTH - 4, publish.top(),
                publish.left() - 4, publish.bottom());
    }

    private UiRect editorUndoButtonBounds() {
        UiRect redo = editorRedoButtonBounds();
        return new UiRect(redo.left() - EDITOR_HISTORY_BUTTON_WIDTH - 4, redo.top(), redo.left() - 4, redo.bottom());
    }

    private void renderPublishConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        if (publishReview == null) return;
        EditorPublishReviewPanel.Layout reviewLayout = EditorPublishReviewPanel.layout(layout());
        UiRect panel = reviewLayout.panel();
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xFF202832);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.publish.review.title"),
                panel.centerX(), panel.top() + 9, 0xFFFFFFFF);
        int readinessColor = publishReview.publishAllowed() ? 0xFF83D69A : 0xFFFF8B8B;
        graphics.drawString(font, Component.translatable(publishReview.publishAllowed()
                        ? "screen.brnquest.editor.publish.review.ready"
                        : "screen.brnquest.editor.publish.review.blocked"),
                panel.left() + 12, panel.top() + 26, readinessColor, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.publish.review.revisions",
                        shortReviewRevision(publishReview.fromRevision()),
                        shortReviewRevision(publishReview.targetRevision())),
                panel.left() + 12, panel.top() + 40, 0xFFB7C5D8, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.publish.review.counts",
                        publishReview.diagnosticCount(), publishReview.changeCount(), publishReview.truncated()
                                ? Component.translatable("screen.brnquest.editor.publish.review.truncated").getString()
                                : ""),
                panel.left() + 12, panel.top() + 54, 0xFFB7C5D8, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.publish.review.backup"),
                panel.left() + 12, panel.top() + 68, 0xFFFFC06A, false);

        int rowCount = publishReviewRowCount();
        int maximum = EditorPublishReviewPanel.maximumScroll(reviewLayout, rowCount);
        publishReviewScroll = Math.max(0, Math.min(maximum, publishReviewScroll));
        graphics.enableScissor(reviewLayout.list().left(), reviewLayout.list().top(),
                reviewLayout.list().right(), reviewLayout.list().bottom());
        int visibleRows = EditorPublishReviewPanel.visibleRows(reviewLayout);
        for (int visibleIndex = 0; visibleIndex < visibleRows; visibleIndex++) {
            int rowIndex = publishReviewScroll + visibleIndex;
            if (rowIndex >= rowCount) break;
            renderPublishReviewRow(graphics, reviewLayout, rowIndex,
                    reviewLayout.list().top() + visibleIndex * EditorPublishReviewPanel.ROW_HEIGHT,
                    mouseX, mouseY);
        }
        graphics.disableScissor();
        EditorScrollbar.render(graphics, reviewLayout.list().right() + 2, reviewLayout.list().top(),
                reviewLayout.list().bottom(), rowCount * EditorPublishReviewPanel.ROW_HEIGHT,
                reviewLayout.list().height(), publishReviewScroll * EditorPublishReviewPanel.ROW_HEIGHT);
        renderEditorTextButton(graphics, reviewLayout.cancel(), Component.translatable("gui.cancel"),
                null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        renderEditorTextButton(graphics, reviewLayout.confirm(),
                Component.translatable(publishReview.publishAllowed()
                        ? "screen.brnquest.editor.publish.confirm"
                        : "screen.brnquest.editor.publish.blocked"),
                null, publishReview.publishAllowed(), EditorButton.Tone.DANGER, mouseX, mouseY);
    }

    /** Renders either a blocking diagnostic or one semantic change from the same bounded list used by hit testing. */
    private void renderPublishReviewRow(GuiGraphics graphics, EditorPublishReviewPanel.Layout layout,
                                        int rowIndex, int y, int mouseX, int mouseY) {
        UiRect row = new UiRect(layout.list().left(), y, layout.list().right(),
                y + EditorPublishReviewPanel.ROW_HEIGHT - 2);
        boolean hovered = row.contains(mouseX, mouseY);
        graphics.fill(row.left(), row.top(), row.right(), row.bottom(),
                hovered ? 0xFF354352 : (rowIndex % 2 == 0 ? 0xFF28313C : 0xFF242C36));
        String heading;
        String detail;
        List<Component> tooltip;
        if (rowIndex < publishReview.diagnostics().size()) {
            AuthoringNetwork.EditorDiagnosticWire diagnostic = publishReview.diagnostics().get(rowIndex);
            heading = diagnostic.severity() + " · " + diagnostic.code();
            detail = diagnostic.objectId() + (diagnostic.path().isBlank() ? "" : " · " + diagnostic.path());
            tooltip = List.of(Component.literal(heading), Component.literal(detail),
                    Component.literal(diagnostic.message()));
        } else if (publishReview.changes().isEmpty()) {
            heading = Component.translatable("screen.brnquest.editor.publish.review.no_changes").getString();
            detail = Component.translatable("screen.brnquest.editor.publish.review.no_changes.detail").getString();
            tooltip = List.of(Component.literal(heading), Component.literal(detail));
        } else {
            AuthoringNetwork.SemanticDiffWire change = publishReview.changes().get(
                    rowIndex - publishReview.diagnostics().size());
            heading = EditorPublishReviewText.heading(change).getString();
            detail = EditorPublishReviewText.detail(change).getString();
            List<Component> changeTooltip = new ArrayList<>();
            changeTooltip.add(Component.literal(heading));
            changeTooltip.add(Component.literal(detail));
            changeTooltip.addAll(EditorPublishReviewText.valueTooltip(change));
            tooltip = List.copyOf(changeTooltip);
        }
        int textWidth = Math.max(20, row.width() - 12);
        graphics.drawString(font, font.plainSubstrByWidth(heading, textWidth), row.left() + 5,
                row.top() + 3, 0xFFFFFFFF, false);
        graphics.drawString(font, font.plainSubstrByWidth(detail, textWidth), row.left() + 5,
                row.top() + 15, 0xFF9FB0C2, false);
        if (hovered) hoveredComponentTooltip = tooltip;
    }

    private boolean handlePublishConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        if (publishReview == null) return true;
        EditorPublishReviewPanel.Layout reviewLayout = EditorPublishReviewPanel.layout(layout());
        if (reviewLayout.cancel().contains(mouseX, mouseY)) {
            closeActiveEditorOverlay();
            return true;
        }
        if (publishReview.publishAllowed() && reviewLayout.confirm().contains(mouseX, mouseY)) {
            String reviewedRevision = publishReview.targetRevision();
            closeActiveEditorOverlay();
            ClientEditorState editor = ClientEditorState.get();
            // The preview is revision-bound; never confirm a different draft with stale review data.
            if (reviewedRevision.equals(editor.draftRevision())) {
                editor.beginPublish().ifPresent(request -> AuthoringNetwork.publishAndApply(
                        request.sessionId(), editor.bookId(), request.draftRevision()));
            }
            return true;
        }
        int row = EditorPublishReviewPanel.rowAt(reviewLayout, publishReviewScroll,
                publishReviewRowCount(), mouseX, mouseY);
        if (row >= 0) {
            ResourceLocation objectId = publishReviewObjectId(row);
            if (objectId != null && jumpToEditorObject(objectId)) closeActiveEditorOverlay();
        }
        return true;
    }

    private int publishReviewRowCount() {
        if (publishReview == null) return 0;
        // Keep a visible semantic-diff result even when the server returned an empty change list.
        return publishReview.diagnostics().size() + Math.max(1, publishReview.changes().size());
    }

    private ResourceLocation publishReviewObjectId(int rowIndex) {
        String raw;
        if (rowIndex < publishReview.diagnostics().size()) {
            raw = publishReview.diagnostics().get(rowIndex).objectId();
        } else if (publishReview.changes().isEmpty()) {
            return null;
        } else {
            raw = publishReview.changes().get(rowIndex - publishReview.diagnostics().size()).objectId();
        }
        return ResourceLocation.tryParse(raw);
    }

    /** Selects the closest surviving chapter or quest represented by a diagnostic/diff object ID. */
    private boolean jumpToEditorObject(ResourceLocation objectId) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return false;
        QuestBookDefinition book = snapshot.book();
        ChapterDefinition chapter = book.chapters().stream()
                .filter(candidate -> candidate.id().equals(objectId)).findFirst().orElse(null);
        QuestDefinition quest = snapshot.quests().get(objectId);
        if (quest == null) {
            quest = book.quests().stream().filter(candidate -> candidate.tasks().stream()
                            .anyMatch(task -> task.id().equals(objectId)) || candidate.rewards().stream()
                            .anyMatch(reward -> reward.id().equals(objectId)))
                    .findFirst().orElse(null);
        }
        if (quest != null) {
            ResourceLocation targetChapter = quest.chapterId();
            chapter = book.chapters().stream().filter(candidate -> candidate.id().equals(targetChapter))
                    .findFirst().orElse(null);
            editorSelectedQuest = quest.id();
            selectOnly(quest.id());
            detailsOpen = true;
            detailScroll = 0;
        }
        if (chapter == null) return quest != null;
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(book);
        int index = chapters.indexOf(chapter);
        if (index >= 0) {
            chapterIndex = index;
            rememberedChapterId = chapter.id();
            rememberedChapterResolved = true;
            navigationScroll = 0;
        }
        return true;
    }

    private static String shortReviewRevision(String revision) {
        if (revision == null || revision.isBlank()) return "<none>";
        return revision.length() <= 12 ? revision : revision.substring(0, 12);
    }

    private void requestDiscardConfirmation(ResourceLocation switchTarget, boolean closeScreen) {
        closeQuestEditingPanels();
        closeActiveEditorOverlay();
        discardSwitchTarget = switchTarget;
        discardClosesScreen = closeScreen;
        editorOverlays.show(EditorOverlayHost.Kind.DISCARD_CONFIRMATION);
    }

    private void renderDiscardConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        EditorConfirmDialog.render(graphics, font, layout(),
                Component.translatable("screen.brnquest.editor.discard.warning"), null, 0,
                Component.translatable("screen.brnquest.editor.discard.cancel"),
                Component.translatable("screen.brnquest.editor.discard.confirm"), mouseX, mouseY);
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
        return itemCache.get(cacheId, snbt, serialized -> {
            if (serialized.isBlank() || minecraft.level == null) return ItemStack.EMPTY;
            try {
                return ItemStack.parseOptional(minecraft.level.registryAccess(), TagParser.parseTag(serialized));
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

    /** Shrinks compact one-line labels before truncating them, preserving more meaningful text. */
    private void drawFittedString(GuiGraphics graphics, Component text, int x, int y,
                                  int maximumWidth, int color, float minimumScale) {
        int measuredWidth = font.width(text);
        float scale = EditorTextLayout.fittedScale(measuredWidth, maximumWidth, minimumScale);
        int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
        String visible = font.plainSubstrByWidth(text.getString(), unscaledWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
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

    private static String chapterTitle(QuestBookDefinition book, ResourceLocation chapterId) {
        return book.chapters().stream().filter(chapter -> chapter.id().equals(chapterId))
                .map(ChapterDefinition::title).findFirst().orElse("");
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

    private record DependencyHitbox(UiRect rowBounds, UiRect removeBounds, ResourceLocation dependencyId) {}

    private record TypedEditorHitbox(ResourceLocation id, int index, UiRect row, UiRect edit, UiRect more) {}

    private record QuickTextHitbox(QuickTextKind kind, UiRect bounds) {}

    private record TypedValue(ResourceLocation id, ResourceLocation typeId) {}

    private record TypedRowPresentation(Component typeName, String symbol, ItemStack stack) {}

    private record TypedEntry(ResourceLocation id, ResourceLocation typeId, Map<String, String> config,
                              boolean optional, String claimPolicy, boolean teamReward,
                              TaskDefinition task, RewardDefinition reward) {
        static TypedEntry task(TaskDefinition task) {
            return new TypedEntry(task.id(), task.typeId(), task.config(), task.optional(), "", false, task, null);
        }

        static TypedEntry reward(RewardDefinition reward) {
            return new TypedEntry(reward.id(), reward.typeId(), reward.config(), false,
                    reward.claimPolicy(), reward.teamReward(), null, reward);
        }
    }

    private enum IconEditorMode {
        ITEM("screen.brnquest.editor.quest.icon_mode.item"),
        TEXTURE("screen.brnquest.editor.quest.icon_mode.texture");

        private final String translationKey;

        IconEditorMode(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private enum ContextKind { NONE, CANVAS, NODE, GROUP, CHAPTER, TYPED_ENTRY, DEPENDENCY_ENTRY }

    private enum QuickTextKind {
        NONE(""),
        TITLE("screen.brnquest.editor.quest.title"),
        SUBTITLE("screen.brnquest.editor.quest.subtitle"),
        DESCRIPTION("screen.brnquest.editor.quest.description");

        private final String translationKey;

        QuickTextKind(String translationKey) {
            this.translationKey = translationKey;
        }

        String translationKey() {
            return translationKey;
        }
    }

    private enum DeleteKind { NONE, GROUP, CHAPTER, QUEST }

    private enum TypedKind {
        TASK("TASK", "task", "screen.brnquest.editor.typed.tasks_heading"),
        REWARD("REWARD", "reward", "screen.brnquest.editor.typed.rewards_heading");

        private final String actionPrefix;
        private final String idStem;
        private final String headingKey;

        TypedKind(String actionPrefix, String idStem, String headingKey) {
            this.actionPrefix = actionPrefix;
            this.idStem = idStem;
            this.headingKey = headingKey;
        }

        String actionPrefix() { return actionPrefix; }
        String idStem() { return idStem; }
        String headingKey() { return headingKey; }

        int size(QuestDefinition quest) {
            return this == TASK ? quest.tasks().size() : quest.rewards().size();
        }

        TypedValue value(QuestDefinition quest, int index) {
            if (this == TASK) {
                TaskDefinition task = quest.tasks().get(index);
                return new TypedValue(task.id(), task.typeId());
            }
            RewardDefinition reward = quest.rewards().get(index);
            return new TypedValue(reward.id(), reward.typeId());
        }

        TypedEntry entry(QuestDefinition quest, ResourceLocation id) {
            if (this == TASK) {
                return quest.tasks().stream().filter(task -> task.id().equals(id))
                        .findFirst().map(TypedEntry::task).orElse(null);
            }
            return quest.rewards().stream().filter(reward -> reward.id().equals(id))
                    .findFirst().map(TypedEntry::reward).orElse(null);
        }

        List<ResourceLocation> builtIns() {
            return this == TASK
                    ? List.of(TaskTypes.CHECKMARK, TaskTypes.CUSTOM, TaskTypes.ITEM)
                    : List.of(RewardTypes.CUSTOM, RewardTypes.ITEM);
        }

        boolean known(ResourceLocation typeId) { return builtIns().contains(typeId); }

        boolean addable(ResourceLocation typeId) {
            return this == TASK
                    ? typeId.equals(TaskTypes.CHECKMARK) || typeId.equals(TaskTypes.CUSTOM)
                            || typeId.equals(TaskTypes.ITEM)
                    : typeId.equals(RewardTypes.CUSTOM) || typeId.equals(RewardTypes.ITEM);
        }

        boolean itemBacked(ResourceLocation typeId) {
            return this == TASK ? typeId.equals(TaskTypes.ITEM) : typeId.equals(RewardTypes.ITEM);
        }
    }

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
