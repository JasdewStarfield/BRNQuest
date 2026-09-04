package yourscraft.jasdewstarfield.brnquest.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test void kubeJsPluginIsConditionallyDiscoveredAndIsolated() throws IOException {
        Path project = Path.of(System.getProperty("brnquest.projectDir"));
        Path pluginList = project.resolve(Path.of("src", "main", "resources", "kubejs.plugins.txt"));
        String declaration = Files.readString(pluginList, StandardCharsets.UTF_8).trim();
        String metadata = Files.readString(project.resolve(Path.of("src", "main", "templates", "META-INF",
                "neoforge.mods.toml")), StandardCharsets.UTF_8);
        Path plugin = project.resolve(Path.of("src", "main", "java", "yourscraft", "jasdewstarfield",
                "brnquest", "compat", "kubejs", "BrnQuestKubeJSPlugin.java"));
        String source = Files.readString(plugin, StandardCharsets.UTF_8);

        assertEquals("yourscraft.jasdewstarfield.brnquest.compat.kubejs.BrnQuestKubeJSPlugin kubejs", declaration);
        assertTrue(metadata.contains("modId = \"kubejs\""));
        assertTrue(metadata.substring(metadata.indexOf("modId = \"kubejs\"")).contains("type = \"optional\""));
        assertTrue(source.contains("bindings.type() == ScriptType.SERVER"));
        assertTrue(source.contains("bindings.add(\"BRNQuest\""));
        assertTrue(source.contains("events.register(BrnQuestKubeJSEvents.GROUP)"));
        assertTrue(source.contains("manager.scriptType == ScriptType.SERVER"));
        assertTrue(source.contains("ScriptExtensionRegistry.sealRegistration()"));
        assertTrue(source.contains("ScriptExtensionRegistry.failRegistration("));
        assertFalse(source.contains("commitSealedRegistration()"),
                "the KubeJS hook must not publish types before the task book validates");
        assertTrue(Files.isRegularFile(project.resolve(Path.of("src", "main", "java", "yourscraft",
                "jasdewstarfield", "brnquest", "compat", "kubejs", "BrnQuestKubeJSBindings.java"))));

        String eventSource = Files.readString(plugin.getParent().resolve("BrnQuestKubeJSEvents.java"),
                StandardCharsets.UTF_8);
        assertTrue(eventSource.contains("server(\"questCompleted\""));
        assertTrue(eventSource.contains("server(\"taskProgressChanged\""));
        assertTrue(eventSource.contains("server(\"rewardClaimed\""));
        assertTrue(eventSource.contains("server(\"customReward\""));
        assertTrue(eventSource.contains("requiredTarget(ID_TARGET)"));

        String rewardSource = Files.readString(plugin.getParent().resolve("BrnQuestKubeJSScriptTypes.java"),
                StandardCharsets.UTF_8);
        // The token is reproducible across retries and separates owners, rewards, and repeat cycles.
        assertTrue(rewardSource.contains("progress.owner().providerId()"));
        assertTrue(rewardSource.contains("progress.owner().ownerId()"));
        assertTrue(rewardSource.contains("context.reward().id()"));
        assertTrue(rewardSource.contains("progress.completedAtEpochMillis()"));

        String transactionSource = Files.readString(project.resolve(Path.of("src", "main", "java", "yourscraft",
                "jasdewstarfield", "brnquest", "runtime", "QuestBookReloadTransaction.java")),
                StandardCharsets.UTF_8);
        assertTrue(transactionSource.contains("withCandidateLookup"));
        assertTrue(transactionSource.contains("commitSealedRegistration"));
        assertTrue(transactionSource.contains("retainAfterReloadFailure"));
    }

    @Test void plannedOptionalModsDoNotLeakForeignTypesIntoCoreSources() throws IOException {
        Path root = Path.of(System.getProperty("brnquest.projectDir"), "src", "main", "java");
        Path integrations = root.resolve(Path.of("yourscraft", "jasdewstarfield", "brnquest", "compat"));
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(integrations)).sorted().toList();
        }

        for (Path source : sources) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            for (String foreignPackage : List.of("dev.latvian.mods.kubejs.", "dev.latvian.mods.rhino.",
                    "yourscraft.jasdewstarfield.brntalk.", "xaero.pac.")) {
                assertFalse(text.contains(foreignPackage),
                        () -> source + " leaks optional integration type " + foreignPackage);
            }
        }
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
