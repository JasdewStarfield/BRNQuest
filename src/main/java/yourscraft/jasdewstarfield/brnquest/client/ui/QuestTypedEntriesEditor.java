package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorEntryRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorEntryListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListViewport;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPickerList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

/** Task/reward lists and type selection share one target identity and input lifecycle. */
final class QuestTypedEntriesEditor {
    interface Host {
        QuestBookSnapshot snapshot();
        QuestDefinition selected();
        QuestScreenLayout layout();
        QuestScreenFrameIdentity frame();
        boolean panelReady();
        int drawerOffset();
        int canvasRight();
        double elapsed();
        String serverContext();
        ItemStack item(ResourceLocation id, String snbt);
        QuestStatus status(QuestDefinition quest);
        void hover(RecipeLookupTarget item, List<Component> tooltip);
        void iconButton(GuiGraphics g, UiRect bounds, Component glyph, Component label, boolean enabled, boolean dangerous, int x, int y);
        void context(ResourceLocation id, int x, int y);
        void closeOverlay();
        void nativeClick(double x, double y);
        boolean mutate(EditorMutationIntent intent);
        EditorChildScreens children();
    }
    private final Host host;
    private final EditorOverlayHost editorOverlays;
    private final QuestTypedPropertySection typedPropertySection;
    private final EditorFormIdentity identity = new EditorFormIdentity();
    private Font font;
    private static final int TYPED_ROW_HEIGHT = 38;
    QuestTypedEntriesEditor(Host host, EditorOverlayHost overlays, QuestTypedPropertySection properties) {
        this.host = host; editorOverlays = overlays; typedPropertySection = properties;
    }
    void bind(Font font) { this.font = font; typedEntryList.invalidate(); typedTypePickerFrame = null; typedTypePicker.invalidate(); }
    boolean open() { return typedEditorOpen; }
    QuestTypedEntryKind kind() { return typedEditorKind; }
    ResourceLocation target() { return typedEditorQuestId; }
    void message(Component value) { typedEditorMessage = value; }
    boolean drag(double y, int button) { return typedEntryList.mouseDragged(y, button); }
    boolean release(int button) { return typedEntryList.mouseReleased(button); }
    boolean dragPicker(double y, int button) { return typedTypePicker.mouseDragged(y, button); }
    boolean releasePicker(int button) { return typedTypePicker.mouseReleased(button); }
    void closePicker() { typedTypePicker.reset(); typedTypePickerFrame = null; editorOverlays.close(); }
    void pickerWheel(double x, double y, double amount) { typedTypePicker.mouseScrolled(x, y, amount, scrollStep()); }
    boolean pickerKey(int key, int modifiers) {
        if (typedTypePicker.navigate(key, (modifiers & 1) != 0)) return true;
        if (key == 257 || key == 335) typedTypePicker.focusedRow().ifPresent(row -> clickPicker(row.visible().centerX(), row.visible().centerY(), 0));
        return true;
    }
    void wheel(double x, double y, double amount) {
        typedEntryList.mouseScrolled(host.frame(), typedEditorQuestId, typedEditorKind, typedListInputReady(), x, y, amount, scrollStep());
    }
    Optional<Component> narration() { return typedEntryList.narration(host.frame(), typedEditorQuestId, typedEditorKind, typedListInputReady()); }
    boolean key(int key, boolean control, boolean shift) {
        if (key == 256 && typedPropertySection.open()) { typedPropertySection.closeEditor(); return true; }
        if (key == 256 && open()) { close(); return true; }
        if (!typedListInputReady()) return false;
        if (control && key == 86) { pasteTypedClipboard(); return true; }
        if (control && key == 67) { typedEntryList.focusedEntry().ifPresent(this::copy); return true; }
        if (key == 258) return typedEntryList.focusNext(host.frame(), typedEditorQuestId, typedEditorKind, true, shift);
        if (key == 257 || key == 335 || key == 32) return typedEntryList.activateFocused(host.frame(), typedEditorQuestId, typedEditorKind, true)
                .map(intent -> { handleTypedListIntent(intent); return true; }).orElse(false);
        return false;
    }
    private <T> Consumer<T> returning(Consumer<T> result) {
        var token = identity.token();
        return value -> {
            var editor = ClientEditorState.get();
            if (identity.matches(token, editor.sessionId(), typedEditorQuestId) && EditorSessionLifecycle.canEdit(editor) && !editor.busy()) result.accept(value);
        };
    }
    private boolean typedEditorOpen;
    private QuestTypedEntryKind typedEditorKind = QuestTypedEntryKind.TASK;
    private ResourceLocation typedEditorQuestId;
    private final QuestTypedEntryListSection typedEntryList = new QuestTypedEntryListSection();
    private final EditorPickerList<ResourceLocation> typedTypePicker = new EditorPickerList<>();
    private QuestTypePickerModel.Frame typedTypePickerFrame;
    private Component typedEditorMessage;
    void render(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        if (typedPropertySection.open()) {
            typedEntryList.invalidate();
            typedPropertySection.render(graphics, quest, mouseX, mouseY);
            return;
        }
        int offset = host.drawerOffset();
        int left = detailLeft() + 10 + offset;
        int right = host.layout().width() - 10 + offset;
        var footer = typedEditorFooter();
        EditorListViewport vertical = typedEditorViewport(footer.add().top());
        UiRect screenClip = new UiRect(host.canvasRight(), topToolbarHeight(), host.layout().width(), host.layout().height() - bottomToolbarHeight());
        QuestTypedEntryListSection.Model model = new QuestTypedEntryListSection.Model(
                host.frame(), quest.id(), typedEditorKind,
                Component.translatable(typedEditorKind.headingKey()),
                typedEditorMessage, !ClientEditorState.get().busy(), typedEditorKind.size(quest),
                index -> typedEditorKind.value(quest, index).id(), row -> typedRowContent(quest, row.index()), canPasteTypedClipboard());
        QuestTypedEntryListSection.Layout sectionLayout = new QuestTypedEntryListSection.Layout(offset,
                new UiRect(detailLeft() + 4 + offset, topToolbarHeight() + 4,
                        host.layout().width() - 4 + offset, host.layout().height() - bottomToolbarHeight() - 4),
                new UiRect(left, vertical.top(), right, vertical.bottom()), screenClip, host.layout().width() - 8 + offset,
                footer.translated(offset, 0),
                detailsWidth() - 24);
        var result = typedEntryList.render(graphics, font, model, sectionLayout, TYPED_ROW_HEIGHT,
                host.elapsed(), scrollSmoothSpeed(), mouseX, mouseY);
        var hover = result.hover();
        host.hover(hover.item(), hover.tooltip());
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        if (typedPropertySection.open()) {
            if (!typedPropertySection.handleClick(mouseX, mouseY)) host.nativeClick(mouseX, mouseY);
            return true;
        }
        typedEntryList.mouseClicked(host.frame(), typedEditorQuestId, typedEditorKind,
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
            close();
            return;
        }
        QuestDefinition quest = host.selected();
        if (quest == null || intent.entryId() == null || typedEditorKind.entry(quest, intent.entryId()) == null) return;
        if (intent.action() == QuestTypedEntryListSection.Action.EDIT) {
            typedPropertySection.openEditor(quest, intent.entryId());
        } else {
            host.context(intent.entryId(), intent.x(), intent.y());
        }
    }

