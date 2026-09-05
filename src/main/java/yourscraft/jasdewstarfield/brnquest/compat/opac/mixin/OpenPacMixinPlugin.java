package yourscraft.jasdewstarfield.brnquest.compat.opac.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/** Selects string targets only for the audited binary; absent mods never resolve target classes. */
public final class OpenPacMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target, String mixin) {
        if (Boolean.getBoolean("brnquest.opac.disableHooks")) return false;
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream().anyMatch(mod ->
                mod.getModId().equals("openpartiesandclaims") && mod.getVersion().toString().equals("0.30.3"));
    }
    public void acceptTargets(Set<String> mine, Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        // Count actual handler calls rather than assuming selection means successful injection.
        long hooks = node.methods.stream().filter(method -> !method.name.contains("brnquest"))
                .filter(method -> java.util.Arrays.stream(method.instructions.toArray()).anyMatch(insn ->
                        insn instanceof MethodInsnNode call && call.name.contains("brnquest$invalidate")))
                .count();
        System.setProperty("brnquest.opac.hookCount", Long.toString(hooks));
    }
}
