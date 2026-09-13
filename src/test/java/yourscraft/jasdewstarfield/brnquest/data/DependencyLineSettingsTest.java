package yourscraft.jasdewstarfield.brnquest.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Appearance inheritance distinguishes false from missing in both serialization paths. */
class DependencyLineSettingsTest {
    @Test void triStateOverridesAndCodecRoundTrip() {
        for (Boolean value : new Boolean[]{null,false,true}) {
            var appearance = new QuestAppearance("chamfer",1,1,0,value);
            assertEquals(value == null || value, appearance.hideDependencyLines(true));
            assertEquals(value != null && value, appearance.hideDependencyLines(false));
            var encoded = QuestAppearance.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,appearance).getOrThrow();
            assertEquals(appearance,QuestAppearance.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow());
        }
    }
}
