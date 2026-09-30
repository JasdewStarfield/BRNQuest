package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

/** Check version-sensitive injection descriptors against the actual Minecraft dependency without booting a client. */
class TextDebugMixinTargetsTest {
    private void target(String owner, String name, String descriptor) throws IOException {
        try (var bytes = getClass().getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(bytes, owner);
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
            assertTrue(node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)),
                    owner + "." + name + descriptor);
        }
    }

    @Test void textAndWidgetHooksExistInTheRuntimeDependency() throws IOException {
        String font = "Lnet/minecraft/client/gui/Font;", component = "Lnet/minecraft/network/chat/Component;";
        target("net/minecraft/client/gui/GuiGraphics", "drawString", "(" + font + "Ljava/lang/String;FFIZ)I");
        target("net/minecraft/client/gui/GuiGraphics", "drawString", "(" + font + "Lnet/minecraft/util/FormattedCharSequence;FFIZ)I");
        target("net/minecraft/client/gui/GuiGraphics", "drawString", "(" + font + component + "IIIZ)I");
        target("net/minecraft/client/gui/GuiGraphics", "drawCenteredString", "(" + font + component + "III)V");
        target("net/minecraft/client/gui/GuiGraphics", "enableScissor", "(IIII)V");
        target("net/minecraft/client/gui/GuiGraphics", "disableScissor", "()V");
        target("net/minecraft/client/gui/GuiGraphics", "renderTooltipInternal",
                "(" + font + "Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V");
        try (var bytes = getClass().getClassLoader().getResourceAsStream("net/minecraft/client/gui/screens/inventory/tooltip/ClientTextTooltip.class")) {
            assertNotNull(bytes);
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
            assertTrue(node.fields.stream().anyMatch(field -> field.name.equals("text")
                    && field.desc.equals("Lnet/minecraft/util/FormattedCharSequence;")));
        }
        target("net/minecraft/client/gui/Font", "plainSubstrByWidth", "(Ljava/lang/String;I)Ljava/lang/String;");
        target("net/minecraft/client/gui/Font", "plainSubstrByWidth", "(Ljava/lang/String;IZ)Ljava/lang/String;");
        target("net/minecraft/client/gui/Font", "split", "(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;");
        target("net/minecraft/client/gui/components/AbstractWidget", "render", "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V");
        target("net/minecraft/client/gui/components/AbstractWidget", "renderScrollingString",
                "(Lnet/minecraft/client/gui/GuiGraphics;" + font + component + "IIIIII)V");
    }
}
