package yourscraft.jasdewstarfield.brnquest.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/** Client-only presentation preferences; no gameplay or server-authoritative state belongs here. */
public final class BrnQuestClientConfig {
    public static final Values VALUES;
    public static final ModConfigSpec SPEC;

    static {
        Pair<Values, ModConfigSpec> configured = new ModConfigSpec.Builder().configure(Values::new);
        VALUES = configured.getLeft();
        SPEC = configured.getRight();
    }

    private BrnQuestClientConfig() {}

    public static final class Values {
        public final ModConfigSpec.DoubleValue scrollStep;
        public final ModConfigSpec.DoubleValue smoothSpeed;
        public final ModConfigSpec.DoubleValue zoomSmoothSpeed;
        public final ModConfigSpec.DoubleValue drawerSmoothSpeed;
        public final ModConfigSpec.DoubleValue focusSmoothSpeed;
        public final ModConfigSpec.BooleanValue autoFocusSelectedQuest;

        private Values(ModConfigSpec.Builder builder) {
            builder.comment("Quest screen scrolling settings").push("scrolling");
            scrollStep = builder
                    .comment("Pixels added to the target for one mouse-wheel notch.")
                    .defineInRange("scrollStep", 24.0, 1.0, 100.0);
            smoothSpeed = builder
                    .comment("Scroll response per second. Higher values reach the target faster.")
                    .defineInRange("smoothSpeed", 12.0, 1.0, 40.0);
            builder.pop();

            builder.comment("Quest screen animation settings").push("animations");
            zoomSmoothSpeed = builder
                    .comment("Zoom response per second.")
                    .defineInRange("zoomSmoothSpeed", 12.0, 1.0, 40.0);
            drawerSmoothSpeed = builder
                    .comment("Left and right drawer response per second.")
                    .defineInRange("drawerSmoothSpeed", 14.0, 1.0, 40.0);
            focusSmoothSpeed = builder
                    .comment("Selected-quest camera focus response per second.")
                    .defineInRange("focusSmoothSpeed", 10.0, 1.0, 40.0);
            autoFocusSelectedQuest = builder
                    .comment("Centers a quest in the remaining canvas when its details are opened or switched.")
                    .define("autoFocusSelectedQuest", true);
            builder.pop();
        }
    }
}
