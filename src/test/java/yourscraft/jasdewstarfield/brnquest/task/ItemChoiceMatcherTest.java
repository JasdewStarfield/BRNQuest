package yourscraft.jasdewstarfield.brnquest.task;
import yourscraft.jasdewstarfield.brnquest.builtin.item.ItemChoiceMatcher;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemChoiceMatcherTest {
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
