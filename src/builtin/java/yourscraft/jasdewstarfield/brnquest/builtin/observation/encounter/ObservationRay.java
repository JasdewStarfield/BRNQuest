package yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Server eye position and look vector are authoritative; neither mode looks through a nearer hit. */
public final class ObservationRay {
    private ObservationRay() {}
    public record Hit(Object identity) {}
    public static Hit hit(ServerPlayer player,EncounterConfig config) {
        if(!player.isAlive() || player.isSpectator()) return null;
        var level=player.serverLevel(); var start=player.getEyePosition(); var end=start.add(player.getLookAngle().scale(config.distance()));
        // clip() may access block states: reject unloaded chunks before tracing, without forcing any load.
        for(int x=BlockPos.containing(Math.min(start.x,end.x),0,0).getX()>>4;x<=(BlockPos.containing(Math.max(start.x,end.x),0,0).getX()>>4);x++)
            for(int z=BlockPos.containing(0,0,Math.min(start.z,end.z)).getZ()>>4;z<=(BlockPos.containing(0,0,Math.max(start.z,end.z)).getZ()>>4);z++)
                if(!level.hasChunk(x,z)) return null;
        var block=level.clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        double nearest=block.getType()==HitResult.Type.MISS ? config.distance()*config.distance() : start.distanceToSqr(block.getLocation());
        net.minecraft.world.entity.Entity entityHit=null;
        for(var entity:level.getEntities(player,new AABB(start,end).inflate(1),entity->entity.isAlive() && !entity.isSpectator() && entity.isPickable())) {
            var box=entity.getBoundingBox().inflate(entity.getPickRadius());
            var hit=box.contains(start) ? java.util.Optional.of(start) : box.clip(start,end);
            if(hit.isPresent() && start.distanceToSqr(hit.get())<nearest) { nearest=start.distanceToSqr(hit.get()); entityHit=entity; }
        }
        if(entityHit!=null) return config.kind().equals("entity") && EncounterTargets.matches(BuiltInRegistries.ENTITY_TYPE,entityHit.getType(),config.selector())
                ? new Hit(entityHit.getUUID()) : null;
        return config.kind().equals("block") && block.getType()!=HitResult.Type.MISS
                && EncounterTargets.matches(BuiltInRegistries.BLOCK,level.getBlockState(block.getBlockPos()).getBlock(),config.selector())
                ? new Hit(block.getBlockPos().immutable()) : null;
    }
}
