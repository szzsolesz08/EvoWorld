package dev.evoworld.core.action;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Exertion;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Optional;

/**
 * Sleep for a stretch of time.
 *
 * <p>The one long rest. For as many ticks as it lasts, the body is
 * {@link ActivityLevel#SLEEPING}: energy comes back quickly and wounds mend
 * fastest, through the metabolism, tick by tick - so a sleep cut short keeps
 * what it had gained. How long to sleep is decided when the agent lies down,
 * by whoever proposed it; the action only carries it out.
 *
 * <p>{@code comfort} is how good the bed is: 1 on bare ground. A bed will be a
 * sleep with more comfort, bringing more back from the same hours. Nothing else
 * has to change for one to exist.
 *
 * <p>Has no precondition. Whether an agent is sleepy enough is a question for
 * the decision layer, not a law of the world - and a precondition would be
 * checked again when the sleep ends, when the agent is rested and would be
 * refused.
 */
public final class SleepAction extends Action {

    public static final String NAME = "SLEEP";

    private final int ticks;
    private final double comfort;

    public SleepAction(int ticks, double comfort) {
        if (ticks < 1) {
            throw new IllegalArgumentException("a sleep lasts at least one tick, was " + ticks);
        }
        this.ticks = ticks;
        // Built once here so a bad comfort is refused now, not at bedtime.
        this.comfort = new Exertion(ActivityLevel.SLEEPING, comfort).comfort();
    }

    /** A sleep on bare ground. */
    public SleepAction(int ticks) {
        this(ticks, 1.0);
    }

    public double comfort() {
        return comfort;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int durationTicks() {
        return ticks;
    }

    /** Energy starts coming back with the first tick asleep, not at the end of the night. */
    @Override
    public int ticksUntilBenefit() {
        return 1;
    }

    @Override
    public ActivityLevel activityLevel() {
        return ActivityLevel.SLEEPING;
    }

    @Override
    public Exertion exertion() {
        return new Exertion(ActivityLevel.SLEEPING, comfort);
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        return String.format(
                Locale.ROOT,
                "woke at %s, energy %.0f",
                agent.location(),
                agent.needs().energy());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SleepAction sleep
                && ticks == sleep.ticks
                && Double.compare(comfort, sleep.comfort) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * ticks + Double.hashCode(comfort);
    }
}
