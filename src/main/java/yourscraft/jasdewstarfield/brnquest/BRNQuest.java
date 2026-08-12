package yourscraft.jasdewstarfield.brnquest;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * NeoForge entry point for BRNQuest.
 *
 * <p>The entry point intentionally stays small. Feature registration will live in focused packages so
 * loader-specific wiring does not leak into the data, progress, or public API layers.</p>
 */
@Mod(BRNQuest.MOD_ID)
public final class BRNQuest {
    public static final String MOD_ID = "brnquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BRNQuest(IEventBus modEventBus, ModContainer modContainer) {
        // Keep both loader-provided handles in the constructor signature for upcoming registrations.
        LOGGER.info("[BRNQuest] Initializing the NeoForge 1.21.1 development scaffold");
    }
}
