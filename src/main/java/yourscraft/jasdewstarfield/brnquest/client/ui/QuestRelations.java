package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import java.util.*;
import java.util.function.Predicate;

/** Direct graph neighbors only; hidden quests never leak through the navigation list. */
record QuestRelations(List<QuestDefinition> upstream, List<QuestDefinition> downstream) {
    static final int UPSTREAM_COLOR = 0xFF68C8ED;
    static final int DOWNSTREAM_COLOR = 0xFFF1B96A;
    QuestRelations { upstream = List.copyOf(upstream); downstream = List.copyOf(downstream); }
    static QuestRelations resolve(Collection<QuestDefinition> quests, ResourceLocation focused, Predicate<QuestDefinition> visible) {
        var source = quests.stream().filter(q -> q.id().equals(focused)).findFirst().orElse(null);
        if (source == null) return new QuestRelations(List.of(), List.of());
        var parents = new HashSet<>(source.dependencies());
        return new QuestRelations(quests.stream().filter(visible).filter(q -> parents.contains(q.id())).toList(),
                quests.stream().filter(visible).filter(q -> q.dependencies().contains(focused)).toList());
    }
    boolean contains(ResourceLocation id) {
        return id != null && (upstream.stream().anyMatch(q -> q.id().equals(id)) || downstream.stream().anyMatch(q -> q.id().equals(id)));
    }
    /** Parent-to-child orientation is shared by the renderer and detail list legend. */
    static int edgeColor(ResourceLocation parent, ResourceLocation child, ResourceLocation focused, int normal) {
        if (focused == null) return normal;
        if (focused.equals(child)) return UPSTREAM_COLOR;
        if (focused.equals(parent)) return DOWNSTREAM_COLOR;
        return normal;
    }
}