    private boolean typedListInputReady() {
        return typedEditorOpen && !typedPropertySection.open() && EditorSessionLifecycle.canEdit(ClientEditorState.get())
                && host.selected() != null && host.selected().id().equals(typedEditorQuestId)
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && host.panelReady();
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
            var details = RewardEntryDetails.resolve(Minecraft.getInstance(), view, presentation,
                    snbt.isBlank() ? ItemStack.EMPTY : host.item(reward.id(), snbt));
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
            ItemStack parsed = snbt.isBlank() ? ItemStack.EMPTY : host.item(task.id(), snbt);
            TaskPresentationContext context = new TaskPresentationContext(Minecraft.getInstance(), view, host.status(quest),
                    ClientQuestState.get().taskProgress().getOrDefault(task.id().toString(), 0L), parsed);
            ItemStack displayed = presentation.displayedItem(context);
            var icon = presentation.icon(view);
            // Author rows use the registered type sprite when there is no instance artwork or real item.
            if (icon.isEmpty() && displayed.isEmpty()
                    && (ClientTaskPresentationRegistry.hasTypeIcon(task.typeId())
                    || !ClientTaskPresentationRegistry.hasPresentation(task.typeId()))) {
                icon = java.util.Optional.of(ClientTaskPresentationRegistry.typeIcon(task.typeId()));
            }
            return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view), displayed, icon);
        }
        RewardDefinition reward = quest.rewards().get(index);
        var view = ApiViews.reward(reward);
        var presentation = ClientRewardPresentationRegistry.get(reward.typeId());
        String snbt = presentation.itemSnbt(view);
        ItemStack displayed = snbt.isBlank() ? ItemStack.EMPTY : host.item(reward.id(), snbt);
        var icon = presentation.icon(view);
        if (icon.isEmpty() && displayed.isEmpty()
                && (ClientRewardPresentationRegistry.hasTypeIcon(reward.typeId())
                || !ClientRewardPresentationRegistry.hasPresentation(reward.typeId()))) {
            icon = java.util.Optional.of(ClientRewardPresentationRegistry.typeIcon(reward.typeId()));
        }
        return new TypedRowPresentation(presentation.typeName(view), presentation.symbol(view), displayed, icon);
    }

    void open(QuestDefinition quest, QuestTypedEntryKind kind) {
        if (!EditorSessionLifecycle.canEdit(ClientEditorState.get())) return;
        host.closeOverlay();
        identity.open(ClientEditorState.get().sessionId(), quest.id());
        typedEditorOpen = true;
        typedEditorKind = kind;
        typedEditorQuestId = quest.id();
        resetTypedList();
        typedEditorMessage = null;
    }

    void close() {
        typedPropertySection.closeEditor();
        identity.close();
        typedEditorOpen = false;
        typedEditorQuestId = null;
        resetTypedList();
        typedEditorMessage = null;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.TYPED_TYPE_PICKER)) host.closeOverlay();
    }

    void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        typedTypePickerFrame = QuestTypePickerModel.frame(host.frame(), typedEditorKind);
        List<ResourceLocation> candidates = typedTypePickerFrame.entries().stream()
                .map(QuestTypePickerModel.Entry::typeId).toList();
        UiRect bounds = typedTypePickerBounds();
        typedTypePicker.advanceIconChoices(bounds, new UiRect(0, topToolbarHeight(), host.layout().width(), host.layout().height() - bottomToolbarHeight()),
                candidates.size(), candidates::get, host.elapsed(), scrollSmoothSpeed());
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
        if (!tooltip.isEmpty()) host.hover(null, tooltip);
        host.iconButton(graphics, typedTypePickerCloseBounds(), Component.literal("×"),
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

    private Component typedTypeDisplayName(ResourceLocation typeId) {
        QuestBookSnapshot snapshot = host.snapshot();
        ResourceLocation bookId = snapshot == null ? typeId : snapshot.book().id();
        ResourceLocation entryId = typedEditorQuestId == null ? typeId : typedEditorQuestId;
        return typedEditorKind == QuestTypedEntryKind.TASK
                ? ClientTaskPresentationRegistry.get(typeId).typeName(
                        new yourscraft.jasdewstarfield.brnquest.api.TaskView(bookId, entryId, typeId, Map.of(), false))
                : ClientRewardPresentationRegistry.get(typeId).typeName(
                        new yourscraft.jasdewstarfield.brnquest.api.RewardView(
                                bookId, entryId, typeId, Map.of(), "manual", false));
    }

    boolean clickPicker(double mouseX, double mouseY, int button) {
        if (button == 0 && typedTypePickerCloseBounds().contains(mouseX, mouseY)) {
            host.closeOverlay();
            return true;
        }
        if (button != 0 || !typedTypePickerBounds().contains(mouseX, mouseY)) {
            host.closeOverlay();
            return true;
        }
        if (typedTypePicker.mouseClicked(mouseX, mouseY, button)) return true;
        ResourceLocation typeId = typedTypePicker.entryAt(mouseX, mouseY).orElse(null);
        QuestTypePickerModel.Entry choice = typeId == null || typedTypePickerFrame == null ? null
                : typedTypePickerFrame.select(host.frame(), typedEditorKind, typeId).orElse(null);
        if (choice == null || ClientEditorState.get().busy()) return true;
        QuestBookSnapshot snapshot = host.snapshot();
        if (snapshot == null || typedEditorQuestId == null) return true;
        if (choice.route() == QuestTypePickerModel.Route.CHOICE_SELECTOR || choice.route() == QuestTypePickerModel.Route.ITEM_SELECTOR) {
            host.closeOverlay();
            host.children().creation(typedEditorKind == QuestTypedEntryKind.REWARD, typeId, returning(config -> {
                var current = host.snapshot();
                if (current == null || typedEditorQuestId == null) return;
                ResourceLocation id = EditorObjectIds.suggest(current.book(), typedEditorKind.idStem());
                host.mutate(new EditorMutationIntent("ADD_" + typedEditorKind.actionPrefix(), id, typedEditorQuestId, typeId,
                        "", 0, 0, 0, List.of(), Map.copyOf(config)));
            }));
            return true;
        }
        if (choice.route() == QuestTypePickerModel.Route.PROPERTY_FORM) {
            host.closeOverlay();
            typedPropertySection.openNewEditor(snapshot, typeId);
            return true;
        }
        ResourceLocation id = EditorObjectIds.suggest(snapshot.book(), typedEditorKind.idStem());
        host.mutate(new EditorMutationIntent("ADD_" + typedEditorKind.actionPrefix(), id, typedEditorQuestId, typeId,
                "", 0, 0, 0, List.of()));
        host.closeOverlay();
        return true;
    }

    private int typedEditorListTop() { return topToolbarHeight() + 34; }

    private EditorListViewport typedEditorViewport(int footerTop) {
        // Keep a small footer gap while deliberately exposing half of the next row. Validation
        // messages temporarily reclaim their original line so they never overlap list content.
        int footerGap = typedEditorMessage == null ? 6 : 16;
        return EditorListViewport.withHalfRowPreview(
                typedEditorListTop(), footerTop - footerGap, TYPED_ROW_HEIGHT);
    }

    private QuestTypedEntryListSection.Footer typedEditorFooter() {
        int bottom = host.layout().height() - bottomToolbarHeight() - 6;
        return QuestTypedEntryListSection.footer(new UiRect(detailLeft() + 10, typedEditorListTop(), host.layout().width() - 10, bottom), font);
    }

    private UiRect typedTypePickerBounds() {
        int desiredHeight = EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT * 6 + 4;
        int maximumHeight = Math.max(EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT + 4,
                host.layout().height() - topToolbarHeight() - bottomToolbarHeight() - 16);
        return host.layout().centeredDialog(430, 260, 20, Math.min(desiredHeight, maximumHeight)).intersection(pickerScreenBounds());
    }

    private UiRect typedTypePickerCloseBounds() {
        UiRect picker = typedTypePickerBounds();
        return new UiRect(picker.right() - 18, picker.top() + 2, picker.right() - 2, picker.top() + 16);
    }

    void copy(ResourceLocation entryId) {
        var quest = host.selected();
        var entry = quest == null ? null : typedEditorKind.entry(quest, entryId);
        if (entry == null) return;
        try {
            EditorTypedClipboard.copy(host.serverContext(), entry.task() != null
                    ? yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(entry.task())
                    : yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(entry.reward()));
            typedEditorMessage = Component.translatable("screen.brnquest.clipboard.copied");
        } catch (IllegalArgumentException exception) {
            typedEditorMessage = Component.translatable("screen.brnquest.clipboard.too_large");
        }
    }

    private boolean canPasteTypedClipboard() {
        var snapshot = host.snapshot();
        return snapshot != null && EditorTypedClipboard.get(host.serverContext(), snapshot.book().id(),
                typedEditorKind == QuestTypedEntryKind.TASK).isPresent();
    }

    private void pasteTypedClipboard() {
        if (ClientEditorState.get().busy() || !typedListInputReady()) return;
        var snapshot = host.snapshot();
        if (snapshot == null) return;
        EditorTypedClipboard.get(host.serverContext(), snapshot.book().id(), typedEditorKind == QuestTypedEntryKind.TASK)
                .ifPresentOrElse(value -> host.mutate(new EditorMutationIntent("PASTE_" + typedEditorKind.actionPrefix(),
                        EditorObjectIds.suggest(snapshot.book(), typedEditorKind.idStem() + "_copy"), typedEditorQuestId, null,
                        "", 0, 0, 0, List.of(), Map.of("snapshot", value.encode()))),
                        () -> typedEditorMessage = Component.translatable("screen.brnquest.clipboard.unavailable"));
    }

    private record TypedRowPresentation(Component typeName, String symbol, ItemStack stack, Optional<EditorIcon> icon) {}
    private int detailLeft() { return host.layout().detailLeft(); }
    private int detailsWidth() { return host.layout().detailsWidth(); }
    private int topToolbarHeight() { return host.layout().topToolbarHeight(); }
    private int bottomToolbarHeight() { return host.layout().bottomToolbarHeight(); }
    private UiRect pickerScreenBounds() { return new UiRect(0, topToolbarHeight(), host.layout().width(), host.layout().height() - bottomToolbarHeight()); }
    private static double scrollStep() { return BrnQuestClientConfig.VALUES.scrollStep.get(); }
    private static double scrollSmoothSpeed() { return BrnQuestClientConfig.VALUES.smoothSpeed.get(); }
}
