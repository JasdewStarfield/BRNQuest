package yourscraft.jasdewstarfield.brnquest.api;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Verify default dispatch on old consumer bytecode, rather than recompiling it against the new SPI. */
class LegacySamplingCompatibilityTest {
    @Test void oldSamplerLinksAndItsNumericResultPassesThrough() throws Exception {
        var url = Path.of(System.getProperty("brnquest.legacyPresentationClasses")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{url}, TaskType.class.getClassLoader())) {
            var consumer = loader.loadClass("legacy.LegacySamplingTaskType");
            assertSame(loader, consumer.getClassLoader());
            var type = (TaskType<?>) consumer.getConstructor().newInstance();
            var book = ResourceLocation.parse("legacy:book");
            var task = new TaskView(book, ResourceLocation.parse("legacy:task"), ResourceLocation.parse("legacy:type"), Map.of(), false);
            var context = new TaskSamplingContext(new TaskContext(null, book, ResourceLocation.parse("legacy:quest"), task, 7), TaskSamplingState.EMPTY, false);
            assertFalse(type.usesSamplingState());
            assertEquals(7, TaskTypeExecutor.prepareSample(type, context).progress());
            assertEquals(10, TaskTypeExecutor.sample(type, context).progress());
            assertTrue(TaskTypeExecutor.sample(type, context).state().values().isEmpty());
        }
    }
}
