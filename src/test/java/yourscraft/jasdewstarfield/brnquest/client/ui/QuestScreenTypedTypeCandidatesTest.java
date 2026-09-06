package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestScreenTypedTypeCandidatesTest {
    @Test void addPickerOnlyListsCurrentlyCreatableTypes() {
        ResourceLocation builtIn = ResourceLocation.parse("brnquest:checkmark");
        ResourceLocation extension = ResourceLocation.parse("example:registered");
        ResourceLocation unavailable = ResourceLocation.parse("missing:historical");
        ResourceLocation legacyAlias = ResourceLocation.parse("brnquest:item_choice");

        List<ResourceLocation> candidates = QuestTypePickerModel.creatableTypeCandidates(
                Set.of(unavailable, legacyAlias, extension, builtIn),
                type -> !type.equals(unavailable), legacyAlias);

        // Missing providers and read-only legacy aliases stay valid in old data, not in creation UI.
        assertEquals(List.of(builtIn, extension), candidates);
    }
}
