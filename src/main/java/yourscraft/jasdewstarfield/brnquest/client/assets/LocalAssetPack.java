package yourscraft.jasdewstarfield.brnquest.client.assets;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Automatically mounted client-only pack; never added to server data packs or book publication. */
@EventBusSubscriber(modid = "brnquest", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LocalAssetPack {
    private LocalAssetPack() {}
    public static LocalAssetStore store() {
        return new LocalAssetStore(FMLPaths.GAMEDIR.get().resolve("brnquest/local-assets"));
    }
    @SubscribeEvent public static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        event.addRepositorySource(consumer -> {
            try { consumer.accept(create(store())); }
            catch (IOException failure) { LogUtils.getLogger().error("Cannot open BRNQuest local asset library", failure); }
        });
    }
    public static Pack create(LocalAssetStore store) throws IOException {
        store.initialize();
        var location = new PackLocationInfo("brnquest/local-assets", Component.literal("BRNQuest local assets"), PackSource.BUILT_IN, Optional.empty());
        // Metadata is supplied by the mod, so users never have to create or maintain pack.mcmeta.
        return new Pack(location, new PathPackResources.PathResourcesSupplier(store.root()),
                new Pack.Metadata(Component.literal("Local imported PNG files"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of(), true),
                new PackSelectionConfig(true, Pack.Position.TOP, true));
    }
}
