package yourscraft.jasdewstarfield.brnquest.network;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Completion follows the type's real threshold and the event packet carries identities rather than translated text. */
class QuestNotificationServiceTest {
    private static final ResourceLocation BOOK = ResourceLocation.parse("test:book");
    private static final TaskView TASK = new TaskView(BOOK, ResourceLocation.parse("test:task"), ResourceLocation.parse("test:counter"), Map.of(), false);
    private static final TaskType<Map<String, String>> COUNTER = new TaskType<>() {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public boolean satisfied(TaskContext context, Map<String, String> config) { return context.progress() >= 10; }
        public net.minecraft.network.chat.Component describe(TaskView task, Map<String, String> config) {
            return net.minecraft.network.chat.Component.literal("Counter");
        }
    };
    private static TaskContext context(long amount) { return new TaskContext(null, BOOK, ResourceLocation.parse("test:quest"), TASK, amount); }
    @Test void partialProgressAlreadyCompleteValuesAndResetsDoNotNotify() {
        assertFalse(QuestNotificationService.crossedCompletion(COUNTER, context(0), context(1)));
        assertFalse(QuestNotificationService.crossedCompletion(COUNTER, context(8), context(9)));
        assertTrue(QuestNotificationService.crossedCompletion(COUNTER, context(9), context(10)));
        assertFalse(QuestNotificationService.crossedCompletion(COUNTER, context(10), context(11)));
        assertFalse(QuestNotificationService.crossedCompletion(COUNTER, context(10), context(0)));
        assertTrue(QuestNotificationService.crossedCompletion(COUNTER, context(0), context(100)));
    }
    @Test void eventPacketRoundTripsWithoutLeakingServerLocaleOrNames() {
        var notice = new BrnQuestNetwork.QuestNotificationPayload("00000000-0000-0000-0000-000000000001",
                "test:book", "revision", "test:quest", "test:task", "task");
        var buffer = Unpooled.buffer();
        try {
            BrnQuestNetwork.QuestNotificationPayload.CODEC.encode(buffer, notice);
            assertEquals(notice, BrnQuestNetwork.QuestNotificationPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }
}
