package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import yourscraft.jasdewstarfield.brnquest.client.mixin.EditBoxSelectionAccessor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSourceField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorControlNavigation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;
import yourscraft.jasdewstarfield.brnquest.data.text.MinecraftTextColor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;
import yourscraft.jasdewstarfield.brnquest.client.mixin.MultiLineEditBoxAccessor;
import yourscraft.jasdewstarfield.brnquest.client.mixin.MultilineTextFieldAccessor;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
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
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

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

    private enum Target { TITLE, SUBTITLE, BODY }
    // Session preferences survive new editor instances; a temporary small window never overwrites them.
    private static TextEditorLayout.View preferredView = TextEditorLayout.View.SPLIT;
    private static boolean viewChosen;
    private static double preferredRatio = .5;
    private Target target = Target.BODY;
    private boolean draggingDivider;
    private boolean metadataExpanded;
    private boolean initialized;
    private final Map<String, CursorState> cursors = new HashMap<>();
    private record CursorState(int titleCursor, int titleAnchor, int subtitleCursor, int subtitleAnchor,
                               int bodyCursor, int bodyAnchor, double sourceScroll, double previewScroll) {}
    private final EditorButtonInput buttons = new EditorButtonInput();
    private final Screen parent;
    private final Predicate<Value> consumer;
    private final Runnable discardRecovery;
    private final ResourceLocation editedQuestId;
    private final List<String> locales;
    private final LocalizedQuestTextDrafts drafts;
    private final DocumentView documentView = new DocumentView();
    private final MarkdownPreviewState preview = new MarkdownPreviewState();
    private final EditorSmoothScroll previewScroll = new EditorSmoothScroll();
    private int localeIndex;
    private String loadedLocale;
    private EditBox titleEditor;
    private EditBox subtitleEditor;
    private MultiLineEditBox descriptionEditor;
    private DocumentFormat descriptionFormat = DocumentFormat.PLAIN;
    private boolean loadingWidgets;
    private boolean submitted;
    private Component formatNotice;
    private Component applyIssue;
    private DocumentView.Prepared renderedPreview;
    private UiRect renderedPreviewBounds;
    private int renderedDocumentX;
    private int renderedDocumentY;
    private int renderedContentHeight;
    private long lastRenderMillis;
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
        this.editedQuestId = quest.id();
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
        if (!initialized) {
            if (!viewChosen) preferredView = width >= 560 ? TextEditorLayout.View.SPLIT : TextEditorLayout.View.SOURCE;
            metadataExpanded = height >= 340;
            initialized = true;
        }
        titleEditor = nativeField(titleBounds(), "screen.brnquest.editor.quest.title");
        subtitleEditor = nativeField(subtitleBounds(), "screen.brnquest.editor.quest.subtitle");
        descriptionEditor = new EditorSourceField(font, sourceBounds(),
                Component.translatable("screen.brnquest.editor.quest.description"));
        descriptionEditor.setCharacterLimit(32_768);
        descriptionEditor.setValueListener(this::descriptionChanged);
        titleEditor.setResponder(value -> metadataChanged());
        subtitleEditor.setResponder(value -> metadataChanged());
        addRenderableWidget(titleEditor);
        addRenderableWidget(subtitleEditor);
        addRenderableWidget(descriptionEditor);
        addWidget(new EditorControlNavigation(text("controls"), layoutModel().body,
                key -> buttons.keyPressed(key==262||key==264||key==263||key==265?258:key,
                        key==263||key==265||hasShiftDown(), rect -> mouseClicked(rect.centerX(),rect.centerY(),0)),
                () -> buttons.atBoundary(hasShiftDown()),
                () -> buttons.keyPressed(258,hasShiftDown(), rect -> {}), buttons::clearFocus));
        loadLocale(true);
        if (!metadataShown() && target != Target.BODY) target = Target.BODY;
        restoreCursors();
        restoreChildSelection();
        updateSourceVisibility();
        setFocused(target == Target.TITLE && titleEditor.visible ? titleEditor
                : target == Target.SUBTITLE && subtitleEditor.visible ? subtitleEditor
                : sourceVisible() ? descriptionEditor : null);
    }

    private EditBox nativeField(UiRect bounds, String translation) {
        EditorTextField field = new EditorTextField(font, Component.translatable(translation), 256);
        field.brnquest$rawText(true);
        field.show(bounds, true);
        return field;
    }

    /** init restores the authored target; Screen's keyboard default must not move focus to another field. */
    @Override protected void setInitialFocus() {}

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void tick() {
        parent.tick();
        super.tick();
        dispatchPendingTool();
        preview.advance(Util.getMillis());
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
        Component hovered = renderDocumentControls(graphics, mouseX, mouseY);
        renderPreview(graphics, mouseX, mouseY);
        renderMessages(graphics, panel);
        if (layoutModel().split) {
            UiRect divider = layoutModel().divider;
            graphics.fill(divider.centerX()-1, divider.top(), divider.centerX()+1, divider.bottom(),
                    draggingDivider || divider.containsExclusive(mouseX, mouseY) ? GraystonePalette.ACCENT : GraystonePalette.EDGE);
            if (divider.containsExclusive(mouseX, mouseY)) hovered = text("divider_hint");
        }
        graphics.drawCenteredString(font, Component.literal(descriptionEditor.getValue().length() + " / 32768"),
                panel.centerX(), panel.bottom()-24, GraystonePalette.SECONDARY);
        buttons.render(graphics, font, cancelBounds(),
                EditorButton.Definition.text(Component.translatable("gui.cancel"), null), true, false,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        buttons.render(graphics, font, applyBounds(),
                EditorButton.Definition.text(Component.translatable("gui.done"), null), true, false,
                EditorButton.Tone.PRIMARY, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        RichDocument.LinkDestination link = previewLinkAt(mouseX, mouseY);
        if (link != null) hovered = previewLinkHint(link);
        ItemStack item = previewItemAt(mouseX, mouseY);
        if (!item.isEmpty()) graphics.renderTooltip(font, item, mouseX, mouseY);
        else if (hovered != null)
            graphics.renderTooltip(font, font.split(hovered, Math.min(280, width - 20)), mouseX, mouseY);
    }

    private Component renderDocumentControls(GuiGraphics graphics, int mouseX, int mouseY) {
        Component hovered = null;
        button(graphics, localeBounds(), Component.literal(locales.get(localeIndex)), text("language_hint"), false, mouseX, mouseY);
        button(graphics, addLocaleBounds(), Component.literal("+"), text("language_add"), false, mouseX, mouseY);
        button(graphics, metadataToggleBounds(), text("information"), text("information_hint"), metadataExpanded, mouseX, mouseY);
        Component formatLabel = Component.translatable(descriptionFormat.equals(DocumentFormat.MARKDOWN_V1)
                ? "screen.brnquest.editor.markdown.format_markdown" : descriptionFormat.equals(DocumentFormat.PLAIN)
                ? "screen.brnquest.editor.markdown.format_plain" : "screen.brnquest.editor.markdown.format_unknown", descriptionFormat.serializedName());
        button(graphics, formatBounds(), formatLabel, Component.translatable("screen.brnquest.editor.markdown.format_hint"),
                descriptionFormat.equals(DocumentFormat.MARKDOWN_V1), mouseX, mouseY);
        for (var view : TextEditorLayout.View.values()) {
            if (button(graphics, viewBounds(view), text("view." + view.name().toLowerCase(java.util.Locale.ROOT)),
                    text(view == TextEditorLayout.View.SPLIT && !layoutModel().split ? "split_hint" : "view_hint"),
                    preferredView == view, mouseX, mouseY)) hovered = text("view_hint");
        }
        if (metadataShown()) {
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.title"),
                    panelBounds().left()+10, titleBounds().top()+5, GraystonePalette.SECONDARY, false);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.quest.subtitle"),
                    panelBounds().left()+10, subtitleBounds().top()+5, GraystonePalette.SECONDARY, false);
        }
        UiRect area = layoutModel().body;
        EditorTextRenderer.drawFittedString(graphics, font, text("target." + target.name().toLowerCase(java.util.Locale.ROOT)),
                area.left(), layoutModel().toolbarTop+6, targetWidth()-4, GraystonePalette.SECONDARY, .75F);
        for (var action:toolbarLayout().actions()) {
            Component hint=actionHint(action.key());
            if (button(graphics,action.bounds(),actionLabel(action.key()),hint,false,mouseX,mouseY)) hovered=hint;
        }
        if (button(graphics,moreBounds(),text("more"),text("more"),false,mouseX,mouseY)) hovered=text("more");
        return hovered;
    }

    private boolean button(GuiGraphics graphics, UiRect bounds, Component label, Component hint,
                           boolean selected, int mouseX, int mouseY) {
        return buttons.render(graphics, font, bounds, EditorButton.Definition.text(label, hint),
                true, selected, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private static Component text(String suffix) { return Component.translatable("screen.brnquest.editor.text." + suffix); }

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
        Component previewTitle = NativeText.component(QuestPresentation.questTitle(titleEditor.getValue(), () -> Component.translatable("screen.brnquest.quest.untitled").getString()));
        Component previewSubtitle = NativeText.component(subtitleEditor.getValue());
        var titleLines = NativeText.split(font, previewTitle, contentWidth);
        var subtitleLines = previewSubtitle.getString().isBlank() ? List.<net.minecraft.util.FormattedCharSequence>of()
                : NativeText.split(font, previewSubtitle, contentWidth);
        int headerHeight = (titleLines.size()+subtitleLines.size()) * font.lineHeight + 8;
        renderedContentHeight = renderedPreview.layout().contentHeight()+headerHeight;
        int viewportHeight = Math.max(1, bounds.height() - padding * 2);
        long now = Util.getMillis();
        double elapsed = lastRenderMillis == 0 ? 0.0 : Math.min(0.1, (now - lastRenderMillis) / 1000.0);
        lastRenderMillis = now;
        double scroll = previewScroll.frameAndRender(graphics, bounds.right() - 4, bounds.top() + 2,
                bounds.bottom() - 2, renderedPreview.layout().contentHeight() + headerHeight, viewportHeight,
                elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
        renderedDocumentX = bounds.left() + padding;
        renderedDocumentY = bounds.top() + padding + headerHeight - (int) Math.round(scroll);
        renderedPreviewBounds = new UiRect(bounds.left() + padding, bounds.top() + padding,
                bounds.right() - padding, bounds.bottom() - padding);
        graphics.enableScissor(renderedPreviewBounds.left(), renderedPreviewBounds.top(),
                renderedPreviewBounds.right(), renderedPreviewBounds.bottom());
        int headerY = bounds.top()+padding-(int)Math.round(scroll);
        for (var line : titleLines) { graphics.drawString(font, line, renderedDocumentX, headerY, GraystonePalette.TEXT, false); headerY+=font.lineHeight; }
        for (var line : subtitleLines) { graphics.drawString(font, line, renderedDocumentX, headerY, GraystonePalette.SECONDARY, false); headerY+=font.lineHeight; }
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
                panel.left() + 10, panel.bottom() - 43, panel.width() - 24, 0xFFF2C96D, 0.75F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.clicked(mouseX, mouseY, button);
        UiRect previewBounds = previewBounds();
        if (button == 0 && previewVisible() && renderedPreview != null
                && previewScroll.handleTrackClick(mouseX, mouseY, previewBounds.right() - 4,
                previewBounds.top() + 2, previewBounds.bottom() - 2,
                renderedContentHeight, Math.max(1, previewBounds.height() - 10))) return true;
        if (button == 0 && layoutModel().divider.containsExclusive(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (button == 0 && localeBounds().containsExclusive(mouseX, mouseY)) {
            rememberWidgets();
            List<EditorChoiceScreen.Choice> choices = locales.stream().map(locale -> new EditorChoiceScreen.Choice(locale, Component.literal(locale))).toList();
            minecraft.setScreen(new EditorChoiceScreen(this, text("language_hint"), choices, locales.get(localeIndex), locale -> {
                localeIndex = locales.indexOf(locale);
                loadLocale(true);
                restoreCursors();
            }));
            return true;
        }
        if (button == 0 && addLocaleBounds().containsExclusive(mouseX, mouseY)) {
            rememberWidgets();
            minecraft.setScreen(new EditorLanguageScreen(this, raw -> {
                String locale = BookLocalization.normalizeLocale(raw);
                if (!locales.contains(locale)) locales.add(locale);
                localeIndex = locales.indexOf(locale);
                loadLocale(true);
                restoreCursors();
            }));
            return true;
        }
        if (button == 0 && metadataToggleBounds().containsExclusive(mouseX, mouseY)) {
            if (height < 260) {
                rememberWidgets();
                minecraft.setScreen(new EditorTextInformationScreen(this,titleEditor.getValue(),subtitleEditor.getValue(),(title,subtitle)-> {
                    titleEditor.setValue(title); subtitleEditor.setValue(subtitle); rememberWidgets();
                }));
                return true;
            }
            metadataExpanded = !metadataExpanded;
            if (!metadataExpanded) target = Target.BODY;
            init(minecraft, width, height);
            return true;
        }
        if (button == 0 && formatBounds().containsExclusive(mouseX, mouseY)) { toggleFormat(); return true; }
        for (var view : TextEditorLayout.View.values()) {
            if (button == 0 && viewBounds(view).containsExclusive(mouseX, mouseY)) {
                preferredView = view;
                viewChosen = true;
                rememberWidgets();
                preview.showNow(descriptionEditor.getValue(), descriptionFormat, locales.get(localeIndex));
                init(minecraft, width, height);
                return true;
            }
        }
        if (button == 0) {
            for (var action:toolbarLayout().actions()) {
                if (action.bounds().containsExclusive(mouseX,mouseY)) { dispatchAction(action.key()); return true; }
            }
            if (moreBounds().containsExclusive(mouseX, mouseY)) { openMore(); return true; }
        }
        RichDocument.LinkDestination link = button == 0 ? previewLinkAt(mouseX, mouseY) : null;
        if (link instanceof RichDocument.ExternalLink external) {
            ConfirmLinkScreen.confirmLinkNow(this, external.uri(), true);
            return true;
        }
        if (link instanceof RichDocument.QuestLink) {
            // Keep the unsaved localized buffer open; navigation belongs to the task-book details screen.
            formatNotice = Component.translatable("screen.brnquest.editor.markdown.quest_link.preview_only");
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
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        updateTarget();
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingDivider && button == 0) {
            preferredRatio = TextEditorLayout.dragRatio(layoutModel().body, mouseX);
            rememberWidgets();
            init(minecraft, width, height);
            return true;
        }
        return previewScroll.handleDrag(mouseY, button)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingDivider && button == 0) { draggingDivider=false; return true; }
        return previewScroll.handleRelease(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (previewVisible() && previewBounds().containsExclusive(mouseX, mouseY) && renderedPreview != null) {
            previewScroll.scrollWheel(scrollY, BrnQuestClientConfig.VALUES.scrollStep.get(),
                    renderedContentHeight, Math.max(1, previewBounds().height() - 10));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 258 && hasControlDown()) {
            viewChosen = true;
            preferredView = TextEditorLayout.View.values()[(preferredView.ordinal()+1)%3];
            init(minecraft, width, height);
            return true;
        }
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        updateTarget();
        return handled;
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
        // Same-locale rebuilds preserve picker errors and rejected-confirmation feedback.
        if (!locales.get(localeIndex).equals(loadedLocale)) {
            formatNotice = null;
            applyIssue = null;
            loadedLocale = locales.get(localeIndex);
        }
        if (immediatePreview) preview.showNow(draft.description(), draft.format(), locales.get(localeIndex));
        previewScroll.snap(0);
    }

    private void rememberWidgets() {
        if (titleEditor == null || subtitleEditor == null || descriptionEditor == null || locales.isEmpty()) return;
        drafts.remember(locales.get(localeIndex), titleEditor.getValue(), subtitleEditor.getValue(),
                descriptionEditor.getValue(), descriptionFormat);
        var body = (MultilineTextFieldAccessor) ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        cursors.put(locales.get(localeIndex), new CursorState(titleEditor.getCursorPosition(), ((EditBoxSelectionAccessor)titleEditor).brnquest$anchor(),
                subtitleEditor.getCursorPosition(), ((EditBoxSelectionAccessor)subtitleEditor).brnquest$anchor(),
                body.brnquest$cursor(), body.brnquest$selectCursor(), ((EditorSourceField)descriptionEditor).savedScroll(), previewScroll.visual()));
    }

    private void descriptionChanged(String text) {
        if (loadingWidgets) return;
        rememberWidgets();
        preview.schedule(text, descriptionFormat, locales.get(localeIndex), Util.getMillis());
        applyIssue = null;
    }

    private void metadataChanged() {
        if (!loadingWidgets) { rememberWidgets(); applyIssue=null; }
    }

    private void toggleFormat() {
        DocumentFormat previous = descriptionFormat;
        descriptionFormat = previous.equals(DocumentFormat.PLAIN)
                ? DocumentFormat.MARKDOWN_V1 : DocumentFormat.PLAIN;
        rememberWidgets();
        preview.showNow(descriptionEditor.getValue(), descriptionFormat, locales.get(localeIndex));
        applyIssue = null;
        formatNotice = null;
    }

    private void applyTool(MarkdownSourceEdits.Tool tool) {
        if (nativeTarget()) {
            char code = switch (tool) { case BOLD -> 'l'; case ITALIC -> 'o'; case UNDERLINE -> 'n';
                case STRIKETHROUGH -> 'm'; case OBFUSCATED -> 'k'; default -> 'r'; };
            applyNative(code);
            return;
        }
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        MultilineTextFieldAccessor selection = (MultilineTextFieldAccessor) field;
        String placeholder = Component.translatable(toolPlaceholder(tool)).getString();
        applySourceEdit(field, MarkdownSourceEdits.apply(descriptionEditor.getValue(), selection.brnquest$cursor(), selection.brnquest$selectCursor(), tool, placeholder));
    }

    private boolean nativeTarget() { return target != Target.BODY || !descriptionFormat.equals(DocumentFormat.MARKDOWN_V1); }
    private void updateTarget() {
        if (getFocused() == titleEditor) target = Target.TITLE;
        else if (getFocused() == subtitleEditor) target = Target.SUBTITLE;
        else if (getFocused() == descriptionEditor) target = Target.BODY;
    }
    private void openColor() {
        rememberWidgets();
        if (nativeTarget()) {
            List<EditorChoiceScreen.Choice> colors = java.util.Arrays.stream(MinecraftTextColor.values())
                    .map(color -> new EditorChoiceScreen.Choice(color.serializedName(),
                            Component.translatable("screen.brnquest.editor.markdown.color." + color.serializedName()).withColor(color.rgb()))).toList();
            minecraft.setScreen(new EditorChoiceScreen(this, Component.translatable("screen.brnquest.editor.markdown.color.title"), colors, "", this::insertColor));
        } else minecraft.setScreen(new EditorMarkdownColorScreen(this, this::insertColor));
    }
    private void openMore() {
        rememberWidgets();
        List<EditorChoiceScreen.Choice> choices = new ArrayList<>();
        var visible=toolbarLayout();
        for (String key:toolbarCandidates()) {
            if (!visible.contains(key)) choices.add(new EditorChoiceScreen.Choice(key,actionHint(key)));
        }
        choices.add(new EditorChoiceScreen.Choice("OBFUSCATED",actionHint("OBFUSCATED")));
        minecraft.setScreen(new EditorChoiceScreen(this, text("more"), choices, "", key -> {
            // Dispatch nested pickers after the menu returns, so it cannot overwrite the new child screen.
            pendingTool = key;
        }));
    }
    private String pendingTool;
    private void dispatchPendingTool() {
        if (pendingTool == null) return;
        String key = pendingTool; pendingTool = null;
        dispatchAction(key);
    }
    private void dispatchAction(String key) {
        if (key.equals("COLOR")) openColor();
        else if (key.equals("RESET")) applyNative('r');
        else if (key.equals("TEXTURE")) { rememberWidgets(); minecraft.setScreen(new EditorTextureBrowserScreen(this,this::insertTexture)); }
        else if (key.equals("ITEM")) { rememberWidgets(); minecraft.setScreen(new EditorItemSelectorScreen(this,this::insertItem)); }
        else if (key.equals("QUEST_LINK")) openQuestLinkPicker();
        else applyTool(MarkdownSourceEdits.Tool.valueOf(key));
    }
    private void applyNative(char code) {
        if (target == Target.BODY) {
            var field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
            var selected = (MultilineTextFieldAccessor) field;
            applySourceEdit(field, NativeSourceEdits.apply(descriptionEditor.getValue(), selected.brnquest$cursor(), selected.brnquest$selectCursor(), code));
        } else {
            EditBox field = target == Target.TITLE ? titleEditor : subtitleEditor;
            var result = NativeSourceEdits.apply(field.getValue(), field.getCursorPosition(), ((EditBoxSelectionAccessor)field).brnquest$anchor(), code);
            if (result.text().length()>256) { formatNotice=text("too_long"); return; }
            field.setValue(result.text());
            field.setCursorPosition(result.selectionEnd()); field.setHighlightPos(result.selectionStart());
            setFocused(field);
        }
        rememberWidgets();
    }
    private void insertColor(String color) {
        if (nativeTarget()) {
            var entry = MinecraftTextColor.byName(color);
            if (entry != null) applyNative(entry.legacyCode());
        } else {
            var field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
            var selection = (MultilineTextFieldAccessor) field;
            applySourceEdit(field, MarkdownSourceEdits.applyColor(descriptionEditor.getValue(), selection.brnquest$cursor(), selection.brnquest$selectCursor(), color,
                    Component.translatable(toolPlaceholder(MarkdownSourceEdits.Tool.COLOR)).getString()));
        }
        rememberWidgets();
    }

    private void insertTexture(String id) {
        boolean changed = insertContent(RichDocument.ContentKind.TEXTURE, id,
                Component.translatable("screen.brnquest.editor.markdown.placeholder.texture").getString());
        if (changed && id.startsWith("brnquest_local:"))
            reopenNotice = Component.translatable("screen.brnquest.editor.markdown.local_texture_warning");
    }

    private void insertItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        insertContent(RichDocument.ContentKind.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                Component.translatable("screen.brnquest.editor.markdown.placeholder.item").getString());
    }

    private void openQuestLinkPicker() {
        rememberWidgets();
        var snapshot = ClientEditorState.get().displayDraft()
                .orElseGet(() -> ClientQuestState.get().book().orElse(null));
        if (snapshot == null) {
            formatNotice = Component.translatable("screen.brnquest.editor.markdown.quest_link.unavailable");
            return;
        }
        String locale = locales.get(localeIndex);
        List<EditorChoiceScreen.Choice> choices = new ArrayList<>();
        for (ChapterDefinition chapter : snapshot.book().chapters()) {
            String chapterTitle = BookText.structureTitle(snapshot.book(), "chapter", chapter.id(), locale,
                    chapter.title());
            if (chapterTitle.isBlank()) chapterTitle = chapter.id().toString();
            for (QuestDefinition quest : chapter.quests()) {
                if (quest.id().equals(editedQuestId)) continue;
                String questTitle = BookText.quest(snapshot.book(), quest, locale, "title", quest.title());
                if (questTitle.isBlank()) questTitle = quest.id().toString();
                choices.add(new EditorChoiceScreen.Choice(quest.id().toString(),
                        NativeText.component(questTitle).copy().append(Component.literal(" · " + chapterTitle))));
            }
        }
        minecraft.setScreen(new EditorChoiceScreen(this,
                Component.translatable("screen.brnquest.editor.markdown.quest_link.select"), choices, "",
                this::insertQuestLink));
    }

    private void insertQuestLink(String questId) {
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        MultilineTextFieldAccessor selection = (MultilineTextFieldAccessor) field;
        MarkdownSourceEdits.Result result = MarkdownSourceEdits.insertQuestLink(descriptionEditor.getValue(),
                selection.brnquest$cursor(), selection.brnquest$selectCursor(), questId,
                Component.translatable(toolPlaceholder(MarkdownSourceEdits.Tool.QUEST_LINK)).getString());
        applySourceEdit(field, result);
    }

    private boolean insertContent(RichDocument.ContentKind kind, String id, String placeholder) {
        MultilineTextField field = ((MultiLineEditBoxAccessor) descriptionEditor).brnquest$textField();
        MultilineTextFieldAccessor selection = (MultilineTextFieldAccessor) field;
        MarkdownSourceEdits.Result result = MarkdownSourceEdits.insertContent(descriptionEditor.getValue(),
                selection.brnquest$cursor(), selection.brnquest$selectCursor(), kind, id, placeholder);
        return applySourceEdit(field, result);
    }

    private boolean applySourceEdit(MultilineTextField field, MarkdownSourceEdits.Result result) {
        if (result.text().length()>32_768) { formatNotice=text("too_long"); return false; }
        descriptionEditor.setValue(result.text());
        // Recreate the selection through vanilla cursor methods so scrolling and selection painting stay synchronized.
        field.setSelecting(false);
        field.seekCursor(Whence.ABSOLUTE, result.selectionStart());
        field.setSelecting(true);
        field.seekCursor(Whence.ABSOLUTE, result.selectionEnd());
        field.setSelecting(false);
        setFocused(descriptionEditor);
        rememberWidgets();
        return true;
    }

    private void restoreChildSelection() {
        if (reopenNotice != null) { formatNotice = reopenNotice; reopenNotice = null; }
    }

    private RichDocument.LinkDestination previewLinkAt(double mouseX, double mouseY) {
        if (renderedPreview == null || renderedPreviewBounds == null
                || !renderedPreviewBounds.containsExclusive(mouseX, mouseY)) return null;
        int localX = (int) Math.floor(mouseX) - renderedDocumentX;
        int localY = (int) Math.floor(mouseY) - renderedDocumentY;
        int viewportTop = renderedPreviewBounds.top() - renderedDocumentY;
        DocumentLayout.Bounds viewport = new DocumentLayout.Bounds(0, viewportTop,
                renderedPreviewBounds.width(), viewportTop + renderedPreviewBounds.height());
        return DocumentView.linkAt(renderedPreview.layout(), localX, localY, viewport);
    }

    private Component previewLinkHint(RichDocument.LinkDestination destination) {
        if (destination instanceof RichDocument.ExternalLink external)
            return Component.literal(external.uri().toString());
        if (!(destination instanceof RichDocument.QuestLink questLink))
            return Component.translatable("screen.brnquest.markdown.quest_link.unavailable");
        ResourceLocation targetId = ResourceLocation.tryParse(questLink.questId());
        var snapshot = ClientEditorState.get().displayDraft()
                .orElseGet(() -> ClientQuestState.get().book().orElse(null));
        QuestDefinition target = snapshot == null || targetId == null ? null : snapshot.quests().get(targetId);
        if (target == null) return Component.translatable("screen.brnquest.markdown.quest_link.unavailable");
        String locale = locales.get(localeIndex);
        String title = BookText.quest(snapshot.book(), target, locale, "title", target.title());
        if (title.isBlank()) title = target.id().toString();
        String chapter = BookText.structureTitle(snapshot.book(), "chapter", target.chapterId(), locale,
                target.chapterId().toString());
        return Component.translatable("screen.brnquest.markdown.quest_link.open", NativeText.component(title), chapter);
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
        descriptionEditor.visible = descriptionEditor.active = sourceVisible();
        titleEditor.visible = titleEditor.active = metadataShown();
        subtitleEditor.visible = subtitleEditor.active = metadataShown();
    }
    private void restoreCursors() {
        CursorState state = cursors.get(locales.get(localeIndex));
        if (state == null) return;
        titleEditor.setCursorPosition(state.titleCursor()); titleEditor.setHighlightPos(state.titleAnchor());
        subtitleEditor.setCursorPosition(state.subtitleCursor()); subtitleEditor.setHighlightPos(state.subtitleAnchor());
        var field = ((MultiLineEditBoxAccessor)descriptionEditor).brnquest$textField();
        field.setSelecting(false); field.seekCursor(Whence.ABSOLUTE, state.bodyAnchor());
        field.setSelecting(true); field.seekCursor(Whence.ABSOLUTE, state.bodyCursor()); field.setSelecting(false);
        ((EditorSourceField) descriptionEditor).restoreScroll(state.sourceScroll());
        previewScroll.snap(state.previewScroll());
    }
    private boolean sourceVisible() { return preferredView != TextEditorLayout.View.PREVIEW; }
    private boolean previewVisible() { return preferredView == TextEditorLayout.View.PREVIEW || layoutModel().split; }

    @Override
    public void onClose() {
        rememberWidgets();
        if (!submitted) discardRecovery.run();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private boolean metadataShown() { return metadataExpanded && height >= 260; }
    private int viewButtonWidth(TextEditorLayout.View view) { return Math.max(46, font.width(text("view."+view.name().toLowerCase(java.util.Locale.ROOT)))+16); }
    private int viewControlsWidth() { return java.util.Arrays.stream(TextEditorLayout.View.values()).mapToInt(this::viewButtonWidth).sum()+6; }
    private int headerControlsWidth() { return 230 + viewControlsWidth(); }
    private TextEditorLayout layoutModel() {
        return new TextEditorLayout(width, height, preferredView, preferredRatio, metadataShown() ? 2 : 0, headerControlsWidth());
    }
    private UiRect panelBounds() { return layoutModel().panel; }
    private UiRect localeBounds() { var p=panelBounds(); return new UiRect(p.left()+10,p.top()+24,p.left()+80,p.top()+44); }
    private UiRect addLocaleBounds() { var p=panelBounds(); return new UiRect(p.left()+83,p.top()+24,p.left()+103,p.top()+44); }
    private UiRect metadataToggleBounds() { var p=panelBounds(); return new UiRect(p.right()-66,p.top()+24,p.right()-10,p.top()+44); }
    private UiRect formatBounds() { var p=panelBounds(); return new UiRect(p.left()+106,p.top()+24,p.left()+Math.min(204,p.width()-70),p.top()+44); }
    private UiRect viewBounds(TextEditorLayout.View view) {
        var p=panelBounds(); boolean singleRow=p.width()-24>=headerControlsWidth();
        int left=singleRow ? p.left()+210 : p.left()+10;
        for (var before:TextEditorLayout.View.values()) { if(before==view) break; left+=viewButtonWidth(before)+3; }
        int top=p.top()+(singleRow?24:48);
        return new UiRect(left,top,left+viewButtonWidth(view),top+20);
    }
    private UiRect titleBounds() { var p=panelBounds(); int top=layoutModel().metadataTop; return new UiRect(p.left()+72,top,p.right()-10,top+20); }
    private UiRect subtitleBounds() { var p=panelBounds(); int top=layoutModel().metadataTop+24; return new UiRect(p.left()+72,top,p.right()-10,top+20); }
    private int targetWidth() { return Math.min(100,layoutModel().body.width()/3); }
    private List<String> toolbarCandidates() {
        return nativeTarget() ? List.of("BOLD","ITALIC","COLOR","UNDERLINE","STRIKETHROUGH","RESET")
                : List.of("BOLD","ITALIC","COLOR","UNDERLINE","STRIKETHROUGH","HEADING","LIST","LINK","QUEST_LINK","TEXTURE","ITEM","CODE");
    }
    private Component actionLabel(String key) {
        return switch(key) {
            case "RESET" -> text("reset_short");
            case "TEXTURE" -> text("image_short");
            case "ITEM" -> text("item_short");
            default -> Component.literal(toolLabel(MarkdownSourceEdits.Tool.valueOf(key)));
        };
    }
    private Component actionHint(String key) {
        return switch(key) {
            case "RESET" -> text("reset");
            case "TEXTURE","ITEM" -> Component.translatable("screen.brnquest.editor.markdown.tool."+key.toLowerCase(java.util.Locale.ROOT));
            default -> Component.translatable(toolTranslation(MarkdownSourceEdits.Tool.valueOf(key)));
        };
    }
    private TextEditorToolbar.Layout toolbarLayout() {
        var body=layoutModel().body;
        var row=new UiRect(body.left(),layoutModel().toolbarTop,body.right(),layoutModel().toolbarTop+20);
        var actions=toolbarCandidates().stream().map(key -> new TextEditorToolbar.Action(key,Math.max(23,font.width(actionLabel(key))+16))).toList();
        return TextEditorToolbar.layout(row,targetWidth(),font.width(text("more"))+16,actions);
    }
    private UiRect moreBounds() { return toolbarLayout().more(); }
    private UiRect sourceBounds() {
        var pane=layoutModel().source;
        // Vanilla draws its source scrollbar outside the widget; reserve space before the divider's hit area.
        return new UiRect(pane.left(),pane.top(),Math.max(pane.left()+1,pane.right()-8),pane.bottom());
    }
    private UiRect previewBounds() { return layoutModel().preview; }
    private UiRect cancelBounds() { var p=panelBounds(); int size=font.width(Component.translatable("gui.cancel"))+24; return new UiRect(p.left()+10,p.bottom()-32,p.left()+10+size,p.bottom()-10); }
    private UiRect applyBounds() { var p=panelBounds(); int size=font.width(Component.translatable("gui.done"))+24; return new UiRect(p.right()-10-size,p.bottom()-32,p.right()-10,p.bottom()-10); }

    private static String toolLabel(MarkdownSourceEdits.Tool tool) {
        return switch (tool) {
            case HEADING -> "H";
            case BOLD -> "B";
            case ITALIC -> "I";
            case LIST -> "•";
            case CODE -> "`";
            case LINK -> "↗";
            case QUEST_LINK -> "Q→";
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
