package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FtbV13ImporterTest {
    @Test void importsCompleteEowFixtureWithoutSilentLoss() throws Exception {
        FtbImportResult result = new FtbV13Importer().importBook(fixture(), "embers_of_winter", "main");
        assertFalse(result.report().hasFatal(), result.report().toJson());
        assertEquals(2, result.chapterGroupCount());
        assertEquals(6, result.chapterCount());
        assertEquals(53, result.questCount());
        assertEquals(60, result.taskCount() + result.rewardCount());
        var typedDefinitions = result.book().quests().stream()
                .flatMap(quest -> java.util.stream.Stream.concat(quest.tasks().stream().map(task -> task.typeId()),
                        quest.rewards().stream().map(reward -> reward.typeId())))
                .toList();
        assertEquals(43, typedDefinitions.stream().filter(id -> id.getPath().equals("checkmark")).count());
        assertEquals(13, typedDefinitions.stream().filter(id -> id.getPath().equals("item")).count());
        assertEquals(4, typedDefinitions.stream().filter(id -> id.getPath().equals("custom")).count());
        assertTrue(result.book().quests().stream().flatMap(quest -> quest.tasks().stream())
                .anyMatch(task -> "16L".equals(task.config().get("count"))), "Outer FTB item counts must survive mapping");
        assertEquals(53, result.book().quests().stream().map(q -> q.id()).distinct().count());
        assertTrue(result.book().legacyIds().containsKey("7D44928441162C4F"));
        assertEquals(NativeBookJson.encode(result.book()), NativeBookJson.encode(new FtbV13Importer().importBook(fixture(), "embers_of_winter", "main").book()));
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(getClass().getResource("/fixtures/ftb_v13/eow/data.snbt").toURI()).getParent();
    }
}
