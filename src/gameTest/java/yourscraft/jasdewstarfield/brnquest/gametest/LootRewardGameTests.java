package yourscraft.jasdewstarfield.brnquest.gametest;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.google.gson.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import java.util.*;

/** Native loot fixtures live solely in the isolated GameTest datapack. Each batch owns its active book. */
@GameTestHolder("brnquest")
@SuppressWarnings("removal")
public final class LootRewardGameTests {
    @GameTest(template="empty",batch="lootStandalone") @PrefixGameTestTemplate(false)
    public static void standaloneFreezesComponentsAndNeverReplays(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel(); var reward=loot("standalone","brnquest_f6:items");
        var quest=install(reward); var engine=ProgressEngine.get(); engine.forceComplete(player,quest.id());
        var result=engine.claim(player,reward.id()); helper.assertTrue(result.success(),result.message());
        helper.assertTrue(diamonds(player)==3,"native items delivered");
        var stack=player.getInventory().items.stream().filter(item->item.is(Items.DIAMOND)).findFirst().orElseThrow();
        helper.assertTrue(stack.getHoverName().getString().equals("Frozen diamond"),"components preserved");
        var context=context(player,quest,reward); var journal=RewardTableService.journal(player); var attempt=journal.read(RewardTableService.key(context));
        helper.assertTrue(attempt.state()==RewardTableJournal.State.SUCCEEDED && attempt.leaves().getFirst().prepared().containsKey("brnquest.loot_items"),"forced native snapshot retained");
        RewardTableService.clear(); engine.claim(player,reward.id());
        helper.assertTrue(diamonds(player)==3,"reopen cannot reroll or duplicate"); helper.succeed();
    }
    @GameTest(template="empty",batch="lootEmpty") @PrefixGameTestTemplate(false)
    public static void emptyIsConsumedAndInvalidTablesHaveNoEffects(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        for(String table:List.of("brnquest_f6:missing","brnquest_f6:wrong_context","brnquest_f6:oversized")) {
            var reward=loot(table.substring(table.indexOf(':')+1),table);var quest=install(reward);engine.forceComplete(player,quest.id());
            helper.assertTrue(!engine.claim(player,reward.id()).success() && !engine.visibleClaims(player).contains(reward.id().toString()),"invalid loot rejected before receipt: "+table);
            helper.assertTrue(diamonds(player)==0,"invalid loot grants nothing");
        }
        var empty=loot("empty","brnquest_f6:empty");var quest=install(empty);engine.forceComplete(player,quest.id());
        helper.assertTrue(engine.claim(player,empty.id()).success() && engine.visibleClaims(player).contains(empty.id().toString()),"empty draw consumes claim");
        helper.succeed();
    }
    @GameTest(template="empty",batch="lootChoice") @PrefixGameTestTemplate(false)
    public static void choiceRecoveryRetainsFrozenLootBeforeAnyEffects(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel(); var engine=ProgressEngine.get();
        var choice=entry("choose","reward_table",Map.of()); choice.add("table",tree("choice",entry("xp","xp",Map.of("xp","2"))));
        var reward=tableReward("choice",tree("all",entry("loot","loot_table",Map.of("loot_table","brnquest_f6:items")),choice));
        var quest=install(reward);engine.forceComplete(player,quest.id());engine.claim(player,reward.id());
        var journal=RewardTableService.journal(player);String key=RewardTableService.key(context(player,quest,reward));var before=journal.read(key);
        helper.assertTrue(diamonds(player)==0 && before.leaves().getFirst().prepared().containsKey("brnquest.loot_items"),"loot frozen while every choice waits");
        RewardTableService.clear();
        var changed=tableReward("choice",tree("all",entry("loot","loot_table",Map.of("loot_table","brnquest_f6:empty")),choice));install(changed);
        engine.claim(player,reward.id());helper.assertTrue(journal.read(key).equals(before),"author edits and service restart cannot replace frozen draw");
        var pending=RewardTablePlan.pending(before);
        RewardTableService.confirmChoice(player,reward.id(),new RewardTableChoice.Confirmation(before.attemptId(),pending.occurrence(),pending.version(),"xp"));
        helper.assertTrue(diamonds(player)==3,"original frozen items delivered");helper.succeed();
    }
    @GameTest(template="empty",batch="lootRandom") @PrefixGameTestTemplate(false)
    public static void randomAndChoiceCanSelectLootLeaves(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel();var engine=ProgressEngine.get();
        var tree=tree("random",entry("loot","loot_table",Map.of("loot_table","brnquest_f6:items")));tree.addProperty("rolls",2);tree.addProperty("replacement",true);
        var reward=tableReward("random",tree);var quest=install(reward);engine.forceComplete(player,quest.id());
        helper.assertTrue(engine.claim(player,reward.id()).success() && diamonds(player)==6,"each repeated occurrence generates and delivers separately");
        var choice=tableReward("selected",tree("choice",entry("loot","loot_table",Map.of("loot_table","brnquest_f6:items"))));
        quest=install(choice);engine.forceComplete(player,quest.id());engine.claim(player,choice.id());
        var attempt=RewardTableService.journal(player).read(RewardTableService.key(context(player,quest,choice)));
        helper.assertTrue(attempt.leaves().isEmpty(),"unselected choice has not generated a loot leaf");
        var pending=RewardTablePlan.pending(attempt);
        RewardTableService.confirmChoice(player,choice.id(),new RewardTableChoice.Confirmation(attempt.attemptId(),pending.occurrence(),pending.version(),"loot"));
        helper.assertTrue(diamonds(player)==9,"selected loot generated and delivered");helper.succeed();
    }
    @GameTest(template="empty",batch="lootOverflow") @PrefixGameTestTemplate(false)
    public static void overflowAndUncertainDeliveryRespectExistingBoundary(GameTestHelper helper) throws Exception {
        var player=helper.makeMockServerPlayerInLevel();for(int i=0;i<36;i++)player.getInventory().setItem(i,new net.minecraft.world.item.ItemStack(Items.COBBLESTONE,64));
        var reward=loot("overflow","brnquest_f6:items");var quest=install(reward);var engine=ProgressEngine.get();engine.forceComplete(player,quest.id());
        helper.assertTrue(engine.claim(player,reward.id()).success(),"overflow claim completes");
        helper.assertTrue(player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(4)).stream()
                .anyMatch(entity->entity.getItem().is(Items.DIAMOND) && entity.getItem().getCount()==3),"overflow retains actual frozen stack");
        // A persisted STARTED leaf without a success receipt cannot be replayed after interruption.
        var context=context(player,quest,reward);var journal=RewardTableService.journal(player);var attempt=journal.read(RewardTableService.key(context));
        var leaf=attempt.leaves().getFirst();
        var recovery=new LootTableReward().recover(new RewardLeafContext(context,leaf.path(),leaf.occurrence(),LootTableReward.ID,leaf.config()),leaf.prepared());
        helper.assertTrue(recovery.code().equals("UNKNOWN"),"uncertain loot delivery requires review, not reroll");helper.succeed();
    }
    private static int diamonds(ServerPlayer player){return player.getInventory().items.stream().filter(item->item.is(Items.DIAMOND)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();}
    private static ResourceLocation id(String path){return ResourceLocation.parse("brnquest_f6:"+path);}
    private static RewardDefinition loot(String path,String table){return new RewardDefinition(id("book"),id(path),LootTableReward.ID,Map.of("loot_table",table),"manual",false);}
    private static JsonObject entry(String id,String type,Map<String,String> values){var e=new JsonObject();e.addProperty("entry_id",id);e.addProperty("type","brnquest:"+type);var c=new JsonObject();values.forEach(c::addProperty);e.add("config",c);return e;}
    private static JsonObject tree(String mode,JsonObject... entries){var t=new JsonObject();t.addProperty("version",1);t.addProperty("mode",mode);var list=new JsonArray();Arrays.stream(entries).forEach(list::add);t.add("entries",list);return t;}
    private static RewardDefinition tableReward(String path,JsonObject tree){return new RewardDefinition(id("book"),id(path),RewardTableReward.ID,Map.of("table",tree.toString()),"manual",false);}
    private static RewardClaimContext context(ServerPlayer player,QuestDefinition quest,RewardDefinition reward){var progress=ProgressEngine.get().progress(player);return new RewardClaimContext(new RewardContext(player,quest.bookId(),quest.id(),yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(reward)),ProgressOwnerService.require(player),progress.completionCycles(quest.id().toString()),progress.claimGeneration(quest.id().toString()));}
    private static QuestDefinition install(RewardDefinition reward){
        var quest=new QuestDefinition(id("book"),id("quest_"+reward.id().getPath()),id("chapter"),"Loot","","","",0,0,List.of(),List.of(),List.of(reward),"");
        var book=new QuestBookDefinition(id("book"),1,"Loot",List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,List.of(quest))),Map.of());
        if(!QuestBookManager.get().install(book,new DiagnosticReport()))throw new IllegalStateException("Loot fixture rejected");return quest;
    }
}
