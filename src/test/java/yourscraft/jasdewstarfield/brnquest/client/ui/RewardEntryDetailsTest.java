package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class RewardEntryDetailsTest {
    @Test void titledItemKeepsEffectiveCountComponentsAndIndependentCopies() {
        var view = view(RewardTypes.ITEM, Map.of("title", "Supply pack", "count", "3"));
        var parsed = new ItemStack(Items.TORCH, 2);
        parsed.set(DataComponents.CUSTOM_NAME, Component.literal("Named torch"));
        var presentation = ClientRewardPresentationRegistry.get(view.typeId());
        var details = RewardEntryDetails.resolve(null, view, presentation, parsed);
        assertEquals("Supply pack · Named torch × 6", details.summary().getString());
        assertEquals(2, parsed.getCount());
        assertEquals(Component.literal("Named torch"), details.item().get(DataComponents.CUSTOM_NAME));
        details.item().setCount(99);
        details.summary().getSiblings().clear();
        assertEquals(6, details.item().getCount());
        assertEquals("Supply pack · Named torch × 6", details.summary().getString());
        assertEquals(details.summary(), RewardEntryDetails.fromDisplayed(null, view, presentation, details.item()).summary());
    }

    @Test void authoredXpTitleStillShowsAmountAndCorrectUnit() {
        for (var type : new ResourceLocation[]{RewardTypes.XP, RewardTypes.XP_LEVELS}) {
            String key = type.equals(RewardTypes.XP) ? "xp" : "xp_levels";
            var view = view(type, Map.of("title", "Travel", key, "12"));
            var presentation = ClientRewardPresentationRegistry.get(type);
            assertEquals("Travel · " + presentation.typeName(view).getString() + " × 12",
                    RewardEntryDetails.resolve(null, view, presentation, ItemStack.EMPTY).summary().getString());
        }
    }

    @Test void decorativeExtensionIconDoesNotBecomeAnIngredientOrQueryState() {
        var view = view(ResourceLocation.parse("example:xp"), Map.of("xp", "900"));
        var presentation = new ClientRewardPresentation() {
            public Optional<EditorIcon> icon(RewardView reward) {
                return Optional.of(EditorIcon.glyph(Component.literal("?")));
            }
            public Component interactionHint(RewardPresentationContext context) {
                throw new AssertionError("A configuration preview must not query execution state");
            }
        };
        var details = RewardEntryDetails.resolve(null, view, presentation, new ItemStack(Items.BOOK, 2));
        assertFalse(details.item().isEmpty());
        assertTrue(details.lookupItem().isEmpty());
        assertTrue(details.summary().getString().endsWith(" × 2"));
        assertFalse(details.summary().getString().contains("900"));
    }

    @Test void externalContentsKeepAuthoredTitleWithoutConsultingStatefulTitle() {
        var view = view(ResourceLocation.parse("example:experience"), Map.of("title", "Journey", "amount", "7"));
        var shared = Component.literal("Experience × 7");
        var presentation = new ClientRewardPresentation() {
            public Optional<Component> contentSummary(RewardView reward) { return Optional.of(shared); }
            public Component title(RewardPresentationContext context) {
                throw new AssertionError("Explicit configuration contents must not need a live title");
            }
            public Component interactionHint(RewardPresentationContext context) {
                throw new AssertionError("A contents preview must not inspect claim state");
            }
        };
        var details = RewardEntryDetails.resolve(null, view, presentation, ItemStack.EMPTY);
        assertEquals("Journey · Experience × 7", details.summary().getString());
        shared.append(" mutated");
        assertEquals("Journey · Experience × 7", details.summary().getString());
        assertTrue(details.lookupItem().isEmpty());
    }

    @Test void contentOverridePreservesItemLookupAndAddsNoDuplicateTitle() {
        var view = view(ResourceLocation.parse("example:bundle"), Map.of("title", "Travel kit"));
        var presentation = new ClientRewardPresentation() {
            public Optional<Component> contentSummary(RewardView reward) {
                return Optional.of(Component.literal("Travel kit"));
            }
        };
        var parsed = new ItemStack(Items.TORCH, 2);
        var details = RewardEntryDetails.resolve(null, view, presentation, parsed);
        assertEquals("Travel kit", details.summary().getString());
        assertEquals(2, details.lookupItem().getCount());
        assertEquals(details.summary(), RewardEntryDetails.fromDisplayed(null, view, presentation, details.item()).summary());
    }

    @Test void legacyPresentationKeepsItemFallbackAndDoesNotInheritXpByPath() {
        var view = view(ResourceLocation.parse("example:xp"), Map.of("xp", "900"));
        var presentation = new ClientRewardPresentation() {
            public Component title(RewardPresentationContext context) { return Component.literal("Legacy contents"); }
        };
        assertTrue(presentation.contentSummary(view).isEmpty());
        var item = new ItemStack(Items.BOOK, 2);
        assertEquals(item.getHoverName().getString() + " × 2",
                RewardEntryDetails.resolve(null, view, presentation, item).summary().getString());
    }

    @Test void statusHintsCanBeReadWithoutSendingPackets() {
        // There is no client connection in this unit test; rendering a missing state must be harmless.
        assertNotNull(RewardTableClientState.hint("test:unqueried"));
        assertEquals(Component.translatable("screen.brnquest.reward.claimed"),
                RewardTableClientState.hint("test:unqueried", true));
    }

    private static RewardView view(ResourceLocation type, Map<String,String> config) {
        return new RewardView(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:reward"),
                type, config, "manual", false);
    }
}
