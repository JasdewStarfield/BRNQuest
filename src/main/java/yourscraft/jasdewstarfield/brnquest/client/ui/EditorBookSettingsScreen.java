package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.BookSettings;
import yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy;
import java.util.*;
import java.util.function.Consumer;

/** Small policy form stages changes locally, then returns them to the book-property transaction. */
final class EditorBookSettingsScreen extends Screen {
    private static final List<String> FIELDS = List.of("consume_items", "reward_team", "reward_claim_policy", "suppress_auto_claim", "pause_game", "share_team_progress");
    private final Screen parent;
    private final Consumer<BookSettings> selection;
    private final Map<String, String> values = new HashMap<>();
    private final EditorEnumDropdown enumDropdown = new EditorEnumDropdown();
    private EditorButtonWidget claimButton;
    EditorBookSettingsScreen(Screen parent, BookSettings initial, Consumer<BookSettings> selection) {
        super(Component.translatable("screen.brnquest.book.settings"));
        this.parent = parent; this.selection = selection; values.putAll(initial.values());
    }
    private int top() { return (height - 234) / 2; }
    private int panelWidth() { return Math.min(430, width - 28); }
    @Override protected void init() {
        int w = panelWidth(), left = (width - w) / 2;
        for (int i = 0; i < FIELDS.size(); i++) {
            String key = FIELDS.get(i);
            var button = addRenderableWidget(new EditorButtonWidget(left + w / 2, top() + 34 + i * 26, w / 2, 20,
                    key.equals("reward_claim_policy")
                            ? EditorButton.Definition.iconAndText(valueLabel(key), null, QuestActionIcons.named("unfold"))
                            : EditorButton.Definition.text(valueLabel(key), null), clicked -> {
                if (key.equals("reward_claim_policy")) {
                    var modes = Arrays.stream(RewardClaimPolicy.values()).map(RewardClaimPolicy::serializedName).toList();
                    enumDropdown.show(new UiRect(clicked.getX(), clicked.getY(), clicked.getX() + clicked.getWidth(),
                            clicked.getY() + clicked.getHeight()), modes,
                            value -> Component.translatable("screen.brnquest.book.claim." + value), value -> {
                                values.put(key, value);
                                clicked.setMessage(valueLabel(key));
                            });
                    claimButton.setIcon(QuestActionIcons.named("fold"));
                } else {
                    values.put(key, Boolean.toString(!Boolean.parseBoolean(values.get(key))));
                    clicked.setMessage(valueLabel(key));
                }
            }));
            if (key.equals("reward_claim_policy")) claimButton = button;
            button.setTooltip(Tooltip.create(Component.translatable("screen.brnquest.book.setting." + key + ".help")));
        }
        addRenderableWidget(new EditorButtonWidget(left, top() + 202, w / 2 - 4, 20, Component.translatable("gui.cancel"), button -> onClose()));
        addRenderableWidget(new EditorButtonWidget(left + w / 2 + 4, top() + 202, w / 2 - 4, 20,
                Component.translatable("screen.brnquest.editor.scope.apply_parent"), button -> {
            selection.accept(new BookSettings(Boolean.parseBoolean(values.get("consume_items")), Boolean.parseBoolean(values.get("reward_team")),
                    values.get("reward_claim_policy"), Boolean.parseBoolean(values.get("suppress_auto_claim")), Boolean.parseBoolean(values.get("pause_game")), Boolean.parseBoolean(values.get("share_team_progress"))));
            onClose();
        }));
    }
    private Component valueLabel(String key) {
        return key.equals("reward_claim_policy") ? Component.translatable("screen.brnquest.book.claim." + values.get(key))
                : Component.translatable(Boolean.parseBoolean(values.get(key)) ? "options.on" : "options.off");
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 1000);
        try {
            int w = panelWidth(), left = (width - w) / 2;
            graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
            GraystoneSurface.raised(graphics, new UiRect(left - 10, top(), left + w + 10, top() + 234), GraystonePalette.PANEL, true);
            graphics.drawCenteredString(font, title, width / 2, top() + 10, GraystonePalette.TEXT);
            for (int i = 0; i < FIELDS.size(); i++) EditorPropertyRow.label(graphics, font,
                    Component.translatable("screen.brnquest.book.setting." + FIELDS.get(i)),
                    new UiRect(left, top() + 34 + i * 26, left + w / 2 - 6, top() + 54 + i * 26), null);
            super.render(graphics, x, y, partial);
            enumDropdown.render(graphics, font, width, top() + 24, top() + 200, x, y);
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (enumDropdown.open()) {
            enumDropdown.click(x, y, button, width, top() + 24, top() + 200);
            claimButton.setIcon(null);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && enumDropdown.open()) { enumDropdown.close(); claimButton.setIcon(null); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
