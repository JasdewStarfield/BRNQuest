package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorLocalizedText;
import yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditPreview;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.ContentAwareCache;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
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
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorFormFields;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorQuickTextDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothValue;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestModeSelection;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestIconEditorRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextureSelector;
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
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Quest-book UI with grouped navigation, a scalable directed graph, and intent-only details. */
public final class QuestScreen extends Screen implements RecipeLookupSource, TransientChildScreenParent {
    private static final int EDITOR_CATALOG_ROW_HEIGHT = 30;
    private static final int EDITOR_CATALOG_SEARCH_HEIGHT = 18;
    private static final int DEPENDENCY_ROW_HEIGHT = 32;
    private static final int TYPED_ROW_HEIGHT = 38;
    private static final int MAX_TYPED_CONFIG_FIELDS = 8;
    private static final List<String> QUEST_SHAPES = List.of("chamfer", "square", "circle", "diamond");
    private static final List<String> REWARD_CLAIM_POLICIES = java.util.Arrays.stream(RewardClaimPolicy.values())
            .map(RewardClaimPolicy::serializedName).toList();

    private int attentionPingOffsetY;
    private long previousMotionFrameNanos;
    private long lastCooldownRecoveryRequestMillis;
    private double currentMotionFrameSeconds = 1.0 / 60.0;
    private final EditorSmoothValue navigationDrawerMotion = new EditorSmoothValue(1.0, 0.001);
    private final EditorSmoothValue detailsDrawerMotion = new EditorSmoothValue(0.0, 0.001);
    private final QuestNavigationPanel navigationPanel = new QuestNavigationPanel();
    private final QuestDetailsPanel detailsPanel = new QuestDetailsPanel();

    private final QuestDetailsInteraction detailsInteraction = new QuestDetailsInteraction();
    private final QuestTaskRowWidget taskRowWidget = new QuestTaskRowWidget();
    private final QuestRewardCellWidget rewardCellWidget = new QuestRewardCellWidget();
    private final QuestCanvasRenderer canvasRenderer = new QuestCanvasRenderer();
    private final QuestCanvasController canvasController = new QuestCanvasController();
    private QuestCanvasRenderer.Frame canvasFrame;
    private int chapterIndex;
    private QuestScreenLayout cachedLayout;
    // Scoped to one render call; this is not a second revision cache.
    private RenderFrame renderFrame;
    private record RenderFrame(QuestBookSnapshot snapshot, QuestScreenLayout layout,
                               QuestScreenFrameIdentity identity) {}
    private ResourceLocation rememberedChapterId;
    private boolean rememberedChapterResolved;
    private boolean detailsOpen;
    private boolean navigationCollapsed;
    private String serverContextId = "unknown";
    private ResourceLocation viewportBookId;
    private ResourceLocation editorSelectedQuest;
    private boolean editorSelectionMode;
    private boolean reopenEditorChecked;
    private Boolean closingEditingPreference;
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
    // Chapter icon changes stay local until the dialog is applied. Child selectors only update this value.
    private String structureChapterIcon = "";
    private QuestIconEditorRow.Layout chapterIconRow;
    private IconEditorMode chapterIconMode = IconEditorMode.ITEM;

    private final EditorFormFields<String> structureFields = new EditorFormFields<String>()
            .define("id", "screen.brnquest.editor.structure.id", 256)
            .define("title", "screen.brnquest.editor.structure.title", 256)
            .define("texture", "screen.brnquest.editor.quest.icon_mode.texture", 256)
            .define("chapter_item", "screen.brnquest.editor.quest.icon", 256)
            .define("group_description", "screen.brnquest.editor.group.description", 2048);
    private EditorTextField quickTextField;
    private EditorLocalizedText quickLocalizedText;
    private record PendingLocalizedQuestText(ResourceLocation questId, EditorLocalizedQuestTextScreen.Value value) {}
    private PendingLocalizedQuestText pendingLocalizedQuestText;
    private EditorLocalizedText structureLocalizedTitle;
    private Boolean structureConsumeItems;
    private boolean structureHideDependencyLines;
    private Boolean questHideDependencyLines;
    private UiRect questHideLinesBounds;
    private ResourceLocation structureAutofocusQuestId;
    private yourscraft.jasdewstarfield.brnquest.data.CanvasScene structureArtwork = yourscraft.jasdewstarfield.brnquest.data.CanvasScene.EMPTY;
    private QuestCreationDefaults structureDefaults = QuestCreationDefaults.EMPTY;
    private QuickTextKind quickTextKind = QuickTextKind.NONE;
    private ResourceLocation quickTextQuestId;
    private Component quickTextIssue;
    private final yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll propertyScroll = new yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll();
    private double propertyDrawnScroll;
    private int propertyRowCount;
    private boolean questEditorOpen;
    private List<Object> formBaseline = List.of();
    private ResourceLocation questEditorQuestId;
    private Component questEditorMessage;
    private Component clipboardMessage;
    private long clipboardMessageUntil;
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
    private final QuestEditorChrome editorChrome = new QuestEditorChrome();
    private final QuestPublishReviewSection publishReviewSection = new QuestPublishReviewSection();
    private final EditorPickerList<ResourceLocation> typedTypePicker = new EditorPickerList<>();
    private QuestTypePickerModel.Frame typedTypePickerFrame;
    private Component typedEditorMessage;
    private final QuestTypedPropertySection typedPropertySection =
            new QuestTypedPropertySection(MAX_TYPED_CONFIG_FIELDS);
    private Component typedPropertyMessage;
    private UiRect enumDropdownAnchor;
    private List<String> enumDropdownValues = List.of();
    private java.util.function.Function<String, Component> enumDropdownLabel = Component::literal;
    private Consumer<String> enumDropdownConsumer;
    private ResourceLocation discardSwitchTarget;
    private boolean discardClosesScreen;
    private ResourceLocation draftChoiceBookId;
    private String draftChoiceActiveRevision = "";
    private String draftChoiceDraftRevision = "";
    private String draftChoiceTitle = "";
    private final TransientScreenLifecycle childLifecycle = new TransientScreenLifecycle();
    private final EditorOverlayHost editorOverlays = new EditorOverlayHost();
    private final CanvasArtwork canvasArtwork = new CanvasArtwork();

    private ContextKind editContextKind = ContextKind.NONE;
    private ResourceLocation editContextTarget;
    private java.util.Set<ResourceLocation> contextSelection = java.util.Set.of();
    private java.util.Set<ResourceLocation> deleteSelection = java.util.Set.of();
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
    private final ContentAwareCache<ResourceLocation, String, ItemStack> itemCache = new ContentAwareCache<>();
    private ItemStack hoveredDetailStack = ItemStack.EMPTY;
    private Component hoveredDetailText;
    private List<Component> hoveredComponentTooltip = List.of();
    // Legacy form dispatch shares one rendered-button feedback adapter.
    private final List<EditorTextField> propertyNativeFields = new ArrayList<>();
    private final EditorButtonInput formButtons = new EditorButtonInput();
    private QuestScreenFrameIdentity formButtonFrame;
    private EditorOverlayHost.Kind formButtonOverlay;
    private RecipeLookupTarget hoveredRecipeLookupTarget;
    private ResourceLocation recoveryCopyBookId;

