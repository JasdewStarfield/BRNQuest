package yourscraft.jasdewstarfield.brnquest.network;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthoringNetworkTest {
    @Test void typedMutationConfigIsCopiedBeforeUse() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("item", "{id:\"minecraft:stone\",count:1}");

        Map<String, String> decoded = AuthoringNetwork.boundedConfig(source);
        source.put("count", "64");

        assertEquals(Map.of("item", "{id:\"minecraft:stone\",count:1}"), decoded);
        assertThrows(UnsupportedOperationException.class, () -> decoded.put("count", "1"));
    }

    @Test void typedMutationConfigRejectsOversizedOrMalformedFields() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthoringNetwork.boundedConfig(Map.of("", "value")));
        assertThrows(IllegalArgumentException.class,
                () -> AuthoringNetwork.boundedConfig(Map.of("item", "x".repeat(65_537))));

        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int index = 0; index < 65; index++) tooMany.put("field_" + index, "value");
        assertThrows(IllegalArgumentException.class, () -> AuthoringNetwork.boundedConfig(tooMany));
    }

    @Test void typedCodecFailuresPointBackToTheCompleteRawConfig() {
        var wire = new AuthoringNetwork.EditorMutationWire("session", "test:book", "revision",
                "UPDATE_TASK", "test:task", "test:quest", "test:task", "", 0, 0, 0,
                List.of(), Map.of());
        var codecFailure = new Diagnostic(Diagnostic.Severity.ERROR, "BQV-119", "", "",
                "test:task", "Invalid task config");

        var mapped = AuthoringNetwork.mutationDiagnosticWires(wire, List.of(codecFailure));

        assertEquals("config", mapped.getFirst().path());
    }
}
