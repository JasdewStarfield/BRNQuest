package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixtureHashTest {
    @Test void fixtureMatchesRecordedHashes() throws Exception {
        Path root = Path.of(getClass().getResource("/fixtures/ftb_v13/eow/SHA256SUMS").toURI()).getParent();
        for (String line : Files.readAllLines(root.resolve("SHA256SUMS"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("  ", 2);
            byte[] bytes = Files.readAllBytes(root.resolve(parts[1]));
            assertEquals(parts[0], HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), parts[1]);
        }
    }
}
