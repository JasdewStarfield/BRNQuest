package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Existing companion bytecode inherits notification wording without implementing the new default hook. */
class LegacyTaskPresentationCompatibilityTest {
    @Test void oldTaskPresentationInheritsCompletionRequirementFromItsObjectiveTitle() throws Exception {
        var url = Path.of(System.getProperty("brnquest.legacyPresentationClasses")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{url}, ClientTaskPresentation.class.getClassLoader())) {
            var legacy = loader.loadClass("legacy.LegacyTaskPresentation");
            assertSame(loader, legacy.getClassLoader());
            assertThrows(NoSuchMethodException.class, () -> legacy.getDeclaredMethod("completionDescription", TaskPresentationContext.class));
            var presentation = (ClientTaskPresentation) legacy.getConstructor().newInstance();
            assertEquals("Legacy requirement", presentation.completionDescription(null).getString());
        }
    }
}
