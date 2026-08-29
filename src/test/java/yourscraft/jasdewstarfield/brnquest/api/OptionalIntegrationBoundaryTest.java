package yourscraft.jasdewstarfield.brnquest.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalIntegrationBoundaryTest {
    @Test void coreSourcesNeverResolveJeiTypesWhenTheOptionalModIsAbsent() throws IOException {
        Path root = Path.of(System.getProperty("brnquest.projectDir"), "src", "main", "java");
        Path integration = root.resolve(Path.of("yourscraft", "jasdewstarfield", "brnquest", "compat", "jei"));
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(integration)).sorted().toList();
        }

        assertFalse(sources.isEmpty());
        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            assertFalse(text.contains("mezz.jei."), () -> source + " leaks JEI into the core class-loading boundary");
            assertFalse(text.contains("compat.jei"), () -> source + " eagerly references the optional JEI package");
        }
        assertTrue(Files.isRegularFile(integration.resolve("BrnQuestJeiPlugin.java")));
    }

    @Test void jeiRegistersEveryScreenThatOffersShortcutLookupTargets() throws IOException {
        Path plugin = Path.of(System.getProperty("brnquest.projectDir"), "src", "main", "java",
                "yourscraft", "jasdewstarfield", "brnquest", "compat", "jei", "BrnQuestJeiPlugin.java");
        String source = Files.readString(plugin, StandardCharsets.UTF_8);

        // IGlobalGuiHandler targets are only queried after JEI recognizes the active Screen.
        assertTrue(source.contains("addGuiScreenHandler(EditorItemSelectorScreen.class"));
        assertTrue(source.contains("addGuiScreenHandler(ItemChoiceScreen.class"));
        assertTrue(source.contains("addGuiScreenHandler(QuestScreen.class"));
        assertTrue(source.contains("addGhostIngredientHandler(ItemChoiceScreen.class"));
        assertTrue(source.contains("addGlobalGuiHandler"));
    }

    @Test void jeiScreenPropertiesRejectThePreInitZeroSizedFrame() throws IOException {
        Path plugin = Path.of(System.getProperty("brnquest.projectDir"), "src", "main", "java",
                "yourscraft", "jasdewstarfield", "brnquest", "compat", "jei", "BrnQuestJeiPlugin.java");
        String source = Files.readString(plugin, StandardCharsets.UTF_8);
        int method = source.indexOf("private static IGuiProperties questProperties");
        int guard = source.indexOf("if (!hasValidDimensions(screen)) return null;", method);
        int properties = source.indexOf("new ScreenGuiProperties(QuestScreen.class", method);

        // Returning null is part of JEI's IScreenHandler contract and lets its next update retry.
        assertTrue(method >= 0 && guard > method && properties > guard,
                "QuestScreen dimensions must be validated before JEI receives GUI properties");
    }
}
