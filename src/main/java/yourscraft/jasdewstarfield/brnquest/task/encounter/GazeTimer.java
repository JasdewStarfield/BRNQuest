package yourscraft.jasdewstarfield.brnquest.task.encounter;

/** Counts actual consecutive server ticks on one identity, never elapsed gaps or repeated polls. */
public final class GazeTimer {
    private Object target;
    private int lastTick=Integer.MIN_VALUE;
    private int ticks;
    public int ticks() { return ticks; }
    public int sample(int tick,Object hit) {
        if(hit==null) { target=null; ticks=0; lastTick=tick; return 0; }
        if(hit.equals(target) && tick==lastTick) return ticks;
        ticks=hit.equals(target) && tick==lastTick+1 ? ticks+1 : 1;
        target=hit; lastTick=tick; return ticks;
    }
}
