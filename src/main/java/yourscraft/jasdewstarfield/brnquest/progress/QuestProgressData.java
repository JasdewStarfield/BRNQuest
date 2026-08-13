package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level progress store; schema 1 persists the active personal owner's UUID. */
public final class QuestProgressData extends SavedData {
    private static final Factory<QuestProgressData> FACTORY = new Factory<>(QuestProgressData::new, QuestProgressData::load);
    private final Map<UUID, PlayerProgress> owners = new HashMap<>();

    /** Owner resolution happens before storage access; only personal owners are active in schema 1. */
    public PlayerProgress get(ProgressOwnerId owner) {
        return owners.computeIfAbsent(owner.ownerId(), ignored -> new PlayerProgress());
    }
    public static QuestProgressData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "brnquest_progress"); }

    private static QuestProgressData load(CompoundTag tag, HolderLookup.Provider lookup) {
        QuestProgressData data = new QuestProgressData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try { data.owners.put(UUID.fromString(key), PlayerProgress.load(players.getCompound(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookup) {
        tag.putInt("schema_version", BrnQuestConstants.PROGRESS_SCHEMA);
        CompoundTag playersTag = new CompoundTag();
        // Retain the schema-1 "players" field until a non-personal provider is deliberately enabled.
        owners.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> playersTag.put(e.getKey().toString(), e.getValue().save()));
        tag.put("players", playersTag);
        return tag;
    }
}
