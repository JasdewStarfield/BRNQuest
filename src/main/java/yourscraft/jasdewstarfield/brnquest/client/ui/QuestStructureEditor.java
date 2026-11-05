package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorLocalizedText;
import yourscraft.jasdewstarfield.brnquest.author.LocalizedSingleLineEdits;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorFormFields;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestIconEditorRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextureSelector;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.DisplayOverrides;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

/** One local working copy for chapter/group metadata, creation and their nested selectors. */
final class QuestStructureEditor {
    interface Host {
        QuestBookSnapshot snapshot();
        QuestScreenLayout layout();
        String serverContext();
        String questTitle(QuestDefinition quest);
        String localizedTitle(String kind, ResourceLocation id, String fallback);
        EditorChildScreens children();
        void focus(EditorTextField field);
        void closeOverlay();
        void clearHover();
        void tooltip(List<Component> text);
        void text(GuiGraphics g, UiRect bounds, Component label, Component tooltip, boolean enabled, EditorButton.Tone tone, int x, int y);
        void icon(GuiGraphics g, UiRect bounds, Component glyph, Component label, boolean enabled, boolean dangerous, int x, int y);
        void action(GuiGraphics g, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone, int x, int y);
        void nativeClick(double x, double y, int button);
        boolean mutate(EditorMutationIntent intent);
    }
    private final Host host;
    private final EditorOverlayHost editorOverlays;
    private final EditorFormIdentity identity = new EditorFormIdentity();
    private Font font;
    private List<Object> baseline = List.of();
    QuestStructureEditor(Host host, EditorOverlayHost overlays) { this.host = host; editorOverlays = overlays; }
    void bind(Font font, Consumer<EditorTextField> register) { this.font = font; chapterIconRow = null; structureFields.bind(font, register); }
    boolean changed() { return open() && !baseline.equals(values()); }
    Kind kind() { return structureFormKind; }
    private List<Object> values() {
        if (structureLocalizedTitle != null) structureLocalizedTitle.remember(structureFields.field("title").getValue());
        return List.of(structureFields.field("id").getValue(),
                structureLocalizedTitle == null ? structureFields.field("title").getValue() : structureLocalizedTitle.changes(),
                Objects.toString(structureFormParent, ""), chapterIconMode, structureDefaults, structureArtwork,
                structureHideDependencyLines, structureChapterProgress, Objects.toString(structureConsumeItems, "default"),
                Objects.toString(structureAutofocusQuestId, ""), structureFields.field("group_description").getValue(),
                structureFields.field(chapterIconMode == IconEditorMode.ITEM ? "chapter_item" : "texture").getValue());
    }
    private <T> Consumer<T> returning(Consumer<T> result) {
        var token = identity.token();
        return value -> {
            var editor = ClientEditorState.get();
            if (identity.matches(token, editor.sessionId(), structureFormTarget) && editor.editing() && !editor.busy()) result.accept(value);
        };
    }
    private void openLocalePicker(EditorLocalizedText text, EditorTextField field) {
        if (text == null) return;
        text.remember(field.getValue());
        host.children().locale(text.locales(), text.locale(), returning(locale -> {
            text.select(locale); structureFields.field("title").setValue(text.value());
        }));
    }
    private String structureChapterIcon = "";
    private QuestIconEditorRow.Layout chapterIconRow;
    private IconEditorMode chapterIconMode = IconEditorMode.ITEM;
    private final EditorFormFields<String> structureFields = new EditorFormFields<String>()
            .define("id", "screen.brnquest.editor.structure.id", 256)
            .define("title", "screen.brnquest.editor.structure.title", 256)
            .define("texture", "screen.brnquest.editor.quest.icon_mode.texture", 256)
            .define("chapter_item", "screen.brnquest.editor.quest.icon", 256)
            .define("group_description", "screen.brnquest.editor.group.description", 2048);
    private EditorLocalizedText structureLocalizedTitle;
    private Boolean structureConsumeItems;
    private boolean structureHideDependencyLines;
    private DisplayOverrides.ProgressVisibility structureChapterProgress = DisplayOverrides.ProgressVisibility.INHERIT;
    private ResourceLocation structureAutofocusQuestId;
    private yourscraft.jasdewstarfield.brnquest.data.CanvasScene structureArtwork = yourscraft.jasdewstarfield.brnquest.data.CanvasScene.EMPTY;
    private QuestCreationDefaults structureDefaults = QuestCreationDefaults.EMPTY;
    private Kind structureFormKind = Kind.NONE;
    private ResourceLocation structureFormTarget;
    private ResourceLocation structureFormParent;
    private double structureFormX;
    private double structureFormY;
    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        host.clearHover();
        UiRect form = structureFormBounds();
        if (structureMetadataForm()) {
            renderChapterProperties(graphics, mouseX, mouseY);
            return;
        }
        graphics.fill(0, 0, host.layout().width(), host.layout().height(), 0x88000000);
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
                (g, bounds, label, enabled, tone) -> host.text(
                        g, bounds, label, null, enabled, tone, mouseX, mouseY));
    }

    private void renderChapterProperties(GuiGraphics graphics, int mouseX, int mouseY) {
        UiRect form = structureFormBounds();
        graphics.fill(0, 0, host.layout().width(), host.layout().height(), 0x88000000);
        boolean enabled = !ClientEditorState.get().busy();
        var rows = new ArrayList<EditorPropertyPanel.RowContent>();
        // Identity is informational until a dedicated reference-migration operation is supported.
        structureFields.field("id").hide();
        rows.add(EditorPropertyPanel.readOnly(font, "screen.brnquest.editor.structure.readonly_id",
                Component.literal(structureFields.field("id").getValue()), 64));
        UiRect idRow = new UiRect(form.left() + 12, form.top() + 32, form.right() - 12, form.top() + 52);
        if (idRow.contains(mouseX, mouseY)) host.tooltip(List.of(
                Component.literal(structureFields.field("id").getValue()),
                Component.translatable("screen.brnquest.editor.structure.id_help")));
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
        if (structureFormKind == Kind.RENAME_GROUP) {
            rows.add(EditorPropertyPanel.text(font, structureFields.field("group_description"),
                    "screen.brnquest.editor.group.description", 64, null, enabled));
        } else rows.add((g, x, y, w) -> {
            var group = host.snapshot().book().chapterGroups().stream()
                    .filter(value -> value.id().equals(structureFormParent)).findFirst().orElse(null);
            Component name = group == null ? Component.literal(structureFormParent == null ? "" : structureFormParent.toString())
                    : Component.literal(host.localizedTitle("chapter_group", group.id(), group.title()));
            host.text(g, chapterGroupBounds(),
                    Component.translatable("screen.brnquest.editor.structure.group_value", name), name,
                    enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            Component focusName = host.snapshot().book().chapters().stream()
                    .filter(c -> c.id().equals(structureFormTarget)).flatMap(c -> c.quests().stream())
                    .filter(q -> q.id().equals(structureAutofocusQuestId)).findFirst()
                    .<Component>map(q -> NativeText.component(host.questTitle(q)))
                    .orElse(Component.translatable("screen.brnquest.chapter.autofocus.none"));
            host.text(g, chapterAutofocusBounds(),
                    Component.translatable("screen.brnquest.chapter.autofocus.value", focusName),
                    structureAutofocusQuestId == null
                            ? Component.translatable("screen.brnquest.chapter.autofocus.unset_help")
                            : Component.translatable("screen.brnquest.chapter.autofocus.help", focusName),
                    enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        });
        if (structureFormKind == Kind.RENAME_CHAPTER) rows.add((g, x, y, w) -> {
            // Both child editors share one row; their staged values still commit with this form.
            host.text(g, chapterDefaultsBounds(), Component.translatable("screen.brnquest.defaults.chapter_open"),
                    null, enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            host.text(g, chapterBackgroundBounds(),
                    EditorCanvasScreen.label("backgrounds"),
                    EditorCanvasScreen.label("backgrounds_help"), enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        });
        if (structureFormKind == Kind.RENAME_CHAPTER) rows.add((g, x, y, w) ->
                host.text(g, chapterProgressBounds(),
                        Component.translatable("screen.brnquest.display.chapter_progress", Component.translatable(
                                "screen.brnquest.display.progress." + structureChapterProgress.name().toLowerCase(java.util.Locale.ROOT))),
                        Component.translatable("screen.brnquest.display.override_help"), enabled,
                        EditorButton.Tone.NEUTRAL, mouseX, mouseY));
        EditorPropertyPanel.renderGraystone(graphics, font,
                new EditorPropertyPanel.Layout(form, form.left() + 12, form.width() - 24,
                        form.top() + 10, form.top() + 32, structureFormKind == Kind.RENAME_CHAPTER ? 26 : 32),
                Component.translatable(structureFormKind == Kind.RENAME_GROUP
                        ? "screen.brnquest.editor.structure.group_properties" : "screen.brnquest.editor.structure.chapter_properties"), 0xFFFFFFFF, rows,
                new EditorPropertyPanel.Footer(structureFormCancelBounds(), structureFormDoneBounds(),
                        Component.translatable("gui.done"), enabled && chapterTextureValid() && structureTitleValid(),
                        EditorButton.Tone.PRIMARY),
                (g, bounds, label, active, tone) -> host.text(
                        g, bounds, label, null, active, tone, mouseX, mouseY));
        host.icon(graphics, chapterIconClearBounds(), Component.literal("×"),
                Component.translatable("screen.brnquest.editor.structure.clear_icon"),
                enabled && !chapterIconValue().isEmpty(), false, mouseX, mouseY);
    }

    private boolean structureTitleValid() {
        return !structureFields.field("title").getValue().isBlank()
                || structureLocalizedTitle != null && !structureLocalizedTitle.placeholder().isBlank();
    }

    private UiRect structureTitleBounds() {
        UiRect form = structureFormBounds();
        return yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout.row(
                form.left() + 12, structureRowTop(1), form.width() - 24, 64).field();
    }

    private int structureRowTop(int index) {
        return structureFormBounds().top() + 32 + index * (structureFormKind == Kind.RENAME_CHAPTER ? 26 : 32);
    }

    private UiRect chapterBackgroundBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, structureRowTop(4), form.right() - 12, structureRowTop(4) + 20);
    }

    private UiRect chapterDefaultsBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, structureRowTop(4), form.centerX() - 4, structureRowTop(4) + 20);
    }

    private UiRect chapterProgressBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, structureRowTop(5), form.right() - 12, structureRowTop(5) + 20);
    }

    private UiRect chapterGroupBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, structureRowTop(3), form.centerX() - 4, structureRowTop(3) + 20);
    }

    private UiRect chapterAutofocusBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, structureRowTop(3), form.right() - 12, structureRowTop(3) + 20);
    }

    private void openChapterAutofocusPicker() {
        var choices = new ArrayList<EditorChoiceScreen.Choice>();
        choices.add(new EditorChoiceScreen.Choice("", Component.translatable("screen.brnquest.chapter.autofocus.none")));
        host.snapshot().book().chapters().stream().filter(c -> c.id().equals(structureFormTarget))
                .flatMap(c -> c.quests().stream()).forEach(q -> choices.add(new EditorChoiceScreen.Choice(
                        q.id().toString(), NativeText.component(host.questTitle(q)).copy().append(Component.literal(" [" + q.id() + "]")))));
        host.children().choice( Component.translatable("screen.brnquest.chapter.autofocus"),
                choices, java.util.Objects.toString(structureAutofocusQuestId, ""),
                returning(id -> structureAutofocusQuestId = id.isEmpty() ? null : ResourceLocation.parse(id)));
    }

    private void openChapterGroupPicker() {
        var choices = host.snapshot().book().chapterGroups().stream()
                .sorted(java.util.Comparator.comparingInt(ChapterGroupDefinition::order)
                        .thenComparing(group -> group.id().toString()))
                .map(group -> new EditorChoiceScreen.Choice(group.id().toString(),
                        Component.literal(host.localizedTitle("chapter_group", group.id(), group.title())))).toList();
        host.children().choice( Component.translatable("screen.brnquest.editor.structure.choose_group"),
                choices, structureFormParent == null ? "" : structureFormParent.toString(),
                returning(id -> structureFormParent = ResourceLocation.parse(id)));
    }

    private UiRect chapterIconClearBounds() {
        UiRect form = structureFormBounds();
        // Match the selector row's shared 18-pixel field host.layout().height() for drawing and clicks.
        int top = structureRowTop(2);
        return new UiRect(form.right() - 32, top, form.right() - 12,
                top + EditorPropertyFormLayout.FIELD_HEIGHT);
    }

    private EditorTextField chapterIconInput() {
        return structureFields.field(chapterIconMode == IconEditorMode.ITEM ? "chapter_item" : "texture");
    }

    private String chapterIconValue() {
        if (chapterIconMode == IconEditorMode.ITEM) {
            String id = chapterIconInput().getValue().strip();
            if (id.equals(iconItemId(structureChapterIcon))) return structureChapterIcon;
            ResourceLocation parsed = ResourceLocation.tryParse(id);
            if (parsed == null || Minecraft.getInstance() == null || Minecraft.getInstance().level == null || !BuiltInRegistries.ITEM.containsKey(parsed)) return "";
            return BuiltInRegistries.ITEM.get(parsed).getDefaultInstance().save(Minecraft.getInstance().level.registryAccess()).toString();
        }
        String path = structureFields.field("texture").getValue().strip();
        return path.isEmpty() ? "" : "texture:" + path;
    }

    private boolean chapterTextureValid() {
        String value = chapterIconInput().getValue().strip();
        ResourceLocation id = ResourceLocation.tryParse(value);
        return value.isEmpty() || (id != null && (chapterIconMode == IconEditorMode.TEXTURE || BuiltInRegistries.ITEM.containsKey(id)));
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (button == 0 && structureMetadataForm()
                && !ClientEditorState.get().busy()) {
            if (structureFormKind == Kind.RENAME_CHAPTER && chapterProgressBounds().contains(mouseX, mouseY)) {
                // The generic popup replaces and closes the active modal. Suspend this form instead,
                // so the selected value and all other pending chapter edits survive until confirmation.
                var choices = List.of("inherit", "show", "hide").stream().map(value ->
                        new EditorChoiceScreen.Choice(value, Component.translatable("screen.brnquest.display.progress." + value))).toList();
                host.children().choice(
                        Component.translatable("brnquest.configuration.showChapterProgress"), choices,
                        structureChapterProgress.name().toLowerCase(java.util.Locale.ROOT),
                        returning(value -> structureChapterProgress = DisplayOverrides.parseProgress(value)));
                return true;
            }
            if (EditorLocalizedText.languageBounds(structureTitleBounds()).contains(mouseX, mouseY)) {
                openLocalePicker(structureLocalizedTitle, structureFields.field("title"));
                return true;
            }
            if (structureFormKind == Kind.RENAME_CHAPTER && chapterAutofocusBounds().contains(mouseX, mouseY)) {
                openChapterAutofocusPicker();
                return true;
            }
            if (structureFormKind == Kind.RENAME_CHAPTER) {
                if (chapterBackgroundBounds().contains(mouseX, mouseY)) {
                    // Child apply only updates the outer working copy; canceling chapter properties discards it.
                    var childToken = identity.token();
                    var result = returning((yourscraft.jasdewstarfield.brnquest.data.CanvasScene scene) -> structureArtwork = scene.backgrounds());
                    host.children().backgrounds(host.serverContext(), host.snapshot().book().id(), structureArtwork,
                            value -> {
                                if (!identity.matches(childToken, ClientEditorState.get().sessionId(), structureFormTarget)) return false;
                                result.accept(value); return true;
                            });
                    return true;
                }
            }
            if (structureFormKind == Kind.RENAME_CHAPTER && chapterDefaultsBounds().contains(mouseX, mouseY)) {
                host.children().defaults( structureDefaults, host.snapshot().book().questDefaults(),
                        returning(value -> structureDefaults = value), structureHideDependencyLines, returning(value -> structureHideDependencyLines = value),
                        structureConsumeItems, host.snapshot().book().settings().consumeItems(),
                        returning(value -> structureConsumeItems = value));
                return true;
            }
            if (structureFormKind == Kind.RENAME_CHAPTER && chapterGroupBounds().contains(mouseX, mouseY)) {
                openChapterGroupPicker();
                return true;
            }
            if (chapterIconRow != null && chapterIconRow.mode().contains(mouseX, mouseY)) {
                // Each mode keeps its local candidate while toggling; only the active candidate is saved.
                chapterIconMode = chapterIconMode == IconEditorMode.ITEM ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
                host.focus(null);
                return true;
            }
            if (chapterIconMode == IconEditorMode.TEXTURE && chapterIconRow != null && chapterIconRow.picker().contains(mouseX, mouseY)) {
                host.children().texture(returning(structureFields.field("texture")::setValue));
                return true;
            }
            if (chapterIconMode == IconEditorMode.ITEM && chapterIconRow != null && chapterIconRow.picker().contains(mouseX, mouseY)) {
                host.children().items(returning(stack -> {
                    if (Minecraft.getInstance() != null && Minecraft.getInstance().level != null && !stack.isEmpty())
                    {
                        structureChapterIcon = stack.copyWithCount(1).save(Minecraft.getInstance().level.registryAccess()).toString();
                        structureFields.field("chapter_item").setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    }
                }));
                return true;
            }
            if (chapterIconClearBounds().contains(mouseX, mouseY)) {
                chapterIconInput().setValue("");
                if (chapterIconMode == IconEditorMode.ITEM) structureChapterIcon = "";
                return true;
            }
        }
        if (button == 0 && structureFormCancelBounds().contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (button == 0 && structureFormDoneBounds().contains(mouseX, mouseY)) {
            submitStructureForm();
            return true;
        }
        host.nativeClick(mouseX, mouseY, button);
        return true;
    }

    void open(Kind kind, ResourceLocation target, ResourceLocation parent,
                                   double x, double y) {
        host.closeOverlay();
        QuestBookDefinition book = host.snapshot().book();
        identity.open(ClientEditorState.get().sessionId(), target);
        structureFormKind = kind;
        structureFormTarget = target;
        structureFormParent = parent;
        // Pointer-created quests use the same nearest-intersection rule as node dragging.
        structureFormX = kind == Kind.ADD_QUEST ? QuestViewportMath.snapQuestCoordinate(x) : x;
        structureFormY = kind == Kind.ADD_QUEST ? QuestViewportMath.snapQuestCoordinate(y) : y;
        ResourceLocation id = target;
        String title = "";
        if (kind == Kind.RENAME_GROUP) {
            ChapterGroupDefinition group = book.chapterGroups().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (group != null) {
                structureLocalizedTitle = new EditorLocalizedText(book.localization(),
                        BookText.structureTitleKey(book, "chapter_group", group.id()), group.title(), Minecraft.getInstance().getLanguageManager().getSelected());
                title = structureLocalizedTitle.value();
                initializeStructureIcon(group.icon());
                structureFields.field("group_description").setValue(group.description());
            }
        } else if (kind == Kind.RENAME_CHAPTER) {
            ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(target))
                    .findFirst().orElse(null);
            if (chapter != null) {
                structureLocalizedTitle = new EditorLocalizedText(book.localization(),
                        BookText.structureTitleKey(book, "chapter", chapter.id()), chapter.title(), Minecraft.getInstance().getLanguageManager().getSelected());
                title = structureLocalizedTitle.value();
                structureFormParent = chapter.groupId();
                initializeStructureIcon(chapter.icon());
                structureDefaults = chapter.questDefaults();
                structureArtwork = chapter.canvasScene().backgrounds();
                structureConsumeItems = chapter.consumeItems();
                structureHideDependencyLines = chapter.defaultHideDependencyLines();
                structureChapterProgress = DisplayOverrides.progress(chapter.extensions());
                structureAutofocusQuestId = chapter.autofocusQuestId();
            }
        } else {
            id = EditorObjectIds.suggest(book, kind.idStem());
            if (kind == Kind.COPY_QUEST && target != null) {
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
        baseline = values();
        host.focus(kind.createsStableId() ? structureFields.field("id") : structureFields.field("title"));
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
        QuestBookDefinition book = host.snapshot().book();
        ResourceLocation id = ResourceLocation.tryParse(structureFields.field("id").getValue());
        if (id == null || !structureTitleValid()) return;
        String title = structureFields.field("title").getValue();
        if (structureLocalizedTitle != null) structureLocalizedTitle.remember(title);
        switch (structureFormKind) {
            case ADD_GROUP -> host.mutate(new EditorMutationIntent("ADD_GROUP", id, null, null, title,
                    book.chapterGroups().size(), 0, 0, List.of()));
            case RENAME_GROUP -> {
                ChapterGroupDefinition group = book.chapterGroups().stream()
                        .filter(value -> value.id().equals(structureFormTarget)).findFirst().orElse(null);
                if (!chapterTextureValid() || group == null || !host.mutate(new EditorMutationIntent("UPDATE_GROUP", group.id(), null, null, title,
                        group.order(), 0, 0, List.of(), localizedConfig(structureLocalizedTitle, "title", Map.of("icon", chapterIconValue(),
                                "description", structureFields.field("group_description").getValue()))))) return;
            }
            case ADD_CHAPTER -> host.mutate(new EditorMutationIntent("ADD_CHAPTER", id, structureFormParent, null, title,
                    (int) book.chapters().stream().filter(value -> value.groupId().equals(structureFormParent)).count(),
                    0, 0, List.of()));
            case RENAME_CHAPTER -> {
                if (!chapterTextureValid()) return;
                ChapterDefinition chapter = book.chapters().stream()
                        .filter(value -> value.id().equals(structureFormTarget)).findFirst().orElse(null);
                if (chapter == null || !host.mutate(new EditorMutationIntent("UPDATE_CHAPTER", chapter.id(), structureFormParent, null, title,
                        chapter.groupId().equals(structureFormParent) ? chapter.order() : book.chapters().stream()
                                .filter(value -> value.groupId().equals(structureFormParent))
                                .mapToInt(ChapterDefinition::order).max().orElse(-1) + 1, 0, 0, List.of(), localizedConfig(structureLocalizedTitle, "title", Map.of("backgrounds", structureArtwork.encode(), "icon", chapterIconValue(), "quest_defaults", structureDefaults.toJson().toString(), "default_consume_items", java.util.Objects.toString(structureConsumeItems, "default"), "autofocus_id", java.util.Objects.toString(structureAutofocusQuestId, ""), "default_hide_dependency_lines", Boolean.toString(structureHideDependencyLines), "chapter_progress", structureChapterProgress.name().toLowerCase(java.util.Locale.ROOT)))))) return;
            }
            case ADD_QUEST -> host.mutate(new EditorMutationIntent("ADD_QUEST", id, structureFormParent, null, title, 0,
                    structureFormX, structureFormY, List.of()));
            case COPY_QUEST -> host.mutate(new EditorMutationIntent("COPY_QUEST", id, structureFormParent, structureFormTarget, title, 0,
                    structureFormX, structureFormY, List.of()));
            case NONE -> { }
        }
        close();
    }

    void close() {
        identity.close();
        structureFormKind = Kind.NONE;
        structureFields.field("title").setSuggestion(null);
        structureLocalizedTitle = null;
        structureFormTarget = null;
        structureFormParent = null;
        host.focus(null);
        for (EditorTextField field : List.of(structureFields.field("id"), structureFields.field("title"), structureFields.field("texture"), structureFields.field("chapter_item"), structureFields.field("group_description"))) {
            if (field == null) continue;
            field.hide();
        }
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM)) editorOverlays.close();
    }

    boolean open() {
        return editorOverlays.isOpen(EditorOverlayHost.Kind.STRUCTURE_FORM);
    }

    private boolean structureMetadataForm() {
        return structureFormKind == Kind.RENAME_CHAPTER || structureFormKind == Kind.RENAME_GROUP;
    }

    private UiRect structureFormBounds() {
        if (structureMetadataForm())
            return host.layout().centeredDialog(420, 300, 20, structureFormKind == Kind.RENAME_CHAPTER ? 216 : 184);
        return host.layout().centeredDialog(380, 260, 20, 126);
    }

    private UiRect structureFormCancelBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.left() + 12, form.bottom() - 28, form.centerX() - 4, form.bottom() - 8);
    }

    private UiRect structureFormDoneBounds() {
        UiRect form = structureFormBounds();
        return new UiRect(form.centerX() + 4, form.bottom() - 28, form.right() - 12, form.bottom() - 8);
    }

    private void showLocalizedPlaceholder(EditorTextField field, EditorLocalizedText text) {
        // Native suggestions are gray and remain visible with focus, but are never selected or submitted.
        field.setSuggestion(field.getValue().isEmpty() && text != null
                ? font.plainSubstrByWidth(text.placeholder(), Math.max(0, field.getWidth() - 8)) : null);
    }
    private void renderLocaleButton(GuiGraphics graphics, EditorLocalizedText text, UiRect row, int mouseX, int mouseY) {
        if (text == null) return;
        String source = text.source();
        Component tooltip = Component.translatable(source.isEmpty() ? "screen.brnquest.editor.locale.native"
                : source.equals(text.locale()) ? "screen.brnquest.editor.locale.current" : "screen.brnquest.editor.locale.fallback", source);
        host.text(graphics, EditorLocalizedText.languageBounds(row), Component.literal(text.locale()),
                tooltip, !ClientEditorState.get().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }
    private Map<String, String> localizedConfig(EditorLocalizedText text, String field, Map<String, String> metadata) {
        var result = new java.util.TreeMap<String, String>(metadata);
        result.put(LocalizedSingleLineEdits.FIELD, field);
        text.changes().forEach((locale, value) -> result.put(LocalizedSingleLineEdits.PREFIX + locale, value));
        return Map.copyOf(result);
    }
    private QuestIconEditorRow.Layout renderTextureSelector(GuiGraphics graphics, int left, int top, int rowWidth,
            EditorTextField input, IconEditorMode mode, int mouseX, int mouseY) {
        return EditorTextureSelector.render(graphics, font, left, top, rowWidth, input, mode == IconEditorMode.TEXTURE,
                !ClientEditorState.get().busy(),
                (g, bounds, label, enabled, tone) -> host.text(g, bounds, label, null, enabled, tone, mouseX, mouseY),
                (g, bounds, definition, enabled, tone) -> host.action(g, bounds, definition, enabled, tone, mouseX, mouseY));
    }
    enum Kind {
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

        Kind(String translationKey, String idStem, boolean createsStableId) {
            this.translationKey = translationKey;
            this.idStem = idStem;
            this.createsStableId = createsStableId;
        }

        String translationKey() { return translationKey; }
        String idStem() { return idStem; }
        boolean createsStableId() { return createsStableId; }
    }
    private enum IconEditorMode { ITEM, TEXTURE }
}
