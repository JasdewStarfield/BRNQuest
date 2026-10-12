package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Checks exact injection sites in the installed Minecraft bytecode; visual input remains an in-game gate. */
class NativeInputMixinTargetsTest {
    private ClassNode read(String owner) throws IOException {
        try(var bytes=getClass().getClassLoader().getResourceAsStream(owner+".class")) {
            assertNotNull(bytes); var node=new ClassNode(); new ClassReader(bytes).accept(node,0); return node;
        }
    }
    private void invocation(String owner,String method,String target,String name,String descriptor) throws IOException {
        var node=read(owner);
        boolean found=node.methods.stream().filter(m->m.name.equals(method)).anyMatch(m->{
            for(var instruction:m.instructions)
                if(instruction instanceof MethodInsnNode call && call.owner.equals(target)
                        && call.name.equals(name) && call.desc.equals(descriptor)) return true;
            return false;
        });
        assertTrue(found,owner+"."+method+" -> "+target+"."+name+descriptor);
    }
    @Test void sourceFilteringWidthAndReflowHooksMatchThisMinecraftVersion() throws IOException {
        String edit="net/minecraft/client/gui/components/EditBox", multi="net/minecraft/client/gui/components/MultilineTextField";
        invocation(edit,"insertText","net/minecraft/util/StringUtil","filterText","(Ljava/lang/String;)Ljava/lang/String;");
        invocation(edit,"renderWidget","net/minecraft/client/gui/Font","width","(Ljava/lang/String;)I");
        invocation(multi,"insertText","net/minecraft/util/StringUtil","filterText","(Ljava/lang/String;Z)Ljava/lang/String;");
        invocation(multi,"insertText",multi,"truncateInsertionText","(Ljava/lang/String;)Ljava/lang/String;");
        invocation(multi,"seekCursorLine","net/minecraft/client/gui/Font","width","(Ljava/lang/String;)I");
        invocation(multi,"reflowDisplayLines","net/minecraft/client/StringSplitter","splitLines",
                "(Ljava/lang/String;ILnet/minecraft/network/chat/Style;ZLnet/minecraft/client/StringSplitter$LinePosConsumer;)V");
        assertTrue(read(edit).fields.stream().anyMatch(f->f.name.equals("highlightPos")&&f.desc.equals("I")));
    }
}
