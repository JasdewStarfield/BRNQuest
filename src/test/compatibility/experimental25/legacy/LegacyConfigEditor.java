package legacy;
import net.minecraft.client.gui.screens.Screen;
import java.util.function.Consumer;
import yourscraft.jasdewstarfield.brnquest.client.ui.ClientConfigEditors;
/** Compiled against the old single-value editor factory contract. */
public final class LegacyConfigEditor implements ClientConfigEditors.Factory {
    public Screen create(Screen parent, String value, Consumer<String> commit) {
        commit.accept(value + "-edited");
        return parent;
    }
}
