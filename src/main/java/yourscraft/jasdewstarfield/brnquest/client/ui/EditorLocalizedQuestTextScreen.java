package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.mixin.MultiLineEditBoxAccessor;
import yourscraft.jasdewstarfield.brnquest.client.mixin.MultilineTextFieldAccessor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextRenderer;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystoneSurface;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupSource;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.RecipeLookupTarget;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.client.ui.document.DocumentLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.document.DocumentView;
import yourscraft.jasdewstarfield.brnquest.data.BookLocalization;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Locale-safe source editor whose preview uses the same parser, layout engine and renderer as quest details. */
public final class EditorLocalizedQuestTextScreen extends Screen implements RecipeLookupSource {
    public record Value(String locale, String title, String subtitle, String description,
                        DocumentFormat descriptionFormat) {}

    private static final int WIDE_LAYOUT_MINIMUM = 620;
    private static final int TOOLBAR_COLUMNS = 6;
    private static final MarkdownSourceEdits.Tool[] TOOLS = MarkdownSourceEdits.Tool.values();
    private static final int CONTENT_TOOL_COUNT = 2;
    private static final EditorIcon TEXTURE_TOOL_ICON = QuestActionIcons.named("search");
    private static final EditorIcon ITEM_TOOL_ICON = EditorIcon.sprite(
            ResourceLocation.parse("brnquest:editor/type/item"));
    private final EditorButtonInput buttons = new EditorButtonInput();
    private final Screen parent;
    private final Predicate<Value> consumer;
    private final Runnable discardRecovery;
    private final List<String> locales;
    private final LocalizedQuestTextDrafts drafts;
    private final DocumentView documentView = new DocumentView();
    private final MarkdownPreviewState preview = new MarkdownPreviewState();
    private final EditorSmoothScroll previewScroll = new EditorSmoothScroll();
    private int localeIndex;
    private EditBox titleEditor;
    private EditBox subtitleEditor;
    private EditBox localeEditor;
    private MultiLineEditBox descriptionEditor;
    private DocumentFormat descriptionFormat = DocumentFormat.PLAIN;
    private boolean loadingWidgets;
    private boolean previewTab;
    private boolean submitted;
    private Component formatNotice;
    private Component applyIssue;
    private DocumentView.Prepared renderedPreview;
    private UiRect renderedPreviewBounds;
    private int renderedDocumentX;
    private int renderedDocumentY;
    private long lastRenderMillis;
    private int reopenSelectionStart = -1;
    private int reopenSelectionEnd = -1;
    private Component reopenNotice;

    /** Compatibility constructor for callers that do not need retry recovery. */
    public EditorLocalizedQuestTextScreen(Screen parent, BookLocalization localization, QuestDefinition quest,
                                          String clientLocale, Consumer<Value> consumer) {
        this(parent, localization, quest, clientLocale, value -> {
            consumer.accept(value);
            return true;
        }, null, () -> {});
    }

    public EditorLocalizedQuestTextScreen(Screen parent, BookLocalization localization, QuestDefinition quest,
                                          String clientLocale, Predicate<Value> consumer, Value recovery,
                                          Runnable discardRecovery) {
        super(Component.translatable("screen.brnquest.editor.localized_text.title"));
        this.parent = parent;
        this.consumer = consumer;
        this.discardRecovery = discardRecovery == null ? () -> {} : discardRecovery;
        this.drafts = new LocalizedQuestTextDrafts(localization, quest);
        this.drafts.seed(recovery);
        LinkedHashSet<String> available = new LinkedHashSet<>();
        available.add(BookLocalization.normalizeLocale(recovery == null ? clientLocale : recovery.locale()));
        available.add(BookLocalization.normalizeLocale(clientLocale));
        available.add(localization.fallbackLocale());
        available.addAll(localization.translations().keySet().stream().sorted().toList());
        this.locales = new ArrayList<>(available);
    }

