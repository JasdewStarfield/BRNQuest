package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import yourscraft.jasdewstarfield.brnquest.api.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.*;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import java.util.*;

/** Deterministic loaded-room fixtures validate server rays, real death events and existing owner ledgers. */
@GameTestHolder("brnquest")
@SuppressWarnings("removal")
public final class EncounterGameTests {
    @GameTest(template="empty",batch="encounterRay") @PrefixGameTestTemplate(false)
    public static void rayHonorsNativeTagsOcclusionDistanceAndLoadedChunks(GameTestHelper h) {
        var player=room(h); var block=observe("block","#minecraft:logs",8,0);
        h.assertTrue(ObservationRay.hit(player,block)!=null,"native block tag matches server ray");
        var zombie=mob(h,EntityType.ZOMBIE,4);
        h.assertTrue(ObservationRay.hit(player,block)==null,"nearer entity obscures block");
        var entity=observe("entity","#brnquest_f4:targets",8,0);
        h.assertTrue(ObservationRay.hit(player,entity)!=null,"native entity tag matches nearest entity");
        h.assertTrue(ObservationRay.hit(player,observe("entity","minecraft:pig",8,0))==null,"wrong entity type cannot match");
        h.assertTrue(ObservationRay.hit(player,observe("entity","minecraft:zombie",2,0))==null,"out of range entity is rejected");
        h.setBlock(new BlockPos(2,3,3),Blocks.STONE);
        h.assertTrue(ObservationRay.hit(player,entity)==null,"solid block prevents seeing the entity");
        h.setBlock(new BlockPos(2,3,3),Blocks.AIR); player.setYRot(180);
        h.assertTrue(ObservationRay.hit(player,entity)==null,"looking away misses");
        var original=player.position();
        zombie.discard(); player.setPos(30000000,120,30000000); player.setYRot(0);
        try {
            // Moving a tracked player loads its own chunk; only the following ray must be load-free.
            int loaded=player.serverLevel().getChunkSource().getLoadedChunksCount();
            h.assertTrue(ObservationRay.hit(player,observe("block","#minecraft:logs",64,0))==null,"unloaded ray is rejected");
            h.assertTrue(player.serverLevel().getChunkSource().getLoadedChunksCount()==loaded,"ray did not load another chunk");
            h.assertTrue(player.serverLevel().getChunkSource().getChunkNow(30000000>>4,(30000000>>4)+3)==null,"unloaded ray endpoint remains unloaded");
        } finally { player.setPos(original); }
        h.succeed();
    }
    @GameTest(template="empty",batch="encounterTimer",timeoutTicks=100) @PrefixGameTestTemplate(false)
    public static void transientGazeResetsAndOnlyCompletedObservationPersists(GameTestHelper h) {
        var player=room(h); var task=task("gaze",true,Map.of("target","#minecraft:logs","duration","100"));
        install(List.of(quest("gaze",List.of(task),false,List.of())));
        var engine=ProgressEngine.get(); engine.reconcile(player); engine.pollTasks(player);
        var context=new TaskContext(player,id("book"),id("gaze"),ApiViews.task(task),0);
        h.assertTrue(ObservationRuntime.ticks(context)==1,"first hit counts one actual tick");
        engine.pollTasks(player); h.assertTrue(ObservationRuntime.ticks(context)==1,"same tick cannot be counted twice");
        engine.reset(player,id("gaze"));
        h.assertTrue(ObservationRuntime.ticks(context)==0,"manual reset clears partial gaze even with zero saved progress");
        engine.pollTasks(player);
        player.setYRot(180); engine.pollTasks(player); h.assertTrue(ObservationRuntime.ticks(context)==0,"looking away clears partial gaze");
        player.setYRot(0); engine.pollTasks(player); ObservationRuntime.clear(player);
        h.assertTrue(ObservationRuntime.ticks(context)==0,"logout clears transient state");
        h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==0,"partial gaze is never saved as permanent progress");
        // A new published configuration starts a new timer; the registered sampler then completes it normally.
        var instant=task("gaze",true,Map.of("target","#minecraft:logs","duration","3"));
        install(List.of(quest("gaze",List.of(instant),false,List.of()))); engine.reconcile(player);
        h.succeedWhen(()->{
            engine.pollTasks(player);
            h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"continuous server ticks latch completion");
            player.setYRot(180); engine.pollTasks(player);
            h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"completion never rolls back on look-away");
            engine.reset(player,id("gaze")); engine.pollTasks(player);
            h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==0,"reset requires a fresh matching gaze");
        });
    }
    @GameTest(template="empty",batch="encounterKills") @PrefixGameTestTemplate(false)
    public static void killsAttributeDirectAndProjectileDeathsOnceAndPersist(GameTestHelper h) {
        var player=room(h); var other=h.makeMockServerPlayerInLevel();
        var task=task("kills",false,Map.of("target","#brnquest_f4:targets","count","3"));
        install(List.of(quest("kills",List.of(task),false,List.of()))); var engine=ProgressEngine.get();engine.reconcile(player);engine.reconcile(other);
        var zombie=mob(h,EntityType.ZOMBIE,4); var source=player.damageSources().playerAttack(player);
        zombie.hurt(source,1000); EncounterEvents.death(new LivingDeathEvent(zombie,source));
        h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1,"duplicate death does not count twice");
        h.assertTrue(engine.progress(other).taskProgress(task.id().toString())==0,"personal kills belong only to killer");
        var arrow=new Arrow(EntityType.ARROW,h.getLevel()); arrow.setOwner(player);
        var skeleton=mob(h,EntityType.SKELETON,5); skeleton.hurt(player.damageSources().arrow(arrow,player),1000);
        h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==2,"owned projectile counts");
        var environment=mob(h,EntityType.ZOMBIE,7); environment.hurt(player.damageSources().generic(),1000);
        h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==2,"environment damage does not borrow last player credit");
        var pet=mob(h,EntityType.WOLF,7);
        h.assertTrue(EncounterEvents.killer(player.damageSources().mobAttack(pet))==null,"pet damage is not player damage");
        var canceled=new LivingDeathEvent(pet,source);canceled.setCanceled(true);EncounterEvents.death(canceled);
        h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==2,"canceled deaths do not count");
        var saved=QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(),player.registryAccess());
        player.server.overworld().getDataStorage().set("brnquest_progress",QuestProgressData.load(saved,player.registryAccess()));
        h.assertTrue(engine.progress(player).taskProgress(task.id().toString())==2,"SavedData restores partial kill count");
        mob(h,EntityType.ZOMBIE,6).hurt(source,1000);
        h.assertTrue(engine.progress(player).status(id("kills").toString())==QuestStatus.COMPLETED,"required count completes quest via normal eligibility");
        h.succeed();
    }
    @GameTest(template="empty",batch="encounterSequence") @PrefixGameTestTemplate(false)
    public static void oneDeathCannotAdvanceFutureSequentialOrLockedTasks(GameTestHelper h) {
        var player=room(h);var first=task("first",false,Map.of("target","minecraft:zombie"));var second=task("second",false,Map.of("target","minecraft:zombie"));
        var future=task("future",false,Map.of("target","minecraft:zombie"));
        install(List.of(quest("sequence",List.of(first,second),true,List.of()),quest("future",List.of(future),false,List.of(id("sequence")))));
        var engine=ProgressEngine.get();engine.reconcile(player);
        mob(h,EntityType.ZOMBIE,4).hurt(player.damageSources().playerAttack(player),1000);
        h.assertTrue(engine.progress(player).taskProgress(first.id().toString())==1,"current sequential objective advances");
        h.assertTrue(engine.progress(player).taskProgress(second.id().toString())==0,"same death cannot also advance next objective");
        h.assertTrue(engine.progress(player).taskProgress(future.id().toString())==0,"locked dependency receives no event");
        mob(h,EntityType.ZOMBIE,5).hurt(player.damageSources().playerAttack(player),1000);
        h.assertTrue(engine.progress(player).taskProgress(second.id().toString())==1,"next death advances next objective");
        h.assertTrue(engine.progress(player).taskProgress(future.id().toString())==0,"completion does not replay death into newly unlocked quest");
        h.succeed();
    }
    @GameTest(template="empty",batch="encounterTeam") @PrefixGameTestTemplate(false)
    @SuppressWarnings("unchecked")
    public static void teamKillsUseOneOwnerLedgerWithoutMemberFanout(GameTestHelper h) throws Exception {
        var a=room(h);var b=h.makeMockServerPlayerInLevel();var members=Set.of(a.getUUID(),b.getUUID());
        var providerId=ResourceLocation.parse("brnquest:openpac");var owner=new ProgressOwnerId(providerId,UUID.randomUUID());
        // Only this isolated fixture replaces the optional provider, then restores the frozen registry exactly.
        var field=ProgressOwnerProviderRegistry.class.getDeclaredField("PROVIDERS");field.setAccessible(true);
        var registry=(Map<ResourceLocation,ProgressOwnerProvider>)field.get(null);var previous=registry.get(providerId);
        var provider=new ProgressOwnerProvider() {
            public ResourceLocation id(){return providerId;}
            public Optional<ProgressOwnerId> resolve(ServerPlayer player){return members.contains(player.getUUID()) ? Optional.of(owner) : Optional.empty();}
            public Set<UUID> members(net.minecraft.server.MinecraftServer server,ProgressOwnerId id){return members;}
            public ProgressOwnerLifecycle lifecycle(net.minecraft.server.MinecraftServer server,ProgressOwnerId id){return ProgressOwnerLifecycle.ACTIVE;}
            public Optional<ProgressOwnerArchive> archivedSnapshot(net.minecraft.server.MinecraftServer server,ProgressOwnerId id){return Optional.empty();}
        };
        registry.put(providerId,provider);
        try {
            var task=task("team",false,Map.of("target","minecraft:zombie","count","2"));
            install(List.of(quest("team",List.of(task),false,List.of())));var engine=ProgressEngine.get();engine.reconcile(a);engine.reconcile(b);
            mob(h,EntityType.ZOMBIE,4).hurt(a.damageSources().playerAttack(a),1000);
            h.assertTrue(engine.progress(a).taskProgress(task.id().toString())==1 && engine.progress(b).taskProgress(task.id().toString())==1,"one kill increments the shared ledger once");
            mob(h,EntityType.ZOMBIE,5).hurt(b.damageSources().playerAttack(b),1000);
            h.assertTrue(engine.progress(a).taskProgress(task.id().toString())==2,"another member contributes to the same owner");
        } finally { if(previous==null) registry.remove(providerId);else registry.put(providerId,previous); }
        h.succeed();
    }
    private static EncounterConfig observe(String kind,String target,double distance,int duration) { return new EncounterConfig(kind,target,distance,duration,1); }
    private static ServerPlayer room(GameTestHelper h) {
        for(int x=0;x<5;x++) for(int y=2;y<6;y++) for(int z=0;z<9;z++) h.setBlock(new BlockPos(x,y,z),Blocks.AIR);
        h.setBlock(new BlockPos(2,3,6),Blocks.OAK_LOG);
        var player=h.makeMockServerPlayerInLevel();var pos=h.absolutePos(new BlockPos(2,2,1));player.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);player.setYRot(0);player.setXRot(0);
        return player;
    }
    private static <T extends Mob> T mob(GameTestHelper h,EntityType<T> type,int z) {
        var entity=type.create(h.getLevel());var pos=h.absolutePos(new BlockPos(2,2,z));
        entity.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);entity.setNoAi(true);entity.setNoGravity(true);h.getLevel().addFreshEntity(entity);return entity;
    }
    private static TaskDefinition task(String name,boolean observe,Map<String,String> config) { return new TaskDefinition(id("book"),id(name),observe?EncounterConfig.OBSERVE:EncounterConfig.KILL,config,false); }
    private static QuestDefinition quest(String name,List<TaskDefinition> tasks,boolean sequential,List<ResourceLocation> dependencies) {
        var behavior=new QuestBehavior(false,false,false,0,false,false,false,DependencyRequirement.ALL_COMPLETED,0,sequential,false,0,false);
        return new QuestDefinition(id("book"),id(name),id("chapter"),name,"","","",0,0,dependencies,tasks,List.of(),"",QuestAppearance.DEFAULT,behavior,Map.of());
    }
    private static void install(List<QuestDefinition> quests) {
        var book=new QuestBookDefinition(id("book"),1,"Encounter tests",List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,quests)),Map.of());
        if(!QuestBookManager.get().install(book,new DiagnosticReport())) throw new IllegalStateException("Encounter fixture rejected");
    }
    private static ResourceLocation id(String value) { return ResourceLocation.parse("brnquest_f4:"+value); }
}
