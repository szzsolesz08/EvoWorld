package dev.evoworld.core.action;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Optional;

/**
 * Stay put and recover energy.
 *
 * <p>The only action that declares itself {@link ActivityLevel#RESTING}, which
 * is what makes it a net gain rather than a smaller loss.
 */
public final class RestAction extends Action {

    public static final String NAME = "REST";

    private final double energyGain;

    public RestAction(double energyGain) {
        if (energyGain < 0.0) {
            throw new IllegalArgumentException("rest cannot cost energy, was " + energyGain);
        }
        this.energyGain = energyGain;
    }

    public double energyGain() {
        return energyGain;
    }

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
        if (agent.needs().energy() >= Needs.MAX) {
            return Optional.of(Refusal.now("already fully rested"));
        }
        return Optional.empty();
    }

    @Override
    protected String effect(Agent agent, World world) {
        double before = agent.needs().energy();
        agent.rest(energyGain);
        double recovered = agent.needs().energy() - before;
        return String.format(
                Locale.ROOT, "rested at %s, recovered %.1f energy", agent.location(), recovered);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RestAction rest
                && Double.compare(energyGain, rest.energyGain) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(energyGain);
    }
}