    @Override
    protected void init() {
        rememberWidgets();
        buttons.begin();
        buttons.clearFocus();
        super.init();
        UiRect panel = panelBounds();
        titleEditor = new EditBox(font, panel.left() + 88, panel.top() + 46, panel.width() - 100, 20,
                Component.translatable("screen.brnquest.editor.quest.title"));
        titleEditor.setMaxLength(256);
        subtitleEditor = new EditBox(font, panel.left() + 88, panel.top() + 72, panel.width() - 100, 20,
                Component.translatable("screen.brnquest.editor.quest.subtitle"));
        subtitleEditor.setMaxLength(256);
        UiRect newLocale = newLocaleBounds();
        localeEditor = new EditBox(font, newLocale.left(), newLocale.top(), newLocale.width(), newLocale.height(),
                Component.translatable("screen.brnquest.editor.localized_text.new_locale"));
        localeEditor.setMaxLength(32);
        localeEditor.setHint(Component.translatable("screen.brnquest.editor.localized_text.new_locale_hint"));
        UiRect source = sourceBounds();
        descriptionEditor = new MultiLineEditBox(font, source.left(), source.top(), source.width(), source.height(),
                Component.translatable("screen.brnquest.editor.multiline.placeholder"),
                Component.translatable("screen.brnquest.editor.quest.description"));
        descriptionEditor.setCharacterLimit(32_768);
        descriptionEditor.setValueListener(this::descriptionChanged);
        addRenderableWidget(titleEditor);
        addRenderableWidget(subtitleEditor);
        addRenderableWidget(localeEditor);
        addRenderableWidget(descriptionEditor);
        loadLocale(true);
        restoreChildSelection();
        updateSourceVisibility();
        setFocused(sourceVisible() ? descriptionEditor : titleEditor);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void tick() {
        parent.tick();
        super.tick();
        if (preview.advance(Util.getMillis())) previewScroll.snap(0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        UiRect panel = panelBounds();
        GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
        graphics.drawCenteredString(font, title, panel.centerX(), panel.top() + 9, 0xFFFFFFFF);
        renderHeader(graphics, panel, mouseX, mouseY);
        Component hovered = renderDocumentControls(graphics, mouseX, mouseY);
        renderPreview(graphics, mouseX, mouseY);
        renderMessages(graphics, panel);
        buttons.render(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null), true, false,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, applyBounds(),
                EditorButton.Definition.text(Component.translatable("gui.done"), null), true, false,
                EditorButton.Tone.PRIMARY, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        URI link = previewLinkAt(mouseX, mouseY);
        if (link != null) hovered = Component.literal(link.toString());
        ItemStack item = previewItemAt(mouseX, mouseY);
        if (!item.isEmpty()) graphics.renderTooltip(font, item, mouseX, mouseY);
        else if (hovered != null)
            graphics.renderTooltip(font, font.split(hovered, Math.min(280, width - 20)), mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, UiRect panel, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.localized_text.locale"),
                panel.left() + 12, panel.top() + 27, GraystonePalette.SECONDARY, false);
        buttons.render(graphics, font, localeBounds(),
                EditorButton.Definition.text(Component.literal(locales.get(localeIndex)),
                        Component.translatable("screen.brnquest.editor.localized_text.switch_locale")),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, addLocaleBounds(),
                EditorButton.Definition.iconOnly(Component.translatable("screen.brnquest.editor.localized_text.add_locale"),
                        Component.translatable("screen.brnquest.editor.localized_text.add_locale"),
                        QuestActionIcons.named("plus")), true, false, EditorButton.Tone.PRIMARY, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.title"),
                panel.left() + 12, panel.top() + 52, GraystonePalette.SECONDARY, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.subtitle"),
                panel.left() + 12, panel.top() + 78, GraystonePalette.SECONDARY, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.description"),
                panel.left() + 12, panel.top() + 103, GraystonePalette.SECONDARY, false);
    }

    private Component renderDocumentControls(GuiGraphics graphics, int mouseX, int mouseY) {
        Component hovered = null;
        Component formatLabel = Component.translatable(descriptionFormat.equals(DocumentFormat.MARKDOWN_V1)
                ? "screen.brnquest.editor.markdown.format_markdown" : descriptionFormat.equals(DocumentFormat.PLAIN)
                ? "screen.brnquest.editor.markdown.format_plain" : "screen.brnquest.editor.markdown.format_unknown",
                descriptionFormat.serializedName());
        if (buttons.render(graphics, font, formatBounds(), EditorButton.Definition.text(formatLabel,
                        Component.translatable("screen.brnquest.editor.markdown.format_hint")), true,
                descriptionFormat.equals(DocumentFormat.MARKDOWN_V1), EditorButton.Tone.NEUTRAL, mouseX, mouseY)) {
            hovered = Component.translatable("screen.brnquest.editor.markdown.format_hint");
        }
        if (!wideLayout()) {
            Component viewLabel = Component.translatable(previewTab
                    ? "screen.brnquest.editor.markdown.preview" : "screen.brnquest.editor.markdown.source");
            buttons.render(graphics, font, viewToggleBounds(), EditorButton.Definition.text(viewLabel,
                            Component.translatable("screen.brnquest.editor.markdown.view_toggle_hint")),
                    true, previewTab, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }
        if (sourceVisible()) {
            for (MarkdownSourceEdits.Tool tool : TOOLS) {
                Component tooltip = Component.translatable(toolTranslation(tool));
                if (buttons.render(graphics, font, toolBounds(tool), EditorButton.Definition.text(
                                Component.literal(toolLabel(tool)), tooltip),
                        descriptionFormat.equals(DocumentFormat.MARKDOWN_V1), false,
                        EditorButton.Tone.NEUTRAL, mouseX, mouseY)) hovered = tooltip;
            }
            Component textureTooltip = Component.translatable("screen.brnquest.editor.markdown.tool.texture");
            if (buttons.render(graphics, font, contentToolBounds(0), EditorButton.Definition.iconOnly(
                            textureTooltip, textureTooltip, TEXTURE_TOOL_ICON),
                    descriptionFormat.equals(DocumentFormat.MARKDOWN_V1), false,
                    EditorButton.Tone.NEUTRAL, mouseX, mouseY)) hovered = textureTooltip;
            Component itemTooltip = Component.translatable("screen.brnquest.editor.markdown.tool.item");
            if (buttons.render(graphics, font, contentToolBounds(1), EditorButton.Definition.iconOnly(
                            itemTooltip, itemTooltip, ITEM_TOOL_ICON),
                    descriptionFormat.equals(DocumentFormat.MARKDOWN_V1), false,
                    EditorButton.Tone.NEUTRAL, mouseX, mouseY)) hovered = itemTooltip;
        }
        return hovered;
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!previewVisible()) {
            renderedPreview = null;
            renderedPreviewBounds = null;
            return;
        }
        UiRect bounds = previewBounds();
        GraystoneSurface.raised(graphics, bounds, GraystonePalette.ROW, true);
        int padding = 5;
        int contentWidth = Math.max(1, bounds.width() - padding * 2 - 4);
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        renderedPreview = documentView.prepare(preview.visible(), preview.visible().sourceLocale(), contentWidth,
                System.identityHashCode(font), Double.doubleToLongBits(minecraft.getWindow().getGuiScale()),
                LoadedTextures.generation(), DocumentView.minecraftMetrics(font));
        int viewportHeight = Math.max(1, bounds.height() - padding * 2);
        long now = Util.getMillis();
        double elapsed = lastRenderMillis == 0 ? 0.0 : Math.min(0.1, (now - lastRenderMillis) / 1000.0);
        lastRenderMillis = now;
        double scroll = previewScroll.frameAndRender(graphics, bounds.right() - 4, bounds.top() + 2,
                bounds.bottom() - 2, renderedPreview.layout().contentHeight(), viewportHeight,
                elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        renderedDocumentX = bounds.left() + padding;
        renderedDocumentY = bounds.top() + padding - (int) Math.round(scroll);
        renderedPreviewBounds = new UiRect(bounds.left() + padding, bounds.top() + padding,
                bounds.right() - padding, bounds.bottom() - padding);
        graphics.enableScissor(renderedPreviewBounds.left(), renderedPreviewBounds.top(),
                renderedPreviewBounds.right(), renderedPreviewBounds.bottom());
        DocumentView.render(graphics, font, renderedPreview.layout(), renderedDocumentX, renderedDocumentY,
                GraystonePalette.TEXT);
        graphics.disableScissor();
    }

    private void renderMessages(GuiGraphics graphics, UiRect panel) {
        Component message = applyIssue != null ? applyIssue : formatNotice;
        if (message == null && renderedPreview != null && !renderedPreview.document().diagnostics().isEmpty()) {
            RichDocument.Diagnostic diagnostic = renderedPreview.document().diagnostics().getFirst();
            int[] position = diagnosticPosition(preview.visible().text(), diagnostic.offset());
            message = Component.translatable("screen.brnquest.editor.markdown.diagnostic", position[0], position[1],
                    diagnostic.code(), renderedPreview.document().diagnostics().size());
        }
        if (message != null) EditorTextRenderer.drawFittedString(graphics, font, message,
                panel.left() + 12, panel.bottom() - 46, panel.width() - 24, 0xFFF2C96D, 0.75F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.clicked(mouseX, mouseY, button);
        if (button == 0 && localeBounds().containsExclusive(mouseX, mouseY)) {
            rememberWidgets();
            localeIndex = (localeIndex + 1) % locales.size();
            localeEditor.setTextColor(0xFFFFFFFF);
            loadLocale(true);
            return true;
        }
        if (button == 0 && addLocaleBounds().containsExclusive(mouseX, mouseY)) {
            addLocale();
            return true;
        }
        if (button == 0 && formatBounds().containsExclusive(mouseX, mouseY)) {
            toggleFormat();
            return true;
        }
        if (!wideLayout() && button == 0 && viewToggleBounds().containsExclusive(mouseX, mouseY)) {
            previewTab = !previewTab;
            updateSourceVisibility();
            setFocused(previewTab ? null : descriptionEditor);
            return true;
        }
        if (button == 0 && sourceVisible() && descriptionFormat.equals(DocumentFormat.MARKDOWN_V1)) {
            for (MarkdownSourceEdits.Tool tool : TOOLS) {
                if (toolBounds(tool).containsExclusive(mouseX, mouseY)) {
                    if (tool == MarkdownSourceEdits.Tool.COLOR)
                        minecraft.setScreen(new EditorMarkdownColorScreen(this, this::insertColor));
                    else applyTool(tool);
                    return true;
                }
            }
            if (contentToolBounds(0).containsExclusive(mouseX, mouseY)) {
                minecraft.setScreen(new EditorTextureBrowserScreen(this, this::insertTexture));
                return true;
            }
            if (contentToolBounds(1).containsExclusive(mouseX, mouseY)) {
                minecraft.setScreen(new EditorItemSelectorScreen(this, this::insertItem));
                return true;
            }
        }
        URI link = button == 0 ? previewLinkAt(mouseX, mouseY) : null;
        if (link != null) {
            ConfirmLinkScreen.confirmLinkNow(this, link, true);
            return true;
        }
        if (button == 0 && cancelBounds().containsExclusive(mouseX, mouseY)) {
            onClose();
            return true;
        }
        if (button == 0 && applyBounds().containsExclusive(mouseX, mouseY)) {
            rememberWidgets();
            LocalizedQuestTextDrafts.Draft draft = drafts.get(locales.get(localeIndex));
            submitted = consumer.test(new Value(locales.get(localeIndex), draft.title(), draft.subtitle(),
                    draft.description(), draft.format()));
            if (submitted) onClose();
            else applyIssue = Component.translatable("screen.brnquest.editor.markdown.apply_unavailable");
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (previewVisible() && previewBounds().containsExclusive(mouseX, mouseY) && renderedPreview != null) {
            previewScroll.scrollWheel(scrollY, BrnQuestClientConfig.VALUES.scrollStep.get(),
                    renderedPreview.layout().contentHeight(), Math.max(1, previewBounds().height() - 10));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && getFocused() == localeEditor) {
            addLocale();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addLocale() {
        String raw = localeEditor.getValue().strip();
        if (!validLocaleCode(raw)) {
            localeEditor.setTextColor(0xFFFF8B8B);
            return;
        }
        rememberWidgets();
        String locale = BookLocalization.normalizeLocale(raw);
        int existing = locales.indexOf(locale);
        if (existing < 0) {
            locales.add(locale);
            localeIndex = locales.size() - 1;
        } else localeIndex = existing;
        localeEditor.setValue("");
        localeEditor.setTextColor(0xFFFFFFFF);
        loadLocale(true);
        setFocused(titleEditor);
    }

    static boolean validLocaleCode(String value) {
        return value != null && value.length() <= 32
                && value.matches("[A-Za-z0-9]{2,16}([_-][A-Za-z0-9]{2,16})*");
    }

    static int[] diagnosticPosition(String text, int offset) {
        int safe = Math.max(0, Math.min(offset, text == null ? 0 : text.length()));
        int line = 1;
        int column = 1;
        for (int index = 0; text != null && index < safe; index++) {
            if (text.charAt(index) == '\n') {
                line++;
                column = 1;
            } else column++;
        }
        return new int[]{line, column};
    }

    private void loadLocale(boolean immediatePreview) {
        LocalizedQuestTextDrafts.Draft draft = drafts.get(locales.get(localeIndex));
        loadingWidgets = true;
        titleEditor.setValue(draft.title());
        subtitleEditor.setValue(draft.subtitle());
        descriptionFormat = draft.format();
        descriptionEditor.setValue(draft.description());
        loadingWidgets = false;
        formatNotice = null;
        applyIssue = null;
        if (immediatePreview) preview.showNow(draft.description(), draft.format(), locales.get(localeIndex));
        previewScroll.snap(0);
    }

    private void rememberWidgets() {
        if (titleEditor == null || subtitleEditor == null || descriptionEditor == null || locales.isEmpty()) return;
        drafts.remember(locales.get(localeIndex), titleEditor.getValue(), subtitleEditor.getValue(),
                descriptionEditor.getValue(), descriptionFormat);
    }

    private void descriptionChanged(String text) {
        if (loadingWidgets) return;
        rememberWidgets();
        preview.schedule(text, descriptionFormat, locales.get(localeIndex), Util.getMillis());
        applyIssue = null;
    }

    private void toggleFormat() {
        DocumentFormat previous = descriptionFormat;
        descriptionFormat = previous.equals(DocumentFormat.PLAIN)
                ? DocumentFormat.MARKDOWN_V1 : DocumentFormat.PLAIN;
        rememberWidgets();
        preview.showNow(descriptionEditor.getValue(), descriptionFormat, locales.get(localeIndex));
        previewScroll.snap(0);
        applyIssue = null;
        formatNotice = previous.equals(DocumentFormat.PLAIN)
                ? Component.translatable("screen.brnquest.editor.markdown.plain_to_markdown_warning") : null;
    }

    private void applyTool(MarkdownSourceEdits.Tool tool) {
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        MultilineTextFieldAccessor selection = (MultilineTextFieldAccessor) field;
        String placeholder = Component.translatable(toolPlaceholder(tool)).getString();
        MarkdownSourceEdits.Result result = MarkdownSourceEdits.apply(descriptionEditor.getValue(),
                selection.brnquest$cursor(), selection.brnquest$selectCursor(), tool, placeholder);
        applySourceEdit(field, result);
    }

    private void insertColor(String color) {
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        MultilineTextFieldAccessor selection = (MultilineTextFieldAccessor) field;
        MarkdownSourceEdits.Result result = MarkdownSourceEdits.applyColor(descriptionEditor.getValue(),
                selection.brnquest$cursor(), selection.brnquest$selectCursor(), color,
                Component.translatable(toolPlaceholder(MarkdownSourceEdits.Tool.COLOR)).getString());
        applySourceEdit(field, result);
        // Color selection is a child screen, so preserve the wrapped selection through reinitialization.
        reopenSelectionStart = result.selectionStart();
        reopenSelectionEnd = result.selectionEnd();
    }

    private void insertTexture(String id) {
        insertContent(RichDocument.ContentKind.TEXTURE, id,
                Component.translatable("screen.brnquest.editor.markdown.placeholder.texture").getString());
        if (id.startsWith("brnquest_local:"))
            reopenNotice = Component.translatable("screen.brnquest.editor.markdown.local_texture_warning");
    }

    private void insertItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        insertContent(RichDocument.ContentKind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                Component.translatable("screen.brnquest.editor.markdown.placeholder.item").getString());
    }

    private void insertContent(RichDocument.ContentKind kind, String id, String placeholder) {
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        MultilineTextFieldAccessor selection = (MultilineTextFieldAccessor) field;
        MarkdownSourceEdits.Result result = MarkdownSourceEdits.insertContent(descriptionEditor.getValue(),
                selection.brnquest$cursor(), selection.brnquest$selectCursor(), kind, id, placeholder);
        applySourceEdit(field, result);
        // Child pickers reinitialize this screen on return; restore the inserted alt-text selection afterwards.
        reopenSelectionStart = result.selectionStart();
        reopenSelectionEnd = result.selectionEnd();
    }

    private void applySourceEdit(MultilineTextField field, MarkdownSourceEdits.Result result) {
        descriptionEditor.setValue(result.text());
        // Recreate the selection through vanilla cursor methods so scrolling and selection painting stay synchronized.
        field.setSelecting(false);
        field.seekCursor(Whence.ABSOLUTE, result.selectionStart());
        field.setSelecting(true);
        field.seekCursor(Whence.ABSOLUTE, result.selectionEnd());
        field.setSelecting(false);
        setFocused(descriptionEditor);
    }

    private void restoreChildSelection() {
        if (reopenSelectionStart < 0) return;
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        field.setSelecting(false);
        field.seekCursor(Whence.ABSOLUTE, reopenSelectionStart);
        field.setSelecting(true);
        field.seekCursor(Whence.ABSOLUTE, reopenSelectionEnd);
        field.setSelecting(false);
        reopenSelectionStart = -1;
        reopenSelectionEnd = -1;
        if (reopenNotice != null) {
            formatNotice = reopenNotice;
            reopenNotice = null;
        }
    }

    private URI previewLinkAt(double mouseX, double mouseY) {
        if (renderedPreview == null || renderedPreviewBounds == null
                || !renderedPreviewBounds.containsExclusive(mouseX, mouseY)) return null;
        int localX = (int) Math.floor(mouseX) - renderedDocumentX;
        int localY = (int) Math.floor(mouseY) - renderedDocumentY;
        int viewportTop = renderedPreviewBounds.top() - renderedDocumentY;
        DocumentLayout.Bounds viewport = new DocumentLayout.Bounds(0, viewportTop,
                renderedPreviewBounds.width(), viewportTop + renderedPreviewBounds.height());
        return DocumentView.linkAt(renderedPreview.layout(), localX, localY, viewport);
    }

    private DocumentLayout.ContentHit previewContentAt(double mouseX, double mouseY) {
        if (renderedPreview == null || renderedPreviewBounds == null
                || !renderedPreviewBounds.containsExclusive(mouseX, mouseY)) return null;
        return DocumentView.contentAt(renderedPreview.layout(), (int) Math.floor(mouseX) - renderedDocumentX,
                (int) Math.floor(mouseY) - renderedDocumentY, previewViewport());
    }

    private ItemStack previewItemAt(double mouseX, double mouseY) {
        if (renderedPreview == null || renderedPreviewBounds == null
                || !renderedPreviewBounds.containsExclusive(mouseX, mouseY)) return ItemStack.EMPTY;
        return DocumentView.itemAt(renderedPreview.layout(), (int) Math.floor(mouseX) - renderedDocumentX,
                (int) Math.floor(mouseY) - renderedDocumentY, previewViewport());
    }

    @Override
    public Optional<RecipeLookupTarget> recipeLookupTargetAt(double mouseX, double mouseY) {
        DocumentLayout.ContentHit content = previewContentAt(mouseX, mouseY);
        if (content == null || content.content().kind() != RichDocument.ContentKind.ITEM) return Optional.empty();
        ItemStack item = previewItemAt(mouseX, mouseY);
        UiRect bounds = new UiRect(renderedDocumentX + content.bounds().left(),
                renderedDocumentY + content.bounds().top(), renderedDocumentX + content.bounds().right(),
                renderedDocumentY + content.bounds().bottom());
        // Keep JEI shortcuts on the exact visible part of an item clipped by preview scrolling.
        return RecipeLookupTarget.clipped(item, bounds, renderedPreviewBounds)
                .filter(target -> target.contains(mouseX, mouseY));
    }

    private DocumentLayout.Bounds previewViewport() {
        int viewportTop = renderedPreviewBounds.top() - renderedDocumentY;
        return new DocumentLayout.Bounds(0, viewportTop, renderedPreviewBounds.width(),
                viewportTop + renderedPreviewBounds.height());
    }

    private void updateSourceVisibility() {
        if (descriptionEditor == null) return;
        descriptionEditor.visible = sourceVisible();
        descriptionEditor.active = sourceVisible();
    }

    private boolean wideLayout() { return panelBounds().width() >= WIDE_LAYOUT_MINIMUM; }
    private boolean sourceVisible() { return wideLayout() || !previewTab; }
    private boolean previewVisible() { return wideLayout() || previewTab; }

    @Override
    public void onClose() {
        rememberWidgets();
        if (!submitted) discardRecovery.run();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private UiRect panelBounds() {
        int panelWidth = Math.min(760, Math.max(260, width - 24));
        int panelHeight = Math.min(560, Math.max(260, height - 24));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect localeBounds() { UiRect p = panelBounds(); return new UiRect(p.left()+88,p.top()+22,p.centerX()-4,p.top()+42); }
    private UiRect newLocaleBounds() { UiRect p = panelBounds(); return new UiRect(p.centerX()+4,p.top()+22,p.right()-38,p.top()+42); }
    private UiRect addLocaleBounds() { UiRect p = panelBounds(); return new UiRect(p.right()-34,p.top()+22,p.right()-12,p.top()+42); }
    private UiRect formatBounds() {
        UiRect p=panelBounds();
        int controlsCenter = (p.left()+88 + p.right()-12) / 2;
        return new UiRect(p.left()+88,p.top()+96,wideLayout() ? p.left()+194 : controlsCenter-2,p.top()+116);
    }
    /** Narrow mode presents format and view as one pair of equally weighted binary controls. */
    private UiRect viewToggleBounds() {
        UiRect p=panelBounds();
        int controlsCenter = (p.left()+88 + p.right()-12) / 2;
        return new UiRect(controlsCenter+2,p.top()+96,p.right()-12,p.top()+116);
    }
    private UiRect toolBounds(MarkdownSourceEdits.Tool tool) {
        return toolbarCell(tool.ordinal());
    }
    private UiRect contentToolBounds(int index) {
        return toolbarCell(TOOLS.length + index);
    }
    private UiRect toolbarCell(int index) {
        UiRect area = sourceColumn();
        int gap = 3;
        int count = TOOLS.length + CONTENT_TOOL_COUNT;
        int columns = Math.min(TOOLBAR_COLUMNS, count);
        int width = Math.max(18, (area.width() - gap * (columns - 1)) / columns);
        int column = index % columns;
        int row = index / columns;
        int left = area.left() + column * (width + gap);
        int right = column == columns - 1 ? area.right() : left + width;
        int top = panelBounds().top() + 120 + row * 23;
        return new UiRect(left, top, right, top + 20);
    }
    private UiRect sourceColumn() {
        UiRect p=panelBounds();
        int left=p.left()+12, right=p.right()-12;
        if (wideLayout()) right=p.centerX()-4;
        int rows = (TOOLS.length + CONTENT_TOOL_COUNT + TOOLBAR_COLUMNS - 1) / TOOLBAR_COLUMNS;
        return new UiRect(left,p.top()+121 + rows*23,right,p.bottom()-66);
    }
    private UiRect sourceBounds() { return sourceColumn(); }
    private UiRect previewBounds() {
        UiRect p=panelBounds();
        return wideLayout() ? new UiRect(p.centerX()+4,p.top()+120,p.right()-12,p.bottom()-66)
                : new UiRect(p.left()+12,sourceColumn().top(),p.right()-12,p.bottom()-66);
    }
    private UiRect cancelBounds() { UiRect p=panelBounds(); return new UiRect(p.left()+12,p.bottom()-34,p.centerX()-4,p.bottom()-10); }
    private UiRect applyBounds() { UiRect p=panelBounds(); return new UiRect(p.centerX()+4,p.bottom()-34,p.right()-12,p.bottom()-10); }

    private static String toolLabel(MarkdownSourceEdits.Tool tool) {
        return switch (tool) {
            case HEADING -> "H";
            case BOLD -> "B";
            case ITALIC -> "I";
            case LIST -> "•";
            case CODE -> "`";
            case LINK -> "↗";
            case UNDERLINE -> "U";
            case STRIKETHROUGH -> "S";
            case OBFUSCATED -> "K";
            case COLOR -> "C";
        };
    }

    private static String toolTranslation(MarkdownSourceEdits.Tool tool) {
        return "screen.brnquest.editor.markdown.tool." + tool.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String toolPlaceholder(MarkdownSourceEdits.Tool tool) {
        return "screen.brnquest.editor.markdown.placeholder." + tool.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Child editors follow the task book's pause policy instead of Screen's unconditional default. */
    @Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
