package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPickerList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;

/** Coordinates catalog, version restore, discard confirmation and revision-bound publication. */
final class QuestBookWorkflow {
    interface Host {
        QuestBookSnapshot snapshot();
        default String locale() { return "en_us"; }
        QuestScreenLayout layout();
        UiRect titleBounds();
        double elapsed();
        EditorButtonInput buttons();
        void closePanels();
        void closeOverlay();
        void closingPreference(Boolean value);
        void closeScreen();
        void tooltip(List<Component> text);
        boolean jump(ResourceLocation id);
        QuestPublishReviewSection.Presentation presentation(ResourceLocation id);
        void details(List<Component> lines);
    }
    private final Host host;
    private final EditorBookTransport transport;
    private final EditorOverlayHost editorOverlays;
    private final EditorCloseFlow closeFlow = new EditorCloseFlow();
    private Font font;
    private static final int EDITOR_CATALOG_ROW_HEIGHT = 30;
    private static final int EDITOR_CATALOG_SEARCH_HEIGHT = 18;
    private static final int EDITOR_CATALOG_ACTION_HEIGHT = 24;
    QuestBookWorkflow(Host host, EditorBookTransport transport, EditorOverlayHost overlays) {
        this.host = host; this.transport = transport; editorOverlays = overlays;
    }
    void bind(Font font) {
        this.font = font; catalogPicker.invalidate(); versionPicker.invalidate();
        if (!catalogRequested && !ClientEditorState.get().editing()) {
            catalogRequested = true; ClientEditorState.get().beginCatalogRequest(); transport.catalog();
        }
    }
    void tick() {
        var editor = ClientEditorState.get();
        if (closeFlow.step() != EditorCloseFlow.Step.CLOSING) return;
        if (editor.mode() == ClientEditorState.Mode.ERROR) { closeFlow.cancel(); return; }
        if (editor.mode() != ClientEditorState.Mode.VIEW || editor.hasLease()) return;
        closeFlow.closed().ifPresent(plan -> {
            switch (plan.after()) {
                case COPY_CURRENT -> createCurrentDraftCopy(ClientQuestState.get().book().orElse(null));
                case SHOW_VERSIONS -> openDraftVersions(ClientQuestState.get().book().orElse(null));
                case NONE -> { }
            }
        });
    }
    void acceptReview(EditorPublishReviewModel model) {
        host.closeOverlay(); publishReviewSection.open(model); editorOverlays.show(EditorOverlayHost.Kind.PUBLISH_CONFIRMATION);
        publishReviewSection.context(host.snapshot(), host.locale());
    }
    void openCatalog() {
        ClientEditorState.get().beginCatalogRequest(); transport.catalog();
        editorOverlays.show(EditorOverlayHost.Kind.CATALOG); catalogFilter = ""; catalogSearchFocused = true; catalogPicker.reset();
    }
    void closeCatalog() { catalogFilter = ""; catalogSearchFocused = false; catalogPicker.reset(); editorOverlays.close(); }
    void closeVersions() { versionPicker.reset(); versionsBookId = null; editorOverlays.close(); }
    void closeRestore() { selectedRestoreVersion = null; restoreCurrentRevision = ""; editorOverlays.close(); }
    void closeDiscard() { closeFlow.cancel(); editorOverlays.close(); }
    void closePublish() { publishReviewSection.close(); editorOverlays.close(); }
    boolean drag(EditorOverlayHost.Kind kind, double y, int button) {
        return switch (kind) { case CATALOG -> catalogPicker.mouseDragged(y, button); case DRAFT_VERSIONS -> versionPicker.mouseDragged(y, button); case PUBLISH_CONFIRMATION -> publishReviewSection.mouseDragged(y, button); default -> false; };
    }
    boolean release(EditorOverlayHost.Kind kind, int button) {
        return switch (kind) { case CATALOG -> catalogPicker.mouseReleased(button); case DRAFT_VERSIONS -> versionPicker.mouseReleased(button); case PUBLISH_CONFIRMATION -> publishReviewSection.mouseReleased(button); default -> false; };
    }
    void wheel(EditorOverlayHost.Kind kind, double x, double y, double amount) {
        switch (kind) { case CATALOG -> catalogPicker.mouseScrolled(x, y, amount, scrollStep()); case DRAFT_VERSIONS -> versionPicker.mouseScrolled(x, y, amount, scrollStep()); case PUBLISH_CONFIRMATION -> publishReviewSection.mouseScrolled(host.layout(), amount, scrollStep()); default -> { } }
    }
    boolean catalogKey(int key, int modifiers) {
        if (catalogPicker.navigate(key, (modifiers & 1) != 0)) return true;
        if (key == 257 || key == 335) catalogPicker.focusedRow().ifPresent(row -> catalogClick(row.visible().centerX(), row.visible().centerY(), 0));
        else if (key == 259 && catalogSearchFocused && !catalogFilter.isEmpty()) { catalogFilter = catalogFilter.substring(0, catalogFilter.length() - 1); catalogPicker.reset(); }
        return true;
    }
    boolean catalogText(char character) {
        if (catalogSearchFocused && !Character.isISOControl(character) && catalogFilter.length() < 48) { catalogFilter += character; catalogPicker.reset(); }
        return true;
    }
    private void afterClose(EditorCloseFlow.AfterClose after) {
        host.closePanels(); host.closeOverlay();
        var editor = ClientEditorState.get();
        var plan = new EditorCloseFlow.Plan(null, "", false, after);
        closeFlow.request(plan, editor.dirty());
        if (editor.dirty()) editorOverlays.show(EditorOverlayHost.Kind.DISCARD_CONFIRMATION);
        else executeClose(plan);
    }
    private void executeClose(EditorCloseFlow.Plan plan) {
        if (plan.after() == EditorCloseFlow.AfterClose.NONE)
            host.closingPreference(plan.target() == null ? plan.closeScreen() : null);
        closeEditorSession(plan.target(), plan.revision());
        // Book switching already has a revision-bound continuation in ClientEditorState.
        if (plan.after() == EditorCloseFlow.AfterClose.NONE) closeFlow.cancel();
        if (plan.closeScreen()) host.closeScreen();
    }
    private ResourceLocation versionsBookId;
    private final EditorPickerList<ClientEditorState.DraftVersionEntry> versionPicker = new EditorPickerList<>();
    private ClientEditorState.DraftVersionEntry selectedRestoreVersion;
    private String restoreCurrentRevision = "";
    private final EditorPickerList<ClientEditorState.CatalogEntry> catalogPicker = new EditorPickerList<>();
    private String catalogFilter = "";
    private boolean catalogSearchFocused;
    private ResourceLocation draftChoiceBookId;
    private String draftChoiceActiveRevision = "";
    private String draftChoiceDraftRevision = "";
    private String draftChoiceTitle = "";
    private final QuestPublishReviewSection publishReviewSection = new QuestPublishReviewSection();
    private boolean catalogRequested;
    void renderEditorCatalog(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = host.layout().width(), height = host.layout().height();
        List<ClientEditorState.CatalogEntry> entries = editorCatalogEntries();
        renderEditorPickerShell(graphics, editorCatalogBounds(), EDITOR_CATALOG_ACTION_HEIGHT);
        UiRect bounds = editorCatalogListBounds();
        catalogPicker.advance(bounds, new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight()),
                entries.size(), entries::get, host.elapsed(), scrollSmoothSpeed());
        boolean searchHint = catalogFilter.isBlank() && !catalogSearchFocused;
        Component searchText = searchHint
                ? Component.translatable("screen.brnquest.editor.catalog.search_hint")
                : Component.literal(catalogFilter);
        Component empty = Component.translatable(catalogFilter.isBlank()
                ? "screen.brnquest.editor.catalog.current_missing" : "screen.brnquest.editor.catalog.no_match");
        ClientEditorState editor = ClientEditorState.get();
        ResourceLocation activeBookId = ClientQuestState.get().book()
                .map(active -> active.book().id()).orElse(null);
        List<Component> tooltip = catalogPicker.render(graphics, font, searchText, searchHint,
                catalogSearchFocused, entry -> {
            Component title = Component.literal(entry.title().isBlank() ? entry.bookId().toString() : entry.title());
            Component id = Component.literal(entry.bookId().toString());
            Component origin = Component.translatable("screen.brnquest.editor.catalog.origin." +
                    entry.origin().name().toLowerCase(java.util.Locale.ROOT));
            Component detail = entry.importSource().isBlank() ? id
                    : Component.translatable("screen.brnquest.editor.catalog.ftb_source",
                            entry.importSource(), entry.bookId().toString());
            boolean editingDraft = editor.editingCatalogEntry(entry);
            // Live editing operates on the active book; its same-ID server draft is related but unopened.
            boolean related = editor.relatedCatalogEntry(entry, activeBookId);
            List<Component> entryTooltip = new ArrayList<>(List.of(title, id, origin));
            if (!entry.importSource().isBlank()) entryTooltip.add(Component.translatable(
                    "screen.brnquest.editor.catalog.ftb_source_tooltip", entry.importSource()));
            if (editingDraft) entryTooltip.add(Component.translatable(
                    "screen.brnquest.editor.catalog.editing_revision", shortRevision(entry.draftRevision())));
            else if (related) entryTooltip.add(Component.translatable(
                    "screen.brnquest.editor.catalog.related_draft"));
            return new EditorPickerList.Entry(title, detail, EditorPickerList.Tone.NORMAL,
                    editingDraft ? EditorPickerList.Highlight.EDITING_DRAFT
                            : related ? EditorPickerList.Highlight.SAME_BOOK : EditorPickerList.Highlight.NONE,
                    entryTooltip);
        }, empty, mouseX, mouseY);
        if (!tooltip.isEmpty()) host.tooltip(tooltip);
        boolean hasCurrentDraft = ClientEditorState.get().catalog().stream()
                .anyMatch(entry -> ClientQuestState.get().book().map(book -> book.book().id().equals(entry.bookId()))
                        .orElse(false));
        boolean createHovered = EditorButton.renderInteractive(graphics, font, editorCatalogCopyBounds(),
                EditorButton.Definition.text(Component.translatable(hasCurrentDraft
                                ? "screen.brnquest.editor.catalog.copy_saved" : "screen.brnquest.editor.catalog.create_current"),
                        Component.translatable("screen.brnquest.editor.catalog.copy_hint")),
                ClientQuestState.get().book().isPresent() && ClientEditorState.get().allowed()
                        && !ClientEditorState.get().busy(), false,
                EditorButton.Tone.PRIMARY, mouseX, mouseY);
        if (createHovered) host.tooltip(List.of(
                Component.translatable("screen.brnquest.editor.catalog.copy_hint")));
        boolean versionsHovered = EditorButton.renderInteractive(graphics, font, editorCatalogVersionsBounds(),
                EditorButton.Definition.text(Component.translatable("screen.brnquest.editor.catalog.versions"), null),
                ClientQuestState.get().book().isPresent() && ClientEditorState.get().allowed()
                        && !ClientEditorState.get().busy(), false,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        if (versionsHovered) host.tooltip(List.of(
                Component.translatable("screen.brnquest.editor.catalog.versions_hint")));
    }

