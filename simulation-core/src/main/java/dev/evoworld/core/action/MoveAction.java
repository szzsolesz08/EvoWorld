package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Take a single grid step towards a destination, spending energy to do so.
 *
 * <p>The action carries the destination rather than a direction so the reason
 * for moving stays visible to logs and, later, to memory. One execution is one
 * tile: a journey is a sequence of moves, which is what the goals milestone
 * will string together.
 *
 * <p>The cost is a flat charge per step, so the energy a route takes is exactly
 * its length - no diagonal bargains, no rounding.
 */
public final class MoveAction extends Action {

    public static final String NAME = "MOVE";

    private final Location destination;
    private final double energyCostPerStep;
    private final int stepsPerTick;

    /**
     * @param stepsPerTick how far one tick's walking goes - one tile at the
     *     default tick length, more when ticks are longer
     */
    public MoveAction(Location destination, double energyCostPerStep, int stepsPerTick) {
        this.destination = Objects.requireNonNull(destination, "destination");
        if (energyCostPerStep < 0.0) {
            throw new IllegalArgumentException(
                    "movement cannot give energy back, was " + energyCostPerStep);
        }
        if (stepsPerTick < 1) {
            throw new IllegalArgumentException("a move takes at least one step, was " + stepsPerTick);
        }
        this.energyCostPerStep = energyCostPerStep;
        this.stepsPerTick = stepsPerTick;
    }

    /** One step a tick. */
    public MoveAction(Location destination, double energyCostPerStep) {
        this(destination, energyCostPerStep, 1);
    }

    public Location destination() {
        return destination;
    }

    public double energyCostPerStep() {
        return energyCostPerStep;
    }

    @Override
    public int stepsPerTick() {
        return stepsPerTick;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected Optional<Refusal> precondition(Agent agent, World world) {
        if (!world.bounds().contains(destination)) {
            return Optional.of(Refusal.never(destination + " is outside the world"));
        }
        if (agent.location().equals(destination)) {
            return Optional.of(Refusal.now("already at " + destination));
        }
        if (agent.needs().energy() < energyCostPerStep) {
            return Optional.of(Refusal.now(
                    String.format(
                            Locale.ROOT,
                            "too tired to move, %.1f energy left of the %.1f a step costs",
                            agent.needs().energy(),
                            energyCostPerStep)));
        }
        return Optional.empty();
    }

    @Override
    public boolean trainable() {
        return true;
    }

    @Override
    protected String effect(Agent agent, World world) {
        Location from = agent.location();
        // A practised walker spends less on the same step.
        double costPerStep = energyCostPerStep * agent.skills().effortMultiplier(NAME);
        for (int step = 0; step < stepsPerTick && !agent.location().equals(destination); step++) {
            if (step > 0 && agent.needs().energy() < costPerStep) {
                break;
            }
            agent.moveTo(agent.location().stepTowards(destination));
            agent.exert(costPerStep);
        }
        return "moved " + from + " -> " + agent.location() + " heading for " + destination;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MoveAction move
                && destination.equals(move.destination)
                && Double.compare(energyCostPerStep, move.energyCostPerStep) == 0
                && stepsPerTick == move.stepsPerTick;
    }

    @Override
    public int hashCode() {
        return Objects.hash(destination, energyCostPerStep, stepsPerTick);
    }

    @Override
    public String toString() {
        return NAME + " to " + destination;
    }
}
