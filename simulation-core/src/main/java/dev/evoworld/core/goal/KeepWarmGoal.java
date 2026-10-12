package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Exposure;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want to not be cold tonight.
 *
 * <p>Cold is not a need with a bar of its own: the body has no "warmth" that
 * fills and empties, it simply pays for the cold in the two currencies it
 * already has - fuel burnt shivering, and rest not had. So the want is read
 * from the conditions, and rises the way the bodily wants do, with the square:
 * a chilly evening is a small matter and a hard frost is not.
 *
 * <p>It measures the cold <em>around</em> the agent rather than the warmth of
 * the tile it is standing on, and then discounts it by how much fire it has in
 * hand. Measured the other way, an agent sitting at its own fire is perfectly
 * warm, wants nothing, never puts another log on, and wakes at three in the
 * morning beside a heap of ash. What it is exposed to is the night; what
 * answers it is fuel.
 *
 * <p>It asks for nothing in particular. Lighting a fire answers it, feeding one
 * answers it, and in a later winter a shelter or a coat will answer it too.
 */
public final class KeepWarmGoal extends Goal {

    public static final String NAME = "KEEP_WARM";

    /** How far below comfortable counts as thoroughly cold, for sizing a fire. */
    public static final double BITTER = 25.0;

    @Override
    public String name() {
        return NAME;
    }

    /** How many hours of fire count as being set up for the night. */
    public static final double A_NIGHT = 8.0;

    @Override
    protected double computePriority(Perception perception) {
        // Quiet while something is actually wrong, as curiosity and providing
        // for tomorrow are. In this spring the cold costs a night's rest, not a
        // life, so a starving agent has no business gathering firewood; when
        // winter arrives and the cold can kill, this is the line that changes.
        return costOfTheCold(perception)
                * (1.0 - sheltered(perception))
                * ExploreGoal.calm(perception.needs());
    }

    /**
     * What the cold is actually taking, from 0 to 1.
     *
     * <p>Not a curve chosen to feel about right: it is the share of a night's
     * rest the agent would lose out here, which is the same number the body
     * will charge it. A spring night costs about a third of a night's sleep
     * and is worth a fire; a mild evening costs nothing and is not.
     */
    public static double costOfTheCold(Perception perception) {
        double daylight = perception.sky().daylight();
        double outside = Exposure.of(perception.sky().temperature(), daylight).comfort();
        double comfortable = Exposure.of(Exposure.MILD, daylight).comfort();
        return Math.max(0.0, 1.0 - outside / comfortable);
    }

    /** How cold it is out here, fires not counted: what the agent is exposed to. */
    public static double belowMild(Perception perception) {
        return Math.max(0.0, Exposure.MILD - perception.sky().temperature());
    }

    /** How much of the night the fire warming this spot would see the agent through. */
    public static double sheltered(Perception perception) {
        return perception.fire()
                .map(fire -> Math.min(1.0, fire.hoursLeft() / A_NIGHT))
                .orElse(0.0);
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(
                Locale.ROOT,
                "%.1f degrees out, %.0f below comfortable, %.0f%% of a night's fire",
                perception.sky().temperature(),
                belowMild(perception),
                sheltered(perception) * 100);
    }
}
