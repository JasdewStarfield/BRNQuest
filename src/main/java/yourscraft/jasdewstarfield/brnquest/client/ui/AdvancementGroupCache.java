package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import yourscraft.jasdewstarfield.brnquest.network.AdvancementGroupNetwork;
import yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementGroupSnapshot;
import java.util.List;

/** The connection identity prevents display data from a previous server from leaking into another world. */
public final class AdvancementGroupCache {
    private static Object connection;
    private static AdvancementGroupSnapshot snapshot = new AdvancementGroupSnapshot();
    private AdvancementGroupCache() {}
    private static void checkConnection() {
        var minecraft = Minecraft.getInstance();
        var current = minecraft == null ? null : minecraft.getConnection();
        if (current != connection) { connection=current; snapshot=new AdvancementGroupSnapshot(); }
    }
    public static void receive(AdvancementGroupNetwork.Page page) { checkConnection(); snapshot.receive(page); }
    public static List<String> members(String group) { checkConnection(); return snapshot.members(group); }
}
