package yourscraft.jasdewstarfield.brnquest.client.ui;

import java.util.*;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

/** Card-local transitions separate a new arrival from older cards folding and returning to the front. */
final class QuestNotificationMotion {
    record Target(String id, UiRect bounds) {}
    record Pose(double left, double top, double width, double height) {
        static Pose of(UiRect bounds) { return new Pose(bounds.left(), bounds.top(), bounds.width(), bounds.height()); }
        Pose translated(double y) { return new Pose(left, top + y, width, height); }
        Pose blend(Pose other, double t) {
            return new Pose(left + (other.left - left) * t, top + (other.top - top) * t,
                    width + (other.width - width) * t, height + (other.height - height) * t);
        }
    }
    private record Transition(Pose from, Pose to, long startedAt, long duration) {
        Pose at(long now) { return from.blend(to, ease((double) (now - startedAt) / duration)); }
    }
    private final Map<String, Transition> transitions = new HashMap<>();
    private double previousClosingOffset;

    /** Targets are ordered front-to-back; only unseen cards slide in from above the screen. */
    List<Pose> frame(List<Target> targets, long now, boolean reducedMotion, double closingOffset) {
        Set<String> ids = new HashSet<>();
        for (var target : targets) ids.add(target.id());
        transitions.keySet().retainAll(ids);
        var poses = new ArrayList<Pose>();
        for (int index = 0; index < targets.size(); index++) {
            var target = targets.get(index);
            Pose destination = Pose.of(target.bounds());
            var previous = transitions.get(target.id());
            if (reducedMotion) {
                previous = new Transition(destination, destination, now, 1);
            } else if (previous == null) {
                Pose origin = destination.translated(-target.bounds().bottom() - 8);
                // Entry and folding start together with separate transitions, without an artificial pause.
                previous = new Transition(origin, destination, now, 240_000_000L);
            } else if (!previous.to().equals(destination) || previousClosingOffset != 0 && closingOffset == 0) {
                // Sample the existing pose instead of restarting its entry, including interrupted final closure.
                previous = new Transition(previous.at(now).translated(previousClosingOffset - closingOffset), destination, now, 200_000_000L);
            }
            transitions.put(target.id(), previous);
            poses.add(previous.at(now).translated(reducedMotion ? 0 : closingOffset));
        }
        previousClosingOffset = targets.isEmpty() || reducedMotion ? 0 : closingOffset;
        return List.copyOf(poses);
    }

    static double ease(double progress) {
        double t = Math.max(0, Math.min(1, progress));
        return t * t * (3 - 2 * t);
    }
    void clear() { transitions.clear(); previousClosingOffset = 0; }
}
