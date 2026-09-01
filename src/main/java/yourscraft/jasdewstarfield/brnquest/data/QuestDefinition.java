package yourscraft.jasdewstarfield.brnquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Immutable quest node including layout, dependencies, tasks and rewards. */
public record QuestDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation chapterId,
                              String title, String subtitle, String description, String icon,
                              double x, double y, List<ResourceLocation> dependencies,
                              List<TaskDefinition> tasks, List<RewardDefinition> rewards, String legacyId,
                              QuestAppearance appearance, Map<String, String> extensions) {
    public static final Codec<QuestDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("book_id").forGetter(QuestDefinition::bookId),
            ResourceLocation.CODEC.fieldOf("id").forGetter(QuestDefinition::id),
            ResourceLocation.CODEC.fieldOf("chapter_id").forGetter(QuestDefinition::chapterId),
            Codec.STRING.optionalFieldOf("title", "").forGetter(QuestDefinition::title),
            Codec.STRING.optionalFieldOf("subtitle", "").forGetter(QuestDefinition::subtitle),
            Codec.STRING.optionalFieldOf("description", "").forGetter(QuestDefinition::description),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(QuestDefinition::icon),
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(QuestDefinition::x),
            Codec.DOUBLE.optionalFieldOf("y", 0.0).forGetter(QuestDefinition::y),
            ResourceLocation.CODEC.listOf().optionalFieldOf("dependencies", List.of()).forGetter(QuestDefinition::dependencies),
            TaskDefinition.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(QuestDefinition::tasks),
            RewardDefinition.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(QuestDefinition::rewards),
            Codec.STRING.optionalFieldOf("legacy_id", "").forGetter(QuestDefinition::legacyId),
            QuestAppearance.CODEC.optionalFieldOf("appearance", QuestAppearance.DEFAULT).forGetter(QuestDefinition::appearance),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("extensions", Map.of()).forGetter(QuestDefinition::extensions)
    ).apply(i, QuestDefinition::new));

    public QuestDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation chapterId,
                           String title, String subtitle, String description, String icon,
                           double x, double y, List<ResourceLocation> dependencies,
                           List<TaskDefinition> tasks, List<RewardDefinition> rewards, String legacyId) {
        this(bookId, id, chapterId, title, subtitle, description, icon, x, y, dependencies,
                tasks, rewards, legacyId, QuestAppearance.DEFAULT, Map.of());
    }

    public QuestDefinition {
        dependencies = List.copyOf(dependencies);
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
        appearance = appearance == null ? QuestAppearance.DEFAULT : appearance;
        extensions = Map.copyOf(extensions);
    }
}
