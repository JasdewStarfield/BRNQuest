package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import yourscraft.jasdewstarfield.brnquest.client.mixin.ClientTextTooltipAccessor;
import java.util.ArrayList;
import java.util.List;

/** Bound every final tooltip text path, including callers that supply preformatted lines directly. */
public final class EditorTooltipLayout {
    private EditorTooltipLayout() {}

    public static boolean owns(Screen screen) {
        if (screen == null) return false;
        String name = screen.getClass().getName();
        return name.startsWith("yourscraft.jasdewstarfield.brnquest.client.ui.")
                || name.startsWith("yourscraft.jasdewstarfield.brnquest.builtin.client.");
    }

    public static List<ClientTooltipComponent> wrap(Font font, List<ClientTooltipComponent> components) {
        Screen screen = Minecraft.getInstance().screen;
        if (!owns(screen) || components.isEmpty() || TextLayoutDebug.drawingTooltip()) return components;
        int limit = TooltipTextLayout.maximumWidth(screen.width);
        int originalWidth = components.stream().mapToInt(part -> part.getWidth(font)).max().orElse(0);
        var result = new ArrayList<ClientTooltipComponent>();
        for (ClientTooltipComponent part : components) {
            if (part instanceof ClientTextTooltip text) {
                var sequence = ((ClientTextTooltipAccessor)text).brnquest$text();
                var lines = TooltipTextLayout.wrap(sequence, limit, glyph -> font.getSplitter().stringWidth(
                        sink -> sink.accept(0, glyph.style(), glyph.codePoint())));
                lines.forEach(line -> result.add(ClientTooltipComponent.create(line)));
            } else result.add(part); // Item images and other mods' custom tooltip components retain their behavior.
        }
        int width = result.stream().mapToInt(part -> part.getWidth(font)).max().orElse(0);
        // Match GuiGraphics' tooltip background dimensions, including its single-line height adjustment.
        int height = result.stream().mapToInt(ClientTooltipComponent::getHeight).sum() + (result.size() == 1 ? -2 : 0);
        TextLayoutDebug.normalTooltip(originalWidth, width, height, limit, Math.max(0, screen.height - 12),
                components.size(), result.size());
        return result;
    }
}
