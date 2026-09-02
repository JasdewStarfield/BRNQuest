package yourscraft.jasdewstarfield.brnquest.data;

import java.util.Locale;

/** Native dependency aggregation semantics, independent from the FTB storage representation. */
public enum DependencyRequirement {
    ALL_COMPLETED, ONE_COMPLETED, ALL_STARTED, ONE_STARTED;

    public String serializedName() { return name().toLowerCase(Locale.ROOT); }

    public static DependencyRequirement parse(String value) {
        if (value == null) return ALL_COMPLETED;
        try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return ALL_COMPLETED; }
    }
}
