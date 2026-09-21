package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import static org.junit.jupiter.api.Assertions.*;

class DecodedTaskConfigsTest {
    @AfterEach void clearCache() { DecodedTaskConfigs.clearForTest(); }

    @Test void explicitImmutableConfigIsReusedButProgressIsAlwaysLive() {
        var type = new ImmutableType();
        assertEquals(3, TaskTypeExecutor.sampledProgress(type, context("3", 0)));
        assertEquals(13, TaskTypeExecutor.sampledProgress(type, context("3", 10)));
        assertEquals(1, type.decodes.get());
        // A same-ID author edit must decode new data, not reuse the preceding book's value.
        assertEquals(9, TaskTypeExecutor.sampledProgress(type, context("9", 0)));
        assertEquals(2, type.decodes.get());
        var replacement = new ImmutableType();
        TaskTypeExecutor.sampledProgress(replacement, context("3", 0));
        assertEquals(1, replacement.decodes.get());
    }

    @Test void ordinaryAddOnKeepsFreshMutableConfigForEveryInvocation() {
        TaskType<AtomicInteger> type = new TaskType<>() {
            public Codec<AtomicInteger> configCodec() {
                return Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(
                        map -> new AtomicInteger(Integer.parseInt(map.get("value"))),
                        value -> Map.of("value", value.toString()));
            }
            public long sampledProgress(TaskContext context, AtomicInteger config) { return config.incrementAndGet(); }
            public boolean satisfied(TaskContext context, AtomicInteger config) { return false; }
            public Component describe(TaskView task, AtomicInteger config) { return Component.empty(); }
        };
        assertEquals(4, TaskTypeExecutor.sampledProgress(type, context("3", 0)));
        assertEquals(4, TaskTypeExecutor.sampledProgress(type, context("3", 0)));
    }

    @Test void boundedEvictionReDecodesWithoutChangingTheResult() {
        var type = new ImmutableType();
        for (int i = 0; i <= 4096; i++) TaskTypeExecutor.sampledProgress(type, context(Integer.toString(i), 0));
        assertEquals(0, TaskTypeExecutor.sampledProgress(type, context("0", 0)));
        assertEquals(4098, type.decodes.get());
    }

    @Test void invalidBuiltinConfigStillRejectsAndAnEditedConfigCanRecover() {
        var type = new yourscraft.jasdewstarfield.brnquest.builtin.observation.location.LocationTask("location");
        var task = new TaskView(id("book"), id("task"), id("type"), Map.of("dimension", "minecraft:overworld", "size", "0,1,1"), false);
        assertTrue(TaskTypeExecutor.configError(type, task).isPresent());
        var valid = new TaskView(task.bookId(), task.id(), task.typeId(), Map.of("dimension", "minecraft:overworld", "size", "1,1,1", "opaque", "kept"), false);
        assertTrue(TaskTypeExecutor.configError(type, valid).isEmpty());
        var decoded = DecodedTaskConfigs.decode(type, valid.config()).getOrThrow();
        assertEquals("kept", decoded.raw().get("opaque"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.raw().put("opaque", "changed"));
    }

    private static class ImmutableType implements ReusableTaskConfigType<Integer> {
        final AtomicInteger decodes = new AtomicInteger();
        public Codec<Integer> configCodec() {
            return Codec.unboundedMap(Codec.STRING, Codec.STRING).xmap(map -> {
                decodes.incrementAndGet(); return Integer.parseInt(map.get("value"));
            }, value -> Map.of("value", value.toString()));
        }
        public long sampledProgress(TaskContext context, Integer config) { return context.progress() + config; }
        public boolean satisfied(TaskContext context, Integer config) { return false; }
        public Component describe(TaskView task, Integer config) { return Component.empty(); }
    }
    private static TaskContext context(String value, long progress) {
        return new TaskContext(null, id("book"), id("quest"),
                new TaskView(id("book"), id("task"), id("type"), Map.of("value", value), false), progress);
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
}
