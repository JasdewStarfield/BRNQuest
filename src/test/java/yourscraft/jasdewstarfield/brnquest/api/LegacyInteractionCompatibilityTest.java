package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientTaskPresentation;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine pre-change bytecode inherits interaction and lifecycle defaults from today's SPI. */
class LegacyInteractionCompatibilityTest {
    @Test @SuppressWarnings("unchecked") void oldTaskAndPresentationKeepTheirDefaultBehavior() throws Exception {
        var location = Path.of(System.getProperty("brnquest.legacyPresentationClasses")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{location}, TaskType.class.getClassLoader())) {
            assertSame(TaskType.class, loader.loadClass(TaskType.class.getName()));
            assertSame(ClientTaskPresentation.class, loader.loadClass(ClientTaskPresentation.class.getName()));
            var taskClass = loader.loadClass("legacy.LegacyTaskType");
            var clientClass = loader.loadClass("legacy.LegacyTaskPresentation");
            assertSame(loader, taskClass.getClassLoader()); assertSame(loader, clientClass.getClassLoader());
            var task = (TaskType<Map<String, String>>) taskClass.getConstructor().newInstance();
            var presentation = (ClientTaskPresentation) clientClass.getConstructor().newInstance();
            assertTrue(presentation.submissionInteraction(null).isEmpty());
            var book = ResourceLocation.parse("legacy:book");
            var view = new TaskView(book, ResourceLocation.parse("legacy:task"), ResourceLocation.parse("legacy:type"), Map.of(), false);
            var context = new TaskContext(null, book, ResourceLocation.parse("legacy:quest"), view, 7);
            assertEquals(7, task.craftedProgress(context, Map.of(), ItemStack.EMPTY));
            assertTrue(task.submit(context, Map.of(), new TaskSubmissionSelection(List.of(0))).success());
        }
    }
}
