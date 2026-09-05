package yourscraft.jasdewstarfield.brnquest.compat.opac;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read-only adapter: no OPAC object escapes a query or survives across ticks. */
public final class OpenPacProgressOwnerProvider implements ProgressOwnerProvider {
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("brnquest", "openpac"); }
    private void checkThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("OPAC requires the server thread");
    }
    public Optional<ProgressOwnerId> resolve(ServerPlayer player) {
        checkThread(player.getServer());
        var party = OpenPACServerAPI.get(player.getServer()).getPartyManager().getPartyByMember(player.getUUID());
        return party == null ? Optional.empty() : Optional.of(new ProgressOwnerId(id(), party.getId()));
    }
    public Set<UUID> members(MinecraftServer server, ProgressOwnerId owner) {
        checkThread(server);
        if (!owner.providerId().equals(id())) return Set.of();
        var party = OpenPACServerAPI.get(server).getPartyManager().getPartyById(owner.ownerId());
        return party == null ? Set.of() : party.getMemberInfoStream().map(member -> member.getUUID())
                .collect(Collectors.toUnmodifiableSet());
    }
    public ProgressOwnerLifecycle lifecycle(MinecraftServer server, ProgressOwnerId owner) {
        checkThread(server);
        if (!owner.providerId().equals(id())) return ProgressOwnerLifecycle.UNAVAILABLE;
        return OpenPACServerAPI.get(server).getPartyManager().getPartyById(owner.ownerId()) == null
                ? ProgressOwnerLifecycle.ARCHIVED : ProgressOwnerLifecycle.ACTIVE;
    }
    public Optional<ProgressOwnerArchive> archivedSnapshot(MinecraftServer server, ProgressOwnerId owner) {
        checkThread(server);
        return yourscraft.jasdewstarfield.brnquest.progress.QuestProgressData.get(server).archive(owner);
    }
}
