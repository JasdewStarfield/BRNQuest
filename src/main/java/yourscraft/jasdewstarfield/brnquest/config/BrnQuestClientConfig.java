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

    /** Read defaults before config loading; live reads also make runtime edits effective immediately. */
    public static <T> T read(ModConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public enum GridVisibility { ALWAYS, EDITING_ONLY, NEVER }

    public static final class Values {
        public final ModConfigSpec.BooleanValue autoCollapseNavigation, reduceMotion, rememberEditingMode, rememberViewport;
        public final ModConfigSpec.DoubleValue zoomStep;
        public final ModConfigSpec.IntValue nodeDragHoldMillis, navigationWidth, detailsWidth;
        public final ModConfigSpec.EnumValue<GridVisibility> showGrid;
        public final ModConfigSpec.DoubleValue scrollStep;
        public final ModConfigSpec.DoubleValue smoothSpeed;
        public final ModConfigSpec.DoubleValue zoomSmoothSpeed;
        public final ModConfigSpec.DoubleValue drawerSmoothSpeed;
        public final ModConfigSpec.DoubleValue focusSmoothSpeed;
        public final ModConfigSpec.BooleanValue autoFocusSelectedQuest;
        public final ModConfigSpec.BooleanValue snapToGrid;
        public final ModConfigSpec.BooleanValue textLayoutDebug;

        private Values(ModConfigSpec.Builder builder) {
            // Zero widths select the existing responsive layout; custom widths are additionally clamped on screen.
            builder.push("interface");
            autoCollapseNavigation = builder.define("autoCollapseNavigation", true);
            rememberEditingMode = builder.define("rememberEditingMode", true);
            rememberViewport = builder.define("rememberViewport", true);
            navigationWidth = builder.comment("GUI pixels; 0 selects automatic width.").defineInRange("navigationWidth", 0, 0, 400);
            detailsWidth = builder.comment("GUI pixels; 0 selects automatic width.").defineInRange("detailsWidth", 0, 0, 600);
            showGrid = builder.defineEnum("showGrid", GridVisibility.ALWAYS);
            builder.pop();
            // Local rendering diagnostics are read each frame and never change book or server state.
            builder.comment("Client development diagnostics").push("debug");
            textLayoutDebug = builder.comment("Replace BRNQuest text tooltips with text layout measurements.")
                    .define("textLayoutDebug", false);
            builder.pop();
            // Author preference only: toggling this must never create a book revision.
            builder.comment("Quest editor preferences").push("editor");
            nodeDragHoldMillis = builder.comment("Captured when a node press begins.").defineInRange("nodeDragHoldMillis", 220, 100, 1000);
            snapToGrid = builder.comment("Snap dragged quest nodes to the grid. Applies to the next drag.")
                    .define("snapToGrid", true);
            builder.pop();

            builder.comment("Quest screen scrolling settings").push("scrolling");
            scrollStep = builder
                    .comment("Pixels added to the target for one mouse-wheel notch.")
                    .defineInRange("scrollStep", 24.0, 1.0, 100.0);
            smoothSpeed = builder
                    .comment("Scroll response per second. Higher values reach the target faster.")
                    .defineInRange("smoothSpeed", 12.0, 1.0, 40.0);
            builder.pop();

            builder.comment("Quest screen animation settings").push("animations");
            reduceMotion = builder.define("reduceMotion", false);
            zoomStep = builder.defineInRange("zoomStep", 0.10, 0.01, 0.50);
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
