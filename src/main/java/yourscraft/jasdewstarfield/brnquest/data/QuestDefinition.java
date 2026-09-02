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
                              QuestAppearance appearance, QuestBehavior behavior, Map<String, String> extensions) {
    private record Metadata(QuestAppearance appearance, QuestBehavior behavior, Map<String, String> extensions) {
        private static final Codec<Metadata> CODEC = RecordCodecBuilder.create(i -> i.group(
                QuestAppearance.CODEC.optionalFieldOf("appearance", QuestAppearance.DEFAULT).forGetter(Metadata::appearance),
                QuestBehavior.CODEC.optionalFieldOf("behavior", QuestBehavior.DEFAULT).forGetter(Metadata::behavior),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("extensions", Map.of()).forGetter(Metadata::extensions)
        ).apply(i, Metadata::new));
    }

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
            Metadata.CODEC.optionalFieldOf("metadata", new Metadata(
                    QuestAppearance.DEFAULT, QuestBehavior.DEFAULT, Map.of()))
                    .forGetter(quest -> new Metadata(quest.appearance(), quest.behavior(), quest.extensions()))
    ).apply(i, (bookId, id, chapterId, title, subtitle, description, icon, x, y, dependencies, tasks, rewards, legacyId, metadata) ->
            new QuestDefinition(bookId, id, chapterId, title, subtitle, description, icon, x, y,
                    dependencies, tasks, rewards, legacyId, metadata.appearance(), metadata.behavior(), metadata.extensions())));

    public QuestDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation chapterId,
                           String title, String subtitle, String description, String icon,
                           double x, double y, List<ResourceLocation> dependencies,
                           List<TaskDefinition> tasks, List<RewardDefinition> rewards, String legacyId) {
        this(bookId, id, chapterId, title, subtitle, description, icon, x, y, dependencies,
                tasks, rewards, legacyId, QuestAppearance.DEFAULT, QuestBehavior.DEFAULT, Map.of());
    }

    public QuestDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation chapterId,
                           String title, String subtitle, String description, String icon,
                           double x, double y, List<ResourceLocation> dependencies,
                           List<TaskDefinition> tasks, List<RewardDefinition> rewards, String legacyId,
                           QuestAppearance appearance, Map<String, String> extensions) {
        this(bookId, id, chapterId, title, subtitle, description, icon, x, y, dependencies,
                tasks, rewards, legacyId, appearance, QuestBehavior.DEFAULT, extensions);
    }

    public QuestDefinition {
        dependencies = List.copyOf(dependencies);
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
        appearance = appearance == null ? QuestAppearance.DEFAULT : appearance;
        behavior = behavior == null ? QuestBehavior.DEFAULT : behavior;
        extensions = Map.copyOf(extensions);
    }
}
