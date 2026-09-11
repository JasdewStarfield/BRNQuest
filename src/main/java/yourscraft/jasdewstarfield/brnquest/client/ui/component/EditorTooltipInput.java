package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** A stationary pointer must not compete with keyboard navigation for tooltip ownership. */
public final class EditorTooltipInput {
    private boolean keyboard;
    private double x = Double.NaN, y = Double.NaN;

    public void keyPressed(int key) {
        if (key == 258 || (key >= 262 && key <= 269) || key == 295) keyboard = true;
    }

    public void pointer(double nextX, double nextY, boolean clicked) {
        // The first frame establishes a baseline; rendering alone never counts as mouse input.
        if (clicked || (!Double.isNaN(x) && (x != nextX || y != nextY))) keyboard = false;
        x = nextX; y = nextY;
    }

    public boolean suppressTooltip() { return keyboard; }
}
