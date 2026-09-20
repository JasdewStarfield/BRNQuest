package yourscraft.jasdewstarfield.brnquest.builtin.client;
import yourscraft.jasdewstarfield.brnquest.client.ui.*;

import net.minecraft.client.Minecraft;
import yourscraft.jasdewstarfield.brnquest.builtin.network.LocationOptionsNetwork;
import yourscraft.jasdewstarfield.brnquest.builtin.network.AdvancementGroupNetwork;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement.AdvancementGroupSnapshot;
import java.util.List;

/** Reuse the ordered member-page assembler; connection and revision changes isolate old display data. */
public final class LocationOptionsCache {
    private static Object connection;
    private static AdvancementGroupSnapshot snapshot=new AdvancementGroupSnapshot();
    private LocationOptionsCache() {}
    private static void checkConnection() {
        var minecraft=Minecraft.getInstance(); var current=minecraft==null ? null : minecraft.getConnection();
        if(connection!=current) { connection=current; snapshot=new AdvancementGroupSnapshot(); }
    }
    public static void receive(LocationOptionsNetwork.Page page) {
        checkConnection(); snapshot.receive(new AdvancementGroupNetwork.Page(page.revision(),page.selector(),page.offset(),page.total(),page.members(),"",page.reset()));
    }
    public static List<String> members(String kind,String selector) { checkConnection(); var result=snapshot.members(kind+"|"+selector); return result==null ? List.of() : result; }
}
