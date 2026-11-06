package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EditorFormIdentityTest {
    @Test void reopeningTheSameTargetRejectsThePreviousChildResult() {
        var identity = new EditorFormIdentity();
        var session = UUID.randomUUID();
        var target = ResourceLocation.parse("test:quest");
        identity.open(session, target);
        var first = identity.token();
        assertTrue(identity.matches(first, session, target));
        identity.close();
        assertFalse(identity.matches(first, session, target));
        identity.open(session, target);
        assertFalse(identity.matches(first, session, target));
        assertTrue(identity.matches(identity.token(), session, target));
    }

    @Test void switchingLeaseOrObjectInvalidatesTheChildIdentity() {
        var identity = new EditorFormIdentity();
        var session = UUID.randomUUID();
        var target = ResourceLocation.parse("test:quest");
        identity.open(session, target);
        assertFalse(identity.matches(identity.token(), UUID.randomUUID(), target));
        assertFalse(identity.matches(identity.token(), session, ResourceLocation.parse("test:other")));
    }
}
