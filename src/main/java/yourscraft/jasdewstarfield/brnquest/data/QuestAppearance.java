package yourscraft.jasdewstarfield.brnquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Portable node appearance independent from FTB presets and theme inheritance. */
public record QuestAppearance(String shape, double size, double iconScale, double minWidth) {
    public static final QuestAppearance DEFAULT = new QuestAppearance("chamfer", 1.0, 1.0, 0.0);
    public static final Codec<QuestAppearance> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("shape", "chamfer").forGetter(QuestAppearance::shape),
            Codec.DOUBLE.optionalFieldOf("size", 1.0).forGetter(QuestAppearance::size),
            Codec.DOUBLE.optionalFieldOf("icon_scale", 1.0).forGetter(QuestAppearance::iconScale),
            Codec.DOUBLE.optionalFieldOf("min_width", 0.0).forGetter(QuestAppearance::minWidth)
    ).apply(i, QuestAppearance::new));

    public QuestAppearance {
        shape = shape == null || shape.isBlank() ? "chamfer" : shape;
    }
}
