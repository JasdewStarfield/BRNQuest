package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTypedPropertyFormModelTest {
    @Test
    void existingDraftUsesStoredValuesAndPreservesUnknownConfig() {
        QuestTypedPropertyFormModel model = new QuestTypedPropertyFormModel(4);
        model.openExisting(schema(List.of(ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER)
                .withDefault("1")), Map.of("count", "4", "extension_data", "kept"), false), "demo:task", "manual");

        model.setConfigValue(0, "7");

        assertEquals(Map.of("count", "7", "extension_data", "kept"), model.currentConfig());
    }

    @Test
    void newDraftAppliesDefaultsAndReportsRequiredValues() {
        QuestTypedPropertyFormModel model = new QuestTypedPropertyFormModel(4);
        model.openNew(schema(List.of(
                ConfigFieldDescriptor.field("title", ConfigValueType.TEXT).asRequired(),
                ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER).withDefault("2")), Map.of(), false),
                "demo:reward", "manual");

        assertEquals("2", model.configValue(1));
        assertTrue(model.localIssues().containsKey("title"));
        model.setConfigValue(0, "Configured");
        assertFalse(model.localIssues().containsKey("title"));
    }

    @Test
    void rawFallbackAndMatcherReturnDoNotDiscardSiblingFields() {
        QuestTypedPropertyFormModel raw = new QuestTypedPropertyFormModel(4);
        raw.openExisting(schema(List.of(), Map.of("legacy", "value"), true), "demo:legacy", "manual");
        raw.replaceRawConfig(Map.of("legacy", "updated", "extra", "kept"));
        assertEquals(Map.of("legacy", "updated", "extra", "kept"), raw.currentConfig());

        QuestTypedPropertyFormModel described = new QuestTypedPropertyFormModel(4);
        described.openExisting(schema(List.of(
                ConfigFieldDescriptor.field("matcher", ConfigValueType.ITEM_MATCHER),
                ConfigFieldDescriptor.field("required_entries", ConfigValueType.INTEGER)),
                Map.of("matcher", "old", "required_entries", "1"), false), "demo:item", "manual");
        described.setConfigValue(described.fieldIndex("matcher"), "new");
        assertEquals("1", described.currentConfig().get("required_entries"));
    }

    private static ConfigEditorSchema schema(List<ConfigFieldDescriptor> fields, Map<String, String> config,
                                             boolean rawFallback) {
        return new ConfigEditorSchema(ConfigEditorSchema.Kind.TASK,
                ResourceLocation.fromNamespaceAndPath("test", "type"), fields, config, List.of(), rawFallback);
    }
}
