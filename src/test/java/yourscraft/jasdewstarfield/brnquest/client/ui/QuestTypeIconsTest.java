package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestTypeIconsTest {
    @Test void registeredKillEntityUsesExistingKillAsset() {
        assertEquals(ResourceLocation.parse("brnquest:editor/type/kill"), QuestTypeIcons.sprite(
                yourscraft.jasdewstarfield.brnquest.task.encounter.EncounterConfig.KILL));
        assertEquals(ResourceLocation.parse("brnquest:editor/type/custom"),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:kill_entity")));
    }
    @Test void reviewedBuiltinGlyphsFitTheirPixelSlotsAndKeepDistinctSemantics() throws Exception {
        for (String type : new String[]{"checkmark","item","item_choice","xp","xp_levels","command","location","observe",
                "kill","advancement","biome","structure","dimension","custom","reward_table","loot_table"}) {
            var sprite = QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:" + type));
            // Decode shipped assets so missing files and invalid PNGs fail before packaging.
            try (var stream = getClass().getResourceAsStream("/assets/brnquest/textures/gui/sprites/" + sprite.getPath() + ".png")) {
                assertNotNull(stream, type);
                var image = javax.imageio.ImageIO.read(stream);
                assertNotNull(image, type);
                assertEquals(16, image.getWidth());
                assertEquals(16, image.getHeight());
            }
        }
        assertNotEquals(QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:xp")),
                QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:xp_levels")));
    }

    @Test void foreignSamePathUsesExtensionFallback() {
        assertEquals(QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:custom")),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:xp")));
        assertNotEquals(QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:xp")),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:xp")));
    }
}
