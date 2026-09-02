package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FtbV13ImporterTest {
    @TempDir Path temporary;
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

    @Test void mapsV13OptionalAutoPoliciesNamespacesLanguagesAndExtensions() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.createDirectories(temporary.resolve("lang"));
        Files.writeString(temporary.resolve("data.snbt"),
                "{version:13,default_autoclaim_rewards:\"enabled\",unknown_root:42}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"),
                "{chapter_groups:[{id:\"A000000000000001\"}]}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/test.snbt"), """
                {id:"B000000000000001",group:"A000000000000001",mystery:"kept",quests:[{
                  id:"C000000000000001",x:1d,y:2d,shape:"circle",size:1.5d,icon_scale:0.75d,min_width:2d,
                  hide_until_deps_complete:true,invisible:true,invisible_until_tasks:2,
                  dependency_requirement:"one_completed",min_required_dependencies:0,
                  require_sequential_tasks:true,can_repeat:true,repeat_cooldown:30,
                  tasks:[{id:"D000000000000001",type:"examplemod:counter",optional_task:true},
                    {id:"D000000000000002",type:"xp",value:5,points:true}],
                  rewards:[
                    {id:"E000000000000001",type:"custom",auto:"default"},
                    {id:"E000000000000002",type:"custom",auto:"no_toast"},
                    {id:"E000000000000003",type:"custom",auto:"invisible"},
                    {id:"E000000000000004",type:"xp",xp:7},
                    {id:"E000000000000005",type:"xp_levels",xp_levels:2}
                  ]
                }]}
                """, StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/en_us.snbt"),
                "{title:\"English\",quest.C000000000000001.title:\"Hello\"}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/zh_cn.snbt"),
                "{title:\"中文\",quest.C000000000000001.title:\"你好\",quest.C000000000000001.quest_desc:[\"第一行\",\"第二行\"]}",
                StandardCharsets.UTF_8);

        FtbImportResult result = new FtbV13Importer().importBook(temporary, "converted", "main");
        var quest = result.book().quests().getFirst();
        assertFalse(result.report().hasFatal(), result.report().toJson());
        assertTrue(quest.tasks().getFirst().optional());
        assertEquals(ResourceLocation.parse("examplemod:counter"), quest.tasks().getFirst().typeId());
        assertEquals(List.of(RewardClaimPolicy.AUTO_VISIBLE, RewardClaimPolicy.AUTO_SILENT,
                        RewardClaimPolicy.AUTO_HIDDEN),
                quest.rewards().subList(0, 3).stream().map(reward -> reward.policy()).toList());
        assertEquals("你好", result.book().localization().resolve("zh_cn", "quest.C000000000000001.title", ""));
        assertEquals("Hello", result.book().localization().resolve("en_us", "quest.C000000000000001.title", ""));
        assertEquals("第一行\n第二行", result.book().localization().resolve("zh_cn",
                "quest.C000000000000001.quest_desc", quest.description()));
        assertEquals("circle", quest.appearance().shape());
        assertTrue(quest.behavior().hideUntilDependenciesComplete());
        assertTrue(quest.behavior().invisibleUntilComplete());
        assertEquals(2, quest.behavior().visibleAfterTasks());
        assertEquals(DependencyRequirement.ONE_COMPLETED, quest.behavior().dependencyRequirement());
        assertTrue(quest.behavior().sequentialTasks());
        assertTrue(quest.behavior().repeatable());
        assertEquals(30, quest.behavior().repeatCooldownSeconds());
        assertEquals(ResourceLocation.parse("brnquest:xp"), quest.tasks().get(1).typeId());
        assertEquals(List.of(ResourceLocation.parse("brnquest:xp"), ResourceLocation.parse("brnquest:xp_levels")),
                quest.rewards().subList(3, 5).stream().map(reward -> reward.typeId()).toList());
        assertEquals("\"kept\"", result.book().chapters().getFirst().extensions().get("ftb.mystery"));
        assertTrue(result.fieldConversions().stream().anyMatch(conversion ->
                conversion.sourceField().equals("optional_task") && conversion.targetField().equals("optional")));
        assertTrue(result.reportJson().contains("PRESERVED_EXTENSION"));
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(getClass().getResource("/fixtures/ftb_v13/eow/data.snbt").toURI()).getParent();
    }
}
