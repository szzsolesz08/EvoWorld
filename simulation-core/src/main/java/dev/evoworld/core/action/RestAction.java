package dev.evoworld.core.action;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Optional;

/**
 * Stay put for a breather.
 *
 * <p>Declares itself {@link ActivityLevel#RESTING}, and that is all it does: the
 * body recovers a little energy through the metabolism while it sits, and mends
 * faster than it would on the move. Real recovery is a night's sleep
 * ({@link SleepAction}); a rest is what an agent takes when it is not sleepy
 * but has nothing better to do, or is hurt.
 */
public final class RestAction extends Action {

    public static final String NAME = "REST";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ActivityLevel activityLevel() {
        return ActivityLevel.RESTING;
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        Needs needs = agent.needs();
        if (needs.energy() >= Needs.MAX && needs.isFullyHealthy()) {
            return Optional.of(Refusal.now("nothing to recover"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        return String.format(Locale.ROOT, "rested at %s", agent.location());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RestAction;
    }

    @Override
    public int hashCode() {
        return RestAction.class.hashCode();
    }
}