    void requestDraftSourceChoice(QuestBookSnapshot activeSnapshot) {
        if (activeSnapshot == null || ClientEditorState.get().busy()) return;
        host.closeOverlay();
        draftChoiceBookId = activeSnapshot.book().id();
        draftChoiceActiveRevision = activeSnapshot.revision();
        ClientEditorState.CatalogEntry existing = ClientEditorState.get().catalog().stream()
                .filter(entry -> entry.bookId().equals(draftChoiceBookId)).findFirst().orElse(null);
        draftChoiceDraftRevision = existing == null ? "" : existing.draftRevision();
        draftChoiceTitle = existing == null ? "" : existing.title();
        editorOverlays.show(EditorOverlayHost.Kind.DRAFT_SOURCE_CHOICE);
    }

    private void createCurrentDraftCopy(QuestBookSnapshot activeSnapshot) {
        ClientEditorState editor = ClientEditorState.get();
        if (activeSnapshot == null || !editor.allowed() || editor.busy()) return;
        ResourceLocation bookId = activeSnapshot.book().id();
        String selectedDraftRevision = editor.catalog().stream()
                .filter(entry -> entry.bookId().equals(bookId))
                .map(ClientEditorState.CatalogEntry::draftRevision).findFirst().orElse("");
        if (editor.beginOpenCurrent(bookId)) {
            if (selectedDraftRevision.isBlank()) {
                transport.openCurrent(bookId, activeSnapshot.revision(), "", true);
            } else {
                transport.version(bookId, selectedDraftRevision);
            }
        }
    }

