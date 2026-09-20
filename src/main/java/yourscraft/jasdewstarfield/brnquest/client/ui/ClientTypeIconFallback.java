package yourscraft.jasdewstarfield.brnquest.client.ui;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
/** Shared unknown-type decoration; built-ins register their actual icons separately. */
public final class ClientTypeIconFallback {
    private static final EditorIcon ICON = EditorIcon.sprite(ResourceLocation.parse("brnquest:editor/type/custom"));
    private ClientTypeIconFallback() {}
    public static EditorIcon icon() { return ICON; }
}
