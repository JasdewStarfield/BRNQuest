package yourscraft.jasdewstarfield.brnquest.gametest;

import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import java.util.*;

/** Real-player effects in the isolated GameTest world; fixtures never modify a user world. */
@GameTestHolder("brnquest")
@SuppressWarnings("removal")
public final class RewardTableGameTests {
    private static JsonObject nested(String id,String mode,JsonObject... entries) {
        var e=entry(id,"reward_table",Map.of());
        var tree=JsonParser.parseString(reward("nested",entries).config().get("table")).getAsJsonObject();
        tree.addProperty("mode",mode);e.add("table",tree);return e;
    }
    @GameTest(template="empty",batch="rewardTableNestedChoices")
    @PrefixGameTestTemplate(false)
    public static void nestedChoicesFreezeBeforeAnyEffects(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel();var engine=ProgressEngine.get();
        var reward=reward("nested_choices",entry("before","xp",Map.of("xp","1")),
                nested("choose","choice",nested("pack","all",entry("a","xp",Map.of("xp","2")),entry("b","xp",Map.of("xp","3")))),
                nested("last","choice",entry("c","xp",Map.of("xp","4"))));
        var quest=install(reward);engine.forceComplete(player,quest.id());int before=player.totalExperience;
        engine.claim(player,reward.id());
        String key=RewardTableService.key(RewardTableService.choiceContext(player,reward.id()));
        var journal=RewardTableService.journal(player);var attempt=journal.read(key);var choice=RewardTablePlan.pending(attempt);
        RewardTableService.confirmChoice(player,reward.id(),new RewardTableChoice.Confirmation(attempt.attemptId(),choice.occurrence(),0,"pack"));
        helper.assertTrue(player.totalExperience==before,"even earlier all leaves wait for every choice");
        RewardTableService.clear();engine.claim(player,reward.id());attempt=journal.read(key);choice=RewardTablePlan.pending(attempt);
        var confirm=new RewardTableChoice.Confirmation(attempt.attemptId(),choice.occurrence(),0,"c");
        RewardTableService.confirmChoice(player,reward.id(),confirm);RewardTableService.confirmChoice(player,reward.id(),confirm);
        helper.assertTrue(player.totalExperience==before+10 && engine.visibleClaims(player).contains(reward.id().toString()),"nested pack delivered once in root receipt");
        helper.succeed();
    }
    @GameTest(template="empty",batch="rewardTableNestedRandom")
    @PrefixGameTestTemplate(false)
    public static void repeatedRandomChoicesAreIndependent(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel();var engine=ProgressEngine.get();
        var reward=randomReward("nested_random",2,nested("pick","choice",entry("a","xp",Map.of("xp","2")),entry("b","xp",Map.of("xp","7"))));
        var quest=install(reward);engine.forceComplete(player,quest.id());int before=player.totalExperience;engine.claim(player,reward.id());
        var journal=RewardTableService.journal(player);String key=RewardTableService.key(RewardTableService.choiceContext(player,reward.id()));
        String first="";
        for(String entry:List.of("a","b")) {
            var attempt=journal.read(key);var choice=RewardTablePlan.pending(attempt);
            helper.assertTrue(!choice.occurrence().equals(first),"each repeated subtable has a distinct choice identity");first=choice.occurrence();
            RewardTableService.confirmChoice(player,reward.id(),new RewardTableChoice.Confirmation(attempt.attemptId(),choice.occurrence(),0,entry));
            if(entry.equals("a"))helper.assertTrue(player.totalExperience==before,"first choice cannot start effects");
        }
        engine.claim(player,reward.id());helper.assertTrue(player.totalExperience==before+9,"separate selections delivered once");helper.succeed();
    }
    @GameTest(template = "empty", batch = "rewardTableChoice")
    @PrefixGameTestTemplate(false)
    public static void choiceSuspendsResumesFrozenAndNeverReplays(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        var reward=choiceReward("choice",entry("a","xp",Map.of("xp","2")),entry("b","xp",Map.of("xp","7")));
        var quest=install(reward);engine.forceComplete(player,quest.id());int before=player.totalExperience;
        var bulk=yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.claimAllRewardsResult(player,quest.id().toString());
        helper.assertTrue(bulk.code().equals("MANUAL_SELECTION_REQUIRED"),"bulk claim skips interactive rewards with an explanation");
        helper.assertTrue(engine.claim(player,reward.id()).code().equals("TABLE_AWAITING_CHOICE"),"manual claim opens preparation only");
        var context=RewardTableService.choiceContext(player,reward.id());String key=RewardTableService.key(context);
        var frozen=RewardTableService.journal(player).read(key);
        helper.assertTrue(player.totalExperience==before && frozen.state()==RewardTableJournal.State.AWAITING_CHOICE,"no choice means zero effects");
        // Simulate a previously blocked, still-unconfirmed record with matching current resources.
        RewardTableService.journal(player).save(frozen.state(RewardTableJournal.State.BLOCKED,"test resource review"));
        helper.assertTrue(!engine.claim(player,reward.id()).success() && player.totalExperience==before,"blocked choice cannot execute its candidates");
        RewardTableService.journal(player).save(frozen);
        RewardTableService.clear();
        install(choiceReward("choice",entry("a","xp",Map.of("xp","99")),entry("b","xp",Map.of("xp","99"))));
        engine.claim(player,reward.id());
        helper.assertTrue(RewardTableService.journal(player).read(key).equals(frozen),"reopen after author edit/restart preserves exact candidates");
        var confirmation=new RewardTableChoice.Confirmation(frozen.attemptId(),RewardTableChoice.PATH,0,"b");
        helper.assertTrue(RewardTableService.confirmChoice(player,reward.id(),confirmation).success(),"valid confirmation succeeds");
        RewardTableService.confirmChoice(player,reward.id(),confirmation);
        helper.assertTrue(player.totalExperience==before+7 && engine.visibleClaims(player).contains(reward.id().toString()),"only frozen selected XP is delivered once");
        boolean rejected=false;
        try { RewardTableService.confirmChoice(player,reward.id(),new RewardTableChoice.Confirmation(frozen.attemptId(),RewardTableChoice.PATH,0,"a")); }
        catch(IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected && player.totalExperience==before+7,"conflicting confirmation cannot change a completed decision");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "rewardTableChoiceReject")
    @PrefixGameTestTemplate(false)
    public static void choiceRejectsCrossPlayerResetAndInvalidCandidates(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel();var other=helper.makeMockServerPlayerInLevel();var engine=ProgressEngine.get();
        var reward=choiceReward("choice_reject",entry("a","xp",Map.of("xp","3")),entry("b","xp",Map.of("xp","8")));
        var quest=install(reward);engine.forceComplete(player,quest.id());engine.forceComplete(other,quest.id());
        engine.claim(player,reward.id());
        var attempt=RewardTableService.journal(player).read(RewardTableService.key(RewardTableService.choiceContext(player,reward.id())));
        var confirmation=new RewardTableChoice.Confirmation(attempt.attemptId(),RewardTableChoice.PATH,0,"a");
        boolean rejected=false;
        try { RewardTableService.confirmChoice(other,reward.id(),confirmation); } catch(IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected,"another player cannot submit this attempt");
        engine.reset(player,quest.id());engine.forceComplete(player,quest.id());rejected=false;
        try { RewardTableService.confirmChoice(player,reward.id(),confirmation); } catch(IllegalArgumentException expected) { rejected=true; }
        helper.assertTrue(rejected,"old-cycle confirmation cannot create a new attempt");
        var denied=choiceReward("choice_denied",entry("a","xp",Map.of("xp","3")),
                entry("bad","command",Map.of("command","experience add @s 2 points","source_mode","player")));
        var deniedQuest=install(denied);engine.forceComplete(player,deniedQuest.id());int before=player.totalExperience;
        helper.assertTrue(!engine.claim(player,denied.id()).success() && player.totalExperience==before,"all candidates must pass preflight before selection");
        helper.succeed();
    }
    private static RewardDefinition choiceReward(String path, JsonObject... entries) {
        var base=reward(path,entries);var tree=JsonParser.parseString(base.config().get("table")).getAsJsonObject();tree.addProperty("mode","choice");
        return new RewardDefinition(base.bookId(),base.id(),base.typeId(),Map.of("table",tree.toString()),"manual",false);
    }
    @GameTest(template = "empty", batch = "rewardTableRandomFrozen")
    @PrefixGameTestTemplate(false)
    public static void randomDecisionsSurviveRestartAndAuthorEdits(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var engine = ProgressEngine.get();
        var guaranteed = entry("gift", "xp", Map.of("xp", "5")); guaranteed.addProperty("always", true);
        var random = randomReward("random_frozen", 10, guaranteed, entry("pick", "xp", Map.of("xp", "2")));
        var quest = install(random); engine.forceComplete(player, quest.id());
        int before = player.totalExperience;
        engine.claim(player, random.id());
        helper.assertTrue(player.totalExperience == before + 19, "first budget includes gift once and seven draws");
        var progress = engine.progress(player);
        var context = new RewardClaimContext(new RewardContext(player, random.bookId(), quest.id(), yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(random)),
                yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService.require(player), progress.completionCycles(quest.id().toString()), progress.claimGeneration(quest.id().toString()));
        String key = RewardTableService.key(context);
        var frozen = RewardTableService.journal(player).read(key);
        helper.assertTrue(frozen.leaves().size() == 11 && frozen.leaves().stream().map(RewardTableJournal.Leaf::occurrence).distinct().count() == 11,
                "repeated random hits each own a durable occurrence");
        helper.assertTrue(JsonParser.parseString(frozen.snapshot()).getAsJsonObject().getAsJsonObject(RewardTablePlan.FIELD).getAsJsonArray("draws").size() == 11,
                "every decision is frozen before first effects");
        RewardTableService.clear(); // Discard only transient runtime state, as a restart would.
        install(randomReward("random_frozen", 1, entry("changed", "xp", Map.of("xp", "99"))));
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(engine.claim(player, random.id()).success(), "existing attempt resumes after author edit");
                engine.claim(player, random.id());
                helper.assertTrue(player.totalExperience == before + 25, "frozen ten draws execute once, never the edited 99 XP");
                helper.assertTrue(RewardTableService.journal(player).read(key).snapshot().equals(frozen.snapshot()), "resume cannot redraw");
                helper.assertTrue(engine.visibleClaims(player).contains(random.id().toString()), "normal claim receipt completes");
                helper.succeed();
            } catch (Exception error) { helper.fail(error.toString()); }
        });
    }

    @GameTest(template = "empty", batch = "rewardTableRandomEmpty")
    @PrefixGameTestTemplate(false)
    public static void randomEmptyClaimsAndAllCandidatePreflight(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var engine = ProgressEngine.get();
        var denied = randomReward("random_denied", 1, entry("xp", "xp", Map.of("xp", "5")),
                entry("denied", "command", Map.of("command", "experience add @s 1 points", "source_mode", "player")));
        var quest = install(denied); engine.forceComplete(player, quest.id());
        int before = player.totalExperience;
        helper.assertTrue(!engine.claim(player, denied.id()).success(), "all random candidates must pass preparation, not only a lucky pick");
        helper.assertTrue(player.totalExperience == before, "rejected pool has no earlier effects");
        var empty = randomReward("random_empty", 3);
        var tree = JsonParser.parseString(empty.config().get("table")).getAsJsonObject(); tree.addProperty("empty_weight", 1);
        empty = new RewardDefinition(empty.bookId(), empty.id(), empty.typeId(), Map.of("table", tree.toString()), "manual", false);
        quest = install(empty); engine.forceComplete(player, quest.id());
        helper.assertTrue(engine.claim(player, empty.id()).success(), "all-empty result is a successful consumed claim");
        var progress = engine.progress(player);
        var context = new RewardClaimContext(new RewardContext(player, empty.bookId(), quest.id(), yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(empty)),
                yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService.require(player), progress.completionCycles(quest.id().toString()), progress.claimGeneration(quest.id().toString()));
        var attempt = RewardTableService.journal(player).read(RewardTableService.key(context));
        helper.assertTrue(attempt.state() == RewardTableJournal.State.SUCCEEDED && attempt.leaves().isEmpty(), "empty attempts have no invented effect leaves");
        var decisions = JsonParser.parseString(attempt.snapshot()).getAsJsonObject().getAsJsonObject(RewardTablePlan.FIELD).getAsJsonArray("draws");
        helper.assertTrue(decisions.size() == 3 && decisions.asList().stream().allMatch(d -> d.getAsJsonObject().get("empty").getAsBoolean()), "empty rolls are durable decisions");
        engine.claim(player, empty.id());
        helper.assertTrue(player.totalExperience == before && engine.visibleClaims(player).contains(empty.id().toString()), "empty root consumes eligibility without effects");
        helper.succeed();
    }

    private static RewardDefinition randomReward(String path, int rolls, JsonObject... entries) {
        var base = reward(path, entries);
        var tree = JsonParser.parseString(base.config().get("table")).getAsJsonObject();
        tree.addProperty("mode", "random"); tree.addProperty("rolls", rolls);
        return new RewardDefinition(base.bookId(), base.id(), base.typeId(), Map.of("table", tree.toString()), "manual", false);
    }

    @GameTest(template = "empty", batch = "rewardTableA")
    @PrefixGameTestTemplate(false)
    public static void allPreflightDeliveryAndReceiptRecovery(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel();
        var engine = ProgressEngine.get();
        var invalid = reward("denied", entry("xp", "xp", Map.of("xp","7")),
                entry("command", "command", Map.of("command", "experience add @s 5 points", "source_mode", "player")));
        var quest = install(invalid); engine.forceComplete(player, quest.id());
        int before = player.totalExperience;
        helper.assertTrue(!engine.claim(player, invalid.id()).success(), "later denied command rejects whole preparation");
        helper.assertTrue(player.totalExperience == before, "preflight grants no earlier XP");
        var reward = reward("mixed", entry("item", "item", Map.of("item", "{id:'minecraft:bread',count:2}", "count", "2")),
                entry("xp", "xp", Map.of("xp","7")), entry("command", "command", Map.of("command", "experience add @s 5 points")),
                entry("custom", "custom", Map.of()));
        quest = install(reward); engine.forceComplete(player, quest.id());
        var oldData = QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
        var result = engine.claim(player, reward.id());
        helper.assertTrue(result.success(), result.code() + ": " + result.message());
        helper.assertTrue(player.totalExperience == before + 12, "XP and native command both delivered");
        helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == 4, "checked multiplier delivers four bread");
        player.server.overworld().getDataStorage().set("brnquest_progress", QuestProgressData.load(oldData, player.registryAccess()));
        helper.assertTrue(engine.claim(player, reward.id()).success(), "successful root supplements stale normal receipt");
        helper.assertTrue(player.totalExperience == before + 12, "root recovery does not replay any leaf");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "rewardTableFailure")
    @PrefixGameTestTemplate(false)
    public static void partialFailureStopsAndAcknowledgementContinues(GameTestHelper helper) throws Exception {
        var player = helper.makeMockServerPlayerInLevel(); var engine = ProgressEngine.get();
        var effects = new java.util.concurrent.atomic.AtomicInteger();
        player.server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("brnquest_f5_failure")
                .executes(c -> { effects.incrementAndGet(); throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("effect then fail")).create(); }));
        var reward = reward("failure", nested("package","all",entry("first", "xp", Map.of("xp","2")), entry("bad", "command", Map.of("command","brnquest_f5_failure")), entry("last","xp",Map.of("xp","3"))));
        var quest = install(reward); engine.forceComplete(player, quest.id());
        int before = player.totalExperience;
        helper.assertTrue(!engine.claim(player, reward.id()).success(), "failure stops the root");
        helper.assertTrue(player.totalExperience == before + 2 && effects.get() == 1, "later leaf not executed");
        engine.claim(player, reward.id());
        helper.assertTrue(effects.get() == 1, "review state never repeats uncertain command");
        var progress = engine.progress(player);
        var context = new RewardClaimContext(new RewardContext(player, reward.bookId(), quest.id(), yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(reward)),
                yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService.require(player), progress.completionCycles(quest.id().toString()), progress.claimGeneration(quest.id().toString()));
        var journal = RewardTableService.journal(player); String key = RewardTableService.key(context); var attempt = journal.read(key);
        journal.acknowledge(key, attempt.attemptId(), attempt.leaves().get(1).occurrence(), "GameTest admin");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(engine.claim(player, reward.id()).success(), "acknowledgement continues remaining leaves");
            helper.assertTrue(player.totalExperience == before + 5 && effects.get() == 1, "only unstarted leaf delivered");
            helper.succeed();
        });
    }
    private static JsonObject entry(String id, String type, Map<String,String> config) {
        JsonObject entry = new JsonObject(), values = new JsonObject(); config.forEach(values::addProperty);
        entry.addProperty("entry_id",id); entry.addProperty("type","brnquest:" + type); entry.add("config",values); return entry;
    }
    @GameTest(template = "empty", batch = "rewardTableBounded")
    @PrefixGameTestTemplate(false)
    public static void batchBudgetAndQueuedCommand(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel(); var engine = ProgressEngine.get();
        JsonObject[] leaves = new JsonObject[10];
        for (int i=0;i<10;i++) leaves[i]=entry("e"+i,"xp",Map.of("xp","1"));
        var reward=reward("bounded",leaves); var quest=install(reward); engine.forceComplete(player,quest.id());
        int before=player.totalExperience;
        engine.claim(player,reward.id()); engine.claim(player,reward.id());
        helper.assertTrue(player.totalExperience==before+8,"repeat clicks cannot exceed eight leaves in one tick");
        helper.runAfterDelay(2,() -> {
            RewardTableService.tick(player);
            helper.assertTrue(player.totalExperience==before+10,"later batch completes without replay");
            helper.assertTrue(engine.visibleClaims(player).contains(reward.id().toString()),
                    "batch continuation commits the authoritative claimed set used by the client badge");
            var queued=reward("queued",entry("command","command",Map.of("command","experience add @s 2 points")),entry("after","xp",Map.of("xp","3")));
            var queuedQuest=install(queued); engine.forceComplete(player,queuedQuest.id());
            player.server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("brnquest_f5_queue")
                    .executes(c -> { engine.claim(player,queued.id()); return 1; }));
            player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(),"brnquest_f5_queue");
            helper.runAfterDelay(2,() -> {
                RewardTableService.tick(player);
                helper.assertTrue(player.totalExperience==before+15,"queued command and later XP execute once in order");
                helper.assertTrue(engine.visibleClaims(player).contains(queued.id().toString()),
                        "deferred command continuation resolves the root receipt without another click");
                helper.succeed();
            });
        });
    }
    @GameTest(template = "empty", batch = "rewardTableReset")
    @PrefixGameTestTemplate(false)
    public static void resetInsideLeafStopsOldCycle(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        var reward=reward("reset_inside",entry("reset","command",Map.of("command","brnquest_f5_reset")),entry("after","xp",Map.of("xp","9")));
        var quest=install(reward); engine.forceComplete(player,quest.id());
        player.server.getCommands().getDispatcher().register(net.minecraft.commands.Commands.literal("brnquest_f5_reset")
                .executes(c -> { engine.reset(player,quest.id()); return 1; }));
        int before=player.totalExperience;
        helper.assertTrue(!engine.claim(player,reward.id()).success(),"reset invalidates the executing root");
        helper.assertTrue(player.totalExperience==before,"old continuation cannot grant into new cycle");
        helper.assertTrue(!engine.progress(player).isClaimed(reward.id().toString()),"old root cannot write the normal receipt");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "rewardTableItemBudget")
    @PrefixGameTestTemplate(false)
    public static void inventoryOverflowAdvancementAndAddon(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        for(int slot=0;slot<36;slot++) player.getInventory().setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE,64));
        var addon=entry("addon","custom",Map.of());
        if(net.neoforged.fml.ModList.get().isLoaded("brnquest_example")) {
            var id=RewardTypeRegistry.registeredIds().stream().filter(type -> type.getNamespace().equals("brnquest_example") && RewardTypeRegistry.get(type).composition().isPresent()).findFirst().orElseThrow();
            addon.addProperty("type",id.toString()); JsonObject config=new JsonObject();config.addProperty("tag","f5_addon_checked");addon.add("config",config);
        }
        var reward=reward("overflow",entry("bread","item",Map.of("item","{id:'minecraft:bread',count:2}")),
                entry("advancement","advancement",Map.of("advancement","minecraft:story/root")),addon);
        var quest=install(reward);engine.forceComplete(player,quest.id());
        var result=engine.claim(player,reward.id());helper.assertTrue(result.success(),result.message());
        helper.assertTrue(player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(4))
                .stream().anyMatch(entity -> entity.getItem().is(net.minecraft.world.item.Items.BREAD) && entity.getItem().getCount()==2),"overflow drops the actual item remainder");
        var holder=player.server.getAdvancements().get(ResourceLocation.parse("minecraft:story/root"));
        helper.assertTrue(holder!=null && player.getAdvancements().getOrStartProgress(holder).isDone(),"native advancement criteria granted");
        if(net.neoforged.fml.ModList.get().isLoaded("brnquest_example")) helper.assertTrue(player.getTags().contains("f5_addon_checked"),"public-only addon leaf executed");
        helper.succeed();
    }
    private static RewardDefinition reward(String path, JsonObject... entries) {
        JsonObject tree = new JsonObject(); tree.addProperty("version",1); tree.addProperty("mode","all");
        JsonArray list = new JsonArray(); Arrays.stream(entries).forEach(list::add); tree.add("entries",list);
        return new RewardDefinition(id("book"),id(path),RewardTableReward.ID,Map.of("table",tree.toString()),"manual",false);
    }
    private static QuestDefinition install(RewardDefinition reward) {
        var quest = new QuestDefinition(id("book"),id("quest_"+reward.id().getPath()),id("chapter"),"Table","","","",0,0,List.of(),List.of(),List.of(reward),"");
        var book = new QuestBookDefinition(id("book"),1,"Table",List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),
                List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,List.of(quest))),Map.of());
        if (!QuestBookManager.get().install(book,new DiagnosticReport())) throw new IllegalStateException("Table fixture rejected");
        return quest;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("brnquest_f5:" + path); }
    @GameTest(template="empty",batch="rewardTableTeam")
    @PrefixGameTestTemplate(false)
    @SuppressWarnings("unchecked")
    public static void sharedRootBindsFirstExecutor(GameTestHelper helper) throws Exception {
        var a=helper.makeMockServerPlayerInLevel();var b=helper.makeMockServerPlayerInLevel();
        var members=Set.of(a.getUUID(),b.getUUID()); var providerId=ResourceLocation.parse("brnquest:openpac");
        var owner=new yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId(providerId,UUID.randomUUID());
        // Replace the optional provider only inside this isolated batch, then restore its exact prior value.
        var field=yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviderRegistry.class.getDeclaredField("PROVIDERS");field.setAccessible(true);
        var registry=(Map<ResourceLocation,yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProvider>)field.get(null);
        var previous=registry.get(providerId);
        registry.put(providerId,new yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProvider() {
            public ResourceLocation id(){return providerId;}
            public Optional<yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId> resolve(net.minecraft.server.level.ServerPlayer player){return members.contains(player.getUUID())?Optional.of(owner):Optional.empty();}
            public Set<UUID> members(net.minecraft.server.MinecraftServer server,yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId id){return members;}
            public yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle lifecycle(net.minecraft.server.MinecraftServer server,yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId id){return yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle.ACTIVE;}
            public Optional<yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerArchive> archivedSnapshot(net.minecraft.server.MinecraftServer server,yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId id){return Optional.empty();}
        });
        Runnable restore=() -> { if(previous==null)registry.remove(providerId);else registry.put(providerId,previous); };
        try {
            JsonObject[] leaves=new JsonObject[10];for(int i=0;i<10;i++)leaves[i]=entry("e"+i,"xp",Map.of("xp","1"));
            var initial=reward("shared",leaves);
            var reward=new RewardDefinition(initial.bookId(),initial.id(),initial.typeId(),initial.config(),"manual",true);
            var quest=install(reward);var engine=ProgressEngine.get();engine.reconcile(a);engine.reconcile(b);engine.forceComplete(a,quest.id());
            int beforeA=a.totalExperience,beforeB=b.totalExperience;
            var first=engine.claim(a,reward.id());
            helper.assertTrue(first.code().equals("TABLE_EXECUTING"),"first shared batch: " + first.code() + ": " + first.message());
            var other=engine.claim(b,reward.id());
            helper.assertTrue(other.code().equals("TABLE_BOUND_EXECUTOR"),"second team member cannot replace the bound executor");
            helper.assertTrue(a.totalExperience==beforeA+8 && b.totalExperience==beforeB,"shared table effects: a="+(a.totalExperience-beforeA)+" b="+(b.totalExperience-beforeB));
            helper.runAfterDelay(2,() -> {
                try {
                    engine.claim(a,reward.id());engine.claim(b,reward.id());
                    helper.assertTrue(a.totalExperience==beforeA+10 && b.totalExperience==beforeB,"one shared root consumes exactly one execution list");
                    helper.succeed();
                } finally { restore.run(); }
            });
        } catch(RuntimeException error) { restore.run();throw error; }
    }
}