    private void openDraftVersions(QuestBookSnapshot activeSnapshot) {
        ClientEditorState editor = ClientEditorState.get();
        if (activeSnapshot == null || !editor.allowed() || editor.busy() || editor.hasLease()) return;
        host.closeOverlay();
        versionsBookId = activeSnapshot.book().id();
        versionPicker.reset();
        editor.beginDraftVersions(versionsBookId);
        transport.versions(versionsBookId);
        editorOverlays.show(EditorOverlayHost.Kind.DRAFT_VERSIONS);
    }

    void renderDraftVersions(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = host.layout().width(), height = host.layout().height();
        ClientEditorState editor = ClientEditorState.get();
        List<ClientEditorState.DraftVersionEntry> versions = editor.draftVersions();
        UiRect popup = editorVersionsBounds();
        renderEditorPickerShell(graphics, popup, EDITOR_CATALOG_ACTION_HEIGHT);
        UiRect listBounds = new UiRect(popup.left() + 2, popup.top() + 2, popup.right() - 2,
                Math.max(popup.top() + 2, popup.bottom() - EDITOR_CATALOG_ACTION_HEIGHT));
        versionPicker.advance(listBounds, pickerScreenBounds(), versions.size(), versions::get,
                host.elapsed(), scrollSmoothSpeed());
        Component empty = editor.versionsLoading()
                ? Component.translatable("screen.brnquest.editor.versions.loading")
                : editor.versionsError().isBlank()
                ? Component.translatable("screen.brnquest.editor.versions.empty")
                : EditorDiagnosticPresentation.operationError(editor.versionsError());
        List<Component> tooltip = versionPicker.renderHeading(graphics, font,
                Component.translatable("screen.brnquest.editor.versions.title"), version -> {
                    String date = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.ofEpochMilli(version.savedAtEpochMillis()));
                    Component label = Component.literal(date + "  " + shortDraftRevision(version.revision()));
                    return new EditorPickerList.Entry(label, Component.literal(version.title()),
                            EditorPickerList.Tone.NORMAL, false,
                            List.of(Component.literal(version.revision()), Component.literal(version.backupId())));
                }, empty, mouseX, mouseY);
        if (!tooltip.isEmpty()) host.tooltip(tooltip);
        EditorButton.renderInteractive(graphics, font, editorVersionsBackBounds(),
                EditorButton.Definition.text(Component.translatable("screen.brnquest.editor.versions.back"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    boolean handleDraftVersionsClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        if (!editorVersionsBounds().contains(mouseX, mouseY)) {
            host.closeOverlay();
            return true;
        }
        if (editorVersionsBackBounds().contains(mouseX, mouseY)) {
            host.closeOverlay();
            ClientEditorState.get().beginCatalogRequest();
            transport.catalog();
            editorOverlays.show(EditorOverlayHost.Kind.CATALOG);
            catalogSearchFocused = true;
            return true;
        }
        if (versionPicker.mouseClicked(mouseX, mouseY, button)) return true;
        var chosen = versionPicker.entryAt(mouseX, mouseY);
        if (chosen.isEmpty() || versionsBookId == null) return true;
        selectedRestoreVersion = chosen.orElseThrow();
        restoreCurrentRevision = ClientEditorState.get().catalog().stream()
                .filter(entry -> entry.bookId().equals(versionsBookId))
                .map(ClientEditorState.CatalogEntry::draftRevision).findFirst().orElse("");
        editorOverlays.show(EditorOverlayHost.Kind.DRAFT_RESTORE_CONFIRMATION);
        return true;
    }

    void renderDraftRestoreConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selectedRestoreVersion == null) return;
        EditorConfirmDialog.render(graphics, font, host.layout(), host.buttons(),
                Component.translatable("screen.brnquest.editor.versions.restore_title"),
                Component.translatable("screen.brnquest.editor.versions.restore_detail",
                        shortDraftRevision(selectedRestoreVersion.revision())),
                GraystonePalette.SECONDARY, Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.versions.restore"), mouseX, mouseY);
    }

    boolean handleDraftRestoreConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        var action = EditorConfirmDialog.actionAt(host.layout(), mouseX, mouseY);
        if (action == EditorConfirmDialog.Action.CANCEL) {
            selectedRestoreVersion = null;
            restoreCurrentRevision = "";
            editorOverlays.show(EditorOverlayHost.Kind.DRAFT_VERSIONS);
        } else if (action == EditorConfirmDialog.Action.CONFIRM && selectedRestoreVersion != null
                && versionsBookId != null) {
            ResourceLocation bookId = versionsBookId;
            String backupId = selectedRestoreVersion.backupId();
            String expected = restoreCurrentRevision;
            selectedRestoreVersion = null;
            restoreCurrentRevision = "";
            host.closeOverlay();
            if (ClientEditorState.get().beginOpenCurrent(bookId)) {
                transport.restore(bookId, backupId, expected);
            }
        }
        return true;
    }

