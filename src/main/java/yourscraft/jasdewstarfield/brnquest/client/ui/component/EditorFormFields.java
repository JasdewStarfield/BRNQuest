package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Owns native inputs, not protocol state. Rebinding a screen never recreates a user's text/cursor. */
public final class EditorFormFields<K> {
    private record Spec(Component label, int limit) {}
    private final Map<K, Spec> specs = new LinkedHashMap<>();
    private final Map<K, EditorTextField> fields = new LinkedHashMap<>();

    public EditorFormFields<K> define(K key, String label, int limit) {
        if (specs.putIfAbsent(key, new Spec(Component.translatable(label), limit)) != null) {
            throw new IllegalArgumentException("Duplicate form field: " + key);
        }
        return this;
    }

    /** Called once per Screen.init; register decides whether this form draws inputs itself. */
    public void bind(Font font, Consumer<EditorTextField> register) {
        specs.forEach((key, spec) -> {
            EditorTextField field = fields.computeIfAbsent(key,
                    ignored -> new EditorTextField(font, spec.label(), spec.limit()));
            field.hide();
            register.accept(field);
        });
    }

    public EditorTextField field(K key) {
        EditorTextField result = fields.get(key);
        if (result == null) throw new IllegalStateException("Form must be bound before reading " + key);
        return result;
    }

    public void hide() { fields.values().forEach(EditorTextField::hide); }
    public void offsetForDrawerAnimation(int x) {
        fields.values().forEach(field -> field.offsetForDrawerAnimation(x));
    }
}
