package dev.evoworld.core.goal;

import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;

/**
 * The want to go and see somewhere else.
 *
 * <p>The odd one out: it answers no need and relieves no pressure, so it has no
 * urgency curve to climb. Instead it is a want scaled by three things - the
 * energy available to act on it, how much of the world the agent has lost track
 * of, and how free it is from pressing needs.
 *
 * <p>That second factor is what makes curiosity a drive rather than a constant.
 * An agent that has just walked the whole map wants little; as its sense of
 * the place fades, the want returns and it goes round again. Nothing schedules
 * those rounds, and nothing tells it when it is done - the rhythm falls out of
 * familiarity decaying.
 *
 * <p>Because it is flat in urgency while the others accelerate, exploring wins
 * by default and loses the moment anything is actually the matter. That
 * ordering falls out of the shapes of the curves; nothing says "stop exploring
 * when hungry".
 */
public final class ExploreGoal extends Goal {

    public static final String NAME = "EXPLORE";

    /** How much an agent with energy to spare wants to see new ground. */
    public static final double DEFAULT_CURIOSITY = 0.60;

    /**
     * Curiosity never falls all the way to nothing.
     *
     * <p>An agent that has just seen everything still has some reason to move:
     * things change while you are not looking, and an agent frozen in place
     * would never find out.
     */
    private static final double FLOOR = 0.15;

    private final double curiosity;

    public ExploreGoal() {
        this(DEFAULT_CURIOSITY);
    }

    public ExploreGoal(double curiosity) {
        if (curiosity < MIN_PRIORITY || curiosity > MAX_PRIORITY) {
            throw new IllegalArgumentException("curiosity must be within [0, 1], was " + curiosity);
        }
        this.curiosity = curiosity;
    }

    public double curiosity() {
        return curiosity;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected double computePriority(Perception perception) {
        double stamina = perception.needs().energy() / Needs.MAX;
        double leftToFindOut = Math.max(perception.unexploredFraction(), FLOOR);
        return curiosity * stamina * leftToFindOut * calm(perception.needs());
    }

    /**
     * How free the agent is to think about anything but survival, from 0 to 1.
     *
     * <p>Curiosity is what is left over once survival has had its say. Without
     * this, the want to find things out stayed just as strong in an agent
     * dying of thirst - and a rock underfoot it had never tasted could outscore
     * the spring three steps away. The most pressing need is squared, the same
     * curve the survival goals use, so mild hunger barely dents curiosity and
     * real thirst silences it.
     */
    static double calm(Needs needs) {
        double pressing = Math.max(needs.hunger(), needs.thirst()) / Needs.MAX;
        return 1.0 - pressing * pressing;
    }

    @Override
    protected String reasonFrom(Perception perception) {
        return String.format(
                Locale.ROOT,
                "%.0f%% of the world unknown, energy at %.1f",
                perception.unexploredFraction() * 100,
                perception.needs().energy());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExploreGoal explore
                && Double.compare(curiosity, explore.curiosity) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(curiosity);
    }
}
