package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ResolvedOptionsTest {
    @Test void visibleNameAndRawIdRemainSeparateWithoutMutatingTheName() {
        var name=Component.literal("Localized name");
        var entry=ResolvedOptions.entry("test:original",name);
        assertEquals("Localized name",entry.getString());
        assertEquals("test:original",ResolvedOptions.hoverText(entry).getString());
        assertNull(name.getStyle().getHoverEvent());
        assertEquals("test:missing",ResolvedOptions.entry("test:missing",null).getString());
    }
    @Test void childAndNestedPickersInheritBothPausePolicies() {
        // No graphics/context initialization is needed to check the parent pause contract.
        for(boolean paused:List.of(true,false)) {
            var parent=new Screen(Component.empty()) { public boolean isPauseScreen() { return paused; } };
            var field=new ServerFieldScreen(parent,"test:source","",ignored->{});
            var options=new ResolvedOptionsScreen(field,List::of);
            assertEquals(paused,field.isPauseScreen());
            assertEquals(paused,options.isPauseScreen());
        }
    }
}
