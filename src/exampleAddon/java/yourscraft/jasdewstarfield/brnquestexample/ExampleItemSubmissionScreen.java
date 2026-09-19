package yourscraft.jasdewstarfield.brnquestexample;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;

import java.util.*;
import java.util.function.Consumer;

/** Companion-owned inventory page; only bounded slot indices leave this screen. */
final class ExampleItemSubmissionScreen extends Screen {
    private final Screen parent;
    private final Map<String, String> config;
    private final Consumer<TaskSubmissionSelection> submit;
    private final Set<Integer> selected = new LinkedHashSet<>();
    private final List<Button> slots = new ArrayList<>();
    private Button confirm;
    private boolean sent;

    ExampleItemSubmissionScreen(Screen parent, Map<String, String> config, Consumer<TaskSubmissionSelection> submit) {
        super(Component.translatable("screen.brnquest_example.item.select"));
        this.parent = parent;
        this.config = Map.copyOf(config);
        this.submit = submit;
    }
    private int left() { return (width - 208) / 2; }
    private int top() { return (height - 178) / 2; }

    @Override protected void init() {
        slots.clear();
        for (int slot = 0; slot < 36; slot++) {
            int index = slot;
            slots.add(addRenderableWidget(Button.builder(Component.empty(), button -> {
                if (!selected.remove(index)) selected.add(index);
                updateButtons();
            }).bounds(left() + 14 + slot % 9 * 20, top() + 48 + slot / 9 * 20, 20, 20).build()));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left() + 14, top() + 146, 84, 20).build());
        confirm = addRenderableWidget(Button.builder(Component.translatable("screen.brnquest_example.item.submit"), button -> {
            if (!ready()) return;
            sent = true;
            submit.accept(new TaskSubmissionSelection(List.copyOf(selected)));
            onClose();
        }).bounds(left() + 110, top() + 146, 84, 20).build());
        updateButtons();
    }

    private int selectedCount() {
        if (minecraft == null || minecraft.player == null) return 0;
        int count = 0;
        for (int slot : selected) {
            var stack = minecraft.player.getInventory().getItem(slot);
            if (!ExampleItemTask.matches(config, stack)) return 0;
            count += stack.getCount();
        }
        return count;
    }
    private boolean ready() { return !sent && !selected.isEmpty() && selectedCount() >= ExampleItemTask.count(config); }
    private void updateButtons() {
        if (minecraft == null || minecraft.player == null) return;
        for (int slot = 0; slot < slots.size(); slot++) {
            slots.get(slot).active = !sent && (selected.contains(slot)
                    || ExampleItemTask.matches(config, minecraft.player.getInventory().getItem(slot)));
        }
        if (confirm != null) confirm.active = ready();
    }
    @Override public void tick() { parent.tick(); updateButtons(); }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    /** Screen.render also invokes this hook; keep its second blur pass above the panel disabled. */
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The suspended quest page is the backdrop. Resize only when needed to preserve its UI state.
        if (parent.width != width || parent.height != height) parent.resize(minecraft, width, height);
        parent.render(graphics, -1, -1, partialTick);
        graphics.flush();
        // Parent items can leave higher depth values; retain their colors while starting a fresh child layer.
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                net.minecraft.client.Minecraft.ON_OSX);
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, 0x88000000);
        graphics.fill(left(), top(), left() + 208, top() + 178, 0xFF282828);
        graphics.renderOutline(left(), top(), 208, 178, 0xFF999999);
        graphics.drawCenteredString(font, title, width / 2, top() + 12, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest_example.item.selected",
                selectedCount(), ExampleItemTask.count(config)), width / 2, top() + 29, 0xFFDDDDDD);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (minecraft == null || minecraft.player == null) return;
        for (int slot = 0; slot < slots.size(); slot++) {
            var button = slots.get(slot);
            var stack = minecraft.player.getInventory().getItem(slot);
            graphics.renderItem(stack, button.getX() + 2, button.getY() + 2);
            graphics.renderItemDecorations(font, stack, button.getX() + 2, button.getY() + 2);
            if (selected.contains(slot)) graphics.renderOutline(button.getX(), button.getY(), 20, 20, 0xFF80D49B);
        }
        // Tooltips are drawn last, above every slot and button.
        for (int slot = 0; slot < slots.size(); slot++) {
            var stack = minecraft.player.getInventory().getItem(slot);
            if (slots.get(slot).isMouseOver(mouseX, mouseY) && !stack.isEmpty()) graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }
}
