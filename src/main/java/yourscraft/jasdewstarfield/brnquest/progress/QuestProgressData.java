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
    private final Map<UUID, ProgressOwnerId> samplingOwners = new HashMap<>();
    /** Switching away pauses the old member read positions, so time spent in another owner is not attributed twice. */
    public void activateSamplingOwner(UUID player, ProgressOwnerId owner) {
        ProgressOwnerId old = samplingOwners.put(player, owner);
        if (old != null && !old.equals(owner)) get(old).suspendSamples(player);
        if (!Objects.equals(old, owner)) setDirty();
    }

    public PlayerProgress get(ProgressOwnerId owner) { return owners.computeIfAbsent(owner, ignored -> new PlayerProgress()); }
    public Set<ProgressOwnerId> ownerIds() { return Set.copyOf(owners.keySet()); }
    public Optional<ProgressOwnerArchive> archive(ProgressOwnerId owner) { return Optional.ofNullable(archives.get(owner)); }
    public String tracked(UUID player) { return tracked.getOrDefault(player, ""); }
    public void tracked(UUID player, String quest) { tracked.put(player, quest); setDirty(); }
    /** A successful authoritative query can restore the same UUID; errors never manufacture deletion. */
    public void observe(ProgressOwnerId owner, Set<UUID> current, ProgressOwnerLifecycle lifecycle) {
        if (lifecycle == ProgressOwnerLifecycle.ACTIVE) {
            var previous = members.put(owner, Set.copyOf(current));
            if (previous != null) previous.stream().filter(member -> !current.contains(member)).forEach(get(owner)::suspendSamples);
            boolean changed = !current.equals(previous);
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
        CompoundTag samplingOwners = tag.getCompound("sampling_owners");
        for (String player : samplingOwners.getAllKeys()) try {
            var entry = samplingOwners.getCompound(player);
            data.samplingOwners.put(UUID.fromString(player), new ProgressOwnerId(ResourceLocation.parse(entry.getString("provider")), entry.getUUID("owner")));
        } catch (IllegalArgumentException ignored) { /* Optional metadata does not affect ordinary ledger recovery. */ }
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
        CompoundTag sampling = new CompoundTag();
        samplingOwners.forEach((player, owner) -> {
            var entry = new CompoundTag();
            entry.putString("provider", owner.providerId().toString());
            entry.putUUID("owner", owner.ownerId());
            sampling.put(player.toString(), entry);
        });
        tag.put("sampling_owners", sampling);
        CompoundTag tracking = new CompoundTag();
        tracked.forEach((uuid, quest) -> tracking.putString(uuid.toString(), quest));
        tag.put("tracking", tracking);
        return tag;
    }
}
