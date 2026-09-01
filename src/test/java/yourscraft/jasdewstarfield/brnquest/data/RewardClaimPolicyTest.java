package yourscraft.jasdewstarfield.brnquest.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardClaimPolicyTest {
    @Test void legacyAndNativePoliciesExposeStableBehavior() {
        assertEquals(RewardClaimPolicy.AUTO_VISIBLE, RewardClaimPolicy.parse("auto"));
        assertTrue(RewardClaimPolicy.parse("auto_visible").automatic());
        assertTrue(RewardClaimPolicy.parse("auto_silent").visible());
        assertFalse(RewardClaimPolicy.parse("auto_silent").notifyPlayer());
        assertFalse(RewardClaimPolicy.parse("auto_hidden").visible());
        assertFalse(RewardClaimPolicy.parse("manual").automatic());
        assertFalse(RewardClaimPolicy.isKnown("surprise"));
    }
}
