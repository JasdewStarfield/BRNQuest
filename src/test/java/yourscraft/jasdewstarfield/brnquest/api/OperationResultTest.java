package yourscraft.jasdewstarfield.brnquest.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationResultTest {
    @Test
    void idempotentNoChangeRemainsSuccessfulWithoutClaimingAMutation() {
        OperationResult result = OperationResult.noChange("ALREADY_COMPLETED", "Quest already completed");

        assertTrue(result.success());
        assertFalse(result.changed());
    }

    @Test
    void compatibilityFailuresReceiveSpecificPublicCategories() {
        assertTrue(OperationResult.failure("NO_BOOK", "missing").status() == OperationStatus.NOT_READY);
        assertTrue(OperationResult.failure("INVALID_AMOUNT", "bad").status() == OperationStatus.INVALID_REQUEST);
        assertTrue(OperationResult.failure("LOCKED", "locked").status() == OperationStatus.REJECTED);
    }

    @Test
    void nullPlayerIsRejectedBeforeAnyMinecraftStateIsRead() {
        OperationResult result = BrnQuestApi.completeQuestResult(null, "test:quest");

        assertTrue(result.status() == OperationStatus.INVALID_REQUEST);
        assertTrue(result.code().equals("INVALID_PLAYER"));
    }
}
