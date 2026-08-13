package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable actor and authority attached to every context-aware write operation. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record OperationContext(Authority authority, String actorId, String actorName, String source) {
    public enum Authority { SELF, ADMINISTRATOR, INTEGRATION, SYSTEM }

    public OperationContext {
        Objects.requireNonNull(authority, "authority");
        actorId = requireText(actorId, "actorId");
        actorName = requireText(actorName, "actorName");
        source = requireText(source, "source");
    }

    public static OperationContext self(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return new OperationContext(Authority.SELF, player.getUUID().toString(), player.getScoreboardName(),
                "brnquest:player_api");
    }

    /** Returns empty instead of manufacturing administrator authority for an unprivileged source. */
    public static Optional<OperationContext> administrator(CommandSourceStack source) {
        Objects.requireNonNull(source, "source");
        if (!source.hasPermission(2)) return Optional.empty();
        return Optional.of(new OperationContext(Authority.ADMINISTRATOR, source.getTextName(), source.getTextName(),
                "brnquest:command"));
    }

    /** In-process integrations are trusted server code and must identify their owning namespace. */
    public static OperationContext integration(ResourceLocation integrationId) {
        Objects.requireNonNull(integrationId, "integrationId");
        return new OperationContext(Authority.INTEGRATION, integrationId.toString(), integrationId.toString(),
                integrationId.toString());
    }

    public static OperationContext system(String source) {
        return new OperationContext(Authority.SYSTEM, "brnquest:system", "BRNQuest", source);
    }

    public boolean mayModify(ServerPlayer target) {
        if (target == null) return false;
        return mayModify(target.getUUID());
    }

    public boolean mayModify(UUID targetId) {
        if (targetId == null) return false;
        if (authority != Authority.SELF) return true;
        return actorUuid().map(targetId::equals).orElse(false);
    }

    private Optional<UUID> actorUuid() {
        try {
            return Optional.of(UUID.fromString(actorId));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
