package yourscraft.jasdewstarfield.brnquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable typed task configuration retained even when its type is unavailable. */
public record TaskDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation typeId,
                             Map<String, String> config, boolean optional) {
    public static final Codec<TaskDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("book_id").forGetter(TaskDefinition::bookId),
            ResourceLocation.CODEC.fieldOf("id").forGetter(TaskDefinition::id),
            ResourceLocation.CODEC.fieldOf("type").forGetter(TaskDefinition::typeId),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("config", Map.of()).forGetter(TaskDefinition::config),
            Codec.BOOL.optionalFieldOf("optional", false).forGetter(TaskDefinition::optional)
    ).apply(i, TaskDefinition::new));

    public TaskDefinition { config = Map.copyOf(config); }
}
