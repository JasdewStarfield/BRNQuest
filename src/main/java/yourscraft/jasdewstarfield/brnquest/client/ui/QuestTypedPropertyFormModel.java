package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorFormFields;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldIssue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Owns typed-property draft values independently from rendering and network submission.
 * Native text fields mirror this model, so a Screen re-init does not become a second state owner.
 */
final class QuestTypedPropertyFormModel {
    private final int capacity;
    private final EditorFormFields<String> identityFields = new EditorFormFields<String>()
            .define("id", "screen.brnquest.editor.typed.property.id", 256)
            .define("claim", "screen.brnquest.editor.typed.property.claim_policy", 64);
    private final EditorFormFields<Integer> configFields = new EditorFormFields<>();
    private final EditorFormFields<Integer> vectorFields = new EditorFormFields<>();
    private boolean syncingVector;
    private final List<String> configValues;
    private final Map<String, String> serverIssues = new LinkedHashMap<>();
    private String id = "";
    private String claim = "manual";
    private ConfigEditorSchema schema;
    private Map<String, String> originalConfig = Map.of();
    private Map<String, String> rawConfig = Map.of();
    private boolean bound;

    QuestTypedPropertyFormModel(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.configValues = new ArrayList<>(Collections.nCopies(capacity, ""));
        for (int index = 0; index < capacity; index++) {
            for (int axis = 0; axis < 3; axis++) vectorFields.define(index * 3 + axis, "screen.brnquest.editor.typed.property.config", 256);
            configFields.define(index, "screen.brnquest.editor.typed.property.config", 65_536);
        }
    }

    void bind(Font font, Consumer<EditorTextField> register) {
        identityFields.bind(font, register);
        configFields.bind(font, register);
        vectorFields.bind(font, register);
        identityFields.field("id").setResponder(value -> id = value);
        identityFields.field("claim").setResponder(value -> claim = value);
        for (int index = 0; index < capacity; index++) {
            int fieldIndex = index;
            configFields.field(index).setResponder(value -> configValues.set(fieldIndex, value));
            for (int axis = 0; axis < 3; axis++) {
                int component = axis;
                var input = vectorFields.field(index * 3 + axis);
                input.setFilter(value -> syncingVector || value.matches("-?[0-9]*"));
                input.setResponder(value -> {
                    if (!syncingVector) {
                        String joined = yourscraft.jasdewstarfield.brnquest.editor.IntegerVectorValue.withAxis(configValues.get(fieldIndex), component, value);
                        configValues.set(fieldIndex, joined);
                        configFields.field(fieldIndex).setValue(joined);
                    }
                });
            }
        }
        bound = true;
        syncFields();
    }

    void openExisting(ConfigEditorSchema nextSchema, String nextId, String nextClaim) {
        open(nextSchema, nextId, nextClaim, true);
    }

    void openNew(ConfigEditorSchema nextSchema, String nextId, String nextClaim) {
        open(nextSchema, nextId, nextClaim, false);
    }

    private void open(ConfigEditorSchema nextSchema, String nextId, String nextClaim, boolean existing) {
        schema = nextSchema;
        id = nextId;
        claim = nextClaim;
        originalConfig = nextSchema == null ? Map.of() : nextSchema.rawConfig();
        rawConfig = originalConfig;
        serverIssues.clear();
        for (int index = 0; index < capacity; index++) {
            String value = "";
            if (nextSchema != null && index < nextSchema.fields().size()) {
                ConfigFieldDescriptor field = nextSchema.fields().get(index);
                // Creation schemas may carry type-owned book/chapter defaults, ahead of generic field defaults.
                value = nextSchema.rawConfig().getOrDefault(field.key(), field.defaultValue().orElse(""));
            }
            configValues.set(index, value);
        }
        syncFields();
    }

    private void syncFields() {
        if (!bound) return;
        identityFields.field("id").setValue(id);
        identityFields.field("claim").setValue(claim);
        for (int index = 0; index < capacity; index++) {
            configFields.field(index).setValue(configValues.get(index));
            syncVector(index);
        }
    }

    ConfigEditorSchema schema() { return schema; }
    String id() { return id; }
    String claim() { return claim; }
    Map<String, String> rawConfig() { return rawConfig; }
    Map<String, String> serverIssues() { return serverIssues; }
    EditorTextField identityField(String key) { return identityFields.field(key); }
    EditorTextField configField(int index) { return configFields.field(index); }
    String configValue(int index) { return configValues.get(index); }

    void setConfigValue(int index, String value) {
        configValues.set(index, value);
        if (bound) { configFields.field(index).setValue(value); syncVector(index); }
    }

    EditorTextField vectorField(int index, int axis) { return vectorFields.field(index * 3 + axis); }
    private void syncVector(int index) {
        syncingVector = true;
        try {
            var parts = yourscraft.jasdewstarfield.brnquest.editor.IntegerVectorValue.components(configValues.get(index));
            for (int axis = 0; axis < 3; axis++) vectorFields.field(index * 3 + axis).setValue(parts.get(axis));
        } finally { syncingVector = false; }
    }

    void replaceRawConfig(Map<String, String> config) { rawConfig = Map.copyOf(config); }

    int fieldIndex(String key) {
        if (schema == null) return -1;
        for (int index = 0; index < Math.min(schema.fields().size(), capacity); index++) {
            if (schema.fields().get(index).key().equals(key)) return index;
        }
        return -1;
    }

    Map<String, String> currentConfig() {
        if (schema != null && schema.rawFallback()) return rawConfig;
        Map<String, String> config = new LinkedHashMap<>(originalConfig);
        List<ConfigFieldDescriptor> fields = schema == null ? List.of() : schema.fields();
        for (int index = 0; index < Math.min(fields.size(), capacity); index++) {
            String value = configValues.get(index).strip();
            if (value.isEmpty()) config.remove(fields.get(index).key());
            else config.put(fields.get(index).key(), value);
        }
        return Map.copyOf(config);
    }

    Map<String, String> localIssues() {
        if (schema == null) return Map.of();
        Map<String, String> issues = new LinkedHashMap<>();
        for (ConfigFieldIssue issue : ConfigEditorSchemas.validate(schema.fields(), currentConfig())) {
            issues.putIfAbsent(issue.fieldKey(), issue.message());
        }
        return Collections.unmodifiableMap(issues);
    }

    void clearNonIdentityServerIssues() {
        serverIssues.keySet().removeIf(key -> !"id".equals(key) && !"claim_policy".equals(key));
    }

    void hide() {
        if (!bound) return;
        identityFields.hide();
        configFields.hide();
        vectorFields.hide();
    }

    void offsetForDrawerAnimation(int offset) {
        if (!bound) return;
        identityFields.offsetForDrawerAnimation(offset);
        configFields.offsetForDrawerAnimation(offset);
        vectorFields.offsetForDrawerAnimation(offset);
    }

    void close() {
        schema = null;
        originalConfig = Map.of();
        rawConfig = Map.of();
        serverIssues.clear();
        hide();
    }
}
