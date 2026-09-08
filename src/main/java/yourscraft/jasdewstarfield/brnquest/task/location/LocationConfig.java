package yourscraft.jasdewstarfield.brnquest.task.location;

import com.mojang.serialization.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Compact integer vectors preserve FTB's half-open block box without overflowing at extreme coordinates. */
public record LocationConfig(String selector, boolean anyDimension, BlockPos position, BlockPos size) {
    public static Codec<Map<String, String>> codec(String kind) {
        return Codec.unboundedMap(Codec.STRING, Codec.STRING).validate(map -> {
            try { parse(kind, map); return DataResult.success(map); }
            catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
        });
    }
    public static LocationConfig parse(String kind, Map<String, String> values) {
        String key = kind.equals("location") ? "dimension" : kind;
        String selector = values.getOrDefault(key, "").strip();
        String any = values.getOrDefault("ignore_dimension", "false");
        if (!any.equals("true") && !any.equals("false")) throw new IllegalArgumentException("ignore_dimension must be true/false");
        boolean ignore = kind.equals("location") && Boolean.parseBoolean(any);
        if (!ignore && ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector) == null)
            throw new IllegalArgumentException("Invalid ID or #group: " + selector);
        BlockPos pos = kind.equals("location") ? vector(values.getOrDefault("position", "0,0,0")) : BlockPos.ZERO;
        BlockPos size = kind.equals("location") ? vector(values.getOrDefault("size", "1,1,1")) : new BlockPos(1,1,1);
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) throw new IllegalArgumentException("Box sizes must be positive");
        return new LocationConfig(selector, ignore, pos, size);
    }
    public static BlockPos vector(String text) {
        String[] values = text.strip().split("[, ]+", -1);
        if (values.length != 3) throw new IllegalArgumentException("Expected three integers: x,y,z");
        try { return new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2])); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("Expected three integers: x,y,z"); }
    }
    public boolean contains(BlockPos point) {
        return inside(point.getX(), position.getX(), size.getX()) && inside(point.getY(), position.getY(), size.getY())
                && inside(point.getZ(), position.getZ(), size.getZ());
    }
    private static boolean inside(int value, int minimum, int size) { return value >= minimum && (long)value < (long)minimum + size; }
}
