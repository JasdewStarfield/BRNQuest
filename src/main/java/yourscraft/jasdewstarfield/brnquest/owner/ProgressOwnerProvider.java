package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-only owner identity provider.
 *
 * <p>Resolution must use a durable provider ID and UUID. Display names, mutable team
 * names, and inferred leader UUIDs are not valid substitutes for owner identity.</p>
 */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ProgressOwnerProvider {
    ResourceLocation id();

    /** Resolves the owner for an online player on the server thread. */
    Optional<ProgressOwnerId> resolve(ServerPlayer player);

    /** Returns an immutable member snapshot for authorization and synchronization. */
    Set<UUID> members(MinecraftServer server, ProgressOwnerId owner);

    /** Reports whether the provider can still resolve the stable owner. */
    ProgressOwnerLifecycle lifecycle(MinecraftServer server, ProgressOwnerId owner);

    /** Returns archival evidence when the owner has left the active provider namespace. */
    Optional<ProgressOwnerArchive> archivedSnapshot(MinecraftServer server, ProgressOwnerId owner);
}
