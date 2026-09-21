package yourscraft.jasdewstarfield.brnquest.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Locks the compiled public API surface to a reviewable UTF-8 text contract. */
class PublicApiSnapshotTest {
    private static final String ROOT_PACKAGE = "yourscraft.jasdewstarfield.brnquest.";
    @Test void compiledPublicApiMatchesReviewedBaseline() throws Exception {
        Path project = Path.of(System.getProperty("brnquest.projectDir"));
        Path build = Path.of(System.getProperty("brnquest.buildDir"));
        Path classes = build.resolve(Path.of("classes", "java", "main"));
        Path actualReport = build.resolve(Path.of("reports", "public-api-signatures.actual.txt"));
        String actual = snapshot(classes);
        Files.createDirectories(actualReport.getParent());
        Files.writeString(actualReport, actual, StandardCharsets.UTF_8);
        String expectedHash;
        try (var stream = PublicApiSnapshotTest.class.getClassLoader()
                .getResourceAsStream("contracts/public-api-signatures.txt")) {
            if (stream == null) throw new IOException("Missing public API signature baseline resource");
            expectedHash = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertEquals(expectedHash, sha256(actual),
                "Public API changed. Review build/reports/public-api-signatures.actual.txt and update the baseline intentionally.");
    }

    private static String snapshot(Path classes) throws Exception {
        List<Class<?>> apiTypes = new ArrayList<>();
        try (var paths = Files.walk(classes)) {
            for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".class")).toList()) {
                String className = classes.relativize(path).toString()
                        .replace('\\', '.').replace('/', '.').replaceAll("\\.class$", "");
                if (!className.startsWith(ROOT_PACKAGE)) continue;
                // Mixin definitions cannot be loaded directly; neither they nor optional foreign entry points are API.
                if (className.startsWith(ROOT_PACKAGE + "client.mixin.")
                        || className.startsWith(ROOT_PACKAGE + "compat.jei.")
                        || className.startsWith(ROOT_PACKAGE + "compat.kubejs.")
                        || className.startsWith(ROOT_PACKAGE + "compat.opac.")) continue;
                Class<?> type = Class.forName(className, false, PublicApiSnapshotTest.class.getClassLoader());
                ApiStatus status = type.getAnnotation(ApiStatus.class);
                // The annotation and its enum define the classification vocabulary and
                // are therefore part of the contract even though an annotation cannot
                // usefully classify itself.
                boolean vocabulary = type == ApiStatus.class || type == ApiStability.class;
                if (Modifier.isPublic(type.getModifiers()) && (vocabulary
                        || status != null && status.value() != ApiStability.INTERNAL)) {
                    apiTypes.add(type);
                }
            }
        }
        apiTypes.sort(Comparator.comparing(Class::getName));

        StringBuilder output = new StringBuilder("# BRNQuest public API 0.1.0-experimental.27\n");
        for (Class<?> type : apiTypes) appendType(output, type);
        return output.toString();
    }

    private static void appendType(StringBuilder output, Class<?> type) {
        output.append("type ").append(type.getName()).append(" [")
                .append(kind(type)).append("]\n");

        Arrays.stream(type.getDeclaredConstructors())
                .filter(PublicApiSnapshotTest::isPublicContract)
                .map(PublicApiSnapshotTest::constructorSignature)
                .sorted().forEach(line -> output.append("  ").append(line).append('\n'));
        Arrays.stream(type.getDeclaredFields())
                .filter(PublicApiSnapshotTest::isPublicContract)
                .map(PublicApiSnapshotTest::fieldSignature)
                .sorted().forEach(line -> output.append("  ").append(line).append('\n'));
        Arrays.stream(type.getDeclaredMethods())
                .filter(PublicApiSnapshotTest::isPublicContract)
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .map(PublicApiSnapshotTest::methodSignature)
                .sorted().forEach(line -> output.append("  ").append(line).append('\n'));
    }

    private static boolean isPublicContract(Constructor<?> constructor) {
        return Modifier.isPublic(constructor.getModifiers()) && !isInternal(constructor.getAnnotation(ApiStatus.class));
    }

    private static boolean isPublicContract(Field field) {
        return Modifier.isPublic(field.getModifiers()) && !field.isSynthetic()
                && !isInternal(field.getAnnotation(ApiStatus.class));
    }

    private static boolean isPublicContract(Method method) {
        return Modifier.isPublic(method.getModifiers()) && !isInternal(method.getAnnotation(ApiStatus.class));
    }

    private static boolean isInternal(ApiStatus status) {
        return status != null && status.value() == ApiStability.INTERNAL;
    }

    private static String constructorSignature(Constructor<?> constructor) {
        return modifiers(constructor.getModifiers()) + " ctor(" + parameters(constructor.getGenericParameterTypes()) + ")";
    }

    private static String fieldSignature(Field field) {
        return modifiers(field.getModifiers()) + " field " + field.getGenericType().getTypeName() + " " + field.getName();
    }

    private static String methodSignature(Method method) {
        String prefix = modifiers(method.getModifiers());
        if (method.isDefault()) prefix += " default";
        return prefix + " method " + method.getGenericReturnType().getTypeName() + " " + method.getName()
                + "(" + parameters(method.getGenericParameterTypes()) + ")";
    }

    private static String parameters(java.lang.reflect.Type[] types) {
        return Arrays.stream(types).map(java.lang.reflect.Type::getTypeName)
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static String modifiers(int value) {
        return Modifier.toString(value & (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL
                | Modifier.ABSTRACT | Modifier.SYNCHRONIZED));
    }

    private static String kind(Class<?> type) {
        if (type.isAnnotation()) return "annotation";
        if (type.isEnum()) return "enum";
        if (type.isRecord()) return "record";
        if (type.isInterface()) return "interface";
        return "class";
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().withUpperCase().formatHex(digest);
    }
}
