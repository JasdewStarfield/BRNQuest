package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

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
        assertEquals(List.of("流程设计", "冬日余烬"), result.book().chapterGroups().stream()
                .sorted(java.util.Comparator.comparingInt(yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition::order))
                .map(yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition::title).toList());
        assertEquals(List.of(0, 1, 2, 3, 4), result.book().chapters().stream()
                .filter(chapter -> chapter.groupId().equals(result.book().chapterGroups().get(1).id()))
                .sorted(java.util.Comparator.comparingInt(yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition::order))
                .map(yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition::order).toList());
        assertTrue(result.book().quests().stream().anyMatch(quest -> quest.description().contains("耗尽之前抵达避难所")),
                "Localized FTB description lines must survive import");
        assertTrue(result.book().quests().stream().filter(quest -> quest.legacyId().equals("7D44928441162C4F"))
                .noneMatch(quest -> quest.title().equals(quest.legacyId())),
                "Untitled quests must derive a readable label from their first objective");
        assertEquals(NativeBookJson.encode(result.book()), NativeBookJson.encode(new FtbV13Importer().importBook(fixture(), "embers_of_winter", "main").book()));
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(getClass().getResource("/fixtures/ftb_v13/eow/data.snbt").toURI()).getParent();
    }
}
