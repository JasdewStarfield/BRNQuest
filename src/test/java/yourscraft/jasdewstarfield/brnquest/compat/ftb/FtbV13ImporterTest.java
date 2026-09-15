package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement;
import yourscraft.jasdewstarfield.brnquest.data.BookText;
import yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FtbV13ImporterTest {
    @TempDir Path temporary;
    @Test void dependencyLineDefaultsKeepTaskInheritanceAndExplicitOverrides() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/a.snbt"),
                "{id:'1000000000000001',default_hide_dependency_lines:true,quests:[{id:'0000000000000001'},{id:'0000000000000002',hide_dependency_lines:false},{id:'0000000000000003',hide_dependency_lines:true}]}", StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary,"test","main");
        assertFalse(result.report().hasErrors(),result.report().toJson());
        var chapter = result.book().chapters().getFirst(); assertTrue(chapter.defaultHideDependencyLines());
        assertNull(chapter.quests().get(0).appearance().hideDependencyLines());
        assertEquals(false,chapter.quests().get(1).appearance().hideDependencyLines());
        assertEquals(true,chapter.quests().get(2).appearance().hideDependencyLines());
        assertFalse(chapter.extensions().containsKey("ftb.default_hide_dependency_lines"));
        assertFalse(chapter.quests().get(1).extensions().containsKey("ftb.hide_dependency_lines"));
        var decoded = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(result.book())).getAsJsonObject());
        assertEquals(chapter,decoded.chapters().getFirst());
    }

    @Test void autofocusResolvesHexQuestIdsAndPreservesUnsupportedTargets() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Path chapter = temporary.resolve("chapters/a.snbt");
        for (String focus : List.of("aBcD", "000000000000ABCD", "1000000000000002", "invalid")) {
            Files.writeString(chapter, "{id:'1000000000000001',autofocus_id:'" + focus
                    + "',quests:[{id:'000000000000ABCD'}]}", StandardCharsets.UTF_8);
            var result = new FtbV13Importer().importBook(temporary, "test", "main");
            assertFalse(result.report().hasErrors(), result.report().toJson());
            var imported = result.book().chapters().getFirst();
            if (focus.equals("aBcD") || focus.equals("000000000000ABCD")) {
                assertEquals(imported.quests().getFirst().id(), imported.autofocusQuestId());
                assertFalse(imported.extensions().containsKey("ftb.autofocus_id"));
            } else {
                assertNull(imported.autofocusQuestId());
                assertTrue(imported.extensions().get("ftb.autofocus_id").contains(focus));
                assertTrue(result.report().toJson().contains("BQF-030"));
            }
        }
    }

    @Test void bookAndChapterPoliciesResolveExplicitFalseAndRetainSuppression() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"),
                "{version:13,default_consume_items:true,default_reward_team:true,default_autoclaim_rewards:'enabled',suppress_all_autoclaiming:true,pause_game:true}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/a.snbt"), """
                {id:'1000000000000001',consume_items:false,quests:[{id:'2000000000000001',tasks:[
                {id:'3000000000000001',type:'item',item:{id:'minecraft:stone',count:1}},
                {id:'3000000000000002',type:'item',item:{id:'minecraft:stone',count:1},consume_items:true}],rewards:[
                {id:'4000000000000001',type:'xp',xp:3},
                {id:'4000000000000002',type:'xp',xp:4,team_reward:false,auto:'invisible'}]}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/b.snbt"), """
                {id:'1000000000000002',quests:[{id:'2000000000000002',tasks:[
                {id:'3000000000000003',type:'item',item:{id:'minecraft:stone',count:1}}]}]}
                """, StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary, "test", "main");
        assertFalse(result.report().hasErrors(), result.report().toJson());
        var book = result.book();
        assertTrue(book.settings().consumeItems());
        assertTrue(book.settings().suppressAutoClaim());
        assertTrue(book.settings().pauseGame());
        var first = book.quests().getFirst();
        assertEquals("false", first.tasks().get(0).config().get("consume_items"));
        assertEquals("true", first.tasks().get(1).config().get("consume_items"));
        assertEquals("true", book.quests().getLast().tasks().getFirst().config().get("consume_items"));
        assertTrue(first.rewards().get(0).teamReward());
        assertFalse(first.rewards().get(1).teamReward());
        assertEquals("auto_visible", first.rewards().get(0).claimPolicy());
        assertEquals("auto_hidden", first.rewards().get(1).claimPolicy(), "Suppression preserves the source policy for later re-enabling");
        assertEquals(book, NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject()));
    }

    @Test void unknownGroupFieldsRemainSourceExtensions() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"),
                "{chapter_groups:[{id:'1234567890123456',addon_data:{enabled:false,count:0}}]}", StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary, "test", "main");
        assertFalse(result.report().hasErrors(), result.report().toJson());
        var group = result.book().chapterGroups().stream().filter(g -> g.extensions().containsKey("ftb.addon_data"))
                .findFirst().orElseThrow();
        assertTrue(group.extensions().get("ftb.addon_data").contains("count:0"));
        assertEquals("", group.icon(), "Unknown source fields must not be guessed into native metadata");
        var decoded = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(result.book())).getAsJsonObject());
        assertEquals(group, decoded.chapterGroups().stream().filter(g -> g.id().equals(group.id())).findFirst().orElseThrow());
    }

    @Test void nestedSharedReferencesAreSnapshotsButCyclesAreRejected() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));Files.createDirectories(temporary.resolve("reward_tables"));
        Files.writeString(temporary.resolve("data.snbt"),"{version:13}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"),"{}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("reward_tables/aa.snbt"),"{id:'aa',rewards:[{type:'xp',xp:2}]}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/nested.snbt"),"""
                {id:'1000000000000001',quests:[{id:'2000000000000001',rewards:[
                {id:'3000000000000001',type:'choice',auto:'disabled',table_data:{rewards:[
                {type:'all_table',table_id:'aa'},{type:'all_table',table_id:'aa'}]}}]}]}
                """,StandardCharsets.UTF_8);
        var result=new FtbV13Importer().importBook(temporary,"test","main");
        assertFalse(result.report().hasErrors(),result.report().toJson());
        var tree=yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(result.book().quests().getFirst().rewards().getFirst().config().get("table"));
        assertTrue(tree.entries().stream().allMatch(e->e.has("table") && !e.getAsJsonObject("config").has("table")));
        // A shared reference is legal; replacing it with a self-reference must stop instead of recursing forever.
        Files.writeString(temporary.resolve("reward_tables/aa.snbt"),"{id:'aa',rewards:[{type:'all_table',table_id:'aa'}]}",StandardCharsets.UTF_8);
        var cyclic=new FtbV13Importer().importBook(temporary,"test","main");
        assertTrue(cyclic.report().hasErrors());assertTrue(cyclic.report().toJson().contains("cycle"));
    }
    @Test void choiceImportsWithoutRandomDrawRequirements() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/choice.snbt"), """
                {id:"1000000000000001",quests:[{id:"2000000000000001",rewards:[
                {id:"3000000000000001",type:"choice",auto:"disabled",table_data:{rewards:[
                {type:"xp",xp:2,weight:0.0f},{type:"xp",xp:3,weight:0.0f}]}}]}]}
                """, StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary, "test", "main");
        assertFalse(result.report().hasErrors(), result.report().toJson());
        var reward = result.book().quests().getFirst().rewards().getFirst();
        var tree = yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(reward.config().get("table"));
        assertEquals("choice",tree.mode()); assertEquals(2,tree.entries().size());
        assertEquals("brnquest:reward_table",reward.typeId().toString());
        assertTrue(reward.config().containsKey("table_data"));
    }

    @Test void randomAndLootMapFractionalWeightsAndDifferentEmptyPolicies() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/random.snbt"), """
                {id:"1000000000000001",quests:[{id:"2000000000000001",rewards:[
                {id:"3000000000000001",type:"random",table_data:{loot_size:2,empty_weight:2.0f,rewards:[
                {type:"xp",xp:2,weight:0.0f},{type:"xp",xp:3,weight:0.25f}]}},
                {id:"3000000000000002",type:"loot",table_data:{loot_size:2,empty_weight:2.0f,rewards:[
                {type:"xp",xp:3,weight:0.25f}]}}]}]}
                """, StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary, "test", "main");
        assertFalse(result.report().hasErrors(), result.report().toJson());
        var rewards = result.book().quests().getFirst().rewards();
        var random = yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(rewards.getFirst().config().get("table"));
        var loot = yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(rewards.get(1).config().get("table"));
        assertEquals("brnquest:reward_table", rewards.get(1).typeId().toString());
        assertEquals("random", random.mode()); assertEquals(2, random.rolls()); assertTrue(random.replacement());
        assertEquals(0, random.emptyWeight().signum()); assertEquals(new java.math.BigDecimal("2"), loot.emptyWeight());
        assertTrue(yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.always(random.entries().getFirst()));
        assertEquals(new java.math.BigDecimal("0.25"), yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.weight(random.entries().get(1)));
        assertTrue(rewards.getFirst().config().containsKey("table_data"), "original source remains available");
    }

    @Test void degenerateFtbRandomPoolsAreDiagnosedInsteadOfInventingRewards() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        for (String data : List.of("{rewards:[{type:'xp',xp:1}]}",
                "{loot_size:0,rewards:[{type:'xp',xp:1}]}", "{loot_size:1,rewards:[{type:'xp',xp:1,weight:0.0f}]}",
                "{loot_size:1,rewards:[{type:'xp',xp:1,weight:'bad'}]}",
                "{loot_size:1,rewards:[{type:'xp',xp:1,weight:3e38f},{type:'xp',xp:2,weight:3e38f}]}",
                "{loot_size:1,rewards:[{type:'random',table_id:5L}]}")) {
            Files.writeString(temporary.resolve("chapters/random.snbt"),
                    "{id:'1000000000000001',quests:[{id:'2000000000000001',rewards:[{id:'3000000000000001',type:'random',table_data:"
                            + data + "}]}]}", StandardCharsets.UTF_8);
            var result = new FtbV13Importer().importBook(temporary, "test", "main");
            assertTrue(result.report().hasErrors(), data);
            assertTrue(result.report().toJson().contains("BQF-108"));
            assertTrue(result.book().quests().getFirst().rewards().getFirst().config().containsKey("table_data"));
        }
    }

    @Test void allTableExpandsAnIndependentSnapshotThroughSharedRewardConversion() throws Exception {
        Files.createDirectories(temporary.resolve("chapters")); Files.createDirectories(temporary.resolve("reward_tables"));
        Files.writeString(temporary.resolve("data.snbt"),"{version:13}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"),"{}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("reward_tables/00000000000000AB.snbt"),"""
                {id:"00000000000000AB",rewards:[{id:"0000000000000001",item:{id:"minecraft:bread",count:2},weight:0.0f},
                {id:"0000000000000002",type:"command",command:"say imported",permission_level:2}]}
                """,StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/tables.snbt"),"""
                {id:"1000000000000001",quests:[{id:"2000000000000001",rewards:[
                {id:"3000000000000001",type:"all_table",table_id:171L},
                {id:"3000000000000002",type:"all_table",table_id:171L}]}]}
                """,StandardCharsets.UTF_8);
        var imported = new FtbV13Importer().importBook(temporary,"test","main");
        assertFalse(imported.report().hasErrors(),imported.report().toJson());
        var rewards = imported.book().quests().getFirst().rewards();
        assertEquals("brnquest:reward_table",rewards.getFirst().typeId().toString());
        var tree = yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.parse(rewards.getFirst().config().get("table"));
        assertEquals(2,tree.entries().size());
        assertTrue(tree.entries().getFirst().get("always").getAsBoolean());
        assertEquals("say imported",yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableTree.config(tree.entries().get(1)).get("command"));
        assertEquals(rewards.getFirst().config().get("table"),rewards.get(1).config().get("table"));
        assertFalse(imported.book().legacyIds().containsKey("0000000000000001"),"child aliases never replace root IDs");
    }
    @Test void importsObservationAndKillAliasesThroughNativeRoundTrip() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"),"{version:13}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"),"{}",StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/encounters.snbt"),"""
                {id:"1000000000000001",quests:[{id:"2000000000000001",tasks:[
                {id:"3000000000000001",type:"ftbquests:observation",observation_type:"block_tag",to_observe:"minecraft:logs",timer:20L},
                {id:"3000000000000002",type:"kill",entity:"minecraft:zombie",value:3L}]}]}
                """,StandardCharsets.UTF_8);
        var imported=new FtbV13Importer().importBook(temporary,"test","main");
        assertFalse(imported.report().hasFatal(),imported.report().toJson());
        var tasks=imported.book().quests().getFirst().tasks();
        assertEquals("brnquest:observe",tasks.getFirst().typeId().toString());
        assertEquals("#minecraft:logs",tasks.getFirst().config().get("target"));
        assertEquals("brnquest:kill_entity",tasks.get(1).typeId().toString());
        assertEquals("3",tasks.get(1).config().get("count"));
        assertEquals(imported.book(),NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(imported.book())).getAsJsonObject()));
    }

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

    @Test void explorationAliasesReportTheSameMappingAsTheImportedType() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        for (String kind : List.of("dimension", "biome", "location", "structure")) {
            for (String prefix : List.of("", "ftbquests:")) {
                String source = prefix + kind;
                Files.writeString(temporary.resolve("chapters/places.snbt"),
                        "{id:'1000000000000001',quests:[{id:'2000000000000001',tasks:[{id:'3000000000000001',type:'"
                                + source + "'}]}]}", StandardCharsets.UTF_8);
                var imported = new FtbV13Importer().importBook(temporary, "test", "main");
                assertEquals("brnquest:" + kind, imported.book().quests().getFirst().tasks().getFirst().typeId().toString());
                var conversions = imported.fieldConversions().stream().filter(c -> c.sourceField().equals("type")).toList();
                assertEquals(1, conversions.size(), source);
                assertEquals(FtbFieldConversion.Status.MAPPED, conversions.getFirst().status(), source);
            }
        }
    }

    @Test void advancementTasksAndRewardsPreserveCriterionForBothAliases() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        for (String source : List.of("advancement", "ftbquests:advancement")) {
            Files.writeString(temporary.resolve("chapters/main.snbt"),
                    "{id:'1000000000000001',quests:[{id:'2000000000000001',tasks:[{id:'3000000000000001',type:'" + source
                    + "',advancement:'test:one',criterion:'a'}],rewards:[{id:'4000000000000001',type:'" + source
                    + "',advancement:'test:one',criterion:'b'}]}]}", StandardCharsets.UTF_8);
            var result = new FtbV13Importer().importBook(temporary,"test","main");
            var quest = result.book().quests().getFirst();
            assertEquals("brnquest:advancement",quest.tasks().getFirst().typeId().toString());
            assertEquals("brnquest:advancement",quest.rewards().getFirst().typeId().toString());
            assertEquals("a",quest.tasks().getFirst().config().get("criterion"));
            assertEquals("b",quest.rewards().getFirst().config().get("criterion"));
            assertTrue(result.fieldConversions().stream().filter(c->c.sourceField().equals("type"))
                    .allMatch(c->c.status()==FtbFieldConversion.Status.MAPPED));
            assertEquals(result.book(),NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(result.book())).getAsJsonObject()));
        }
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

    @Test void richDescriptionsConvertPerLocaleWithFormatsDiagnosticsAndSourceRecovery() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.createDirectories(temporary.resolve("lang"));
        Files.writeString(temporary.resolve("data.snbt"),
                "{version:13,fallback_locale:'en_us'}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/text.snbt"),
                "{id:'1000000000000001',quests:[{id:'2000000000000001'}]}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/en_us.snbt"), """
                {quest.2000000000000001.title:"Text",quest.2000000000000001.quest_desc:[
                "literal * marker &cRed&r", "{@pagebreak}",
                "{\\\"text\\\":\\\"Docs\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"open_url\\\",\\\"value\\\":\\\"https://example.com\\\"}}"]}
                """, StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/zh_cn.snbt"),
                "{quest.2000000000000001.quest_desc:[\"中文 &n下划线&r\"]}", StandardCharsets.UTF_8);

        FtbImportResult result = new FtbV13Importer().importBook(temporary, "converted", "rich_text");
        var quest = result.book().quests().getFirst();
        var chinese = BookText.resolveQuestDescription(result.book(), quest, "zh_cn");

        assertFalse(result.report().hasErrors(), result.report().toJson());
        assertEquals(DocumentFormat.MARKDOWN_V1, quest.descriptionFormat());
        assertTrue(quest.description().contains("literal \\* marker"));
        assertTrue(quest.description().contains("brnquest:style/color/ff5555"));
        assertTrue(quest.description().contains("[Docs](<https://example.com>)"));
        assertEquals(DocumentFormat.MARKDOWN_V1, chinese.format());
        assertTrue(chinese.text().contains("brnquest:style/underline"));
        assertTrue(result.report().toJson().contains("BQF-TEXT-PAGEBREAK-FLATTENED"));
        assertTrue(result.book().extensions().keySet().stream()
                .anyMatch(key -> key.startsWith("ftb.rich_text_source.en_us.")));
        assertTrue(result.fieldConversions().stream().anyMatch(conversion ->
                conversion.sourceField().endsWith("quest_desc")
                        && conversion.targetField().endsWith("quest_desc_format")
                        && conversion.status() == FtbFieldConversion.Status.MAPPED));
        assertEquals(result.book(), NativeBookJson.decode(com.google.gson.JsonParser.parseString(
                NativeBookJson.encode(result.book())).getAsJsonObject()));
    }

    @Test void dangerousRichTextActionIsReportedAndNeverImportedAsAnAction() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.createDirectories(temporary.resolve("lang"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/text.snbt"),
                "{id:'1000000000000001',quests:[{id:'2000000000000001'}]}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/en_us.snbt"), """
                {quest.2000000000000001.quest_desc:[
                "{\\\"text\\\":\\\"Visible\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"run_command\\\",\\\"value\\\":\\\"/op @s\\\"}}"]}
                """, StandardCharsets.UTF_8);

        FtbImportResult result = new FtbV13Importer().importBook(temporary, "converted", "unsafe_text");

        assertTrue(result.report().hasErrors());
        assertTrue(result.report().toJson().contains("BQF-TEXT-UNSAFE-ACTION"));
        assertEquals("Visible", result.book().quests().getFirst().description());
        assertFalse(result.book().quests().getFirst().description().contains("/op"));
        assertTrue(result.fieldConversions().stream().anyMatch(conversion ->
                conversion.sourceField().endsWith("quest_desc")
                        && conversion.status() == FtbFieldConversion.Status.UNSUPPORTED));
    }

    @Test void changePageMapsOnlyKnownQuestIdsToStableRuntimeLinks() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.createDirectories(temporary.resolve("lang"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/text.snbt"), """
                {id:'1000000000000001',quests:[{id:'2000000000000001'},{id:'20000000000000AB'}]}
                """, StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("lang/en_us.snbt"), """
                {quest.2000000000000001.quest_desc:[
                "{\\\"text\\\":\\\"known\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"change_page\\\",\\\"value\\\":\\\"20000000000000AB/3\\\"}}",
                "{\\\"text\\\":\\\"missing\\\",\\\"clickEvent\\\":{\\\"action\\\":\\\"change_page\\\",\\\"value\\\":\\\"3000000000000001\\\"}}"]}
                """, StandardCharsets.UTF_8);

        FtbImportResult result = new FtbV13Importer().importBook(temporary, "converted", "quest_links");
        String description = result.book().quests().getFirst().description();

        assertTrue(description.contains("[known](brnquest:quest/converted:legacy/20000000000000ab)"), description);
        assertTrue(description.endsWith("missing"));
        assertTrue(result.report().toJson().contains("BQF-TEXT-SUBPAGE-FLATTENED"));
        assertTrue(result.report().toJson().contains("BQF-TEXT-CHANGE-PAGE"));
        assertFalse(description.contains("3000000000000001"));
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

    @Test void chapterMinWidthDefaultsPreserveExplicitZeroAndCreationTemplate() throws Exception {
        Files.createDirectories(temporary.resolve("chapters"));
        Files.writeString(temporary.resolve("data.snbt"), "{version:13,default_quest_shape:circle}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapter_groups.snbt"), "{}", StandardCharsets.UTF_8);
        Files.writeString(temporary.resolve("chapters/test.snbt"), """
                {id:"B000000000000001",default_min_width:4.0,default_repeatable_quest:true,quests:[
                  {id:"C000000000000001"}, {id:"C000000000000002",min_width:0.0}
                ]}
                """, StandardCharsets.UTF_8);
        var result = new FtbV13Importer().importBook(temporary, "converted", "defaults");
        assertEquals(4, result.book().quests().getFirst().appearance().minWidth());
        assertEquals(0, result.book().quests().getLast().appearance().minWidth());
        assertEquals("4.0", result.book().chapters().getFirst().questDefaults().values().get("min_width"));
        assertEquals("true", result.book().chapters().getFirst().questDefaults().values().get("repeatable"));
        assertEquals("circle", result.book().questDefaults().values().get("shape"));
    }

    private Path fixture() throws URISyntaxException {
        return Path.of(getClass().getResource("/fixtures/ftb_v13/eow/data.snbt").toURI()).getParent();
    }
}
