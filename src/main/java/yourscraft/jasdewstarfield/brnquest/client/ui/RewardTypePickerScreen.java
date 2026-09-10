package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The existing creation picker, restricted to rewards that explicitly support table composition. */
final class RewardTypePickerScreen extends RewardEditorScreen {
    private final EditorPickerList<ResourceLocation> picker = new EditorPickerList<>();
    private final Consumer<ResourceLocation> select;
    private final List<ResourceLocation> types;

    RewardTypePickerScreen(Screen parent, Consumer<ResourceLocation> select) {
        super(parent, Component.translatable("screen.brnquest.editor.typed.type_heading"));
        this.select = select;
        types = QuestTypePickerModel.creatableTypeCandidates(RewardTypeRegistry.registeredIds(),
                id -> id.toString().equals("brnquest:reward_table") || RewardTypeRegistry.get(id).composition().isPresent(), QuestTypedEntryKind.REWARD.hiddenLegacyAlias());
    }

    @Override protected void init() { super.init(); picker.invalidate(); }

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderPanel(graphics, x, y, partial);
        picker.advance(body(), panel(), types.size(), types::get, frameSeconds(), scrollSpeed());
        var tooltip = picker.render(graphics, font, title, false, type -> new EditorPickerList.Entry(
                ClientRewardPresentationRegistry.get(type).typeName(new RewardView(type, type, type, Map.of(), "manual", false)),
                Component.translatable("screen.brnquest.editor.typed.click_to_configure"),
                EditorPickerList.Tone.NORMAL, false, List.of(Component.literal(type.toString()))),
                Component.translatable("screen.brnquest.editor.typed.empty"), x, y);
        var p = panel();
        controls.setActions(List.of(button("cancel", Component.translatable("gui.cancel"),
                new UiRect(p.left() + 12, p.bottom() - 30, p.right() - 12, p.bottom() - 10),
                true, EditorButton.Tone.NEUTRAL, this::onClose)));
        controls.render(graphics, font, x, y);
        if (!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, x, y);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (picker.mouseClicked(x, y, button)) return true;
        var selected = picker.entryAt(x, y);
        if (button == 0 && selected.isPresent()) {
            select.accept(selected.orElseThrow());
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return picker.mouseScrolled(x, y, vertical, scrollStep()) || super.mouseScrolled(x, y, horizontal, vertical);
    }
}
