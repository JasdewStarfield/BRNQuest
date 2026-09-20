package yourscraft.jasdewstarfield.brnquest.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Output ownership checks include bootstrap, constants and adapters, beyond selected domain packages. */
class CompilationModuleBoundaryTest {
    private static final String ROOT = "yourscraft.jasdewstarfield.brnquest.";
    private static Path output(String module) {
        return Path.of(System.getProperty("brnquest.buildDir"), "classes/java", module);
    }
    private static Set<String> names(String module) {
        return new ClassFileImporter().importPath(output(module)).stream().map(type -> type.getName()).collect(Collectors.toSet());
    }

    @Test void coreHasNoDownstreamBytecodeDependency() {
        Set<String> forbidden = names("builtin");
        forbidden.addAll(names("integration"));
        var core = new ClassFileImporter().importPath(output("main"));
        assertFalse(core.isEmpty());
        core.forEach(type -> type.getDirectDependenciesFromSelf().forEach(dependency ->
                assertFalse(forbidden.contains(dependency.getTargetClass().getName()), dependency.getDescription())));
    }

    @Test void builtinDoesNotDependOnItsAdapters() {
        Set<String> forbidden = names("integration");
        new ClassFileImporter().importPath(output("builtin")).forEach(type ->
                type.getDirectDependenciesFromSelf().forEach(dependency ->
                        assertFalse(forbidden.contains(dependency.getTargetClass().getName()), dependency.getDescription())));
    }

    @Test void coreOwnsEverySharedFallbackSprite() {
        // These legacy paths also decorate generic UI when the built-in module is absent.
        Path resources = Path.of(System.getProperty("brnquest.buildDir"), "resources/main/assets/brnquest/textures/gui/sprites/editor/type");
        for (String name : java.util.List.of("custom", "item", "checkmark", "reward_table"))
            assertTrue(java.nio.file.Files.isRegularFile(resources.resolve(name + ".png")), name);
    }

    @Test void compiledAddonConsumesClassifiedPublicTypesOnly() throws Exception {
        var addon = new ClassFileImporter().importPath(output("exampleAddon"));
        assertFalse(addon.isEmpty());
        for (var type : addon) {
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String name = dependency.getTargetClass().getName();
                if (!name.startsWith(ROOT)) continue;
                while (name.endsWith("[]")) name = name.substring(0, name.length() - 2);
                Class<?> target = Class.forName(name, false, getClass().getClassLoader());
                // Nested enum/record helpers share the compatibility classification of their public owner.
                while (target.getAnnotation(ApiStatus.class) == null && target.getEnclosingClass() != null)
                    target = target.getEnclosingClass();
                ApiStatus status = target.getAnnotation(ApiStatus.class);
                assertTrue(status != null && status.value() != ApiStability.INTERNAL, dependency.getDescription());
            }
            // A public type can still contain internal members; reject calls to those escape hatches.
            for (var access : type.getAccessesFromSelf()) {
                if (!access.getTargetOwner().getName().startsWith(ROOT)) continue;
                var member = access.getTarget().resolveMember().orElseThrow();
                member.tryGetAnnotationOfType(ApiStatus.class).ifPresent(status ->
                        assertNotEquals(ApiStability.INTERNAL, status.value(), access.getDescription()));
            }
        }
    }
}
