package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** A pre-change consumer must link to the current interface without being recompiled against it. */
class LegacyRewardPresentationCompatibilityTest {
    @Test void experimental22ConsumerInheritsNewOptionalSummary() throws Exception {
        var location = Path.of(System.getProperty("brnquest.legacyPresentationClasses")).toUri().toURL();
        try (var loader = new URLClassLoader(new java.net.URL[]{location}, ClientRewardPresentation.class.getClassLoader())) {
            // Parent-first lookup intentionally uses the real current SPI, never the fixture interface.
            assertSame(ClientRewardPresentation.class, loader.loadClass(ClientRewardPresentation.class.getName()));
            Class<?> consumer = loader.loadClass("legacy.LegacyRewardPresentation");
            assertSame(loader, consumer.getClassLoader());
            assertFalse(java.util.Arrays.stream(consumer.getDeclaredMethods())
                    .anyMatch(method -> method.getName().equals("contentSummary")));
            var presentation = (ClientRewardPresentation) consumer.getConstructor().newInstance();
            assertTrue(presentation.contentSummary(null).isEmpty());
            assertEquals("Legacy reward", presentation.typeName(null).getString());
        }
    }
}
