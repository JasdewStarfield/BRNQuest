package yourscraft.jasdewstarfield.brnquest.client.ui;

import java.util.*;
import net.minecraft.client.Minecraft;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.network.ObservationNetwork;

/** Short-lived display samples expire after polling stops, and never survive a server connection change. */
public final class ObservationDisplay {
    private record Sample(int ticks,long received) {}
    private static final Map<String,Sample> SAMPLES=new HashMap<>();
    private static Object connection;
    private ObservationDisplay() {}
    private static void refresh() {
        var current=Minecraft.getInstance().getConnection();
        if(current!=connection) { connection=current; SAMPLES.clear(); }
        long now=System.nanoTime(); SAMPLES.values().removeIf(sample->now-sample.received()>500_000_000L);
    }
    public static void receive(ObservationNetwork.Payload payload) { refresh(); SAMPLES.put(payload.book()+"/"+payload.task(),new Sample(payload.ticks(),System.nanoTime())); }
    public static int ticks(TaskView task) { refresh(); var sample=SAMPLES.get(task.bookId()+"/"+task.id()); return sample==null ? 0 : sample.ticks(); }
}
