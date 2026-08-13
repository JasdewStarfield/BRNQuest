package yourscraft.jasdewstarfield.brnquest.editor;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigEditorSchemasTest {
    @Test void builtInItemMetadataDescribesRequiredCountAndConsumption() {
        var schema = ConfigEditorSchemas.forTask(task(TaskTypes.ITEM, Map.of(
                "item", "{count:1,id:\"minecraft:stone\"}", "count", "0", "consume_items", "true")));

        assertFalse(schema.rawFallback());
        assertEquals(List.of("item", "count", "consume_items", "title"),
                schema.fields().stream().map(ConfigFieldDescriptor::key).toList());
        assertTrue(schema.fields().stream().filter(field -> field.key().equals("item")).findFirst().orElseThrow().required());
        assertTrue(schema.issues().stream().anyMatch(issue -> issue.fieldKey().equals("count")
                && issue.code().equals("MINIMUM")));
    }

    @Test void unknownTypesRetainLosslessImmutableRawFallback() {
        Map<String, String> mutable = new java.util.HashMap<>(Map.of("opaque", "value"));
        var taskSchema = ConfigEditorSchemas.forTask(task(id("foreign", "item"), mutable));
        var rewardSchema = ConfigEditorSchemas.forReward(reward(id("foreign", "item"), mutable));
        mutable.clear();

        assertTrue(taskSchema.rawFallback());
        assertTrue(rewardSchema.rawFallback());
        assertEquals(Map.of("opaque", "value"), taskSchema.rawConfig());
        assertThrows(UnsupportedOperationException.class, () -> taskSchema.rawConfig().clear());
    }

    @Test void enumResourceRangeAndCustomValidatorsProduceFieldIssues() {
        ConfigFieldDescriptor mode = ConfigFieldDescriptor.enumeration("mode", List.of("safe", "fast"));
        ConfigFieldDescriptor target = ConfigFieldDescriptor.field("target", ConfigValueType.RESOURCE_LOCATION)
                .withResourceRegistry(ResourceLocation.parse("minecraft:item"));
        ConfigFieldDescriptor amount = ConfigFieldDescriptor.field("amount", ConfigValueType.INTEGER)
                .withRange(1, 4).withValidator((value, config) -> List.of(new ConfigFieldIssue("amount",
                        ConfigFieldIssue.Severity.WARNING, "CUSTOM", "Example warning")));

        var issues = ConfigEditorSchemas.validate(List.of(mode, target, amount),
                Map.of("mode", "other", "target", "bad id", "amount", "8"));

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("ENUM")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("TYPE")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("MAXIMUM")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("CUSTOM")));
        assertEquals(ResourceLocation.parse("minecraft:item"), target.resourceRegistry().orElseThrow());
    }

    @Test void publicEditorSpiDoesNotExposeInternalDefinitionsOrProgress() {
        Stream.of(ConfigFieldDescriptor.class, ConfigFieldIssue.class, ConfigFieldValidator.class,
                        ConfigEditorSchema.class, ConfigEditorSchemas.class)
                .flatMap(type -> Stream.of(type.getMethods()))
                .flatMap(ConfigEditorSchemasTest::signatureTypes)
                .forEach(type -> assertFalse(type.getPackageName().contains(".data")
                                || type.getPackageName().contains(".progress"),
                        () -> "Editor SPI leaks internal type: " + type.getName()));
    }

    private static Stream<Class<?>> signatureTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()));
    }

    private static TaskView task(ResourceLocation type, Map<String, String> config) {
        return new TaskView(id("test", "book"), id("test", "task"), type, config, false);
    }

    private static RewardView reward(ResourceLocation type, Map<String, String> config) {
        return new RewardView(id("test", "book"), id("test", "reward"), type, config, "ONCE", false);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
