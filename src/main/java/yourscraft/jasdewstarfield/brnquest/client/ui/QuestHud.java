package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ClientQuestState;

/** Minimal tracked-quest HUD rendered only from the immutable client cache. */
public final class QuestHud {
    public static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("brnquest", "tracked_quest");
    private QuestHud() {}
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;
        ClientQuestState.get().trackedQuest().flatMap(id -> ClientQuestState.get().book().map(s -> s.quests().get(id))).ifPresent(quest -> {
            if (quest == null) return;
            int width = minecraft.font.width(quest.title()) + 16;
            graphics.fill(graphics.guiWidth() - width - 8, 10, graphics.guiWidth() - 8, 30, 0xB010141C);
            graphics.drawString(minecraft.font, quest.title(), graphics.guiWidth() - width, 16, 0xFFFFFF, false);
        });
    }
}
