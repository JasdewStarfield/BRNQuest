package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import java.io.DataInputStream;
import java.util.*;

/** Client-only resource catalog shared by icons, artwork and future document images. No binary upload or URL loading. */
@EventBusSubscriber(modid = "brnquest", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LoadedTextures {
    private static List<ResourceLocation> catalog;
    private static final Map<ResourceLocation, Size> sizes = new HashMap<>();
    private static long generation;
    public record Size(int width, int height, boolean present) {}
    private LoadedTextures() {}
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            catalog = null; sizes.clear(); generation++;
        });
    }
    public static long generation() { return generation; }
    public static List<ResourceLocation> all() {
        if (catalog == null) catalog = Minecraft.getInstance().getResourceManager()
                .listResources("textures", id -> id.getPath().endsWith(".png")).keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        return catalog;
    }
    /** Read only the PNG header, avoiding full image decoding just to preview a resource. */
    public static Size size(ResourceLocation id) {
        return sizes.computeIfAbsent(id, key -> {
            try {
                var resource = Minecraft.getInstance().getResourceManager().getResource(key);
                if (resource.isPresent()) try (var input = new DataInputStream(resource.get().open())) {
                    if (input.readLong() != 0x89504E470D0A1A0AL || input.readInt() != 13 || input.readInt() != 0x49484452)
                        return new Size(16, 16, false);
                    int w = input.readInt(), h = input.readInt();
                    if (w > 0 && h > 0 && w <= 32768 && h <= 32768) return new Size(w, h, true);
                }
            } catch (Exception ignored) { /* Missing/broken packs leave a visible, recoverable placeholder. */ }
            return new Size(16, 16, false);
        });
    }
    public static void draw(GuiGraphics g, String texture, int x, int y, int w, int h, double opacity) {
        if (w <= 0 || h <= 0 || opacity <= 0) return;
        ResourceLocation id = ResourceLocation.tryParse(texture);
        Size size = id == null ? new Size(16, 16, false) : size(id);
        if (!size.present()) {
            int alpha = (int) Math.round(opacity * 255) << 24;
            g.fill(x, y, x + w, y + h, alpha | 0x572958);
            g.renderOutline(x, y, w, h, alpha | 0xE3A8E3);
            return;
        }
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1, 1, 1, (float) opacity);
        try { g.blit(id, x, y, w, h, 0F, 0F, size.width(), size.height(), size.width(), size.height()); }
        finally { g.setColor(1, 1, 1, 1); com.mojang.blaze3d.systems.RenderSystem.disableBlend(); }
    }
}
