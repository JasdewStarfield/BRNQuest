package yourscraft.jasdewstarfield.brnquest.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleAddonBoundaryTest {
    @Test void exampleAddonUsesOnlyDocumentedBrnQuestPackages() throws IOException {
        // NeoForge changes the test working directory, so resolve sources from
        // the explicit project root supplied by the Gradle test task.
        Path root = Path.of(System.getProperty("brnquest.projectDir"), "src", "exampleAddon", "java");
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertFalse(sources.isEmpty(), "example add-on sources must exist");

        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            for (String forbidden : List.of(".data.", ".progress.", ".runtime.", ".network.",
                    ".workspace.", ".command.", ".platform.", "java.lang.reflect", "Class.forName",
                    "setAccessible(")) {
                assertFalse(text.contains("yourscraft.jasdewstarfield.brnquest" + forbidden)
                                || (!forbidden.startsWith(".") && text.contains(forbidden)),
                        () -> source + " bypasses the public API boundary with " + forbidden);
            }
        }

        String combined = sources.stream().map(path -> {
            try { return Files.readString(path, StandardCharsets.UTF_8); }
            catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
        }).reduce("", String::concat);
        for (String required : List.of("TaskTypeRegistry.register", "RewardTypeRegistry.register",
                "ClientTaskPresentationRegistry.register", "ClientRewardPresentationRegistry.register",
                "configFields", "BrnQuestEvents.subscribe")) {
            assertTrue(combined.contains(required), "example add-on must demonstrate " + required);
        }
    }
}
