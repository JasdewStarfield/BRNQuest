package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableJournal.*;

/** Choice identity and restart tests do not assume network input or a default first option is trusted. */
class RewardTableChoiceTest {
    @TempDir Path directory;
    private Attempt awaiting() {
        var leaves = List.of("a","b").stream().map(id -> new Leaf("root/"+id,"attempt/root/"+id+"/0","brnquest:xp",
                Map.of("xp","3"),Map.of("xp","3"),"1",LeafState.NOT_STARTED,0,0,1,"prepared")).toList();
        return new Attempt(1,"key",UUID.randomUUID().toString(),UUID.randomUUID().toString(),"{\"mode\":\"choice\"}",
                "resources",State.AWAITING_CHOICE,leaves,1,"choose");
    }
    private RewardTableChoice.Confirmation request(Attempt a, String entry) {
        return new RewardTableChoice.Confirmation(a.attemptId(),RewardTableChoice.PATH,0,entry);
    }
    @Test void restartRetainsPendingAndConfirmedDecisions() throws Exception {
        var original=awaiting(); var journal=new RewardTableJournal(directory); journal.begin(original);
        var restored=new RewardTableJournal(directory).read(original.key());
        assertEquals(State.AWAITING_CHOICE,restored.state());
        assertTrue(restored.leaves().stream().allMatch(l->l.state()==LeafState.NOT_STARTED));
        var confirmation=request(original,"b"); var selected=RewardTableChoice.select(restored,confirmation);
        journal.save(selected);
        var frozen=new RewardTableJournal(directory).read(original.key());
        assertEquals(List.of("root/b"),frozen.leaves().stream().map(Leaf::path).toList());
        assertSame(frozen,RewardTableChoice.select(frozen,confirmation));
        assertThrows(IllegalArgumentException.class,()->RewardTableChoice.select(frozen,request(original,"a")));
    }
    @Test void forgedVersionsPathsAndEntriesCannotChangeTheAttempt() {
        var original=awaiting();
        for(var request:List.of(request(original,"missing"),new RewardTableChoice.Confirmation("other",RewardTableChoice.PATH,0,"a"),
                new RewardTableChoice.Confirmation(original.attemptId(),"root/other/0",0,"a"),
                new RewardTableChoice.Confirmation(original.attemptId(),RewardTableChoice.PATH,1,"a")))
            assertThrows(IllegalArgumentException.class,()->RewardTableChoice.select(original,request));
        assertEquals(State.AWAITING_CHOICE,original.state());
        assertEquals(2,original.leaves().size());
    }
    @Test void choiceCannotFallThroughToRandomOrAutomaticPolicy() {
        var config=Map.of("table","{\"mode\":\"choice\",\"entries\":[{\"entry_id\":\"a\",\"type\":\"brnquest:xp\",\"config\":{\"xp\":\"2\"}}]}");
        var tree=RewardTableTree.parse(config.get("table"));
        assertThrows(IllegalArgumentException.class,()->RewardTableDraws.select(tree,bound->java.math.BigInteger.ZERO));
        var type=new RewardTableReward(); assertTrue(type.requiresManualClaim(config));
        var id=net.minecraft.resources.ResourceLocation.parse("test:choice");
        var view=new yourscraft.jasdewstarfield.brnquest.api.RewardView(id,id,RewardTableReward.ID,config,"auto_visible",false);
        assertTrue(yourscraft.jasdewstarfield.brnquest.reward.RewardTypeExecutor.configError(type,view).orElseThrow().contains("manual"));
    }
    @Test void selectedSnapshotCannotRetainMultipleExecutableCandidates() throws Exception {
        var original=awaiting();
        var corrupt=new Attempt(1,original.key(),original.attemptId(),original.executor(),
                "{\"mode\":\"choice\",\"brnquest.selected\":\"a\"}",original.resources(),State.READY,original.leaves(),1,"malformed selection");
        var journal=new RewardTableJournal(directory);journal.begin(corrupt);
        assertThrows(java.io.IOException.class,()->journal.read(corrupt.key()));
    }
}
