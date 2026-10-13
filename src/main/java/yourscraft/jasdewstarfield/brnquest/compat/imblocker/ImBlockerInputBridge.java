package yourscraft.jasdewstarfield.brnquest.compat.imblocker;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.fml.ModList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextInput;

/** Client-only focus lifecycle for owned inputs. Absence or an unsupported optional API leaves ordinary editing intact. */
public final class ImBlockerInputBridge {
    private static boolean attempted,disabled,synchronizing;
    private static ImBlockerBinding binding;
    private static EditorTextInput owner;
    private static EditorTextInput.ImeGeometry lastGeometry;
    private ImBlockerInputBridge() {}

    private static EditorTextInput focusedInput() {
        var minecraft=Minecraft.getInstance();
        if(minecraft.screen==null || !minecraft.isWindowActive()) return null;
        GuiEventListener focused=minecraft.screen;
        while(focused instanceof ContainerEventHandler container) focused=container.getFocused();
        return focused instanceof EditorTextInput input && input.acceptsText()?input:null;
    }
    public static void synchronize() {
        if(disabled || synchronizing) return;
        if(!attempted) {
            attempted=true;
            if(!ModList.get().isLoaded("imblocker")) return;
            try { binding=ImBlockerBinding.load(ImBlockerInputBridge.class.getClassLoader()); }
            catch(ReflectiveOperationException | LinkageError exception) { disable(exception); }
        }
        if(binding==null) return;
        synchronizing=true;
        try {
            EditorTextInput next=focusedInput();
            if(next!=owner) {
                binding.release(); owner=next; lastGeometry=null;
                if(owner!=null) {
                    EditorTextInput acquired=owner;
                    binding.acquire(()-> {
                        var box=acquired.imeGeometry();
                        return new ImBlockerBinding.Geometry(box.x(),box.y(),box.width(),box.height(),box.caretX(),box.caretY(),box.fontHeight());
                    },()->focusedInput()==acquired);
                }
            }
            if(owner!=null) {
                var geometry=owner.imeGeometry();
                if(!geometry.equals(lastGeometry)) { binding.moved(); lastGeometry=geometry; }
            }
        } catch(ReflectiveOperationException | RuntimeException | LinkageError exception) { disable(exception); }
        finally { synchronizing=false; }
    }
    public static boolean characterProbe(EditorTextInput input,char character) {
        if(character!=0) return false;
        synchronize();
        if(binding!=null && owner==input && focusedInput()==input) {
            try { binding.probe(); }
            catch(ReflectiveOperationException | RuntimeException | LinkageError exception) { disable(exception); }
        }
        return true;
    }
    public static void release() {
        if(binding!=null) {
            try { binding.release(); }
            catch(ReflectiveOperationException | RuntimeException | LinkageError exception) { disable(exception); }
        }
        owner=null; lastGeometry=null;
    }
    private static void disable(Throwable exception) {
        disabled=true;
        if(binding!=null) {
            try { binding.release(); } catch(ReflectiveOperationException | RuntimeException | LinkageError ignored) {}
        }
        binding=null; owner=null; lastGeometry=null;
        LogUtils.getLogger().warn("BRNQuest could not bind the optional IMBlocker input API; text editing remains available",exception);
    }
}
