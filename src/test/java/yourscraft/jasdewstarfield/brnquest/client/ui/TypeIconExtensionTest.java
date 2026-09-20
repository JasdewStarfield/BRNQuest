package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.builtin.client.QuestTypeIcons;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class TypeIconExtensionTest {
    @Test void taskAndRewardWithSameIdResolveTheirOwnRegistration() {
        var id = ResourceLocation.parse("icon_test:shared");
        var taskIcon = EditorIcon.glyph(Component.literal("T"));
        var rewardIcon = EditorIcon.glyph(Component.literal("R"));
        ClientTaskPresentationRegistry.register(id, new ClientTaskPresentation() {}, taskIcon);
        ClientRewardPresentationRegistry.register(id, new ClientRewardPresentation() {
            public Optional<EditorIcon> typeIcon() { return Optional.of(rewardIcon); }
        });
        assertSame(taskIcon, ClientTaskPresentationRegistry.typeIcon(id));
        assertSame(rewardIcon, ClientRewardPresentationRegistry.typeIcon(id));
        assertThrows(IllegalArgumentException.class,
                () -> ClientTaskPresentationRegistry.register(id, new ClientTaskPresentation() {}, rewardIcon));
        assertSame(taskIcon, ClientTaskPresentationRegistry.typeIcon(id));
    }

    @Test void legacyAndForeignTypesKeepFallbackWithoutBorrowingBuiltinIcons() {
        var id = ResourceLocation.parse("icon_test:kill_entity");
        ClientTaskPresentationRegistry.register(id, new ClientTaskPresentation() {});
        assertSame(QuestTypeIcons.fallback(), ClientTaskPresentationRegistry.typeIcon(id));
        assertSame(QuestTypeIcons.fallback(), ClientRewardPresentationRegistry.typeIcon(id));
        assertNotSame(QuestTypeIcons.fallback(), ClientTaskPresentationRegistry.typeIcon(
                yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig.KILL));
    }
}
