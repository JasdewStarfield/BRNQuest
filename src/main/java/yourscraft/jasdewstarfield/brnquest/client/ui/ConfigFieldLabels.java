package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;

/** Generic label projection shared by selected values and popup entries, with a literal fallback. */
public final class ConfigFieldLabels {
    private ConfigFieldLabels() {}
    public static Component value(ConfigFieldDescriptor descriptor, String value) {
        String key = descriptor.valueLabelKeys().get(value);
        return key == null || key.isBlank() ? Component.literal(value) : Component.translatable(key);
    }
}
