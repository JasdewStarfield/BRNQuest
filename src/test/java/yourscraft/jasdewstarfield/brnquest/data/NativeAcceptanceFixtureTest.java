package yourscraft.jasdewstarfield.brnquest.data;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** Protects the hand-authored acceptance workspace against schema and coverage regressions. */
class NativeAcceptanceFixtureTest {
    private static final String BOOK_RESOURCE =
            "/fixtures/native/acceptance_workspace/data/brnquest_test/brnquest/books/acceptance.json";

    @Test
    void acceptanceBookIsValidAndCoversInteractiveCases() throws IOException {
        QuestBookDefinition book = readBook();
        DiagnosticReport report = new DiagnosticReport();
        QuestBookValidator.validate(book, report);

        assertEquals(ResourceLocation.parse("brnquest_test:acceptance"), book.id());
        assertEquals(3, book.chapterGroups().size());
        assertEquals(24, book.chapters().size());
        assertEquals(14, book.quests().size());
        assertTrue(report.diagnostics().isEmpty(), report::toJson);

        QuestDefinition submit = quest(book, "brnquest_test:submit_stone");
        assertEquals("true", submit.tasks().getFirst().config().get("consume_items"));
        QuestDefinition multi = quest(book, "brnquest_test:multi_objective");
        assertEquals(3, multi.tasks().size());
        assertEquals(3, multi.rewards().size());
        QuestDefinition longDetail = quest(book, "brnquest_test:long_detail");
        assertTrue(longDetail.description().length() > 150);
        assertEquals(11, longDetail.rewards().size());
        assertEquals(ResourceLocation.parse("brnquest_test:multi_objective"),
                quest(book, "brnquest_test:cross_target").dependencies().getFirst());
        assertEquals(ResourceLocation.parse("brnquest:custom"),
                quest(book, "brnquest_test:custom_api").tasks().getFirst().typeId());
    }

    @Test
    void acceptanceBookRoundTripIsDeterministic() throws IOException {
        QuestBookDefinition book = readBook();
        String first = NativeBookJson.encode(book);
        String second = NativeBookJson.encode(NativeBookJson.decode(JsonParser.parseString(first).getAsJsonObject()));
        assertEquals(first, second);
    }

    @Test
    void deployableFixtureFilesMatchRecordedHashes() throws Exception {
        Path root = Path.of(NativeAcceptanceFixtureTest.class
                .getResource("/fixtures/native/acceptance_workspace/SHA256SUMS").toURI()).getParent();
        for (String line : Files.readAllLines(root.resolve("SHA256SUMS"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("  ", 2);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(parts[1])));
            assertEquals(parts[0], HexFormat.of().withUpperCase().formatHex(digest), parts[1]);
        }
    }

    private static QuestBookDefinition readBook() throws IOException {
        try (InputStream stream = NativeAcceptanceFixtureTest.class.getResourceAsStream(BOOK_RESOURCE)) {
            assertNotNull(stream, "Missing acceptance fixture " + BOOK_RESOURCE);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return NativeBookJson.decode(JsonParser.parseString(json).getAsJsonObject());
        }
    }

    private static QuestDefinition quest(QuestBookDefinition book, String id) {
        ResourceLocation expected = ResourceLocation.parse(id);
        return book.quests().stream().filter(quest -> quest.id().equals(expected)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing fixture quest " + id));
    }
}
