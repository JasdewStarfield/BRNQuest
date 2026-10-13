package yourscraft.jasdewstarfield.brnquest.data;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Task, quest and reward switches are independent and preserve old/addon content. */
class QuestNotificationPolicyTest {
    @Test void absentFlagsUseDefaultsAndExplicitSwitchesRemainIndependent() {
        assertEquals(QuestNotificationPolicy.DEFAULT, QuestNotificationPolicy.read(Map.of()));
        var base = Map.of("addon:opaque", "keep");
        var changed = QuestNotificationPolicy.apply(base, Map.of(QuestNotificationPolicy.TASK, "false", QuestNotificationPolicy.REWARD, "true"));
        assertEquals(new QuestNotificationPolicy(false, true, true), QuestNotificationPolicy.read(changed));
        assertEquals("keep", changed.get("addon:opaque"));
        assertEquals(changed, QuestNotificationPolicy.apply(changed, Map.of()));
        assertEquals(base, QuestNotificationPolicy.apply(changed, QuestNotificationPolicy.DEFAULT.fields()));
    }
    @Test void malformedReservedFieldsAreRejectedBeforeMutatingContent() {
        assertThrows(IllegalArgumentException.class, () -> QuestNotificationPolicy.apply(Map.of(), Map.of(QuestNotificationPolicy.QUEST, "yes")));
        assertThrows(IllegalArgumentException.class, () -> QuestNotificationPolicy.read(Map.of("brnquest:" + QuestNotificationPolicy.TASK, "1")));
    }
}
