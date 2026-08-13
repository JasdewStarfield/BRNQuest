package yourscraft.jasdewstarfield.brnquest.owner;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Built-in provider preserving the schema-1 one-ledger-per-player behavior. */
final class PersonalProgressOwnerProvider implements ProgressOwnerProvider {
    static final ResourceLocation ID = ProgressOwnerProviders.PERSONAL;
    static final PersonalProgressOwnerProvider INSTANCE = new PersonalProgressOwnerProvider();

    private PersonalProgressOwnerProvider() {}

    public ResourceLocation id() {
        return ID;
    }

    public Optional<ProgressOwnerId> resolve(ServerPlayer player) {
        return Optional.of(new ProgressOwnerId(ID, player.getUUID()));
    }

    public Set<UUID> members(MinecraftServer server, ProgressOwnerId owner) {
        return owner.providerId().equals(ID) ? Set.of(owner.ownerId()) : Set.of();
    }

    public ProgressOwnerLifecycle lifecycle(MinecraftServer server, ProgressOwnerId owner) {
        return owner.providerId().equals(ID) ? ProgressOwnerLifecycle.ACTIVE : ProgressOwnerLifecycle.UNAVAILABLE;
    }

    public Optional<ProgressOwnerArchive> archivedSnapshot(MinecraftServer server, ProgressOwnerId owner) {
        return Optional.empty();
    }
}
