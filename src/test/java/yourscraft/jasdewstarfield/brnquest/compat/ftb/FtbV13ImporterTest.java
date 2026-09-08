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
    @Test void importsLocationBoxesAndRegistrySelectorsWithoutLosingSourceArrays() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/places.snbt"), """
                {id:"1000000000000001",quests:[{id:"2000000000000001",tasks:[
                {id:"3000000000000001",type:"location",dimension:"minecraft:the_nether",position:[I;-2,0,4],size:[I;2,3,4],ignore_dimension:true},
                {id:"3000000000000002",type:"ftbquests:biome",biome:"#minecraft:is_overworld"},
                {id:"3000000000000003",type:"dimension",dimension:"minecraft:overworld"},
                {id:"3000000000000004",type:"structure",structure:"#minecraft:village"}]}]}
                """,StandardCharsets.UTF_8);
        var imported=new FtbV13Importer().importBook(temporary,"test","main");
        assertFalse(imported.report().hasFatal(),imported.report().toJson());
        var tasks=imported.book().quests().getFirst().tasks();
        assertEquals("brnquest:location",tasks.getFirst().typeId().toString());
        assertEquals("-2,0,4",tasks.getFirst().config().get("position"));
        assertEquals("2,3,4",tasks.getFirst().config().get("size"));
        assertEquals("true",tasks.getFirst().config().get("ignore_dimension"));
        assertTrue(tasks.getFirst().config().containsKey("ftb.position"));
        assertEquals("#minecraft:is_overworld",tasks.get(1).config().get("biome"));
        assertEquals(imported.book(),NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(imported.book())).getAsJsonObject()));
    }

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

    @Test void equivalentLanguageFilesMergeAndConflictingTextBlocksImport() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.createDirectories(temporary.resolve("lang"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/zh-CN.snbt"), "{title:\"中文\"}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/zh_cn.snbt"), "{extra:\"保留\"}", StandardCharsets.UTF_8);
        var merged = new FtbV13Importer().importBook(temporary, "test", "main");
        assertFalse(merged.report().hasFatal(), merged.report().toJson());
        assertEquals("中文", merged.book().localization().resolve("zh_cn", "title", ""));
        assertEquals("保留", merged.book().localization().resolve("zh_cn", "extra", ""));
        Files.writeString(temporary.resolve("lang/zh_cn.snbt"), "{title:\"冲突\"}", StandardCharsets.UTF_8);
        var conflict = new FtbV13Importer().importBook(temporary, "test", "main");
        assertTrue(conflict.report().hasFatal());
        assertTrue(conflict.report().toJson().contains("BQF-106"));
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

    @Test void commandRewardsConvertPermissionsTemplatesAndUnresolvedFeedback() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/test.snbt"), """
                {id:"B000000000000001",quests:[{id:"C000000000000001",rewards:[
                  {id:"D000000000000001",type:"command",command:"/say {p}",silent:true},
                  {id:"D000000000000002",type:"command",command:"say hello",elevate_perms:true,permission_level:4},
                  {id:"D000000000000003",type:"command",command:"say feedback",feedback_message:"missing.key"}
                ]}]}
                """, StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary, "converted", "commands");
        var rewards = result.book().quests().getFirst().rewards();
        assertEquals("brnquest:command", rewards.getFirst().typeId().toString());
        assertEquals("say {p}", rewards.getFirst().config().get("command"));
        assertEquals("player", rewards.getFirst().config().get("source_mode"));
        assertEquals("true", rewards.getFirst().config().get("silent"));
        assertEquals("2", rewards.get(1).config().get("permission_level"));
        assertEquals("missing.key", rewards.get(2).config().get("ftb.feedback_message"));
        assertTrue(result.reportJson().contains("BQF-107"));
        var decoded = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(result.book())).getAsJsonObject());
        assertEquals(rewards, decoded.quests().getFirst().rewards());
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(getClass().getResource("/fixtures/ftb_v13/eow/data.snbt").toURI()).getParent();
    }
}
