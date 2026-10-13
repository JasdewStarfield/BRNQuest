package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.builtin.client.QuestTypeIcons;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestTypeIconsTest {
    @Test void registeredKillEntityUsesExistingKillAsset() {
        assertEquals(ResourceLocation.parse("brnquest:editor/type/kill"), QuestTypeIcons.sprite(
                yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig.KILL));
        assertEquals(ResourceLocation.parse("brnquest:editor/type/unknown"),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:kill_entity")));
    }
    @Test void reviewedBuiltinGlyphsFitTheirPixelSlotsAndKeepDistinctSemantics() throws Exception {
        for (String type : new String[]{"checkmark","item","item_choice","xp","xp_levels","command","location","observe",
                "kill","advancement","biome","structure","dimension","custom","reward_table","loot_table","statistics","unknown"}) {
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
        assertEquals(QuestTypeIcons.sprite(ResourceLocation.parse("example:other_type")),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:xp")));
        assertNotEquals(QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:custom")),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:xp")));
        assertNotEquals(QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:xp")),
                QuestTypeIcons.sprite(ResourceLocation.parse("example:xp")));
    }

    @Test void statisticsAndRewardTableHaveDifferentPixelSilhouettes() throws Exception {
        var masks = new java.util.ArrayList<java.util.List<Integer>>();
        for (String type : new String[]{"statistics", "reward_table"}) {
            var sprite = QuestTypeIcons.sprite(ResourceLocation.parse("brnquest:" + type));
            try (var stream = getClass().getResourceAsStream("/assets/brnquest/textures/gui/sprites/" + sprite.getPath() + ".png")) {
                assertNotNull(stream);
                var image = javax.imageio.ImageIO.read(stream);
                var alpha = new java.util.ArrayList<Integer>();
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) alpha.add(image.getRGB(x, y) >>> 24);
                masks.add(alpha);
            }
        }
        assertNotEquals(masks.get(0), masks.get(1), "separate resource paths must also carry distinguishable artwork");
    }
}
