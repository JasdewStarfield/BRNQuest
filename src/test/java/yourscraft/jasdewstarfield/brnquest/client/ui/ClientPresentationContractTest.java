package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientPresentationContractTest {
    @Test void builtInAndUnknownTaskTypesKeepDistinctPresentation() {
        TaskView item = task(TaskTypes.ITEM, Map.of("item", "{count:1,id:\"minecraft:stone\"}"));
        TaskView checkmark = task(TaskTypes.CHECKMARK, Map.of());
        TaskView custom = task(TaskTypes.CUSTOM, Map.of());
        TaskView foreignSamePath = task(id("foreign", "item"), Map.of("item", "ignored"));

        assertEquals(ClientTaskPresentation.NodeStyle.ITEM,
                ClientTaskPresentationRegistry.get(item.typeId()).nodeStyle(item));
        assertEquals(item.config().get("item"), ClientTaskPresentationRegistry.get(item.typeId()).itemSnbt(item));
        assertTrue(ClientTaskPresentationRegistry.get(item.typeId()).interactive(item));
        assertEquals(ClientTaskPresentation.NodeStyle.CHECKMARK,
                ClientTaskPresentationRegistry.get(checkmark.typeId()).nodeStyle(checkmark));
        assertTrue(ClientTaskPresentationRegistry.get(checkmark.typeId()).acceptsQuestCompletionIntent(checkmark));
        assertEquals("◆", ClientTaskPresentationRegistry.get(custom.typeId()).symbol(custom));
        assertEquals(ClientTaskPresentation.NodeStyle.PLACEHOLDER,
                ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).nodeStyle(foreignSamePath));
        assertEquals("?", ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).symbol(foreignSamePath));
        assertFalse(ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).interactive(foreignSamePath));
    }

    @Test void builtInAndUnknownRewardTypesKeepDistinctPresentation() {
        RewardView item = reward(RewardTypes.ITEM, Map.of("item", "{count:1,id:\"minecraft:diamond\"}"));
        RewardView foreignSamePath = reward(id("foreign", "item"), Map.of("item", "ignored"));

        assertEquals(item.config().get("item"),
                ClientRewardPresentationRegistry.get(item.typeId()).itemSnbt(item));
        assertEquals("?", ClientRewardPresentationRegistry.get(foreignSamePath.typeId()).symbol(foreignSamePath));
        assertEquals("", ClientRewardPresentationRegistry.get(foreignSamePath.typeId()).itemSnbt(foreignSamePath));
    }

    @Test void publicPresentationSpiDoesNotExposeInternalDefinitions() {
        Stream.of(ClientTaskPresentation.class, ClientRewardPresentation.class)
                .flatMap(type -> Stream.of(type.getMethods()))
                .flatMap(ClientPresentationContractTest::signatureTypes)
                .forEach(type -> assertFalse(type.getPackageName().contains(".data"),
                        () -> "Presentation SPI leaks internal data type: " + type.getName()));
    }

    private static Stream<Class<?>> signatureTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Stream.of(method.getParameterTypes()));
    }

    private static TaskView task(ResourceLocation typeId, Map<String, String> config) {
        return new TaskView(id("test", "book"), id("test", "task"), typeId, config, false);
    }

    private static RewardView reward(ResourceLocation typeId, Map<String, String> config) {
        return new RewardView(id("test", "book"), id("test", "reward"), typeId, config, "ONCE", false);
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