    private static String shortDraftRevision(String revision) {
        return revision == null || revision.isBlank() ? "-"
                : revision.substring(0, Math.min(8, revision.length()));
    }

    void renderDraftSourceChoice(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean existing = !draftChoiceDraftRevision.isBlank();
        Component title = draftChoiceTitle.isBlank() ? null : Component.literal(draftChoiceTitle);
        yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.render(
                graphics, font, host.layout(), existing, title, mouseX, mouseY);
    }

    boolean handleDraftSourceChoiceClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        boolean existing = !draftChoiceDraftRevision.isBlank();
        var action = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorDraftChoiceDialog.actionAt(
                host.layout(), existing, mouseX, mouseY);
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
            transport.openCurrent(bookId, activeRevision, draftRevision, replace);
        }
        return true;
    }

    void clearDraftSourceChoice() {
        draftChoiceBookId = null;
        draftChoiceActiveRevision = "";
        draftChoiceDraftRevision = "";
        draftChoiceTitle = "";
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DRAFT_SOURCE_CHOICE)) editorOverlays.close();
    }

    void renderPublishConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        publishReviewSection.presentation(host::presentation);
        publishReviewSection.context(host.snapshot(), host.locale());
        QuestPublishReviewSection.RenderResult result = publishReviewSection.render(
                graphics, font, host.layout(), host.elapsed(), scrollSmoothSpeed(), mouseX, mouseY);
        if (!result.tooltip().isEmpty()) graphics.renderTooltip(font, result.tooltip().stream()
                .flatMap(line -> font.split(line, Math.max(80, Math.min(420, host.layout().width() - 40))).stream())
                .toList(), mouseX, mouseY);
        graphics.flush();
        graphics.pose().popPose();
    }

    boolean handlePublishConfirmationClick(double mouseX, double mouseY, int button) {
        QuestPublishReviewSection.ClickResult result = publishReviewSection.click(host.layout(), mouseX, mouseY, button);
        if (result.intent() != null) handlePublishReviewIntent(result.intent());
        return result.consumed();
    }

    private void handlePublishReviewIntent(QuestPublishReviewSection.Intent intent) {
        switch (intent.action()) {
            case CANCEL -> host.closeOverlay();
            case DETAILS -> {
                var lines = publishReviewSection.detailLines();
                host.details(lines);
            }
            case JUMP_TO_OBJECT -> {
                if (intent.objectId() != null && host.jump(intent.objectId())) host.closeOverlay();
            }
            case CONFIRM -> {
                String reviewedRevision = intent.reviewedRevision();
                host.closeOverlay();
                ClientEditorState editor = ClientEditorState.get();
                // The preview is revision-bound; never confirm a different draft with stale review data.
                if (reviewedRevision.equals(editor.draftRevision())) {
                    editor.beginPublish().ifPresent(request -> transport.publish(
                            request.sessionId(), editor.bookId(), request.draftRevision(),
                            intent.workspaceRevision(), intent.activeBookId(), intent.activeRevision()));
                }
            }
        }
    }

    void requestDiscardConfirmation(ResourceLocation switchTarget, boolean closeScreen) {
        requestDiscardConfirmation(switchTarget, "", closeScreen);
    }

    void requestDiscardConfirmation(ResourceLocation switchTarget, String expectedDraftRevision, boolean closeScreen) {
        host.closePanels(); host.closeOverlay();
        closeFlow.request(new EditorCloseFlow.Plan(switchTarget, expectedDraftRevision, closeScreen, EditorCloseFlow.AfterClose.NONE), true);
        editorOverlays.show(EditorOverlayHost.Kind.DISCARD_CONFIRMATION);
    }

    void renderDiscardConfirmation(GuiGraphics graphics, int mouseX, int mouseY) {
        EditorConfirmDialog.render(graphics, font, host.layout(), host.buttons(),
                Component.translatable("screen.brnquest.editor.discard.warning"), null, 0,
                Component.translatable("screen.brnquest.editor.discard.cancel"),
                Component.translatable("screen.brnquest.editor.discard.confirm"), mouseX, mouseY);
    }

    boolean handleDiscardConfirmationClick(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        var action = EditorConfirmDialog.actionAt(host.layout(), mouseX, mouseY);
        if (action == EditorConfirmDialog.Action.CANCEL) closeDiscard();
        else if (action == EditorConfirmDialog.Action.CONFIRM) {
            closeFlow.confirm().ifPresent(plan -> { editorOverlays.close(); executeClose(plan); });
        }
        return true;
    }

    void closeEditorSession(ResourceLocation openAfterClose) {
        closeEditorSession(openAfterClose, "");
    }

    void closeEditorSession(ResourceLocation openAfterClose, String expectedDraftRevision) {
        ClientEditorState.get().beginClose(openAfterClose, expectedDraftRevision).ifPresent(request ->
                transport.close(request.sessionId(), request.draftRevision()));
    }

    private UiRect editorCatalogBounds() {
        int width = host.layout().width();
        UiRect title = host.titleBounds();
        int visibleRows = editorCatalogVisibleRows();
        int height = EditorPickerList.popupHeightForRows(visibleRows, EDITOR_CATALOG_ACTION_HEIGHT);
        int availableWidth = Math.max(title.width(), width - 8);
        int catalogWidth = Math.min(420, availableWidth);
        int left = Math.max(4, title.centerX() - catalogWidth / 2);
        int right = Math.min(width - 4, left + catalogWidth);
        left = Math.max(4, right - catalogWidth);
        return new UiRect(left, title.bottom() + 2, right, title.bottom() + 2 + height).intersection(pickerScreenBounds());
    }

    private UiRect editorCatalogListBounds() {
        UiRect popup = editorCatalogBounds();
        return new UiRect(popup.left() + 2, popup.top() + 2, popup.right() - 2,
                Math.max(popup.top() + 2, popup.bottom() - EDITOR_CATALOG_ACTION_HEIGHT));
    }

    private UiRect editorCatalogSearchBounds() {
        UiRect list = editorCatalogListBounds();
        return new UiRect(list.left() + 2, list.top() + 2, list.right() - 2,
                Math.min(list.bottom(), list.top() + EDITOR_CATALOG_SEARCH_HEIGHT));
    }

    private void renderEditorPickerShell(GuiGraphics graphics, UiRect popup, int actionHeight) {
        GraystoneSurface.raised(graphics, popup, GraystonePalette.PANEL, true);
        int seamY = popup.bottom() - actionHeight;
        if (seamY > popup.top() + 2 && seamY < popup.bottom() - 2)
            graphics.fill(popup.left() + 3, seamY, popup.right() - 3, seamY + 1,
                    GraystonePalette.EDGE);
    }

    private UiRect editorCatalogActionBounds() {
        UiRect popup = editorCatalogBounds();
        return new UiRect(popup.left() + 4, popup.bottom() - EDITOR_CATALOG_ACTION_HEIGHT + 2,
                popup.right() - 4, popup.bottom() - 3);
    }

    private UiRect editorCatalogCopyBounds() {
        UiRect actions = editorCatalogActionBounds();
        return new UiRect(actions.left(), actions.top(), actions.centerX() - 2, actions.bottom());
    }

    private UiRect editorCatalogVersionsBounds() {
        UiRect actions = editorCatalogActionBounds();
        return new UiRect(actions.centerX() + 2, actions.top(), actions.right(), actions.bottom());
    }

    private UiRect editorVersionsBounds() {
        int width = host.layout().width(), height = host.layout().height();
        UiRect title = host.titleBounds();
        int rows = editorPickerVisibleRows(ClientEditorState.get().draftVersions().size());
        int popupHeight = EditorPickerList.popupHeightForRows(rows, EDITOR_CATALOG_ACTION_HEIGHT);
        int popupWidth = Math.min(420, Math.max(title.width(), width - 8));
        int left = Math.max(4, Math.min(width - 4 - popupWidth, title.centerX() - popupWidth / 2));
        return new UiRect(left, title.bottom() + 2, left + popupWidth,
                title.bottom() + 2 + popupHeight).intersection(pickerScreenBounds());
    }

    private UiRect editorVersionsBackBounds() {
        UiRect popup = editorVersionsBounds();
        return new UiRect(popup.left() + 4, popup.bottom() - EDITOR_CATALOG_ACTION_HEIGHT + 2,
                popup.right() - 4, popup.bottom() - 3);
    }

    private UiRect pickerScreenBounds() {
        int width = host.layout().width(), height = host.layout().height();
        return new UiRect(4, topToolbarHeight(), Math.max(4, width - 4),
                Math.max(topToolbarHeight(), height - bottomToolbarHeight() - 4));
    }

    private int editorCatalogVisibleRows() {
        return editorPickerVisibleRows(editorCatalogEntries().size());
    }

    private int editorPickerVisibleRows(int entries) {
        int availablePopupHeight = pickerScreenBounds().bottom() - host.titleBounds().bottom() - 2;
        int chromeHeight = EditorPickerList.popupHeightForRows(0, EDITOR_CATALOG_ACTION_HEIGHT);
        int possible = Math.max(1, (availablePopupHeight - chromeHeight) / EDITOR_CATALOG_ROW_HEIGHT);
        return Math.min(8, Math.min(Math.max(1, entries), possible));
    }

    private List<ClientEditorState.CatalogEntry> editorCatalogEntries() {
        QuestBookSnapshot snapshot = host.snapshot();
        ResourceLocation displayedBookId = snapshot == null ? null : snapshot.book().id();
        // This is an explicit "Advanced drafts" catalog: show every draft by default.
        // ClientEditorState still ranks the currently displayed book first, while
        // the search field can narrow the complete server-authoritative list.
        return ClientEditorState.get().filteredCatalog(displayedBookId, catalogFilter);
    }

    boolean catalogClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        ClientEditorState editor = ClientEditorState.get();
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.CATALOG)) {
            UiRect catalogBounds = editorCatalogBounds();
            if (!catalogClickDismisses(catalogBounds, mouseX, mouseY, button)) {
                if (editorCatalogSearchBounds().contains(mouseX, mouseY)) {
                    catalogSearchFocused = true;
                    catalogPicker.clearFocus();
                    return true;
                }
                catalogSearchFocused = false;
                if (editorCatalogCopyBounds().contains(mouseX, mouseY)) {
                    if (ClientQuestState.get().book().isPresent() && !editor.busy()) {
                        editorOverlays.close();
                        if (editor.hasLease()) {
                            afterClose(EditorCloseFlow.AfterClose.COPY_CURRENT);
                        } else createCurrentDraftCopy(ClientQuestState.get().book().orElse(null));
                    }
                    return true;
                }
                if (editorCatalogVersionsBounds().contains(mouseX, mouseY)) {
                    if (ClientQuestState.get().book().isPresent() && editor.allowed() && !editor.busy()) {
                        editorOverlays.close();
                        if (editor.hasLease()) {
                            afterClose(EditorCloseFlow.AfterClose.SHOW_VERSIONS);
                        } else openDraftVersions(ClientQuestState.get().book().orElse(null));
                    }
                    return true;
                }
                if (catalogPicker.mouseClicked(mouseX, mouseY, button)) return true;
                var chosen = catalogPicker.entryAt(mouseX, mouseY);
                if (chosen.isPresent()) {
                    ClientEditorState.CatalogEntry selected = chosen.orElseThrow();
                    ResourceLocation target = selected.bookId();
                    if (editorCatalogEntries().stream().noneMatch(entry -> entry.bookId().equals(target))) return true;
                    editorOverlays.close();
                    catalogFilter = "";
                    if (editor.hasLease()) {
                        if (!target.equals(editor.bookId())) {
                            if (editor.dirty()) requestDiscardConfirmation(target, selected.draftRevision(), false);
                            else closeEditorSession(target, selected.draftRevision());
                        }
                    } else if (editor.beginOpen(target)) {
                        transport.open(target, selected.draftRevision());
                    }
                }
                return true;
            }
            // The open popup owns this click. Returning immediately prevents a
            // title click from closing and reopening the catalog in one event.
            editorOverlays.close();
            return true;
        }
        return false;
    }
    private int topToolbarHeight() { return host.layout().topToolbarHeight(); }
    private int bottomToolbarHeight() { return host.layout().bottomToolbarHeight(); }
    private static double scrollStep() { return BrnQuestClientConfig.VALUES.scrollStep.get(); }
    private static double scrollSmoothSpeed() { return BrnQuestClientConfig.VALUES.smoothSpeed.get(); }
    private static String shortRevision(String revision) { return revision.length() > 12 ? revision.substring(0, 12) : revision; }
    static boolean catalogClickDismisses(UiRect catalogBounds, double mouseX, double mouseY, int button) {
        return button == 0 && !catalogBounds.contains(mouseX, mouseY);
    }
}
