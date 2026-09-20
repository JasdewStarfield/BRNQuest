package yourscraft.jasdewstarfield.brnquest.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces.BuriedTreasurePiece;
import net.neoforged.neoforge.gametest.*;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import yourscraft.jasdewstarfield.brnquest.progress.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.location.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real registry, chunk and progress checks; every fixture belongs to the isolated GameTest world. */
@GameTestHolder("brnquest")
@SuppressWarnings("removal")
public final class LocationTaskGameTests {
    @GameTest(template = "empty", batch = "locationPolling", timeoutTicks = 100)
    @PrefixGameTestTemplate(false)
    public static void visitsLatchPersistAndResetThroughTheEngine(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(origin.getX(), origin.getY(), origin.getZ());
        var visit = task("visit", "location", Map.of("ignore_dimension", "true", "position", vector(origin), "size", "1,1,1"));
        var gate = task("gate", "dimension", Map.of("dimension", "minecraft:the_nether"));
        var quest = install("latch", List.of(visit, gate), false);
        var engine = ProgressEngine.get();
        engine.reconcile(player);
        var phase = new AtomicInteger();
        // Exercise the public entry alongside normal player ticks; repeated same-tick samples must be harmless.
        helper.succeedWhen(() -> {
            engine.pollTasks(player);
            if (phase.get() == 0) {
                helper.assertTrue(engine.progress(player).taskProgress(visit.id().toString()) == 1, "visit is sampled at its configured cadence");
                player.setPos(origin.getX()+2, origin.getY(), origin.getZ());
                var saved = QuestProgressData.get(player.server).save(new net.minecraft.nbt.CompoundTag(), player.registryAccess());
                player.server.overworld().getDataStorage().set("brnquest_progress", QuestProgressData.load(saved, player.registryAccess()));
                helper.assertTrue(engine.progress(player).taskProgress(visit.id().toString()) == 1, "leaving and SavedData reload retain the visit");
                engine.reset(player, quest.id());
                phase.set(1);
                helper.assertTrue(false, "wait for an out-of-box sample after reset");
            }
            if (phase.get() == 1) {
                helper.assertTrue(engine.progress(player).taskProgress(visit.id().toString()) == 0, "reset outside the box clears the old visit");
                player.setPos(origin.getX(), origin.getY(), origin.getZ());
                phase.set(2);
            }
            helper.assertTrue(engine.progress(player).taskProgress(visit.id().toString()) == 1, "re-entering completes a new visit");
            helper.assertTrue(engine.progress(player).taskProgress(gate.id().toString()) == 0, "other dimension never matches");
        });
    }

    @GameTest(template = "empty", batch = "locationSequential", timeoutTicks = 100)
    @PrefixGameTestTemplate(false)
    public static void pollingHonorsSequentialTasks(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var gate = task("first", "checkmark", Map.of());
        var visit = task("second", "dimension", Map.of("dimension", "minecraft:overworld"));
        var quest = install("sequence", List.of(gate, visit), true);
        var engine = ProgressEngine.get(); engine.reconcile(player);
        var phase = new AtomicInteger();
        helper.succeedWhen(() -> {
            engine.pollTasks(player);
            if (phase.get() == 0) {
                helper.assertTrue(player.server.getTickCount() % 10 == 0, "wait for a polling tick");
                helper.assertTrue(engine.progress(player).taskProgress(visit.id().toString()) == 0, "future visit cannot complete before checkmark");
                engine.progress(player).addTaskProgress(gate.id().toString(), 1);
                phase.set(1);
                helper.assertTrue(false, "wait for the next sample");
            }
            helper.assertTrue(engine.progress(player).taskProgress(visit.id().toString()) == 1, "newly current visit is sampled");
        });
    }

