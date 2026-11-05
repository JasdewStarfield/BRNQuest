package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextRenderer;

import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorLocalizedText;
import yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPopupMenu;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
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
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.DisplayOverrides;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Quest-book UI with grouped navigation, a scalable directed graph, and intent-only details. */
public final class QuestScreen extends Screen implements RecipeLookupSource, TransientChildScreenParent {
    private static final int EDITOR_CATALOG_ROW_HEIGHT = 30;
    private static final int EDITOR_CATALOG_SEARCH_HEIGHT = 18;
    private static final int EDITOR_CATALOG_ACTION_HEIGHT = 24;
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
    // Chapter icon changes stay local until the dialog is applied. Child selectors only update this value.

    private EditorTextField quickTextField;
    private EditorLocalizedText quickLocalizedText;
    private record PendingLocalizedQuestText(ResourceLocation questId, EditorLocalizedQuestTextScreen.Value value) {}
    private PendingLocalizedQuestText pendingLocalizedQuestText;
    private QuickTextKind quickTextKind = QuickTextKind.NONE;
    private ResourceLocation quickTextQuestId;
    private Component quickTextIssue;
    private Component clipboardMessage;
    private long clipboardMessageUntil;
    private final QuestEditorChrome editorChrome = new QuestEditorChrome();
    private final QuestTypedPropertySection typedPropertySection =
            new QuestTypedPropertySection(MAX_TYPED_CONFIG_FIELDS);
    private UiRect enumDropdownAnchor;
    private List<String> enumDropdownValues = List.of();
    private java.util.function.Function<String, Component> enumDropdownLabel = Component::literal;
    private Consumer<String> enumDropdownConsumer;
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
    private DeleteKind deleteKind = DeleteKind.NONE;
    private ResourceLocation deleteTarget;
    private String deleteImpact = "";
    private final ContentAwareCache<ResourceLocation, String, ItemStack> itemCache = new ContentAwareCache<>();
    private ItemStack hoveredDetailStack = ItemStack.EMPTY;
    private Component hoveredDetailText;
    private List<Component> hoveredComponentTooltip = List.of();
    // Legacy form dispatch shares one rendered-button feedback adapter.
    private final EditorButtonInput formButtons = new EditorButtonInput();
    private QuestScreenFrameIdentity formButtonFrame;
    private EditorOverlayHost.Kind formButtonOverlay;
    private RecipeLookupTarget hoveredRecipeLookupTarget;
    private ResourceLocation recoveryCopyBookId;

    private final QuestPropertiesEditor questProperties = new QuestPropertiesEditor(new QuestPropertiesEditor.Host() {
        public QuestBookSnapshot snapshot() { return displaySnapshot(); }
        public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
        public EditorButtonInput buttons() { return formButtons; }
        public void focus(EditorTextField field) { setFocused(field); }
        public void openItems(Consumer<ItemStack> result) { openEditorItemSelector(result); }
        public void openTexture(Consumer<String> result) { openChildScreen(new EditorTextureBrowserScreen(QuestScreen.this, result)); }
        public void openLocalized(QuestDefinition quest) { openLocalizedQuestTextEditor(quest); }
        public void openRegistered(Component title, Map<String, String> values, List<QuestPropertySchema.Property> schema,
                                   Consumer<Map<String, String>> result) {
            openChildScreen(new EditorRegisteredPropertiesScreen(QuestScreen.this, title, values, schema, result));
        }
        public void dropdown(UiRect anchor, List<String> values, Consumer<String> result, java.util.function.Function<String, Component> labels) {
            openEnumDropdown(anchor, values, result, labels);
        }
        public void rename(boolean open) {
            if (open) { closeActiveEditorOverlay(); editorOverlays.show(EditorOverlayHost.Kind.QUEST_RENAME_CONFIRMATION); }
            else editorOverlays.close();
        }
        public void resetScroll() { propertySurface.reset(); }
        public void panel(GuiGraphics g, Component heading, int color, List<EditorPropertyPanel.RowContent> rows,
                          Component done, EditorButton.Tone tone, int mouseX, int mouseY) {
            propertySurface.render(g, heading, color, rows, done, tone, mouseX, mouseY);
        }
        public void textButton(GuiGraphics g, UiRect bounds, Component label, Component tooltip, boolean enabled,
                               EditorButton.Tone tone, int mouseX, int mouseY) {
            renderEditorTextButton(g, bounds, label, tooltip, enabled, tone, mouseX, mouseY);
        }
        public QuestIconEditorRow.Layout texture(GuiGraphics g, int left, int top, int width, EditorTextField input,
                                                 boolean texture, int mouseX, int mouseY) {
            return renderTextureSelector(g, left, top, width, input, texture ? IconEditorMode.TEXTURE : IconEditorMode.ITEM, mouseX, mouseY);
        }
        public boolean submit(QuestPropertiesEditor.Submission intent) { return submitQuestProperties(intent); }
    });

    private void openQuestEditor(QuestDefinition quest) {
        if (ClientEditorState.get().busy()) return;
        dependencies.close(); typedEntries.close();
        questProperties.open(quest);
    }

    /** The form emits values; this boundary alone starts the authoritative request and updates selection. */
    private boolean submitQuestProperties(QuestPropertiesEditor.Submission intent) {
        var editor = ClientEditorState.get();
        if (!editor.beginMutation()) return false;
        rememberPendingEditUi();
        var replacement = intent.replacement();
        editor.previewEdit(book -> ClientEditPreview.questProperties(book, intent.originalId(), replacement));
        itemCache.remove(intent.originalId()); itemCache.remove(replacement.id());
        AuthoringNetwork.updateQuest(editor.sessionId(), editor.bookId(), editor.draftRevision(), intent.originalId(),
                replacement.id(), replacement.title(), replacement.subtitle(), replacement.description(), intent.iconMode(),
                intent.iconValue(), intent.preserveIcon(), replacement.x(), replacement.y(), replacement.appearance().shape(),
                replacement.appearance().size(), replacement.appearance().iconScale(), replacement.appearance().minWidth(), intent.config());
        editorSelectedQuest = replacement.id();
        closeQuestEditingPanels(); editorOverlays.close();
        return true;
    }

    private final QuestDependenciesEditor dependencies = new QuestDependenciesEditor(new QuestDependenciesEditor.Host() {
        public QuestBookSnapshot snapshot() { return displaySnapshot(); }
        public QuestDefinition selected() { return selectedQuest(); }
        public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
        public boolean panelReady() { return detailsOpen && detailsDrawerMotion.current() >= 1.0; }
        public int drawerOffset() { return detailsDrawerOffsetX(); }
        public int canvasRight() { return QuestScreen.this.canvasRight(); }
        public double elapsed() { return currentMotionFrameSeconds; }
        public String locale() { return minecraft.getLanguageManager().getSelected(); }
        public String title(QuestDefinition quest) { return questTitle(quest); }
        public String chapterTitle(QuestBookDefinition book, ResourceLocation chapter) { return QuestScreen.this.chapterTitle(book, chapter); }
        public void tooltip(List<Component> text) { hoveredComponentTooltip = text; }
        public void button(GuiGraphics g, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone, int x, int y) {
            renderEditorActionButton(g, bounds, definition, enabled, tone, x, y);
        }
        public void change(QuestDependenciesEditor.Submission intent) {
            sendMutation(intent.remove() ? "REMOVE_DEPENDENCY" : "ADD_DEPENDENCY", intent.quest(), null, intent.dependency(), "", 0, 0, 0, List.of());
        }
        public void context(ResourceLocation dependency, int x, int y) { openDependencyEntryContext(dependency, x, y); }
        public void closeOverlay() { closeActiveEditorOverlay(); }
    }, editorOverlays);
    private void openDependencyEditor(QuestDefinition quest) {
        questProperties.closeQuestEditor(); typedEntries.close(); closeActiveEditorOverlay(); dependencies.open(quest);
    }
    static Component dependencyCandidateSubtitle(QuestDependencyEditorModel.Candidate candidate) {
        return QuestDependenciesEditor.dependencyCandidateSubtitle(candidate);
    }

