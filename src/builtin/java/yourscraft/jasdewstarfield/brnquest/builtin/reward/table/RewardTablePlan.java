package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import com.google.gson.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import static yourscraft.jasdewstarfield.brnquest.builtin.reward.table.RewardTableJournal.*;

/** Frozen occurrence tree: unselected choice descendants are expanded only after an explicit decision. */
public final class RewardTablePlan {
    private RewardTablePlan() {}
    public static final String FIELD = "brnquest.plan";
    private static final int MAX_PLAN_NODES = 16384;
    public record Choice(String occurrence, int version, List<JsonObject> entries) {}
    @FunctionalInterface public interface Prepare { Leaf prepare(JsonObject entry, String path, String occurrence); }

    public static boolean present(Attempt attempt) { return JsonParser.parseString(attempt.snapshot()).getAsJsonObject().has(FIELD); }
    public static JsonObject create(RewardTableTree tree, Function<BigInteger,BigInteger> tickets, Prepare prepare, List<Leaf> leaves) {
        return expand(tree.document(), "root", "root", tickets, prepare, leaves, new int[]{0});
    }
    private static JsonObject expand(JsonObject table, String path, String occurrence,
            Function<BigInteger,BigInteger> tickets, Prepare prepare, List<Leaf> leaves, int[] nodes) {
        if (++nodes[0] > MAX_PLAN_NODES) throw new IllegalArgumentException("Expanded table node budget exceeded");
        var tree = RewardTableTree.parse(table.toString());
        var node = new JsonObject(); node.addProperty("path",path); node.addProperty("occurrence",occurrence);
        node.addProperty("mode",tree.mode());
        if (tree.mode().equals("choice")) {
            node.add("candidates",table.getAsJsonArray("entries").deepCopy());
            return node;
        }
        var draws = RewardTableDraws.select(tree,tickets);
        node.add("draws",RewardTableDraws.encode(draws));
        var children = new JsonArray(); node.add("children",children);
        for (var draw : draws) {
            if (draw.empty()) continue;
            var entry = tree.entries().stream().filter(e -> e.get("entry_id").getAsString().equals(draw.entryId())).findFirst().orElseThrow();
            children.add(entry(entry,path,occurrence + "/" + draw.entryId() + "/" + draw.ordinal(),tickets,prepare,leaves,nodes));
        }
        return node;
    }
    private static JsonObject entry(JsonObject entry, String parent, String occurrence,
            Function<BigInteger,BigInteger> tickets, Prepare prepare, List<Leaf> leaves, int[] nodes) {
        String path = parent + "/" + entry.get("entry_id").getAsString();
        if (entry.has("table")) return expand(entry.getAsJsonObject("table"),path,occurrence,tickets,prepare,leaves,nodes);
        if (++nodes[0] > MAX_PLAN_NODES || leaves.size() >= RewardTableTree.MAX_OCCURRENCES)
            throw new IllegalArgumentException("Expanded reward budget exceeded");
        var leaf = prepare.prepare(entry,path,occurrence); leaves.add(leaf);
        var node = new JsonObject(); node.addProperty("path",path); node.addProperty("occurrence",occurrence);
        node.addProperty("mode","leaf"); return node;
    }
    public static Choice pending(Attempt attempt) {
        var snapshot = JsonParser.parseString(attempt.snapshot()).getAsJsonObject();
        if (!snapshot.has(FIELD)) return attempt.state() == State.AWAITING_CHOICE
                ? new Choice(RewardTableChoice.PATH,0,RewardTableTree.parse(attempt.snapshot()).entries()) : null;
        var node = pending(snapshot.getAsJsonObject(FIELD));
        if (node == null) return null;
        var entries = new ArrayList<JsonObject>(); node.getAsJsonArray("candidates").forEach(e -> entries.add(e.getAsJsonObject().deepCopy()));
        return new Choice(node.get("occurrence").getAsString()+"/choice/0",0,List.copyOf(entries));
    }
    private static JsonObject pending(JsonObject node) {
        if (node.get("mode").getAsString().equals("choice") && !node.has("selected")) return node;
        if (node.has("children")) for (var child : node.getAsJsonArray("children")) {
            var found=pending(child.getAsJsonObject()); if(found!=null) return found;
        }
        return null;
    }
    private static JsonObject find(JsonObject node, String occurrence) {
        if ((node.get("occurrence").getAsString()+"/choice/0").equals(occurrence) && node.get("mode").getAsString().equals("choice")) return node;
        if(node.has("children")) for(var child:node.getAsJsonArray("children")) {
            var found=find(child.getAsJsonObject(),occurrence); if(found!=null)return found;
        }
        return null;
    }
    /** Validation is separate from expansion: network checks must never consume random draws. */
    public static void validate(Attempt current, RewardTableChoice.Confirmation request) {
        if(!present(current)) { RewardTableChoice.select(current,request); return; }
        if(!current.attemptId().equals(request.attempt()) || request.version()!=0) throw new IllegalArgumentException("Stale choice");
        var node=find(JsonParser.parseString(current.snapshot()).getAsJsonObject().getAsJsonObject(FIELD),request.occurrence());
        if(node==null) throw new IllegalArgumentException("Unknown choice occurrence");
        if(node.has("selected")) {
            if(!node.get("selected").getAsString().equals(request.entry())) throw new IllegalArgumentException("Conflicting choice retry");
            return;
        }
        var pending=pending(current);
        if(current.state()!=State.AWAITING_CHOICE || pending==null || !pending.occurrence().equals(request.occurrence())
                || pending.entries().stream().noneMatch(e->e.get("entry_id").getAsString().equals(request.entry()))
                || current.leaves().stream().anyMatch(l->l.state()!=LeafState.NOT_STARTED)) throw new IllegalArgumentException("Choice unavailable");
    }
    public static Attempt select(Attempt current, RewardTableChoice.Confirmation request,
            Function<BigInteger,BigInteger> tickets, Prepare prepare) {
        if(!present(current)) return RewardTableChoice.select(current,request);
        validate(current,request);
        var snapshot=JsonParser.parseString(current.snapshot()).getAsJsonObject();
        var plan=snapshot.getAsJsonObject(FIELD); var node=find(plan,request.occurrence());
        if(node.has("selected")) return current;
        JsonObject selected=null;
        for(var candidate:node.getAsJsonArray("candidates")) if(candidate.getAsJsonObject().get("entry_id").getAsString().equals(request.entry())) selected=candidate.getAsJsonObject();
        var leaves=new ArrayList<>(current.leaves()); var children=new JsonArray();
        children.add(entry(Objects.requireNonNull(selected),node.get("path").getAsString(),
                node.get("occurrence").getAsString()+"/"+request.entry()+"/0",tickets,prepare,leaves,new int[]{count(plan)}));
        node.addProperty("selected",request.entry()); node.add("children",children);
        // DFS order must be preserved when an earlier choice is expanded after a later all leaf was prepared.
        var ordered=new ArrayList<Leaf>(); var byOccurrence=new HashMap<String,Leaf>();
        leaves.forEach(l->byOccurrence.put(l.occurrence(),l));
        order(plan,current.attemptId(),byOccurrence,ordered);
        return new Attempt(2,current.key(),current.attemptId(),current.executor(),snapshot.toString(),current.resources(),
                pending(plan)==null?State.READY:State.AWAITING_CHOICE,ordered,System.currentTimeMillis(),"Choice frozen; all decisions precede effects");
    }
    private static int count(JsonObject node) {
        int total=1; if(node.has("children")) for(var child:node.getAsJsonArray("children")) total+=count(child.getAsJsonObject()); return total;
    }
    private static void order(JsonObject node,String attempt,Map<String,Leaf> leaves,List<Leaf> output) {
        if(node.get("mode").getAsString().equals("leaf")) {
            var leaf=leaves.get(attempt+"/"+node.get("occurrence").getAsString());
            if(leaf==null || !leaf.path().equals(node.get("path").getAsString())) throw new IllegalArgumentException("Plan leaf missing");
            output.add(leaf);
        }
        if(node.has("children")) for(var child:node.getAsJsonArray("children")) order(child.getAsJsonObject(),attempt,leaves,output);
    }
    /** Never allow an unconfirmed plan or mismatched execution list to become a successful receipt. */
    public static void verify(Attempt attempt) {
        var snapshot=JsonParser.parseString(attempt.snapshot()).getAsJsonObject();
        var plan=snapshot.getAsJsonObject(FIELD);
        if(plan==null || count(plan)>MAX_PLAN_NODES) throw new IllegalArgumentException("Invalid frozen plan");
        verifyNode(plan,snapshot,"root","root",new HashSet<>(),1);
        var byOccurrence=new HashMap<String,Leaf>();attempt.leaves().forEach(l->byOccurrence.put(l.occurrence(),l));
        var ordered=new ArrayList<Leaf>();order(plan,attempt.attemptId(),byOccurrence,ordered);
        if(!ordered.equals(attempt.leaves()))throw new IllegalArgumentException("Plan does not match execution order");
        for(var leaf:ordered) {
            var table=snapshot;JsonObject entry=null;String[] parts=leaf.path().split("/");
            for(int i=1;i<parts.length;i++) {
                entry=null;
                for(var candidate:table.getAsJsonArray("entries"))if(candidate.getAsJsonObject().get("entry_id").getAsString().equals(parts[i]))entry=candidate.getAsJsonObject();
                if(entry==null)throw new IllegalArgumentException("Leaf source missing");
                if(i+1<parts.length)table=entry.getAsJsonObject("table");
            }
            if(entry==null || !entry.get("type").getAsString().equals(leaf.type()) || !RewardTableTree.config(entry).equals(leaf.config()))
                throw new IllegalArgumentException("Leaf differs from frozen source");
        }
        if(pending(plan)!=null && (attempt.state()==State.SUCCEEDED || attempt.leaves().stream().anyMatch(l->l.state()!=LeafState.NOT_STARTED)))
            throw new IllegalArgumentException("Unconfirmed plan contains effects");
    }
    private static void verifyNode(JsonObject node,JsonObject table,String path,String occurrence,Set<String> seen,int depth) {
        if(depth>RewardTableTree.MAX_DEPTH || !seen.add(occurrence) || !node.get("path").getAsString().equals(path)
                || !node.get("occurrence").getAsString().equals(occurrence))throw new IllegalArgumentException("Invalid plan path");
        String mode=table.has("mode")?table.get("mode").getAsString():"all";
        if(!mode.equals(node.get("mode").getAsString()))throw new IllegalArgumentException("Plan mode changed");
        var children=node.has("children")?node.getAsJsonArray("children"):new JsonArray();
        var ids=new ArrayList<String>();var ordinals=new ArrayList<Integer>();
        if(mode.equals("choice")) {
            if(!node.get("candidates").equals(table.get("entries")))throw new IllegalArgumentException("Choice candidates changed");
            if(node.has("selected")) {ids.add(node.get("selected").getAsString());ordinals.add(0);}
        } else {
            for(var draw:node.getAsJsonArray("draws")) {
                var d=draw.getAsJsonObject();if(!d.get("empty").getAsBoolean()) {ids.add(d.get("entry_id").getAsString());ordinals.add(d.get("roll").getAsInt());}
            }
            if(mode.equals("all") && !node.get("draws").equals(RewardTableDraws.encode(RewardTableDraws.select(
                    RewardTableTree.parse(tableOnly(table).toString()),b->{throw new IllegalArgumentException("Unexpected draw");}))))
                throw new IllegalArgumentException("All plan lost or added entries");
        }
        if(children.size()!=ids.size())throw new IllegalArgumentException("Selected branch does not match children");
        for(int i=0;i<ids.size();i++) {
            String id=ids.get(i);JsonObject entry=null;
            for(var candidate:table.getAsJsonArray("entries"))if(candidate.getAsJsonObject().get("entry_id").getAsString().equals(id))entry=candidate.getAsJsonObject();
            if(entry==null)throw new IllegalArgumentException("Unknown frozen entry");
            var child=children.get(i).getAsJsonObject();String childPath=path+"/"+id,childOccurrence=occurrence+"/"+id+"/"+ordinals.get(i);
            if(entry.has("table"))verifyNode(child,entry.getAsJsonObject("table"),childPath,childOccurrence,seen,depth+1);
            else if(!child.get("mode").getAsString().equals("leaf") || child.has("children") || !seen.add(childOccurrence)
                    || !child.get("path").getAsString().equals(childPath) || !child.get("occurrence").getAsString().equals(childOccurrence))
                throw new IllegalArgumentException("Invalid frozen leaf path");
        }
    }
    private static JsonObject tableOnly(JsonObject table) {
        var copy=table.deepCopy();copy.remove(FIELD);copy.remove("brnquest.root_quest");return copy;
    }
}
