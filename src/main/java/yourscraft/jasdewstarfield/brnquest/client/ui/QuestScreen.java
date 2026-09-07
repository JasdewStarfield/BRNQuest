package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.ContentAwareCache;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorActionGroup;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorEntryRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorEntryListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListViewport;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPickerList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPopupMenu;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorFormFields;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorQuickTextDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothValue;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSelectionFocus;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestModeSelection;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestIconEditorRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestNodeGeometry;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupHint;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupSource;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TransientChildScreenParent;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.TransientScreenLifecycle;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBehavior;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Quest-book UI with grouped navigation, a scalable directed graph, and intent-only details. */
public final class QuestScreen extends Screen implements RecipeLookupSource, TransientChildScreenParent {
    private static final int NAV_LEFT = 0;
    private static final int CANVAS_MARGIN = 0;
    private static final int NODE_BASE_SIZE = 18;
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
    private static final int DEPENDENCY_ROW_HEIGHT = 32;
    private static final int TYPED_ROW_HEIGHT = 38;
    private static final int MAX_TYPED_CONFIG_FIELDS = 8;
    private static final int ATTENTION_PING_SIZE = 10;
    private static final List<String> QUEST_SHAPES = List.of("chamfer", "square", "circle", "diamond");
    private static final List<String> REWARD_CLAIM_POLICIES = java.util.Arrays.stream(RewardClaimPolicy.values())
            .map(RewardClaimPolicy::serializedName).toList();
    private static final ResourceLocation REWARD_PING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BRNQuest.MOD_ID, "textures/gui/reward_ping.png");
    private static final ResourceLocation SUBMITTABLE_PING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BRNQuest.MOD_ID, "textures/gui/submitable_ping.png");

    private double panX;
    private double panY;
    private double zoom;
    private double renderedPanX;
    private double renderedPanY;
    private double renderedZoom;
    private int attentionPingOffsetY;
    private long previousMotionFrameNanos;
    private long lastCooldownRecoveryRequestMillis;
    private double currentMotionFrameSeconds = 1.0 / 60.0;
    // Zoom needs a much smaller terminal snap than pixel scrolling; 0.01 zoom is visibly abrupt.
    private final EditorSmoothValue zoomMotion = new EditorSmoothValue(1.0, 0.00001);
    private final EditorSmoothValue navigationDrawerMotion = new EditorSmoothValue(1.0, 0.001);
    private final EditorSmoothValue detailsDrawerMotion = new EditorSmoothValue(0.0, 0.001);
    private final QuestNavigationPanel navigationPanel = new QuestNavigationPanel();
    private final QuestDetailsPanel detailsPanel = new QuestDetailsPanel();
    private int navigationContentHeight;
    private int chapterIndex;
    private QuestScreenLayout cachedLayout;
    // Scoped to one render call; this is not a second revision cache.
    private RenderFrame renderFrame;
    private record RenderFrame(QuestBookSnapshot snapshot, QuestScreenLayout layout,
                               QuestScreenFrameIdentity identity) {}
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
    private boolean editorSelectionMode;
    private boolean catalogRequested;
    private final EditorPickerList<ClientEditorState.CatalogEntry> catalogPicker = new EditorPickerList<>();
    private String catalogFilter = "";
    private String questEditorOriginalIconItemId = "";
    private IconEditorMode questEditorIconMode = IconEditorMode.ITEM;
    private IconEditorMode questEditorOriginalIconMode = IconEditorMode.ITEM;
    private String questEditorItemIconValue = "";
    private String questEditorTextureIconValue = "";
    private QuestIconEditorRow.Layout questIconRowLayout;
    private UiRect questLocalizedTextEditorBounds;
    private UiRect questShapeDropdownBounds;
    private UiRect questBehaviorEditorBounds;
    private QuestBehavior questEditorBehavior = QuestBehavior.DEFAULT;
    private final EditorFormFields<String> questFields = new EditorFormFields<String>()
            .define("title", "screen.brnquest.editor.quest.title", 256)
            .define("subtitle", "screen.brnquest.editor.quest.subtitle", 256)
            .define("description", "screen.brnquest.editor.quest.description", 32768)
            .define("id", "screen.brnquest.editor.quest.id", 256)
            .define("icon", "screen.brnquest.editor.quest.icon", 256)
            .define("x", "screen.brnquest.editor.quest.position_x", 64)
            .define("y", "screen.brnquest.editor.quest.position_y", 64)
            .define("shape", "screen.brnquest.editor.quest.shape", 32)
            .define("size", "screen.brnquest.editor.quest.size", 32)
            .define("icon_scale", "screen.brnquest.editor.quest.icon_scale", 32)
            .define("min_width", "screen.brnquest.editor.quest.min_width", 32);
    private final EditorFormFields<String> structureFields = new EditorFormFields<String>()
            .define("id", "screen.brnquest.editor.structure.id", 256)
            .define("title", "screen.brnquest.editor.structure.title", 256);
    private EditorTextField quickTextField;
    private QuickTextKind quickTextKind = QuickTextKind.NONE;
    private ResourceLocation quickTextQuestId;
    private Component quickTextIssue;
    private boolean questEditorOpen;
    private ResourceLocation questEditorQuestId;
    private Component questEditorMessage;
    private boolean dependencyEditorOpen;
    private ResourceLocation dependencyEditorQuestId;
    private final EditorListPanel<ResourceLocation> dependencyList = new EditorListPanel<>();
    private final EditorActionGroup<ResourceLocation> dependencyActions = new EditorActionGroup<>();
    private QuestBookSnapshot dependencyListSnapshot;
    private String dependencyFilter = "";
    private final EditorPickerList<QuestDependencyEditorModel.Candidate> dependencyPicker = new EditorPickerList<>();
    private Component dependencyEditorMessage;
    private boolean typedEditorOpen;
    private QuestTypedEntryKind typedEditorKind = QuestTypedEntryKind.TASK;
    private ResourceLocation typedEditorQuestId;
    private final QuestTypedEntryListSection typedEntryList = new QuestTypedEntryListSection();
    private final EditorPickerList<ResourceLocation> typedTypePicker = new EditorPickerList<>();
    private QuestTypePickerModel.Frame typedTypePickerFrame;
    private Component typedEditorMessage;
    private final QuestTypedPropertySection typedPropertySection =
            new QuestTypedPropertySection(MAX_TYPED_CONFIG_FIELDS);
    private Component typedPropertyMessage;
    private UiRect enumDropdownAnchor;
    private List<String> enumDropdownValues = List.of();
    private Consumer<String> enumDropdownConsumer;
    private ResourceLocation discardSwitchTarget;
    private boolean discardClosesScreen;
    private ResourceLocation draftChoiceBookId;
    private String draftChoiceActiveRevision = "";
    private String draftChoiceDraftRevision = "";
    private String draftChoiceTitle = "";
    private final TransientScreenLifecycle childLifecycle = new TransientScreenLifecycle();
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
    private final EditorSelectionFocus<ResourceLocation> selectionFocus = new EditorSelectionFocus<>();
    private final QuestNodeDrag nodeDrag = new QuestNodeDrag();
    private final ContentAwareCache<ResourceLocation, String, ItemStack> itemCache = new ContentAwareCache<>();
    private final List<RewardHitbox> rewardHitboxes = new ArrayList<>();
    private final List<TaskHitbox> taskHitboxes = new ArrayList<>();
    private final List<TaskCandidateHitbox> taskCandidateHitboxes = new ArrayList<>();
    private final List<QuickTextHitbox> quickTextHitboxes = new ArrayList<>();
    private ItemStack hoveredDetailStack = ItemStack.EMPTY;
    private Component hoveredDetailText;
    private List<Component> hoveredComponentTooltip = List.of();
    private RecipeLookupTarget hoveredRecipeLookupTarget;
    private AuthoringNetwork.PublishReviewWire publishReview;
    private final EditorSmoothScroll publishReviewScroll = new EditorSmoothScroll();
    private ResourceLocation recoveryCopyBookId;
    private int editorKeyboardFocus = -1;

    public QuestScreen() {
        super(Component.translatable("screen.brnquest.title"));
        QuestScreenSessionState.Snapshot defaults = QuestScreenSessionState.Snapshot.defaults();
        zoom = defaults.zoom();
        zoomMotion.snap(zoom);
        renderedZoom = zoom;
        navigationCollapsed = defaults.navigationCollapsed();
        navigationDrawerMotion.snap(navigationCollapsed ? 0.0 : 1.0);
        editorSelectionMode = ClientEditorState.get().draft().isPresent();
    }

    @Override
    protected void init() {
        super.init();
        childLifecycle.returnedToParent();
        navigationPanel.invalidate();
        configureOverlayRoutes();
        // Resize/return from a child invalidates old hit geometry, not the remembered scroll position.
        typedEntryList.invalidate();
        typedTypePickerFrame = null;
        dependencyList.invalidate();
        dependencyListSnapshot = null;
        dependencyActions.clear();
        catalogPicker.invalidate();
        dependencyPicker.invalidate();
        typedTypePicker.invalidate();
        clearRenderedPropertyGeometry();
        // setScreen(parent) initializes this same QuestScreen again after an item/raw-config child
        // closes. Re-register existing fields instead of replacing them, preserving the complete
        // in-progress form (stable ID, semantics, config values, cursor, and selection).
        questFields.bind(font, this::addRenderableWidget);
        structureFields.bind(font, this::addRenderableWidget);
        typedPropertySection.bind(font, this::addRenderableWidget);
        quickTextField = reinitializeOverlayEditorField(quickTextField,
                "screen.brnquest.editor.quick_edit.input", 32_768);
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
        if (childLifecycle.consumeRemoval()) {
            // A normal setScreen call gives JEI its full Opening/Init lifecycle. The selector owns
            // this same QuestScreen as a suspended parent, so this removal must not close its lease.
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
        reconcileModeSelection(editor);
        if (questEditorOpen && !editor.editing()) closeQuestEditor();
        if (dependencyEditorOpen && !editor.editing()) closeDependencyEditor();
        if ((!editor.editing() && switch (editorOverlays.active()) {
                case CONTEXT_MENU, ENUM_DROPDOWN, STRUCTURE_FORM, DELETE_CONFIRMATION, QUEST_RENAME_CONFIRMATION,
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
            publishReviewScroll.snap(0);
            editorOverlays.show(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION);
        });
        reconcileDragPreview();
        requestExpiredRepeatRecovery();
    }

    /**
     * A server tick normally reopens repeatable quests. This bounded recovery request prevents one
     * missed delta from leaving an open screen permanently stuck on the completed prior cycle.
     */
    private void requestExpiredRepeatRecovery() {
        if (!gameplayAllowed()) return;
        QuestBookSnapshot snapshot = ClientQuestState.get().book().orElse(null);
        if (snapshot == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCooldownRecoveryRequestMillis < 1_000L) return;
        boolean refreshDue = snapshot.book().quests().stream().anyMatch(quest -> {
            QuestStatus current = status(quest);
            if (!quest.behavior().repeatable() || !isCompleted(current)) return false;
            long deadline = ClientQuestState.get().nextAvailableAt(quest.id());
            if (deadline <= 0 || deadline > now) return false;
            return quest.behavior().ignoreRewardBlocking() || quest.rewards().isEmpty()
                    || quest.rewards().stream().allMatch(reward -> ClientQuestState.get().claimed()
                    .contains(reward.id().toString()));
        });
        if (!refreshDue) return;
        lastCooldownRecoveryRequestMillis = now;
        BrnQuestNetwork.requestBook(ClientQuestState.get().revision());
    }

    /**
     * Transfers the visible task by stable ID exactly once when the authoritative display source
     * changes between the runtime snapshot and an editor draft. Missing IDs close details instead
     * of reviving the target mode's unrelated historical selection.
     */
    private void reconcileModeSelection(ClientEditorState editor) {
        boolean nextEditorMode = editor.draft().isPresent();
        boolean modeChanged = nextEditorMode != editorSelectionMode;
        QuestBookSnapshot target = nextEditorMode
                ? editor.draft().orElse(null) : ClientQuestState.get().book().orElse(null);
        ResourceLocation preferred = editorSelectionMode
                ? editorSelectedQuest : ClientQuestState.get().selected();
        Set<ResourceLocation> targetIds = target == null ? Set.of() : target.quests().keySet();
        QuestModeSelection.Result result = QuestModeSelection.resolve(preferred, detailsOpen, targetIds,
                nextEditorMode ? ignored -> true : ClientQuestState.get()::visible);
        boolean selectionChanged = !Objects.equals(preferred, result.selectedId())
                || detailsOpen != result.detailsOpen();
        // Recheck within one mode as well: an accepted delete mutation can replace the draft snapshot in place.
        if (!modeChanged && !selectionChanged) return;

        if (nextEditorMode) {
            editorSelectedQuest = result.selectedId();
            editorSelection.clear();
            if (result.selectedId() != null) selectOnly(result.selectedId());
        } else {
            ClientQuestState.get().selected(result.selectedId());
            if (result.selectedId() != null) {
                // Match an ordinary view-mode node selection so the explicit protocol intent
                // remains consistent even though selection currently has no authority effect.
                BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), result.selectedId().toString());
            }
        }
        detailsOpen = result.detailsOpen();
        if (result.selectedId() != null && target != null) {
            selectChapterContaining(target, result.selectedId());
        } else {
            detailsPanel.scroll().snap(0);
            closeQuestEditingPanels();
        }
        editorSelectionMode = nextEditorMode;
    }

    private void selectChapterContaining(QuestBookSnapshot snapshot, ResourceLocation questId) {
        QuestDefinition quest = snapshot.quests().get(questId);
        if (quest == null) return;
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
        for (int index = 0; index < chapters.size(); index++) {
            if (chapters.get(index).id().equals(quest.chapterId())) {
                chapterIndex = index;
                rememberedChapterId = quest.chapterId();
                rememberedChapterResolved = true;
                return;
            }
        }
    }

    /** Prevents Screen.render from applying a second blur pass over the completed UI. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Network work can install or clear the draft between two screen ticks. Reconcile here
        // before this frame reads either snapshot or selection, preventing a transient null task
        // from cancelling and restarting an otherwise unchanged auto-focus animation.
        reconcileModeSelection(ClientEditorState.get());
        // Tooltips are collected by content layers and rendered only after every opaque panel.
        hoveredDetailStack = ItemStack.EMPTY;
        hoveredDetailText = null;
        hoveredComponentTooltip = List.of();
        hoveredRecipeLookupTarget = null;
        // Blur the world once, then render every BRNQuest layer above it.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0xC8151820);
        var snapshot = displaySnapshot();
        if (snapshot == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.brnquest.loading"), width / 2, height / 2, 0xFFFFFF);
            return;
        }

        ensureViewportBook(snapshot.book().id());
        renderFrame = new RenderFrame(snapshot, layout(),
                QuestScreenFrameIdentity.of(snapshot, ClientEditorState.get().editing(), width, height));
        try {
            double motionFrameSeconds = motionFrameSeconds();
            currentMotionFrameSeconds = motionFrameSeconds;
            advanceZoomMotion(motionFrameSeconds);
            advanceDrawerMotion(motionFrameSeconds);
            // Sample once per frame so every visible notification hops in lockstep.
            attentionPingOffsetY = AttentionPingAnimation.verticalOffset(System.nanoTime());

            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            ChapterDefinition selectedChapter = null;
            if (!chapters.isEmpty()) {
                resolveRememberedChapter(chapters);
                chapterIndex = Math.min(chapterIndex, chapters.size() - 1);
                selectedChapter = chapters.get(chapterIndex);
            }
            updateSelectedQuestFocus(snapshot, motionFrameSeconds);
            renderNavigation(graphics, snapshot.book(), selectedChapter, mouseX, mouseY, motionFrameSeconds);
            if (!structureFormOpen()) renderCanvas(graphics, selectedChapter, mouseX, mouseY);
            else graphics.fill(canvasLeft(), topToolbarHeight(), canvasRight(),
                    height - bottomToolbarHeight(), 0xD0151820);
            if (detailsDrawerVisible() && !structureFormOpen()) {
                int visibleLeft = canvasRight();
                graphics.enableScissor(visibleLeft, topToolbarHeight(), width, height - bottomToolbarHeight());
                int detailMouseX = detailsPanelAcceptsPointer(mouseX) ? mouseX : Integer.MIN_VALUE;
                int detailMouseY = detailsPanelAcceptsPointer(mouseX) ? mouseY : Integer.MIN_VALUE;
                graphics.pose().pushPose();
                graphics.pose().translate(detailsDrawerOffsetX(), 0, 0);
                renderDetails(graphics, detailMouseX, detailMouseY, motionFrameSeconds);
                graphics.pose().popPose();
                graphics.disableScissor();
            }
            renderEditorChrome(graphics, snapshot.book(), mouseX, mouseY);
            if (structureFormOpen()) renderStructureForm(graphics, mouseX, mouseY);
            offsetDetailsDrawerFieldsForMotion();
            super.render(graphics, mouseX, mouseY, partialTick);
            renderDeferredTooltip(graphics, mouseX, mouseY);
        } finally {
            renderFrame = null;
        }
    }

    private void renderNavigation(GuiGraphics graphics, QuestBookDefinition book, ChapterDefinition selectedChapter,
                                  int mouseX, int mouseY, double motionFrameSeconds) {
        navigationContentHeight = navigationPanel.contentHeight(book);
        navigationPanel.render(graphics, font, book, selectedChapter,
                new QuestNavigationPanel.Layout(navigationWidth(), navigationTop(), height - navigationBottomMargin(),
                        navigationListBottom(), navigationHandleLeft(), navigationDrawerOffsetX(),
                        navigationHandleWidth(), contentCenterY(), navigationCollapsed),
                motionFrameSeconds, scrollSmoothSpeed(), () -> {
                    if (ClientEditorState.get().editing()) renderNavigationEditorButtons(graphics,
                            navigationPanelAcceptsPointer(mouseX) ? mouseX : Integer.MIN_VALUE,
                            navigationPanelAcceptsPointer(mouseX) ? mouseY : Integer.MIN_VALUE);
                });
    }

    private void renderCanvas(GuiGraphics graphics, ChapterDefinition chapter, int mouseX, int mouseY) {
        int right = canvasRight() - CANVAS_MARGIN;
        int top = topToolbarHeight();
        int bottom = height - bottomToolbarHeight();
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
        nodeDrag.update(graphMouseX, graphMouseY, mouseX, mouseY, System.nanoTime());
        graphics.pose().pushPose();
        graphics.pose().translate((float) graphOriginX(), (float) graphOriginY(), 0.0F);
        graphics.pose().scale((float) renderedZoom, (float) renderedZoom, 1.0F);
        renderGrid(graphics, graphLeft, graphRight, graphTop, graphBottom);

        if (chapter != null) {
            Map<ResourceLocation, QuestDefinition> chapterQuests = new HashMap<>();
            chapter.quests().stream().filter(this::questVisible).forEach(quest -> chapterQuests.put(quest.id(), quest));
            for (QuestDefinition quest : chapter.quests()) {
                if (!questVisible(quest)) continue;
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
                if (!questVisible(quest)) continue;
                renderNodeSnapGhost(graphics, quest, graphLeft, graphRight, graphTop, graphBottom);
            }
            for (QuestDefinition quest : chapter.quests()) {
                if (!questVisible(quest)) continue;
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
        boolean pickedUp = nodeDrag.pickedUp() && quest.id().equals(nodeDrag.anchor());
        int size = nodeSize(quest) + (pickedUp ? 4 : 0);
        int radius = size / 2;
        // Keep partially visible nodes; cull only after their entire bounds leave the canvas.
        if (!QuestViewportMath.intersectsViewport(x, y, radius, left, right, top, bottom)) return;

        boolean editing = ClientEditorState.get().editing();
        QuestStatus status = status(quest);
        int color = !gameplayAllowed() ? 0xFF4A6A88 : switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF4C9A66;
            case AVAILABLE, ACTIVE -> 0xFFCF9F42;
            default -> 0xFF59606B;
        };
        boolean selected = editing ? editorSelection.contains(quest.id()) : quest.id().equals(selectedQuestId());
        boolean tracked = gameplayAllowed() && status == QuestStatus.ACTIVE;
        if (tracked) fillNodeShape(graphics, quest.appearance().shape(), x, y, size + 7, 0xFF57C7F2);
        fillNodeShape(graphics, quest.appearance().shape(), x, y, size + (selected ? 4 : 2),
                selected ? 0xFF91C9F4 : 0xFF222936);
        fillNodeShape(graphics, quest.appearance().shape(), x, y, size, color);
        renderQuestVisual(graphics, quest, x, y, size);
        boolean attentionTask = gameplayAllowed() && questHasAttentionTask(quest, status);
        boolean pendingReward = gameplayAllowed()
                && QuestPresentation.hasPendingReward(quest, status, ClientQuestState.get().claimed());
        // These states are mutually exclusive in normal progression, so both authored badges share
        // the clearer top-right anchor instead of reserving opposite corners.
        if (attentionTask) {
            renderNodeAttentionPing(graphics, SUBMITTABLE_PING_TEXTURE, x, y, size);
        }
        if (pendingReward) {
            renderNodeAttentionPing(graphics, REWARD_PING_TEXTURE, x, y, size);
        }

        if (!nodeDrag.active() && mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom
                && Math.abs(mouseX - x) <= radius && Math.abs(mouseY - y) <= radius) {
            List<Component> tooltip = new ArrayList<>();
            boolean hiddenText = !editing && quest.behavior().hideTextUntilComplete() && !isCompleted(status);
            tooltip.add(Component.literal(hiddenText ? "???" : questTitle(quest)).withStyle(ChatFormatting.WHITE));
            String subtitle = hiddenText ? "" : localizedQuestText(quest, "quest_subtitle", quest.subtitle());
            if (!subtitle.isBlank()) {
                tooltip.add(Component.literal(subtitle).withStyle(ChatFormatting.GRAY));
            }
            hoveredComponentTooltip = List.copyOf(tooltip);
        }
    }

    /** Draws the server-bound grid destination below the freely moving picked-up node. */
    private void renderNodeSnapGhost(GuiGraphics graphics, QuestDefinition quest, double left, double right,
                                     double top, double bottom) {
        DraftBookEditor.Position target = nodeDrag.snapPreview(quest.id());
        if (!nodeDrag.pickedUp() || target == null) return;
        int x = graphCoordinate(target.x());
        int y = graphCoordinate(target.y());
        int radius = NODE_BASE_SIZE / 2 + 3;
        if (!QuestViewportMath.intersectsViewport(x, y, radius, left, right, top, bottom)) return;
        fillChamfer(graphics, x, y, NODE_BASE_SIZE + 6, 0x9091C9F4);
        fillChamfer(graphics, x, y, NODE_BASE_SIZE + 2, 0xB0202632);
        graphics.drawCenteredString(font, Component.literal("◇"), x, y - 4, 0xD091C9F4);
    }

    private void renderQuestVisual(GuiGraphics graphics, QuestDefinition quest, int x, int y, int size) {
        QuestPresentation.QuestVisual visual = QuestPresentation.visual(quest);
        if (visual.kind() == QuestPresentation.VisualKind.TEXTURE) {
            int iconSize = Math.max(1, (int) Math.round(size * safeAppearanceScale(quest.appearance().iconScale())));
            QuestIconValue.textureId(visual.value()).ifPresent(texture ->
                    graphics.blit(texture, x - iconSize / 2, y - iconSize / 2, 0.0F, 0.0F,
                            iconSize, iconSize, iconSize, iconSize));
            return;
        }
        if (visual.kind() == QuestPresentation.VisualKind.ITEM) {
            ItemStack stack = item(quest.id(), visual.itemSnbt());
            if (!stack.isEmpty()) {
                float scale = Math.max(0.25F, (float) (size / 18.0F * safeAppearanceScale(quest.appearance().iconScale())));
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
        float scale = Math.max(0.5F, (float) (size / 18.0F * safeAppearanceScale(quest.appearance().iconScale())));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, symbol, 0, -font.lineHeight / 2, 0xFFFFFFFF);
        graphics.pose().popPose();
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY, double motionFrameSeconds) {
        rewardHitboxes.clear();
        taskHitboxes.clear();
        taskCandidateHitboxes.clear();
        quickTextHitboxes.clear();
        int left = detailLeft();
        graphics.fill(left, topToolbarHeight(), width, height - bottomToolbarHeight(), 0xF0202632);
        graphics.drawString(font, Component.literal("×"), width - 14, topToolbarHeight() + 4, 0xFFFFFF, false);

        QuestDefinition quest = selectedQuest();
        if (quest == null) return;
        QuestStatus status = status(quest);
        boolean editing = ClientEditorState.get().editing();
        if (!editing && quest.behavior().hideDetailsUntilStartable()
                && status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE && !isCompleted(status)) {
            graphics.drawString(font, Component.translatable("screen.brnquest.quest_details_hidden"),
                    left + 12, detailContentTop() + 8, 0xFF9AA6B5, false);
            return;
        }
        if (editing && questEditorOpen && quest.id().equals(questEditorQuestId)) {
            detailsPanel.reset();
            renderQuestPropertyEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        if (editing && dependencyEditorOpen && quest.id().equals(dependencyEditorQuestId)) {
            detailsPanel.reset();
            renderDependencyEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        if (editing && typedEditorOpen && quest.id().equals(typedEditorQuestId)) {
            detailsPanel.reset();
            renderTypedEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        Component cooldownText = repeatCooldownText(quest, status);
        Component statusText = !gameplayAllowed() ? Component.translatable("screen.brnquest.editor.preview")
                : cooldownText != null ? cooldownText
                : Component.translatable(QuestPresentation.statusTranslationKey(quest, status, ClientQuestState.get().claimed()));
        UiRect content = new UiRect(left + 10, detailContentTop(), left + 10 + detailsWidth() - 24, detailContentBottom());
        UiRect clip = new UiRect(left + 1 + detailsDrawerOffsetX(), detailContentTop(),
                Math.min(width, width - 10 + detailsDrawerOffsetX()), detailContentBottom());
        var result = detailsPanel.render(graphics, font, new QuestDetailsPanel.Layout(content, clip, width - 8),
                new QuestDetailsPanel.Model(quest,
                        !editing && quest.behavior().hideTextUntilComplete() && !isCompleted(status) ? "???" : questTitle(quest),
                        !editing && quest.behavior().hideTextUntilComplete() && !isCompleted(status) ? "" : localizedQuestText(quest, "quest_subtitle", quest.subtitle()),
                        !editing && quest.behavior().hideTextUntilComplete() && !isCompleted(status) ? "" : localizedQuestText(quest, "quest_desc", quest.description()),
                        status, statusText, statusColor(status),
                        editing, gameplayAllowed(), canSubmit(quest, status)), new QuestDetailsPanel.Rows() {
                    public int task(TaskDefinition task, int x, int y, int rowWidth) {
                        return renderTask(graphics, quest, task, status, x, y, rowWidth, mouseX, mouseY);
                    }
                    public void reward(RewardDefinition reward, int x, int y) {
                        renderReward(graphics, reward, x, y, status, mouseX, mouseY);
                    }
                }, mouseX, mouseY, motionFrameSeconds, scrollSmoothSpeed());
        result.textAreas().forEach((key, bounds) -> quickTextHitboxes.add(new QuickTextHitbox(QuickTextKind.valueOf(key), bounds)));
        if (result.hint() != null) hoveredComponentTooltip = List.of(result.hint());
        if (result.lockedStatusHovered()) hoveredComponentTooltip = dependencyTooltip(quest);
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


    private int renderTask(GuiGraphics graphics, QuestDefinition quest, TaskDefinition task, QuestStatus status,
                           int x, int y, int width, int mouseX, int mouseY) {
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var taskView = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(taskView);
        ItemStack stack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        long storedProgress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        TaskPresentationContext presentationContext = new TaskPresentationContext(minecraft, taskView, status,
                storedProgress, stack);
        // Choice tasks select their representative from the live inventory; rebuild the context so
        // title, progress and readiness all observe the same immutable render-frame decision.
        ItemStack representative = presentation.displayedItem(presentationContext);
        if (!ItemStack.matches(stack, representative)) {
            stack = representative;
            presentationContext = new TaskPresentationContext(minecraft, taskView, status,
                    storedProgress, stack);
        }
        TaskDisplayState state = taskDisplayState(quest, task, status, presentation, presentationContext);
        var row = QuestDetailRows.task(graphics, font, task, presentation, presentationContext, state,
                x, y, width, detailRecipeLookupViewport(), mouseX, mouseY, attentionPingOffsetY);
        if (row.action() != null) {
            UiRect bounds = row.action();
            taskHitboxes.add(new TaskHitbox(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), quest, task));
        }
        if (row.candidates() != null) taskCandidateHitboxes.add(new TaskCandidateHitbox(row.candidates(), task));
        acceptDetailRowHover(row);
        return row.nextY();
    }

    private void acceptDetailRowHover(QuestDetailRows.Result row) {
        if (row.lookup() != null) {
            hoveredRecipeLookupTarget = row.lookup();
            hoveredDetailStack = row.lookup().stack();
        } else if (row.hint() != null) hoveredDetailText = row.hint();
    }

    private void renderReward(GuiGraphics graphics, RewardDefinition reward, int x, int y, QuestStatus status, int mouseX, int mouseY) {
        boolean claimed = ClientQuestState.get().claimed().contains(reward.id().toString());
        boolean claimable = gameplayAllowed() && isCompleted(status) && !claimed;
        ClientRewardPresentation presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        var rewardView = ApiViews.reward(reward);
        String itemSnbt = presentation.itemSnbt(rewardView);
        ItemStack parsedStack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), itemSnbt);
        ItemStack stack = presentation.displayedItem(rewardView, parsedStack);
        var context = new RewardPresentationContext(minecraft, rewardView, claimable, claimed, stack);
        var row = QuestDetailRows.reward(graphics, font, presentation, context, x, y,
                detailRecipeLookupViewport(), mouseX, mouseY, attentionPingOffsetY);
        if (row.action() != null) {
            UiRect bounds = row.action();
            rewardHitboxes.add(new RewardHitbox(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), reward));
        }
        acceptDetailRowHover(row);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editorOverlays.mouseClicked(mouseX, mouseY, button)) return true;
        var snapshot = displaySnapshot();
        if (snapshot == null) return super.mouseClicked(mouseX, mouseY, button);
        if (handleEditorChromeClick(mouseX, mouseY, button, snapshot.book())) return true;

        if (dependencyEditorOpen && detailsPanelAcceptsPointer(mouseX)) {
            return handleDependencyEditorClick(mouseX, mouseY, button);
        }

        if (typedEditorOpen && detailsPanelAcceptsPointer(mouseX)) {
            return handleTypedEditorClick(mouseX, mouseY, button);
        }

        if (questEditorOpen && detailsPanelAcceptsPointer(mouseX)) {
            if (button == 0 && questLocalizedTextEditorBounds != null
                    && questLocalizedTextEditorBounds.contains(mouseX, mouseY)) {
                QuestDefinition quest = draftQuest(questEditorQuestId);
                if (quest != null) openLocalizedQuestTextEditor(quest);
                return true;
            }
            if (button == 0 && questShapeDropdownBounds != null
                    && questShapeDropdownBounds.contains(mouseX, mouseY)) {
                openEnumDropdown(questShapeDropdownBounds, QUEST_SHAPES,
                        questFields.field("shape")::setValue);
                return true;
            }
            if (button == 0 && questBehaviorEditorBounds != null
                    && questBehaviorEditorBounds.contains(mouseX, mouseY)) {
                minecraft.setScreen(new EditorQuestBehaviorScreen(this, questEditorBehavior,
                        value -> questEditorBehavior = value));
                return true;
            }
            if (button == 0 && questIconRowLayout != null && questIconRowLayout.mode().contains(mouseX, mouseY)) {
                rememberCurrentIconInput();
                questEditorIconMode = questEditorIconMode == IconEditorMode.ITEM
                        ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
                questFields.field("icon").setValue(questEditorIconMode == IconEditorMode.ITEM
                        ? questEditorItemIconValue : questEditorTextureIconValue);
                setFocused(questFields.field("icon"));
                return true;
            }
            if (button == 0 && questEditorIconMode == IconEditorMode.ITEM
                    && questIconRowLayout != null && questIconRowLayout.picker().contains(mouseX, mouseY)
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

        int navigationHandleLeft = navigationHandleLeft();
        if (mouseX >= navigationHandleLeft && mouseX <= navigationHandleLeft + navigationHandleWidth()
                && isContentY(mouseY)) {
            navigationCollapsed = !navigationCollapsed;
            return true;
        }

        if (navigationPanelAcceptsPointer(mouseX) && mouseX >= navigationWidth()
                && navigationContentHeight > navigationViewportHeight()) {
            navigationPanel.scroll().snapFromTrack(mouseY, navigationTop(), navigationListBottom(),
                    navigationContentHeight, navigationViewportHeight());
            return true;
        }
        if (detailsPanelAcceptsPointer(mouseX) && mouseX >= width - 12
                && detailsPanel.contentHeight() > detailViewportHeight()
                && mouseY >= detailContentTop() && mouseY <= detailContentBottom()) {
            detailsPanel.scroll().snapFromTrack(mouseY, detailContentTop(), detailContentBottom(),
                    detailsPanel.contentHeight(), detailViewportHeight());
            return true;
        }

        if (ClientEditorState.get().editing() && navigationPanelAcceptsPointer(mouseX)) {
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
            nodeDrag.clearPreview();
            zoomMotion.snap(renderedZoom);
            zoom = renderedZoom;
            panX = 0;
            panY = 0;
            renderedPanX = 0;
            renderedPanY = 0;
            detailsOpen = false;
            detailsPanel.scroll().snap(0);
            closeQuestEditingPanels();
            return true;
        }
        // Group headings and empty navigation space belong to the left panel and
        // must not fall through into quest selection or canvas interaction.
        if (navigationPanelVisibleAt(mouseX)) return true;

        QuestDefinition selected = selectedQuest();
        int left = detailLeft();
        if (detailsPanelAcceptsPointer(mouseX) && mouseX >= width - 18
                && mouseY >= topToolbarHeight() && mouseY <= topToolbarHeight() + 16) {
            detailsOpen = false;
            detailsPanel.scroll().snap(0);
            closeQuestEditingPanels();
            return true;
        }
        if (detailsPanelAcceptsPointer(mouseX) && selected != null) {
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
                openTypedEditor(selected, QuestTypedEntryKind.TASK);
                return true;
            }
            if (ClientEditorState.get().editing() && questRewardButtonBounds().contains(mouseX, mouseY)) {
                openTypedEditor(selected, QuestTypedEntryKind.REWARD);
                return true;
            }
            if (ClientEditorState.get().editing() && questPropertyButtonBounds().contains(mouseX, mouseY)) {
                openQuestEditor(selected);
                return true;
            }
            // Right-click is an editor gesture, never an implicit consume/claim action.
            if (button != 0) return true;
            for (TaskCandidateHitbox hitbox : taskCandidateHitboxes) {
                if (hitbox.bounds().contains(mouseX, mouseY)) {
                    ItemChoiceMatcher.parseConfig(hitbox.task().config()).result()
                            .ifPresent(spec -> openGameplayItemChoiceScreen(selected, hitbox.task(), spec, true));
                    return true;
                }
            }
            if (!gameplayAllowed() && mouseX >= left) return true;
            QuestStatus status = status(selected);
            Component statusText = Component.translatable(
                    QuestPresentation.statusTranslationKey(selected, status, ClientQuestState.get().claimed()));
            int statusY = detailStatusY(selected);
            if (canSubmit(selected, status)) {
                Component readyText = Component.translatable("screen.brnquest.ready");
                int readyX = left + 10 + font.width(statusText) + 6;
                if (mouseX >= readyX && mouseX <= readyX + font.width(readyText)
                        && mouseY >= statusY && mouseY <= statusY + font.lineHeight) {
                    BrnQuestNetwork.completeCheckmark(ClientQuestState.get().revision(), selected.id().toString());
                    return true;
                }
            }
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
                    ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(hitbox.task().typeId());
                    if (!presentation.interactive(ApiViews.task(hitbox.task()))
                            && taskSatisfied(hitbox.task(), status) && canSubmit(hitbox.quest(), status)) {
                        // A completed passive objective is the natural confirmation target for event-driven types.
                        BrnQuestNetwork.completeCheckmark(ClientQuestState.get().revision(),
                                hitbox.quest().id().toString());
                        return true;
                    }
                    ItemChoiceMatcher.Spec itemSpec = ItemChoiceMatcher.parseConfig(hitbox.task().config())
                            .result().orElse(null);
                    if (itemSpec != null && needsManualItemSelection(hitbox.task(), itemSpec)) {
                        openGameplayItemChoiceScreen(hitbox.quest(), hitbox.task(), itemSpec, false);
                        return true;
                    }
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

        if (detailsPanelVisibleAt(mouseX) && !detailsPanelAcceptsPointer(mouseX)) return true;

        int canvasRight = canvasRight() - CANVAS_MARGIN;
        if (mouseX > canvasLeft() && mouseX < canvasRight && isContentY(mouseY)) {
            // Direct canvas interaction adopts the exact visible camera so dragging never
            // chases an animation that is still converging underneath the pointer.
            adoptRenderedCamera();
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
                        nodeDrag.rememberSelection(editorSelection);
                        if (hasControlDown()) {
                            if (!editorSelection.add(hit.id())) editorSelection.remove(hit.id());
                            if (editorSelection.isEmpty()) editorSelection.add(hit.id());
                        } else if (!editorSelection.contains(hit.id())) {
                            selectOnly(hit.id());
                        }
                        // Defer opening details until release so a long press can pick up the node
                        // without flashing or replacing the detail panel underneath the gesture.
                        beginNodeDrag(hit.id(), graphMouseX, graphMouseY, mouseX, mouseY, snapshot.book());
                        return true;
                    }
                } else if (button == 0) {
                    ClientQuestState.get().selected(hit.id());
                }
                if (button == 0) {
                    closeQuestEditingPanels();
                    openDetailsPanel();
                    detailsPanel.scroll().snap(0);
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
        if (editorOverlays.mouseReleased(x, y, button, super::mouseReleased)) return true;
        if (nodeDrag.active() && button == 0) {
            if (nodeDrag.pickedUp()) {
                commitNodeDrag();
            } else {
                ResourceLocation clickedQuestId = nodeDrag.anchor();
                cancelNodeDrag();
                openEditorQuestDetails(clickedQuestId);
            }
            return true;
        }
        dragging = false;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (editorOverlays.mouseDragged(x, y, button, dx, dy, super::mouseDragged)) return true;
        if (nodeDrag.active() && button == 0) {
            long nowNanos = System.nanoTime();
            if (!nodeDrag.pickedUp() && nodeDrag.requestsPan(x, y, nowNanos)) {
                // Motion before the hold threshold is a canvas-pan intent, not an accidental
                // node move. Apply the displacement already travelled before handing off.
                double pressX = nodeDrag.pressX();
                double pressY = nodeDrag.pressY();
                Set<ResourceLocation> selectionBeforePress = nodeDrag.previousSelection();
                cancelNodeDrag();
                // Panning from a node should behave like panning from empty canvas and therefore
                // must not leave behind the provisional selection made on pointer-down.
                editorSelection.clear();
                editorSelection.addAll(selectionBeforePress);
                dragging = true;
                panX += x - pressX;
                panY += y - pressY;
                renderedPanX = panX;
                renderedPanY = panY;
                dragX = x;
                dragY = y;
                return true;
            }
            nodeDrag.update(graphX(x), graphY(y), x, y, nowNanos);
            return true;
        }
        if (dragging) {
            panX += x - dragX;
            panY += y - dragY;
            renderedPanX = panX;
            renderedPanY = panY;
            dragX = x;
            dragY = y;
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (editorOverlays.mouseScrolled(x, y, vertical)) return true;
        if (dependencyEditorOpen && detailsPanelAcceptsPointer(x)) {
            if (dependencyListInputReady()) dependencyList.mouseScrolled(x, y, vertical, scrollStep());
            return true;
        }
        if (typedEditorOpen && detailsPanelAcceptsPointer(x)) {
            typedEntryList.mouseScrolled(currentFrameIdentity(), typedEditorQuestId, typedEditorKind,
                    typedListInputReady(),
                    x, y, vertical, scrollStep());
            return true;
        }
        if (navigationPanelVisibleAt(x) && isContentY(y)) {
            if (navigationPanelAcceptsPointer(x)) {
                navigationPanel.scroll().scrollWheel(vertical, scrollStep(),
                        navigationContentHeight, navigationViewportHeight());
            }
            return true;
        }
        if (detailsPanelVisibleAt(x) && isContentY(y)) {
            if (detailsPanelAcceptsPointer(x)) {
                detailsPanel.scroll().scrollWheel(vertical, scrollStep(), detailsPanel.contentHeight(), detailViewportHeight());
            }
            return true;
        }

        if (!isContentY(y)) return true;
        cancelSelectedQuestFocus();
        double nextZoom = QuestViewportMath.clampZoom(zoomMotion.target() + vertical * 0.10);
        zoomMotion.target(nextZoom);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editorOverlays.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 256 && questEditorOpen) {
            closeQuestEditor();
            return true;
        }
        if (keyCode == 256 && dependencyEditorOpen) {
            closeDependencyEditor();
            return true;
        }
        if (keyCode == 256 && typedPropertySection.open()) {
            closeTypedPropertyEditor();
            return true;
        }
        if (keyCode == 256 && typedEditorOpen) {
            closeTypedEditor();
            return true;
        }
        // The composed list follows its visible action order, including footer controls.
        if (typedListInputReady() && keyCode == 258) {
            return typedEntryList.focusNext(currentFrameIdentity(), typedEditorQuestId, typedEditorKind,
                    true, hasShiftDown());
        }
        if (typedListInputReady() && (keyCode == 257 || keyCode == 335 || keyCode == 32)) {
            return typedEntryList.activateFocused(currentFrameIdentity(), typedEditorQuestId, typedEditorKind, true)
                    .map(intent -> {
                        handleTypedListIntent(intent);
                        return true;
                    }).orElse(false);
        }
        if (editorKeyboardSurfaceReady() && keyCode == 258) {
            // Follow visible left-to-right controls; live mode has no hidden save/publish tab stops.
            List<Integer> actions = ClientEditorState.get().live()
                    ? List.of(EDITOR_ACTION_UNDO, EDITOR_ACTION_REDO, EDITOR_ACTION_EXIT)
                    : List.of(EDITOR_ACTION_UNDO, EDITOR_ACTION_REDO, EDITOR_ACTION_PUBLISH, EDITOR_ACTION_SAVE, EDITOR_ACTION_EXIT);
            int index = actions.indexOf(editorKeyboardFocus);
            int next = index < 0 ? (hasShiftDown() ? actions.size() - 1 : 0)
                    : Math.floorMod(index + (hasShiftDown() ? -1 : 1), actions.size());
            editorKeyboardFocus = actions.get(next);
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
        if (keyCode == 256 && detailsOpen) {
            detailsOpen = false;
            detailsPanel.scroll().snap(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public Component getNarrationMessage() {
        ClientEditorState editor = ClientEditorState.get();
        Component status = editorStatus();
        if (!editor.hasLease()) return super.getNarrationMessage();
        Optional<Component> typedNarration = typedEntryList.narration(currentFrameIdentity(), typedEditorQuestId,
                typedEditorKind, typedListInputReady());
        if (typedNarration.isPresent()) {
            return Component.translatable("screen.brnquest.editor.narration", getTitle(),
                    status == null ? "" : status, typedNarration.orElseThrow());
        }
        Component action = editorKeyboardFocus < 0 ? Component.translatable("screen.brnquest.editor.keyboard.help")
                : Component.translatable("screen.brnquest.editor.keyboard.focus",
                Component.translatable(editorKeyboardActionKey(editorKeyboardFocus)));
        return Component.translatable("screen.brnquest.editor.narration", getTitle(),
                status == null ? "" : status, action);
    }

    private boolean editorKeyboardSurfaceReady() {
        return ClientEditorState.get().hasLease() && !ClientEditorState.get().busy()
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen && !typedPropertySection.open()
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
        openDetailsPanel();
        switch (panel) {
            case 0 -> openQuestEditor(quest);
            case 1 -> openTypedEditor(quest, QuestTypedEntryKind.TASK);
            case 2 -> openTypedEditor(quest, QuestTypedEntryKind.REWARD);
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
        return editorOverlays.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    private ChapterDefinition navigationChoice(QuestBookDefinition book, double mouseX, double mouseY) {
        var entry = navigationEntry(book, mouseX, mouseY);
        return entry == null ? null : entry.chapter();
    }

    private QuestPresentation.NavigationEntry navigationEntry(QuestBookDefinition book, double mouseX, double mouseY) {
        return navigationPanelAcceptsPointer(mouseX) ? navigationPanel.entryAt(book, mouseX, mouseY) : null;
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

        if (!editor.hasLease() && editor.allowed()) {
            renderEditorActionButton(graphics, editorSaveButtonBounds(), EditorButton.Definition.text(
                    Component.translatable("screen.brnquest.editor.live.advanced"),
                    Component.translatable("screen.brnquest.editor.live.advanced_hint")),
                    !editor.busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }
        if (editor.hasLease()) {
            if (!editor.live()) {
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
            }

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
            UiRect leadingButton = editor.hasLease() ? editorUndoButtonBounds()
                    : editor.allowed() ? editorSaveButtonBounds() : editorButtonBounds();
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
                hoveredComponentTooltip = EditorMessageText.errorTooltip(
                        editor.statusCode(), editor.statusMessage());
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
        editorOverlays.render(graphics, mouseX, mouseY);
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

    /** Opens a real popup list while keeping the selected value in the form field owned by the caller. */
    private void openEnumDropdown(UiRect anchor, List<String> values, Consumer<String> consumer) {
        if (anchor == null || values.isEmpty() || consumer == null || ClientEditorState.get().busy()) return;
        closeActiveEditorOverlay();
        enumDropdownAnchor = anchor;
        enumDropdownValues = List.copyOf(values);
        enumDropdownConsumer = consumer;
        setFocused(null);
        editorOverlays.show(EditorOverlayHost.Kind.ENUM_DROPDOWN);
    }

    private void renderEnumDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        if (enumDropdownAnchor == null || enumDropdownValues.isEmpty()) return;
        EditorPopupMenu.render(graphics, font, enumDropdownLayout(), enumDropdownEntries(), mouseX, mouseY);
    }

    private boolean handleEnumDropdownClick(double mouseX, double mouseY, int button) {
        if (button != 0 || enumDropdownAnchor == null) {
            closeEnumDropdown();
            return true;
        }
        EditorPopupMenu.CascadeLayout layout = enumDropdownLayout();
        if (!layout.contains(mouseX, mouseY)) {
            closeEnumDropdown();
            return true;
        }
        String action = EditorPopupMenu.actionAt(layout, enumDropdownEntries(), mouseX, mouseY);
        if (!action.startsWith("ENUM_")) return true;
        try {
            int index = Integer.parseInt(action.substring("ENUM_".length()));
            if (index >= 0 && index < enumDropdownValues.size() && enumDropdownConsumer != null) {
                enumDropdownConsumer.accept(enumDropdownValues.get(index));
            }
        } catch (NumberFormatException ignored) {
            // Popup actions are generated locally; a malformed action simply closes without changing the form.
        }
        closeEnumDropdown();
        return true;
    }

    private List<EditorPopupMenu.Entry> enumDropdownEntries() {
        List<EditorPopupMenu.Entry> entries = new ArrayList<>();
        for (int index = 0; index < enumDropdownValues.size(); index++) {
            entries.add(new EditorPopupMenu.Entry("ENUM_" + index, Component.literal(enumDropdownValues.get(index)),
                    false, true, List.of()));
        }
        return List.copyOf(entries);
    }

    private EditorPopupMenu.CascadeLayout enumDropdownLayout() {
        int menuWidth = Math.max(100, enumDropdownAnchor.width());
        UiRect root = EditorPopupMenu.layout(enumDropdownAnchor.left(), enumDropdownAnchor.bottom() + 1,
                width, topToolbarHeight(), height - bottomToolbarHeight(), menuWidth, enumDropdownValues.size());
        return EditorPopupMenu.cascadeLayout(root, enumDropdownEntries(), -1, width,
                topToolbarHeight(), height - bottomToolbarHeight(), menuWidth);
    }

    private void closeEnumDropdown() {
        enumDropdownAnchor = null;
        enumDropdownValues = List.of();
        enumDropdownConsumer = null;
        editorOverlays.close();
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
            case "ADMIN_QUEST" -> openAdminProgress(target, null);
            case "ADMIN_TASK" -> openAdminProgress(typedEditorQuestId, target);
            case "SELF_FORCE_QUEST" -> openSelfProgress(target, null, false);
            case "SELF_RESET_QUEST" -> openSelfProgress(target, null, true);
            case "SELF_FORCE_TASK" -> openSelfProgress(typedEditorQuestId, target, false);
            case "SELF_RESET_TASK" -> openSelfProgress(typedEditorQuestId, target, true);
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
                    .action("SELF_FORCE_QUEST", Component.translatable("screen.brnquest.admin.self_force"), false,
                            canManageProgress(editContextTarget, null))
                    .action("SELF_RESET_QUEST", Component.translatable("screen.brnquest.admin.self_reset"), true,
                            canManageProgress(editContextTarget, null))
                    .action("ADMIN_QUEST", adminProgressLabel(editContextTarget, null), false,
                            canManageProgress(editContextTarget, null))
                    .action("COPY_QUEST", Component.translatable("screen.brnquest.editor.context.copy_quest"), false)
                    .action("DELETE_QUEST", Component.translatable(
                            "screen.brnquest.editor.context.delete_quest"), true)).stream()
                    .filter(entry -> !(entry.action().equals("ADMIN_QUEST") || entry.action().startsWith("SELF_"))
                            || hasAdminProgressPermission()).toList();
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
                    .action("SELF_FORCE_TASK", Component.translatable("screen.brnquest.admin.self_force"), false,
                            canManageProgress(typedEditorQuestId, editContextTarget))
                    .action("SELF_RESET_TASK", Component.translatable("screen.brnquest.admin.self_reset"), true,
                            canManageProgress(typedEditorQuestId, editContextTarget))
                    .action("ADMIN_TASK", adminProgressLabel(typedEditorQuestId, editContextTarget), false,
                            canManageProgress(typedEditorQuestId, editContextTarget))
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
                            "screen.brnquest.editor.context.delete"), true)).stream()
                    .filter(entry -> !(entry.action().equals("ADMIN_TASK") || entry.action().startsWith("SELF_"))
                            || typedEditorKind == QuestTypedEntryKind.TASK && hasAdminProgressPermission()).toList();
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
                topToolbarHeight(), height - bottomToolbarHeight(), 142);
    }

    private UiRect editContextBounds(int rows) {
        return EditorPopupMenu.layout(editContextX, editContextY, width,
                topToolbarHeight(), height - bottomToolbarHeight(), 142, rows);
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

    private void openTypedEntryContext(ResourceLocation id, int x, int y) {
        QuestDefinition quest = selectedQuest();
        if (quest == null) return;
        // Resolve ordering at dispatch time; a row's stable ID is never an old list index.
        for (int index = 0; index < typedEditorKind.size(quest); index++) {
            if (!typedEditorKind.value(quest, index).id().equals(id)) continue;
            openEditContext(ContextKind.TYPED_ENTRY, id, x, y, 0, 0);
            editContextTypedIndex = index;
            return;
        }
    }

    private void openDependencyEntryContext(ResourceLocation dependencyId, int x, int y) {
        openEditContext(ContextKind.DEPENDENCY_ENTRY, dependencyId, x, y, 0, 0);
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
                QuestTypedEntryKind.Entry entry = typedEditorKind.entry(quest, typedId);
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
    /** Routes are installed once per init, never from render; callbacks resolve current business state. */
    private void configureOverlayRoutes() {
        editorOverlays.focusRestoration(() -> {
            var previous = getFocused();
            return () -> {
                if (previous == null || children().contains(previous)) setFocused(previous);
            };
        });
        registerOverlay(EditorOverlayHost.Kind.STRUCTURE_FORM, null, this::handleStructureFormClick);
        registerOverlay(EditorOverlayHost.Kind.DELETE_CONFIRMATION, this::renderDeleteConfirmation, this::handleDeleteConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.DISCARD_CONFIRMATION, this::renderDiscardConfirmation, this::handleDiscardConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.QUEST_RENAME_CONFIRMATION, this::renderQuestRenameConfirmation, this::handleQuestRenameConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION, this::renderPublishConfirmation, this::handlePublishConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.CONFLICT_RECOVERY, this::renderConflictRecovery, this::handleConflictRecoveryClick);
        registerOverlay(EditorOverlayHost.Kind.DRAFT_SOURCE_CHOICE, this::renderDraftSourceChoice,
                this::handleDraftSourceChoiceClick);
        registerOverlay(EditorOverlayHost.Kind.CONTEXT_MENU, this::renderEditContextMenu, (x, y, button) -> displaySnapshot() != null && handleEditContextClick(x, y, button, displaySnapshot().book()));
        registerOverlay(EditorOverlayHost.Kind.ENUM_DROPDOWN, this::renderEnumDropdown, this::handleEnumDropdownClick);
        registerOverlay(EditorOverlayHost.Kind.DEPENDENCY_PICKER, this::renderDependencyPicker, this::handleDependencyPickerClick);
        registerOverlay(EditorOverlayHost.Kind.TYPED_TYPE_PICKER, this::renderTypedTypePicker, this::handleTypedTypePickerClick);
        registerOverlay(EditorOverlayHost.Kind.QUICK_TEXT, this::renderQuickTextEditor, this::handleQuickTextEditorClick);
        registerOverlay(EditorOverlayHost.Kind.CATALOG, (g, x, y) -> { if (ClientEditorState.get().allowed()) renderEditorCatalog(g, x, y); }, (x, y, button) -> displaySnapshot() != null && handleEditorChromeClick(x, y, button, displaySnapshot().book()));
    }

    private void registerOverlay(EditorOverlayHost.Kind kind, EditorOverlayHost.Draw draw, EditorOverlayHost.Click click) {
        editorOverlays.register(kind, new EditorOverlayHost.Route(draw, click,
                this::handleOverlayKey, this::handleOverlayText, this::handleOverlayWheel, this::closeActiveEditorOverlay,
                kind == EditorOverlayHost.Kind.STRUCTURE_FORM || kind == EditorOverlayHost.Kind.QUICK_TEXT));
    }

    private boolean handleOverlayKey(int key, int scan, int modifiers) {
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT) && (key == 257 || key == 335)) {
            submitQuickTextEdit();
            return true;
        }
        if (key == 259 && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG) && !catalogFilter.isEmpty()) {
            catalogFilter = catalogFilter.substring(0, catalogFilter.length() - 1);
            catalogPicker.reset();
            return true;
        }
        if (key == 259 && editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER) && !dependencyFilter.isEmpty()) {
            dependencyFilter = dependencyFilter.substring(0, dependencyFilter.length() - 1);
            dependencyPicker.reset();
            return true;
        }
        if (structureFormOpen() || editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) {
            return super.keyPressed(key, scan, modifiers);
        }
        return true;
    }

    private boolean handleOverlayText(char character, int modifiers) {
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)
                && !Character.isISOControl(character) && catalogFilter.length() < 48) {
            catalogFilter += character;
            catalogPicker.reset();
        } else if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)
                && !Character.isISOControl(character) && dependencyFilter.length() < 48) {
            dependencyFilter += character;
            dependencyPicker.reset();
        } else if (structureFormOpen() || editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) {
            return super.charTyped(character, modifiers);
        }
        return true;
    }

    private void handleOverlayWheel(double x, double y, double amount) {
        switch (editorOverlays.active()) {
            case CATALOG -> catalogPicker.mouseScrolled(x, y, amount, scrollStep());
            case DEPENDENCY_PICKER -> dependencyPicker.mouseScrolled(x, y, amount, scrollStep());
            case TYPED_TYPE_PICKER -> typedTypePicker.mouseScrolled(x, y, amount, scrollStep());
            case PUBLISH_CONFIRMATION -> {
                if (publishReview != null) publishReviewScroll.scrollWheel(amount, scrollStep(),
                        publishReviewRowCount() * EditorPublishReviewPanel.ROW_HEIGHT,
                        EditorPublishReviewPanel.layout(layout()).list().height());
            }
            default -> { }
        }
    }

    private void closeActiveEditorOverlay() {
        switch (editorOverlays.active()) {
            case CATALOG -> {
                catalogFilter = "";
                catalogPicker.reset();
                editorOverlays.close();
            }
            case CONTEXT_MENU -> closeEditContext();
            case ENUM_DROPDOWN -> closeEnumDropdown();
            case STRUCTURE_FORM -> closeStructureForm();
            case DEPENDENCY_PICKER -> {
                dependencyFilter = "";
                dependencyPicker.reset();
                editorOverlays.close();
            }
            case TYPED_TYPE_PICKER -> {
                // Esc, outside clicks and the visible close button share this path.
                typedTypePicker.reset();
                typedTypePickerFrame = null;
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
                publishReviewScroll.snap(0);
                editorOverlays.close();
            }
            case CONFLICT_RECOVERY -> {
                recoveryCopyBookId = null;
                editorOverlays.close();
            }
            case DRAFT_SOURCE_CHOICE -> clearDraftSourceChoice();
            case NONE -> { }
        }
    }

    private void openQuickTextEditor(QuestDefinition quest, QuickTextKind kind) {
        if (kind == QuickTextKind.NONE || ClientEditorState.get().busy()) return;
        if (kind == QuickTextKind.DESCRIPTION) {
            openLocalizedQuestTextEditor(quest);
            return;
        }
        closeActiveEditorOverlay();
        quickTextKind = kind;
        quickTextQuestId = quest.id();
        quickTextIssue = null;
        quickTextField.setValue(localizedQuestTextValue(quest, kind));
        editorOverlays.show(EditorOverlayHost.Kind.QUICK_TEXT);
        setFocused(quickTextField);
    }

    private void openLocalizedQuestTextEditor(QuestDefinition quest) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || minecraft == null || ClientEditorState.get().busy()) return;
        // This is the same suspended-parent transition as item and raw-config editors. Without
        // the token, Screen.removed() may close a clean server lease before Apply is dispatched.
        childLifecycle.prepareChild();
        minecraft.setScreen(new EditorLocalizedQuestTextScreen(this, snapshot.book().localization(), quest,
                minecraft.getLanguageManager().getSelected(), value -> submitLocalizedQuestText(quest.id(), value)));
    }

    private boolean submitLocalizedQuestText(ResourceLocation questId, EditorLocalizedQuestTextScreen.Value value) {
        if (sendMutation("UPDATE_QUEST_TRANSLATION", questId, null, null, value.locale(), 0, 0, 0, List.of(),
                Map.of("title", value.title(), "subtitle", value.subtitle(), "description", value.description()))) {
            editorSelectedQuest = questId;
            return true;
        }
        return false;
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

    /** Quick edits update the selected locale through the same mutation as the full text editor. */
    private void submitQuickTextEdit() {
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition quest = snapshot == null || quickTextQuestId == null
                ? null : snapshot.quests().get(quickTextQuestId);
        if (ClientEditorState.get().busy() || quest == null || quickTextKind == QuickTextKind.NONE
                || minecraft == null) return;
        String value = quickTextField.getValue();
        EditorLocalizedQuestTextScreen.Value localized = new EditorLocalizedQuestTextScreen.Value(
                minecraft.getLanguageManager().getSelected(),
                quickTextKind == QuickTextKind.TITLE ? value : localizedQuestTextValue(quest, QuickTextKind.TITLE),
                quickTextKind == QuickTextKind.SUBTITLE ? value : localizedQuestTextValue(quest, QuickTextKind.SUBTITLE),
                quickTextKind == QuickTextKind.DESCRIPTION ? value : localizedQuestTextValue(quest, QuickTextKind.DESCRIPTION));
        if (submitLocalizedQuestText(quest.id(), localized)) closeQuickTextEditor();
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
        int top = Math.max(topToolbarHeight() + 4, (height - panelHeight) / 2);
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
        hoveredDetailStack = ItemStack.EMPTY;
        hoveredDetailText = null;
        hoveredComponentTooltip = List.of();
        UiRect form = structureFormBounds();
        graphics.fill(0, 0, width, height, 0x88000000);
        EditorPropertyPanel.renderCentered(graphics, font,
                new EditorPropertyPanel.Layout(form, form.left() + 12, form.width() - 24,
                        form.top() + 10, form.top() + 25, 32),
                Component.translatable(structureFormKind.translationKey()), 0xFFFFFFFF,
                List.of(EditorPropertyPanel.text(font, structureFields.field("id"),
                                "screen.brnquest.editor.structure.id", 70, null, structureFormKind.createsStableId()),
                        EditorPropertyPanel.text(font, structureFields.field("title"),
                                "screen.brnquest.editor.structure.title", 70, null, !ClientEditorState.get().busy())),
                new EditorPropertyPanel.Footer(structureFormCancelBounds(), structureFormDoneBounds(),
                        Component.translatable("gui.done"), true, EditorButton.Tone.PRIMARY),
                (g, bounds, label, enabled, tone) -> renderEditorTextButton(
                        g, bounds, label, null, enabled, tone, mouseX, mouseY));
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
        // Pointer-created quests use the same nearest-intersection rule as node dragging.
        structureFormX = kind == StructureFormKind.ADD_QUEST ? QuestViewportMath.snapQuestCoordinate(x) : x;
        structureFormY = kind == StructureFormKind.ADD_QUEST ? QuestViewportMath.snapQuestCoordinate(y) : y;
        ResourceLocation id = target;
        String title = "";
        if (kind == StructureFormKind.RENAME_GROUP) {
            ChapterGroupDefinition group = book.chapterGroups().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (group != null) title = localizedStructureTitle("chapter_group", group.id(), group.title());
        } else if (kind == StructureFormKind.RENAME_CHAPTER) {
            ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (chapter != null) {
                title = localizedStructureTitle("chapter", chapter.id(), chapter.title());
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
        structureFields.field("id").setValue(id == null ? "" : id.toString());
        structureFields.field("title").setValue(title);
        structureFields.field("id").setVisible(true);
        structureFields.field("title").setVisible(true);
        editorOverlays.show(EditorOverlayHost.Kind.STRUCTURE_FORM);
        setFocused(kind.createsStableId() ? structureFields.field("id") : structureFields.field("title"));
    }

    private void submitStructureForm() {
        ClientEditorState editor = ClientEditorState.get();
        QuestBookDefinition book = displaySnapshot().book();
        ResourceLocation id = ResourceLocation.tryParse(structureFields.field("id").getValue());
        if (id == null || structureFields.field("title").getValue().isBlank()) return;
        String title = structureFields.field("title").getValue();
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
        for (EditorTextField field : List.of(structureFields.field("id"), structureFields.field("title"))) {
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
        book.legacyIds().keySet().forEach(alias -> {
            // Migration sources are retired IDs. Reusing one would make a reload move
            // the newly created entry's ledger state into the historical target.
            String raw = alias.startsWith("@task:") ? alias.substring("@task:".length())
                    : alias.startsWith("@reward:") ? alias.substring("@reward:".length()) : alias;
            ResourceLocation retired = ResourceLocation.tryParse(raw);
            if (retired != null) ids.add(retired);
        });
        // A timestamp-derived generation keeps deleted stable IDs from being silently reused.
        // Reuse would attach an old player's completion or claim ledger to a new object.
        String generation = Long.toUnsignedString(System.currentTimeMillis(), 36);
        for (int suffix = 0; suffix < 10_000; suffix++) {
            String path = stem + "_" + generation + (suffix == 0 ? "" : "_" + suffix);
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(namespace, path);
            if (!ids.contains(candidate)) return candidate;
        }
        return ResourceLocation.fromNamespaceAndPath(namespace, stem + "_" + java.util.UUID.randomUUID()
                .toString().replace("-", ""));
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
        int top = height - bottomToolbarHeight() - 18;
        return new UiRect(2, top, navigationWidth() / 2, top + 16);
    }

    private UiRect navigationAddChapterBounds() {
        int top = height - bottomToolbarHeight() - 18;
        return new UiRect(navigationWidth() / 2 + 2, top, navigationWidth() - 2, top + 16);
    }

    private void renderEditorCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ClientEditorState.CatalogEntry> entries = editorCatalogEntries();
        UiRect bounds = editorCatalogBounds();
        catalogPicker.advance(bounds, new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight()),
                entries.size(), entries::get, currentMotionFrameSeconds, scrollSmoothSpeed());
        Component searchText = catalogFilter.isBlank()
                ? Component.translatable("screen.brnquest.editor.catalog.search_hint")
                : Component.literal("⌕ " + catalogFilter);
        Component empty = Component.translatable(catalogFilter.isBlank()
                ? "screen.brnquest.editor.catalog.current_missing" : "screen.brnquest.editor.catalog.no_match");
        List<Component> tooltip = catalogPicker.render(graphics, font, searchText, catalogFilter.isBlank(), entry -> {
            Component title = Component.literal(entry.title().isBlank() ? entry.bookId().toString() : entry.title());
            Component id = Component.literal(entry.bookId().toString());
            return new EditorPickerList.Entry(title, id, EditorPickerList.Tone.NORMAL,
                    entry.bookId().equals(ClientEditorState.get().bookId()), List.of(title, id,
                    Component.translatable("screen.brnquest.editor.catalog.origin." +
                            entry.origin().name().toLowerCase(java.util.Locale.ROOT))));
        }, empty, mouseX, mouseY);
        if (!tooltip.isEmpty()) hoveredComponentTooltip = tooltip;
    }

    private boolean handleEditorChromeClick(double mouseX, double mouseY, int button,
                                            QuestBookDefinition displayedBook) {
        if (button != 0) return false;
        ClientEditorState editor = ClientEditorState.get();
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)) {
            UiRect catalogBounds = editorCatalogBounds();
            if (catalogBounds.contains(mouseX, mouseY)) {
                if (catalogPicker.mouseClicked(mouseX, mouseY, button)) return true;
                var chosen = catalogPicker.entryAt(mouseX, mouseY);
                if (chosen.isPresent()) {
                    ResourceLocation target = chosen.orElseThrow().bookId();
                    if (editorCatalogEntries().stream().noneMatch(entry -> entry.bookId().equals(target))) return true;
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
                catalogPicker.reset();
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
            if (editor.beginOpenCurrent(target)) {
                AuthoringNetwork.openLiveSession(target);
            }
            return true;
        }
        if (!editor.hasLease() && editor.allowed() && editorSaveButtonBounds().contains(mouseX, mouseY)) {
            requestDraftSourceChoice(displaySnapshot());
            return true;
        }
        if (editor.hasLease() && !editor.live() && editorSaveButtonBounds().contains(mouseX, mouseY)) {
            editor.beginSave().ifPresent(request ->
                    AuthoringNetwork.saveSession(request.sessionId(), editor.bookId(), request.draftRevision()));
            return true;
        }
        if (editor.hasLease() && !editor.live() && editorPublishButtonBounds().contains(mouseX, mouseY)) {
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

    /** Captures both revisions shown to the player so a later server change cannot silently alter the choice. */
    private void requestDraftSourceChoice(QuestBookSnapshot activeSnapshot) {
        if (activeSnapshot == null || ClientEditorState.get().busy()) return;
        closeActiveEditorOverlay();
        draftChoiceBookId = activeSnapshot.book().id();
        draftChoiceActiveRevision = activeSnapshot.revision();
        ClientEditorState.CatalogEntry existing = ClientEditorState.get().catalog().stream()
                .filter(entry -> entry.bookId().equals(draftChoiceBookId)).findFirst().orElse(null);
        draftChoiceDraftRevision = existing == null ? "" : existing.draftRevision();
        draftChoiceTitle = existing == null ? "" : existing.title();
        editorOverlays.show(EditorOverlayHost.Kind.DRAFT_SOURCE_CHOICE);
    }

    private void renderDraftSourceChoice(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean existing = !draftChoiceDraftRevision.isBlank();
        Component title = draftChoiceTitle.isBlank() ? null : Component.literal(draftChoiceTitle);
        yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.render(
                graphics, font, layout(), existing, title, mouseX, mouseY);
    }

    private boolean handleDraftSourceChoiceClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        boolean existing = !draftChoiceDraftRevision.isBlank();
        var action = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.actionAt(
                layout(), existing, mouseX, mouseY);
        if (action == yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.Action.CANCEL) {
            clearDraftSourceChoice();
            return true;
        }
        boolean replace = action
                == yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.Action.CREATE_FROM_ACTIVE;
        if (!replace && action
                != yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.Action.CONTINUE) {
            return true;
        }
        ResourceLocation bookId = draftChoiceBookId;
        String activeRevision = draftChoiceActiveRevision;
        String draftRevision = draftChoiceDraftRevision;
        clearDraftSourceChoice();
        ClientEditorState editor = ClientEditorState.get();
        if (bookId != null && editor.beginOpenCurrent(bookId)) {
            AuthoringNetwork.openCurrentSession(bookId, activeRevision, draftRevision, replace);
        }
        return true;
    }

    private void clearDraftSourceChoice() {
        draftChoiceBookId = null;
        draftChoiceActiveRevision = "";
        draftChoiceDraftRevision = "";
        draftChoiceTitle = "";
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DRAFT_SOURCE_CHOICE)) editorOverlays.close();
    }

    /** History never discards values that are still only present in an open client-side form. */
    private boolean editorHistorySurfaceReady() {
        return !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen && !structureFormOpen()
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE;
    }

    private Component editorStatus() {
        ClientEditorState editor = ClientEditorState.get();
        if (editor.live()) return Component.translatable("screen.brnquest.editor.live." +
                (editor.mode() == ClientEditorState.Mode.ERROR ? "failed" : editor.busy() ? "saving" : "saved"));
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
            case ERROR -> editor.allowed() ? EditorMessageText.operationError(editor.statusCode()) : null;
            case VIEW -> null;
        };
    }

    private void renderTypedEditor(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        if (typedPropertySection.open()) {
            typedEntryList.invalidate();
            renderTypedPropertyEditor(graphics, quest, mouseX, mouseY);
            return;
        }
        int offset = detailsDrawerOffsetX();
        int left = detailLeft() + 10 + offset;
        int right = width - 10 + offset;
        EditorListViewport vertical = typedEditorViewport();
        UiRect screenClip = new UiRect(canvasRight(), topToolbarHeight(), width, height - bottomToolbarHeight());
        QuestTypedEntryListSection.Model model = new QuestTypedEntryListSection.Model(
                currentFrameIdentity(), quest.id(), typedEditorKind,
                Component.translatable(typedEditorKind.headingKey()),
                typedEditorMessage, !ClientEditorState.get().busy(), typedEditorKind.size(quest),
                index -> typedEditorKind.value(quest, index).id(), row -> typedRowContent(quest, row.index()));
        QuestTypedEntryListSection.Layout sectionLayout = new QuestTypedEntryListSection.Layout(offset,
                new UiRect(detailLeft() + 4 + offset, topToolbarHeight() + 4,
                        width - 4 + offset, height - bottomToolbarHeight() - 4),
                new UiRect(left, vertical.top(), right, vertical.bottom()), screenClip, width - 8 + offset,
                typedEditorAddBounds().translated(offset, 0), typedEditorDoneBounds().translated(offset, 0),
                detailsWidth() - 24);
        var result = typedEntryList.render(graphics, font, model, sectionLayout, TYPED_ROW_HEIGHT,
                currentMotionFrameSeconds, scrollSmoothSpeed(), mouseX, mouseY);
        var hover = result.hover();
        if (hover.item() != null) {
            hoveredRecipeLookupTarget = hover.item();
            hoveredDetailStack = hover.item().stack();
        }
        if (!hover.tooltip().isEmpty()) hoveredComponentTooltip = hover.tooltip();
    }

    private boolean handleTypedEditorClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        if (typedPropertySection.open()) return handleTypedPropertyEditorClick(mouseX, mouseY);
        typedEntryList.mouseClicked(currentFrameIdentity(), typedEditorQuestId, typedEditorKind,
                        typedListInputReady(),
                        ClientEditorState.get().busy(), mouseX, mouseY, button)
                .ifPresent(this::handleTypedListIntent);
        return true;
    }

    private void handleTypedListIntent(QuestTypedEntryListSection.Intent intent) {
        if (intent.action() == QuestTypedEntryListSection.Action.ADD) {
            typedTypePicker.reset();
            typedTypePickerFrame = null;
            typedEditorMessage = null;
            editorOverlays.show(EditorOverlayHost.Kind.TYPED_TYPE_PICKER);
            return;
        }
        if (intent.action() == QuestTypedEntryListSection.Action.DONE) {
            closeTypedEditor();
            return;
        }
        QuestDefinition quest = selectedQuest();
        if (quest == null || intent.entryId() == null || typedEditorKind.entry(quest, intent.entryId()) == null) return;
        if (intent.action() == QuestTypedEntryListSection.Action.EDIT) {
            openTypedPropertyEditor(quest, intent.entryId());
        } else {
            openTypedEntryContext(intent.entryId(), intent.x(), intent.y());
        }
    }

    /** Old list geometry is rejected after a data, mode, overlay or drawer transition. */
    private boolean typedListInputReady() {
        return typedEditorOpen && !typedPropertySection.open() && ClientEditorState.get().editing()
                && selectedQuest() != null && selectedQuest().id().equals(typedEditorQuestId)
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && detailsOpen && detailsDrawerMotion.current() >= 1.0;
    }

    private void resetTypedList() {
        typedEntryList.reset();
    }

    private EditorEntryListPanel.Content typedRowContent(QuestDefinition quest, int index) {
        QuestTypedEntryKind.Value value = typedEditorKind.value(quest, index);
        TypedRowPresentation presentation = typedRowPresentation(quest, index);
        boolean known = typedEditorKind.known(value.typeId());
        EditorIcon icon = presentation.stack().isEmpty()
                ? EditorIcon.glyph(Component.literal(presentation.symbol())) : EditorIcon.item(presentation.stack());
        return new EditorEntryListPanel.Content(new EditorEntryRow.Content(icon, presentation.typeName(),
                typedRowSummary(quest, index, known), known ? 0xFF9FB0C2 : 0xFFFFA070), presentation.stack());
    }

    private Component typedRowSummary(QuestDefinition quest, int index, boolean known) {
        if (!known) return Component.translatable("screen.brnquest.editor.typed.summary.unknown");
        if (typedEditorKind == QuestTypedEntryKind.TASK) {
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
        if (typedEditorKind == QuestTypedEntryKind.TASK) {
            TaskDefinition task = quest.tasks().get(index);
            var view = ApiViews.task(task);
            var presentation = ClientTaskPresentationRegistry.get(task.typeId());
            String snbt = presentation.itemSnbt(view);
            ItemStack parsed = snbt.isBlank() ? ItemStack.EMPTY : item(task.id(), snbt);
            TaskPresentationContext context = new TaskPresentationContext(minecraft, view, status(quest),
                    ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L), parsed);
            return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view),
                    presentation.displayedItem(context));
        }
        RewardDefinition reward = quest.rewards().get(index);
        var view = ApiViews.reward(reward);
        var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        String snbt = presentation.itemSnbt(view);
        return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view),
                snbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), snbt));
    }

    private void openTypedPropertyEditor(QuestDefinition quest, ResourceLocation typedId) {
        QuestTypedEntryKind.Entry entry = typedEditorKind.entry(quest, typedId);
        if (entry == null || ClientEditorState.get().busy()) return;
        typedPropertyMessage = null;
        ConfigEditorSchema schema = typedEditorKind == QuestTypedEntryKind.TASK
                ? ConfigEditorSchemas.forTask(ApiViews.task(entry.task()))
                : ConfigEditorSchemas.forReward(ApiViews.reward(entry.reward()));
        // Item tasks project both historical config shapes into one canonical editor model.
        // The original map for this form must be that projection so hidden legacy keys do
        // not silently survive a successful canonical save.
        typedPropertySection.openExisting(entry, schema);
        setFocused(typedPropertySection.form().identityField("id"));
    }

    /** Starts a configured extension entry locally so required fields can be filled before server validation. */
    private void openNewTypedPropertyEditor(QuestBookSnapshot snapshot, ResourceLocation typeId) {
        ResourceLocation id = suggestId(snapshot.book(), typedEditorKind.idStem());
        typedPropertyMessage = null;
        ConfigEditorSchema schema = typedEditorKind == QuestTypedEntryKind.TASK
                ? ConfigEditorSchemas.forTask(new yourscraft.jasdewstarfield.brnquest.api.TaskView(
                        snapshot.book().id(), id, typeId, Map.of(), false))
                : ConfigEditorSchemas.forReward(new yourscraft.jasdewstarfield.brnquest.api.RewardView(
                        snapshot.book().id(), id, typeId, Map.of(), "manual", false));
        typedPropertySection.openNew(id, typeId, schema);
        setFocused(typedPropertySection.form().identityField("id"));
    }

    private void renderTypedPropertyEditor(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        refreshTypedPropertySubmission();
        if (!typedPropertySection.open()) return;
        boolean enabled = !ClientEditorState.get().busy();
        Map<String, String> issues = typedPropertyLocalIssues();
        Component heading = typedPropertyMessage == null ? firstTypedIssue(issues) : typedPropertyMessage;
        if (heading == null) heading = Component.translatable("screen.brnquest.editor.typed.property.heading");
        List<EditorPropertyPanel.RowContent> rows = new ArrayList<>();
        rows.add(EditorPropertyPanel.readOnly(font, "screen.brnquest.editor.typed.property.type",
                typedTypeName(quest, typedPropertySection.originalId()), 68));
        rows.add(EditorPropertyPanel.text(font, typedPropertySection.form().identityField("id"),
                "screen.brnquest.editor.typed.property.id", 68,
                typedPropertySection.form().serverIssues().get("id"), enabled));
        typedPropertySection.hide();
        List<ConfigFieldDescriptor> descriptors = typedPropertySection.form().schema() == null
                ? List.of() : typedPropertySection.form().schema().fields();
        captureTypedPropertyInteractionFrame(descriptors);
        for (int index = 0; index < Math.min(descriptors.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            ConfigFieldDescriptor descriptor = descriptors.get(index);
            int fieldIndex = index;
            rows.add((g, x, y, w) -> renderTypedConfigRow(g, descriptor, fieldIndex, x, y, w,
                    issues.getOrDefault(descriptor.key(),
                            typedPropertySection.form().serverIssues().get(descriptor.key())),
                    mouseX, mouseY));
        }
        if (typedPropertySection.form().schema() != null && typedPropertySection.form().schema().rawFallback()) {
            rows.add((g, x, y, w) -> {
                if (typedPropertyRawEditable()) renderTypedRawConfigRow(g, x, y, w, typedPropertyRawIssue(), mouseX, mouseY);
                else g.drawString(font, Component.translatable("screen.brnquest.editor.typed.property.raw_preserved"),
                        x, y + 5, 0xFFFFA070, false);
            });
        }
        if (typedEditorKind == QuestTypedEntryKind.TASK) {
            rows.add((g, x, y, w) -> renderTypedToggleRow(g, "screen.brnquest.editor.typed.property.optional",
                    typedPropertySection.optional(), x, y, w, mouseX, mouseY));
        } else {
            rows.add((g, x, y, w) -> {
                EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(x, y, w, 68);
                drawTypedLabel(g, "screen.brnquest.editor.typed.property.claim_policy", row.label(),
                        typedPropertySection.form().serverIssues().get("claim_policy"));
                renderEditorTextButton(g, row.field(),
                        Component.literal(typedPropertySection.form().claim() + " ▾"),
                        null, enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            });
            rows.add((g, x, y, w) -> renderTypedToggleRow(g, "screen.brnquest.editor.typed.property.team_reward",
                    typedPropertySection.teamReward(), x, y, w, mouseX, mouseY));
        }
        renderPropertyPanel(graphics, heading, typedPropertyMessage == null && issues.isEmpty()
                        && typedPropertySection.form().serverIssues().isEmpty() ? 0xFFFFFFFF : 0xFFFFA070, rows,
                Component.translatable(typedPropertySection.renameArmed()
                        ? "screen.brnquest.editor.typed.property.confirm_rename" : "gui.done"),
                typedPropertySection.renameArmed()
                        ? EditorButton.Tone.WARNING : EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    private Component typedTypeName(QuestDefinition quest, ResourceLocation typedId) {
        QuestTypedEntryKind.Entry entry = typedEditorKind.entry(quest, typedId);
        if (entry == null) return Component.literal(typedPropertySection.typeId() == null
                ? "" : typedPropertySection.typeId().toString());
        return typedEditorKind == QuestTypedEntryKind.TASK
                ? ClientTaskPresentationRegistry.get(entry.typeId()).typeName(ApiViews.task(entry.task()))
                : ClientRewardPresentationRegistry.get(entry.typeId()).typeName(ApiViews.reward(entry.reward()));
    }

    private void renderTypedConfigRow(GuiGraphics graphics, ConfigFieldDescriptor descriptor, int index,
                                      int left, int top, int width, String issue, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, typedConfigLabel(descriptor.key()), row.label(), issue);
        EditorTextField field = typedPropertySection.form().configField(index);
        if (descriptor.valueType() == ConfigValueType.BOOLEAN) {
            renderEditorTextButton(graphics, row.field(), booleanValue(field.getValue())
                            ? Component.translatable("options.on") : Component.translatable("options.off"),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ENUM) {
            renderEditorTextButton(graphics, row.field(), Component.literal(field.getValue() + " ▾"),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ITEM_STACK) {
            ItemStack stack = item(typedPropertySection.originalId(), field.getValue());
            Component select = Component.translatable("screen.brnquest.editor.typed.property.select_item");
            EditorIcon icon = stack.isEmpty()
                    ? EditorIcon.glyph(Component.literal("+")) : EditorIcon.item(stack);
            EditorButton.Definition definition = EditorButton.Definition.iconAndText(select, select, icon);
            renderEditorActionButton(graphics, row.field(), definition,
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            registerRecipeLookupTarget(stack, EditorButton.iconBounds(font, row.field(), definition),
                    row.field(), mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ITEM_MATCHER) {
            ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parse(field.getValue()).result().orElse(null);
            List<ItemStack> candidates = choiceCandidates(spec);
            ItemStack stack = candidates.isEmpty() ? ItemStack.EMPTY : candidates.getFirst();
            Component edit = Component.translatable(
                    "screen.brnquest.editor.typed.property.edit_matcher", candidates.size());
            EditorIcon icon = stack.isEmpty()
                    ? EditorIcon.glyph(Component.literal("+")) : EditorIcon.item(stack);
            EditorButton.Definition definition = EditorButton.Definition.iconAndText(edit, edit, icon);
            renderEditorActionButton(graphics, row.field(), definition,
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            registerRecipeLookupTarget(stack, EditorButton.iconBounds(font, row.field(), definition),
                    row.field(), mouseX, mouseY);
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
                "screen.brnquest.editor.typed.property.edit_raw", typedPropertySection.form().rawConfig().size());
        renderEditorActionButton(graphics, row.field(), EditorButton.Definition.iconAndText(
                        edit, edit, EditorIcon.glyph(Component.literal("{}"))),
                !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void drawTypedLabel(GuiGraphics graphics, String labelKey, UiRect bounds) {
        drawTypedLabel(graphics, labelKey, bounds, null);
    }

    private void drawTypedLabel(GuiGraphics graphics, String labelKey, UiRect bounds, String issue) {
        EditorPropertyRow.label(graphics, font, Component.translatable(labelKey), bounds, issue);
    }

    private String typedConfigLabel(String key) {
        return switch (key) {
            case "item" -> "screen.brnquest.editor.config.item";
            case "matcher" -> "screen.brnquest.editor.config.matcher";
            case "required_entries" -> "screen.brnquest.editor.config.required_entries";
            case "count" -> "screen.brnquest.editor.config.count";
            case "consume_items" -> "screen.brnquest.editor.config.consume_items";
            case "title" -> "screen.brnquest.editor.config.title";
            case "script_id" -> "screen.brnquest.editor.config.script_id";
            case "message_id" -> "screen.brnquest.editor.config.message_id";
            default -> key;
        };
    }

    private void captureTypedPropertyInteractionFrame(List<ConfigFieldDescriptor> descriptors) {
        List<QuestTypedPropertySection.FieldHit> fields = new ArrayList<>();
        for (int index = 0; index < Math.min(descriptors.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            fields.add(new QuestTypedPropertySection.FieldHit(index, descriptors.get(index),
                    typedPropertyConfigBounds(index)));
        }
        int semanticsTop = typedPropertySemanticsTop();
        boolean raw = typedPropertySection.form().schema() != null
                && typedPropertySection.form().schema().rawFallback() && typedPropertyRawEditable();
        boolean task = typedEditorKind == QuestTypedEntryKind.TASK;
        typedPropertySection.captureInteractionFrame(new QuestTypedPropertySection.InteractionFrame(
                currentFrameIdentity(), typedEditorKind, typedPropertyCancelBounds(), typedPropertyDoneBounds(),
                task ? null : typedPropertySemanticBounds(semanticsTop), fields,
                raw ? typedPropertySemanticBounds(semanticsTop - 22) : null,
                task ? typedPropertySemanticBounds(semanticsTop) : null,
                task ? null : typedPropertySemanticBounds(semanticsTop + 22)));
    }

    private boolean handleTypedPropertyEditorClick(double mouseX, double mouseY) {
        Optional<QuestTypedPropertySection.Intent> result = typedPropertySection.click(
                currentFrameIdentity(), typedEditorKind, mouseX, mouseY);
        if (result.isPresent()) {
            QuestTypedPropertySection.Intent intent = result.orElseThrow();
            switch (intent.action()) {
                case CANCEL -> closeTypedPropertyEditor();
                case SUBMIT -> prepareTypedPropertyEdit();
                case CLAIM -> openEnumDropdown(intent.anchor(), REWARD_CLAIM_POLICIES,
                        typedPropertySection.form().identityField("claim")::setValue);
                case BOOLEAN -> {
                    EditorTextField field = typedPropertySection.form().configField(intent.fieldIndex());
                    field.setValue(Boolean.toString(!booleanValue(field.getValue())));
                }
                case ENUM -> openEnumDropdown(intent.anchor(), intent.values(),
                        typedPropertySection.form().configField(intent.fieldIndex())::setValue);
                case ITEM -> openTypedPropertyItemSelector(intent.fieldIndex());
                case MATCHER -> openTypedPropertyMatcherEditor(intent.fieldIndex());
                case RAW -> openTypedPropertyRawEditor();
                case OPTIONAL -> typedPropertySection.toggleOptional();
                case TEAM_REWARD -> typedPropertySection.toggleTeamReward();
            }
            return true;
        }
        super.mouseClicked(mouseX, mouseY, 0);
        return true;
    }

    private void openTypedPropertyItemSelector(int fieldIndex) {
        if (minecraft == null) return;
        openEditorItemSelector(stack -> {
            if (minecraft.level == null || fieldIndex >= MAX_TYPED_CONFIG_FIELDS) return;
            typedPropertySection.form().setConfigValue(fieldIndex,
                    stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString());
            itemCache.remove(typedPropertySection.originalId());
        });
    }

    private void openTypedPropertyMatcherEditor(int fieldIndex) {
        if (fieldIndex >= MAX_TYPED_CONFIG_FIELDS) return;
        ItemChoiceMatcher.Spec initial = ItemChoiceMatcher.parse(
                typedPropertySection.form().configValue(fieldIndex)).result().orElse(null);
        int requiredIndex = typedConfigFieldIndex("required_entries");
        if (initial != null && requiredIndex >= 0) {
            try {
                int required = Integer.parseInt(typedPropertySection.form().configValue(requiredIndex));
                initial = initial.withRequiredEntries(required);
            } catch (IllegalArgumentException ignored) {
                // The target-level field keeps its own inline validation; child editing still opens.
            }
        }
        if (initial == null) {
            openNewItemChoiceEditor(spec -> setTypedMatcher(fieldIndex, spec));
        } else {
            openItemChoiceScreen(initial, true, spec -> setTypedMatcher(fieldIndex, spec));
        }
    }

    private void setTypedMatcher(int fieldIndex, ItemChoiceMatcher.Spec spec) {
        if (fieldIndex >= MAX_TYPED_CONFIG_FIELDS) return;
        typedPropertySection.form().setConfigValue(fieldIndex, spec.encode());
        int requiredIndex = typedConfigFieldIndex("required_entries");
        if (requiredIndex >= 0) {
            typedPropertySection.form().setConfigValue(requiredIndex, Integer.toString(spec.requiredEntries()));
        }
        itemCache.remove(typedPropertySection.originalId());
        typedPropertyMessage = null;
    }

    private int typedConfigFieldIndex(String key) {
        return typedPropertySection.form().fieldIndex(key);
    }

    private void openNewItemChoiceEditor(java.util.function.Consumer<ItemChoiceMatcher.Spec> resultConsumer) {
        if (minecraft == null) return;
        childLifecycle.prepareChild();
        minecraft.setScreen(ItemChoiceScreen.createEditor(this, resultConsumer));
    }

    /** Permission hiding is only a convenience; the server checks every request independently. */
    private boolean hasAdminProgressPermission() {
        return minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2);
    }

    /** The editor shows drafts, but management may only touch exact IDs in the published book. */
    private boolean canManageProgress(ResourceLocation questId, ResourceLocation taskId) {
        if (minecraft == null || minecraft.player == null || !minecraft.player.hasPermissions(2)
                || questId == null || ClientEditorState.get().busy()) return false;
        var active = ClientQuestState.get().book().orElse(null);
        if (active == null || !active.book().id().equals(ClientEditorState.get().bookId())) return false;
        QuestDefinition quest = active.quests().get(questId);
        return quest != null && (taskId == null || quest.tasks().stream().anyMatch(task -> task.id().equals(taskId)));
    }

    private Component adminProgressLabel(ResourceLocation questId, ResourceLocation taskId) {
        return Component.translatable("screen.brnquest.admin." + (canManageProgress(questId, taskId)
                ? taskId == null ? "manage_quest" : "manage_task" : "publish_first"));
    }

    private void openAdminProgress(ResourceLocation questId, ResourceLocation taskId) {
        if (!canManageProgress(questId, taskId)) return;
        var active = ClientQuestState.get().book().orElseThrow();
        childLifecycle.prepareChild();
        minecraft.setScreen(new AdminProgressScreen(this, active.book().id().toString(), active.revision(),
                questId.toString(), taskId == null ? "" : taskId.toString()));
    }

    /** Self actions use the same server preview ticket, but never ask the player to select themselves. */
    private void openSelfProgress(ResourceLocation questId, ResourceLocation taskId, boolean reset) {
        if (!canManageProgress(questId, taskId)) return;
        var active = ClientQuestState.get().book().orElseThrow();
        var action = taskId == null
                ? (reset ? yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction.RESET_QUEST
                         : yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction.FORCE_QUEST)
                : (reset ? yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction.RESET_TASK
                         : yourscraft.jasdewstarfield.brnquest.progress.AdminProgressAction.FORCE_TASK);
        childLifecycle.prepareChild();
        minecraft.setScreen(new AdminProgressScreen(this, active.book().id().toString(), active.revision(),
                questId.toString(), taskId == null ? "" : taskId.toString(), action));
    }

    private List<ItemStack> choiceCandidates(ItemChoiceMatcher.Spec spec) {
        if (spec == null || minecraft == null || minecraft.level == null) return List.of();
        return ItemChoiceMatcher.displayedCandidates(minecraft.level.registryAccess(), spec);
    }

    private void openItemChoiceScreen(ItemChoiceMatcher.Spec initial, boolean editing,
                                      java.util.function.Consumer<ItemChoiceMatcher.Spec> resultConsumer) {
        if (minecraft == null) return;
        childLifecycle.prepareChild();
        minecraft.setScreen(new ItemChoiceScreen(this, initial, editing, resultConsumer));
    }

    private void openGameplayItemChoiceScreen(QuestDefinition quest, TaskDefinition task,
                                               ItemChoiceMatcher.Spec spec, boolean candidatesOnly) {
        if (minecraft == null) return;
        childLifecycle.prepareChild();
        boolean selectionRequired = !candidatesOnly && needsManualItemSelection(task, spec);
        boolean alreadySubmitted = ClientQuestState.get().taskProgress()
                .getOrDefault(task.id().toString(), 0L) >= 1;
        ItemChoiceOpenMode mode = itemChoiceOpenMode(candidatesOnly, gameplayAllowed(),
                selectionRequired, alreadySubmitted);
        if (mode == ItemChoiceOpenMode.VIEW_CANDIDATES) {
            minecraft.setScreen(new ItemChoiceScreen(this, spec, false, ignored -> {}));
            return;
        }
        minecraft.setScreen(new ItemSubmissionScreen(this, spec, selectedSlots -> {
            String taskId = task.id().toString();
            if (ClientQuestState.get().beginTaskSubmission(taskId)) {
                BrnQuestNetwork.completeTask(ClientQuestState.get().revision(), quest.id().toString(),
                        taskId, selectedSlots);
            }
        }));
    }

    /** Keeps the candidate-list affordance read-only regardless of quest or inventory state. */
    static ItemChoiceOpenMode itemChoiceOpenMode(boolean candidatesOnly, boolean gameplayAllowed,
                                                 boolean selectionRequired, boolean alreadySubmitted) {
        return !candidatesOnly && gameplayAllowed && selectionRequired && !alreadySubmitted
                ? ItemChoiceOpenMode.SELECT_INVENTORY : ItemChoiceOpenMode.VIEW_CANDIDATES;
    }

    private boolean needsManualItemSelection(TaskDefinition task, ItemChoiceMatcher.Spec spec) {
        if (!ClientTaskPresentationRegistry.consumesItems(ApiViews.task(task)) || minecraft == null
                || minecraft.level == null || minecraft.player == null) return false;
        ItemChoiceMatcher.MatchPlan plan = ItemChoiceMatcher.plan(minecraft.level.registryAccess(),
                minecraft.player.getInventory().items, spec);
        // Every satisfiable consume objective enters the real-inventory picker. Even a one-entry
        // objective may have several stacks with different components that the player must choose between.
        return plan.satisfied();
    }

    private void openTypedPropertyRawEditor() {
        if (minecraft == null || !typedPropertyRawEditable()) return;
        childLifecycle.prepareChild();
        minecraft.setScreen(new EditorRawConfigScreen(this, typedPropertySection.form().rawConfig(), config -> {
            typedPropertySection.form().replaceRawConfig(config);
            // A corrected local value supersedes old field diagnostics; the server will return
            // fresh Codec diagnostics when the containing property form is submitted.
            typedPropertySection.form().clearNonIdentityServerIssues();
            typedPropertyMessage = null;
        }));
    }

    private void prepareTypedPropertyEdit() {
        QuestTypedPropertySection.Preparation preparation =
                typedPropertySection.prepare(typedEditorKind, typedPropertyLocalIssues());
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.INVALID_ID) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.invalid_id");
            setFocused(typedPropertySection.form().identityField("id"));
            return;
        }
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.LOCAL_ISSUE) {
            typedPropertyMessage = Component.literal(preparation.fieldKey() + ": " + preparation.issue());
            focusTypedConfigField(preparation.fieldKey());
            return;
        }
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.CONFIRM_RENAME) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.rename_warning");
            return;
        }
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.CLAIM_REQUIRED) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.claim_required");
            typedPropertySection.form().serverIssues().put("claim_policy", typedPropertyMessage.getString());
            setFocused(typedPropertySection.form().identityField("claim"));
            return;
        }
        QuestTypedPropertySection.Submission submission = preparation.submission();
        typedPropertySection.form().serverIssues().clear();
        String action = (typedPropertySection.creating() ? "ADD_" : "UPDATE_") + typedEditorKind.actionPrefix();
        if (!sendMutation(action, submission.replacementId(), typedEditorQuestId,
                submission.sourceId(), submission.claimPolicy(), submission.semanticFlag(),
                0, 0, List.of(), submission.config())) return;
        typedPropertySection.markSubmissionPending();
        typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.submitting");
        itemCache.remove(typedPropertySection.originalId());
        itemCache.remove(submission.replacementId());
    }

    private void closeTypedPropertyEditor() {
        typedPropertyMessage = null;
        typedPropertySection.close();
        setFocused(null);
    }

    private int typedPropertySemanticsTop() {
        int fields = typedPropertySection.form().schema() == null ? 0
                : Math.min(typedPropertySection.form().schema().fields().size(), MAX_TYPED_CONFIG_FIELDS);
        return topToolbarHeight() + 20 + 44 + fields * 22
                + (typedPropertySection.form().schema() != null
                && typedPropertySection.form().schema().rawFallback() ? 22 : 0);
    }

    private UiRect typedPropertyConfigBounds(int index) {
        int left = detailLeft() + 10;
        return EditorPropertyFormLayout.row(left, topToolbarHeight() + 20 + 44 + index * 22,
                detailsWidth() - 24, 68).field();
    }

    private UiRect typedPropertySemanticBounds(int top) {
        return EditorPropertyFormLayout.row(detailLeft() + 10, top, detailsWidth() - 24, 68).field();
    }

    private UiRect typedPropertyCancelBounds() { return questEditorCancelBounds(); }
    private UiRect typedPropertyDoneBounds() { return questEditorSaveBounds(); }

    private static boolean booleanValue(String value) {
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    private Map<String, String> currentTypedPropertyConfig() {
        return typedPropertySection.form().currentConfig();
    }

    /** Missing registrations stay read-only because there is no corresponding Codec to approve an edit. */
    private boolean typedPropertyRawEditable() {
        if (typedPropertySection.form().schema() == null || !typedPropertySection.form().schema().rawFallback()
                || typedPropertySection.typeId() == null) {
            return false;
        }
        return typedEditorKind == QuestTypedEntryKind.TASK
                ? TaskTypeRegistry.get(typedPropertySection.typeId()) != null
                : RewardTypeRegistry.get(typedPropertySection.typeId()) != null;
    }

    private String typedPropertyRawIssue() {
        return typedPropertySection.form().serverIssues().entrySet().stream()
                .filter(entry -> !"id".equals(entry.getKey()) && !"claim_policy".equals(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    /** Combines descriptor validation with the client registry check needed for an ItemStack field. */
    private Map<String, String> typedPropertyLocalIssues() {
        if (typedPropertySection.form().schema() == null) return Map.of();
        Map<String, String> issues = new LinkedHashMap<>(typedPropertySection.form().localIssues());
        Map<String, String> config = currentTypedPropertyConfig();
        if (minecraft != null && minecraft.level != null) {
            for (ConfigFieldDescriptor field : typedPropertySection.form().schema().fields()) {
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
        Map.Entry<String, String> issue = !typedPropertySection.form().serverIssues().isEmpty()
                ? typedPropertySection.form().serverIssues().entrySet().iterator().next()
                : localIssues.isEmpty() ? null : localIssues.entrySet().iterator().next();
        return issue == null ? null : Component.literal("! " + issue.getKey() + ": " + issue.getValue());
    }

    private void focusTypedConfigField(String fieldKey) {
        List<ConfigFieldDescriptor> fields = typedPropertySection.form().schema() == null
                ? List.of() : typedPropertySection.form().schema().fields();
        for (int index = 0; index < Math.min(fields.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            if (fields.get(index).key().equals(fieldKey)) {
                if (fields.get(index).valueType() != ConfigValueType.ITEM_STACK) {
                    setFocused(typedPropertySection.form().configField(index));
                }
                return;
            }
        }
    }

    /** Keeps the form open on server rejection and closes it only after the verified replacement draft arrives. */
    private void refreshTypedPropertySubmission() {
        if (!typedPropertySection.submissionPending()) return;
        ClientEditorState editor = ClientEditorState.get();
        if (editor.busy()) return;
        if (editor.mode() == ClientEditorState.Mode.EDITING) {
            closeTypedPropertyEditor();
            return;
        }
        if (editor.mode() != ClientEditorState.Mode.ERROR) return;
        typedPropertySection.clearSubmissionPending();
        typedPropertyMessage = EditorMessageText.operationError(editor.statusCode());
        typedPropertySection.form().serverIssues().clear();
        for (AuthoringNetwork.EditorDiagnosticWire diagnostic : editor.diagnostics()) {
            String path = diagnostic.path() == null ? "" : diagnostic.path();
            String fieldKey = path.startsWith("config.") ? path.substring("config.".length()) : path;
            if (!fieldKey.isBlank()) typedPropertySection.form().serverIssues().putIfAbsent(fieldKey,
                    EditorMessageText.diagnostic(diagnostic).getString());
        }
        if (!typedPropertySection.form().serverIssues().isEmpty()) {
            String fieldKey = typedPropertySection.form().serverIssues().keySet().iterator().next();
            if ("id".equals(fieldKey)) setFocused(typedPropertySection.form().identityField("id"));
            else if ("claim_policy".equals(fieldKey)) {
                setFocused(typedPropertySection.form().identityField("claim"));
            }
            else focusTypedConfigField(fieldKey);
        }
    }

    private void openTypedEditor(QuestDefinition quest, QuestTypedEntryKind kind) {
        closeQuestEditor();
        closeDependencyEditor();
        closeActiveEditorOverlay();
        typedEditorOpen = true;
        typedEditorKind = kind;
        typedEditorQuestId = quest.id();
        resetTypedList();
        typedEditorMessage = null;
    }

    private void closeTypedEditor() {
        closeTypedPropertyEditor();
        typedEditorOpen = false;
        typedEditorQuestId = null;
        resetTypedList();
        typedEditorMessage = null;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.TYPED_TYPE_PICKER)) closeActiveEditorOverlay();
    }

    private void renderTypedTypePicker(GuiGraphics graphics, int mouseX, int mouseY) {
        typedTypePickerFrame = QuestTypePickerModel.frame(currentFrameIdentity(), typedEditorKind);
        List<ResourceLocation> candidates = typedTypePickerFrame.entries().stream()
                .map(QuestTypePickerModel.Entry::typeId).toList();
        UiRect bounds = typedTypePickerBounds();
        typedTypePicker.advance(bounds, new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight()),
                candidates.size(), candidates::get, currentMotionFrameSeconds, scrollSmoothSpeed());
        List<Component> tooltip = typedTypePicker.render(graphics, font,
                Component.translatable("screen.brnquest.editor.typed.type_heading"), false,
                type -> new EditorPickerList.Entry(typedTypeDisplayName(type),
                        Component.translatable(typePickerHint(typedTypePickerFrame, type)),
                        EditorPickerList.Tone.NORMAL, false, List.of(Component.translatable(
                                "screen.brnquest.editor.typed.type_id", type.toString()))),
                null, mouseX, mouseY);
        if (!tooltip.isEmpty()) hoveredComponentTooltip = tooltip;
        renderEditorIconButton(graphics, typedTypePickerCloseBounds(), Component.literal("×"),
                Component.translatable("screen.brnquest.editor.action.close"), true, false, mouseX, mouseY);
    }

    private static String typePickerHint(QuestTypePickerModel.Frame frame, ResourceLocation typeId) {
        QuestTypePickerModel.Route route = frame.entries().stream()
                .filter(entry -> entry.typeId().equals(typeId)).findFirst()
                .map(QuestTypePickerModel.Entry::route).orElse(QuestTypePickerModel.Route.DIRECT);
        return switch (route) {
            case PROPERTY_FORM -> "screen.brnquest.editor.typed.click_to_configure";
            case CHOICE_SELECTOR -> "screen.brnquest.editor.typed.click_to_select_candidates";
            case ITEM_SELECTOR -> "screen.brnquest.editor.typed.click_to_select_item";
            case DIRECT -> "screen.brnquest.editor.typed.click_to_add";
        };
    }

    /** Uses the client presentation contract while keeping stable IDs confined to storage and diagnostics. */
    private Component typedTypeDisplayName(ResourceLocation typeId) {
        QuestBookSnapshot snapshot = displaySnapshot();
        ResourceLocation bookId = snapshot == null ? typeId : snapshot.book().id();
        ResourceLocation entryId = typedEditorQuestId == null ? typeId : typedEditorQuestId;
        return typedEditorKind == QuestTypedEntryKind.TASK
                ? ClientTaskPresentationRegistry.get(typeId).typeName(
                        new yourscraft.jasdewstarfield.brnquest.api.TaskView(bookId, entryId, typeId, Map.of(), false))
                : ClientRewardPresentationRegistry.get(typeId).typeName(
                        new yourscraft.jasdewstarfield.brnquest.api.RewardView(
                                bookId, entryId, typeId, Map.of(), "manual", false));
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
        if (typedTypePicker.mouseClicked(mouseX, mouseY, button)) return true;
        ResourceLocation typeId = typedTypePicker.entryAt(mouseX, mouseY).orElse(null);
        QuestTypePickerModel.Entry choice = typeId == null || typedTypePickerFrame == null ? null
                : typedTypePickerFrame.select(currentFrameIdentity(), typedEditorKind, typeId).orElse(null);
        if (choice == null || ClientEditorState.get().busy()) return true;
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || typedEditorQuestId == null) return true;
        if (choice.route() == QuestTypePickerModel.Route.CHOICE_SELECTOR) {
            closeActiveEditorOverlay();
            openNewItemChoiceEditor(spec -> addSelectedChoice(typeId, spec));
            return true;
        }
        if (choice.route() == QuestTypePickerModel.Route.ITEM_SELECTOR) {
            closeActiveEditorOverlay();
            openItemSelector(typeId);
            return true;
        }
        if (choice.route() == QuestTypePickerModel.Route.PROPERTY_FORM) {
            closeActiveEditorOverlay();
            openNewTypedPropertyEditor(snapshot, typeId);
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
        childLifecycle.prepareChild();
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

    private void addSelectedChoice(ResourceLocation typeId, ItemChoiceMatcher.Spec spec) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || typedEditorQuestId == null) return;
        ResourceLocation id = suggestId(snapshot.book(), typedEditorKind.idStem());
        sendMutation("ADD_" + typedEditorKind.actionPrefix(), id, typedEditorQuestId, typeId,
                "", 0, 0, 0, List.of(), Map.of(
                        "matcher", spec.encode(),
                        "required_entries", Integer.toString(spec.requiredEntries()),
                        "consume_items", "false"));
    }

    private int typedEditorListTop() { return topToolbarHeight() + 34; }

    private EditorListViewport typedEditorViewport() {
        // Keep a small footer gap while deliberately exposing half of the next row. Validation
        // messages temporarily reclaim their original line so they never overlap list content.
        int footerGap = typedEditorMessage == null ? 6 : 16;
        return EditorListViewport.withHalfRowPreview(
                typedEditorListTop(), typedEditorAddBounds().top() - footerGap, TYPED_ROW_HEIGHT);
    }

    private UiRect typedEditorAddBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
        int center = detailLeft() + detailsWidth() / 2;
        return new UiRect(detailLeft() + 10, bottom - 20, center - 4, bottom);
    }

    private UiRect typedEditorDoneBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
        int center = detailLeft() + detailsWidth() / 2;
        return new UiRect(center + 4, bottom - 20, width - 10, bottom);
    }

    private UiRect typedTypePickerBounds() {
        int desiredHeight = EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT * 6 + 4;
        int maximumHeight = Math.max(EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT + 4,
                height - topToolbarHeight() - bottomToolbarHeight() - 16);
        return layout().centeredDialog(430, 260, 20, Math.min(desiredHeight, maximumHeight)).intersection(pickerScreenBounds());
    }

    private UiRect typedTypePickerCloseBounds() {
        UiRect picker = typedTypePickerBounds();
        return new UiRect(picker.right() - 18, picker.top() + 2, picker.right() - 2, picker.top() + 16);
    }

    private void renderDependencyEditor(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        int offset = detailsDrawerOffsetX();
        graphics.pose().pushPose();
        graphics.pose().translate(-offset, 0, 0);
        try {
            int left = detailLeft() + 10 + offset, right = width - 10 + offset;
            graphics.fill(detailLeft() + 4 + offset, topToolbarHeight() + 4, width - 4 + offset,
                    height - bottomToolbarHeight() - 4, 0xFF202632);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.heading"),
                    left, topToolbarHeight() + 12, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.direction"),
                    left, topToolbarHeight() + 27, 0xFF9FB0C2, false);
            QuestBookSnapshot snapshot = displaySnapshot();
            dependencyListSnapshot = snapshot;
            UiRect viewport = dependencyListBounds().translated(offset, 0);
            UiRect clip = new UiRect(canvasRight(), topToolbarHeight(), width, height - bottomToolbarHeight());
            dependencyList.advance(viewport, clip, width - 8 + offset, DEPENDENCY_ROW_HEIGHT, 2,
                    quest.dependencies().size(), quest.dependencies()::get, currentMotionFrameSeconds, scrollSmoothSpeed());
            List<EditorActionGroup.Placed<ResourceLocation>> actions = new ArrayList<>();
            dependencyList.render(graphics, row -> {
                QuestDefinition dependency = snapshot == null ? null : snapshot.quests().get(row.key());
                UiRect rect = row.bounds();
                UiRect remove = new UiRect(rect.right() - 20, rect.top() + 5, rect.right() - 2, rect.top() + 23);
                graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), 0xA02A323E);
                String title = dependency == null ? row.key().toString() : questTitle(dependency);
                String direction = Component.translatable("screen.brnquest.editor.dependency.edge", title, questTitle(quest)).getString();
                int textWidth = Math.max(0, rect.width() - 30);
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(direction, textWidth)),
                        rect.left() + 5, rect.top() + 4, dependency == null ? 0xFFFFA070 : 0xFFFFFFFF, false);
                Component secondary = dependency == null ? Component.translatable("screen.brnquest.editor.dependency.missing")
                        : Component.literal(chapterTitle(snapshot.book(), dependency.chapterId()));
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(secondary.getString(), textWidth)),
                        rect.left() + 5, rect.top() + 16, dependency == null ? 0xFFFFA070 : 0xFF9FB0C2, false);
                Component label = Component.translatable("screen.brnquest.editor.dependency.remove");
                actions.add(new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(row.key(),
                        EditorButton.Definition.iconOnly(label, label, EditorIcon.glyph(Component.literal("×"))),
                        !ClientEditorState.get().busy(), EditorButton.Tone.DANGER, (x, y) -> {
                            if (!dependencyListInputReady() || ClientEditorState.get().busy()) return;
                            if (!selectedQuest().dependencies().contains(row.key())) return;
                            sendMutation("REMOVE_DEPENDENCY", dependencyEditorQuestId, null,
                                    row.key(), "", 0, 0, 0, List.of());
                            dependencyEditorMessage = null;
                        }), remove, row.visible()));
                if (row.visible().containsExclusive(mouseX, mouseY) && !row.clip(remove).containsExclusive(mouseX, mouseY)) {
                    hoveredComponentTooltip = List.of(Component.translatable("screen.brnquest.editor.dependency.more_hint"));
                }
            }, () -> graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.dependency.empty"),
                    (left + right) / 2, viewport.top() + 8, 0xFF9AA6B5));
            dependencyActions.setActions(actions);
            List<Component> tooltip = dependencyActions.render(graphics, font, mouseX, mouseY);
            if (!tooltip.isEmpty()) hoveredComponentTooltip = tooltip;
            if (dependencyEditorMessage != null) {
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                                dependencyEditorMessage.getString(), detailsWidth() - 24)),
                        left, dependencyAddBounds().top() - 12, 0xFFFFA070, false);
            }
            Component add = Component.translatable("screen.brnquest.editor.dependency.add");
            renderEditorActionButton(graphics, dependencyAddBounds().translated(offset, 0),
                    EditorButton.Definition.iconAndText(add, add, EditorIcon.glyph(Component.literal("+"))),
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.PRIMARY, mouseX, mouseY);
            renderEditorTextButton(graphics, dependencyDoneBounds().translated(offset, 0), Component.translatable("gui.done"),
                    null, true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    private boolean handleDependencyEditorClick(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        if (button == 0 && dependencyDoneBounds().contains(mouseX, mouseY)) {
            closeDependencyEditor();
            return true;
        }
        if (!dependencyListInputReady()) return true;
        if (dependencyList.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && dependencyAddBounds().contains(mouseX, mouseY)) {
            if (!ClientEditorState.get().busy()) openDependencyPicker();
            return true;
        }
        if (dependencyActions.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1 && !ClientEditorState.get().busy()) {
            dependencyList.rowAt(mouseX, mouseY).ifPresent(row ->
                    openDependencyEntryContext(row.key(), (int) mouseX, (int) mouseY));
        }
        return true;
    }

    private boolean dependencyListInputReady() {
        return dependencyEditorOpen && dependencyListSnapshot != null && dependencyListSnapshot == displaySnapshot()
                && ClientEditorState.get().editing() && selectedQuest() != null
                && selectedQuest().id().equals(dependencyEditorQuestId)
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && detailsOpen && detailsDrawerMotion.current() >= 1.0;
    }

    private void resetDependencyList() {
        dependencyList.reset();
        dependencyActions.clear();
        dependencyListSnapshot = null;
    }

    private void openDependencyEditor(QuestDefinition quest) {
        closeQuestEditor();
        closeTypedEditor();
        closeActiveEditorOverlay();
        dependencyEditorOpen = true;
        dependencyEditorQuestId = quest.id();
        resetDependencyList();
        dependencyEditorMessage = null;
    }

    private void closeDependencyEditor() {
        dependencyEditorOpen = false;
        dependencyEditorQuestId = null;
        resetDependencyList();
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
        dependencyPicker.reset();
        dependencyEditorMessage = null;
        editorOverlays.show(EditorOverlayHost.Kind.DEPENDENCY_PICKER);
    }

    private void renderDependencyPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<QuestDependencyEditorModel.Candidate> candidates = dependencyCandidates();
        UiRect bounds = dependencyPickerBounds();
        Component searchText = dependencyFilter.isBlank()
                ? Component.translatable("screen.brnquest.editor.dependency.search_hint") : Component.literal("⌕ " + dependencyFilter);
        graphics.fill(0, 0, width, height, 0x66000000);
        dependencyPicker.advance(bounds, new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight()),
                candidates.size(), candidates::get, currentMotionFrameSeconds, scrollSmoothSpeed());
        List<Component> tooltip = dependencyPicker.render(graphics, font, searchText, dependencyFilter.isBlank(),
                candidate -> new EditorPickerList.Entry(
                        Component.literal((candidate.createsCycle() ? "⚠ " : "") + candidate.title()),
                        dependencyCandidateSubtitle(candidate),
                        candidate.createsCycle() ? EditorPickerList.Tone.WARNING : EditorPickerList.Tone.NORMAL,
                        false, List.of(Component.translatable(candidate.createsCycle()
                                ? "screen.brnquest.editor.dependency.cycle_warning" : "screen.brnquest.editor.dependency.click_to_add"))),
                Component.translatable("screen.brnquest.editor.dependency.no_candidates"), mouseX, mouseY);
        if (!tooltip.isEmpty()) hoveredComponentTooltip = tooltip;
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
        if (dependencyPicker.mouseClicked(mouseX, mouseY, button)) return true;
        var shown = dependencyPicker.entryAt(mouseX, mouseY).orElse(null);
        if (shown == null) return true;
        // Recheck current dependencies/cycles by the displayed ID, not by an index into a freshly filtered list.
        var candidate = dependencyCandidates().stream().filter(value -> value.questId().equals(shown.questId()))
                .findFirst().orElse(null);
        if (candidate == null) return true;
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
                height - topToolbarHeight() - bottomToolbarHeight() - 16);
        int desiredHeight = EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT * 8 + 4;
        return layout().centeredDialog(480, 300, 20, Math.min(desiredHeight, maximumHeight)).intersection(pickerScreenBounds());
    }

    private int dependencyListTop() {
        return topToolbarHeight() + 44;
    }

    private UiRect dependencyListBounds() {
        // Keep the existing whole-row layout, allowing only a partial row when the window is very short.
        int maximumBottom = Math.max(topToolbarHeight(), dependencyAddBounds().top() - 16);
        int top = Math.min(dependencyListTop(), maximumBottom);
        int available = maximumBottom - top;
        int listHeight = available >= DEPENDENCY_ROW_HEIGHT ? available / DEPENDENCY_ROW_HEIGHT * DEPENDENCY_ROW_HEIGHT : available;
        return new UiRect(detailLeft() + 10, top, width - 10, top + listHeight);
    }

    private UiRect dependencyAddBounds() {
        return questPropertyButtonBounds();
    }

    private UiRect dependencyDoneBounds() {
        return questDependencyButtonBounds();
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
        boolean enabled = !ClientEditorState.get().busy();
        List<EditorPropertyPanel.RowContent> rows = new ArrayList<>();
        questFields.field("title").hide();
        questFields.field("subtitle").hide();
        questFields.field("description").hide();
        questFields.field("shape").hide();
        questLocalizedTextEditorBounds = null;
        questShapeDropdownBounds = null;
        questBehaviorEditorBounds = null;
        questIconRowLayout = null;
        rows.add(EditorPropertyPanel.text(font, questFields.field("id"),
                "screen.brnquest.editor.quest.id", 78, null, enabled));
        rows.add((g, x, y, w) -> renderQuestLocalizedTextEditorField(g, x, y, w,
                enabled, mouseX, mouseY));
        rows.add((g, x, y, w) -> renderQuestShapeEditorField(g, x, y, w, enabled, mouseX, mouseY));
        for (String key : List.of("size", "icon_scale", "min_width")) {
            rows.add(EditorPropertyPanel.text(font, questFields.field(key),
                    "screen.brnquest.editor.quest." + key, 78, null, enabled));
        }
        rows.add((g, x, y, w) -> renderQuestBehaviorEditorField(g, x, y, w, enabled, mouseX, mouseY));
        rows.add((g, x, y, w) -> renderQuestIconEditorField(g, x, y, w, mouseX, mouseY));
        rows.add(this::renderQuestPositionEditorField);
        Component heading = questEditorMessage == null
                ? Component.translatable("screen.brnquest.editor.quest.heading") : questEditorMessage;
        renderPropertyPanel(graphics, heading, questEditorMessage == null ? 0xFFFFFFFF : 0xFFFF8B8B,
                rows, Component.translatable("gui.done"), EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

    private void renderQuestLocalizedTextEditorField(GuiGraphics graphics, int left, int top, int width, boolean enabled,
                                                     int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 78);
        EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.editor.quest.localized_text"),
                row.label(), null);
        questLocalizedTextEditorBounds = row.field();
        renderEditorTextButton(graphics, row.field(),
                Component.translatable("screen.brnquest.editor.quest.localized_text_open"),
                Component.translatable("screen.brnquest.editor.quest.localized_text_hint"), enabled,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void renderQuestShapeEditorField(GuiGraphics graphics, int left, int top, int width,
                                             boolean enabled, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 78);
        EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.editor.quest.shape"),
                row.label(), null);
        questShapeDropdownBounds = row.field();
        renderEditorTextButton(graphics, row.field(),
                Component.literal(questFields.field("shape").getValue() + " ▾"), null,
                enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void renderQuestBehaviorEditorField(GuiGraphics graphics, int left, int top, int width,
                                                boolean enabled, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 78);
        EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.editor.quest.behavior"),
                row.label(), null);
        questBehaviorEditorBounds = row.field();
        renderEditorTextButton(graphics, row.field(),
                Component.translatable("screen.brnquest.editor.quest.behavior_open"), null,
                enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    /** The same row surface composes basic and descriptor-driven forms without owning their transactions. */
    private void renderPropertyPanel(GuiGraphics graphics, Component heading, int headingColor,
                                     List<EditorPropertyPanel.RowContent> rows, Component done,
                                     EditorButton.Tone tone, int mouseX, int mouseY) {
        EditorPropertyPanel.Layout form = new EditorPropertyPanel.Layout(
                new UiRect(detailLeft() + 4, topToolbarHeight() + 4, width - 4,
                        height - bottomToolbarHeight() - 4), detailLeft() + 10, detailsWidth() - 24,
                topToolbarHeight() + 7, topToolbarHeight() + 20, 22);
        EditorPropertyPanel.render(graphics, font, form, heading, headingColor, rows,
                new EditorPropertyPanel.Footer(questEditorCancelBounds(), questEditorSaveBounds(), done,
                        !ClientEditorState.get().busy(), tone),
                (g, bounds, label, enabled, buttonTone) -> renderEditorTextButton(
                        g, bounds, label, null, enabled, buttonTone, mouseX, mouseY));
    }

    /** Shows an ordinary item ID while retaining the native SNBT representation behind the form. */
    private void renderQuestIconEditorField(GuiGraphics graphics, int left, int top, int width,
                                            int mouseX, int mouseY) {
        int labelWidth = 48;
        questIconRowLayout = QuestIconEditorRow.layout(left, top, width, labelWidth);
        String label = font.plainSubstrByWidth(Component.translatable("screen.brnquest.editor.quest.icon").getString(),
                questIconRowLayout.label().width() - 4);
        graphics.drawString(font, Component.literal(label), questIconRowLayout.label().left(), questIconRowLayout.label().top() + 5,
                0xFF9FB0C2, false);
        renderEditorTextButton(graphics, questIconRowLayout.mode(), Component.translatable(questEditorIconMode.translationKey),
                null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        questFields.field("icon").show(questIconRowLayout.input(), !ClientEditorState.get().busy());
        ResourceLocation iconId = ResourceLocation.tryParse(questFields.field("icon").getValue().strip());
        if (questEditorIconMode == IconEditorMode.ITEM
                && iconId != null && BuiltInRegistries.ITEM.containsKey(iconId)) {
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_item");
            ItemStack stack = BuiltInRegistries.ITEM.get(iconId).getDefaultInstance();
            EditorButton.Definition definition = EditorButton.Definition.iconOnly(
                    select, select, EditorIcon.item(stack));
            renderEditorActionButton(graphics, questIconRowLayout.picker(), definition,
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            registerRecipeLookupTarget(stack, EditorButton.iconBounds(font, questIconRowLayout.picker(), definition),
                    questIconRowLayout.picker(), mouseX, mouseY);
        } else if (questEditorIconMode == IconEditorMode.ITEM) {
            Component select = Component.translatable("screen.brnquest.editor.quest.icon.select_item");
            renderEditorActionButton(graphics, questIconRowLayout.picker(), EditorButton.Definition.iconOnly(
                            select, select, EditorIcon.glyph(Component.literal(
                                    questFields.field("icon").getValue().isBlank() ? "+" : "?"))),
                    !ClientEditorState.get().busy(), -1, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (questEditorIconMode == IconEditorMode.TEXTURE && iconId != null) {
            graphics.blit(iconId, questIconRowLayout.picker().left() + 2, questIconRowLayout.picker().top() + 1,
                    0.0F, 0.0F, 16, 16, 16, 16);
        } else {
            graphics.drawCenteredString(font, questFields.field("icon").getValue().isBlank() ? "−" : "?",
                    questIconRowLayout.picker().centerX(), questIconRowLayout.picker().top() + 5, 0xFF9FB0C2);
        }
    }

    /** Keeps exact coordinate entry available even though pointer dragging snaps to the visible grid. */
    private void renderQuestPositionEditorField(GuiGraphics graphics, int left, int top, int width) {
        int labelWidth = 48;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, labelWidth);
        String label = font.plainSubstrByWidth(
                Component.translatable("screen.brnquest.editor.quest.position").getString(),
                row.label().width() - 4);
        graphics.drawString(font, Component.literal(label), row.label().left(), row.label().top() + 5,
                0xFF9FB0C2, false);

        int axisLabelWidth = 10;
        int gap = 4;
        int inputWidth = Math.max(16, (row.field().width() - axisLabelWidth * 2 - gap) / 2);
        int xLabelLeft = row.field().left();
        int xInputLeft = xLabelLeft + axisLabelWidth;
        int yLabelLeft = xInputLeft + inputWidth + gap;
        int yInputLeft = yLabelLeft + axisLabelWidth;
        graphics.drawString(font, "X", xLabelLeft + 1, row.field().top() + 5, 0xFF9FB0C2, false);
        graphics.drawString(font, "Y", yLabelLeft + 1, row.field().top() + 5, 0xFF9FB0C2, false);
        questFields.field("x").show(new UiRect(xInputLeft, row.field().top(), xInputLeft + inputWidth, row.field().bottom()),
                !ClientEditorState.get().busy());
        questFields.field("y").show(new UiRect(yInputLeft, row.field().top(), row.field().right(), row.field().bottom()),
                !ClientEditorState.get().busy());
    }

    /** Reuses the ghost inventory/JEI Screen while the quest form retains identity-only item input. */
    private void openQuestIconItemSelector() {
        openEditorItemSelector(stack -> {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) return;
            String value = itemId.toString();
            questFields.field("icon").setValue(value);
            questEditorItemIconValue = value;
            questEditorMessage = null;
        });
    }

    private void openQuestEditor(QuestDefinition quest) {
        if (ClientEditorState.get().busy()) return;
        closeDependencyEditor();
        closeTypedEditor();
        questEditorOpen = true;
        questEditorQuestId = quest.id();
        questEditorMessage = null;
        questFields.field("id").setValue(quest.id().toString());
        questFields.field("title").setValue(quest.title());
        questFields.field("subtitle").setValue(quest.subtitle());
        questFields.field("description").setValue(quest.description());
        questEditorIconMode = QuestIconValue.isTexture(quest.icon()) ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
        questEditorOriginalIconMode = questEditorIconMode;
        questEditorOriginalIconItemId = questEditorIconMode == IconEditorMode.TEXTURE
                ? QuestIconValue.textureId(quest.icon()).map(ResourceLocation::toString).orElse("")
                : iconItemId(quest.icon());
        questEditorItemIconValue = questEditorIconMode == IconEditorMode.ITEM ? questEditorOriginalIconItemId : "";
        questEditorTextureIconValue = questEditorIconMode == IconEditorMode.TEXTURE
                ? questEditorOriginalIconItemId : "";
        questFields.field("icon").setValue(questEditorOriginalIconItemId);
        questFields.field("x").setValue(Double.toString(quest.x()));
        questFields.field("y").setValue(Double.toString(quest.y()));
        questFields.field("shape").setValue(quest.appearance().shape());
        questFields.field("size").setValue(Double.toString(quest.appearance().size()));
        questFields.field("icon_scale").setValue(Double.toString(quest.appearance().iconScale()));
        questFields.field("min_width").setValue(Double.toString(quest.appearance().minWidth()));
        questEditorBehavior = quest.behavior();
        setFocused(questFields.field("id"));
    }

    private void prepareQuestPropertyEdit() {
        ResourceLocation replacementId = ResourceLocation.tryParse(questFields.field("id").getValue());
        if (replacementId == null) {
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.invalid_id");
            return;
        }
        String iconItemId = questFields.field("icon").getValue().strip();
        ResourceLocation parsedIcon = iconItemId.isEmpty() ? null : ResourceLocation.tryParse(iconItemId);
        if (!iconItemId.isEmpty() && (parsedIcon == null || (questEditorIconMode == IconEditorMode.ITEM
                && !BuiltInRegistries.ITEM.containsKey(parsedIcon)))) {
            questEditorMessage = Component.translatable(questEditorIconMode == IconEditorMode.ITEM
                    ? "screen.brnquest.editor.quest.invalid_icon" : "screen.brnquest.editor.quest.invalid_texture");
            return;
        }
        Double x = parseFiniteCoordinate(questFields.field("x"));
        Double y = parseFiniteCoordinate(questFields.field("y"));
        Double size = parsePositive(questFields.field("size"), false);
        Double iconScale = parsePositive(questFields.field("icon_scale"), false);
        Double minWidth = parsePositive(questFields.field("min_width"), true);
        if (x == null || y == null || size == null || iconScale == null || minWidth == null
                || questFields.field("shape").getValue().isBlank()) {
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.invalid_position");
            return;
        }
        QuestDefinition currentQuest = draftQuest(questEditorQuestId);
        if (!replacementId.equals(questEditorQuestId) && currentQuest != null
                && (Double.compare(x, currentQuest.x()) != 0 || Double.compare(y, currentQuest.y()) != 0)) {
            // A rename migrates aliases and a coordinate update changes content; keeping them as separate
            // revision steps prevents a confirmed rename from racing a second mutation request.
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.rename_position_conflict");
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

    /** Rejects NaN and infinity because both would poison viewport bounds and serialized drafts. */
    private static Double parseFiniteCoordinate(EditorTextField field) {
        if (field == null) return null;
        try {
            double value = Double.parseDouble(field.getValue().strip());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Double parsePositive(EditorTextField field, boolean allowZero) {
        Double value = parseFiniteCoordinate(field);
        return value != null && (allowZero ? value >= 0 : value > 0) ? value : null;
    }

    private QuestDefinition draftQuest(ResourceLocation questId) {
        QuestBookSnapshot snapshot = displaySnapshot();
        return snapshot == null || questId == null ? null : snapshot.quests().get(questId);
    }

    private void submitQuestPropertyEdit(ResourceLocation replacementId) {
        ClientEditorState editor = ClientEditorState.get();
        if (editor.busy() || editor.sessionId() == null || editor.bookId() == null || questEditorQuestId == null) return;
        QuestDefinition currentQuest = draftQuest(questEditorQuestId);
        if (currentQuest == null) return;
        if (!editor.beginMutation()) return;
        ResourceLocation oldId = questEditorQuestId;
        // Icon rendering is cached by quest ID; invalidate both sides of a rename before the new draft arrives.
        itemCache.remove(oldId);
        itemCache.remove(replacementId);
        AuthoringNetwork.updateQuest(editor.sessionId(), editor.bookId(), editor.draftRevision(), questEditorQuestId,
                replacementId, currentQuest.title(), currentQuest.subtitle(), currentQuest.description(),
                questEditorIconMode.name(), questFields.field("icon").getValue().strip(),
                questEditorIconMode == questEditorOriginalIconMode
                        && questFields.field("icon").getValue().strip().equals(questEditorOriginalIconItemId),
                Double.parseDouble(questFields.field("x").getValue().strip()),
                Double.parseDouble(questFields.field("y").getValue().strip()),
                questFields.field("shape").getValue().strip(),
                Double.parseDouble(questFields.field("size").getValue().strip()),
                Double.parseDouble(questFields.field("icon_scale").getValue().strip()),
                Double.parseDouble(questFields.field("min_width").getValue().strip()),
                behaviorConfig(questEditorBehavior));
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
        questEditorBehavior = QuestBehavior.DEFAULT;
        clearRenderedPropertyGeometry();
        setFocused(null);
        for (EditorTextField field : List.of(questFields.field("id"), questFields.field("title"), questFields.field("subtitle"),
                questFields.field("description"), questFields.field("icon"), questFields.field("x"), questFields.field("y"),
                questFields.field("shape"), questFields.field("size"), questFields.field("icon_scale"),
                questFields.field("min_width"))) {
            if (field == null) continue;
            field.hide();
        }
    }

    /** Frame-derived hitboxes are invalid as soon as their owning form closes or this Screen re-initializes. */
    private void clearRenderedPropertyGeometry() {
        questIconRowLayout = null;
        questLocalizedTextEditorBounds = null;
        questShapeDropdownBounds = null;
        questBehaviorEditorBounds = null;
    }

    private Map<String, String> behaviorConfig(QuestBehavior value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("hide_until_dependencies_visible", Boolean.toString(value.hideUntilDependenciesVisible()));
        result.put("hide_until_dependencies_complete", Boolean.toString(value.hideUntilDependenciesComplete()));
        result.put("invisible_until_complete", Boolean.toString(value.invisibleUntilComplete()));
        result.put("visible_after_tasks", Integer.toString(value.visibleAfterTasks()));
        result.put("hide_details_until_startable", Boolean.toString(value.hideDetailsUntilStartable()));
        result.put("hide_text_until_complete", Boolean.toString(value.hideTextUntilComplete()));
        result.put("hide_lock_icon", Boolean.toString(value.hideLockIcon()));
        result.put("dependency_requirement", value.dependencyRequirement().serializedName());
        result.put("minimum_required_dependencies", Integer.toString(value.minimumRequiredDependencies()));
        result.put("sequential_tasks", Boolean.toString(value.sequentialTasks()));
        result.put("repeatable", Boolean.toString(value.repeatable()));
        result.put("repeat_cooldown_seconds", Integer.toString(value.repeatCooldownSeconds()));
        result.put("ignore_reward_blocking", Boolean.toString(value.ignoreRewardBlocking()));
        return result;
    }

    /** EditBox widgets render through Screen after custom panels, so apply the drawer translation explicitly. */
    private void offsetDetailsDrawerFieldsForMotion() {
        if (!detailsOpen && !detailsDrawerVisible()) {
            questFields.hide();
            typedPropertySection.hide();
            return;
        }
        int offset = detailsDrawerOffsetX();
        questFields.offsetForDrawerAnimation(offset);
        typedPropertySection.offsetForDrawerAnimation(offset);
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
            questEditorItemIconValue = questFields.field("icon").getValue();
        } else {
            questEditorTextureIconValue = questFields.field("icon").getValue();
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
            ResourceLocation replacementId = ResourceLocation.tryParse(questFields.field("id").getValue());
            if (replacementId != null) submitQuestPropertyEdit(replacementId);
        }
        return true;
    }

    private UiRect questPropertyButtonBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
        return questEditorTabBounds(0, bottom);
    }

    private UiRect questTaskButtonBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
        return questEditorTabBounds(1, bottom);
    }

    private UiRect questRewardButtonBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
        return questEditorTabBounds(2, bottom);
    }

    private UiRect questDependencyButtonBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
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
        int bottom = height - bottomToolbarHeight() - 6;
        int center = detailLeft() + detailsWidth() / 2;
        return new UiRect(detailLeft() + 10, bottom - 20, center - 4, bottom);
    }

    private UiRect questEditorSaveBounds() {
        int bottom = height - bottomToolbarHeight() - 6;
        int center = detailLeft() + detailsWidth() / 2;
        return new UiRect(center + 4, bottom - 20, width - 10, bottom);
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
        // Lightweight editing collapses the absent save/publish controls instead of leaving a large gap.
        UiRect anchor = ClientEditorState.get().live() ? editorButtonBounds() : editorPublishButtonBounds();
        return new UiRect(anchor.left() - EDITOR_HISTORY_BUTTON_WIDTH - 4, anchor.top(),
                anchor.left() - 4, anchor.bottom());
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
        publishReviewScroll.frameAndRender(graphics, reviewLayout.list().right() + 2,
                reviewLayout.list().top(), reviewLayout.list().bottom(),
                rowCount * EditorPublishReviewPanel.ROW_HEIGHT, reviewLayout.list().height(),
                currentMotionFrameSeconds, scrollSmoothSpeed());
        graphics.enableScissor(reviewLayout.list().left(), reviewLayout.list().top(),
                reviewLayout.list().right(), reviewLayout.list().bottom());
        int firstIndex = publishReviewScroll.firstIndex(EditorPublishReviewPanel.ROW_HEIGHT);
        int rowOffset = publishReviewScroll.rowOffset(EditorPublishReviewPanel.ROW_HEIGHT);
        int renderedRows = EditorPublishReviewPanel.renderedRows(reviewLayout, rowOffset);
        for (int visibleIndex = 0; visibleIndex < renderedRows; visibleIndex++) {
            int rowIndex = firstIndex + visibleIndex;
            if (rowIndex >= rowCount) break;
            renderPublishReviewRow(graphics, reviewLayout, rowIndex,
                    reviewLayout.list().top() + rowOffset
                            + visibleIndex * EditorPublishReviewPanel.ROW_HEIGHT,
                    mouseX, mouseY);
        }
        graphics.disableScissor();
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
            heading = EditorMessageText.severity(diagnostic.severity()).getString() + " · "
                    + EditorMessageText.diagnostic(diagnostic).getString();
            detail = diagnostic.objectId() + (diagnostic.path().isBlank() ? "" : " · " + diagnostic.path());
            tooltip = EditorMessageText.diagnosticTooltip(diagnostic);
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
        if (publishReviewScroll.handleTrackClick(mouseX, mouseY, reviewLayout.list().right() + 2,
                reviewLayout.list().top(), reviewLayout.list().bottom(),
                publishReviewRowCount() * EditorPublishReviewPanel.ROW_HEIGHT,
                reviewLayout.list().height())) return true;
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
            openDetailsPanel();
            detailsPanel.scroll().snap(0);
        }
        if (chapter == null) return quest != null;
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(book);
        int index = chapters.indexOf(chapter);
        if (index >= 0) {
            chapterIndex = index;
            rememberedChapterId = chapter.id();
            rememberedChapterResolved = true;
            navigationPanel.scroll().snap(0);
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
        return new UiRect(left, title.bottom() + 2, right, title.bottom() + 2 + height).intersection(pickerScreenBounds());
    }

    /** Picker chrome and its list share the same available screen area, including on very short windows. */
    private UiRect pickerScreenBounds() {
        return new UiRect(4, topToolbarHeight(), Math.max(4, width - 4),
                Math.max(topToolbarHeight(), height - bottomToolbarHeight() - 4));
    }

    private int editorCatalogVisibleRows() {
        int availableHeight = Math.max(EDITOR_CATALOG_ROW_HEIGHT,
                height - bottomToolbarHeight() - editorTitleBounds().bottom()
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

    /** Builds the same state used by row color, row input and the outer-node attention badge. */
    private TaskDisplayState taskDisplayState(QuestDefinition quest, TaskDefinition task, QuestStatus status) {
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(view);
        ItemStack displayedItem = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        long storedProgress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        TaskPresentationContext context = new TaskPresentationContext(
                minecraft, view, status, storedProgress, displayedItem);
        return taskDisplayState(quest, task, status, presentation, context);
    }

    private TaskDisplayState taskDisplayState(QuestDefinition quest, TaskDefinition task, QuestStatus status,
                                              ClientTaskPresentation presentation,
                                              TaskPresentationContext context) {
        boolean sequentiallyAvailable = taskSequentiallyAvailable(quest, task, status);
        return TaskDisplayState.resolve(status,
                ClientTaskPresentationRegistry.confirmed(context.task(), context.storedProgress()),
                ClientQuestState.get().isTaskSubmissionPending(task.id().toString()),
                sequentiallyAvailable && presentation.interactive(context.task()),
                sequentiallyAvailable && presentation.readyForSubmission(context),
                !gameplayAllowed());
    }

    private boolean questHasAttentionTask(QuestDefinition quest, QuestStatus status) {
        return quest.tasks().stream().map(task -> taskDisplayState(quest, task, status))
                .anyMatch(state -> state == TaskDisplayState.READY);
    }

    /** Mirrors the server's author-ordered gate for presentation; submission is still revalidated server-side. */
    private boolean taskSequentiallyAvailable(QuestDefinition quest, TaskDefinition task, QuestStatus status) {
        if (!quest.behavior().sequentialTasks() || task.optional()) return true;
        for (TaskDefinition candidate : quest.tasks()) {
            if (candidate.id().equals(task.id())) return true;
            if (!candidate.optional() && !taskSatisfied(candidate, status)) return false;
        }
        return false;
    }

    private Component repeatCooldownText(QuestDefinition quest, QuestStatus status) {
        if (!gameplayAllowed() || !quest.behavior().repeatable() || !isCompleted(status)) return null;
        boolean rewardsResolved = quest.behavior().ignoreRewardBlocking() || quest.rewards().isEmpty()
                || quest.rewards().stream().allMatch(reward -> ClientQuestState.get().claimed()
                .contains(reward.id().toString()));
        long remainingMillis = ClientQuestState.get().nextAvailableAt(quest.id()) - System.currentTimeMillis();
        if (!rewardsResolved || remainingMillis <= 0) return null;
        return Component.translatable("screen.brnquest.repeat_cooldown",
                Math.max(1L, (remainingMillis + 999L) / 1000L));
    }

    /** Editing adds controls; only isolated drafts or unacknowledged live revisions suppress gameplay. */
    private boolean gameplayAllowed() {
        return ClientEditorState.get().gameplayAllowed(ClientQuestState.get().revision());
    }

    private QuestStatus status(QuestDefinition quest) {
        if (!gameplayAllowed()) return QuestStatus.LOCKED;
        return ClientQuestState.get().statuses().getOrDefault(quest.id().toString(), QuestStatus.LOCKED);
    }

    private boolean questVisible(QuestDefinition quest) {
        return ClientEditorState.get().editing() || ClientQuestState.get().visible(quest.id());
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
                lines.add(Component.translatable("screen.brnquest.dependency.missing")
                        .withStyle(net.minecraft.ChatFormatting.RED));
                continue;
            }
            ChapterDefinition chapter = snapshot.book().chapters().stream()
                    .filter(candidate -> candidate.id().equals(dependency.chapterId())).findFirst().orElse(null);
            String chapterTitle = chapter == null || chapter.title().isBlank()
                    ? Component.translatable("screen.brnquest.chapter.untitled").getString() : chapter.title();
            String dependencyTitle = dependency.title().isBlank()
                    ? Component.translatable("screen.brnquest.quest.untitled").getString() : dependency.title();
            lines.add(Component.translatable("screen.brnquest.dependency.entry", chapterTitle, dependencyTitle));
        }
        if (quest.dependencies().isEmpty()) lines.add(Component.translatable("screen.brnquest.dependencies.none"));
        return lines;
    }

    private boolean isCompleted(QuestStatus status) {
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED;
    }

    /** Anchors either node notification just outside the visual's top-right corner. */
    private void renderNodeAttentionPing(GuiGraphics graphics, ResourceLocation texture,
                                         int nodeX, int nodeY, int nodeSize) {
        int nodeRadius = nodeSize / 2;
        renderAttentionPing(graphics, texture,
                nodeX + nodeRadius - 2,
                nodeY - nodeRadius - ATTENTION_PING_SIZE + 2);
    }

    /** Renders a 10px authored badge above ItemRenderer's GUI depth. */
    private void renderAttentionPing(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
        QuestDetailRows.renderAttentionPing(graphics, texture, x, y, attentionPingOffsetY);
    }

    /** Keeps the claimed check above the item while leaving its vanilla count corner unobstructed. */


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
        QuestDefinition quest = snapshot == null || selectedId == null ? null : snapshot.quests().get(selectedId);
        return quest == null || !questVisible(quest) ? null : quest;
    }

    private ResourceLocation selectedQuestId() {
        return ClientEditorState.get().draft().isPresent() ? editorSelectedQuest : ClientQuestState.get().selected();
    }

    @Override
    public Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY) {
        // Modal editor surfaces cover the graph and detail icons, so they must also suppress
        // optional lookup hit testing instead of relying on scissoring alone.
        if (editorOverlays.active() != EditorOverlayHost.Kind.NONE || hoveredRecipeLookupTarget == null
                || !hoveredRecipeLookupTarget.contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        return Optional.of(hoveredRecipeLookupTarget);
    }

    @Override
    public void prepareForTransientChildScreen() {
        // The matching ScreenEvent.Opening is raised before removed(), allowing a recipe viewer
        // to suspend this exact editor object without closing its server-authoritative lease.
        childLifecycle.prepareChild();
    }

    /** Records only the visible pixels of an item icon; the last rendered layer wins hover priority. */
    private boolean registerRecipeLookupTarget(ItemStack stack, UiRect bounds, UiRect viewport,
                                               double mouseX, double mouseY) {
        Optional<RecipeLookupTarget> target = RecipeLookupTarget.clipped(stack, bounds, viewport)
                .filter(candidate -> candidate.contains(mouseX, mouseY));
        target.ifPresent(candidate -> hoveredRecipeLookupTarget = candidate);
        return target.isPresent();
    }

    private UiRect detailRecipeLookupViewport() {
        return new UiRect(detailLeft() + 1, detailContentTop(), width - 10,
                detailContentBottom());
    }

    /** Draws the one winning hover surface at the final z-order, above details and navigation chrome. */
    private void renderDeferredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Component lookupHint = hoveredRecipeLookupTarget == null
                ? null : RecipeLookupHint.current().orElse(null);
        if (!hoveredComponentTooltip.isEmpty()) {
            if (lookupHint == null) {
                graphics.renderComponentTooltip(font, hoveredComponentTooltip, mouseX, mouseY);
            } else {
                List<Component> tooltip = new ArrayList<>(hoveredComponentTooltip);
                tooltip.add(lookupHint);
                graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            }
        } else if (!hoveredDetailStack.isEmpty()) {
            // The optional integration appends its hint through GatherComponents so the complete
            // vanilla/modded ItemStack tooltip pipeline remains intact.
            graphics.renderTooltip(font, hoveredDetailStack, mouseX, mouseY);
        } else if (hoveredDetailText != null) {
            if (lookupHint == null) {
                graphics.renderTooltip(font, hoveredDetailText, mouseX, mouseY);
            } else {
                graphics.renderComponentTooltip(font, List.of(hoveredDetailText, lookupHint), mouseX, mouseY);
            }
        } else if (lookupHint != null) {
            graphics.renderTooltip(font, lookupHint, mouseX, mouseY);
        }
    }

    private String questTitle(QuestDefinition quest) {
        String localized = localizedQuestText(quest, "title", quest.title());
        return QuestPresentation.questTitle(localized, () -> objectiveTitleFallback(quest));
    }

    /** Keeps legacy untitled quests readable without replacing an authored quest title with its first objective. */
    private String objectiveTitleFallback(QuestDefinition quest) {
        if (quest.tasks().isEmpty()) return Component.translatable("screen.brnquest.quest.untitled").getString();
        TaskDefinition task = quest.tasks().getFirst();
        String custom = task.config().getOrDefault("title", "");
        if (!custom.isBlank()) return custom;
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(view);
        ItemStack stack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        long stored = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        return presentation.title(new TaskPresentationContext(minecraft, view, status(quest), stored, stack)).getString();
    }

    /** Locale resolution is presentation-only; the synchronized book retains every source translation. */
    private String localizedQuestText(QuestDefinition quest, String field, String fallback) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return fallback;
        String sourceId = quest.legacyId().isBlank() ? quest.id().toString() : quest.legacyId();
        String locale = minecraft.getLanguageManager().getSelected();
        return snapshot.book().localization().resolve(locale, "quest." + sourceId + "." + field, fallback);
    }

    /** Every editor entry point reads the same locale-resolved value that the player-facing screen renders. */
    private String localizedQuestTextValue(QuestDefinition quest, QuickTextKind kind) {
        return switch (kind) {
            case TITLE -> {
                String value = localizedQuestText(quest, "title", quest.title());
                yield value.isBlank() ? questTitle(quest) : value;
            }
            case SUBTITLE -> localizedQuestText(quest, "quest_subtitle", quest.subtitle());
            case DESCRIPTION -> localizedQuestText(quest, "quest_desc", quest.description());
            case NONE -> "";
        };
    }

    private String localizedStructureTitle(String kind, ResourceLocation id, String fallback) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return fallback;
        String sourceId = snapshot.book().legacyIds().entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("@") && entry.getValue().equals(id))
                .map(Map.Entry::getKey).findFirst().orElse(id.toString());
        return snapshot.book().localization().resolve(minecraft.getLanguageManager().getSelected(),
                kind + "." + sourceId + ".title", fallback);
    }

    private int detailStatusY(QuestDefinition quest) { return detailsPanel.statusY(); }

    private int detailTrackX(String pin) {
        // Reserve a clear gap from the close glyph so their hitboxes can never overlap.
        return detailLeft() + 10 + (detailsWidth() - 24) - font.width(pin) - 18;
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

    /** Shrinks compact one-line labels before truncating them, preserving more meaningful text. */


    /** Right-aligns compact detail text while using the same shrink-before-truncate policy. */


    /**
     * Draws built-in item semantics as a styled prefix and returns that prefix's exact hover area.
     * Other presentations retain the public objective-title path and do not inherit item semantics.
     */


    private void fillChamfer(GuiGraphics graphics, int x, int y, int size, int color) {
        int radius = size / 2;
        int cut = Math.max(1, size / 6);
        graphics.fill(x - radius + cut, y - radius, x + radius - cut + 1, y + radius + 1, color);
        graphics.fill(x - radius, y - radius + cut, x + radius + 1, y + radius - cut + 1, color);
    }

    private int nodeSize(QuestDefinition quest) {
        return QuestNodeGeometry.visualSize(NODE_BASE_SIZE, quest.appearance());
    }

    private static double safeAppearanceScale(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 1.0;
    }

    /** Renders the portable native shape subset and gives unknown imported shapes a safe chamfer fallback. */
    private void fillNodeShape(GuiGraphics graphics, String shape, int x, int y, int size, int color) {
        int radius = size / 2;
        switch (shape.toLowerCase(Locale.ROOT)) {
            case "square" -> graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
            case "circle" -> {
                for (int dy = -radius; dy <= radius; dy++) {
                    int half = (int) Math.floor(Math.sqrt(Math.max(0, radius * radius - dy * dy)));
                    graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
                }
            }
            case "diamond" -> {
                for (int dy = -radius; dy <= radius; dy++) {
                    int half = radius - Math.abs(dy);
                    graphics.fill(x - half, y + dy, x + half + 1, y + dy + 1, color);
                }
            }
            default -> fillChamfer(graphics, x, y, size, color);
        }
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
        DraftBookEditor.Position preview = nodeDrag.preview(quest.id());
        return graphCoordinate(preview == null ? quest.x() : preview.x());
    }

    private int nodeGraphY(QuestDefinition quest) {
        DraftBookEditor.Position preview = nodeDrag.preview(quest.id());
        return graphCoordinate(preview == null ? quest.y() : preview.y());
    }

    private QuestDefinition nodeAt(ChapterDefinition chapter, double graphMouseX, double graphMouseY) {
        for (int index = chapter.quests().size() - 1; index >= 0; index--) {
            QuestDefinition quest = chapter.quests().get(index);
            if (!questVisible(quest)) continue;
            int radius = QuestNodeGeometry.hitRadius(NODE_BASE_SIZE, quest.appearance());
            if (Math.abs(graphMouseX - nodeGraphX(quest)) <= radius
                    && Math.abs(graphMouseY - nodeGraphY(quest)) <= radius) return quest;
        }
        return null;
    }

    private void selectOnly(ResourceLocation questId) {
        editorSelection.clear();
        editorSelection.add(questId);
    }

    private void beginNodeDrag(ResourceLocation anchorId, double graphMouseX, double graphMouseY,
                               double screenMouseX, double screenMouseY, QuestBookDefinition book) {
        Map<ResourceLocation, DraftBookEditor.Position> origins = new LinkedHashMap<>();
        for (QuestDefinition quest : book.quests()) {
            if (editorSelection.contains(quest.id())) origins.put(quest.id(), new DraftBookEditor.Position(quest.x(), quest.y()));
        }
        nodeDrag.begin(origins, anchorId, graphMouseX, graphMouseY, screenMouseX, screenMouseY, System.nanoTime());
        if (nodeDrag.active()) dragging = false;
    }

    /** Resolves click versus long-press independently from render or mouse-event frequency. */

    /** Completes the click branch only after release proves that no drag gesture was intended. */
    private void openEditorQuestDetails(ResourceLocation questId) {
        if (questId == null) return;
        editorSelectedQuest = questId;
        closeQuestEditingPanels();
        openDetailsPanel();
        detailsPanel.scroll().snap(0);
    }

    private void commitNodeDrag() {
        var committed = nodeDrag.releaseMove();
        if (committed.isEmpty()) return;
        List<AuthoringNetwork.PositionWire> positions = committed.entrySet().stream()
                .map(entry -> new AuthoringNetwork.PositionWire(entry.getKey().toString(),
                        entry.getValue().x(), entry.getValue().y())).toList();
        if (!sendMutation("MOVE_QUESTS", null, null, null, "", 0, 0, 0, positions)) nodeDrag.clearPreview();
    }

    private void cancelNodeDrag() { nodeDrag.cancel(); }

    private void reconcileDragPreview() {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return;
        nodeDrag.reconcile(id -> {
            QuestDefinition quest = snapshot.quests().get(id);
            return quest == null ? null : new DraftBookEditor.Position(quest.x(), quest.y());
        }, ClientEditorState.get().mode() == ClientEditorState.Mode.ERROR);
    }

    private double graphX(double screenX) {
        return (screenX - graphOriginX()) / renderedZoom;
    }

    private double graphY(double screenY) {
        return (screenY - graphOriginY()) / renderedZoom;
    }

    private double graphOriginX() {
        return screenOriginX() + renderedPanX;
    }

    private double graphOriginY() {
        return contentCenterY() + renderedPanY;
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
        return layout().canvasLeft(navigationDrawerMotion.current());
    }

    private int canvasRight() {
        return layout().canvasRight(detailsDrawerMotion.current());
    }

    private int navigationHandleLeft() {
        return canvasLeft() - navigationHandleWidth();
    }

    private int navigationDrawerOffsetX() {
        return layout().navigationDrawerOffset(navigationDrawerMotion.current());
    }

    private int detailsDrawerOffsetX() {
        return layout().detailsDrawerOffset(detailsDrawerMotion.current());
    }

    private boolean navigationPanelVisibleAt(double x) {
        return x >= NAV_LEFT && x < navigationHandleLeft();
    }

    private boolean navigationPanelAcceptsPointer(double x) {
        return !navigationCollapsed && navigationDrawerMotion.current() >= 1.0
                && navigationPanelVisibleAt(x);
    }

    private boolean detailsPanelVisibleAt(double x) {
        return x >= canvasRight() && x < width;
    }

    private boolean detailsPanelAcceptsPointer(double x) {
        return detailsOpen && detailsDrawerMotion.current() >= 1.0 && detailsPanelVisibleAt(x);
    }

    private boolean detailsDrawerVisible() {
        return detailsDrawerMotion.current() > 0.0;
    }

    private int navigationWidth() {
        return layout().navigationWidth();
    }

    private int navigationHandleWidth() {
        return layout().navigationHandleWidth();
    }

    private int detailsWidth() {
        return layout().detailsWidth();
    }

    private int topToolbarHeight() {
        return layout().topToolbarHeight();
    }

    private int bottomToolbarHeight() {
        return layout().bottomToolbarHeight();
    }

    private int navigationTop() {
        return topToolbarHeight();
    }

    private int navigationBottomMargin() {
        return bottomToolbarHeight();
    }

    private int detailContentTop() {
        return topToolbarHeight() + 8;
    }

    private int detailContentBottomMargin() {
        return bottomToolbarHeight() + 8;
    }

    /** The scrolling detail body stops above editor tabs instead of rendering behind them. */
    private int detailContentBottom() {
        int ordinaryBottom = height - detailContentBottomMargin();
        int controlAwareBottom = ClientEditorState.get().editing()
                ? Math.min(ordinaryBottom, questPropertyButtonBounds().top() - 4)
                : ordinaryBottom;
        return Math.max(detailContentTop() + 1, controlAwareBottom);
    }

    private QuestScreenLayout layout() {
        if (renderFrame != null) return renderFrame.layout();
        QuestScreenLayout current = cachedLayout;
        if (current == null || current.width() != width || current.height() != height
                || current.navigationCollapsed() != navigationCollapsed || current.detailsOpen() != detailsOpen) {
            // Width profiles change only on resize/toggles; animation progress is applied to this snapshot.
            current = new QuestScreenLayout(width, height, navigationCollapsed, detailsOpen);
            cachedLayout = current;
        }
        return current;
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
        if (renderFrame != null) return renderFrame.snapshot();
        return ClientEditorState.get().draft().orElseGet(() -> ClientQuestState.get().book().orElse(null));
    }

    private QuestScreenFrameIdentity currentFrameIdentity() {
        if (renderFrame != null) return renderFrame.identity();
        QuestBookSnapshot snapshot = displaySnapshot();
        return snapshot == null ? null
                : QuestScreenFrameIdentity.of(snapshot, ClientEditorState.get().editing(), width, height);
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
        zoomMotion.snap(zoom);
        renderedZoom = zoom;
        panX = QuestViewportMath.panForGraphCenter(remembered.centerX(), zoom);
        panY = QuestViewportMath.panForGraphCenter(remembered.centerY(), zoom);
        renderedPanX = panX;
        renderedPanY = panY;
        selectionFocus.reset(panX, panY);
        rememberedChapterId = remembered.chapterId();
        navigationCollapsed = remembered.navigationCollapsed();
        navigationDrawerMotion.snap(navigationCollapsed ? 0.0 : 1.0);
        rememberedChapterResolved = false;
        navigationPanel.scroll().snap(0);
        detailsPanel.scroll().snap(0);
        detailsOpen = false;
        detailsDrawerMotion.snap(0.0);
        editorSelectedQuest = null;
    }

    private void saveViewport(ResourceLocation chapterId) {
        finishZoomMotion();
        QuestScreenSessionState.save(serverContextId, viewportBookId, chapterId,
                QuestViewportMath.graphCenterForPan(panX, zoom),
                QuestViewportMath.graphCenterForPan(panY, zoom), zoom, navigationCollapsed);
    }

    /** Advances wheel zoom every rendered frame while preserving the physical-screen-center anchor. */
    private void advanceZoomMotion(double elapsedSeconds) {
        double oldZoom = zoom;
        zoom = zoomMotion.advanceFrame(elapsedSeconds, zoomSmoothSpeed());
        if (Double.compare(oldZoom, zoom) != 0) {
            panX = QuestViewportMath.panForStableAnchor(width / 2.0, screenOriginX(), panX, oldZoom, zoom);
            panY = QuestViewportMath.panForStableAnchor(height / 2.0, height / 2.0, panY, oldZoom, zoom);
        }
        renderedZoom = zoom;
        renderedPanX = panX;
        renderedPanY = panY;
    }

    /** Advances both drawer reveal boundaries from the same frame time used by scrolling and zoom. */
    private void advanceDrawerMotion(double elapsedSeconds) {
        navigationDrawerMotion.target(navigationCollapsed ? 0.0 : 1.0);
        detailsDrawerMotion.target(detailsOpen ? 1.0 : 0.0);
        navigationDrawerMotion.advanceFrame(elapsedSeconds, drawerSmoothSpeed());
        detailsDrawerMotion.advanceFrame(elapsedSeconds, drawerSmoothSpeed());
    }

    /** Starts one focus pass for a newly opened or newly selected quest, then releases manual control. */
    private void updateSelectedQuestFocus(QuestBookSnapshot snapshot, double elapsedSeconds) {
        QuestDefinition selected = detailsOpen ? selectedQuest() : null;
        ResourceLocation id = selected == null ? null : selected.id();
        boolean requested = selectionFocus.observe(detailsOpen, id);
        if (!autoFocusSelectedQuest() || !detailsOpen || selected == null) {
            cancelSelectedQuestFocus();
            return;
        }
        if (requested) {
            finishZoomMotion();
            selectionFocus.start(id, renderedPanX, renderedPanY);
        }
        ResourceLocation focusing = selectionFocus.focusing();
        if (focusing == null) return;
        QuestDefinition target = snapshot.quests().get(focusing);
        if (target == null) {
            cancelSelectedQuestFocus();
            return;
        }
        EditorSelectionFocus.Point point = selectionFocus.advance(dragging || nodeDrag.active(),
                renderedPanX, renderedPanY, () -> {
                    QuestScreenLayout finalLayout = layout();
                    double targetX = (finalLayout.canvasLeft(navigationCollapsed ? 0.0 : 1.0)
                            + finalLayout.canvasRight(detailsOpen ? 1.0 : 0.0)) / 2.0;
                    return new EditorSelectionFocus.Point(
                            QuestViewportMath.panForGraphPoint(nodeGraphX(target), targetX, screenOriginX(), renderedZoom),
                            QuestViewportMath.panForGraphPoint(nodeGraphY(target), finalLayout.contentCenterY(),
                                    finalLayout.contentCenterY(), renderedZoom));
                }, elapsedSeconds, focusSmoothSpeed());
        panX = renderedPanX = point.x();
        panY = renderedPanY = point.y();
    }

    private void cancelSelectedQuestFocus() {
        selectionFocus.cancel(renderedPanX, renderedPanY);
        panX = renderedPanX;
        panY = renderedPanY;
    }

    /** Auto-collapses navigation only for a genuine closed-to-open details transition. */
    private void openDetailsPanel() {
        if (!detailsOpen) navigationCollapsed = true;
        detailsOpen = true;
    }

    /** Supplies bounded wall-clock frame time so pauses do not collapse an animation into one jump. */
    private double motionFrameSeconds() {
        long now = System.nanoTime();
        double elapsed = previousMotionFrameNanos == 0
                ? 1.0 / 60.0
                : (now - previousMotionFrameNanos) / 1_000_000_000.0;
        previousMotionFrameNanos = now;
        return Math.max(0.0, Math.min(0.05, elapsed));
    }

    private static double scrollStep() {
        return BrnQuestClientConfig.VALUES.scrollStep.get();
    }

    private static double scrollSmoothSpeed() {
        return BrnQuestClientConfig.VALUES.smoothSpeed.get();
    }

    private static double zoomSmoothSpeed() {
        return BrnQuestClientConfig.VALUES.zoomSmoothSpeed.get();
    }

    private static double drawerSmoothSpeed() {
        return BrnQuestClientConfig.VALUES.drawerSmoothSpeed.get();
    }

    private static double focusSmoothSpeed() {
        return BrnQuestClientConfig.VALUES.focusSmoothSpeed.get();
    }

    private static boolean autoFocusSelectedQuest() {
        return BrnQuestClientConfig.VALUES.autoFocusSelectedQuest.get();
    }

    /** Cancels residual easing at the currently drawn transform before direct manipulation begins. */
    private void adoptRenderedCamera() {
        cancelSelectedQuestFocus();
        zoom = renderedZoom;
        zoomMotion.snap(zoom);
        panX = renderedPanX;
        panY = renderedPanY;
    }

    /** Persists the player's requested zoom rather than an arbitrary mid-animation frame. */
    private void finishZoomMotion() {
        double targetZoom = zoomMotion.target();
        if (Double.compare(zoom, targetZoom) != 0) {
            panX = QuestViewportMath.panForStableAnchor(width / 2.0, screenOriginX(), panX, zoom, targetZoom);
            panY = QuestViewportMath.panForStableAnchor(height / 2.0, height / 2.0, panY, zoom, targetZoom);
            zoom = targetZoom;
        }
        zoomMotion.snap(zoom);
        renderedPanX = panX;
        renderedPanY = panY;
        renderedZoom = zoom;
    }

    private String currentServerContext() {
        if (minecraft == null) return "unknown";
        var remote = minecraft.getCurrentServer();
        if (remote != null) return "remote:" + remote.ip;
        var integrated = minecraft.getSingleplayerServer();
        return integrated == null ? "unknown" : "integrated:" + integrated.getWorldData().getLevelName();
    }

    private int navigationViewportHeight() {
        return Math.max(1, navigationListBottom() - navigationTop());
    }

    private int navigationListBottom() {
        return height - navigationBottomMargin() - (ClientEditorState.get().editing() ? 20 : 0);
    }

    private int detailViewportHeight() {
        return Math.max(1, detailContentBottom() - detailContentTop());
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

    private record TaskCandidateHitbox(UiRect bounds, TaskDefinition task) {}

    enum ItemChoiceOpenMode {
        VIEW_CANDIDATES,
        SELECT_INVENTORY
    }


    private record QuickTextHitbox(QuickTextKind kind, UiRect bounds) {}

    private record TypedRowPresentation(Component typeName, String symbol, ItemStack stack) {}

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