    private final EditorChildScreens editorChildren = new EditorChildScreens(() -> this, this::openChildScreen, this::openEditorItemSelector);

    private final QuestTypedEntriesEditor typedEntries = new QuestTypedEntriesEditor(new QuestTypedEntriesEditor.Host() {
        public QuestBookSnapshot snapshot() { return displaySnapshot(); }
        public QuestDefinition selected() { return selectedQuest(); }
        public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
        public QuestScreenFrameIdentity frame() { return currentFrameIdentity(); }
        public boolean panelReady() { return detailsOpen && detailsDrawerMotion.current() >= 1.0; }
        public int drawerOffset() { return detailsDrawerOffsetX(); }
        public int canvasRight() { return QuestScreen.this.canvasRight(); }
        public double elapsed() { return currentMotionFrameSeconds; }
        public String serverContext() { return serverContextId; }
        public ItemStack item(ResourceLocation id, String snbt) { return QuestScreen.this.item(id, snbt); }
        public QuestStatus status(QuestDefinition quest) { return QuestScreen.this.status(quest); }
        public void hover(RecipeLookupTarget item, List<Component> tooltip) {
            if (item != null) { hoveredRecipeLookupTarget = item; hoveredDetailStack = item.stack(); }
            if (!tooltip.isEmpty()) hoveredComponentTooltip = tooltip;
        }
        public void iconButton(GuiGraphics g, UiRect bounds, Component glyph, Component label, boolean enabled, boolean dangerous, int x, int y) {
            renderEditorIconButton(g, bounds, glyph, label, enabled, dangerous, x, y);
        }
        public void context(ResourceLocation id, int x, int y) { openTypedEntryContext(id, x, y); }
        public void closeOverlay() { closeActiveEditorOverlay(); }
        public void nativeClick(double x, double y) { QuestScreen.super.mouseClicked(x, y, 0); }
        public boolean mutate(EditorMutationIntent intent) { return sendMutation(intent); }
        public EditorChildScreens children() { return editorChildren; }
    }, editorOverlays, typedPropertySection);
    private void openTypedEditor(QuestDefinition quest, QuestTypedEntryKind kind) {
        questProperties.closeQuestEditor(); dependencies.close(); closeActiveEditorOverlay(); typedEntries.open(quest, kind);
    }
    private boolean sendMutation(EditorMutationIntent intent) {
        return sendMutation(intent.action(), intent.target(), intent.parent(), intent.secondary(), intent.text(),
                intent.index(), intent.x(), intent.y(), intent.positions().stream()
                        .map(position -> new AuthoringNetwork.PositionWire(position.quest().toString(), position.x(), position.y())).toList(), intent.config());
    }

    private final QuestStructureEditor structure = new QuestStructureEditor(new QuestStructureEditor.Host() {
        public QuestBookSnapshot snapshot() { return displaySnapshot(); }
        public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
        public String serverContext() { return serverContextId; }
        public String questTitle(QuestDefinition quest) { return QuestScreen.this.questTitle(quest); }
        public String localizedTitle(String kind, ResourceLocation id, String fallback) { return localizedStructureTitle(kind, id, fallback); }
        public EditorChildScreens children() { return editorChildren; }
        public void focus(EditorTextField field) { setFocused(field); }
        public void closeOverlay() { closeActiveEditorOverlay(); }
        public void clearHover() { hoveredDetailStack = ItemStack.EMPTY; hoveredDetailText = null; hoveredComponentTooltip = List.of(); }
        public void tooltip(List<Component> text) { hoveredComponentTooltip = text; }
        public void text(GuiGraphics g, UiRect bounds, Component label, Component tooltip, boolean enabled, EditorButton.Tone tone, int x, int y) {
            renderEditorTextButton(g, bounds, label, tooltip, enabled, tone, x, y);
        }
        public void icon(GuiGraphics g, UiRect bounds, Component glyph, Component label, boolean enabled, boolean dangerous, int x, int y) {
            renderEditorIconButton(g, bounds, glyph, label, enabled, dangerous, x, y);
        }
        public void action(GuiGraphics g, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone, int x, int y) {
            renderEditorActionButton(g, bounds, definition, enabled, tone, x, y);
        }
        public void nativeClick(double x, double y, int button) { QuestScreen.super.mouseClicked(x, y, button); }
        public boolean mutate(EditorMutationIntent intent) { return sendMutation(intent); }
    }, editorOverlays);
    private void openStructureForm(QuestStructureEditor.Kind kind, ResourceLocation target, ResourceLocation parent, double x, double y) {
        closeQuestEditingPanels(); closeActiveEditorOverlay(); structure.open(kind, target, parent, x, y);
    }

    private final QuestBookWorkflow bookWorkflow = new QuestBookWorkflow(new QuestBookWorkflow.Host() {
        public QuestBookSnapshot snapshot() { return displaySnapshot(); }
        public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
        public UiRect titleBounds() { return editorTitleBounds(); }
        public double elapsed() { return currentMotionFrameSeconds; }
        public EditorButtonInput buttons() { return formButtons; }
        public void closePanels() { closeQuestEditingPanels(); }
        public void closeOverlay() { closeActiveEditorOverlay(); }
        public void closingPreference(Boolean value) { closingEditingPreference = value; }
        public void closeScreen() { QuestScreen.super.onClose(); }
        public void tooltip(List<Component> text) { hoveredComponentTooltip = text; }
        public boolean jump(ResourceLocation id) { return jumpToEditorObject(id); }
        public QuestPublishReviewSection.Presentation presentation(ResourceLocation id) { return reviewPresentation(id); }
        public void details(List<Component> lines) {
            openChildScreen(new ResolvedOptionsScreen(QuestScreen.this, () -> lines, Component.translatable("screen.brnquest.editor.publish.review.details")));
        }
    }, new EditorBookTransport() {
        public void catalog() { AuthoringNetwork.requestCatalog(); }
        public void open(ResourceLocation book, String revision) { AuthoringNetwork.openSession(book, revision); }
        public void openCurrent(ResourceLocation book, String activeRevision, String draftRevision, boolean replace) { AuthoringNetwork.openCurrentSession(book, activeRevision, draftRevision, replace); }
        public void version(ResourceLocation book, String revision) { AuthoringNetwork.versionSavedDraft(book, revision); }
        public void versions(ResourceLocation book) { AuthoringNetwork.requestDraftVersions(book); }
        public void restore(ResourceLocation book, String backup, String revision) { AuthoringNetwork.restoreDraftVersion(book, backup, revision); }
        public void close(java.util.UUID session, String revision) { AuthoringNetwork.closeSession(session, revision); }
        public void publish(java.util.UUID session, ResourceLocation book, String revision, String workspaceRevision, String activeBook, String activeRevision) {
            AuthoringNetwork.publishAndApply(session, book, revision, workspaceRevision, activeBook, activeRevision);
        }
    }, editorOverlays);

    private final EditorPropertySurface propertySurface = new EditorPropertySurface(new EditorPropertySurface.Host() {
        public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
        public double elapsed() { return currentMotionFrameSeconds; }
        public int drawerOffset() { return detailsDrawerOffsetX(); }
        public int canvasRight() { return QuestScreen.this.canvasRight(); }
        public EditorButtonInput buttons() { return formButtons; }
        public boolean changed() { return localFormChanged(); }
        public void button(GuiGraphics g, UiRect bounds, Component label, boolean enabled, EditorButton.Tone tone, int x, int y) {
            renderEditorTextButton(g, bounds, label, null, enabled, tone, x, y);
        }
    });

