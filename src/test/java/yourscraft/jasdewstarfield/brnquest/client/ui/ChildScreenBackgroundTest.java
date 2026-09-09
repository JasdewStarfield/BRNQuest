package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChildScreenBackgroundTest {
    @Test void changesEitherDimensionOnceAndLeavesMatchingParentUntouched() {
        var parent = new RecordingScreen();
        ChildScreenBackground.resizeIfNeeded(parent,800,600);
        assertEquals(1,parent.resizes);
        assertEquals(800,parent.width);
        assertEquals(600,parent.height);
        ChildScreenBackground.resizeIfNeeded(parent,800,600);
        assertEquals(1,parent.resizes);
        ChildScreenBackground.resizeIfNeeded(parent,900,600);
        ChildScreenBackground.resizeIfNeeded(parent,900,700);
        assertEquals(3,parent.resizes);
        assertEquals("unsaved text",parent.draft);
    }
    /** Test the resize contract without initializing Minecraft graphics or replacing form state. */
    private static final class RecordingScreen extends Screen {
        int resizes;
        String draft="unsaved text";
        RecordingScreen() { super(Component.empty()); }
        @Override public void resize(Minecraft minecraft,int width,int height) {
            this.width=width; this.height=height; resizes++;
        }
    }
}
