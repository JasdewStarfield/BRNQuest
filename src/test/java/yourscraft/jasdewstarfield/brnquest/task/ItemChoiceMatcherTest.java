package yourscraft.jasdewstarfield.brnquest.task;
import yourscraft.jasdewstarfield.brnquest.builtin.item.ItemChoiceMatcher;
import yourscraft.jasdewstarfield.brnquest.builtin.item.UnifiedItemTask;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemChoiceMatcherTest {
    @Test void taskCodecReadsEnchantedMatcherWithoutAWorldRegistry() {
        String sword = "{id:\"minecraft:netherite_sword\",count:1,components:{\"minecraft:enchantments\":{levels:{\"minecraft:bane_of_arthropods\":5}}}}";
        var spec = new ItemChoiceMatcher.Spec(List.of(ItemChoiceMatcher.Entry.item(
                sword, 1, ItemChoiceMatcher.ComponentMatch.FUZZY)), 1);
        var config = Map.of("matcher", spec.encode(), "required_entries", "1");

        // The ordinary task codec has no level registry, yet it must retain authored components.
        var decoded = yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec.decode(
                new UnifiedItemTask().configCodec(), config);
        assertEquals(config, decoded.result().orElseThrow());
    }

    @Test void onlyHoldingObjectivesOptIntoAutomaticInventorySubmission() {
        var task = new UnifiedItemTask();
        assertTrue(task.reevaluateOnInventoryChange(Map.of("consume_items", "false")));
        assertFalse(task.reevaluateOnInventoryChange(Map.of("consume_items", "true")));
        assertFalse(task.reevaluateOnInventoryChange(Map.of("only_from_crafting", "true")));
    }
    @Test void componentPoliciesKeepTheirDifferentAcceptanceScopes() {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        ItemStack filter = new ItemStack(Items.DIAMOND_SWORD);
        filter.set(DataComponents.CUSTOM_NAME, Component.literal("Required"));
        ItemStack same = filter.copy();
        ItemStack extra = filter.copy();
        extra.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemStack wrong = new ItemStack(Items.DIAMOND_SWORD);
        wrong.set(DataComponents.CUSTOM_NAME, Component.literal("Wrong"));
        String snbt = filter.save(registries).toString();
        var none = new ItemChoiceMatcher.Spec(List.of(ItemChoiceMatcher.Entry.item(
                snbt, 1, ItemChoiceMatcher.ComponentMatch.NONE)), 1);
        var fuzzy = new ItemChoiceMatcher.Spec(List.of(ItemChoiceMatcher.Entry.item(
                snbt, 1, ItemChoiceMatcher.ComponentMatch.FUZZY)), 1);
        var strict = new ItemChoiceMatcher.Spec(List.of(ItemChoiceMatcher.Entry.item(
                snbt, 1, ItemChoiceMatcher.ComponentMatch.STRICT)), 1);

        assertTrue(ItemChoiceMatcher.accepts(registries, none, wrong));
        assertFalse(ItemChoiceMatcher.accepts(registries, fuzzy, wrong));
        assertTrue(ItemChoiceMatcher.accepts(registries, fuzzy, same));
        assertTrue(ItemChoiceMatcher.accepts(registries, fuzzy, extra));
        assertFalse(ItemChoiceMatcher.accepts(registries, strict, extra));
        assertEquals(ItemChoiceMatcher.ComponentMatch.FUZZY,
                ItemChoiceMatcher.parse(fuzzy.encode()).result().orElseThrow().entries().getFirst().componentMatch());
        assertTrue(ItemChoiceMatcher.plan(registries, new ArrayList<>(List.of(extra)), fuzzy).selectionValid());
    }
    @Test void tagMatcherRoundTripsCanonicalShape() {
        var parsed = ItemChoiceMatcher.parse("{\"tag\":\"minecraft:planks\",\"mode\":\"tag\"}")
                .result().orElseThrow();

        assertEquals(ItemChoiceMatcher.EntryKind.TAG, parsed.entries().getFirst().kind());
        assertEquals("minecraft:planks", parsed.entries().getFirst().value());
        assertTrue(parsed.encode().contains("\"version\":2"));
    }

    @Test void listMatcherPreservesOrderAndForcesCountOne() {
        var parsed = ItemChoiceMatcher.parse("{\"mode\":\"list\",\"items\":["
                + "\"{count:9,id:\\\"minecraft:stone\\\"}\","
                + "\"{count:2,id:\\\"minecraft:dirt\\\"}\"],\"required\":2}")
                .result().orElseThrow();
        assertEquals(2, parsed.requiredEntries());
        assertTrue(parsed.entries().get(0).value().contains("count:1"));
        assertTrue(parsed.entries().get(0).value().contains("minecraft:stone"));
        assertTrue(parsed.entries().get(1).value().contains("minecraft:dirt"));
    }

    @Test void invalidChoiceCountsAndDuplicateCandidatesAreRejected() {
        String duplicate = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\","
                + "\"{count:4,id:\\\"minecraft:stone\\\"}\"],\"required\":1}";
        String tooManyRequired = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\"],\"required\":2}";

        assertTrue(ItemChoiceMatcher.parse(duplicate).error().isPresent());
        assertTrue(ItemChoiceMatcher.parse(tooManyRequired).error().isPresent());
    }

    @Test void consumeUsesOnlySelectedCandidatesAndRejectsUnsatisfiedPlans() {
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(Items.STONE, 3), new ItemStack(Items.DIRT, 3)));
        var satisfied = new ItemChoiceMatcher.MatchPlan(List.of(
                new ItemChoiceMatcher.Candidate(0, new ItemStack(Items.STONE), 3, 2, true, true,
                        List.of(new ItemChoiceMatcher.Removal(0, 2))),
                new ItemChoiceMatcher.Candidate(1, new ItemStack(Items.DIRT), 3, 2, false, true, List.of())),
                1, 2, true, true);

        assertTrue(ItemChoiceMatcher.consume(inventory, satisfied));
        assertEquals(1, inventory.get(0).getCount());
        assertEquals(3, inventory.get(1).getCount());

        var unsatisfied = new ItemChoiceMatcher.MatchPlan(List.of(
                new ItemChoiceMatcher.Candidate(0, new ItemStack(Items.DIRT), 1, 2, true, true,
                        List.of(new ItemChoiceMatcher.Removal(1, 1)))), 1, 0, true, false);
        assertFalse(ItemChoiceMatcher.consume(inventory, unsatisfied));
        assertEquals(3, inventory.get(1).getCount());
    }

    @Test void canonicalEntriesKeepIndependentCountsAndPlayerSelection() {
        String raw = "{\"version\":2,\"entries\":["
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:stone\\\"}\",\"count\":2},"
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:dirt\\\"}\",\"count\":3}],"
                + "\"required\":1}";
        ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parse(raw).result().orElseThrow();
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(Items.STONE, 2), new ItemStack(Items.DIRT, 3)));

        ItemChoiceMatcher.MatchPlan selectedDirt = ItemChoiceMatcher.plan(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY), inventory, spec, List.of(1));

        assertTrue(selectedDirt.selectionValid());
        assertFalse(selectedDirt.candidates().get(0).selected());
        assertTrue(selectedDirt.candidates().get(1).selected());
        assertTrue(ItemChoiceMatcher.consume(inventory, selectedDirt));
        assertEquals(2, inventory.get(0).getCount());
        assertEquals(0, inventory.get(1).getCount());
    }

    @Test void itemTypeMatcherConsumesOnlyTheChosenComponentBearingStack() {
        ItemStack valuableSword = new ItemStack(Items.DIAMOND_SWORD);
        valuableSword.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
        ItemStack plainSword = new ItemStack(Items.DIAMOND_SWORD);
        List<ItemStack> inventory = new ArrayList<>(List.of(valuableSword, plainSword));
        ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parse("{\"version\":2,\"entries\":["
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:diamond_sword\\\"}\","
                + "\"count\":1}],\"required\":1}").result().orElseThrow();

        ItemChoiceMatcher.MatchPlan selectedPlainSword = ItemChoiceMatcher.plan(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY), inventory, spec, List.of(1));

        assertTrue(selectedPlainSword.selectionValid());
        assertTrue(ItemChoiceMatcher.consume(inventory, selectedPlainSword));
        assertEquals(1, inventory.get(0).getCount(), "the unselected component-bearing sword must remain");
        assertEquals(0, inventory.get(1).getCount(), "the explicitly selected plain sword must be consumed");
    }

    @Test void authoredComponentsRequireTheMatchingStackForProgressAndConsumption() {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Required name"));
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        ItemChoiceMatcher.Spec spec = new ItemChoiceMatcher.Spec(List.of(
                ItemChoiceMatcher.Entry.item(named.save(registries).toString(), 1)), 1);
        List<ItemStack> inventory = new ArrayList<>(List.of(plain, named));

        assertFalse(ItemChoiceMatcher.accepts(registries, spec, plain));
        assertTrue(ItemChoiceMatcher.accepts(registries, spec, named));
        assertFalse(ItemChoiceMatcher.plan(registries, inventory, spec, List.of(0)).selectionValid());
        ItemChoiceMatcher.MatchPlan plan = ItemChoiceMatcher.plan(registries, inventory, spec);
        assertEquals(1, plan.candidates().getFirst().present());
        assertTrue(ItemChoiceMatcher.consume(inventory, plan));
        assertEquals(1, inventory.get(0).getCount());
        assertEquals(0, inventory.get(1).getCount());
    }

    @Test void normalizedPlainItemStillAcceptsComponentVariants() {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        ItemChoiceMatcher.Spec plain = new ItemChoiceMatcher.Spec(List.of(
                ItemChoiceMatcher.Entry.item("{count:1,id:\"minecraft:diamond_sword\"}", 1)), 1);
        ItemChoiceMatcher.Spec normalized = ItemChoiceMatcher.normalize(registries, plain).result().orElseThrow();
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Variant"));

        assertTrue(ItemChoiceMatcher.accepts(registries, normalized, named));
    }

    @Test void distinctComponentVariantsStaySeparateWithoutOverlappingPlainItem() {
        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        first.set(DataComponents.CUSTOM_NAME, Component.literal("First"));
        ItemStack second = new ItemStack(Items.DIAMOND_SWORD);
        second.set(DataComponents.CUSTOM_NAME, Component.literal("Second"));
        ItemChoiceMatcher.Entry firstEntry = ItemChoiceMatcher.Entry.item(first.save(registries).toString(), 1);
        ItemChoiceMatcher.Entry secondEntry = ItemChoiceMatcher.Entry.item(second.save(registries).toString(), 1);
        ItemChoiceMatcher.Spec both = new ItemChoiceMatcher.Spec(List.of(firstEntry, secondEntry), 2);
        List<ItemStack> inventory = new ArrayList<>(List.of(first, second));

        assertEquals(2, ItemChoiceMatcher.plan(registries, inventory, both).satisfiedEntries());
        assertTrue(ItemChoiceMatcher.consume(inventory, ItemChoiceMatcher.plan(registries, inventory, both)));
        assertEquals(0, inventory.get(0).getCount());
        assertEquals(0, inventory.get(1).getCount());
        assertThrows(IllegalArgumentException.class, () -> new ItemChoiceMatcher.Spec(List.of(firstEntry,
                ItemChoiceMatcher.Entry.item("{count:1,id:\"minecraft:diamond_sword\"}", 1)), 1));
    }

    @Test void extraSelectedSlotThatWouldNotBeConsumedIsRejected() {
        ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parse("{\"version\":2,\"entries\":["
                + "{\"kind\":\"item\",\"stack\":\"{count:1,id:\\\"minecraft:stone\\\"}\","
                + "\"count\":1}],\"required\":1}").result().orElseThrow();
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(Items.STONE), new ItemStack(Items.STONE)));

        ItemChoiceMatcher.MatchPlan overSelected = ItemChoiceMatcher.plan(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(
                        net.minecraft.core.registries.BuiltInRegistries.REGISTRY), inventory, spec, List.of(0, 1));

        assertFalse(overSelected.selectionValid());
        assertFalse(ItemChoiceMatcher.consume(inventory, overSelected));
        assertEquals(1, inventory.get(0).getCount());
        assertEquals(1, inventory.get(1).getCount());
    }
}
