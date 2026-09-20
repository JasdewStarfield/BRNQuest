package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
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
        assertEquals(Component.translatable("screen.brnquest.type.task.item"),
                ClientTaskPresentationRegistry.get(item.typeId()).typeName(item));
        assertTrue(ClientTaskPresentationRegistry.get(item.typeId()).interactive(item));
        assertEquals(ClientTaskPresentation.NodeStyle.CHECKMARK,
                ClientTaskPresentationRegistry.get(checkmark.typeId()).nodeStyle(checkmark));
        assertTrue(ClientTaskPresentationRegistry.get(checkmark.typeId()).acceptsQuestCompletionIntent(checkmark));
        assertEquals("◆", ClientTaskPresentationRegistry.get(custom.typeId()).symbol(custom));
        assertEquals(ClientTaskPresentation.NodeStyle.PLACEHOLDER,
                ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).nodeStyle(foreignSamePath));
        assertEquals("?", ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).symbol(foreignSamePath));
        assertFalse(ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).interactive(foreignSamePath));
        assertEquals(Component.translatable("screen.brnquest.type.task.unknown"),
                ClientTaskPresentationRegistry.get(foreignSamePath.typeId()).typeName(foreignSamePath));
    }

    @Test void builtInAndUnknownRewardTypesKeepDistinctPresentation() {
        RewardView item = reward(RewardTypes.ITEM, Map.of("item", "{count:1,id:\"minecraft:diamond\"}"));
        RewardView foreignSamePath = reward(id("foreign", "item"), Map.of("item", "ignored"));

        assertEquals(item.config().get("item"),
                ClientRewardPresentationRegistry.get(item.typeId()).itemSnbt(item));
        assertEquals(Component.translatable("screen.brnquest.type.reward.item"),
                ClientRewardPresentationRegistry.get(item.typeId()).typeName(item));
        assertEquals("?", ClientRewardPresentationRegistry.get(foreignSamePath.typeId()).symbol(foreignSamePath));
        assertEquals("", ClientRewardPresentationRegistry.get(foreignSamePath.typeId()).itemSnbt(foreignSamePath));
        assertEquals(Component.translatable("screen.brnquest.type.reward.unknown"),
                ClientRewardPresentationRegistry.get(foreignSamePath.typeId()).typeName(foreignSamePath));
    }

    @Test void itemChoicePresentationAdvertisesItsSecondaryCandidateMenu() {
        String matcher = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\","
                + "\"{count:1,id:\\\"minecraft:dirt\\\"}\"],\"required\":1}";
        TaskView choice = task(TaskTypes.ITEM_CHOICE, Map.of("matcher", matcher));
        var presentation = ClientTaskPresentationRegistry.get(choice.typeId());

        assertEquals(ClientTaskPresentation.NodeStyle.ITEM, presentation.nodeStyle(choice));
        assertTrue(presentation.itemSnbt(choice).contains("minecraft:stone"));
        assertEquals(Component.translatable("screen.brnquest.type.task.item_choice"),
                presentation.typeName(choice));
        assertTrue(presentation.interactive(choice));
    }

    @Test void itemTaskConsumptionWordingHonorsCanonicalAndLegacyFields() {
        TaskView consuming = task(TaskTypes.ITEM, Map.of("consume_items", "true"));
        TaskView observing = task(TaskTypes.ITEM, Map.of("consume_items", "false", "consume", "true"));
        assertTrue(yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.consumesItems(consuming));
        assertTrue(yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.consumesItems(task(TaskTypes.ITEM,
                Map.of("consume", "1b"))));
        assertFalse(yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.consumesItems(observing));
        assertEquals(Component.translatable("screen.brnquest.task.item.require.label"),
                yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.itemObjectiveQualifier(consuming));
        assertEquals(Component.translatable("screen.brnquest.task.item.hold.label"),
                yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.itemObjectiveQualifier(observing));
        assertEquals(Component.translatable("screen.brnquest.task.item.require.hint"),
                yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.itemObjectiveQualifierHint(consuming));
        assertEquals(Component.translatable("screen.brnquest.task.item.hold.hint"),
                yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.itemObjectiveQualifierHint(observing));
    }

    @Test void multipleItemObjectiveWordingUsesRequiredTypesInsteadOfStackCount() {
        String matcher = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\","
                + "\"{count:1,id:\\\"minecraft:dirt\\\"}\","
                + "\"{count:1,id:\\\"minecraft:apple\\\"}\"],\"required\":2}";
        TaskView consuming = task(TaskTypes.ITEM, Map.of("matcher", matcher, "consume_items", "true"));
        TaskView observing = task(TaskTypes.ITEM, Map.of("matcher", matcher, "consume_items", "false"));

        var spec = yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.itemSpec(consuming);
        assertEquals(Component.translatable("screen.brnquest.task.item_choice.require", 3, 2),
                yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.multipleItemObjectiveTitle(consuming, spec));
        assertEquals(Component.translatable("screen.brnquest.task.item_choice.hold", 3, 2),
                yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinItemPresentation.multipleItemObjectiveTitle(observing, spec));
    }

    @Test void itemRewardDisplayCountIncludesTheConfiguredMultiplier() {
        RewardView reward = reward(RewardTypes.ITEM, Map.of("count", "4"));

        assertEquals(8, yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinClientTypes.displayedCount(reward, 2));
        assertEquals(1, yourscraft.jasdewstarfield.brnquest.builtin.client.BuiltinClientTypes.displayedCount(
                reward(RewardTypes.ITEM, Map.of("count", "invalid")), 1));
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
