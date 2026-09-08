package yourscraft.jasdewstarfield.brnquest.editor;

import java.util.List;

/** Lossless editor projection; malformed legacy values stay visible until the author repairs them. */
public final class IntegerVectorValue {
    private IntegerVectorValue() {}
    public static List<String> components(String value) {
        String[] parts = value.strip().contains(",") ? value.strip().split(",", -1) : value.strip().split(" +", -1);
        return parts.length == 3 ? List.of(parts) : List.of(value, "", "");
    }
    public static String withAxis(String value, int axis, String replacement) {
        var parts = new java.util.ArrayList<>(components(value));
        parts.set(axis, replacement);
        return String.join(",", parts);
    }
    public static int[] parse(String value) {
        // Empty axes must not collapse: x,,z represents an unfinished form, not a different vector.
        String[] parts = value.strip().contains(",") ? value.strip().split(",", -1) : value.strip().split(" +", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Expected X, Y, Z integers");
        return java.util.Arrays.stream(parts).map(String::strip).mapToInt(Integer::parseInt).toArray();
    }
}