    private final EditorSessionLifecycle editorSessionLifecycle = new EditorSessionLifecycle();

    /** Module invalidation follows session identity changes, including a return from a suspended child. */
    private void synchronizeEditorModules(ClientEditorState editor) {
        editorSessionLifecycle.accept(new EditorSessionLifecycle.Scope(editor.sessionId(), editor.bookId(), editor.editing()), () -> {
            closeQuestEditingPanels();
            switch (editorOverlays.active()) {
                case CONTEXT_MENU, ENUM_DROPDOWN, STRUCTURE_FORM, DELETE_CONFIRMATION, QUEST_RENAME_CONFIRMATION,
                        QUICK_TEXT, PUBLISH_CONFIRMATION -> closeActiveEditorOverlay();
                default -> { }
            }
        });
        // Catalog refresh temporarily clears permission while loading; keep its overlay until a final result.
        if (catalogAuthorizationDenied(editor.mode(), editor.allowed()) && editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG))
            closeActiveEditorOverlay();
    }

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
        typedEntries.bind(font);
        editorChrome.invalidate();
        dependencies.bind(font);
        bookWorkflow.bind(font);

        // setScreen(parent) initializes this same QuestScreen again after an item/raw-config child
        // closes. Re-register existing fields instead of replacing them, preserving the complete
        // in-progress form (stable ID, semantics, config values, cursor, and selection).
        propertySurface.bind(font);
        questProperties.bind(font, this::registerPropertyField);
        structure.bind(font, this::addRenderableWidget);
        typedPropertySection.attach(new QuestTypedPropertySection.Host() {
            public QuestTypedEntryKind kind() { return typedEntries.kind(); }
            public ResourceLocation quest() { return typedEntries.target(); }
            public QuestBookSnapshot snapshot() { return displaySnapshot(); }
            public QuestScreenFrameIdentity frame() { return currentFrameIdentity(); }
            public QuestScreenLayout layout() { return QuestScreen.this.layout(); }
            public EditorChildScreens children() { return editorChildren; }
            public void focus(EditorTextField field) { setFocused(field); }
            public void resetScroll() { propertySurface.reset(); }
            public UiRect viewport() { return propertySurface.viewport(); }
            public UiRect cancel() { return propertySurface.cancel(); }
            public UiRect save() { return propertySurface.save(); }
            public UiRect row(int index) { return propertySurface.row(index); }
            public UiRect visible(UiRect bounds) { return propertySurface.visible(bounds); }
            public void panel(GuiGraphics g, Component heading, int color, List<EditorPropertyPanel.RowContent> rows, Component done, EditorButton.Tone tone, int x, int y) {
                propertySurface.render(g, heading, color, rows, done, tone, x, y);
            }
            public void action(GuiGraphics g, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone, int x, int y) {
                renderEditorActionButton(g, bounds, definition, enabled, tone, x, y);
            }
            public void text(GuiGraphics g, UiRect bounds, Component label, Component tooltip, boolean enabled, EditorButton.Tone tone, int x, int y) {
                renderEditorTextButton(g, bounds, label, tooltip, enabled, tone, x, y);
            }
            public void dropdown(UiRect anchor, List<String> values, Consumer<String> result, java.util.function.Function<String, Component> labels) {
                openEnumDropdown(anchor, values, result, labels);
            }
            public ItemStack item(ResourceLocation id, String snbt) { return QuestScreen.this.item(id, snbt); }
            public void invalidateIcon(ResourceLocation id) { itemCache.remove(id); }
            public void tooltip(List<Component> text) { hoveredComponentTooltip = text; }
            public List<EditorPublishReviewModel.Diagnostic> diagnostics() {
                return ClientEditorState.get().diagnostics().stream().map(QuestScreen::publishDiagnostic).toList();
            }
            public void currentRequest(String id, String source, String currentValue) {
                yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork.send(
                        new yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork.Query(id, source, "", currentValue));
            }
            public boolean submit(String action, ResourceLocation quest, QuestTypedPropertySection.Submission value) {
                return sendMutation(action, value.replacementId(), quest, value.sourceId(), value.claimPolicy(), value.semanticFlag(), 0, 0, List.of(), value.config());
            }
        });
        typedPropertySection.bind(font, this::registerPropertyField);
        quickTextField = reinitializeOverlayEditorField(quickTextField,
                "screen.brnquest.editor.quick_edit.input", 256);
        quickTextField.setNativeFormatting(true);
        serverContextId = currentServerContext();
        synchronizeEditorModules(ClientEditorState.get());

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
            bookWorkflow.requestDiscardConfirmation(null, true);
            return;
        }
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        ClientEditorState editor = ClientEditorState.get();
        restoreEditingPreference(editor);
        bookWorkflow.tick();
        reconcileModeSelection(editor);
        synchronizeEditorModules(editor);
        editor.pollPublishReview().ifPresent(review -> bookWorkflow.acceptReview(publishReviewModel(review)));
        reconcileDragPreview();
        requestExpiredRepeatRecovery();
        // Only a live selected quest may query execution status; previews/rendering remain read-only.
        QuestDefinition selected = selectedQuest();
        if (gameplayAllowed() && selected != null && isCompleted(status(selected))) {
            selected.rewards().forEach(reward -> ClientRewardPresentationRegistry.get(reward.typeId()).refresh(ApiViews.reward(reward)));
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
            if (result.followSelection()) selectOnly(result.selectedId());
        } else {
            ClientQuestState.get().selected(result.selectedId());
            if (result.selectedId() != null) {
                // Match an ordinary view-mode node selection so the explicit protocol intent
                // remains consistent even though selection currently has no authority effect.
                BrnQuestNetwork.selectQuest(ClientQuestState.get().revision(), result.selectedId().toString());
            }
        }
        detailsOpen = result.detailsOpen();
        // Keep chapter browsing and camera position independent of a closed drawer's historical task.
        if (modeChanged && target != null) chapterIndex = QuestModeSelection.chapterIndex(rememberedChapterId,
                QuestPresentation.orderedChapters(target.book()).stream().map(ChapterDefinition::id).toList(), chapterIndex);
        if (result.followSelection() && target != null) {
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
            if (!structure.open() || structure.kind() == QuestStructureEditor.Kind.RENAME_CHAPTER) renderCanvas(graphics, selectedChapter, mouseX, mouseY);
            else {
                canvasFrame = null;
                graphics.fill(canvasLeft(), topToolbarHeight(), canvasRight(),
                        height - bottomToolbarHeight(), GraystonePalette.DRAWER);
            }
            renderNavigation(graphics, snapshot.book(), selectedChapter, mouseX, mouseY, motionFrameSeconds);
            if (detailsDrawerVisible() && !structure.open()) {
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
                if (structure.open()) graphics.pose().translate(0, 0, 500);
                if (structure.open()) structure.render(graphics, mouseX, mouseY);
                offsetDetailsDrawerFieldsForMotion();
                // Native inputs render later than their rows, so give them the same viewport explicitly.
                propertySurface.renderFields(graphics, mouseX, mouseY, partialTick);
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
        Map<ResourceLocation, ChapterProgress> progress = new java.util.HashMap<>();
        // Author drafts have no player progress. Count only the live book's server-visible quests.
        if (gameplayAllowed() && ClientQuestState.get().hasAuthoritativeVisibility()) {
            var state = ClientQuestState.get();
            boolean clientDefault = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.showChapterProgress);
            for (var chapter : book.chapters()) {
                if (DisplayOverrides.progress(chapter.extensions()).resolve(clientDefault))
                    progress.put(chapter.id(), ChapterProgress.count(chapter,
                            state::visible, id -> state.statuses().get(id.toString())));
            }
        }
        var navigationLayout = new QuestNavigationPanel.Layout(navigationWidth(), topToolbarHeight(), height - bottomToolbarHeight(),
                navigationListBottom(), navigationHandleLeft(), navigationDrawerOffsetX(),
                navigationHandleWidth(), contentCenterY(), navigationCollapsed);
        QuestNavigationPanel.RenderResult result = navigationPanel.render(graphics, font,
                new QuestNavigationPanel.Model(currentFrameIdentity(), book, selectedChapter,
                        ClientEditorState.get().editing(), defaultGroupId(book) != null, progress),
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
                    openStructureForm(QuestStructureEditor.Kind.ADD_GROUP, null, null, 0, 0);
                }
            }
            case ADD_CHAPTER -> {
                ResourceLocation groupId = defaultGroupId(book);
                if (ClientEditorState.get().editing() && groupId != null) {
                    openStructureForm(QuestStructureEditor.Kind.ADD_CHAPTER, null, groupId, 0, 0);
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
                tooltip.add(NativeText.component(hiddenText ? "???" : questTitle(quest)).withStyle(ChatFormatting.WHITE));
                String subtitle = hiddenText ? "" : localizedQuestText(quest, "quest_subtitle", quest.subtitle());
                if (!NativeText.visible(subtitle).isBlank()) tooltip.add(NativeText.component(subtitle).withStyle(ChatFormatting.GRAY));
                if (allowGameplay) tooltip.add(Component.translatable("screen.brnquest.status."
                        + status.name().toLowerCase(java.util.Locale.ROOT)).withStyle(ChatFormatting.GRAY));
                DraftBookEditor.Position preview = canvasController.preview(quest.id());
                nodes.add(new QuestCanvasRenderer.NodeModel(quest.id(), quest.appearance(),
                        preview == null ? new DraftBookEditor.Position(quest.x(), quest.y()) : preview,
                        canvasController.snapPreview(quest.id()), canvasNodeColor(status, allowGameplay),
                        editing ? canvasController.selected(quest.id()) : quest.id().equals(selectedQuestId()),
                        allowGameplay && ClientQuestState.get().trackedQuest().filter(quest.id()::equals).isPresent(),
                        canvasController.pickedUp() && quest.id().equals(canvasController.dragAnchor()),
                        allowGameplay && questHasAttentionTask(quest, status),
                        allowGameplay && QuestPresentation.hasPendingReward(
                                quest, status, ClientQuestState.get().claimed()),
                        visual, stack, tooltip, quest.dependencies(),
                        allowGameplay && !(status == QuestStatus.LOCKED && quest.behavior().hideLockIcon()) ? status : null,
                        DisplayOverrides.name(quest.extensions())));
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
        // Paint the existing close hitbox with the same native sprite as other close controls.
        QuestActionIcons.named("close").render(graphics, font,
                new UiRect(width - 18, topToolbarHeight(), width, topToolbarHeight() + 16), 0xFFFFFFFF);

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
        if (editing && questProperties.open() && quest.id().equals(questProperties.target())) {
            detailsPanel.reset();
            questProperties.render(graphics, quest, mouseX, mouseY);
            detailsInteraction.finish();
            return;
        }
        if (editing && dependencies.open() && quest.id().equals(dependencies.target())) {
            detailsPanel.reset();
            dependencies.render(graphics, quest, mouseX, mouseY);
            detailsInteraction.finish();
            return;
        }
        if (editing && typedEntries.open() && quest.id().equals(typedEntries.target())) {
            detailsPanel.reset();
            typedEntries.render(graphics, quest, mouseX, mouseY);
            detailsInteraction.finish();
            return;
        }
        Component cooldownText = repeatCooldownText(quest, status);
        Component statusText = !gameplayAllowed() ? Component.translatable("screen.brnquest.editor.preview")
                : cooldownText != null ? cooldownText
                : Component.translatable(QuestPresentation.statusTranslationKey(quest, status, ClientQuestState.get().claimed()));
        if (gameplayAllowed() && ClientQuestState.get().questCompletionPending(quest.id().toString()))
            statusText = Component.translatable("screen.brnquest.choice.waiting");
        if (gameplayAllowed() && ClientQuestState.get().waitingForTeam(quest.id().toString()))
            statusText = Component.translatable("screen.brnquest.status.waiting_for_team");
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
                        editing, gameplayAllowed(), ClientQuestState.get().trackedQuest().filter(quest.id()::equals).isPresent(), canSubmit(quest, status),
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
        Component visible = translationKey.equals("screen.brnquest.editor.dependency.edit")
                ? Component.translatable("screen.brnquest.editor.dependency.short") : label;
        renderEditorActionButton(graphics, bounds, EditorButton.Definition.iconAndText(
                        visible, label, QuestActionIcons.symbol(Component.literal(glyph))),
                true, EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }

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
        QuestTaskRowWidget.Model model = new QuestTaskRowWidget.Model(task, presentation, presentationContext, state,
                taskSatisfied(task, status), canSubmit(quest, status),
                presentation.submissionInteraction(presentationContext).isPresent());
        QuestTaskRowWidget.Result row = taskRowWidget.render(graphics, font, model,
                new QuestTaskRowWidget.Layout(x, y, width, detailRecipeLookupViewport(),
                        mouseX, mouseY, attentionPingOffsetY));
        QuestDetailsInteraction.Action rowAction = switch (row.rowAction()) {
            case SUBMIT_TASK -> QuestDetailsInteraction.Action.SUBMIT_TASK;
            case COMPLETE_QUEST -> QuestDetailsInteraction.Action.COMPLETE_QUEST;
            case OPEN_TASK_INTERACTION -> QuestDetailsInteraction.Action.OPEN_TASK_INTERACTION;
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
                return new QuestRelationsScreen.Entry(target.id(), NativeText.component(hideText ? "???" : questTitle(target)),
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
                    presentation.candidateScreen(this, taskPresentationContext(task, status(quest)))
                            .ifPresent(this::openChildScreen);
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
                if (gameplayAllowed() && status.trackable()) {
                    BrnQuestNetwork.toggleTracked(ClientQuestState.get().revision(), quest.id().toString());
                }
            }
            case SUBMIT_TASK, OPEN_TASK_INTERACTION -> handleDetailsTaskIntent(intent.action(), quest,
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
        var presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var interaction = presentation.submissionInteraction(taskPresentationContext(task, status));
        String revision = ClientQuestState.get().revision();
        var sourceLevel = minecraft.level;
        var sourcePlayer = minecraft.player;
        // A factory cannot submit while it is still constructing its page.
        Screen[] activePage = {null};
        var submit = new TaskSubmissionDispatch(() -> gameplayAllowed() && minecraft.screen == activePage[0]
                && minecraft.level == sourceLevel && minecraft.player == sourcePlayer
                && revision.equals(ClientQuestState.get().revision())
                && taskDisplayState(quest, task, status(quest)).actionable()
                && ClientQuestState.get().beginTaskSubmission(task.id().toString()), selection ->
                BrnQuestNetwork.completeTask(revision, quest.id().toString(), task.id().toString(), selection.inventorySlots()));
        if (interaction.isPresent()) {
            activePage[0] = interaction.orElseThrow().createScreen(this, submit);
            openChildScreen(activePage[0]);
        } else {
            activePage[0] = this;
            submit.accept(yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection.AUTOMATIC);
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
            QuestActionIcons.named("clock").render(graphics, font,
                    new UiRect(x, y - 2, x + 10, y + 8), GraystonePalette.ACCENT);
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
        synchronizeEditorModules(ClientEditorState.get());
        if (java.util.Objects.equals(formButtonFrame, currentFrameIdentity())
                && formButtonOverlay == editorOverlays.active()) formButtons.clicked(mouseX, mouseY, button);
        if (editorOverlays.mouseClicked(mouseX, mouseY, button)) return true;
        var snapshot = displaySnapshot();
        if (snapshot == null) return super.mouseClicked(mouseX, mouseY, button);
        if (handleEditorChromeClick(mouseX, mouseY, button, snapshot.book())) return true;

        if (dependencies.open() && detailsPanelAcceptsPointer(mouseX)) {
            return dependencies.click(mouseX, mouseY, button);
        }

        if ((questProperties.open() || typedPropertySection.open()) && detailsPanelAcceptsPointer(mouseX) && button == 0
                && propertySurface.trackClick(mouseX, mouseY, button)) return true;
        if (typedEntries.open() && detailsPanelAcceptsPointer(mouseX)) {
            return typedEntries.click(mouseX, mouseY, button);
        }

        if (questProperties.open() && detailsPanelAcceptsPointer(mouseX)) {
            if (!questProperties.click(mouseX, mouseY, button, propertySurface.viewport(), propertySurface.save(), propertySurface.cancel()))
                super.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        QuestNavigationPanel.ClickResult navigationClick = navigationPanel.click(
                currentFrameIdentity(), mouseX, mouseY, button);
        if (navigationClick.intent() != null) handleNavigationIntent(navigationClick.intent(), snapshot.book());
        if (navigationClick.consumed()) return true;
        if (button == 0 && detailsPanelAcceptsPointer(mouseX)
                && detailsPanel.scroll().handleTrackClick(mouseX, mouseY, width - 8,
                detailContentTop(), detailContentBottom(), detailsPanel.contentHeight(), detailViewportHeight())) {
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
        synchronizeEditorModules(ClientEditorState.get());
        if (releaseOverlayScroll(button)) return true;
        if (editorOverlays.mouseReleased(x, y, button, super::mouseReleased)) return true;
        if (propertySurface.release(button) || detailsPanel.scroll().handleRelease(button)
                || navigationPanel.releaseScroll(button) || dependencies.release(button)
                || typedEntries.release(button)) return true;
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
        synchronizeEditorModules(ClientEditorState.get());
        if (dragOverlayScroll(y, button)) return true;
        if (editorOverlays.mouseDragged(x, y, button, dx, dy, super::mouseDragged)) return true;
        if (propertySurface.drag(y, button) || detailsPanel.scroll().handleDrag(y, button)
                || navigationPanel.dragScroll(y, button) || dependencies.drag(y, button)
                || typedEntries.drag(y, button)) return true;
        if (canvasArtwork.drag(currentFrameIdentity(), x, y, button)) return true;
        if (navigationPanel.drag(currentFrameIdentity(), x, y, button)) return true;
        QuestCanvasController.GestureResult result = canvasController.mouseDragged(
                canvasFrame, currentFrameIdentity(), x, y, button, System.nanoTime());
        if (result.consumed()) return true;
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        synchronizeEditorModules(ClientEditorState.get());
        if (editorOverlays.mouseScrolled(x, y, vertical)) return true;
        if (canvasArtwork.dragging()) return true;
        // The property form is painted over the entry list; it owns wheel input throughout this panel.
        if ((questProperties.open() || typedPropertySection.open()) && detailsPanelAcceptsPointer(x)) {
            propertySurface.wheel(x, y, vertical);
            return true;
        }
        if (dependencies.open() && detailsPanelAcceptsPointer(x)) {
            dependencies.wheel(x, y, vertical);
            return true;
        }
        if (typedEntries.open() && detailsPanelAcceptsPointer(x)) {
            typedEntries.wheel(x, y, vertical);
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
        synchronizeEditorModules(ClientEditorState.get());
        if (keyCode == 256 && canvasArtwork.cancel()) return true;
        if (keyCode == 256 && navigationPanel.cancelDrag()) return true;
        if (editorOverlays.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (questProperties.keyPressed(keyCode)) return true;
        if (keyCode == 256 && dependencies.open()) {
            dependencies.close();
            return true;
        }
        if (typedEntries.key(keyCode, hasControlDown(), hasShiftDown())) return true;
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
                    openStructureForm(QuestStructureEditor.Kind.ADD_QUEST, null, chapterId,
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
        Optional<Component> typedNarration = typedEntries.narration();
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
                && !questProperties.open() && !dependencies.open() && !typedEntries.open() && !typedPropertySection.open()
                && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox)
                && !(getFocused() instanceof yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextInput);
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
                if (editor.dirty()) bookWorkflow.requestDiscardConfirmation(null, false);
                else {
                    closingEditingPreference = false;
                    bookWorkflow.closeEditorSession(null);
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
                bookWorkflow.requestDraftSourceChoice(displaySnapshot());
            }
            case OPEN_CATALOG -> {
                boolean opening = !editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG);
                closeActiveEditorOverlay();
                if (opening) {
                    bookWorkflow.openCatalog();
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
        synchronizeEditorModules(ClientEditorState.get());
        return editorOverlays.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    /** Adds editor controls as overlays so switching modes never resizes the established three regions. */
    private boolean handleEditorChromeClick(double x, double y, int button, QuestBookDefinition displayedBook) {
        if (bookWorkflow.catalogClick(x, y, button)) return true;
        if (button != 0) return false;
        var result = editorChrome.click(currentFrameIdentity(), x, y);
        if (result.intent() != null) handleEditorChromeIntent(result.intent(), displayedBook);
        return result.consumed();
    }

    private void renderEditorChrome(GuiGraphics graphics, QuestBookDefinition book, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        // Item rendering uses a raised GUI depth; keep opaque editor popovers above
        // every background node/task icon without changing their screen geometry.
        graphics.pose().translate(0, 0, 300);
        ClientEditorState editor = ClientEditorState.get();
        Component status = editorStatus();
        boolean publishSurfaceReady = !editor.busy() && !questProperties.open() && !dependencies.open() && !typedEntries.open()
                && !structure.open() && editorOverlays.active() == EditorOverlayHost.Kind.NONE;
        List<Component> errorTooltip = editor.mode() == ClientEditorState.Mode.ERROR
                ? EditorTooltipComposer.operationError(editor.statusCode(), editor.statusMessage()) : List.of();
        QuestEditorChrome.Model chromeModel = new QuestEditorChrome.Model(currentFrameIdentity(),
                BookText.title(book, minecraft.getLanguageManager().getSelected()),
                book.id(), editor.allowed(), editor.editing(), editor.hasLease(), editor.live(), editor.busy(),
                editor.dirty(), editor.canUndo(), editor.canRedo(), editor.undoSteps(), editor.redoSteps(),
                publishSurfaceReady, editorHistorySurfaceReady(), status,
                editor.mode() == ClientEditorState.Mode.ERROR, errorTooltip, BrnQuestClientConfig.VALUES.snapToGrid.get(),
                editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG), editorTitleTooltip(book.id()));
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

    /** Dropdown values remain localized text; the disclosure cue uses the shared native-size PNG. */
    private void renderEditorDropdownButton(GuiGraphics graphics, UiRect bounds, Component label,
                                            boolean enabled, int mouseX, int mouseY) {
        renderEditorActionButton(graphics, bounds, EditorButton.Definition.iconAndText(
                        label, null, QuestActionIcons.named(enumDropdownAnchor != null
                                && enumDropdownAnchor.equals(bounds) ? "fold" : "unfold")),
                enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
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
            case "ADMIN_TASK" -> openAdminProgress(typedEntries.target(), target);
            case "SELF_FORCE_QUEST" -> openSelfProgress(target, null, false);
            case "SELF_RESET_QUEST" -> openSelfProgress(target, null, true);
            case "SELF_FORCE_TASK" -> openSelfProgress(typedEntries.target(), target, false);
            case "SELF_RESET_TASK" -> openSelfProgress(typedEntries.target(), target, true);
            case "ADD_QUEST" -> {
                ResourceLocation chapterId = currentChapterId();
                if (chapterId != null) openStructureForm(QuestStructureEditor.Kind.ADD_QUEST, null, chapterId, graphX, graphY);
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
            case "COPY_QUEST" -> openStructureForm(QuestStructureEditor.Kind.COPY_QUEST, target, null, graphX + 1, graphY + 1);
            case "DELETE_QUEST" -> requestDelete(DeleteKind.QUEST, target, book);
            case "ADD_CHAPTER" -> openStructureForm(QuestStructureEditor.Kind.ADD_CHAPTER, null, target, 0, 0);
            case "RENAME_GROUP" -> openStructureForm(QuestStructureEditor.Kind.RENAME_GROUP, target, null, 0, 0);
            case "MOVE_GROUP_UP" -> moveGroup(book, target, -1);
            case "MOVE_GROUP_DOWN" -> moveGroup(book, target, 1);
            case "DELETE_GROUP" -> requestDelete(DeleteKind.GROUP, target, book);
            case "BOOK_PROPERTIES" -> openBookProperties();
            case "COPY_CHAPTER" -> sendMutation("COPY_CHAPTER", target, null, null, "", 0, 0, 0, List.of());
            case "RENAME_CHAPTER" -> openStructureForm(QuestStructureEditor.Kind.RENAME_CHAPTER, target, null, 0, 0);
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
                if (dependencies.target() != null) {
                    sendMutation("REMOVE_DEPENDENCY", dependencies.target(), null,
                            target, "", 0, 0, 0, List.of());
                    dependencies.message(null);
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
                            canManageProgress(typedEntries.target(), editContextTarget))
                    .action("SELF_RESET_TASK", Component.translatable("screen.brnquest.admin.self_reset"), true,
                            canManageProgress(typedEntries.target(), editContextTarget))
                    .action("ADMIN_TASK", adminProgressLabel(typedEntries.target(), editContextTarget), false,
                            canManageProgress(typedEntries.target(), editContextTarget))
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
                                            && editContextTypedIndex + 1 < typedEntries.kind().size(selectedQuest())))
                    .submenu(Component.translatable("screen.brnquest.editor.context.technical"), technical -> technical
                            .action("COPY_TYPED_ID", Component.translatable(
                                    "screen.brnquest.editor.context.copy_stable_id"), false)
                            .action("COPY_TYPED_TYPE", Component.translatable(
                                    "screen.brnquest.editor.context.copy_type_id"), false))
                    .action("DELETE_TYPED", Component.translatable(
                            "screen.brnquest.editor.context.delete"), true)).stream()
                    .filter(entry -> !(entry.action().equals("ADMIN_TASK") || entry.action().startsWith("SELF_"))
                            || typedEntries.kind() == QuestTypedEntryKind.TASK && hasAdminProgressPermission()).toList();
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
        for (int index = 0; index < typedEntries.kind().size(quest); index++) {
            if (!typedEntries.kind().value(quest, index).id().equals(id)) continue;
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
        if (quest == null || typedId == null || index < 0 || index >= typedEntries.kind().size(quest)) return;
        String prefix = typedEntries.kind().actionPrefix();
        switch (action) {
            case "EDIT_TYPED" -> typedPropertySection.openEditor(quest, typedId);
            case "COPY_TYPED_SNAPSHOT" -> typedEntries.copy(typedId);
            case "COPY_TYPED" -> {
                ResourceLocation copyId = EditorObjectIds.suggest(book, typedEntries.kind().idStem() + "_copy");
                sendMutation("COPY_" + prefix, copyId, quest.id(), typedId, "", 0, 0, 0, List.of());
            }
            case "MOVE_TYPED_UP" -> {
                if (index > 0) sendMutation("MOVE_" + prefix, typedId, quest.id(), null, "",
                        index - 1, 0, 0, List.of());
            }
            case "MOVE_TYPED_DOWN" -> {
                if (index + 1 < typedEntries.kind().size(quest)) {
                    sendMutation("MOVE_" + prefix, typedId, quest.id(), null, "",
                            index + 1, 0, 0, List.of());
                }
            }
            case "COPY_TYPED_ID" -> copyTechnicalId(typedId.toString());
            case "COPY_TYPED_TYPE" -> {
                QuestTypedEntryKind.Entry entry = typedEntries.kind().entry(quest, typedId);
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

    private void copyTechnicalId(String value) {
        if (minecraft == null) return;
        minecraft.keyboardHandler.setClipboard(value);
        Component copied = Component.translatable("screen.brnquest.editor.context.copied_id");
        if (dependencies.open()) dependencies.message(copied);
        else typedEntries.message(copied);
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
        registerOverlay(EditorOverlayHost.Kind.STRUCTURE_FORM, null, structure::click);
        registerOverlay(EditorOverlayHost.Kind.DELETE_CONFIRMATION, this::renderDeleteConfirmation, this::handleDeleteConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.DISCARD_CONFIRMATION, bookWorkflow::renderDiscardConfirmation, bookWorkflow::handleDiscardConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.QUEST_RENAME_CONFIRMATION, questProperties::renderRename, questProperties::clickRename);
        registerOverlay(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION, bookWorkflow::renderPublishConfirmation,
                bookWorkflow::handlePublishConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.CONFLICT_RECOVERY, this::renderConflictRecovery, this::handleConflictRecoveryClick);
        registerOverlay(EditorOverlayHost.Kind.DRAFT_SOURCE_CHOICE, bookWorkflow::renderDraftSourceChoice,
                bookWorkflow::handleDraftSourceChoiceClick);
        registerOverlay(EditorOverlayHost.Kind.DRAFT_VERSIONS, bookWorkflow::renderDraftVersions,
                bookWorkflow::handleDraftVersionsClick);
        registerOverlay(EditorOverlayHost.Kind.DRAFT_RESTORE_CONFIRMATION, bookWorkflow::renderDraftRestoreConfirmation,
                bookWorkflow::handleDraftRestoreConfirmationClick);
        registerOverlay(EditorOverlayHost.Kind.CONTEXT_MENU, this::renderEditContextMenu, (x, y, button) -> displaySnapshot() != null && handleEditContextClick(x, y, button, displaySnapshot().book()));
        registerOverlay(EditorOverlayHost.Kind.ENUM_DROPDOWN, this::renderEnumDropdown, this::handleEnumDropdownClick);
        registerOverlay(EditorOverlayHost.Kind.DEPENDENCY_PICKER, dependencies::renderPicker, dependencies::clickPicker);
        registerOverlay(EditorOverlayHost.Kind.TYPED_TYPE_PICKER, typedEntries::renderPicker, typedEntries::clickPicker);
        registerOverlay(EditorOverlayHost.Kind.QUICK_TEXT, this::renderQuickTextEditor, this::handleQuickTextEditorClick);
        registerOverlay(EditorOverlayHost.Kind.CATALOG, (g, x, y) -> { if (ClientEditorState.get().allowed()) bookWorkflow.renderEditorCatalog(g, x, y); }, (x, y, button) -> displaySnapshot() != null && handleEditorChromeClick(x, y, button, displaySnapshot().book()));
    }

    private void registerOverlay(EditorOverlayHost.Kind kind, EditorOverlayHost.Draw draw, EditorOverlayHost.Click click) {
        editorOverlays.register(kind, new EditorOverlayHost.Route(draw, click,
                this::handleOverlayKey, this::handleOverlayText, this::handleOverlayWheel, this::closeActiveEditorOverlay,
                kind == EditorOverlayHost.Kind.STRUCTURE_FORM || kind == EditorOverlayHost.Kind.QUICK_TEXT));
    }

    private boolean handleOverlayKey(int key, int scan, int modifiers) {
        // Overlay-owned navigation never falls through into the suspended editor or canvas.
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.TYPED_TYPE_PICKER)) return typedEntries.pickerKey(key, modifiers);
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)) return bookWorkflow.catalogKey(key, modifiers);
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT) && (key == 257 || key == 335)) {
            submitQuickTextEdit();
            return true;
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)) return dependencies.pickerKey(key, modifiers);
        if (structure.open() || editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) {
            return super.keyPressed(key, scan, modifiers);
        }
        return true;
    }

    private boolean handleOverlayText(char character, int modifiers) {
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)) {
            return bookWorkflow.catalogText(character);
        } else if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)) {
            return dependencies.pickerText(character);
        } else if (structure.open() || editorOverlays.isOpen(EditorOverlayHost.Kind.QUICK_TEXT)) {
            return super.charTyped(character, modifiers);
        }
        return true;
    }

    private void handleOverlayWheel(double x, double y, double amount) {
        switch (editorOverlays.active()) {
            case CATALOG -> bookWorkflow.wheel(editorOverlays.active(), x, y, amount);
            case DRAFT_VERSIONS -> bookWorkflow.wheel(editorOverlays.active(), x, y, amount);
            case DEPENDENCY_PICKER -> dependencies.pickerWheel(x, y, amount);
            case TYPED_TYPE_PICKER -> typedEntries.pickerWheel(x, y, amount);
            case PUBLISH_CONFIRMATION -> bookWorkflow.wheel(editorOverlays.active(), x, y, amount);
            default -> { }
        }
    }

    private void closeActiveEditorOverlay() {
        switch (editorOverlays.active()) {
            case CATALOG -> bookWorkflow.closeCatalog();
            case DRAFT_VERSIONS -> bookWorkflow.closeVersions();
            case DRAFT_RESTORE_CONFIRMATION -> bookWorkflow.closeRestore();
            case CONTEXT_MENU -> closeEditContext();
            case ENUM_DROPDOWN -> closeEnumDropdown();
            case STRUCTURE_FORM -> structure.close();
            case DEPENDENCY_PICKER -> dependencies.closePicker();
            case TYPED_TYPE_PICKER -> typedEntries.closePicker();
            case QUICK_TEXT -> closeQuickTextEditor();
            case DELETE_CONFIRMATION -> {
                deleteSelection = java.util.Set.of();
                deleteKind = DeleteKind.NONE;
                deleteTarget = null;
                deleteImpact = "";
                editorOverlays.close();
            }
            case DISCARD_CONFIRMATION -> bookWorkflow.closeDiscard();
            case QUEST_RENAME_CONFIRMATION -> editorOverlays.close();
            case PUBLISH_CONFIRMATION -> bookWorkflow.closePublish();
            case CONFLICT_RECOVERY -> {
                recoveryCopyBookId = null;
                editorOverlays.close();
            }
            case DRAFT_SOURCE_CHOICE -> bookWorkflow.clearDraftSourceChoice();
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

    /** An in-flight permission refresh is undecided, not a denial that should dismiss its own overlay. */
    static boolean catalogAuthorizationDenied(ClientEditorState.Mode mode, boolean allowed) {
        return mode != ClientEditorState.Mode.CATALOG_LOADING && !allowed;
    }

    private boolean dragOverlayScroll(double y, int button) {
        return switch (editorOverlays.active()) {
            case CATALOG -> bookWorkflow.drag(editorOverlays.active(), y, button);
            case DRAFT_VERSIONS -> bookWorkflow.drag(editorOverlays.active(), y, button);
            case DEPENDENCY_PICKER -> dependencies.dragPicker(y, button);
            case TYPED_TYPE_PICKER -> typedEntries.dragPicker(y, button);
            case PUBLISH_CONFIRMATION -> bookWorkflow.drag(editorOverlays.active(), y, button);
            default -> false;
        };
    }

    private boolean releaseOverlayScroll(int button) {
        return switch (editorOverlays.active()) {
            case CATALOG -> bookWorkflow.release(editorOverlays.active(), button);
            case DRAFT_VERSIONS -> bookWorkflow.release(editorOverlays.active(), button);
            case DEPENDENCY_PICKER -> dependencies.releasePicker(button);
            case TYPED_TYPE_PICKER -> typedEntries.releasePicker(button);
            case PUBLISH_CONFIRMATION -> bookWorkflow.release(editorOverlays.active(), button);
            default -> false;
        };
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
            EditorTextRenderer.drawCaptionCentered(graphics, font, Component.literal(recoveryCopyBookId.toString()),
                    new UiRect(panel.left()+12,panel.bottom()-16,panel.right()-12,panel.bottom()-7),
                    GraystonePalette.MUTED, mouseX, mouseY);
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

    private void showLocalizedPlaceholder(EditorTextField field, EditorLocalizedText text) {
        // Native suggestions are gray and remain visible with focus, but are never selected or submitted.
        field.setSuggestion(field.getValue().isEmpty() && text != null
                ? font.plainSubstrByWidth(text.placeholder(), Math.max(0, field.getWidth() - 8)) : null);
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
        openChildScreen(new EditorLocaleScreen(this, text.locales(), text.locale(), locale -> {
            text.select(locale); quickTextField.setValue(text.value());
        }));
    }

    private Map<String, String> localizedConfig(EditorLocalizedText text, String field, Map<String, String> metadata) {
        var result = new java.util.TreeMap<String, String>(metadata);
        result.put(LocalizedSingleLineEdits.FIELD, field);
        text.changes().forEach((locale, value) -> result.put(LocalizedSingleLineEdits.PREFIX + locale, value));
        return Map.copyOf(result);
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

    /** Describe the displayed edit session without adding another permanent header label. */
    private List<Component> editorTitleTooltip(ResourceLocation displayedBookId) {
        ClientEditorState editor = ClientEditorState.get();
        List<Component> lines = new ArrayList<>();
        if (editor.editing()) {
            lines.add(Component.translatable(editor.live()
                    ? "screen.brnquest.editor.identity.edit_live" : "screen.brnquest.editor.identity.edit_draft"));
        } else if (editor.mode() == ClientEditorState.Mode.OPENING
                || editor.mode() == ClientEditorState.Mode.RECEIVING_DRAFT) {
            lines.add(Component.translatable("screen.brnquest.editor.identity.opening"));
        } else {
            lines.add(Component.translatable("screen.brnquest.editor.identity.view_active"));
        }
        lines.add(Component.translatable("screen.brnquest.editor.identity.book_id", displayedBookId.toString()));
        if (editor.editing() && !editor.live()) {
            if (!editor.savedRevision().isBlank()) lines.add(Component.translatable(
                    "screen.brnquest.editor.identity.saved_revision", shortRevision(editor.savedRevision())));
            else lines.add(Component.translatable("screen.brnquest.editor.identity.unsaved_draft"));
            if (editor.dirty()) lines.add(Component.translatable("screen.brnquest.editor.identity.unsaved_changes"));
            // Origin belongs to the exact saved catalog revision; an older catalog reply cannot label a newer draft.
            editor.catalog().stream().filter(editor::editingCatalogEntry).findFirst().ifPresent(entry -> {
                lines.add(Component.translatable("screen.brnquest.editor.identity.origin." +
                        entry.origin().name().toLowerCase(java.util.Locale.ROOT)));
                if (entry.origin() == DraftOrigin.ACTIVE && ClientQuestState.get().book()
                        .filter(active -> active.book().id().equals(displayedBookId)
                                && active.revision().equals(editor.baseRevision())).isPresent()) {
                    lines.add(Component.translatable("screen.brnquest.editor.identity.from_current_active"));
                }
            });
        } else if (ClientQuestState.get().book().filter(active -> active.book().id().equals(displayedBookId))
                .isPresent()) {
            lines.add(Component.translatable("screen.brnquest.editor.identity.active_revision",
                    shortRevision(ClientQuestState.get().book().orElseThrow().revision())));
        }
        return List.copyOf(lines);
    }

    private static String shortRevision(String revision) {
        return revision.length() > 12 ? revision.substring(0, 12) : revision;
    }

    /** History never discards values that are still only present in an open client-side form. */
    private boolean editorHistorySurfaceReady() {
        return !questProperties.open() && !dependencies.open() && !typedEntries.open() && !structure.open()
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE;
    }

    /** Compare with the initialized form, not persisted config: opening may populate defaults or normalize icons. */
    private boolean localFormChanged() {
        if (questProperties.open()) return questProperties.changed();
        if (typedPropertySection.open()) return typedPropertySection.changed();
        return structure.changed();
    }

    private Component editorStatus() {
        // Keep stale-progress feedback visible while the book remains usable for reading.
        if (ClientQuestState.get().progressSyncFailure().isPresent())
            return Component.translatable("screen.brnquest.progress_sync.failed");
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
                if ("DRAFT_COPY_CREATED".equals(editor.statusCode())) {
                    yield Component.translatable("screen.brnquest.editor.status.copied");
                }
                if ("DRAFT_VERSION_CREATED".equals(editor.statusCode())) {
                    yield Component.translatable("screen.brnquest.editor.status.versioned");
                }
                if ("DRAFT_BACKUP_RESTORED".equals(editor.statusCode())) {
                    yield Component.translatable("screen.brnquest.editor.status.restored");
                }
                yield Component.translatable(editor.dirty() ? "screen.brnquest.editor.status.dirty"
                        : "screen.brnquest.editor.status.saved");
            }
            case CLOSING -> Component.translatable("screen.brnquest.editor.session.closing");
            case ERROR -> editor.allowed() ? EditorDiagnosticPresentation.operationError(editor.statusCode()) : null;
            case VIEW -> null;
        };
    }

    /** Read-only field replies return to the still-open form; they never save a draft by themselves. */
    public void receiveServerField(yourscraft.jasdewstarfield.brnquest.network.ServerFieldNetwork.Reply reply) {
        if (reply != null && reply.result() != null)
            typedPropertySection.receiveCurrent(reply.id(), reply.result().current());
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

    /** Opens a real child Screen so JEI initializes ghost dragging before the first interaction. */
    private void openEditorItemSelector(java.util.function.Consumer<ItemStack> selectionConsumer) {
        if (minecraft == null) return;
        openChildScreen(new EditorItemSelectorScreen(this, selectionConsumer));
    }

    private void closeQuestEditingPanels() {
        questProperties.closeQuestEditor();
        dependencies.close();
        typedEntries.close();
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

    /** Keep native fields in input/focus routing while drawing them ourselves inside the property viewport. */
    private void registerPropertyField(EditorTextField field) {
        propertySurface.register(field);
        addWidget(field);
    }

    /** Chapter and quest forms share decorative picker icons and their action tooltips. */
    private QuestIconEditorRow.Layout renderTextureSelector(GuiGraphics graphics, int left, int top, int width,
            EditorTextField input, IconEditorMode mode, int mouseX, int mouseY) {
        return EditorTextureSelector.render(graphics, font, left, top, width, input, mode == IconEditorMode.TEXTURE,
                !ClientEditorState.get().busy(),
                (g, bounds, label, enabled, tone) -> renderEditorTextButton(g, bounds, label, null, enabled, tone, mouseX, mouseY),
                (g, bounds, definition, enabled, tone) -> renderEditorActionButton(g, bounds, definition, enabled, tone, mouseX, mouseY));
    }

    /** EditBox widgets render through Screen after custom panels, so apply the drawer translation explicitly. */
    private void offsetDetailsDrawerFieldsForMotion() {
        if (!detailsOpen && !detailsDrawerVisible()) {
            questProperties.hide();
            typedPropertySection.hide();
            return;
        }
        int offset = detailsDrawerOffsetX();
        questProperties.offset(offset);
        typedPropertySection.offsetForDrawerAnimation(offset);
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
        // The same responsive rectangles drive painting, scrolling boundaries, pointer input and keyboard focus.
        return layout().detailEditorButton(index);
    }

    private UiRect editorTitleBounds() {
        ClientEditorState editor = ClientEditorState.get();
        return QuestEditorChrome.layout(layout(), editor.live(), editor.hasLease(), editor.allowed()).title();
    }

    /** Copies protocol records into an immutable UI model at the screen integration boundary. */
    private static EditorPublishReviewModel publishReviewModel(AuthoringNetwork.PublishReviewWire review) {
        return new EditorPublishReviewModel(review.publishAllowed(), review.fromRevision(), review.targetRevision(),
                review.activeBookId(), review.activeRevision(), review.diagnosticCount(), review.changeCount(), review.truncated(),
                review.diagnostics().stream().map(QuestScreen::publishDiagnostic).toList(),
                review.changes().stream().map(change -> new EditorPublishReviewModel.Change(change.kind(),
                        change.objectKind(), change.objectId(), change.path(), change.before(), change.after())).toList());
    }

    private static EditorPublishReviewModel.Diagnostic publishDiagnostic(
            AuthoringNetwork.EditorDiagnosticWire diagnostic) {
        return new EditorPublishReviewModel.Diagnostic(diagnostic.severity(), diagnostic.code(),
                diagnostic.objectId(), diagnostic.path(), diagnostic.message());
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
            if (quest.id().equals(id)) return new QuestPublishReviewSection.Presentation(NativeText.component(questTitle(quest)), reviewIcon(quest.id(), quest.icon()));
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

    private boolean canSubmit(QuestDefinition quest, QuestStatus status) {
        // Match the server's empty-definition gate while the author is still configuring objectives.
        if (quest.tasks().isEmpty()) return false;
        if (ClientQuestState.get().waitingForTeam(quest.id().toString())) return false;
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
        var presentation = ClientTaskPresentationRegistry.get(task.typeId());
        return taskDisplayState(quest, task, status, presentation, taskPresentationContext(task, status));
    }

    /** Typed pages and rows see the same server-ledger projection without accessing runtime definitions. */
    private TaskPresentationContext taskPresentationContext(TaskDefinition task, QuestStatus status) {
        ClientTaskPresentation presentation = ClientTaskPresentationRegistry.get(task.typeId());
        var view = ApiViews.task(task);
        String itemSnbt = presentation.itemSnbt(view);
        ItemStack displayedItem = itemSnbt.isBlank() ? ItemStack.EMPTY : item(task.id(), itemSnbt);
        long storedProgress = ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L);
        return new TaskPresentationContext(minecraft, view, status, storedProgress, displayedItem);
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
            lines.add(Component.translatable("screen.brnquest.dependency.entry", chapterTitle, NativeText.component(dependencyTitle)));
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
        ChapterDefinition chapter = chapters.get(Math.min(chapterIndex, chapters.size() - 1));
        rememberedChapterId = chapter.id();
        focusChapter(chapter);
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

    /** Left-clicking anywhere outside the popup dismisses it without activating the covered surface. */
    static boolean catalogClickDismisses(UiRect bounds, double x, double y, int button) {
        return QuestScreenCatalogInput.dismisses(bounds, x, y, button);
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
        return Component.translatable("screen.brnquest.markdown.quest_link.open", NativeText.component(title),
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
        return height - bottomToolbarHeight() - (ClientEditorState.get().editing() ? 38 : 0);
    }

    private int detailViewportHeight() {
        return Math.max(1, detailContentBottom() - detailContentTop());
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

}
