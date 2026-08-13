package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationContextTest {
    @Test
    void selfAuthorityCannotCrossThePlayerBoundary() {
        UUID actor = UUID.randomUUID();
        OperationContext context = new OperationContext(OperationContext.Authority.SELF,
                actor.toString(), "actor", "test:self");

        assertTrue(context.mayModify(actor));
        assertFalse(context.mayModify(UUID.randomUUID()));
    }

    @Test
    void integrationAuthorityIsExplicitAndCrossPlayer() {
        OperationContext context = OperationContext.integration(ResourceLocation.parse("example:quests"));

        assertTrue(context.mayModify(UUID.randomUUID()));
        assertTrue(context.source().equals("example:quests"));
    }

    @Test
    void blankAuditIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new OperationContext(
                OperationContext.Authority.INTEGRATION, "", "actor", "test:source"));
    }
}
