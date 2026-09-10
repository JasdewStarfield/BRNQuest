package yourscraft.jasdewstarfield.brnquest.reward.table;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
import static yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableJournal.*;

/** Nested preparation and journal recovery must remain effect-free until every reachable choice is decided. */
class RewardTablePlanTest {
    @TempDir Path directory;
    private final String id=UUID.randomUUID().toString();
    private Leaf leaf(JsonObject e,String path,String occurrence) {
        return new Leaf(path,id+"/"+occurrence,e.get("type").getAsString(),RewardTableTree.config(e),Map.of(),"1",LeafState.NOT_STARTED,0,0,1,"");
    }
    private static JsonObject leaf(String id) {
        var e=new JsonObject();e.addProperty("entry_id",id);e.addProperty("type","brnquest:custom");return e;
    }
    private static JsonObject table(String mode,JsonObject... entries) {
        var t=new JsonObject();t.addProperty("mode",mode);var a=new JsonArray();for(var e:entries)a.add(e);t.add("entries",a);return t;
    }
    private static JsonObject nested(String id,JsonObject table) {
        var e=leaf(id);e.addProperty("type","brnquest:reward_table");e.add("table",table);return e;
    }
    private Attempt prepare(JsonObject tree) {
        var leaves=new ArrayList<Leaf>();var snapshot=tree.deepCopy();
        snapshot.add(RewardTablePlan.FIELD,RewardTablePlan.create(RewardTableTree.parse(tree.toString()),b->BigInteger.ZERO,this::leaf,leaves));
        var attempt=new Attempt(2,"key",id,UUID.randomUUID().toString(),snapshot.toString(),"r",State.READY,leaves,1,"");
        return RewardTablePlan.pending(attempt)==null?attempt:attempt.state(State.AWAITING_CHOICE,"");
    }
    private Attempt choose(Attempt a,String entry) {
        var choice=RewardTablePlan.pending(a);
        return RewardTablePlan.select(a,new RewardTableChoice.Confirmation(id,choice.occurrence(),choice.version(),entry),b->BigInteger.ZERO,this::leaf);
    }
    @Test void multipleChoicesRetainDepthFirstOrderAcrossJournalRestart() throws Exception {
        var a=prepare(table("all",nested("first",table("choice",nested("pack",table("all",leaf("a"),leaf("b"))))),
                leaf("middle"),nested("last",table("choice",leaf("c")))));
        assertEquals(State.AWAITING_CHOICE,a.state()); assertEquals(List.of("root/middle"),a.leaves().stream().map(Leaf::path).toList());
        a=choose(a,"pack");assertEquals(State.AWAITING_CHOICE,a.state());
        var journal=new RewardTableJournal(directory);journal.begin(a);a=journal.read("key");
        a=choose(a,"c");journal.save(a);a=journal.read("key");
        assertEquals(State.READY,a.state());assertNull(RewardTablePlan.pending(a));
        assertEquals(List.of("root/first/pack/a","root/first/pack/b","root/middle","root/last/c"),a.leaves().stream().map(Leaf::path).toList());
        assertTrue(a.leaves().stream().allMatch(l->l.state()==LeafState.NOT_STARTED));
    }
    @Test void repeatedRandomSubtablesHaveIndependentChoicesAndStableRetries() {
        var tree=table("random",nested("bundle",table("choice",leaf("a"),leaf("b"))));tree.addProperty("rolls",2);
        var initial=prepare(tree);var first=RewardTablePlan.pending(initial);
        var request=new RewardTableChoice.Confirmation(id,first.occurrence(),0,"a");
        var a=choose(initial,"a"); assertNotEquals(first.occurrence(),RewardTablePlan.pending(a).occurrence());
        assertSame(a,RewardTablePlan.select(a,request,b->{throw new AssertionError("rerolled");},this::leaf));
        assertThrows(IllegalArgumentException.class,()->RewardTablePlan.validate(a,new RewardTableChoice.Confirmation(id,first.occurrence(),0,"b")));
        var done=choose(a,"b");assertEquals(2,done.leaves().size());
        assertEquals(2,done.leaves().stream().map(Leaf::occurrence).distinct().count());
    }
    @Test void choiceToRandomIsDrawnOnlyForSelectedBranchAndPersistsEmptyDraws() throws Exception {
        var random=table("random",leaf("a"));random.addProperty("empty_weight",5);
        var a=prepare(table("choice",nested("empty",random),leaf("other")));assertTrue(a.leaves().isEmpty());
        var done=choose(a,"empty");assertEquals(State.READY,done.state());assertTrue(done.leaves().isEmpty());
        assertTrue(done.snapshot().contains("\"empty\":true"));
        var journal=new RewardTableJournal(directory);journal.begin(done.state(State.SUCCEEDED,"empty receipt"));
        assertEquals(State.SUCCEEDED,journal.read("key").state());
    }
    @Test void unconfirmedOrReorderedEvidenceCannotBeLoaded() throws Exception {
        var a=prepare(table("all",leaf("a"),nested("choose",table("choice",leaf("b")))));
        var corrupt=a.leaf(0,a.leaves().getFirst().state(LeafState.SUCCEEDED,"unexpected effect"));
        var journal=new RewardTableJournal(directory);journal.begin(corrupt);
        assertThrows(java.io.IOException.class,()->journal.read("key"));
    }
    @Test void allContainingChoiceRequiresManualClaimAndExpansionIsBounded() {
        var config=table("all",nested("choice",table("choice",leaf("a"))));
        assertTrue(new RewardTableReward().requiresManualClaim(Map.of("table",config.toString())));
        var inner=table("random",leaf("a"));inner.addProperty("rolls",64);
        var outer=table("random",nested("inner",inner));outer.addProperty("rolls",64);
        assertThrows(IllegalArgumentException.class,()->RewardTableTree.parse(outer.toString()));
    }
    @Test void maximumLeafBudgetAndGuaranteedOnlyNestedTablesAreAccepted() throws Exception {
        var inner=table("random",leaf("a"));inner.addProperty("rolls",64);
        var outer=table("random",nested("inner",inner));outer.addProperty("rolls",16);
        var a=prepare(outer);assertEquals(1024,a.leaves().size());
        var journal=new RewardTableJournal(directory);journal.begin(a);assertEquals(a,journal.read("key"));
        var guaranteed=nested("gift",inner);guaranteed.addProperty("always",true);
        var only=table("random",guaranteed);only.addProperty("rolls",64);
        assertEquals(64,prepare(only).leaves().size(),"guaranteed subtree is not drawn 64 extra times");
    }
}
