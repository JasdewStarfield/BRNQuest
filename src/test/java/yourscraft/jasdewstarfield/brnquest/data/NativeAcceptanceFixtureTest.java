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
        assertEquals(4, book.chapterGroups().size());
        assertEquals(28, book.chapters().size());
        assertEquals(38, book.quests().size());
        assertEquals(2, report.diagnostics().size(), report::toJson);
        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-117")
                && diagnostic.objectId().equals("brnquest_test:unknown_task")), report::toJson);
        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-118")
                && diagnostic.objectId().equals("brnquest_test:unknown_reward")), report::toJson);

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
        QuestDefinition unknown = quest(book, "brnquest_test:unknown_types");
        assertEquals(ResourceLocation.parse("missing_test_mod:counter"), unknown.tasks().getFirst().typeId());
        assertEquals(ResourceLocation.parse("missing_test_mod:token"), unknown.rewards().getFirst().typeId());

        QuestDefinition hidden = quest(book, "brnquest_test:hidden_until_dependency_complete");
        assertTrue(hidden.behavior().hideUntilDependenciesComplete());
        assertTrue(quest(book, "brnquest_test:hidden_lock_icon").behavior().hideLockIcon());
        assertEquals(DependencyRequirement.ONE_COMPLETED,
                quest(book, "brnquest_test:one_completed").behavior().dependencyRequirement());
        assertEquals(2, quest(book, "brnquest_test:minimum_two_completed")
                .behavior().minimumRequiredDependencies());
        assertTrue(quest(book, "brnquest_test:sequential_objectives").behavior().sequentialTasks());
        assertEquals("true", quest(book, "brnquest_test:crafting_only_planks")
                .tasks().getFirst().config().get("only_from_crafting"));
        assertEquals(ResourceLocation.parse("brnquest:xp"),
                quest(book, "brnquest_test:experience_points").tasks().getFirst().typeId());
        assertEquals(ResourceLocation.parse("brnquest:xp_levels"),
                quest(book, "brnquest_test:experience_levels").rewards().getFirst().typeId());
        QuestDefinition repeat = quest(book, "brnquest_test:repeat_reward_blocked");
        assertTrue(repeat.behavior().repeatable());
        assertEquals(10, repeat.behavior().repeatCooldownSeconds());
        assertFalse(repeat.behavior().ignoreRewardBlocking());
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