    public QuestScreen() {
        super(Component.translatable("screen.brnquest.title"));
        QuestScreenSessionState.Snapshot defaults = QuestScreenSessionState.Snapshot.defaults();
        canvasController.resetCamera(defaults.centerX(), defaults.centerY(), defaults.zoom());
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
        editorChrome.invalidate();
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
        propertyNativeFields.clear();
        questFields.bind(font, this::registerPropertyField);
        structureFields.bind(font, this::addRenderableWidget);
        typedPropertySection.bind(font, this::registerPropertyField);
        quickTextField = reinitializeOverlayEditorField(quickTextField,
                "screen.brnquest.editor.quick_edit.input", 256);
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
        QuestScreenSessionState.rememberEditing(serverContextId,
                closingEditingPreference != null ? closingEditingPreference : editor.editing());
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

    @Override public boolean isPauseScreen() {
        // Pause world simulation in either mode. Main-thread packet work and client lease ticks still run.
        var snapshot = ClientEditorState.get().draft().orElseGet(() -> ClientQuestState.get().book().orElse(null));
        return minecraft != null && minecraft.hasSingleplayerServer() && snapshot != null
                && snapshot.book().settings().pauseGame();
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
        restoreEditingPreference(editor);
        reconcileModeSelection(editor);
        // Session loss must also remove the typed form widgets, not merely stop painting its panel.
        if (!editor.editing() && (typedEditorOpen || typedPropertySection.open())) closeTypedEditor();
        if (questEditorOpen && !editor.editing()) closeQuestEditor();
        if (dependencyEditorOpen && !editor.editing()) closeDependencyEditor();
        if ((!editor.editing() && switch (editorOverlays.active()) {
                case CONTEXT_MENU, ENUM_DROPDOWN, STRUCTURE_FORM, DELETE_CONFIRMATION, QUEST_RENAME_CONFIRMATION,
                        QUICK_TEXT, PUBLISH_CONFIRMATION -> true;
                default -> false;
            }) || (!editor.allowed() && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG))) {
            closeActiveEditorOverlay();
        }
        editor.pollPublishReview().ifPresent(review -> {
            closeActiveEditorOverlay();
            publishReviewSection.open(publishReviewModel(review));
            editorOverlays.show(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION);
        });
        reconcileDragPreview();
        requestExpiredRepeatRecovery();
        // Only a live selected quest may query execution status; previews/rendering remain read-only.
        QuestDefinition selected = selectedQuest();
        if (gameplayAllowed() && selected != null && isCompleted(status(selected))) {
            selected.rewards().stream().filter(reward -> reward.typeId().equals(
                    yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableReward.ID)
                    || reward.typeId().equals(yourscraft.jasdewstarfield.brnquest.reward.LootTableReward.ID))
                    .forEach(reward -> RewardTableClientState.refresh(reward.id().toString()));
        }
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
    /** Wait for permissions and book synchronization, then attempt reopening once per screen. */
    private void restoreEditingPreference(ClientEditorState editor) {
        if (reopenEditorChecked) return;
        if (editor.editing() || !QuestScreenSessionState.reopenEditing(serverContextId)) {
            reopenEditorChecked = true;
            return;
        }
        if (editor.mode() == ClientEditorState.Mode.CATALOG_LOADING || editor.busy()) return;
        if (!editor.allowed()) { reopenEditorChecked = true; return; }
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return;
        reopenEditorChecked = true;
        // Resume ordinary editing through the same handshake as the explicit toolbar action.
        if (editor.beginOpenCurrent(snapshot.book().id())) AuthoringNetwork.openLiveSession(snapshot.book().id());
    }

    private record PendingEditUi(java.util.UUID session, ResourceLocation book, ResourceLocation chapter,
                                 ResourceLocation quest, boolean details, Set<ResourceLocation> selection) {}
    private PendingEditUi pendingEditUi;

    /** A rejected delete/rename also restores the pre-submit selection instead of leaving an empty detail panel. */
    private void rememberPendingEditUi() {
        var editor = ClientEditorState.get();
        pendingEditUi = new PendingEditUi(editor.sessionId(), editor.bookId(), currentChapterId(),
                editorSelectedQuest, detailsOpen, canvasController.allSelection());
    }
    private void reconcilePendingEditUi(ClientEditorState editor) {
        if (pendingEditUi == null || editor.busy()) return;
        var previous = pendingEditUi;
        pendingEditUi = null;
        if (editor.mode() != ClientEditorState.Mode.ERROR || !Objects.equals(previous.session(), editor.sessionId())
                || !Objects.equals(previous.book(), editor.bookId()) || editor.draft().isEmpty()) return;
        var chapters = QuestPresentation.orderedChapters(editor.draft().orElseThrow().book());
        for (int i = 0; i < chapters.size(); i++) if (chapters.get(i).id().equals(previous.chapter())) {
            chapterIndex = i; rememberedChapterId = previous.chapter(); rememberedChapterResolved = true; break;
        }
        editorSelectedQuest = previous.quest(); detailsOpen = previous.details();
        canvasController.restoreSelection(previous.selection());
        canvasController.clearPreview(); canvasController.cancelFocus();
        itemCache.clear();
    }

    private void reconcileModeSelection(ClientEditorState editor) {
        reconcilePendingEditUi(editor);
        reconcileLocalizedQuestText(editor);
        boolean nextEditorMode = editor.draft().isPresent();
        boolean modeChanged = nextEditorMode != editorSelectionMode;
        QuestBookSnapshot target = nextEditorMode
                ? editor.displayDraft().orElse(null) : ClientQuestState.get().book().orElse(null);
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
            canvasController.clearSelection();
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

    private yourscraft.jasdewstarfield.brnquest.data.CanvasScene backgroundPreview;
    private boolean previewBookScope;
    private final CanvasArtwork previewArtwork = new CanvasArtwork();

    /** Preview is a render-only override: no snapshot, draft revision, or undo entry is changed. */
    void renderBackgroundPreview(GuiGraphics graphics, int width, int height, float partialTick,
            yourscraft.jasdewstarfield.brnquest.data.CanvasScene value, boolean bookScope) {
        ChildScreenBackground.resizeIfNeeded(this, width, height);
        backgroundPreview = value; previewBookScope = bookScope;
        try { render(graphics, -1, -1, partialTick); }
        finally { backgroundPreview = null; previewBookScope = false; }
    }
    private yourscraft.jasdewstarfield.brnquest.data.CanvasScene previewChapterArtwork(ChapterDefinition chapter) {
        var scene = chapter == null ? yourscraft.jasdewstarfield.brnquest.data.CanvasScene.EMPTY : chapter.canvasScene();
        // Book properties preview their defaults directly, even if the current chapter has overrides.
        return backgroundPreview == null ? scene : scene.withBackgrounds(previewBookScope
                ? yourscraft.jasdewstarfield.brnquest.data.CanvasScene.EMPTY : backgroundPreview);
    }
    private yourscraft.jasdewstarfield.brnquest.data.CanvasScene previewBookArtwork(QuestBookDefinition book) {
        return backgroundPreview != null && previewBookScope ? book.canvasScene().withBackgrounds(backgroundPreview) : book.canvasScene();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Network work can install or clear the draft between two screen ticks. Reconcile here
        // before this frame reads either snapshot or selection, preventing a transient null task
        // from cancelling and restarting an otherwise unchanged auto-focus animation.
        reconcileModeSelection(ClientEditorState.get());
        formButtons.begin();
        formButtonFrame = currentFrameIdentity();
        // Tooltips are collected by content layers and rendered only after every opaque panel.
        hoveredDetailStack = ItemStack.EMPTY;
        hoveredDetailText = null;
        hoveredComponentTooltip = List.of();
        hoveredRecipeLookupTarget = null;
        // Blur the world once, then render every BRNQuest layer above it.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.CANVAS);
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
            if (backgroundPreview == null) advanceDrawerMotion(motionFrameSeconds);
            // Sample once per frame so every visible notification hops in lockstep.
            attentionPingOffsetY = AttentionPingAnimation.verticalOffset(System.nanoTime());

            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            ChapterDefinition selectedChapter = null;
            if (!chapters.isEmpty()) {
                resolveRememberedChapter(chapters);
                chapterIndex = Math.min(chapterIndex, chapters.size() - 1);
                selectedChapter = chapters.get(chapterIndex);
            }
            yourscraft.jasdewstarfield.brnquest.data.CanvasScene bookArtwork = previewBookArtwork(snapshot.book());
            if (!previewChapterArtwork(selectedChapter).screenAbove(bookArtwork)) CanvasArtwork.screenBackground(graphics, yourscraft.jasdewstarfield.brnquest.data.CanvasScene.effective(
                    previewChapterArtwork(selectedChapter).screen(), bookArtwork.screen()), width, height);
            if (backgroundPreview != null) {
                // Preview owns the whole window: omit all chrome instead of leaving its layout reservations.
                // Keep drawer and camera animation state suspended so returning restores the original view.
                renderCanvas(graphics, selectedChapter, mouseX, mouseY);
                return;
            }
            advanceCanvasMotion(motionFrameSeconds);
            if (!structureFormOpen() || structureFormKind == StructureFormKind.RENAME_CHAPTER) renderCanvas(graphics, selectedChapter, mouseX, mouseY);
            else {
                canvasFrame = null;
                graphics.fill(canvasLeft(), topToolbarHeight(), canvasRight(),
                        height - bottomToolbarHeight(), GraystonePalette.DRAWER);
            }
            renderNavigation(graphics, snapshot.book(), selectedChapter, mouseX, mouseY, motionFrameSeconds);
            if (detailsDrawerVisible() && !structureFormOpen()) {
                int visibleLeft = canvasRight();
                formButtons.viewport(new UiRect(visibleLeft, topToolbarHeight(), width, height - bottomToolbarHeight()), detailsDrawerOffsetX());
                graphics.enableScissor(visibleLeft, topToolbarHeight(), width, height - bottomToolbarHeight());
                int detailMouseX = detailsPanelAcceptsPointer(mouseX) ? mouseX : Integer.MIN_VALUE;
                int detailMouseY = detailsPanelAcceptsPointer(mouseX) ? mouseY : Integer.MIN_VALUE;
                graphics.pose().pushPose();
                graphics.pose().translate(detailsDrawerOffsetX(), 0, 0);
                renderDetails(graphics, detailMouseX, detailMouseY, motionFrameSeconds);
                graphics.pose().popPose();
                graphics.disableScissor();
                formButtons.viewport(null, 0);
            }
            // Draw drawer seams after their contents so every form and list shares the same boundary.
            int panelTop = topToolbarHeight(), panelBottom = height - bottomToolbarHeight();
            if (canvasLeft() > 2) {
                int edge = canvasLeft();
                graphics.fill(edge-2, panelTop, edge-1, panelBottom, GraystonePalette.LIP);
                graphics.fill(edge-1, panelTop, edge, panelBottom, GraystonePalette.SEAM);
            }
            if (detailsDrawerVisible()) {
                int edge = canvasRight();
                graphics.fill(edge, panelTop, edge+1, panelBottom, GraystonePalette.SEAM);
                graphics.fill(edge+1, panelTop, edge+2, panelBottom, GraystonePalette.LIP);
            }
            renderEditorChrome(graphics, snapshot.book(), mouseX, mouseY);
            // Item rendering and canvas badges use positive depth. Raise the entire modal, including
            // registered EditBoxes and tooltips, above them; restore the pose even if rendering fails.
            graphics.flush();
            graphics.pose().pushPose();
            try {
                if (structureFormOpen()) graphics.pose().translate(0, 0, 500);
                if (structureFormOpen()) renderStructureForm(graphics, mouseX, mouseY);
                offsetDetailsDrawerFieldsForMotion();
                // Native inputs render later than their rows, so give them the same viewport explicitly.
                var inputClip = propertyViewport().translated(detailsDrawerOffsetX(), 0);
                graphics.enableScissor(Math.max(canvasRight(), inputClip.left()), inputClip.top(), inputClip.right(), inputClip.bottom());
                try {
                    for (var field : propertyNativeFields) field.render(graphics,
                            inputClip.containsExclusive(mouseX, mouseY) ? mouseX : Integer.MIN_VALUE,
                            inputClip.containsExclusive(mouseX, mouseY) ? mouseY : Integer.MIN_VALUE, partialTick);
                } finally { graphics.disableScissor(); }
                super.render(graphics, mouseX, mouseY, partialTick);
                renderDeferredTooltip(graphics, mouseX, mouseY);
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            renderFrame = null;
        }
    }

    private void renderNavigation(GuiGraphics graphics, QuestBookDefinition book, ChapterDefinition selectedChapter,
                                  int mouseX, int mouseY, double motionFrameSeconds) {
        var navigationLayout = new QuestNavigationPanel.Layout(navigationWidth(), topToolbarHeight(), height - bottomToolbarHeight(),
                navigationListBottom(), navigationHandleLeft(), navigationDrawerOffsetX(),
                navigationHandleWidth(), contentCenterY(), navigationCollapsed);
        QuestNavigationPanel.RenderResult result = navigationPanel.render(graphics, font,
                new QuestNavigationPanel.Model(currentFrameIdentity(), book, selectedChapter,
                        ClientEditorState.get().editing(), defaultGroupId(book) != null),
                navigationLayout,
                motionFrameSeconds, scrollSmoothSpeed(),
                navigationPanelAcceptsPointer(mouseX) ? mouseX : Integer.MIN_VALUE,
                navigationPanelAcceptsPointer(mouseX) ? mouseY : Integer.MIN_VALUE,
                minecraft.getLanguageManager().getSelected(),
                (chapter, bounds) -> renderChapterIcon(graphics, chapter, bounds),
                (group, bounds) -> {
                    // Group rows are smaller than chapter rows; scale the existing item/texture renderer together.
                    graphics.pose().pushPose();
                    graphics.pose().translate(bounds.left(), bounds.top(), 0);
                    graphics.pose().scale(bounds.width() / 16F, bounds.height() / 16F, 1);
                    renderChapterIcon(graphics, group.id(), group.icon(), new UiRect(0, 0, 16, 16));
                    graphics.pose().popPose();
                });
        // Register only the stationary, visible footer; moving drawers reject actions as well as feedback.
        if (ClientEditorState.get().editing() && !navigationCollapsed && navigationDrawerOffsetX() == 0) {
            formButtons.register(navigationLayout.groupButton(), true);
            formButtons.register(navigationLayout.chapterButton(), defaultGroupId(book) != null);
        }
        if (!result.tooltip().isEmpty()) hoveredComponentTooltip = result.tooltip();
    }

    /** Chapter icons share the existing cached item parser; decorative icons never become JEI targets. */
    private void renderChapterIcon(GuiGraphics graphics, ChapterDefinition chapter, UiRect bounds) {
        renderChapterIcon(graphics, chapter.id(), chapter.icon(), bounds);
    }

    private void renderChapterIcon(GuiGraphics graphics, ResourceLocation cacheId, String icon, UiRect bounds) {
        if (icon == null || icon.isBlank()) return;
        var texture = yourscraft.jasdewstarfield.brnquest.data.QuestIconValue.textureId(icon);
        if (texture.isPresent()) {
            graphics.blit(texture.orElseThrow(), bounds.left(), bounds.top(), 0, 0, 16, 16, 16, 16);
            return;
        }
        ItemStack stack = item(cacheId, icon);
        if (!stack.isEmpty()) graphics.renderItem(stack, bounds.left(), bounds.top());
    }

    /** Applies navigation intents after resolving their stable IDs against the current book snapshot. */
    private void handleNavigationIntent(QuestNavigationPanel.Intent intent, QuestBookDefinition book) {
        switch (intent.action()) {
            case TOGGLE_DRAWER -> navigationCollapsed = !navigationCollapsed;
            case ADD_GROUP -> {
                if (ClientEditorState.get().editing()) {
                    openStructureForm(StructureFormKind.ADD_GROUP, null, null, 0, 0);
                }
            }
            case ADD_CHAPTER -> {
                ResourceLocation groupId = defaultGroupId(book);
                if (ClientEditorState.get().editing() && groupId != null) {
                    openStructureForm(StructureFormKind.ADD_CHAPTER, null, groupId, 0, 0);
                }
            }
            case OPEN_GROUP_CONTEXT, OPEN_CHAPTER_CONTEXT -> {
                if (ClientEditorState.get().editing() && intent.targetId() != null) {
                    openEditContext(intent.action() == QuestNavigationPanel.Action.OPEN_GROUP_CONTEXT
                                    ? ContextKind.GROUP : ContextKind.CHAPTER,
                            intent.targetId(), intent.pointerX(), intent.pointerY(), 0, 0);
                }
            }
            case SELECT_CHAPTER -> selectNavigationChapter(book, intent.targetId());
        }
    }

    private void selectNavigationChapter(QuestBookDefinition book, ResourceLocation chapterId) {
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(book);
        int index = -1;
        for (int candidate = 0; candidate < chapters.size(); candidate++) {
            if (chapters.get(candidate).id().equals(chapterId)) {
                index = candidate;
                break;
            }
        }
        if (index < 0) return;
        chapterIndex = index;
        rememberedChapterId = chapterId;
        rememberedChapterResolved = true;
        canvasArtwork.cancel();
        canvasController.resetChapter();
        focusChapter(chapters.get(index));
        detailsOpen = false;
        detailsPanel.scroll().snap(0);
        closeQuestEditingPanels();
    }

    private void renderCanvas(GuiGraphics graphics, ChapterDefinition chapter, int mouseX, int mouseY) {
        canvasArtwork.selectionController(canvasController);
        QuestCanvasRenderer.Camera camera = canvasController.renderedCamera(screenOriginX(), contentCenterY());
        if (backgroundPreview == null) canvasController.advancePointer(camera, mouseX, mouseY, System.nanoTime());
        boolean editing = ClientEditorState.get().editing();
        boolean allowGameplay = gameplayAllowed();
        List<QuestCanvasRenderer.NodeModel> nodes = new ArrayList<>();
        if (chapter != null) {
            for (QuestDefinition quest : chapter.quests()) {
                if (!questVisible(quest)) continue;
                QuestStatus status = status(quest);
                QuestPresentation.QuestVisual visual = QuestPresentation.visual(quest);
                ItemStack stack = visual.kind() == QuestPresentation.VisualKind.ITEM
                        ? item(quest.id(), visual.itemSnbt()) : ItemStack.EMPTY;
                boolean hiddenText = !editing && quest.behavior().hideTextUntilComplete() && !isCompleted(status);
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal(hiddenText ? "???" : questTitle(quest)).withStyle(ChatFormatting.WHITE));
                String subtitle = hiddenText ? "" : localizedQuestText(quest, "quest_subtitle", quest.subtitle());
                if (!subtitle.isBlank()) tooltip.add(Component.literal(subtitle).withStyle(ChatFormatting.GRAY));
                if (allowGameplay) tooltip.add(Component.translatable("screen.brnquest.status."
                        + status.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
                DraftBookEditor.Position preview = canvasController.preview(quest.id());
                nodes.add(new QuestCanvasRenderer.NodeModel(quest.id(), quest.appearance(),
                        preview == null ? new DraftBookEditor.Position(quest.x(), quest.y()) : preview,
                        canvasController.snapPreview(quest.id()), canvasNodeColor(status, allowGameplay),
                        editing ? canvasController.selected(quest.id()) : quest.id().equals(selectedQuestId()),
                        allowGameplay && status == QuestStatus.ACTIVE,
                        canvasController.pickedUp() && quest.id().equals(canvasController.dragAnchor()),
                        allowGameplay && questHasAttentionTask(quest, status),
                        allowGameplay && QuestPresentation.hasPendingReward(
                                quest, status, ClientQuestState.get().claimed()),
                        visual, stack, tooltip, quest.dependencies(),
                        allowGameplay && !(status == QuestStatus.LOCKED && quest.behavior().hideLockIcon()) ? status : null));
            }
        }
        if (displaySnapshot() != null) (backgroundPreview == null ? canvasArtwork : previewArtwork).render(graphics, currentFrameIdentity(), chapter == null ? null : chapter.id(),
                previewChapterArtwork(chapter),
                previewBookArtwork(displaySnapshot().book()), camera,
                new UiRect(canvasLeft(), topToolbarHeight(), canvasRight(), height - bottomToolbarHeight()));
        QuestCanvasRenderer.RenderResult result = canvasRenderer.render(graphics, font,
                new QuestCanvasRenderer.Model(currentFrameIdentity(), chapter == null ? null : chapter.id(),
                        new UiRect(canvasLeft(), topToolbarHeight(), canvasRight(),
                                height - bottomToolbarHeight()),
                        camera, nodes, canvasController.dragActive(), attentionPingOffsetY, mouseX, mouseY,
                        detailsOpen ? selectedQuestId() : null, chapter != null && chapter.defaultHideDependencyLines()));
        canvasFrame = result.frame();
        if (!result.tooltip().isEmpty()) hoveredComponentTooltip = result.tooltip();
    }

    private static int canvasNodeColor(QuestStatus status, boolean gameplayAllowed) {
        if (!gameplayAllowed) return GraystonePalette.HOVER;
        return switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF4C9A66;
            case AVAILABLE, ACTIVE -> 0xFFCF9F42;
            default -> 0xFF575B51;
        };
    }

    private void renderDetails(GuiGraphics graphics, int mouseX, int mouseY, double motionFrameSeconds) {
        detailsInteraction.invalidate();
        int left = detailLeft();
        graphics.fill(left, topToolbarHeight(), width, height - bottomToolbarHeight(), GraystonePalette.PANEL);
        graphics.drawString(font, Component.literal("×"), width - 14, topToolbarHeight() + 4, 0xFFFFFF, false);

        QuestDefinition quest = selectedQuest();
        boolean editing = ClientEditorState.get().editing();
        detailsInteraction.begin(currentFrameIdentity(), quest == null ? null : quest.id(), editing, gameplayAllowed(),
                new UiRect(left, topToolbarHeight(), width, height - bottomToolbarHeight()),
                new UiRect(width - 18, topToolbarHeight(), width, topToolbarHeight() + 16));
        if (quest == null) {
            detailsInteraction.finish();
            return;
        }
        QuestStatus status = status(quest);
        if (!editing && quest.behavior().hideDetailsUntilStartable()
                && status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE && !isCompleted(status)) {
            graphics.drawString(font, Component.translatable("screen.brnquest.quest_details_hidden"),
                    left + 12, detailContentTop() + 8, GraystonePalette.MUTED, false);
            detailsInteraction.finish();
            return;
        }
        if (editing && questEditorOpen && quest.id().equals(questEditorQuestId)) {
            detailsPanel.reset();
            renderQuestPropertyEditor(graphics, quest, mouseX, mouseY);
            detailsInteraction.finish();
            return;
        }
        if (editing && dependencyEditorOpen && quest.id().equals(dependencyEditorQuestId)) {
            detailsPanel.reset();
            renderDependencyEditor(graphics, quest, mouseX, mouseY);
            detailsInteraction.finish();
            return;
        }
        if (editing && typedEditorOpen && quest.id().equals(typedEditorQuestId)) {
            detailsPanel.reset();
            renderTypedEditor(graphics, quest, mouseX, mouseY);
            detailsInteraction.finish();
            return;
        }
        Component cooldownText = repeatCooldownText(quest, status);
        Component statusText = !gameplayAllowed() ? Component.translatable("screen.brnquest.editor.preview")
                : cooldownText != null ? cooldownText
                : Component.translatable(QuestPresentation.statusTranslationKey(quest, status, ClientQuestState.get().claimed()));
        if (gameplayAllowed() && ClientQuestState.get().questCompletionPending(quest.id().toString()))
            statusText = Component.translatable("screen.brnquest.choice.waiting");
        UiRect content = new UiRect(left + 10, detailContentTop(), left + 10 + detailsWidth() - 24, detailContentBottom());
        UiRect clip = new UiRect(left + 1 + detailsDrawerOffsetX(), detailContentTop(),
                Math.min(width, width - 10 + detailsDrawerOffsetX()), detailContentBottom());
        String locale = minecraft.getLanguageManager().getSelected();
        boolean hideText = !editing && quest.behavior().hideTextUntilComplete() && !isCompleted(status);
        // Hidden descriptions stay empty here so Markdown is not parsed before the quest reveals its text.
        var description = hideText
                ? new yourscraft.jasdewstarfield.brnquest.data.text.ResolvedDocument("",
                yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat.PLAIN, locale)
                : BookText.resolveQuestDescription(displaySnapshot().book(), quest, locale);
        var result = detailsPanel.render(graphics, font, new QuestDetailsPanel.Layout(content, clip, width - 8),
                new QuestDetailsPanel.Model(quest,
                        hideText ? "???" : questTitle(quest),
                        hideText ? "" : localizedQuestText(quest, "quest_subtitle", quest.subtitle()),
                        description, locale,
                        status, statusText, statusColor(status),
                        editing, gameplayAllowed(), canSubmit(quest, status),
                        displaySnapshot().book().settings().suppressAutoClaim(), this::documentLinkHint),
                new QuestDetailsPanel.Rows() {
                    public int relations(int x, int y, int rowWidth) {
                        return renderQuestRelations(graphics, quest, x, y, rowWidth, content, mouseX, mouseY);
                    }
                    public int task(TaskDefinition task, int x, int y, int rowWidth) {
                        return renderTask(graphics, quest, task, status, x, y, rowWidth, mouseX, mouseY);
                    }
                    public int rewardWidth(RewardDefinition reward) {
                        return ClientRewardPresentationRegistry.get(reward.typeId()).resolvedOptions(ApiViews.reward(reward)).isPresent() ? 44 : 28;
                    }
                    public void reward(RewardDefinition reward, int x, int y) {
                        renderReward(graphics, reward, x, y, status, mouseX, mouseY);
                    }
                }, mouseX, mouseY, motionFrameSeconds, scrollSmoothSpeed());
        detailsInteraction.statusActions(result.completeAction(), result.trackAction());
        detailsInteraction.textAreas(result.textAreas());
        detailsInteraction.links(result.links());
        if (result.hint() != null) hoveredComponentTooltip = List.of(result.hint());
        if (result.hoveredLookup() != null) {
            hoveredRecipeLookupTarget = result.hoveredLookup();
            hoveredDetailStack = result.hoveredLookup().stack();
        }
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
            detailsInteraction.editorAction(QuestDetailsInteraction.Action.EDIT_PROPERTIES,
                    questPropertyButtonBounds());
            detailsInteraction.editorAction(QuestDetailsInteraction.Action.EDIT_TASKS, questTaskButtonBounds());
            detailsInteraction.editorAction(QuestDetailsInteraction.Action.EDIT_REWARDS, questRewardButtonBounds());
            detailsInteraction.editorAction(QuestDetailsInteraction.Action.EDIT_DEPENDENCIES,
                    questDependencyButtonBounds());
        }
        detailsInteraction.finish();
    }

    /** Detail entry points keep explanatory text while adding a fast-scanning icon cue. */
    private void renderDetailEditorEntry(GuiGraphics graphics, UiRect bounds, String glyph,
                                         String translationKey, int mouseX, int mouseY) {
        Component label = Component.translatable(translationKey);
        renderEditorActionButton(graphics, bounds, EditorButton.Definition.iconAndText(
                        label, label, QuestActionIcons.symbol(Component.literal(glyph))),
                true, EditorButton.Tone.PRIMARY, mouseX, mouseY);
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
        ItemChoiceMatcher.Spec itemSpec = ItemChoiceMatcher.parseConfig(task.config()).result().orElse(null);
        QuestTaskRowWidget.Model model = new QuestTaskRowWidget.Model(task, presentation, presentationContext, state,
                taskSatisfied(task, status), canSubmit(quest, status),
                itemSpec != null && needsManualItemSelection(task, itemSpec));
        QuestTaskRowWidget.Result row = taskRowWidget.render(graphics, font, model,
                new QuestTaskRowWidget.Layout(x, y, width, detailRecipeLookupViewport(),
                        mouseX, mouseY, attentionPingOffsetY));
        QuestDetailsInteraction.Action rowAction = switch (row.rowAction()) {
            case SUBMIT_TASK -> QuestDetailsInteraction.Action.SUBMIT_TASK;
            case COMPLETE_QUEST -> QuestDetailsInteraction.Action.COMPLETE_QUEST;
            case OPEN_ITEM_SLOT_SELECTION -> QuestDetailsInteraction.Action.OPEN_ITEM_SLOT_SELECTION;
        };
        detailsInteraction.task(task.id(), row.action(), row.candidates(), rowAction);
        // Candidate help uses the shared feedback adapter; semantic dispatch still belongs to the detail frame.
        if (row.candidates() != null) formButtons.register(row.candidates(), true);
        acceptTaskRowHover(row);
        return row.nextY();
    }

    private void acceptTaskRowHover(QuestTaskRowWidget.Result row) {
        if (row.lookup() != null) {
            hoveredRecipeLookupTarget = row.lookup();
            hoveredDetailStack = row.hoveredStack();
        } else if (row.hint() != null) hoveredDetailText = row.hint();
    }

    private void acceptRewardCellHover(QuestRewardCellWidget.Result cell) {
        if (cell.lookup() != null) {
            hoveredRecipeLookupTarget = cell.lookup();
            hoveredDetailStack = cell.hoveredStack();
        } else if (cell.hint() != null) hoveredDetailText = cell.hint();
    }

    /** Native-size icons share the status row; navigation never adds height to the detail flow. */
    private int renderQuestRelations(GuiGraphics graphics, QuestDefinition quest, int x, int y, int rowWidth,
            UiRect viewport, int mouseX, int mouseY) {
        var relations = QuestRelations.resolve(displaySnapshot().book().quests(), quest.id(), this::questVisible);
        renderRelationButton(graphics, new UiRect(x, y, x + 16, y + 16), viewport,
                true, relations.upstream().size(), mouseX, mouseY);
        renderRelationButton(graphics, new UiRect(x + rowWidth - 16, y, x + rowWidth, y + 16), viewport,
                false, relations.downstream().size(), mouseX, mouseY);
        return y;
    }
    private void renderRelationButton(GuiGraphics graphics, UiRect bounds, UiRect viewport, boolean upstream,
            int count, int mouseX, int mouseY) {
        int color = upstream ? QuestRelations.UPSTREAM_COLOR : QuestRelations.DOWNSTREAM_COLOR;
        String direction = upstream ? "upstream" : "downstream";
        var label = Component.translatable("screen.brnquest.relations." + direction, count);
        var visible = bounds.intersection(viewport);
        boolean hovered = visible.width() > 0 && visible.height() > 0 && visible.containsExclusive(mouseX, mouseY);
        // Render the full 16x16 PNG directly, without the generic button's padding or permanent bevel.
        if (hovered) graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), GraystonePalette.HOVER);
        EditorIcon.sprite(ResourceLocation.parse("brnquest:editor/relations/" + direction))
                .render(graphics, font, bounds, count == 0 && !hovered ? GraystonePalette.DISABLED : color);
        if (hovered) hoveredComponentTooltip = List.of(label);
        if (visible.width() > 0 && visible.height() > 0) detailsInteraction.relationToggle(upstream
                ? QuestDetailsInteraction.Action.OPEN_UPSTREAM : QuestDetailsInteraction.Action.OPEN_DOWNSTREAM, visible);
    }
    /** Live rows follow snapshot/visibility changes while the child keeps the parent session renewing. */
    private void openRelationsScreen(QuestDefinition source, boolean upstream) {
        openChildScreen(new QuestRelationsScreen(this, upstream, () -> {
            var snapshot = displaySnapshot();
            if (snapshot == null) return List.of();
            var relations = QuestRelations.resolve(snapshot.book().quests(), source.id(), this::questVisible);
            return (upstream ? relations.upstream() : relations.downstream()).stream().map(target -> {
                var state = status(target);
                boolean hideText = !ClientEditorState.get().editing() && target.behavior().hideTextUntilComplete() && !isCompleted(state);
                return new QuestRelationsScreen.Entry(target.id(), Component.literal(hideText ? "???" : questTitle(target)),
                        Component.literal(chapterTitle(snapshot.book(), target.chapterId())),
                        gameplayAllowed() ? Component.translatable("screen.brnquest.status." + state.name().toLowerCase(java.util.Locale.ROOT))
                                : Component.translatable("screen.brnquest.editor.preview"),
                        gameplayAllowed() ? statusColor(state) : GraystonePalette.MUTED);
            }).toList();
        }, id -> navigateRelatedQuest(source, id)));
    }

    /** Explicit relationship navigation overrides automatic-focus preferences, while retaining visibility checks. */
    private void navigateRelatedQuest(QuestDefinition source, ResourceLocation targetId) {
        var snapshot = displaySnapshot();
        if (snapshot == null || !QuestRelations.resolve(snapshot.book().quests(), source.id(), this::questVisible).contains(targetId)) return;
        navigateToQuest(targetId);
    }

    /** Shared destination for relation lists and future Markdown links; resolve IDs against the live book. */
    boolean navigateToQuest(ResourceLocation targetId) {
        var snapshot = displaySnapshot();
        if (snapshot == null) return false;
        var target = snapshot.quests().get(targetId);
        if (target == null || !questVisible(target)) return false;
        boolean sameChapter = target.chapterId().equals(currentChapterId());
        if (!sameChapter) selectNavigationChapter(snapshot.book(), target.chapterId());
        if (ClientEditorState.get().editing()) openEditorQuestDetails(target.id());
        else {
            ClientQuestState.get().selected(target.id()); closeQuestEditingPanels(); openDetailsPanel();
            detailsPanel.scroll().snap(0);
            BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), target.id().toString());
        }
        // Center inside the final drawer layout even when chapter autofocus points somewhere else.
        var finalLayout = layout();
        double centerX = (finalLayout.canvasLeft(navigationCollapsed ? 0.0 : 1.0) + finalLayout.canvasRight(1.0)) / 2.0;
        if (sameChapter) canvasController.requestFocus(target.id());
        else canvasController.focusChapterPoint(nodeGraphX(target), nodeGraphY(target), centerX, screenOriginX());
        if (ClientEditorState.get().editing()) canvasController.selectOnly(target.id());
        detailsInteraction.invalidate(); canvasFrame = null;
        return true;
    }

    /** Re-resolves every detail intent against the current snapshot before starting a client request. */
    private void handleDetailsIntent(QuestDetailsInteraction.Intent intent) {
        if (intent.action() == QuestDetailsInteraction.Action.CLOSE) {
            detailsOpen = false;
            detailsPanel.scroll().snap(0);
            closeQuestEditingPanels();
            return;
        }
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition quest = snapshot == null ? null : snapshot.quests().get(intent.questId());
        if (quest == null || !quest.id().equals(selectedQuestId())) return;
        switch (intent.action()) {
            case CLOSE -> { }
            case OPEN_UPSTREAM -> openRelationsScreen(quest, true);
            case OPEN_DOWNSTREAM -> openRelationsScreen(quest, false);
            case OPEN_LINK -> {
                if (intent.link() instanceof RichDocument.ExternalLink external)
                    childLifecycle.openChild(() -> net.minecraft.client.gui.screens.ConfirmLinkScreen
                            .confirmLinkNow(this, external.uri(), true));
                else if (intent.link() instanceof RichDocument.QuestLink target) {
                    ResourceLocation targetId = ResourceLocation.tryParse(target.questId());
                    if (targetId != null) navigateToQuest(targetId);
                }
            }
            case QUICK_EDIT_TEXT -> {
                if (ClientEditorState.get().editing() && intent.textArea() != null) {
                    openQuickTextEditor(quest, QuickTextKind.valueOf(intent.textArea()));
                }
            }
            case EDIT_PROPERTIES -> { if (ClientEditorState.get().editing()) { openQuestEditor(quest); } }
            case EDIT_TASKS -> { if (ClientEditorState.get().editing()) { openTypedEditor(quest, QuestTypedEntryKind.TASK); } }
            case EDIT_REWARDS -> { if (ClientEditorState.get().editing()) { openTypedEditor(quest, QuestTypedEntryKind.REWARD); } }
            case EDIT_DEPENDENCIES -> { if (ClientEditorState.get().editing()) { openDependencyEditor(quest); } }
            case OPEN_SUBMISSION_CHOICES -> {
                TaskDefinition task = quest.tasks().stream()
                        .filter(candidate -> candidate.id().equals(intent.targetId())).findFirst().orElse(null);
                if (task != null) {
                    var presentation = ClientTaskPresentationRegistry.get(task.typeId());
                    var view = ApiViews.task(task);
                    if (presentation.resolvedOptions(view).isPresent()) openChildScreen(new ResolvedOptionsScreen(this,
                            () -> presentation.resolvedOptions(view).orElse(List.of())));
                    else ItemChoiceMatcher.parseConfig(task.config()).result()
                            .ifPresent(spec -> openGameplayItemChoiceScreen(quest, task, spec, true));
                }
            }
            case COMPLETE_QUEST -> {
                QuestStatus status = status(quest);
                if (gameplayAllowed() && canSubmit(quest, status)
                        && ClientQuestState.get().beginQuestCompletion(quest.id().toString())) {
                    BrnQuestNetwork.completeCheckmark(ClientQuestState.get().revision(), quest.id().toString());
                }
            }
            case TOGGLE_TRACKED -> {
                QuestStatus status = status(quest);
                if (gameplayAllowed() && (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)) {
                    BrnQuestNetwork.toggleTracked(ClientQuestState.get().revision(), quest.id().toString());
                }
            }
            case SUBMIT_TASK, OPEN_ITEM_SLOT_SELECTION -> handleDetailsTaskIntent(intent.action(), quest,
                    intent.targetId());
            case OPEN_REWARD_OPTIONS -> {
                var reward = quest.rewards().stream().filter(value -> value.id().equals(intent.targetId())).findFirst().orElse(null);
                if (reward != null) {
                    var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
                    var view = ApiViews.reward(reward);
                    if (presentation.resolvedOptions(view).isPresent()) openChildScreen(new ResolvedOptionsScreen(this,
                            () -> presentation.resolvedOptions(view).orElse(List.of())));
                }
            }
            case CLAIM_REWARD -> {
                RewardDefinition reward = quest.rewards().stream()
                        .filter(candidate -> candidate.id().equals(intent.targetId())).findFirst().orElse(null);
                if (reward != null && gameplayAllowed() && isCompleted(status(quest))
                        && !ClientQuestState.get().claimed().contains(reward.id().toString())
                        && ClientQuestState.get().beginRewardClaim(reward.id().toString())) {
                    BrnQuestNetwork.claimReward(ClientQuestState.get().revision(), reward.id().toString());
                }
            }
        }
    }

    private void handleDetailsTaskIntent(QuestDetailsInteraction.Action action, QuestDefinition quest,
                                         ResourceLocation taskId) {
        TaskDefinition task = taskId == null ? null : quest.tasks().stream()
                .filter(candidate -> candidate.id().equals(taskId)).findFirst().orElse(null);
        QuestStatus status = status(quest);
        if (task == null || !gameplayAllowed() || !taskDisplayState(quest, task, status).actionable()) return;
        if (action == QuestDetailsInteraction.Action.OPEN_ITEM_SLOT_SELECTION) {
            ItemChoiceMatcher.parseConfig(task.config()).result().filter(spec -> needsManualItemSelection(task, spec))
                    .ifPresent(spec -> openGameplayItemChoiceScreen(quest, task, spec, false));
            return;
        }
        // Pending ownership remains in ClientQuestState so rapid clicks cannot enqueue duplicate consumption.
        if (ClientQuestState.get().beginTaskSubmission(task.id().toString())) {
            BrnQuestNetwork.completeTask(ClientQuestState.get().revision(), quest.id().toString(),
                    task.id().toString());
        }
    }

    private void renderReward(GuiGraphics graphics, RewardDefinition reward, int x, int y, QuestStatus status, int mouseX, int mouseY) {
        boolean claimed = ClientQuestState.get().claimed().contains(reward.id().toString());
        boolean claimable = gameplayAllowed() && isCompleted(status) && !claimed
                && !ClientQuestState.get().rewardClaimPending(reward.id().toString());
        ClientRewardPresentation presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        var rewardView = ApiViews.reward(reward);
        String itemSnbt = presentation.itemSnbt(rewardView);
        ItemStack parsedStack = itemSnbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), itemSnbt);
        ItemStack stack = RewardEntryDetails.resolve(minecraft, rewardView, presentation, parsedStack).item();
        var context = new RewardPresentationContext(minecraft, rewardView, claimable, claimed, stack);
        QuestRewardCellWidget.Result cell = rewardCellWidget.render(graphics, font,
                new QuestRewardCellWidget.Model(presentation, context),
                new QuestRewardCellWidget.Layout(x, y, detailRecipeLookupViewport(),
                        mouseX, mouseY, attentionPingOffsetY));
        detailsInteraction.reward(reward.id(), cell.action());
        detailsInteraction.rewardOptions(reward.id(), cell.candidates());
        if (cell.candidates() != null) formButtons.register(cell.candidates(), true);
        acceptRewardCellHover(cell);
        // Request state belongs to the screen coordinator, not the stateless reward renderer.
        boolean waiting = ClientQuestState.get().rewardClaimPending(reward.id().toString());
        if (waiting) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            graphics.drawString(font, "…", x, y - 2, GraystonePalette.ACCENT, false);
            graphics.pose().popPose();
        }
        UiRect visibleCell = new UiRect(x, y, x + 24, y + 24).intersection(detailRecipeLookupViewport());
        if (cell.lookup() == null && visibleCell.containsExclusive(mouseX, mouseY)) {
            if (waiting) hoveredDetailText = Component.translatable("screen.brnquest.choice.waiting");
            else if (!claimed && ClientQuestState.get().rewardClaimTimedOut(reward.id().toString()))
                hoveredDetailText = Component.translatable("screen.brnquest.claim.retry");
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (java.util.Objects.equals(formButtonFrame, currentFrameIdentity())
                && formButtonOverlay == editorOverlays.active()) formButtons.clicked(mouseX, mouseY, button);
        if (editorOverlays.mouseClicked(mouseX, mouseY, button)) return true;
        var snapshot = displaySnapshot();
        if (snapshot == null) return super.mouseClicked(mouseX, mouseY, button);
        if (handleEditorChromeClick(mouseX, mouseY, button, snapshot.book())) return true;

        if (dependencyEditorOpen && detailsPanelAcceptsPointer(mouseX)) {
            return handleDependencyEditorClick(mouseX, mouseY, button);
        }

        if ((questEditorOpen || typedPropertySection.open()) && detailsPanelAcceptsPointer(mouseX) && button == 0
                && propertyScroll.handleTrackClick(mouseX, mouseY, propertyViewport().right() + 2,
                propertyViewport().top(), propertyViewport().bottom(), propertyRowCount * 22, propertyViewport().height())) return true;
        if (typedEditorOpen && detailsPanelAcceptsPointer(mouseX)) {
            return handleTypedEditorClick(mouseX, mouseY, button);
        }

        if (questEditorOpen && detailsPanelAcceptsPointer(mouseX)) {
            // Partially visible controls may only receive clicks in the clipped part; footer stays independent.
            if (!propertyViewport().containsExclusive(mouseX, mouseY)
                    && !questEditorSaveBounds().contains(mouseX, mouseY)
                    && !questEditorCancelBounds().contains(mouseX, mouseY)) return true;
            if (button == 0 && questLocalizedTextEditorBounds != null
                    && questLocalizedTextEditorBounds.contains(mouseX, mouseY)) {
                QuestDefinition quest = draftQuest(questEditorQuestId);
                if (quest != null) openLocalizedQuestTextEditor(quest);
                return true;
            }
            if (button == 0 && questShapeDropdownBounds != null
                    && questShapeDropdownBounds.contains(mouseX, mouseY)) {
                openEnumDropdown(questShapeDropdownBounds, QUEST_SHAPES,
                        questFields.field("shape")::setValue, value -> authorValueLabel("shape", value));
                return true;
            }
            if (button == 0 && propertyViewport().contains(mouseX, mouseY) && questHideLinesBounds != null && questHideLinesBounds.contains(mouseX, mouseY)) {
                questHideDependencyLines = questHideDependencyLines == null ? Boolean.TRUE : questHideDependencyLines ? Boolean.FALSE : null;
                return true;
            }
            if (button == 0 && questBehaviorEditorBounds != null
                    && questBehaviorEditorBounds.contains(mouseX, mouseY)) {
                openChildScreen(new EditorQuestBehaviorScreen(this, questEditorBehavior,
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
            if (button == 0 && questEditorIconMode == IconEditorMode.TEXTURE
                    && questIconRowLayout != null && questIconRowLayout.picker().contains(mouseX, mouseY)
                    && !ClientEditorState.get().busy()) {
                openChildScreen(new EditorTextureBrowserScreen(this, questFields.field("icon")::setValue));
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

        QuestNavigationPanel.ClickResult navigationClick = navigationPanel.click(
                currentFrameIdentity(), mouseX, mouseY, button);
        if (navigationClick.intent() != null) handleNavigationIntent(navigationClick.intent(), snapshot.book());
        if (navigationClick.consumed()) return true;
        if (detailsPanelAcceptsPointer(mouseX) && mouseX >= width - 12
                && detailsPanel.contentHeight() > detailViewportHeight()
                && mouseY >= detailContentTop() && mouseY <= detailContentBottom()) {
            detailsPanel.scroll().snapFromTrack(mouseY, detailContentTop(), detailContentBottom(),
                    detailsPanel.contentHeight(), detailViewportHeight());
            return true;
        }

        // Group headings and empty navigation space belong to the left panel and
        // must not fall through into quest selection or canvas interaction.
        if (navigationPanelVisibleAt(mouseX)) return true;

        if (detailsPanelAcceptsPointer(mouseX)) {
            QuestDetailsInteraction.ClickResult result = detailsInteraction.click(
                    currentFrameIdentity(), mouseX, mouseY, button);
            if (result.intent() != null) handleDetailsIntent(result.intent());
            if (result.consumed()) return true;
        }

        if (detailsPanelVisibleAt(mouseX) && !detailsPanelAcceptsPointer(mouseX)) return true;

        int canvasRight = canvasRight();
        if (mouseX > canvasLeft() && mouseX < canvasRight && isContentY(mouseY)) {
            List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
            ChapterDefinition chapter = chapters.isEmpty() ? null : chapters.get(Math.min(chapterIndex, chapters.size() - 1));
            boolean selecting = ClientEditorState.get().editing() && (hasControlDown() || canvasController.selectionCount() > 1);
            var artwork = chapter == null || canvasFrame == null || QuestCanvasRenderer.nodeAt(canvasFrame, currentFrameIdentity(), mouseX, mouseY) != null
                    ? null : CanvasArtwork.hit(chapter.canvasScene().decorations(), canvasFrame.camera(), mouseX, mouseY);
            // Resizing is deliberately a single-selection gesture; group drags always translate all unlocked members.
            if (!ClientEditorState.get().busy() && !selecting && button == 0 && artwork != null
                    && canvasController.selectionCount() == 1 && canvasController.decorationSelected(artwork.id())
                    && CanvasArtwork.resizeHandle(artwork, canvasFrame.camera(), mouseX, mouseY)
                    && canvasArtwork.click(canvasFrame, currentFrameIdentity(), mouseX, mouseY, button)) return true;
            QuestCanvasController.ClickResult result = canvasController.mouseClicked(canvasFrame,
                    canvasInputModel(snapshot, chapter), mouseX, mouseY, button, hasControlDown(), System.nanoTime());
            if (result.consumed() && ClientEditorState.get().editing() && (selecting || artwork != null)) {
                editorSelectedQuest = null;
                detailsOpen = false;
                closeQuestEditingPanels();
            }
            if (result.intent() != null) handleCanvasIntent(result.intent(), snapshot, chapter);
            if (result.consumed()) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (editorOverlays.mouseReleased(x, y, button, super::mouseReleased)) return true;
        if (button == 0 && canvasArtwork.dragging()) {
            var replacement = canvasArtwork.release(currentFrameIdentity(), currentChapterId());
            if (replacement != null) sendCanvas(currentChapterId(), replacement);
            return true;
        }
        if (button == 0 && navigationPanel.hasDrag()) {
            var drop = navigationPanel.release(currentFrameIdentity(), x, y);
            if (drop != null) sendMutation("MOVE_CHAPTER", drop.chapter(), null, null, "", drop.index(),
                    0, 0, List.of(), Map.of("group", drop.group().toString()));
            return true;
        }
        QuestCanvasController.GestureResult result = canvasController.mouseReleased(
                canvasFrame, currentFrameIdentity(), button);
        if (result.intent() != null) handleCanvasIntent(result.intent(), displaySnapshot(), currentChapter());
        if (result.consumed()) return true;
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (editorOverlays.mouseDragged(x, y, button, dx, dy, super::mouseDragged)) return true;
        if (canvasArtwork.drag(currentFrameIdentity(), x, y, button)) return true;
        if (navigationPanel.drag(currentFrameIdentity(), x, y, button)) return true;
        QuestCanvasController.GestureResult result = canvasController.mouseDragged(
                canvasFrame, currentFrameIdentity(), x, y, button, System.nanoTime());
        if (result.consumed()) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (editorOverlays.mouseScrolled(x, y, vertical)) return true;
        if (canvasArtwork.dragging()) return true;
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
        if ((questEditorOpen || typedPropertySection.open()) && detailsPanelAcceptsPointer(x) && propertyViewport().contains(x, y)) {
            propertyScroll.scrollWheel(vertical, scrollStep(), propertyRowCount * 22, propertyViewport().height());
            return true;
        }
        if (navigationPanelVisibleAt(x) && isContentY(y)) {
            if (navigationPanelAcceptsPointer(x)) {
                navigationPanel.mouseScrolled(vertical, scrollStep());
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
        canvasController.scrollZoom(vertical);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && canvasArtwork.cancel()) return true;
        if (keyCode == 256 && navigationPanel.cancelDrag()) return true;
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
        // Only the list surface owns object clipboard shortcuts; text editors retain native Ctrl+C/V.
        if (typedListInputReady() && hasControlDown()) {
            if (keyCode == 86) { pasteTypedClipboard(); return true; }
            if (keyCode == 67) {
                typedEntryList.focusedEntry().ifPresent(this::copyTypedClipboard);
                return true;
            }
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
        if (chromeKeyboardSurfaceReady() && keyCode == 258) {
            return editorChrome.focusNext(currentFrameIdentity(), hasShiftDown());
        }
        if (chromeKeyboardSurfaceReady() && (keyCode == 257 || keyCode == 335)) {
            return editorChrome.activateFocused(currentFrameIdentity()).map(intent -> {
                handleEditorChromeIntent(intent, displaySnapshot() == null ? null : displaySnapshot().book());
                return true;
            }).orElse(false);
        }
        if (editorKeyboardSurfaceReady() && hasControlDown()) {
            if (keyCode == 67 && !canvasController.allSelection().isEmpty()) {
                copyQuestClipboard(canvasController.allSelection());
                return true;
            }
            if (keyCode == 86 && currentChapter() != null) {
                var camera = canvasController.renderedCamera(screenOriginX(), contentCenterY());
                double x = camera.graphX((canvasLeft()+canvasRight())/2.0)/QuestViewportMath.GRID_SCALE;
                double y = camera.graphY(contentCenterY())/QuestViewportMath.GRID_SCALE;
                if (BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.snapToGrid)) {
                    x = QuestViewportMath.snapQuestCoordinate(x); y = QuestViewportMath.snapQuestCoordinate(y);
                }
                pasteQuestClipboard(x, y);
                return true;
            }
            if (keyCode >= 49 && keyCode <= 52) {
                openSelectedQuestEditor(keyCode - 49);
                return true;
            }
            if (keyCode == 78) {
                ResourceLocation chapterId = currentChapterId();
                if (chapterId != null) {
                    QuestCanvasRenderer.Camera camera = canvasController.renderedCamera(
                            width / 2.0, contentCenterY());
                    openStructureForm(StructureFormKind.ADD_QUEST, null, chapterId,
                            camera.graphX(width / 2.0) / QuestViewportMath.GRID_SCALE,
                            camera.graphY(contentCenterY()) / QuestViewportMath.GRID_SCALE);
                }
                return true;
            }
            if (keyCode == 83) {
                handleEditorChromeIntent(new QuestEditorChrome.Intent(QuestEditorChrome.Action.SAVE),
                        displaySnapshot() == null ? null : displaySnapshot().book());
                return true;
            }
            if (keyCode == 90 && hasShiftDown() || keyCode == 89) {
                handleEditorChromeIntent(new QuestEditorChrome.Intent(QuestEditorChrome.Action.REDO),
                        displaySnapshot() == null ? null : displaySnapshot().book());
                return true;
            }
            if (keyCode == 90) {
                handleEditorChromeIntent(new QuestEditorChrome.Intent(QuestEditorChrome.Action.UNDO),
                        displaySnapshot() == null ? null : displaySnapshot().book());
                return true;
            }
            if (keyCode == 80 && hasShiftDown()) {
                handleEditorChromeIntent(new QuestEditorChrome.Intent(QuestEditorChrome.Action.REVIEW_PUBLISH),
                        displaySnapshot() == null ? null : displaySnapshot().book());
                return true;
            }
        }
        if (editorKeyboardSurfaceReady() && keyCode == 261 && currentChapter() != null && !canvasController.allSelection().isEmpty()) {
            requestCanvasSelectionDelete(canvasController.allSelection(), displaySnapshot().book());
            return true;
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
        Component action = editorChrome.focusedLabel(currentFrameIdentity())
                .map(label -> Component.translatable("screen.brnquest.editor.keyboard.focus", label))
                .orElseGet(() -> Component.translatable("screen.brnquest.editor.keyboard.help"));
        return Component.translatable("screen.brnquest.editor.narration", getTitle(),
                status == null ? "" : status, action);
    }

    private boolean editorKeyboardSurfaceReady() {
        return ClientEditorState.get().hasLease() && chromeKeyboardSurfaceReady();
    }

    /** Browsing players can reach client settings without acquiring an authoring session. */
    private boolean chromeKeyboardSurfaceReady() {
        return !ClientEditorState.get().busy()
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen && !typedPropertySection.open()
                && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox);
    }

    /** Executes semantic chrome intents while keeping state transitions and packets in the parent screen. */
    private void handleEditorChromeIntent(QuestEditorChrome.Intent intent, QuestBookDefinition displayedBook) {
        ClientEditorState editor = ClientEditorState.get();
        switch (intent.action()) {
            case TOGGLE_GRID_SNAP -> toggleGridSnap();
            case OPEN_CLIENT_SETTINGS -> net.neoforged.fml.config.ModConfigs.getConfigSet(
                    net.neoforged.fml.config.ModConfig.Type.CLIENT).stream()
                    .filter(config -> config.getSpec() == BrnQuestClientConfig.SPEC).findFirst()
                    .ifPresent(config -> openChildScreen(new net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen(
                            this, net.neoforged.fml.config.ModConfig.Type.CLIENT, config,
                            Component.translatable("screen.brnquest.client_settings"))));
            case SAVE -> editor.beginSave().ifPresent(request -> AuthoringNetwork.saveSession(
                    request.sessionId(), editor.bookId(), request.draftRevision()));
            case REVIEW_PUBLISH -> {
                closeActiveEditorOverlay();
                editor.beginPublishReview().ifPresent(request -> AuthoringNetwork.reviewPublish(
                    request.sessionId(), editor.bookId(), request.draftRevision()));
            }
            case REDO -> {
                if (editor.canRedo()) rememberPendingEditUi();
                editor.beginRedo().ifPresent(request -> AuthoringNetwork.history(request.sessionId(), editor.bookId(), request.draftRevision(), true));
            }
            case UNDO -> {
                if (editor.canUndo()) rememberPendingEditUi();
                editor.beginUndo().ifPresent(request -> AuthoringNetwork.history(request.sessionId(), editor.bookId(), request.draftRevision(), false));
            }
            case EXIT -> {
                if (editor.dirty()) requestDiscardConfirmation(null, false);
                else {
                    closingEditingPreference = false;
                    closeEditorSession(null);
                }
            }
            case OPEN_LIVE -> {
                closingEditingPreference = null;
                if (displayedBook != null && editor.beginOpenCurrent(displayedBook.id())) {
                    AuthoringNetwork.openLiveSession(displayedBook.id());
                }
            }
            case OPEN_ADVANCED -> {
                closingEditingPreference = null;
                requestDraftSourceChoice(displaySnapshot());
            }
            case OPEN_CATALOG -> {
                boolean opening = !editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG);
                closeActiveEditorOverlay();
                if (opening) {
                    editorOverlays.show(EditorOverlayHost.Kind.CATALOG);
                    catalogFilter = "";
                    catalogPicker.reset();
                }
            }
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

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return editorOverlays.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    /** Adds editor controls as overlays so switching modes never resizes the established three regions. */
    private void renderEditorChrome(GuiGraphics graphics, QuestBookDefinition book, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        // Item rendering uses a raised GUI depth; keep opaque editor popovers above
        // every background node/task icon without changing their screen geometry.
        graphics.pose().translate(0, 0, 300);
        ClientEditorState editor = ClientEditorState.get();
        Component status = editorStatus();
        boolean publishSurfaceReady = !editor.busy() && !questEditorOpen && !dependencyEditorOpen && !typedEditorOpen
                && !structureFormOpen() && editorOverlays.active() == EditorOverlayHost.Kind.NONE;
        List<Component> errorTooltip = editor.mode() == ClientEditorState.Mode.ERROR
                ? EditorTooltipComposer.operationError(editor.statusCode(), editor.statusMessage()) : List.of();
        QuestEditorChrome.Model chromeModel = new QuestEditorChrome.Model(currentFrameIdentity(),
                BookText.title(book, minecraft.getLanguageManager().getSelected()),
                book.id(), editor.allowed(), editor.editing(), editor.hasLease(), editor.live(), editor.busy(),
                editor.dirty(), editor.canUndo(), editor.canRedo(), editor.undoSteps(), editor.redoSteps(),
                publishSurfaceReady, editorHistorySurfaceReady(), status,
                editor.mode() == ClientEditorState.Mode.ERROR, errorTooltip, BrnQuestClientConfig.VALUES.snapToGrid.get());
        QuestEditorChrome.RenderResult chromeResult = editorChrome.render(
                graphics, font, layout(), chromeModel, mouseX, mouseY);
        if (chromeResult.hoveredDetail() != null) hoveredDetailText = chromeResult.hoveredDetail();
        if (!chromeResult.tooltip().isEmpty()) hoveredComponentTooltip = chromeResult.tooltip();

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
        formButtonOverlay = editorOverlays.active();
        if (formButtonOverlay != EditorOverlayHost.Kind.NONE) formButtons.begin();
        editorOverlays.render(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    /** Shared controls outside the extracted chrome still use the same semantic button renderer. */
    private void renderEditorActionButton(GuiGraphics graphics, UiRect bounds, EditorButton.Definition definition,
                                          boolean enabled, EditorButton.Tone tone,
                                          int mouseX, int mouseY) {
        boolean hovered = formButtons.render(graphics, font, bounds, definition, enabled,
                false, tone, mouseX, mouseY);
        if (hovered && !definition.tooltip().isEmpty()) {
            hoveredComponentTooltip = definition.tooltip();
        }
    }

    /** Text actions use the same semantic rendering path as icon-backed actions. */
    private void renderEditorTextButton(GuiGraphics graphics, UiRect bounds, Component label,
                                        Component tooltip, boolean enabled, EditorButton.Tone tone,
                                        int mouseX, int mouseY) {
        renderEditorActionButton(graphics, bounds, EditorButton.Definition.text(label, tooltip),
                enabled, tone, mouseX, mouseY);
    }

    /** Compact icon buttons retain a localized semantic label for Tooltip and future narration. */
    private void renderEditorIconButton(GuiGraphics graphics, UiRect bounds, Component glyph,
                                        Component accessibleLabel, boolean enabled, boolean dangerous,
                                        int mouseX, int mouseY) {
        EditorButton.Definition definition = EditorButton.Definition.iconOnly(
                accessibleLabel, accessibleLabel, QuestActionIcons.symbol(glyph));
        renderEditorActionButton(graphics, bounds, definition, enabled,
                dangerous ? EditorButton.Tone.DANGER : EditorButton.Tone.PRIMARY, mouseX, mouseY);
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
        openEnumDropdown(anchor, values, consumer, Component::literal);
    }

    /** The caller supplies labels; dropdown values remain stable configuration tokens. */
    private void openEnumDropdown(UiRect anchor, List<String> values, Consumer<String> consumer,
                                  java.util.function.Function<String, Component> labels) {
        if (anchor == null || values.isEmpty() || consumer == null || ClientEditorState.get().busy()) return;
        closeActiveEditorOverlay();
        enumDropdownAnchor = anchor;
        enumDropdownValues = List.copyOf(values);
        enumDropdownConsumer = consumer;
        enumDropdownLabel = labels;
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
            entries.add(new EditorPopupMenu.Entry("ENUM_" + index, enumDropdownLabel.apply(enumDropdownValues.get(index)),
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
        enumDropdownLabel = Component::literal;
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
            case "ADD_DECORATION" -> addDecorationAt(graphX, graphY);
            case "DECORATION_PROPERTIES" -> openDecorationProperties(target);
            case "SET_CHAPTER_AUTOFOCUS" -> setChapterAutofocus(book, target);
            case "COPY_SELECTION_SNAPSHOT" -> copyQuestClipboard(contextSelection);
            case "COPY_QUEST_SNAPSHOT" -> copyQuestClipboard(java.util.Set.of(CanvasSelectionKey.quest(target)));
            case "PASTE_QUEST_SNAPSHOT" -> pasteQuestClipboard(graphX, graphY);
            case "COPY_SELECTION" -> sendSelectionMutation("COPY_CANVAS_SELECTION", contextSelection);
            case "DELETE_SELECTION" -> requestCanvasSelectionDelete(contextSelection, book);
            case "CLEAR_SELECTION" -> canvasController.clearSelection();
            case "COPY_QUEST" -> openStructureForm(StructureFormKind.COPY_QUEST, target, null, graphX + 1, graphY + 1);
            case "DELETE_QUEST" -> requestDelete(DeleteKind.QUEST, target, book);
            case "ADD_CHAPTER" -> openStructureForm(StructureFormKind.ADD_CHAPTER, null, target, 0, 0);
            case "RENAME_GROUP" -> openStructureForm(StructureFormKind.RENAME_GROUP, target, null, 0, 0);
            case "MOVE_GROUP_UP" -> moveGroup(book, target, -1);
            case "MOVE_GROUP_DOWN" -> moveGroup(book, target, 1);
            case "DELETE_GROUP" -> requestDelete(DeleteKind.GROUP, target, book);
            case "BOOK_PROPERTIES" -> openBookProperties();
            case "COPY_CHAPTER" -> sendMutation("COPY_CHAPTER", target, null, null, "", 0, 0, 0, List.of());
            case "RENAME_CHAPTER" -> openStructureForm(StructureFormKind.RENAME_CHAPTER, target, null, 0, 0);
            case "MOVE_CHAPTER_UP" -> moveChapter(book, target, -1);
            case "MOVE_CHAPTER_DOWN" -> moveChapter(book, target, 1);
            case "DELETE_CHAPTER" -> requestDelete(DeleteKind.CHAPTER, target, book);
            case "EDIT_TYPED", "COPY_TYPED", "COPY_TYPED_SNAPSHOT", "MOVE_TYPED_UP", "MOVE_TYPED_DOWN",
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

    /** Shortcut writes the same live config exposed by the settings screen, never an authoring mutation. */
    private void toggleGridSnap() {
        var preference = BrnQuestClientConfig.VALUES.snapToGrid;
        preference.set(!preference.get());
        BrnQuestClientConfig.SPEC.save();
    }

    /** Reuse the chapter mutation so shortcut changes retain metadata and participate in undo. */
    private void setChapterAutofocus(QuestBookDefinition book, ResourceLocation questId) {
        if (questId == null || ClientEditorState.get().busy()) return;
        book.chapters().stream().filter(chapter -> chapter.quests().stream().anyMatch(q -> q.id().equals(questId)))
                .findFirst().filter(chapter -> !questId.equals(chapter.autofocusQuestId())).ifPresent(chapter ->
                        sendMutation("UPDATE_CHAPTER", chapter.id(), chapter.groupId(), null, chapter.title(),
                                chapter.order(), 0, 0, List.of(), Map.of("autofocus_id", questId.toString())));
    }

    private void openBookProperties() {
        if (ClientEditorState.get().busy()) return;
        var book = displaySnapshot().book();
        openChildScreen(new EditorBookPropertiesScreen(this, book, minecraft.getLanguageManager().getSelected(), value -> {
            var config = new java.util.TreeMap<String, String>();
            config.put("fallback_locale", value.fallback());
            config.put("quest_defaults", value.defaults().toJson().toString());
            config.put("book_settings", value.settings().toJson().toString());
            config.put("backgrounds", value.backgrounds().encode());
            config.put(LocalizedSingleLineEdits.FIELD, "title");
            value.titles().forEach((locale, title) -> config.put(LocalizedSingleLineEdits.PREFIX + locale, title));
            sendMutation("UPDATE_BOOK_PROPERTIES", book.id(), null, null, "", 0, 0, 0, List.of(), config);
        }));
    }

    private List<EditorPopupMenu.Entry> contextMenuEntries() {
        return switch (editContextKind) {
            case CANVAS -> EditorPopupMenu.menu(menu -> menu.action("ADD_QUEST",
                    Component.translatable("screen.brnquest.editor.context.add_quest"), false)
                    .action("PASTE_QUEST_SNAPSHOT", Component.translatable("screen.brnquest.quest_clipboard.paste"), false, canPasteQuestClipboard())
                    .action("ADD_DECORATION", EditorCanvasScreen.label("add_here"), false, currentChapterId() != null)
                    .action("BOOK_PROPERTIES", Component.translatable("screen.brnquest.book.properties"), false));
            case DECORATION -> EditorPopupMenu.menu(menu -> menu.action("DECORATION_PROPERTIES", EditorCanvasScreen.label("properties"), false)
                    .action("COPY_SELECTION_SNAPSHOT", Component.translatable("screen.brnquest.quest_clipboard.copy"), false)
                    .action("COPY_SELECTION", Component.translatable("screen.brnquest.selection.copy", contextSelection.size()), false)
                    .action("PASTE_QUEST_SNAPSHOT", Component.translatable("screen.brnquest.quest_clipboard.paste"), false, canPasteQuestClipboard())
                    .action("DELETE_SELECTION", Component.translatable("screen.brnquest.selection.delete", contextSelection.size()), true));
            case SELECTION -> EditorPopupMenu.menu(menu -> menu
                    .action("COPY_SELECTION_SNAPSHOT", Component.translatable("screen.brnquest.quest_clipboard.copy"), false)
                    .action("PASTE_QUEST_SNAPSHOT", Component.translatable("screen.brnquest.quest_clipboard.paste"), false, canPasteQuestClipboard())
                    .action("COPY_SELECTION", Component.translatable("screen.brnquest.selection.copy", contextSelection.size()), false)
                    .action("DELETE_SELECTION", Component.translatable("screen.brnquest.selection.delete", contextSelection.size()), true)
                    .action("CLEAR_SELECTION", Component.translatable("screen.brnquest.selection.clear"), false));
            case NODE -> EditorPopupMenu.menu(menu -> menu
                    .action("SELF_FORCE_QUEST", Component.translatable("screen.brnquest.admin.self_force"), false,
                            canManageProgress(editContextTarget, null))
                    .action("SELF_RESET_QUEST", Component.translatable("screen.brnquest.admin.self_reset"), true,
                            canManageProgress(editContextTarget, null))
                    .action("ADMIN_QUEST", adminProgressLabel(editContextTarget, null), false,
                            canManageProgress(editContextTarget, null))
                    .action("SET_CHAPTER_AUTOFOCUS", Component.translatable("screen.brnquest.chapter.autofocus.set_here"), false)
                    .action("COPY_QUEST_SNAPSHOT", Component.translatable("screen.brnquest.quest_clipboard.copy"), false)
                    .action("COPY_QUEST", Component.translatable("screen.brnquest.editor.context.copy_quest"), false)
                    .action("DELETE_QUEST", Component.translatable(
                            "screen.brnquest.editor.context.delete_quest"), true)).stream()
                    .filter(entry -> !(entry.action().equals("ADMIN_QUEST") || entry.action().startsWith("SELF_"))
                            || hasAdminProgressPermission()).toList();
            case GROUP -> EditorPopupMenu.menu(menu -> menu
                    .action("ADD_CHAPTER", Component.translatable(
                            "screen.brnquest.editor.context.add_chapter"), false)
                    .action("RENAME_GROUP", Component.translatable("screen.brnquest.editor.structure.group_properties"), false)
                    .submenu(Component.translatable("screen.brnquest.editor.context.move"), move -> move
                            .action("MOVE_GROUP_UP", Component.translatable(
                                    "screen.brnquest.editor.context.move_up"), false)
                            .action("MOVE_GROUP_DOWN", Component.translatable(
                                    "screen.brnquest.editor.context.move_down"), false))
                    .action("DELETE_GROUP", Component.translatable("screen.brnquest.editor.context.delete"), true));
            case CHAPTER -> EditorPopupMenu.menu(menu -> menu
                    .action("COPY_CHAPTER", Component.translatable("screen.brnquest.chapter.copy"), false)
                    .action("RENAME_CHAPTER", Component.translatable(
                            "screen.brnquest.editor.structure.chapter_properties"), false)
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
                    .action("COPY_TYPED_SNAPSHOT", Component.translatable("screen.brnquest.clipboard.copy"), false)
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
            case "COPY_TYPED_SNAPSHOT" -> copyTypedClipboard(typedId);
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

    /** Store detached definitions before navigation; this does not send a server mutation. */
    private void copyQuestClipboard(java.util.Set<ResourceLocation> ids) {
        if (ClientEditorState.get().busy()) return;
        try {
            EditorDecorationClipboard.clear();
            EditorQuestClipboard.copy(serverContextId,
                    yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot.capture(displaySnapshot().book(), currentChapterId(),
                            CanvasSelectionKey.ids(ids, false), CanvasSelectionKey.ids(ids, true)));
            clipboardMessage = Component.translatable("screen.brnquest.quest_clipboard.copied", ids.size());
        } catch (IllegalArgumentException exception) {
            clipboardMessage = Component.translatable("screen.brnquest.clipboard.too_large");
        }
        clipboardMessageUntil = System.currentTimeMillis() + 5000;
    }
    private boolean canPasteQuestClipboard() {
        return !ClientEditorState.get().busy() && currentChapterId() != null && displaySnapshot() != null
                && EditorQuestClipboard.get(serverContextId, displaySnapshot().book().id()).isPresent();
    }
    /** The selection's minimum X/Y corner anchors at the canvas context-menu position. */
    private void pasteQuestClipboard(double x, double y) {
        if (!canPasteQuestClipboard()) return;
        EditorQuestClipboard.get(serverContextId, displaySnapshot().book().id()).ifPresent(value -> {
            clipboardMessage = null;
            sendMutation("PASTE_QUESTS", currentChapterId(), null, null, "", 0, x, y, List.of(), Map.of("snapshot", value.encode()));
        });
    }

    /** Copy freezes every config string, including localized text and opaque nested reward data. */
    private void copyTypedClipboard(ResourceLocation entryId) {
        var quest = selectedQuest();
        var entry = quest == null ? null : typedEditorKind.entry(quest, entryId);
        if (entry == null) return;
        try {
            EditorTypedClipboard.copy(serverContextId, entry.task() != null
                    ? yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(entry.task())
                    : yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(entry.reward()));
            typedEditorMessage = Component.translatable("screen.brnquest.clipboard.copied");
        } catch (IllegalArgumentException exception) {
            typedEditorMessage = Component.translatable("screen.brnquest.clipboard.too_large");
        }
    }

    private boolean canPasteTypedClipboard() {
        var snapshot = displaySnapshot();
        return snapshot != null && EditorTypedClipboard.get(serverContextId, snapshot.book().id(),
                typedEditorKind == QuestTypedEntryKind.TASK).isPresent();
    }

    /** Each accepted paste appends one new ID and creates one server-owned undo step. */
    private void pasteTypedClipboard() {
        if (ClientEditorState.get().busy() || !typedListInputReady()) return;
        var snapshot = displaySnapshot();
        if (snapshot == null) return;
        EditorTypedClipboard.get(serverContextId, snapshot.book().id(), typedEditorKind == QuestTypedEntryKind.TASK)
                .ifPresentOrElse(value -> sendMutation("PASTE_" + typedEditorKind.actionPrefix(),
                        suggestId(snapshot.book(), typedEditorKind.idStem() + "_copy"), typedEditorQuestId, null,
                        "", 0, 0, 0, List.of(), Map.of("snapshot", value.encode())),
                        () -> typedEditorMessage = Component.translatable("screen.brnquest.clipboard.unavailable"));
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
        registerOverlay(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION, this::renderPublishConfirmation,
                this::handlePublishConfirmationClick);
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
        // Overlay-owned navigation never falls through into the suspended editor or canvas.
        EditorPickerList<?> keyboardPicker = editorOverlays.isOpen(EditorOverlayHost.Kind.TYPED_TYPE_PICKER) ? typedTypePicker
                : editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG) ? catalogPicker
                : editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER) ? dependencyPicker : null;
        if (keyboardPicker != null) {
            if (keyboardPicker.navigate(key, (modifiers & 1) != 0)) return true;
            if (key == 257 || key == 335) {
                keyboardPicker.focusedRow().ifPresent(row -> {
                    double x = row.visible().centerX(), y = row.visible().centerY();
                    if (keyboardPicker == typedTypePicker) handleTypedTypePickerClick(x, y, 0);
                    else if (keyboardPicker == dependencyPicker) handleDependencyPickerClick(x, y, 0);
                    else if (displaySnapshot() != null) handleEditorChromeClick(x, y, 0, displaySnapshot().book());
                });
                return true;
            }
        }
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
            case PUBLISH_CONFIRMATION -> publishReviewSection.mouseScrolled(layout(), amount, scrollStep());
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
                deleteSelection = java.util.Set.of();
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
                publishReviewSection.close();
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
        quickLocalizedText = new EditorLocalizedText(displaySnapshot().book().localization(),
                BookText.questPrefix(quest) + (kind == QuickTextKind.TITLE ? "title" : "quest_subtitle"),
                kind == QuickTextKind.TITLE ? quest.title() : quest.subtitle(), minecraft.getLanguageManager().getSelected());
        quickTextField.setValue(quickLocalizedText.value());
        editorOverlays.show(EditorOverlayHost.Kind.QUICK_TEXT);
        setFocused(quickTextField);
    }

    private void openLocalizedQuestTextEditor(QuestDefinition quest) {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null || minecraft == null || ClientEditorState.get().busy()) return;
        // This is the same suspended-parent transition as item and raw-config editors. Without
        // the token, Screen.removed() may close a clean server lease before Apply is dispatched.
        EditorLocalizedQuestTextScreen.Value recovery = pendingLocalizedQuestText != null
                && pendingLocalizedQuestText.questId().equals(quest.id()) ? pendingLocalizedQuestText.value() : null;
        openChildScreen(new EditorLocalizedQuestTextScreen(this, snapshot.book().localization(), quest,
                minecraft.getLanguageManager().getSelected(), value -> submitLocalizedQuestText(quest.id(), value),
                recovery, () -> discardLocalizedQuestText(quest.id())));
    }

    private boolean submitLocalizedQuestText(ResourceLocation questId, EditorLocalizedQuestTextScreen.Value value) {
        if (sendMutation("UPDATE_QUEST_TRANSLATION", questId, null, null, value.locale(), 0, 0, 0, List.of(),
                Map.of("title", value.title(), "subtitle", value.subtitle(), "description", value.description(),
                        "description_format", value.descriptionFormat().serializedName()))) {
            editorSelectedQuest = questId;
            // Keep the exact submitted buffer until the verified server draft agrees; rejection can reopen it.
            pendingLocalizedQuestText = new PendingLocalizedQuestText(questId, value);
            return true;
        }
        return false;
    }

    private void discardLocalizedQuestText(ResourceLocation questId) {
        if (pendingLocalizedQuestText != null && pendingLocalizedQuestText.questId().equals(questId))
            pendingLocalizedQuestText = null;
    }

    /** Accepted replacements clear recovery; retryable server rejection deliberately leaves it available. */
    private void reconcileLocalizedQuestText(ClientEditorState editor) {
        if (pendingLocalizedQuestText == null || editor.busy() || editor.mode() == ClientEditorState.Mode.ERROR) return;
        QuestBookSnapshot snapshot = editor.draft().orElse(null);
        if (snapshot == null) return;
        QuestDefinition quest = snapshot.quests().get(pendingLocalizedQuestText.questId());
        if (quest == null) return;
        if (LocalizedQuestTextRecovery.matches(snapshot.book(), quest, pendingLocalizedQuestText.value()))
            pendingLocalizedQuestText = null;
    }

    private void renderQuickTextEditor(GuiGraphics graphics, int mouseX, int mouseY) {
        if (quickTextKind == QuickTextKind.NONE || quickTextField == null) return;
        Component fieldName = Component.translatable(quickTextKind.translationKey());
        EditorQuickTextDialog.render(graphics, font, layout(), formButtons,
                Component.translatable("screen.brnquest.editor.quick_edit.heading", fieldName),
                quickTextIssue, !ClientEditorState.get().busy(), mouseX, mouseY);
        quickLocalizedText.remember(quickTextField.getValue());
        UiRect textRow = EditorQuickTextDialog.layout(layout()).input();
        quickTextField.show(EditorLocalizedText.inputBounds(textRow), !ClientEditorState.get().busy());
        showLocalizedPlaceholder(quickTextField, quickLocalizedText);
        renderLocaleButton(graphics, quickLocalizedText, textRow, mouseX, mouseY);
        // The editor chrome is translated to the final overlay depth. Rendering this field through
        // Screen's ordinary renderable list would leave its border and text behind the modal panel.
        quickTextField.render(graphics, mouseX, mouseY, 0.0F);
    }

    private boolean handleQuickTextEditorClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!ClientEditorState.get().busy() && EditorLocalizedText.languageBounds(
                    EditorQuickTextDialog.layout(layout()).input()).contains(mouseX, mouseY)) {
                openLocalePicker(quickLocalizedText, quickTextField);
                return true;
            }
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

    /** Confirm all local language drafts together, touching only this single field. */
    private void submitQuickTextEdit() {
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition quest = snapshot == null || quickTextQuestId == null
                ? null : snapshot.quests().get(quickTextQuestId);
        if (ClientEditorState.get().busy() || quest == null || quickTextKind == QuickTextKind.NONE
                || minecraft == null) return;
        quickLocalizedText.remember(quickTextField.getValue());
        if (quickLocalizedText.changes().isEmpty()) { closeQuickTextEditor(); return; }
        if (sendMutation("UPDATE_QUEST_TRANSLATION", quest.id(), null, null, quickLocalizedText.locale(), 0, 0, 0,
                List.of(), localizedConfig(quickLocalizedText,
                        quickTextKind == QuickTextKind.TITLE ? "title" : "quest_subtitle", Map.of()))) {
            editorSelectedQuest = quest.id();
            closeQuickTextEditor();
        }
    }

    private void closeQuickTextEditor() {
        quickTextKind = QuickTextKind.NONE;
        quickLocalizedText = null;
        quickTextQuestId = null;
        quickTextIssue = null;
        setFocused(null);
        if (quickTextField != null) quickTextField.hide();
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) editorOverlays.close();
    }

    private void renderConflictRecovery(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect panel = conflictRecoveryBounds();
        graphics.fill(0, 0, width, height, 0x99000000);
        yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.recovery.title"),
                panel.centerX(), panel.top() + 10, 0xFFFF8B8B);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.recovery.detail"),
                panel.centerX(), panel.top() + 26, GraystonePalette.SECONDARY);
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
                    panel.centerX(), panel.bottom() - 16, GraystonePalette.MUTED);
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
        if (structureMetadataForm()) {
            renderChapterProperties(graphics, mouseX, mouseY);
            return;
        }
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

    /** Modal editing matches the input boundary; selecting an icon never saves implicitly. */
    private void renderChapterProperties(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect form = structureFormBounds();
        graphics.fill(0, 0, width, height, 0x88000000);
        boolean enabled = !ClientEditorState.get().busy();
        var rows = new ArrayList<EditorPropertyPanel.RowContent>();
        // Identity is informational until a dedicated reference-migration operation is supported.
        structureFields.field("id").hide();
        rows.add(EditorPropertyPanel.readOnly(font, "screen.brnquest.editor.structure.readonly_id",
                Component.literal(structureFields.field("id").getValue()), 64));
        UiRect idRow = new UiRect(form.left() + 12, form.top() + 32, form.right() - 12, form.top() + 52);
        if (idRow.contains(mouseX, mouseY)) hoveredComponentTooltip = List.of(
                Component.literal(structureFields.field("id").getValue()),
                Component.translatable("screen.brnquest.editor.structure.id_help"));
        rows.add((g, x, y, w) -> {
            var row = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout.row(x, y, w, 64);
            yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow.label(g, font,
                    Component.translatable("screen.brnquest.editor.structure.title"), row.label(), null);
            structureFields.field("title").show(EditorLocalizedText.inputBounds(row.field()), enabled);
            showLocalizedPlaceholder(structureFields.field("title"), structureLocalizedTitle);
            renderLocaleButton(g, structureLocalizedTitle, row.field(), mouseX, mouseY);
        });
        rows.add((g, x, y, w) -> chapterIconRow = renderTextureSelector(g, x, y, w - 24,
                chapterIconInput(), chapterIconMode, mouseX, mouseY));
        if (chapterIconMode == IconEditorMode.ITEM) structureFields.field("texture").hide();
        else structureFields.field("chapter_item").hide();
        if (structureFormKind == StructureFormKind.RENAME_GROUP) {
            rows.add(EditorPropertyPanel.text(font, structureFields.field("group_description"),
                    "screen.brnquest.editor.group.description", 64, null, enabled));
        } else rows.add((g, x, y, w) -> {
            var group = displaySnapshot().book().chapterGroups().stream()
                    .filter(value -> value.id().equals(structureFormParent)).findFirst().orElse(null);
            Component name = group == null ? Component.literal(structureFormParent == null ? "" : structureFormParent.toString())
                    : Component.literal(localizedStructureTitle("chapter_group", group.id(), group.title()));
            renderEditorTextButton(g, chapterGroupBounds(),
                    Component.translatable("screen.brnquest.editor.structure.group_value", name), name,
                    enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            Component focusName = displaySnapshot().book().chapters().stream()
                    .filter(c -> c.id().equals(structureFormTarget)).flatMap(c -> c.quests().stream())
                    .filter(q -> q.id().equals(structureAutofocusQuestId)).findFirst()
                    .<Component>map(q -> Component.literal(questTitle(q)))
                    .orElse(Component.translatable("screen.brnquest.chapter.autofocus.none"));
            renderEditorTextButton(g, chapterAutofocusBounds(),
                    Component.translatable("screen.brnquest.chapter.autofocus.value", focusName),
                    structureAutofocusQuestId == null
                            ? Component.translatable("screen.brnquest.chapter.autofocus.unset_help")
                            : Component.translatable("screen.brnquest.chapter.autofocus.help", focusName),
                    enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        });
        if (structureFormKind == StructureFormKind.RENAME_CHAPTER) rows.add((g, x, y, w) -> {
            // Share the existing row so the chapter dialog still fits the minimum GUI height.
            renderEditorTextButton(g, chapterDefaultsBounds(), Component.translatable("screen.brnquest.defaults.title"),
                    null, enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            renderEditorTextButton(g, chapterHideLinesBounds(), Component.translatable("screen.brnquest.chapter.hide_dependency_lines",
                            Component.translatable(structureHideDependencyLines ? "options.on" : "options.off")),
                    Component.translatable("screen.brnquest.dependency_lines.help"), enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            renderEditorTextButton(g, chapterBackgroundBounds(),
                    EditorCanvasScreen.label("backgrounds"),
                    EditorCanvasScreen.label("backgrounds_help"), enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            Component value = Component.translatable(structureConsumeItems == null
                    ? "screen.brnquest.defaults.inherit" : structureConsumeItems ? "options.on" : "options.off",
                    Component.translatable(displaySnapshot().book().settings().consumeItems() ? "options.on" : "options.off"));
            renderEditorTextButton(g, chapterConsumeBounds(),
                    Component.translatable("screen.brnquest.chapter.consume_items", value),
                    Component.translatable("screen.brnquest.book.setting.consume_items.help"), enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        });
        EditorPropertyPanel.renderGraystone(graphics, font,
                new EditorPropertyPanel.Layout(form, form.left() + 12, form.width() - 24,
                        form.top() + 10, form.top() + 32, 32),
                Component.translatable(structureFormKind == StructureFormKind.RENAME_GROUP
                        ? "screen.brnquest.editor.structure.group_properties" : "screen.brnquest.editor.structure.chapter_properties"), 0xFFFFFFFF, rows,
                new EditorPropertyPanel.Footer(structureFormCancelBounds(), structureFormDoneBounds(),
                        Component.translatable("gui.done"), enabled && chapterTextureValid() && structureTitleValid(),
                        EditorButton.Tone.PRIMARY),
                (g, bounds, label, active, tone) -> renderEditorTextButton(
                        g, bounds, label, null, active, tone, mouseX, mouseY));
        renderEditorIconButton(graphics, chapterIconClearBounds(), Component.literal("×"),
                Component.translatable("screen.brnquest.editor.structure.clear_icon"),
                enabled && !chapterIconValue().isEmpty(), false, mouseX, mouseY);
    }

    /** Empty untranslated names may retain their fallback while other properties are saved. */
    private boolean structureTitleValid() {
        return !structureFields.field("title").getValue().isBlank()
                || structureLocalizedTitle != null && !structureLocalizedTitle.placeholder().isBlank();
    }

    private void showLocalizedPlaceholder(EditorTextField field, EditorLocalizedText text) {
        // Native suggestions are gray and remain visible with focus, but are never selected or submitted.
        field.setSuggestion(field.getValue().isEmpty() && text != null
                ? font.plainSubstrByWidth(text.placeholder(), Math.max(0, field.getWidth() - 8)) : null);
    }

    private UiRect structureTitleBounds() {
        UiRect form = structureFormBounds();
        return yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout.row(
                form.left() + 12, form.top() + 64, form.width() - 24, 64).field();
    }

    /** Both quick dialogs and property forms share the same compact language control. */
    private void renderLocaleButton(GuiGraphics graphics, EditorLocalizedText text, UiRect row, int mouseX, int mouseY) {
        if (text == null) return;
        String source = text.source();
        Component tooltip = Component.translatable(source.isEmpty() ? "screen.brnquest.editor.locale.native"
                : source.equals(text.locale()) ? "screen.brnquest.editor.locale.current" : "screen.brnquest.editor.locale.fallback", source);
        renderEditorTextButton(graphics, EditorLocalizedText.languageBounds(row), Component.literal(text.locale()),
                tooltip, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void openLocalePicker(EditorLocalizedText text, EditorTextField field) {
        if (text == null) return;
        text.remember(field.getValue());
        // Resolve the field again on return: resizing a child screen can recreate the parent's EditBox.
        boolean quick = text == quickLocalizedText;
        openChildScreen(new EditorLocaleScreen(this, text.locales(), text.locale(), locale -> {
            text.select(locale);
            (quick ? quickTextField : structureFields.field("title")).setValue(text.value());
        }));
    }

    private Map<String, String> localizedConfig(EditorLocalizedText text, String field, Map<String, String> metadata) {
        var result = new java.util.TreeMap<String, String>(metadata);
        result.put(LocalizedSingleLineEdits.FIELD, field);
        text.changes().forEach((locale, value) -> result.put(LocalizedSingleLineEdits.PREFIX + locale, value));
        return Map.copyOf(result);
    }

    private UiRect chapterHideLinesBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left()+12, form.top()+188, form.centerX()-4, form.top()+208);
    }

    /** Keep both background entry points in the existing row at the minimum GUI height. */
    private UiRect chapterBackgroundBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, form.top() + 188, form.right() - 12, form.top() + 208);
    }

    private UiRect chapterConsumeBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, form.top() + 160, form.right() - 12, form.top() + 180);
    }

    private UiRect chapterDefaultsBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, form.top() + 160, form.centerX() - 4, form.top() + 180);
    }

    private UiRect chapterGroupBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, form.top() + 128, form.centerX() - 4, form.top() + 148);
    }

    /** Stable IDs are distinct from localized labels; choosing only changes the pending form. */
    private UiRect chapterAutofocusBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, form.top() + 128, form.right() - 12, form.top() + 148);
    }

    /** The picker edits only the pending form; the parent submits the selected stable ID. */
    private void openChapterAutofocusPicker() {
        var choices = new ArrayList<EditorChoiceScreen.Choice>();
        choices.add(new EditorChoiceScreen.Choice("", Component.translatable("screen.brnquest.chapter.autofocus.none")));
        displaySnapshot().book().chapters().stream().filter(c -> c.id().equals(structureFormTarget))
                .flatMap(c -> c.quests().stream()).forEach(q -> choices.add(new EditorChoiceScreen.Choice(
                        q.id().toString(), Component.literal(questTitle(q) + " [" + q.id() + "]"))));
        openChildScreen(new EditorChoiceScreen(this, Component.translatable("screen.brnquest.chapter.autofocus"),
                choices, java.util.Objects.toString(structureAutofocusQuestId, ""),
                id -> structureAutofocusQuestId = id.isEmpty() ? null : ResourceLocation.parse(id)));
    }

    private void openChapterGroupPicker() {
        var choices = displaySnapshot().book().chapterGroups().stream()
                .sorted(java.util.Comparator.comparingInt(ChapterGroupDefinition::order)
                        .thenComparing(group -> group.id().toString()))
                .map(group -> new EditorChoiceScreen.Choice(group.id().toString(),
                        Component.literal(localizedStructureTitle("chapter_group", group.id(), group.title())))).toList();
        openChildScreen(new EditorChoiceScreen(this, Component.translatable("screen.brnquest.editor.structure.choose_group"),
                choices, structureFormParent == null ? "" : structureFormParent.toString(),
                id -> structureFormParent = ResourceLocation.parse(id)));
    }

    private UiRect chapterIconClearBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.right() - 32, form.top() + 96, form.right() - 12, form.top() + 116);
    }

    private EditorTextField chapterIconInput() {
        return structureFields.field(chapterIconMode == IconEditorMode.ITEM ? "chapter_item" : "texture");
    }

    /** Empty is an intentional absence of decoration, not a request for a fallback item. */
    private String chapterIconValue() {
        if (chapterIconMode == IconEditorMode.ITEM) {
            String id = chapterIconInput().getValue().strip();
            if (id.equals(iconItemId(structureChapterIcon))) return structureChapterIcon;
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || minecraft == null || minecraft.level == null || !BuiltInRegistries.ITEM.containsKey(parsed)) return "";
            return BuiltInRegistries.ITEM.get(parsed).getDefaultInstance().save(minecraft.level.registryAccess()).toString();
        }
        String path = structureFields.field("texture").getValue().strip();
        return path.isEmpty() ? "" : "texture:" + path;
    }

    private boolean chapterTextureValid() {
        String value = chapterIconInput().getValue().strip();
        ResourceLocation id = ResourceLocation.tryParse(value);
        return value.isEmpty() || (id != null && (chapterIconMode == IconEditorMode.TEXTURE || BuiltInRegistries.ITEM.containsKey(id)));
    }

    private boolean handleStructureFormClick(double mouseX, double mouseY, int button) {
        if (button == 0 && structureMetadataForm()
                && !ClientEditorState.get().busy()) {
            if (EditorLocalizedText.languageBounds(structureTitleBounds()).contains(mouseX, mouseY)) {
                openLocalePicker(structureLocalizedTitle, structureFields.field("title"));
                return true;
            }
            if (structureFormKind == StructureFormKind.RENAME_CHAPTER && chapterAutofocusBounds().contains(mouseX, mouseY)) {
                openChapterAutofocusPicker();
                return true;
            }
            if (structureFormKind == StructureFormKind.RENAME_CHAPTER) {
                if (chapterBackgroundBounds().contains(mouseX, mouseY)) {
                    // Child apply only updates the outer working copy; canceling chapter properties discards it.
                    openChildScreen(new EditorCanvasScreen(this, serverContextId, displaySnapshot().book().id(), false,
                            structureArtwork, 1, null, value -> { structureArtwork = value.backgrounds(); return true; }));
                    return true;
                }
            }
            if (structureFormKind == StructureFormKind.RENAME_CHAPTER && chapterHideLinesBounds().contains(mouseX, mouseY)) {
                structureHideDependencyLines = !structureHideDependencyLines; return true;
            }
            if (structureFormKind == StructureFormKind.RENAME_CHAPTER && chapterConsumeBounds().contains(mouseX, mouseY)) {
                structureConsumeItems = structureConsumeItems == null ? Boolean.TRUE : structureConsumeItems ? Boolean.FALSE : null;
                return true;
            }
            if (structureFormKind == StructureFormKind.RENAME_CHAPTER && chapterDefaultsBounds().contains(mouseX, mouseY)) {
                openChildScreen(new EditorCreationDefaultsScreen(this, structureDefaults, displaySnapshot().book().questDefaults(),
                        value -> structureDefaults = value));
                return true;
            }
            if (structureFormKind == StructureFormKind.RENAME_CHAPTER && chapterGroupBounds().contains(mouseX, mouseY)) {
                openChapterGroupPicker();
                return true;
            }
            if (chapterIconRow != null && chapterIconRow.mode().contains(mouseX, mouseY)) {
                // Each mode keeps its local candidate while toggling; only the active candidate is saved.
                chapterIconMode = chapterIconMode == IconEditorMode.ITEM ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
                setFocused(null);
                return true;
            }
            if (chapterIconMode == IconEditorMode.TEXTURE && chapterIconRow != null && chapterIconRow.picker().contains(mouseX, mouseY)) {
                openChildScreen(new EditorTextureBrowserScreen(this, structureFields.field("texture")::setValue));
                return true;
            }
            if (chapterIconMode == IconEditorMode.ITEM && chapterIconRow != null && chapterIconRow.picker().contains(mouseX, mouseY)) {
                openEditorItemSelector(stack -> {
                    if (minecraft != null && minecraft.level != null && !stack.isEmpty())
                    {
                        structureChapterIcon = stack.copyWithCount(1).save(minecraft.level.registryAccess()).toString();
                        structureFields.field("chapter_item").setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    }
                });
                return true;
            }
            if (chapterIconClearBounds().contains(mouseX, mouseY)) {
                chapterIconInput().setValue("");
                if (chapterIconMode == IconEditorMode.ITEM) structureChapterIcon = "";
                return true;
            }
        }
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
            if (group != null) {
                structureLocalizedTitle = new EditorLocalizedText(book.localization(),
                        BookText.structureTitleKey(book, "chapter_group", group.id()), group.title(), minecraft.getLanguageManager().getSelected());
                title = structureLocalizedTitle.value();
                initializeStructureIcon(group.icon());
                structureFields.field("group_description").setValue(group.description());
            }
        } else if (kind == StructureFormKind.RENAME_CHAPTER) {
            ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (chapter != null) {
                structureLocalizedTitle = new EditorLocalizedText(book.localization(),
                        BookText.structureTitleKey(book, "chapter", chapter.id()), chapter.title(), minecraft.getLanguageManager().getSelected());
                title = structureLocalizedTitle.value();
                structureFormParent = chapter.groupId();
                initializeStructureIcon(chapter.icon());
                structureDefaults = chapter.questDefaults();
                structureArtwork = chapter.canvasScene().backgrounds();
                structureConsumeItems = chapter.consumeItems();
                structureHideDependencyLines = chapter.defaultHideDependencyLines();
                structureAutofocusQuestId = chapter.autofocusQuestId();
            }
        } else {
            id = suggestId(book, kind.idStem());
            if (kind == StructureFormKind.COPY_QUEST && target != null) {
                QuestDefinition source = book.quests().stream().filter(value -> value.id().equals(target))
                        .findFirst().orElse(null);
                if (source != null) {
                    title = yourscraft.jasdewstarfield.brnquest.author.QuestCopyTitles.suggestedTitle(book, source);
                    structureFormParent = source.chapterId();
                }
            }
        }
        structureFields.field("id").setValue(id == null ? "" : id.toString());
        structureFields.field("title").setValue(title);
        structureFields.field("id").setVisible(true);
        structureFields.field("title").setVisible(true);
        editorOverlays.show(EditorOverlayHost.Kind.STRUCTURE_FORM);
        formBaseline = localFormValues();
        setFocused(kind.createsStableId() ? structureFields.field("id") : structureFields.field("title"));
    }

    /** Initialize shared icon controls from the source; switching containers must not reuse stale candidates. */
    private void initializeStructureIcon(String icon) {
        chapterIconMode = yourscraft.jasdewstarfield.brnquest.data.QuestIconValue.isTexture(icon)
                ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
        structureChapterIcon = chapterIconMode == IconEditorMode.ITEM ? icon : "";
        structureFields.field("chapter_item").setValue(iconItemId(structureChapterIcon));
        chapterIconRow = null;
        structureFields.field("texture").setValue(chapterIconMode == IconEditorMode.TEXTURE
                ? icon.substring("texture:".length()) : "");
    }

    private void submitStructureForm() {
        ClientEditorState editor = ClientEditorState.get();
        if (editor.busy()) return;
        QuestBookDefinition book = displaySnapshot().book();
        ResourceLocation id = ResourceLocation.tryParse(structureFields.field("id").getValue());
        if (id == null || !structureTitleValid()) return;
        String title = structureFields.field("title").getValue();
        if (structureLocalizedTitle != null) structureLocalizedTitle.remember(title);
        switch (structureFormKind) {
            case ADD_GROUP -> sendMutation("ADD_GROUP", id, null, null, title,
                    book.chapterGroups().size(), 0, 0, List.of());
            case RENAME_GROUP -> {
                ChapterGroupDefinition group = book.chapterGroups().stream()
                        .filter(value -> value.id().equals(structureFormTarget)).findFirst().orElse(null);
                if (!chapterTextureValid() || group == null || !sendMutation("UPDATE_GROUP", group.id(), null, null, title,
                        group.order(), 0, 0, List.of(), localizedConfig(structureLocalizedTitle, "title", Map.of("icon", chapterIconValue(),
                                "description", structureFields.field("group_description").getValue())))) return;
            }
            case ADD_CHAPTER -> sendMutation("ADD_CHAPTER", id, structureFormParent, null, title,
                    (int) book.chapters().stream().filter(value -> value.groupId().equals(structureFormParent)).count(),
                    0, 0, List.of());
            case RENAME_CHAPTER -> {
                if (!chapterTextureValid()) return;
                ChapterDefinition chapter = book.chapters().stream()
                        .filter(value -> value.id().equals(structureFormTarget)).findFirst().orElse(null);
                if (chapter == null || !sendMutation("UPDATE_CHAPTER", chapter.id(), structureFormParent, null, title,
                        chapter.groupId().equals(structureFormParent) ? chapter.order() : book.chapters().stream()
                                .filter(value -> value.groupId().equals(structureFormParent))
                                .mapToInt(ChapterDefinition::order).max().orElse(-1) + 1, 0, 0, List.of(), localizedConfig(structureLocalizedTitle, "title", Map.of("backgrounds", structureArtwork.encode(), "icon", chapterIconValue(), "quest_defaults", structureDefaults.toJson().toString(), "default_consume_items", java.util.Objects.toString(structureConsumeItems, "default"), "autofocus_id", java.util.Objects.toString(structureAutofocusQuestId, ""), "default_hide_dependency_lines", Boolean.toString(structureHideDependencyLines))))) return;
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
        structureFields.field("title").setSuggestion(null);
        structureLocalizedTitle = null;
        structureFormTarget = null;
        structureFormParent = null;
        setFocused(null);
        for (EditorTextField field : List.of(structureFields.field("id"), structureFields.field("title"), structureFields.field("texture"), structureFields.field("chapter_item"), structureFields.field("group_description"))) {
            if (field == null) continue;
            field.hide();
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)) editorOverlays.close();
    }

    private boolean structureFormOpen() {
        return editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM);
    }

    private boolean structureMetadataForm() {
        return structureFormKind == StructureFormKind.RENAME_CHAPTER || structureFormKind == StructureFormKind.RENAME_GROUP;
    }

    private UiRect structureFormBounds() {
        if (structureMetadataForm())
            return layout().centeredDialog(420, 300, 20, structureFormKind == StructureFormKind.RENAME_CHAPTER ? 244 : 184);
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
        deleteSelection = java.util.Set.of();
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

    /** Confirm one mixed delete; locked artwork stays selected but is excluded from destructive operations. */
    private void requestCanvasSelectionDelete(Set<ResourceLocation> keys, QuestBookDefinition book) {
        var locked = currentChapter().canvasScene().decorations().stream().filter(d -> d.locked())
                .map(d -> CanvasSelectionKey.decoration(d.id())).collect(java.util.stream.Collectors.toSet());
        deleteSelection = keys.stream().filter(key -> !locked.contains(key)).collect(java.util.stream.Collectors.toSet());
        if (deleteSelection.isEmpty()) return;
        deleteKind = DeleteKind.NONE;
        deleteTarget = null;
        var nodes = CanvasSelectionKey.ids(deleteSelection, false);
        long references = book.quests().stream().filter(q -> !nodes.contains(q.id()))
                .filter(q -> q.dependencies().stream().anyMatch(nodes::contains)).count();
        deleteImpact = Component.translatable("screen.brnquest.selection.delete_warning", deleteSelection.size(), references).getString();
        editorOverlays.show(EditorOverlayHost.Kind.DELETE_CONFIRMATION);
    }

    /** Freeze IDs in the request; the server reads current coordinates and validates the whole selection. */
    private void sendSelectionMutation(String action, java.util.Set<ResourceLocation> ids) {
        if (ids.isEmpty() || ClientEditorState.get().busy()) return;
        var positions = ids.stream().sorted().map(id -> new AuthoringNetwork.PositionWire(id.toString(), 0, 0)).toList();
        sendMutation(action, currentChapterId(), null, null, "", 0, 1, 1, positions);
    }

    private void renderDeleteConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        EditorConfirmDialog.render(graphics, font, layout(), formButtons,
                Component.translatable("screen.brnquest.editor.delete.warning"), Component.literal(deleteImpact),
                0xFFFFC07A, Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.delete.confirm"), mouseX, mouseY);
    }

    private boolean handleDeleteConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        EditorConfirmDialog.Action dialogAction = EditorConfirmDialog.actionAt(layout(), mouseX, mouseY);
        if (dialogAction == EditorConfirmDialog.Action.CANCEL) {
            deleteSelection = java.util.Set.of();
            deleteKind = DeleteKind.NONE;
            deleteTarget = null;
            editorOverlays.close();
            return true;
        }
        if (dialogAction == EditorConfirmDialog.Action.CONFIRM) {
            if (!deleteSelection.isEmpty()) {
                var selected = deleteSelection;
                deleteSelection = java.util.Set.of();
                editorOverlays.close();
                sendSelectionMutation("DELETE_CANVAS_SELECTION", selected);
                return true;
            }
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
                    canvasController.removeSelection(target);
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
        rememberPendingEditUi();
        var request = new AuthoringNetwork.EditorMutationWire(editor.sessionId().toString(),
                editor.bookId().toString(), editor.draftRevision(), action,
                target == null ? "" : target.toString(), parent == null ? "" : parent.toString(),
                source == null ? "" : source.toString(), title, targetIndex, x, y, positions, config);
        editor.previewMutation(request);
        AuthoringNetwork.mutate(request);
        return true;
    }

    /** Capture data revision/session, but allow window resize while the child form remains open. */
    private void openDecorationProperties(ResourceLocation decorationId) {
        var chapter = currentChapter();
        var editor = ClientEditorState.get();
        if (chapter == null || editor.busy() || !editor.editing()
                || chapter.canvasScene().decorations().stream().noneMatch(d -> d.id().equals(decorationId))) return;
        var revision = editor.draftRevision(); var session = editor.sessionId(); var book = editor.bookId();
        canvasArtwork.cancel();
        openChildScreen(new EditorCanvasScreen(this, serverContextId, book, false, chapter.canvasScene(), 0, decorationId, scene -> {
            var current = ClientEditorState.get();
            return java.util.Objects.equals(session, current.sessionId()) && java.util.Objects.equals(revision, current.draftRevision())
                    && java.util.Objects.equals(book, current.bookId()) && sendCanvas(chapter.id(), scene);
        }));
    }

    /** Freeze the right-click graph position and revision while the resource picker is open. */
    private void addDecorationAt(double x, double y) {
        var chapter = currentChapter();
        var editor = ClientEditorState.get();
        if (chapter == null || !editor.editing() || editor.busy()) return;
        var scene = chapter.canvasScene();
        if (scene.decorations().size() >= yourscraft.jasdewstarfield.brnquest.data.CanvasScene.MAX_DECORATIONS) return;
        var session = editor.sessionId(); var revision = editor.draftRevision(); var book = editor.bookId();
        boolean snap = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.snapToGrid);
        double anchorX = snap ? QuestViewportMath.snapQuestCoordinate(x) : x;
        double anchorY = snap ? QuestViewportMath.snapQuestCoordinate(y) : y;
        openChildScreen(new EditorTextureBrowserScreen(this, texture -> {
            var current = ClientEditorState.get();
            if (!java.util.Objects.equals(session, current.sessionId()) || !java.util.Objects.equals(revision, current.draftRevision())
                    || !java.util.Objects.equals(book, current.bookId())) return;
            var size = LoadedTextures.size(ResourceLocation.parse(texture));
            double h = Math.clamp(2.0 * size.height() / size.width(), 0.05, 1024);
            var values = new ArrayList<>(scene.decorations());
            values.add(new yourscraft.jasdewstarfield.brnquest.data.CanvasScene.Decoration(
                    yourscraft.jasdewstarfield.brnquest.data.CanvasScene.newId(), texture, anchorX, anchorY, 2, h, true, 0, false));
            sendCanvas(chapter.id(), new yourscraft.jasdewstarfield.brnquest.data.CanvasScene(values, scene.canvas(), scene.screen(), scene.screenAbove()));
        }));
    }

    private boolean sendCanvas(ResourceLocation target, yourscraft.jasdewstarfield.brnquest.data.CanvasScene scene) {
        return target != null && sendMutation("UPDATE_CANVAS", target, null, null, "", 0, 0, 0, List.of(), Map.of("scene", scene.encode()));
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

    private void renderEditorCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        List<ClientEditorState.CatalogEntry> entries = editorCatalogEntries();
        UiRect bounds = editorCatalogBounds();
        catalogPicker.advance(bounds, new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight()),
                entries.size(), entries::get, currentMotionFrameSeconds, scrollSmoothSpeed());
        Component searchText = catalogFilter.isBlank()
                ? Component.translatable("screen.brnquest.editor.catalog.search_hint")
                : Component.literal(catalogFilter);
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
        QuestEditorChrome.ClickResult result = editorChrome.click(currentFrameIdentity(), mouseX, mouseY);
        if (result.intent() != null) handleEditorChromeIntent(result.intent(), displayedBook);
        return result.consumed();
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

    /** Compare with the initialized form, not persisted config: opening may populate defaults or normalize icons. */
    private boolean localFormChanged() {
        return (questEditorOpen || typedPropertySection.open() || structureFormOpen())
                && !formBaseline.equals(localFormValues());
    }

    private List<Object> localFormValues() {
        if (structureFormOpen()) {
            if (structureLocalizedTitle != null) structureLocalizedTitle.remember(structureFields.field("title").getValue());
            return List.of(structureFields.field("id").getValue(),
                    structureLocalizedTitle == null ? structureFields.field("title").getValue() : structureLocalizedTitle.changes(),
                    java.util.Objects.toString(structureFormParent, ""), chapterIconMode, structureDefaults, structureArtwork, structureHideDependencyLines, java.util.Objects.toString(structureConsumeItems, "default"), java.util.Objects.toString(structureAutofocusQuestId, ""),
                    structureFields.field("group_description").getValue(),
                    structureFields.field(chapterIconMode == IconEditorMode.ITEM ? "chapter_item" : "texture").getValue());
        }
        if (typedPropertySection.open()) {
            var form = typedPropertySection.form();
            return List.of(form.id(), form.claim(), form.currentConfig(), typedPropertySection.optional(), typedPropertySection.teamReward());
        }
        if (questEditorOpen) {
            var values = new ArrayList<Object>();
            for (String key : List.of("id", "title", "subtitle", "description", "icon", "x", "y", "shape", "size", "icon_scale", "min_width"))
                values.add(questFields.field(key).getValue());
            values.add(questEditorIconMode);
            values.add(questEditorBehavior);
            values.add(java.util.Objects.toString(questHideDependencyLines, "default"));
            return List.copyOf(values);
        }
        return List.of();
    }

    private Component editorStatus() {
        ClientEditorState editor = ClientEditorState.get();
        if (editor.editing() && !editor.busy() && editor.mode() != ClientEditorState.Mode.ERROR
                && clipboardMessage != null && System.currentTimeMillis() < clipboardMessageUntil) return clipboardMessage;
        // Unsaved form values are local even when the parent draft reports saved.
        if (editor.editing() && !editor.busy() && editor.mode() != ClientEditorState.Mode.ERROR
                && localFormChanged())
            return Component.translatable("screen.brnquest.editor.scope.form");
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
                // The open editor renews its lease automatically; show the author's save state only.
                if ("PUBLISH_APPLY_COMPLETE".equals(editor.statusCode())) {
                    yield Component.translatable("screen.brnquest.editor.status.published");
                }
                yield Component.translatable(editor.dirty() ? "screen.brnquest.editor.status.dirty"
                        : "screen.brnquest.editor.status.saved");
            }
            case CLOSING -> Component.translatable("screen.brnquest.editor.session.closing");
            case ERROR -> editor.allowed() ? EditorDiagnosticPresentation.operationError(editor.statusCode()) : null;
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
                index -> typedEditorKind.value(quest, index).id(), row -> typedRowContent(quest, row.index()), canPasteTypedClipboard());
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
        if (intent.action() == QuestTypedEntryListSection.Action.PASTE) {
            pasteTypedClipboard();
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
        if (typedEditorKind == QuestTypedEntryKind.REWARD) {
            var reward = quest.rewards().get(index);
            var view = ApiViews.reward(reward);
            var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
            String snbt = presentation.itemSnbt(view);
            var details = RewardEntryDetails.resolve(minecraft, view, presentation,
                    snbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), snbt));
            return new EditorEntryListPanel.Content(new EditorEntryRow.Content(details.icon(), details.summary(),
                    typedRowSummary(quest, index, typedEditorKind.known(reward.typeId())), GraystonePalette.SECONDARY), details.lookupItem());
        }
        QuestTypedEntryKind.Value value = typedEditorKind.value(quest, index);
        TypedRowPresentation presentation = typedRowPresentation(quest, index);
        boolean known = typedEditorKind.known(value.typeId());
        EditorIcon icon = presentation.icon().orElseGet(() -> presentation.stack().isEmpty()
                ? EditorIcon.glyph(Component.literal(presentation.symbol())) : EditorIcon.item(presentation.stack()));
        // Named rewards remain distinguishable while editing a list containing several tables of the same type.
        String rewardTitle = typedEditorKind == QuestTypedEntryKind.REWARD
                ? quest.rewards().get(index).config().getOrDefault("title", "") : "";
        Component rowTitle = rewardTitle.isBlank() ? presentation.typeName() : Component.literal(rewardTitle);
        return new EditorEntryListPanel.Content(new EditorEntryRow.Content(icon, rowTitle,
                typedRowSummary(quest, index, known), known ? GraystonePalette.SECONDARY : 0xFFFFA070), presentation.icon().isPresent() ? ItemStack.EMPTY : presentation.stack());
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
                    presentation.displayedItem(context), presentation.icon(view));
        }
        RewardDefinition reward = quest.rewards().get(index);
        var view = ApiViews.reward(reward);
        var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        String snbt = presentation.itemSnbt(view);
        return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view),
                snbt.isBlank() ? ItemStack.EMPTY : item(reward.id(), snbt), presentation.icon(view));
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
        propertyScroll.snap(0);
        typedPropertySection.openExisting(entry, schema);
        formBaseline = localFormValues();
        setFocused(typedPropertySection.form().identityField("id"));
    }

    /** Starts a configured extension entry locally so required fields can be filled before server validation. */
    private void openNewTypedPropertyEditor(QuestBookSnapshot snapshot, ResourceLocation typeId) {
        ResourceLocation id = suggestId(snapshot.book(), typedEditorKind.idStem());
        typedPropertyMessage = null;
        ConfigEditorSchema schema = typedEditorKind == QuestTypedEntryKind.TASK
                ? ConfigEditorSchemas.forTask(new yourscraft.jasdewstarfield.brnquest.api.TaskView(
                        snapshot.book().id(), id, typeId, yourscraft.jasdewstarfield.brnquest.author.EntryCreationPolicy.taskConfig(
                                snapshot.book(), typedEditorQuestId, typeId, Map.of()), false))
                : ConfigEditorSchemas.forReward(new yourscraft.jasdewstarfield.brnquest.api.RewardView(
                        snapshot.book().id(), id, typeId, Map.of(), "manual", false));
        propertyScroll.snap(0);
        typedPropertySection.openNew(id, typeId, schema);
        if (typedEditorKind == QuestTypedEntryKind.REWARD) typedPropertySection.creationRewardDefaults(
                yourscraft.jasdewstarfield.brnquest.author.EntryCreationPolicy.rewardPolicy(snapshot.book(), typeId, schema.rawConfig()),
                snapshot.book().settings().rewardTeam());
        formBaseline = localFormValues();
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
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.identity"));
        rows.add(EditorPropertyPanel.readOnly(font, "screen.brnquest.editor.typed.property.type",
                typedTypeName(quest, typedPropertySection.originalId()), 68));
        rows.add(EditorPropertyPanel.text(font, typedPropertySection.form().identityField("id"),
                "screen.brnquest.editor.typed.property.id", 68,
                typedPropertySection.form().serverIssues().get("id"), enabled));
        typedPropertySection.hide();
        List<ConfigFieldDescriptor> descriptors = typedPropertySection.form().schema() == null
                ? List.of() : typedPropertySection.form().schema().fields();
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.configuration"));
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
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.completion"));
        if (typedEditorKind == QuestTypedEntryKind.TASK) {
            rows.add((g, x, y, w) -> renderTypedToggleRow(g, "screen.brnquest.editor.typed.property.optional",
                    typedPropertySection.optional(), x, y, w, mouseX, mouseY));
        } else {
            rows.add((g, x, y, w) -> {
                EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(x, y, w, 68);
                drawTypedLabel(g, "screen.brnquest.editor.typed.property.claim_policy", row.label(),
                        typedPropertySection.form().serverIssues().get("claim_policy"));
                renderEditorTextButton(g, row.field(),
                        authorValueLabel("claim", typedPropertySection.form().claim()).copy().append(" ▾"),
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
        captureTypedPropertyInteractionFrame(descriptors);
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
        drawTypedLabel(graphics, descriptor.labelKey().isBlank() ? typedConfigLabel(descriptor.key()) : descriptor.labelKey(), row.label(), issue);
        // Types may supply either a translated help key or existing literal help text.
        if (!descriptor.helpText().isBlank() && row.label().containsExclusive(mouseX, mouseY))
            hoveredDetailText = Component.translatable(descriptor.helpText());
        EditorTextField field = typedPropertySection.form().configField(index);
        if (ClientConfigEditors.find(typedPropertySection.typeId(), descriptor.key()).isPresent()) {
            renderEditorTextButton(graphics, row.field(), Component.translatable("screen.brnquest.config.edit").append(" · ").append(
                            yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText.compactValue(field.getValue())),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.INTEGER_VECTOR3) {
            var vector = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorVectorRow.layout(row.field(), descriptor.serverSource().isPresent());
            for (int axis = 0; axis < 3; axis++) {
                UiRect bounds = vector.axes().get(axis);
                graphics.drawString(font, new String[]{"X", "Y", "Z"}[axis], bounds.left(), bounds.top() + 5, GraystonePalette.SECONDARY, false);
                typedPropertySection.form().vectorField(index, axis).show(new UiRect(bounds.left() + 8, bounds.top(), bounds.right(), bounds.bottom()), !ClientEditorState.get().busy());
            }
            if (vector.current() != null) renderEditorTextButton(graphics, vector.current(), Component.literal("@"),
                    Component.translatable("screen.brnquest.field.current"), !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.serverSource().isPresent()) {
            renderEditorTextButton(graphics, row.field(), Component.literal(field.getValue() + " …"),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.BOOLEAN) {
            renderEditorTextButton(graphics, row.field(), booleanValue(field.getValue())
                            ? Component.translatable("options.on") : Component.translatable("options.off"),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ENUM) {
            renderEditorTextButton(graphics, row.field(), ConfigFieldLabels.value(descriptor, field.getValue()).copy().append(" ▾"),
                    null, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ITEM_STACK) {
            ItemStack stack = item(typedPropertySection.originalId(), field.getValue());
            Component select = Component.translatable("screen.brnquest.editor.typed.property.select_item");
            EditorIcon icon = stack.isEmpty()
                    ? QuestActionIcons.named("plus") : EditorIcon.item(stack);
            EditorButton.Definition definition = EditorButton.Definition.iconAndText(select, select, icon);
            renderEditorActionButton(graphics, row.field(), definition,
                    !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            // This icon opens an editor; it is not a recipe lookup target.
        } else if (descriptor.valueType() == ConfigValueType.ITEM_MATCHER) {
            ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parse(field.getValue()).result().orElse(null);
            List<ItemStack> candidates = choiceCandidates(spec);
            ItemStack stack = candidates.isEmpty() ? ItemStack.EMPTY : candidates.getFirst();
            Component edit = Component.translatable(
                    "screen.brnquest.editor.typed.property.edit_matcher", candidates.size());
            EditorIcon icon = stack.isEmpty()
                    ? QuestActionIcons.named("plus") : EditorIcon.item(stack);
            EditorButton.Definition definition = EditorButton.Definition.iconAndText(edit, edit, icon);
            renderEditorActionButton(graphics, row.field(), definition,
                    !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            // This icon opens an editor; it is not a recipe lookup target.
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
                !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
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
            // Built-in crafting progress uses a localized label instead of exposing its storage key.
            case "only_from_crafting" -> "screen.brnquest.editor.config.only_from_crafting";
            case "title" -> "screen.brnquest.editor.config.title";
            case "script_id" -> "screen.brnquest.editor.config.script_id";
            case "message_id" -> "screen.brnquest.editor.config.message_id";
            default -> key;
        };
    }

    private void captureTypedPropertyInteractionFrame(List<ConfigFieldDescriptor> descriptors) {
        List<QuestTypedPropertySection.FieldHit> fields = new ArrayList<>();
        for (int index = 0; index < Math.min(descriptors.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            UiRect bounds = visiblePropertyBounds(typedPropertyConfigBounds(index));
            if (bounds != null) fields.add(new QuestTypedPropertySection.FieldHit(index, descriptors.get(index), bounds));
        }
        int semanticsTop = typedPropertySemanticsTop();
        boolean raw = typedPropertySection.form().schema() != null
                && typedPropertySection.form().schema().rawFallback() && typedPropertyRawEditable();
        boolean task = typedEditorKind == QuestTypedEntryKind.TASK;
        typedPropertySection.captureInteractionFrame(new QuestTypedPropertySection.InteractionFrame(
                currentFrameIdentity(), typedEditorKind, questEditorCancelBounds(), questEditorSaveBounds(),
                task ? null : visiblePropertyBounds(typedPropertySemanticBounds(semanticsTop)), fields,
                raw ? visiblePropertyBounds(typedPropertySemanticBounds(semanticsTop - 44)) : null,
                task ? visiblePropertyBounds(typedPropertySemanticBounds(semanticsTop)) : null,
                task ? null : visiblePropertyBounds(typedPropertySemanticBounds(semanticsTop + 22))));
    }

    private boolean handleTypedPropertyEditorClick(double mouseX, double mouseY) {
        if (!propertyViewport().contains(mouseX, mouseY) && !questEditorCancelBounds().contains(mouseX, mouseY)
                && !questEditorSaveBounds().contains(mouseX, mouseY)) return true;
        Optional<QuestTypedPropertySection.Intent> result = typedPropertySection.click(
                currentFrameIdentity(), typedEditorKind, mouseX, mouseY);
        if (result.isPresent()) {
            QuestTypedPropertySection.Intent intent = result.orElseThrow();
            switch (intent.action()) {
                case CANCEL -> closeTypedPropertyEditor();
                case SUBMIT -> prepareTypedPropertyEdit();
                case CLAIM -> openEnumDropdown(intent.anchor(), REWARD_CLAIM_POLICIES,
                        typedPropertySection.form().identityField("claim")::setValue, value -> authorValueLabel("claim", value));
                case BOOLEAN -> {
                    EditorTextField field = typedPropertySection.form().configField(intent.fieldIndex());
                    field.setValue(Boolean.toString(!booleanValue(field.getValue())));
                }
                case ENUM -> {
                    var descriptor = typedPropertySection.form().schema().fields().get(intent.fieldIndex());
                    openEnumDropdown(intent.anchor(), intent.values(),
                            typedPropertySection.form().configField(intent.fieldIndex())::setValue,
                            value -> ConfigFieldLabels.value(descriptor, value));
                }
                case SERVER_CURRENT -> {
                    int index = intent.fieldIndex();
                    var descriptor = typedPropertySection.form().schema().fields().get(index);
                    String requestId = typedPropertySection.beginCurrentRequest(index);
                    yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork.send(new yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork.Query(
                            requestId, descriptor.serverSource().orElseThrow().toString(), "", typedPropertySection.form().configValue(index)));
                }
                case SERVER_FIELD -> {
                    var descriptor = typedPropertySection.form().schema().fields().get(intent.fieldIndex());
                    var field = typedPropertySection.form().configField(intent.fieldIndex());
                    openChildScreen(new ServerFieldScreen(this, descriptor.serverSource().orElseThrow().toString(), field.getValue(), value -> typedPropertySection.form().setConfigValue(intent.fieldIndex(), value), typedPropertySection.form().currentConfig(), false, descriptor.labelKey().isBlank() ? Component.literal(descriptor.key()) : Component.translatable(descriptor.labelKey())));
                }
                case ITEM -> openTypedPropertyItemSelector(intent.fieldIndex());
                case MATCHER -> openTypedPropertyMatcherEditor(intent.fieldIndex());
                case CUSTOM -> {
                    var descriptor = typedPropertySection.form().schema().fields().get(intent.fieldIndex());
                    openChildScreen(ClientConfigEditors.find(typedPropertySection.typeId(), descriptor.key()).orElseThrow()
                            .create(this, typedPropertySection.form().configValue(intent.fieldIndex()),
                                    value -> typedPropertySection.form().setConfigValue(intent.fieldIndex(), value)));
                }
                case RAW -> openTypedPropertyRawEditor();
                case OPTIONAL -> typedPropertySection.toggleOptional();
                case TEAM_REWARD -> typedPropertySection.toggleTeamReward();
            }
            return true;
        }
        super.mouseClicked(mouseX, mouseY, 0);
        return true;
    }

    /** Read-only field replies return to the still-open form; they never save a draft by themselves. */
    public void receiveServerField(yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork.Reply reply) {
        if (reply != null && reply.result() != null)
            typedPropertySection.receiveCurrent(reply.id(), reply.result().current());
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
        int requiredIndex = typedPropertySection.form().fieldIndex("required_entries");
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
        int requiredIndex = typedPropertySection.form().fieldIndex("required_entries");
        if (requiredIndex >= 0) {
            typedPropertySection.form().setConfigValue(requiredIndex, Integer.toString(spec.requiredEntries()));
        }
        itemCache.remove(typedPropertySection.originalId());
        typedPropertyMessage = null;
    }

    private void openNewItemChoiceEditor(java.util.function.Consumer<ItemChoiceMatcher.Spec> resultConsumer) {
        if (minecraft == null) return;
        openChildScreen(ItemChoiceScreen.createEditor(this, resultConsumer));
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
        openChildScreen(new AdminProgressScreen(this, active.book().id().toString(), active.revision(),
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
        openChildScreen(new AdminProgressScreen(this, active.book().id().toString(), active.revision(),
                questId.toString(), taskId == null ? "" : taskId.toString(), action));
    }

    private List<ItemStack> choiceCandidates(ItemChoiceMatcher.Spec spec) {
        if (spec == null || minecraft == null || minecraft.level == null) return List.of();
        return ItemChoiceMatcher.displayedCandidates(minecraft.level.registryAccess(), spec);
    }

    private void openItemChoiceScreen(ItemChoiceMatcher.Spec initial, boolean editing,
                                      java.util.function.Consumer<ItemChoiceMatcher.Spec> resultConsumer) {
        if (minecraft == null) return;
        openChildScreen(new ItemChoiceScreen(this, initial, editing, resultConsumer));
    }

    private void openGameplayItemChoiceScreen(QuestDefinition quest, TaskDefinition task,
                                               ItemChoiceMatcher.Spec spec, boolean candidatesOnly) {
        if (minecraft == null) return;
        boolean selectionRequired = !candidatesOnly && needsManualItemSelection(task, spec);
        boolean alreadySubmitted = ClientQuestState.get().taskProgress()
                .getOrDefault(task.id().toString(), 0L) >= 1;
        ItemChoiceOpenMode mode = itemChoiceOpenMode(candidatesOnly, gameplayAllowed(),
                selectionRequired, alreadySubmitted);
        if (mode == ItemChoiceOpenMode.VIEW_CANDIDATES) {
            openChildScreen(new ItemChoiceScreen(this, spec, false, ignored -> {}));
            return;
        }
        String submissionRevision = ClientQuestState.get().revision();
        openChildScreen(new ItemSubmissionScreen(this, spec, selectedSlots -> {
            // Never apply a selection captured from an older book to the newly displayed revision.
            if (!submissionRevision.equals(ClientQuestState.get().revision())) return;
            String taskId = task.id().toString();
            if (ClientQuestState.get().beginTaskSubmission(taskId)) {
                BrnQuestNetwork.completeTask(submissionRevision, quest.id().toString(),
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
        openChildScreen(new EditorRawConfigScreen(this, typedPropertySection.form().rawConfig(), config -> {
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
        return topToolbarHeight() + 20 + 110 + fields * 22 - (int) Math.round(propertyDrawnScroll)
                + (typedPropertySection.form().schema() != null
                && typedPropertySection.form().schema().rawFallback() ? 22 : 0);
    }

    private UiRect typedPropertyConfigBounds(int index) {
        int left = detailLeft() + 10;
        return EditorPropertyFormLayout.row(left, topToolbarHeight() + 20 + 88 + index * 22 - (int) Math.round(propertyDrawnScroll),
                detailsWidth() - 24, 68).field();
    }

    private UiRect typedPropertySemanticBounds(int top) {
        return EditorPropertyFormLayout.row(detailLeft() + 10, top, detailsWidth() - 24, 68).field();
    }

    private static boolean booleanValue(String value) {
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
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
        Map<String, String> config = typedPropertySection.form().currentConfig();
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
                if (fields.get(index).valueType() == ConfigValueType.INTEGER_VECTOR3) {
                    setFocused(typedPropertySection.form().vectorField(index, 0));
                } else if (fields.get(index).valueType() != ConfigValueType.ITEM_STACK) {
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
        typedPropertyMessage = EditorDiagnosticPresentation.operationError(editor.statusCode());
        typedPropertySection.form().serverIssues().clear();
        for (AuthoringNetwork.EditorDiagnosticWire diagnostic : editor.diagnostics()) {
            String path = diagnostic.path() == null ? "" : diagnostic.path();
            String fieldKey = path.startsWith("config.") ? path.substring("config.".length()) : path;
            if (!fieldKey.isBlank()) typedPropertySection.form().serverIssues().putIfAbsent(fieldKey,
                    EditorDiagnosticPresentation.diagnostic(publishDiagnostic(diagnostic)).getString());
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
        typedTypePicker.advanceIconChoices(bounds, new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight()),
                candidates.size(), candidates::get, currentMotionFrameSeconds, scrollSmoothSpeed());
        List<Component> tooltip = typedTypePicker.renderIconChoices(graphics, font,
                Component.translatable("screen.brnquest.editor.typed.type_heading"),
                type -> new EditorPickerList.Entry(typedTypeDisplayName(type),
                        Component.translatable(typePickerHint(typedTypePickerFrame, type)),
                        EditorPickerList.Tone.NORMAL, false, List.of(typedTypeDisplayName(type),
                                Component.translatable(typePickerHint(typedTypePickerFrame, type)), Component.translatable(
                                "screen.brnquest.editor.typed.type_id", type.toString()))),
                type -> typedEditorKind == QuestTypedEntryKind.TASK
                        ? ClientTaskPresentationRegistry.typeIcon(type) : ClientRewardPresentationRegistry.typeIcon(type),
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
        openChildScreen(new EditorItemSelectorScreen(this, selectionConsumer));
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
                    height - bottomToolbarHeight() - 4, GraystonePalette.PANEL);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.heading"),
                    left, topToolbarHeight() + 12, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.direction"),
                    left, topToolbarHeight() + 27, GraystonePalette.SECONDARY, false);
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
                graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), GraystonePalette.ROW);
                String title = dependency == null ? row.key().toString() : questTitle(dependency);
                String direction = Component.translatable("screen.brnquest.editor.dependency.edge", title, questTitle(quest)).getString();
                int textWidth = Math.max(0, rect.width() - 30);
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(direction, textWidth)),
                        rect.left() + 5, rect.top() + 4, dependency == null ? 0xFFFFA070 : 0xFFFFFFFF, false);
                Component secondary = dependency == null ? Component.translatable("screen.brnquest.editor.dependency.missing")
                        : Component.literal(chapterTitle(snapshot.book(), dependency.chapterId()));
                graphics.drawString(font, Component.literal(font.plainSubstrByWidth(secondary.getString(), textWidth)),
                        rect.left() + 5, rect.top() + 16, dependency == null ? 0xFFFFA070 : GraystonePalette.SECONDARY, false);
                Component label = Component.translatable("screen.brnquest.editor.dependency.remove");
                actions.add(new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(row.key(),
                        EditorButton.Definition.iconOnly(label, label, QuestActionIcons.named("close")),
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
                    (left + right) / 2, viewport.top() + 8, GraystonePalette.MUTED));
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
                    EditorButton.Definition.iconAndText(add, add, QuestActionIcons.named("plus")),
                    !ClientEditorState.get().busy(), EditorButton.Tone.PRIMARY, mouseX, mouseY);
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
                ? Component.translatable("screen.brnquest.editor.dependency.search_hint") : Component.literal(dependencyFilter);
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
        return QuestDependencyEditorModel.candidates(snapshot.book(), dependencyEditorQuestId, dependencyFilter,
                minecraft.getLanguageManager().getSelected());
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
        questFields.hide();
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.identity"));
        questFields.field("title").hide();
        questFields.field("subtitle").hide();
        questFields.field("description").hide();
        questFields.field("shape").hide();
        questLocalizedTextEditorBounds = null;
        questShapeDropdownBounds = null;
        questBehaviorEditorBounds = null;
        questHideLinesBounds = null;
        questIconRowLayout = null;
        rows.add(EditorPropertyPanel.text(font, questFields.field("id"),
                "screen.brnquest.editor.quest.id", 78, null, enabled));
        rows.add((g, x, y, w) -> renderQuestLocalizedTextEditorField(g, x, y, w,
                enabled, mouseX, mouseY));
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.appearance"));
        rows.add((g, x, y, w) -> renderQuestShapeEditorField(g, x, y, w, enabled, mouseX, mouseY));
        for (String key : List.of("size", "icon_scale", "min_width")) {
            rows.add(EditorPropertyPanel.text(font, questFields.field(key),
                    "screen.brnquest.editor.quest." + key, 78, null, enabled));
        }
        rows.add((g, x, y, w) -> renderQuestIconEditorField(g, x, y, w, mouseX, mouseY));
        rows.add(this::renderQuestPositionEditorField);
        rows.add((g,x,y,w) -> {
            var row = EditorPropertyFormLayout.row(x,y,w,78);
            EditorPropertyRow.label(g,font,Component.translatable("screen.brnquest.quest.hide_dependency_lines"),row.label(),null);
            questHideLinesBounds = row.field();
            var label = Component.translatable(questHideDependencyLines == null ? "screen.brnquest.dependency_lines.inherit"
                    : questHideDependencyLines ? "screen.brnquest.dependency_lines.hide" : "screen.brnquest.dependency_lines.show");
            renderEditorTextButton(g,row.field(),label,Component.translatable("screen.brnquest.dependency_lines.help"),enabled,EditorButton.Tone.NEUTRAL,mouseX,mouseY);
        });
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.completion"));
        rows.add((g, x, y, w) -> renderQuestBehaviorEditorField(g, x, y, w, enabled, mouseX, mouseY));
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
                authorValueLabel("shape", questFields.field("shape").getValue()).copy().append(" ▾"), null,
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

    /** Keep native fields in input/focus routing while drawing them ourselves inside the property viewport. */
    private void registerPropertyField(EditorTextField field) {
        propertyNativeFields.add(field);
        addWidget(field);
    }

    /** Pointer targets use the visible intersection, while row rendering retains its original geometry. */
    private UiRect visiblePropertyBounds(UiRect bounds) {
        var viewport = propertyViewport();
        var visible = bounds.intersection(viewport);
        return visible.width() > 0 && visible.height() > 0 ? visible : null;
    }

    private UiRect propertyViewport() {
        return new UiRect(detailLeft() + 10, topToolbarHeight() + 20,
                detailLeft() + detailsWidth() - 14, Math.max(topToolbarHeight() + 20, questEditorCancelBounds().top() - 18));
    }

    /** Rows and native fields share a scroll viewport; footer actions always remain outside it. */
    private void renderPropertyPanel(GuiGraphics graphics, Component heading, int headingColor,
                                     List<EditorPropertyPanel.RowContent> rows, Component done,
                                     EditorButton.Tone tone, int mouseX, int mouseY) {
        var viewport = propertyViewport();
        propertyRowCount = rows.size();
        var form = new EditorPropertyPanel.Layout(new UiRect(detailLeft(), topToolbarHeight(), width,
                height - bottomToolbarHeight() - 4), viewport.left(), viewport.width(),
                topToolbarHeight() + 7, viewport.top(), 22);
        EditorPropertyPanel.render(graphics, font, form, heading, headingColor, List.of(),
                new EditorPropertyPanel.Footer(questEditorCancelBounds(), questEditorSaveBounds(), done,
                        !ClientEditorState.get().busy(), tone),
                (g, bounds, label, enabled, buttonTone) -> renderEditorTextButton(g, bounds, label, null, enabled, buttonTone, mouseX, mouseY));
        propertyDrawnScroll = propertyScroll.frameAndRender(graphics, viewport.right() + 2, viewport.top(), viewport.bottom(),
                rows.size() * 22, viewport.height(), currentMotionFrameSeconds, scrollSmoothSpeed());
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        formButtons.viewport(viewport.translated(detailsDrawerOffsetX(), 0), detailsDrawerOffsetX());
        try {
            for (int index = 0; index < rows.size(); index++) {
                int y = viewport.top() + index * 22 - (int)Math.round(propertyDrawnScroll);
                // Keep edge rows; the scissor clips their hidden pixels instead of removing the whole control.
                if (y + 18 <= viewport.top() || y >= viewport.bottom()) continue;
                rows.get(index).render(graphics, viewport.left(), y, viewport.width());
            }
        } finally { graphics.disableScissor(); }
        graphics.drawString(font, Component.translatable(localFormChanged() ? "screen.brnquest.editor.scope.form"
                        : "screen.brnquest.editor.scope.unchanged"), viewport.left(),
                questEditorCancelBounds().top() - 12, GraystonePalette.SECONDARY, false);
    }

    private static Component authorValueLabel(String category, String value) {
        String key = "screen.brnquest.editor.value." + category + "." + value;
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(value);
    }

    /** Shows an ordinary item ID while retaining the native SNBT representation behind the form. */
    private void renderQuestIconEditorField(GuiGraphics graphics, int left, int top, int width,
                                            int mouseX, int mouseY) {
        questIconRowLayout = renderTextureSelector(graphics, left, top, width, questFields.field("icon"),
                questEditorIconMode, mouseX, mouseY);
    }

    /** Chapter and quest forms share decorative picker icons and their action tooltips. */
    private QuestIconEditorRow.Layout renderTextureSelector(GuiGraphics graphics, int left, int top, int width,
            EditorTextField input, IconEditorMode mode, int mouseX, int mouseY) {
        return EditorTextureSelector.render(graphics, font, left, top, width, input, mode == IconEditorMode.TEXTURE,
                !ClientEditorState.get().busy(),
                (g, bounds, label, enabled, tone) -> renderEditorTextButton(g, bounds, label, null, enabled, tone, mouseX, mouseY),
                (g, bounds, definition, enabled, tone) -> renderEditorActionButton(g, bounds, definition, enabled, tone, mouseX, mouseY));
    }

    /** Keeps exact coordinate entry available even though pointer dragging snaps to the visible grid. */
    private void renderQuestPositionEditorField(GuiGraphics graphics, int left, int top, int width) {
        int labelWidth = 48;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, labelWidth);
        String label = font.plainSubstrByWidth(
                Component.translatable("screen.brnquest.editor.quest.position").getString(),
                row.label().width() - 4);
        graphics.drawString(font, Component.literal(label), row.label().left(), row.label().top() + 5,
                GraystonePalette.SECONDARY, false);

        int axisLabelWidth = 10;
        int gap = 4;
        int inputWidth = Math.max(16, (row.field().width() - axisLabelWidth * 2 - gap) / 2);
        int xLabelLeft = row.field().left();
        int xInputLeft = xLabelLeft + axisLabelWidth;
        int yLabelLeft = xInputLeft + inputWidth + gap;
        int yInputLeft = yLabelLeft + axisLabelWidth;
        graphics.drawString(font, "X", xLabelLeft + 1, row.field().top() + 5, GraystonePalette.SECONDARY, false);
        graphics.drawString(font, "Y", yLabelLeft + 1, row.field().top() + 5, GraystonePalette.SECONDARY, false);
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
        propertyScroll.snap(0);
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
        questHideDependencyLines = quest.appearance().hideDependencyLines();
        formBaseline = localFormValues();
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
        rememberPendingEditUi();
        ResourceLocation oldId = questEditorQuestId;
        String iconValue = questFields.field("icon").getValue().strip();
        boolean preserveIcon = questEditorIconMode == questEditorOriginalIconMode && iconValue.equals(questEditorOriginalIconItemId);
        String previewIcon = preserveIcon ? currentQuest.icon() : iconValue.isBlank() ? ""
                : questEditorIconMode == IconEditorMode.TEXTURE ? yourscraft.jasdewstarfield.brnquest.data.QuestIconValue.texture(ResourceLocation.parse(iconValue))
                : "{id:\"" + iconValue + "\",count:1}";
        var replacement = new QuestDefinition(currentQuest.bookId(), replacementId, currentQuest.chapterId(),
                currentQuest.title(), currentQuest.subtitle(), currentQuest.description(), currentQuest.descriptionFormat(), previewIcon,
                Double.parseDouble(questFields.field("x").getValue().strip()), Double.parseDouble(questFields.field("y").getValue().strip()),
                currentQuest.dependencies(), currentQuest.tasks(), currentQuest.rewards(), currentQuest.legacyId(),
                new yourscraft.jasdewstarfield.brnquest.data.QuestAppearance(questFields.field("shape").getValue().strip(),
                        Double.parseDouble(questFields.field("size").getValue().strip()), Double.parseDouble(questFields.field("icon_scale").getValue().strip()),
                        Double.parseDouble(questFields.field("min_width").getValue().strip()), questHideDependencyLines),
                questEditorBehavior, currentQuest.extensions());
        editor.previewEdit(book -> ClientEditPreview.questProperties(book, oldId, replacement));
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
        questHideLinesBounds = null;
    }

    private Map<String, String> behaviorConfig(QuestBehavior value) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("hide_dependency_lines", java.util.Objects.toString(questHideDependencyLines, "default"));
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
        EditorConfirmDialog.render(graphics, font, layout(), formButtons,
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
        ClientEditorState editor = ClientEditorState.get();
        return QuestEditorChrome.layout(layout(), editor.live(), editor.hasLease(), editor.allowed()).title();
    }

    /** Copies protocol records into an immutable UI model at the screen integration boundary. */
    private static EditorPublishReviewModel publishReviewModel(AuthoringNetwork.PublishReviewWire review) {
        return new EditorPublishReviewModel(review.publishAllowed(), review.fromRevision(), review.targetRevision(),
                review.diagnosticCount(), review.changeCount(), review.truncated(),
                review.diagnostics().stream().map(QuestScreen::publishDiagnostic).toList(),
                review.changes().stream().map(change -> new EditorPublishReviewModel.Change(change.kind(),
                        change.objectKind(), change.objectId(), change.path(), change.before(), change.after())).toList());
    }

    private static EditorPublishReviewModel.Diagnostic publishDiagnostic(
            AuthoringNetwork.EditorDiagnosticWire diagnostic) {
        return new EditorPublishReviewModel.Diagnostic(diagnostic.severity(), diagnostic.code(),
                diagnostic.objectId(), diagnostic.path(), diagnostic.message());
    }

    private void renderPublishConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        publishReviewSection.presentation(this::reviewPresentation);
        QuestPublishReviewSection.RenderResult result = publishReviewSection.render(
                graphics, font, layout(), currentMotionFrameSeconds, scrollSmoothSpeed(), mouseX, mouseY);
        if (!result.tooltip().isEmpty()) graphics.renderComponentTooltip(font, result.tooltip(), mouseX, mouseY);
        graphics.flush();
        graphics.pose().popPose();
    }

    private boolean handlePublishConfirmationClick(double mouseX, double mouseY, int button) {
        QuestPublishReviewSection.ClickResult result = publishReviewSection.click(layout(), mouseX, mouseY, button);
        if (result.intent() != null) handlePublishReviewIntent(result.intent());
        return result.consumed();
    }

    /** Dispatches review intents while the extracted overlay remains protocol-agnostic. */
    private void handlePublishReviewIntent(QuestPublishReviewSection.Intent intent) {
        switch (intent.action()) {
            case CANCEL -> closeActiveEditorOverlay();
            case DETAILS -> {
                var lines = publishReviewSection.detailLines();
                openChildScreen(new ResolvedOptionsScreen(this, () -> lines,
                        Component.translatable("screen.brnquest.editor.publish.review.details")));
            }
            case JUMP_TO_OBJECT -> {
                if (intent.objectId() != null && jumpToEditorObject(intent.objectId())) closeActiveEditorOverlay();
            }
            case CONFIRM -> {
                String reviewedRevision = intent.reviewedRevision();
                closeActiveEditorOverlay();
                ClientEditorState editor = ClientEditorState.get();
                // The preview is revision-bound; never confirm a different draft with stale review data.
                if (reviewedRevision.equals(editor.draftRevision())) {
                    editor.beginPublish().ifPresent(request -> AuthoringNetwork.publishAndApply(
                            request.sessionId(), editor.bookId(), request.draftRevision()));
                }
            }
        }
    }

    private QuestBookSnapshot reviewPresentationSnapshot;
    private final Map<ResourceLocation, QuestPublishReviewSection.Presentation> reviewPresentations = new java.util.HashMap<>();

    /** Resolve names through the same extension presentations as the author list; IDs stay in details. */
    private QuestPublishReviewSection.Presentation reviewPresentation(ResourceLocation id) {
        var snapshot = displaySnapshot();
        if (id == null || snapshot == null) return null;
        if (snapshot != reviewPresentationSnapshot) { reviewPresentations.clear(); reviewPresentationSnapshot = snapshot; }
        return reviewPresentations.computeIfAbsent(id, key -> resolveReviewPresentation(snapshot.book(), key));
    }

    private QuestPublishReviewSection.Presentation resolveReviewPresentation(QuestBookDefinition book, ResourceLocation id) {
        for (var chapter : book.chapters()) if (chapter.id().equals(id))
            return new QuestPublishReviewSection.Presentation(Component.literal(chapter.title()), reviewIcon(chapter.id(), chapter.icon()));
        for (var group : book.chapterGroups()) if (group.id().equals(id))
            return new QuestPublishReviewSection.Presentation(Component.literal(group.title()), null);
        for (var quest : book.quests()) {
            if (quest.id().equals(id)) return new QuestPublishReviewSection.Presentation(Component.literal(questTitle(quest)), reviewIcon(quest.id(), quest.icon()));
            for (var task : quest.tasks()) if (task.id().equals(id)) {
                var view = ApiViews.task(task);
                return new QuestPublishReviewSection.Presentation(task.config().getOrDefault("title", "").isBlank()
                        ? ClientTaskPresentationRegistry.get(task.typeId()).typeName(view) : Component.literal(task.config().get("title")),
                        ClientTaskPresentationRegistry.typeIcon(task.typeId()));
            }
            for (var reward : quest.rewards()) if (reward.id().equals(id)) {
                var details = RewardEntryDetails.resolve(minecraft, ApiViews.reward(reward));
                return new QuestPublishReviewSection.Presentation(details.summary(), details.icon());
            }
        }
        return null;
    }

    /** Decorative review previews deliberately expose no recipe target or claim action. */
    private EditorIcon reviewIcon(ResourceLocation id, String value) {
        if (value.isBlank()) return null;
        return new EditorIcon() {
            public int width(net.minecraft.client.gui.Font font) { return 16; }
            public void render(GuiGraphics graphics, net.minecraft.client.gui.Font font, UiRect bounds, int color) {
                renderChapterIcon(graphics, id, value, bounds);
            }
        };
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
            navigationPanel.resetScroll();
        }
        return true;
    }

    private void requestDiscardConfirmation(ResourceLocation switchTarget, boolean closeScreen) {
        closeQuestEditingPanels();
        closeActiveEditorOverlay();
        discardSwitchTarget = switchTarget;
        discardClosesScreen = closeScreen;
        editorOverlays.show(EditorOverlayHost.Kind.DISCARD_CONFIRMATION);
    }

    private void renderDiscardConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        EditorConfirmDialog.render(graphics, font, layout(), formButtons,
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
            // Closing the UI remembers editing; explicitly leaving the mode remembers browsing.
            closingEditingPreference = target == null ? closeScreen : null;
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
        if (ClientQuestState.get().questCompletionPending(quest.id().toString())) return false;
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
            String chapterTitle = chapter == null ? "" : chapterTitle(snapshot.book(), chapter.id());
            if (chapterTitle.isBlank()) chapterTitle = Component.translatable("screen.brnquest.chapter.untitled").getString();
            String dependencyTitle = questTitle(dependency);
            lines.add(Component.translatable("screen.brnquest.dependency.entry", chapterTitle, dependencyTitle));
        }
        if (quest.dependencies().isEmpty()) lines.add(Component.translatable("screen.brnquest.dependencies.none"));
        return lines;
    }

    private boolean isCompleted(QuestStatus status) {
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED;
    }

    /** Keeps the claimed check above the item while leaving its vanilla count corner unobstructed. */


    private int statusColor(QuestStatus status) {
        return switch (status) {
            case COMPLETED, REWARD_CLAIMED -> 0xFF72D88D;
            case AVAILABLE, ACTIVE -> 0xFFE4B75A;
            default -> GraystonePalette.MUTED;
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

    private void openChildScreen(net.minecraft.client.gui.screens.Screen child) {
        // All editor child windows suspend this parent; unsaved form fields and its lease stay alive.
        java.util.Objects.requireNonNull(child, "child");
        childLifecycle.openChild(() -> minecraft.setScreen(child));
    }

    @Override
    public void prepareForTransientChildScreen() {
        childLifecycle.prepareChild();
        // The matching ScreenEvent.Opening is raised before removed(), allowing a recipe viewer
        // to suspend this exact editor object without closing its server-authoritative lease.
    }

    private UiRect detailRecipeLookupViewport() {
        return new UiRect(detailLeft() + 1, detailContentTop(), width - 10,
                detailContentBottom());
    }

    /** Draws the one winning hover surface at the final z-order, above details and navigation chrome. */
    private void renderDeferredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Only advertise shortcuts when the same public hit test can actually handle them.
        Component lookupHint = recipeLookupTargetAt(mouseX, mouseY).isEmpty()
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
                graphics.renderTooltip(font, font.split(hoveredDetailText, Math.min(320, width - 20)), mouseX, mouseY);
            } else {
                var lines = new java.util.ArrayList<>(font.split(hoveredDetailText, Math.min(320, width - 20)));
                lines.addAll(font.split(lookupHint, Math.min(320, width - 20)));
                graphics.renderTooltip(font, lines, mouseX, mouseY);
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
        return BookText.quest(snapshot.book(), quest,
                minecraft.getLanguageManager().getSelected(), field, fallback);
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
        return BookText.structureTitle(snapshot.book(), kind, id,
                minecraft.getLanguageManager().getSelected(), fallback);
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


    private int graphCoordinate(double coordinate) {
        return (int) Math.round(coordinate * QuestViewportMath.GRID_SCALE);
    }

    private int nodeGraphX(QuestDefinition quest) {
        DraftBookEditor.Position preview = canvasController.preview(quest.id());
        return graphCoordinate(preview == null ? quest.x() : preview.x());
    }

    private int nodeGraphY(QuestDefinition quest) {
        DraftBookEditor.Position preview = canvasController.preview(quest.id());
        return graphCoordinate(preview == null ? quest.y() : preview.y());
    }

    private void selectOnly(ResourceLocation questId) {
        canvasController.selectOnly(questId);
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

    private void reconcileDragPreview() {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return;
        canvasController.reconcile(id -> {
            QuestDefinition quest = snapshot.quests().get(id);
            return quest == null ? null : new DraftBookEditor.Position(quest.x(), quest.y());
        }, id -> currentChapter() == null ? null : currentChapter().canvasScene().decorations().stream()
                .filter(d -> d.id().equals(id)).map(d -> new DraftBookEditor.Position(d.x(), d.y())).findFirst().orElse(null),
                ClientEditorState.get().mode() == ClientEditorState.Mode.ERROR);
    }

    private QuestCanvasController.InputModel canvasInputModel(QuestBookSnapshot snapshot, ChapterDefinition chapter) {
        Map<ResourceLocation, DraftBookEditor.Position> positions = new LinkedHashMap<>();
        (chapter == null ? List.<QuestDefinition>of() : chapter.quests()).forEach(quest -> positions.put(quest.id(),
                new DraftBookEditor.Position(quest.x(), quest.y())));
        return new QuestCanvasController.InputModel(currentFrameIdentity(), chapter == null ? null : chapter.id(),
                ClientEditorState.get().editing(), BrnQuestClientConfig.VALUES.snapToGrid.get(), positions,
                chapter == null ? List.of() : chapter.canvasScene().decorations());
    }

    /** Re-resolves stable canvas IDs against the live snapshot before any network mutation. */
    private void handleCanvasIntent(QuestCanvasController.Intent intent, QuestBookSnapshot snapshot,
                                    ChapterDefinition chapter) {
        if (intent == null || snapshot == null) return;
        switch (intent.action()) {
            case OPEN_DETAILS -> {
                QuestDefinition quest = snapshot.quests().get(intent.targetId());
                if (quest == null || !questVisible(quest)) return;
                if (ClientEditorState.get().editing()) {
                    openEditorQuestDetails(quest.id());
                } else {
                    ClientQuestState.get().selected(quest.id());
                    closeQuestEditingPanels();
                    openDetailsPanel();
                    detailsPanel.scroll().snap(0);
                    BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), quest.id().toString());
                }
            }
            case OPEN_NODE_CONTEXT -> {
                QuestDefinition quest = snapshot.quests().get(intent.targetId());
                if (ClientEditorState.get().editing() && quest != null) {
                    boolean multiple = canvasController.selectionCount() > 1;
                    openEditContext(multiple ? ContextKind.SELECTION : ContextKind.NODE, quest.id(), intent.pointerX(), intent.pointerY(),
                            quest.x(), quest.y());
                    contextSelection = multiple ? canvasController.allSelection() : java.util.Set.of();
                }
            }
            case OPEN_CANVAS_CONTEXT -> {
                if (ClientEditorState.get().editing() && chapter != null) {
                    boolean multiple = canvasController.selectionCount() > 1;
                    openEditContext(multiple ? ContextKind.SELECTION : ContextKind.CANVAS, null, intent.pointerX(), intent.pointerY(),
                            intent.questX(), intent.questY());
                    contextSelection = multiple ? canvasController.allSelection() : java.util.Set.of();
                }
            }
            case OPEN_DECORATION_CONTEXT -> {
                if (ClientEditorState.get().editing() && chapter != null) {
                    openEditContext(canvasController.selectionCount() > 1 ? ContextKind.SELECTION : ContextKind.DECORATION,
                            intent.targetId(), intent.pointerX(), intent.pointerY(), intent.questX(), intent.questY());
                    contextSelection = canvasController.allSelection();
                }
            }
            case MOVE_CANVAS_SELECTION -> {
                if (!ClientEditorState.get().editing() || chapter == null || intent.positions().isEmpty()) {
                    canvasController.clearPreview(); return;
                }
                var positions = intent.positions().entrySet().stream().map(e -> new AuthoringNetwork.PositionWire(
                        e.getKey().toString(), e.getValue().x(), e.getValue().y())).toList();
                if (!sendMutation("MOVE_CANVAS_SELECTION", chapter.id(), null, null, "", 0, 0, 0, positions)) canvasController.clearPreview();
            }
            case MOVE_QUESTS -> {
                if (!ClientEditorState.get().editing() || intent.positions().isEmpty()
                        || intent.positions().keySet().stream().anyMatch(id -> !snapshot.quests().containsKey(id))) {
                    canvasController.clearPreview();
                    return;
                }
                List<AuthoringNetwork.PositionWire> positions = intent.positions().entrySet().stream()
                        .map(entry -> new AuthoringNetwork.PositionWire(entry.getKey().toString(),
                                entry.getValue().x(), entry.getValue().y())).toList();
                if (!sendMutation("MOVE_QUESTS", null, null, null, "", 0, 0, 0, positions)) {
                    canvasController.clearPreview();
                }
            }
        }
    }

    private ChapterDefinition currentChapter() {
        QuestBookSnapshot snapshot = displaySnapshot();
        if (snapshot == null) return null;
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
        return chapters.isEmpty() ? null : chapters.get(Math.min(chapterIndex, chapters.size() - 1));
    }

    private int contentCenterY() {
        return backgroundPreview != null ? height / 2 : layout().contentCenterY();
    }

    private boolean isContentY(double y) {
        return layout().isContentY(y);
    }

    private int detailLeft() {
        return layout().detailLeft();
    }

    private int canvasLeft() {
        return backgroundPreview != null ? 0 : layout().canvasLeft(navigationDrawerMotion.current());
    }

    private int canvasRight() {
        return backgroundPreview != null ? width : layout().canvasRight(detailsDrawerMotion.current());
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
        return x >= 0 && x < navigationHandleLeft();
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
        return backgroundPreview != null ? 0 : layout().topToolbarHeight();
    }

    private int bottomToolbarHeight() {
        return backgroundPreview != null ? 0 : layout().bottomToolbarHeight();
    }

    private int detailContentTop() {
        return topToolbarHeight() + 8;
    }

    /** The scrolling detail body stops above editor tabs instead of rendering behind them. */
    private int detailContentBottom() {
        int ordinaryBottom = height - bottomToolbarHeight() - 8;
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
        focusChapter(chapters.get(Math.min(chapterIndex, chapters.size() - 1)));
    }

    /** Chapter entry is a one-shot camera move, independent of selection focus and child-screen lifecycles. */
    private void focusChapter(ChapterDefinition chapter) {
        if (chapter.autofocusQuestId() == null) return;
        chapter.quests().stream().filter(q -> q.id().equals(chapter.autofocusQuestId())).findFirst().ifPresent(q -> {
            var currentLayout = layout();
            double centerX = (currentLayout.canvasLeft(navigationCollapsed ? 0.0 : 1.0)
                    + currentLayout.canvasRight(0.0)) / 2.0;
            canvasController.focusChapterPoint(nodeGraphX(q), nodeGraphY(q), centerX, screenOriginX());
        });
    }

    private ResourceLocation currentChapterId() {
        var snapshot = displaySnapshot();
        if (snapshot == null) return rememberedChapterId;
        List<ChapterDefinition> chapters = QuestPresentation.orderedChapters(snapshot.book());
        if (chapters.isEmpty()) return rememberedChapterId;
        return chapters.get(Math.min(chapterIndex, chapters.size() - 1)).id();
    }

    private String chapterTitle(QuestBookDefinition book, ResourceLocation chapterId) {
        return book.chapters().stream().filter(chapter -> chapter.id().equals(chapterId))
                .map(chapter -> BookText.structureTitle(book, "chapter",
                        chapter.id(), minecraft.getLanguageManager().getSelected(), chapter.title())).findFirst().orElse("");
    }

    /** Hover resolves against the same live snapshot and visibility rules used by click navigation. */
    private Component documentLinkHint(RichDocument.LinkDestination destination) {
        if (destination instanceof RichDocument.ExternalLink external)
            return Component.literal(external.uri().toString());
        if (!(destination instanceof RichDocument.QuestLink questLink))
            return Component.translatable("screen.brnquest.markdown.quest_link.unavailable");
        ResourceLocation targetId = ResourceLocation.tryParse(questLink.questId());
        QuestBookSnapshot snapshot = displaySnapshot();
        QuestDefinition target = snapshot == null || targetId == null ? null : snapshot.quests().get(targetId);
        if (target == null || !questVisible(target))
            return Component.translatable("screen.brnquest.markdown.quest_link.unavailable");
        boolean hideText = !ClientEditorState.get().editing() && target.behavior().hideTextUntilComplete()
                && !isCompleted(status(target));
        String title = hideText ? "???" : questTitle(target);
        return Component.translatable("screen.brnquest.markdown.quest_link.open", title,
                chapterTitle(snapshot.book(), target.chapterId()));
    }

    private QuestBookSnapshot displaySnapshot() {
        if (renderFrame != null) return renderFrame.snapshot();
        return ClientEditorState.get().displayDraft().orElseGet(() -> ClientQuestState.get().book().orElse(null));
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
        canvasController.resetCamera(remembered.centerX(), remembered.centerY(), remembered.zoom());
        rememberedChapterId = remembered.chapterId();
        navigationCollapsed = remembered.navigationCollapsed();
        navigationPanel.restoreFoldedGroups(bookId, remembered.foldedGroups());
        navigationDrawerMotion.snap(navigationCollapsed ? 0.0 : 1.0);
        rememberedChapterResolved = false;
        navigationPanel.resetScroll();
        detailsPanel.scroll().snap(0);
        detailsOpen = false;
        detailsDrawerMotion.snap(0.0);
        editorSelectedQuest = null;
    }

    private void saveViewport(ResourceLocation chapterId) {
        finishZoomMotion();
        QuestCanvasController.StoredViewport viewport = canvasController.storedViewport();
        QuestScreenSessionState.save(serverContextId, viewportBookId, chapterId,
                viewport.centerX(), viewport.centerY(), viewport.zoom(), navigationCollapsed, navigationPanel.foldedGroups());
    }

    /** Advances both drawer reveal boundaries from the same frame time used by scrolling and zoom. */
    private void advanceDrawerMotion(double elapsedSeconds) {
        navigationDrawerMotion.target(navigationCollapsed ? 0.0 : 1.0);
        detailsDrawerMotion.target(detailsOpen ? 1.0 : 0.0);
        navigationDrawerMotion.advanceFrame(elapsedSeconds, drawerSmoothSpeed());
        detailsDrawerMotion.advanceFrame(elapsedSeconds, drawerSmoothSpeed());
    }

    /** Advances zoom and one-shot focus from the same real render-frame duration. */
    private void advanceCanvasMotion(double elapsedSeconds) {
        QuestDefinition selected = detailsOpen ? selectedQuest() : null;
        ResourceLocation id = selected == null ? null : selected.id();
        QuestScreenLayout finalLayout = layout();
        double targetX = (finalLayout.canvasLeft(navigationCollapsed ? 0.0 : 1.0)
                + finalLayout.canvasRight(detailsOpen ? 1.0 : 0.0)) / 2.0;
        canvasController.advanceFrame(elapsedSeconds, zoomSmoothSpeed(), focusSmoothSpeed(),
                new QuestCanvasController.FocusModel(autoFocusSelectedQuest(), detailsOpen, id,
                        selected == null ? null : (double) nodeGraphX(selected),
                        selected == null ? null : (double) nodeGraphY(selected),
                        targetX, finalLayout.contentCenterY(), screenOriginX(), finalLayout.contentCenterY()));
    }

    /** Auto-collapses navigation only for a genuine closed-to-open details transition. */
    private void openDetailsPanel() {
        if (!detailsOpen && BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.autoCollapseNavigation)) navigationCollapsed = true;
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

    /** Persists the player's requested zoom rather than an arbitrary mid-animation frame. */
    private void finishZoomMotion() {
        canvasController.finishZoomMotion(screenOriginX(), height / 2.0);
    }

    private String currentServerContext() {
        if (minecraft == null) return "unknown";
        var remote = minecraft.getCurrentServer();
        if (remote != null) return "remote:" + remote.ip;
        var integrated = minecraft.getSingleplayerServer();
        return integrated == null ? "unknown" : "integrated:" + integrated.getWorldData().getLevelName();
    }

    private int navigationListBottom() {
        return height - bottomToolbarHeight() - (ClientEditorState.get().editing() ? 20 : 0);
    }

    private int detailViewportHeight() {
        return Math.max(1, detailContentBottom() - detailContentTop());
    }

    enum ItemChoiceOpenMode {
        VIEW_CANDIDATES,
        SELECT_INVENTORY
    }


    private record TypedRowPresentation(Component typeName, String symbol, ItemStack stack, java.util.Optional<EditorIcon> icon) {}

    private enum IconEditorMode {
        ITEM("screen.brnquest.editor.quest.icon_mode.item"),
        TEXTURE("screen.brnquest.editor.quest.icon_mode.texture");

        private final String translationKey;

        IconEditorMode(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private enum ContextKind { NONE, CANVAS, NODE, DECORATION, SELECTION, GROUP, CHAPTER, TYPED_ENTRY, DEPENDENCY_ENTRY }

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
