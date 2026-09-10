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
    void rewardTableTitleAndChildEditsPreserveEachOtherAndUnknownFields() {
        var type = new yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableReward();
        var model = new QuestTypedPropertyFormModel(type.configFields().size());
        model.openExisting(schema(type.configFields(), Map.of("table", "original", "extension_data", "kept"), false),
                "demo:table", "manual");
        // Returning from the child editor must change only the tree field, not the outer display name.
        model.setConfigValue(model.fieldIndex("title"), "启程奖励");
        model.setConfigValue(model.fieldIndex("table"), "edited");
        assertEquals(Map.of("title", "启程奖励", "table", "edited", "extension_data", "kept"), model.currentConfig());
        model.openExisting(schema(type.configFields(), model.currentConfig(), false), "demo:table", "manual");
        assertEquals("启程奖励", model.configValue(model.fieldIndex("title")));
        model.setConfigValue(model.fieldIndex("title"), "");
        assertFalse(model.currentConfig().containsKey("title"), "blank title restores the presentation fallback");
        assertEquals("edited", model.currentConfig().get("table"));
    }

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

    @Test void vectorInputsKeepTheirAxesAcrossPartialEditsAndScreenRebinding() {
        var model = new QuestTypedPropertyFormModel(4);
        model.openExisting(schema(List.of(ConfigFieldDescriptor.field("position",ConfigValueType.INTEGER_VECTOR3)),
                Map.of("position","1,2,3","opaque","keep"),false),"test:vector","manual");
        var font = new net.minecraft.client.gui.Font(id -> null,false) {
            public String plainSubstrByWidth(String text,int width) { return text.substring(0,Math.min(text.length(),Math.max(0,width))); }
            public String plainSubstrByWidth(String text,int width,boolean tail) {
                int count=Math.min(text.length(),Math.max(0,width));
                return tail ? text.substring(text.length()-count) : text.substring(0,count);
            }
        };
        model.bind(font, field -> {});
        model.vectorField(0,1).setValue("");
        assertEquals("1,,3",model.currentConfig().get("position"));
        assertTrue(model.localIssues().containsKey("position"));
        model.vectorField(0,0).setValue("-12");
        model.vectorField(0,1).setValue("64");
        model.vectorField(0,2).setValue("1,2");
        assertEquals("3",model.vectorField(0,2).getValue(),"pasting separators into one axis is rejected");
        model.bind(font,field -> {});
        assertEquals("-12",model.vectorField(0,0).getValue());
        assertEquals("64",model.vectorField(0,1).getValue());
        assertEquals(Map.of("position","-12,64,3","opaque","keep"),model.currentConfig());
        model.setConfigValue(0,"4,5,6");
        assertEquals("6",model.vectorField(0,2).getValue(),"direct-current response updates all native inputs");
    }

    private static ConfigEditorSchema schema(List<ConfigFieldDescriptor> fields, Map<String, String> config,
                                             boolean rawFallback) {
        return new ConfigEditorSchema(ConfigEditorSchema.Kind.TASK,
                ResourceLocation.fromNamespaceAndPath("test", "type"), fields, config, List.of(), rawFallback);
    }
}
