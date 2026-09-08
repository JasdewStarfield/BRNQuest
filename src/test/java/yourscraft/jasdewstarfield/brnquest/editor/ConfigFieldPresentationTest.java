package yourscraft.jasdewstarfield.brnquest.editor;

import org.junit.jupiter.api.Test;
import net.minecraft.network.chat.contents.TranslatableContents;
import yourscraft.jasdewstarfield.brnquest.client.ui.ConfigFieldLabels;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigFieldPresentationTest {
    @Test void labelsBelongToTheDescriptorRatherThanSharedFieldNamesOrValues() {
        var external = ConfigFieldDescriptor.enumeration("source_mode", List.of("explicit", "player"))
                .withLabel("external.mode").withValueLabels(Map.of("player", "external.player"))
                .withDefault("player").asRequired().withHelp("Example");
        assertEquals("external.mode", external.labelKey());
        assertEquals("external.player", ((TranslatableContents) ConfigFieldLabels.value(external, "player").getContents()).getKey());
        assertEquals("explicit", ConfigFieldLabels.value(external, "explicit").getString());
        var another = ConfigFieldDescriptor.enumeration("source_mode", List.of("explicit", "player"));
        assertEquals("player", ConfigFieldLabels.value(another, "player").getString());
        assertEquals(List.of("explicit", "player"), external.allowedValues());
        assertThrows(UnsupportedOperationException.class, () -> external.valueLabelKeys().put("player", "changed"));
    }

    @Test void oldDescriptorConstructorKeepsCompatibleDefaults() {
        var descriptor = new ConfigFieldDescriptor("legacy", ConfigValueType.TEXT, false,
                java.util.Optional.empty(), java.util.OptionalDouble.empty(), java.util.OptionalDouble.empty(),
                List.of(), java.util.Optional.empty(), "", java.util.Optional.empty());
        assertTrue(descriptor.labelKey().isEmpty());
        assertTrue(descriptor.valueLabelKeys().isEmpty());
    }
}
