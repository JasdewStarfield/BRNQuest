package yourscraft.jasdewstarfield.brnquest.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned ceiling for command reward sources; reward authors cannot raise this ceiling. */
public final class BrnQuestServerConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.IntValue COMMAND_PERMISSION_LIMIT;
    static {
        var builder = new ModConfigSpec.Builder();
        COMMAND_PERMISSION_LIMIT = builder.comment("Maximum permission level of command reward sources (0-4).")
                .defineInRange("commandRewardPermissionLimit", 2, 0, 4);
        SPEC = builder.build();
    }
    private BrnQuestServerConfig() {}
    public static int commandPermissionLimit() { return COMMAND_PERMISSION_LIMIT.get(); }
}
