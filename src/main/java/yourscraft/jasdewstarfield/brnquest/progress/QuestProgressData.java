package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-level personal progress store for schema 1. */
public final class QuestProgressData extends SavedData {
    private static final Factory<QuestProgressData> FACTORY = new Factory<>(QuestProgressData::new, QuestProgressData::load);
    private final Map<UUID, PlayerProgress> players = new HashMap<>();

    public PlayerProgress get(UUID playerId) { return players.computeIfAbsent(playerId, ignored -> new PlayerProgress()); }
    public static QuestProgressData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "brnquest_progress"); }

    private static QuestProgressData load(CompoundTag tag, HolderLookup.Provider lookup) {
        QuestProgressData data = new QuestProgressData();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try { data.players.put(UUID.fromString(key), PlayerProgress.load(players.getCompound(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookup) {
        tag.putInt("schema_version", BrnQuestConstants.PROGRESS_SCHEMA);
        CompoundTag playersTag = new CompoundTag();
        players.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> playersTag.put(e.getKey().toString(), e.getValue().save()));
        tag.put("players", playersTag);
        return tag;
    }
}
