package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import java.util.*;

/** Schema 2 separates provider namespaces and preserves unavailable/archived ledgers verbatim. */
public final class QuestProgressData extends SavedData {
    private static final Factory<QuestProgressData> FACTORY = new Factory<>(QuestProgressData::new, QuestProgressData::load);
    private final Map<ProgressOwnerId, PlayerProgress> owners = new HashMap<>();
    private final Map<ProgressOwnerId, Set<UUID>> members = new HashMap<>();
    private final Map<ProgressOwnerId, ProgressOwnerArchive> archives = new HashMap<>();
    private final Map<UUID, String> tracked = new HashMap<>();

    public PlayerProgress get(ProgressOwnerId owner) { return owners.computeIfAbsent(owner, ignored -> new PlayerProgress()); }
    public Set<ProgressOwnerId> ownerIds() { return Set.copyOf(owners.keySet()); }
    public Optional<ProgressOwnerArchive> archive(ProgressOwnerId owner) { return Optional.ofNullable(archives.get(owner)); }
    public String tracked(UUID player) { return tracked.getOrDefault(player, ""); }
    public void tracked(UUID player, String quest) { tracked.put(player, quest); setDirty(); }
    /** A successful authoritative query can restore the same UUID; errors never manufacture deletion. */
    public void observe(ProgressOwnerId owner, Set<UUID> current, ProgressOwnerLifecycle lifecycle) {
        if (lifecycle == ProgressOwnerLifecycle.ACTIVE) {
            boolean changed = !current.equals(members.put(owner, Set.copyOf(current)));
            changed |= archives.remove(owner) != null;
            if (changed) setDirty();
        } else if (lifecycle == ProgressOwnerLifecycle.ARCHIVED && !archives.containsKey(owner)) {
            archives.put(owner, new ProgressOwnerArchive(owner, members.getOrDefault(owner, Set.of()),
                    System.currentTimeMillis(), "party_missing"));
            setDirty();
        }
    }
    public static QuestProgressData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "brnquest_progress");
    }
    public static QuestProgressData load(CompoundTag tag, HolderLookup.Provider lookup) {
        QuestProgressData data = new QuestProgressData();
        // Import schema-1 players exactly once into the personal namespace, without any team copy.
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try { data.owners.put(new ProgressOwnerId(ProgressOwnerProviders.PERSONAL, UUID.fromString(key)),
                    PlayerProgress.load(players.getCompound(key))); }
            catch (IllegalArgumentException ignored) { }
        }
        CompoundTag namespaces = tag.getCompound("owners");
        for (String provider : namespaces.getAllKeys()) {
            CompoundTag entries = namespaces.getCompound(provider);
            for (String uuid : entries.getAllKeys()) {
                try {
                    ProgressOwnerId id = new ProgressOwnerId(ResourceLocation.parse(provider), UUID.fromString(uuid));
                    CompoundTag entry = entries.getCompound(uuid);
                    data.owners.put(id, PlayerProgress.load(entry.getCompound("progress")));
                    Set<UUID> known = readMembers(entry.getCompound("members"));
                    data.members.put(id, known);
                    if (entry.contains("archived_at")) data.archives.put(id,
                            new ProgressOwnerArchive(id, known, entry.getLong("archived_at"), entry.getString("reason")));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        CompoundTag tracking = tag.getCompound("tracking");
        for (String player : tracking.getAllKeys()) {
            try { data.tracked.put(UUID.fromString(player), tracking.getString(player)); }
            catch (IllegalArgumentException ignored) { }
        }
        return data;
    }
    private static Set<UUID> readMembers(CompoundTag tag) {
        Set<UUID> result = new HashSet<>();
        for (String key : tag.getAllKeys()) {
            try { result.add(UUID.fromString(key)); } catch (IllegalArgumentException ignored) { }
        }
        return Set.copyOf(result);
    }
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider lookup) {
        tag.putInt("schema_version", BrnQuestConstants.PROGRESS_SCHEMA);
        tag.remove("players");
        CompoundTag namespaces = new CompoundTag();
        owners.forEach((id, progress) -> {
            String provider = id.providerId().toString();
            CompoundTag entries = namespaces.getCompound(provider);
            CompoundTag entry = new CompoundTag();
            entry.put("progress", progress.save());
            CompoundTag known = new CompoundTag();
            members.getOrDefault(id, Set.of()).forEach(uuid -> known.putBoolean(uuid.toString(), true));
            entry.put("members", known);
            ProgressOwnerArchive archive = archives.get(id);
            if (archive != null) {
                entry.putLong("archived_at", archive.archivedAtEpochMillis());
                entry.putString("reason", archive.reason());
            }
            entries.put(id.ownerId().toString(), entry);
            namespaces.put(provider, entries);
        });
        tag.put("owners", namespaces);
        CompoundTag tracking = new CompoundTag();
        tracked.forEach((uuid, quest) -> tracking.putString(uuid.toString(), quest));
        tag.put("tracking", tracking);
        return tag;
    }
}
