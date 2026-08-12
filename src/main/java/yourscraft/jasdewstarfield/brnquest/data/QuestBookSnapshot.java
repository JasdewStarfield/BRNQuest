package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Immutable indexed book with a content-derived revision. */
public record QuestBookSnapshot(QuestBookDefinition book, String revision,
                                Map<ResourceLocation, QuestDefinition> quests,
                                List<ResourceLocation> topologicalOrder) {
    public static QuestBookSnapshot of(QuestBookDefinition book) {
        String json = NativeBookJson.encode(book);
        try {
            String revision = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
            Map<ResourceLocation, QuestDefinition> quests = book.quests().stream().collect(Collectors.toUnmodifiableMap(QuestDefinition::id, Function.identity()));
            return new QuestBookSnapshot(book, revision, quests, topologicalOrder(quests));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by Java", impossible);
        }
    }

    public QuestBookSnapshot { quests = Map.copyOf(quests); topologicalOrder = List.copyOf(topologicalOrder); }

    private static List<ResourceLocation> topologicalOrder(Map<ResourceLocation, QuestDefinition> quests) {
        Map<ResourceLocation, Integer> indegree = new HashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> dependents = new HashMap<>();
        quests.keySet().forEach(id -> indegree.put(id, 0));
        quests.values().forEach(quest -> quest.dependencies().stream().filter(quests::containsKey).forEach(dependency -> {
            indegree.merge(quest.id(), 1, Integer::sum);
            dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(quest.id());
        }));
        PriorityQueue<ResourceLocation> ready = new PriorityQueue<>(Comparator.comparing(ResourceLocation::toString));
        indegree.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        List<ResourceLocation> result = new ArrayList<>();
        while (!ready.isEmpty()) {
            ResourceLocation id = ready.remove();
            result.add(id);
            dependents.getOrDefault(id, List.of()).stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(dependent -> {
                if (indegree.computeIfPresent(dependent, (ignored, value) -> value - 1) == 0) ready.add(dependent);
            });
        }
        return result;
    }
}
