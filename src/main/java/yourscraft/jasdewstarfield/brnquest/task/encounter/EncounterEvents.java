package yourscraft.jasdewstarfield.brnquest.task.encounter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.*;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import java.util.*;

/** One death event belongs to one credited player; owner routing and eligibility stay in the public progress API. */
@EventBusSubscriber(modid=BRNQuest.MOD_ID)
public final class EncounterEvents {
    private static final Set<LivingEntity> SEEN=Collections.newSetFromMap(new WeakHashMap<>());
    private static final OperationContext CONTEXT=OperationContext.integration(EncounterConfig.KILL);
    private EncounterEvents() {}
    public static void initialize() {
        yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents.subscribe(yourscraft.jasdewstarfield.brnquest.event.QuestBookReloadedEvent.class,event->ObservationRuntime.clear());
    }
    public static ServerPlayer killer(DamageSource source) {
        if(!(source.getEntity() instanceof ServerPlayer player)) return null;
        var direct=source.getDirectEntity();
        return direct==player || direct instanceof Projectile projectile && projectile.getOwner()==player ? player : null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(event.isCanceled()) return;
        var player=killer(event.getSource());
        if(player==null || player.isSpectator() || !SEEN.add(event.getEntity())) return;
        record(player,event.getEntity());
    }
    public static void record(ServerPlayer player,LivingEntity victim) {
        // Snapshot quest availability before any completion can unlock dependent quests on this same death.
        var candidates=new LinkedHashMap<QuestView,ProgressView>();
        for(var quest:BrnQuestApi.getQuests()) BrnQuestApi.getProgress(player,quest.id().toString()).ifPresent(progress->{
            if(progress.status()==QuestStatus.AVAILABLE || progress.status()==QuestStatus.ACTIVE) candidates.put(quest,progress);
        });
        var changed=new ArrayList<QuestView>();
        candidates.forEach((quest,progress)->{
            boolean wrote=false;
            // Later sequential tasks are checked first, before this death advances an earlier task.
            for(var task:quest.tasks().reversed()) {
                if(!task.typeId().equals(EncounterConfig.KILL)) continue;
                EncounterConfig config;
                try { config=EncounterConfig.parse(task.config(),false); } catch(IllegalArgumentException ignored) { continue; }
                if(progress.taskProgress().getOrDefault(task.id(),0L)>=config.count()
                        || !EncounterTargets.matches(BuiltInRegistries.ENTITY_TYPE,victim.getType(),config.selector())) continue;
                wrote|=BrnQuestApi.addTaskProgressResult(CONTEXT,player,task.id().toString(),1).success();
            }
            if(wrote) changed.add(quest);
        });
        for(var quest:changed) BrnQuestApi.submitQuestCompletionResult(CONTEXT,player,quest.id().toString(),false);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) ObservationRuntime.clear(player);
    }
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) resources->ObservationRuntime.clear());
    }
}