    @GameTest(template = "empty", batch = "locationRegistry")
    @PrefixGameTestTemplate(false)
    public static void nativeSelectorsResolveAndSpectatorsDoNotVisit(GameTestHelper helper) {
        var player = realPlayer(helper);
        var pos = helper.absolutePos(new BlockPos(1, 2, 1)); player.setPos(pos.getX(),pos.getY(),pos.getZ());
        var biome = player.level().getBiome(pos).unwrapKey().orElseThrow().location().toString();
        helper.assertTrue(LocationTargets.matches(player,"biome",LocationConfig.parse("biome",Map.of("biome",biome))), "actual biome ID matches");
        helper.assertTrue(LocationTargets.matches(player,"biome",LocationConfig.parse("biome",Map.of("biome","#minecraft:is_overworld"))), "native biome tag matches");
        helper.assertTrue(!LocationTargets.matches(player,"dimension",LocationConfig.parse("dimension",Map.of("dimension","#minecraft:overworld"))), "dimension type IDs are not invented dimension groups");
        helper.assertTrue(!LocationTargets.resolve(player,"structure","#brnquest:missing").error().isBlank(), "missing tag is diagnosed");
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        helper.assertTrue(!LocationTargets.matches(player,"dimension",LocationConfig.parse("dimension",Map.of("dimension","minecraft:overworld"))), "spectator visits do not count");
        helper.assertTrue(ServerFieldSources.query(player,ResourceLocation.parse("brnquest:dimension"),"", "minecraft:overworld").error().equals("permission"), "non-author cannot query author sources");
        // GameTestServer defaults OP to level 0, so the fixture explicitly grants author level 2.
        player.server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(player.getGameProfile(),2,false));
        try {
            var result = ServerFieldSources.query(player,ResourceLocation.parse("brnquest:dimension"),"overworld", "minecraft:overworld");
            helper.assertTrue(result.error().isEmpty() && result.selectedCount()==1 && result.current().equals("minecraft:overworld"), "authorized search resolves live dimension and current value");
            // Real registries contain more than one page: every ID/tag must remain retrievable.
            var firstPage = ServerFieldSources.query(player,ResourceLocation.parse("brnquest:biome"),"", "");
            helper.assertTrue(firstPage.total()>64,"biome fixture exercises the historical cutoff");
            var all = new java.util.HashSet<String>();
            for (int offset=0; offset<firstPage.total(); offset+=ServerFieldSources.PAGE_SIZE) {
                var page=ServerFieldSources.query(player,ResourceLocation.parse("brnquest:biome"),"", "",offset);
                helper.assertTrue(page.entries().size()<=64,"each response remains bounded");
                page.entries().forEach(entry -> all.add(entry.value()));
            }
            helper.assertTrue(all.size()==firstPage.total(),"all real biome IDs and tags are reachable without truncation or duplicate pages");
            var position = ServerFieldSources.query(player,ResourceLocation.parse("brnquest:position"),"", "0,0,0");
            helper.assertTrue(position.current().equals(vector(pos)), "current coordinates come from the server player");
            if (net.neoforged.fml.ModList.get().isLoaded("brnquest_example")) {
                var group = LocationTargets.resolve(player,"dimension","#brnquest_example:surface");
                helper.assertTrue(group.error().isEmpty() && group.ids().equals(Set.of(ResourceLocation.parse("minecraft:overworld"))), "datapack nested dimension group uses actual level IDs");
                LocationTargets.invalidate();
                helper.assertTrue(LocationTargets.resolve(player,"dimension","#brnquest_example:surface").equals(group), "resource invalidation rebuilds groups correctly");
            }
        } finally { player.server.getPlayerList().deop(player.getGameProfile()); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "locationStructure")
    @PrefixGameTestTemplate(false)
    public static void structuresUsePiecesAndNeverLoadReferenceChunks(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var origin = helper.absolutePos(new BlockPos(1, 2, 1));
        var chunk = helper.getLevel().getChunkAt(origin);
        // Two one-block pieces make a deterministic gap inside the overall structure box.
        origin = new BlockPos(chunk.getPos().getMinBlockX()+2, origin.getY(), chunk.getPos().getMinBlockZ()+2);
        var structure = player.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("minecraft:village_plains"));
        var previousStarts = new HashMap<>(chunk.getAllStarts());
        var references = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(chunk.getReferencesForStructure(structure));
        try {
            chunk.setStartForStructure(structure, new StructureStart(structure,chunk.getPos(),0,
                    new PiecesContainer(List.of(new BuriedTreasurePiece(origin),new BuriedTreasurePiece(origin.offset(4,0,0))))));
            chunk.addReferenceForStructure(structure,chunk.getPos().toLong());
            player.setPos(origin.getX(),origin.getY(),origin.getZ());
            var config=LocationConfig.parse("structure",Map.of("structure","#minecraft:village"));
            helper.assertTrue(LocationTargets.matches(player,"structure",config), "native structure tag recognizes a loaded piece");
            player.setPos(origin.getX()+2,origin.getY(),origin.getZ());
            helper.assertTrue(!LocationTargets.matches(player,"structure",config), "gap in aggregate box does not count as a structure piece");
            var far = new ChunkPos(100000,100000);
            chunk.getReferencesForStructure(structure).clear();
            chunk.addReferenceForStructure(structure,far.toLong());
            LocationTargets.invalidate();
            helper.assertTrue(!LocationTargets.matches(player,"structure",config), "unloaded structure reference does not match");
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(far.x,far.z)==null, "sampling did not load the referenced chunk");
        } finally {
            chunk.setAllStarts(previousStarts);
            chunk.getReferencesForStructure(structure).clear(); chunk.getReferencesForStructure(structure).addAll(references);
            LocationTargets.invalidate();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "externalPolling", timeoutTicks = 100)
    @PrefixGameTestTemplate(false)
    public static void externalTaskSuppliesItsOwnSamplerAndFieldSource(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("brnquest_example")) { helper.succeed(); return; }
        var player = helper.makeMockServerPlayerInLevel(); player.addTag("external_visit");
        var task = new TaskDefinition(id("book"),id("external_visit"),ResourceLocation.parse("brnquest_example:marker"),Map.of("tag","external_visit"),false);
        var gate = task("external_gate","dimension",Map.of("dimension","minecraft:the_nether"));
        install("external",List.of(task,gate),false);
        var engine = ProgressEngine.get(); engine.reconcile(player);
        helper.succeedWhen(() -> {
            engine.pollTasks(player);
            helper.assertTrue(engine.progress(player).taskProgress(task.id().toString())==1, "external sampler reaches the core ledger without a type branch");
            player.removeTag("external_visit");
            helper.assertTrue(TaskTypeExecutor.satisfied(TaskTypeRegistry.get(task.typeId()),
                    new TaskContext(player,id("book"),id("external"),ApiViews.task(task),1)), "external visit remains latched");
        });
    }

    /** Vanilla's mock helper hardcodes isSpectator=false; use a normal player for game-mode checks. */
    @GameTest(template="empty",batch="locationPreview") @PrefixGameTestTemplate(false)
    public static void ordinaryPlayerReceivesResolvedBiomeMembers(GameTestHelper helper) {
        var player=helper.makeMockServerPlayerInLevel();
        var pages=yourscraft.jasdewstarfield.brnquest.builtin.network.LocationOptionsNetwork.snapshot(player);
        helper.assertTrue(pages.getFirst().reset(),"login/reload resets removed selectors");
        var members=pages.stream().filter(page->page.selector().equals("biome|#minecraft:is_overworld"))
                .flatMap(page->page.members().stream()).toList();
        helper.assertTrue(members.contains("minecraft:plains"),"native biome tag expands for ordinary players");
        helper.assertTrue(pages.stream().allMatch(page->page.members().size()<=64),"display pages stay bounded");
        helper.succeed();
    }

    private static net.minecraft.server.level.ServerPlayer realPlayer(GameTestHelper helper) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "exploration-player"), false);
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        return player;
    }

    private static TaskDefinition task(String name,String kind,Map<String,String> config) {
        return new TaskDefinition(id("book"),id(name),ResourceLocation.parse("brnquest:"+kind),config,false);
    }
    private static QuestDefinition install(String name,List<TaskDefinition> tasks,boolean sequential) {
        var behavior = new QuestBehavior(false,false,false,0,false,false,false,DependencyRequirement.ALL_COMPLETED,0,sequential,false,0,false);
        var quest = new QuestDefinition(id("book"),id(name),id("chapter"),"Exploration","","","",0,0,List.of(),tasks,List.of(),"",QuestAppearance.DEFAULT,behavior,Map.of());
        var book = new QuestBookDefinition(id("book"),1,"Exploration",List.of(new ChapterGroupDefinition(id("book"),id("group"),"Group",0)),
                List.of(new ChapterDefinition(id("book"),id("chapter"),id("group"),"Chapter","",0,List.of(quest))),Map.of());
        if (!QuestBookManager.get().install(book,new DiagnosticReport())) throw new IllegalStateException("Exploration fixture rejected");
        return quest;
    }
    private static String vector(BlockPos p) { return p.getX()+","+p.getY()+","+p.getZ(); }
    private static ResourceLocation id(String path) { return ResourceLocation.parse("brnquest_f2:"+path); }
}
