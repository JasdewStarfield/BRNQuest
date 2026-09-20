package yourscraft.jasdewstarfield.brnquest.client.ui;
import org.junit.jupiter.api.Test;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
/** Old bytecode must inherit contextual editing without replacing unrelated config fields. */
class LegacyConfigEditorCompatibilityTest {
    @Test void oldFactoryProducesOnlyItsOwnFieldPatch() throws Exception {
        var url = Path.of(System.getProperty("brnquest.legacyEditorClasses")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{url}, ClientConfigEditors.class.getClassLoader())) {
            var consumer = loader.loadClass("legacy.LegacyConfigEditor");
            assertSame(loader, consumer.getClassLoader());
            var factory = (ClientConfigEditors.Factory) consumer.getConstructor().newInstance();
            var patch = new AtomicReference<Map<String, String>>();
            assertNull(factory.create(null, "field", Map.of("field", "old", "opaque", "keep"), patch::set));
            assertEquals(Map.of("field", "old-edited"), patch.get());
            assertTrue(factory.icon("old").isEmpty());
            assertNotNull(factory.label("old"));
        }
    }
}
