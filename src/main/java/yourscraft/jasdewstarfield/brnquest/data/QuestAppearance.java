package yourscraft.jasdewstarfield.brnquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Portable node appearance independent from FTB presets and theme inheritance. */
public record QuestAppearance(String shape, double size, double iconScale, double minWidth, Boolean hideDependencyLines) {
    /** Null keeps chapter inheritance; explicit false overrides a hidden chapter default. */
    public QuestAppearance(String shape, double size, double iconScale, double minWidth) {
        this(shape, size, iconScale, minWidth, null);
    }
    public boolean hideDependencyLines(boolean chapterDefault) {
        return hideDependencyLines == null ? chapterDefault : hideDependencyLines;
    }
    public static final QuestAppearance DEFAULT = new QuestAppearance("chamfer", 1.0, 1.0, 0.0);
    public static final Codec<QuestAppearance> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("shape", "chamfer").forGetter(QuestAppearance::shape),
            Codec.DOUBLE.optionalFieldOf("size", 1.0).forGetter(QuestAppearance::size),
            Codec.DOUBLE.optionalFieldOf("icon_scale", 1.0).forGetter(QuestAppearance::iconScale),
            Codec.DOUBLE.optionalFieldOf("min_width", 0.0).forGetter(QuestAppearance::minWidth),
            Codec.BOOL.optionalFieldOf("hide_dependency_lines").forGetter(a -> java.util.Optional.ofNullable(a.hideDependencyLines()))
    ).apply(i, (shape, size, scale, width, hide) -> new QuestAppearance(shape, size, scale, width, hide.orElse(null))));

    public QuestAppearance {
        shape = shape == null || shape.isBlank() ? "chamfer" : shape;
    }
}
