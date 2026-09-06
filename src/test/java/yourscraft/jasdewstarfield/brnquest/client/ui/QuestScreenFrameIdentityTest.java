package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestScreenFrameIdentityTest {
    @Test void revisionModeAndViewportAllInvalidateOldGeometry() {
        ResourceLocation book = ResourceLocation.parse("example:book");
        QuestScreenFrameIdentity baseline = new QuestScreenFrameIdentity(book, "rev-a", true, 800, 480);

        assertEquals(baseline, new QuestScreenFrameIdentity(book, "rev-a", true, 800, 480));
        assertNotEquals(baseline, new QuestScreenFrameIdentity(book, "rev-b", true, 800, 480));
        assertNotEquals(baseline, new QuestScreenFrameIdentity(book, "rev-a", false, 800, 480));
        assertNotEquals(baseline, new QuestScreenFrameIdentity(book, "rev-a", true, 640, 480));
    }

    @Test void rejectsNegativeLogicalDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new QuestScreenFrameIdentity(
                ResourceLocation.parse("example:book"), "rev-a", true, -1, 480));
    }
}
