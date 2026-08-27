package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttentionPingAnimationTest {
    @Test void remainsStillBeforeAndAfterTheThreeHopBurst() {
        assertEquals(0, AttentionPingAnimation.verticalOffset(0));
        assertEquals(0, AttentionPingAnimation.verticalOffset(
                AttentionPingAnimation.INITIAL_IDLE_NANOS - 1));
        assertEquals(0, AttentionPingAnimation.verticalOffset(
                AttentionPingAnimation.INITIAL_IDLE_NANOS
                        + AttentionPingAnimation.HOP_COUNT * AttentionPingAnimation.HOP_STRIDE_NANOS));
    }

    @Test void eachHopRisesAndReturnsToItsRestingPosition() {
        for (int hop = 0; hop < AttentionPingAnimation.HOP_COUNT; hop++) {
            long start = AttentionPingAnimation.INITIAL_IDLE_NANOS
                    + hop * AttentionPingAnimation.HOP_STRIDE_NANOS;
            assertEquals(0, AttentionPingAnimation.verticalOffset(start));
            assertTrue(AttentionPingAnimation.verticalOffset(start
                    + AttentionPingAnimation.HOP_ACTIVE_NANOS / 2) < 0);
            assertEquals(0, AttentionPingAnimation.verticalOffset(start
                    + AttentionPingAnimation.HOP_ACTIVE_NANOS));
        }
    }

    @Test void restartsCleanlyAtTheNextCycle() {
        assertEquals(AttentionPingAnimation.verticalOffset(1_770_000_000L),
                AttentionPingAnimation.verticalOffset(
                        AttentionPingAnimation.CYCLE_NANOS + 1_770_000_000L));
    }

    @Test void authoredBadgeTexturesRemainAvailableAtTheirNativeSize() throws IOException {
        for (String name : new String[]{"reward_ping.png", "submitable_ping.png"}) {
            String path = "/assets/brnquest/textures/gui/" + name;
            try (var stream = AttentionPingAnimationTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertNotNull(image, path);
                assertEquals(10, image.getWidth(), path);
                assertEquals(10, image.getHeight(), path);
            }
        }
    }
}
