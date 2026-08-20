package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestIconValueTest {
    @Test void textureEncodingIsExplicitAndRoundTripsResourceLocation() {
        ResourceLocation texture = ResourceLocation.parse("example:textures/gui/quest/icon.png");
        String encoded = QuestIconValue.texture(texture);

        assertTrue(QuestIconValue.isTexture(encoded));
        assertEquals(texture, QuestIconValue.textureId(encoded).orElseThrow());
    }

    @Test void existingItemSnbtRemainsOutsideTextureEncoding() {
        String item = "{id:\"minecraft:diamond\",count:1}";

        assertFalse(QuestIconValue.isTexture(item));
        assertTrue(QuestIconValue.textureId(item).isEmpty());
    }
}
