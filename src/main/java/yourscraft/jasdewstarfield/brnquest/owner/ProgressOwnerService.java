package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Optional;
import java.util.Set;

/** Resolves the single active owner policy used by progress transactions. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class ProgressOwnerService {
    private ProgressOwnerService() {}

    /**
     * Returns the current immutable owner view on the server thread.
     * Stage 3 intentionally activates only personal ownership.
     */
    public static Optional<ProgressOwnerView> resolve(ServerPlayer player) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) {
            return Optional.empty();
        }
        ProgressOwnerProvider provider = ProgressOwnerProviderRegistry.get(PersonalProgressOwnerProvider.ID);
        if (provider == null) return Optional.empty();
        try {
            Optional<ProgressOwnerId> resolved = provider.resolve(player);
            if (resolved == null || resolved.isEmpty()) return Optional.empty();
            ProgressOwnerId owner = resolved.orElseThrow();
            if (!owner.providerId().equals(provider.id())) return Optional.empty();
            Set<java.util.UUID> members = Set.copyOf(provider.members(player.getServer(), owner));
            // An owner that does not contain the resolving player is unsafe to activate.
            if (!members.contains(player.getUUID())) return Optional.empty();
            return Optional.of(new ProgressOwnerView(owner, members,
                    provider.lifecycle(player.getServer(), owner)));
        } catch (RuntimeException | LinkageError exception) {
            // A broken optional provider must fail closed instead of selecting guessed identity.
            BRNQuest.LOGGER.error("[BRNQuest/OWNER] Provider {} failed to resolve {}; refusing owner",
                    provider.id(), player.getUUID(), exception);
            return Optional.empty();
        }
    }

    /** Internal transaction helper: failure is explicit instead of falling back to a guessed owner. */
    @ApiStatus(ApiStability.INTERNAL)
    public static ProgressOwnerId require(ServerPlayer player) {
        return resolve(player).filter(owner -> owner.lifecycle() == ProgressOwnerLifecycle.ACTIVE)
                .map(ProgressOwnerView::id)
                .orElseThrow(() -> new IllegalStateException("No safe progress owner for player"));
    }
}
