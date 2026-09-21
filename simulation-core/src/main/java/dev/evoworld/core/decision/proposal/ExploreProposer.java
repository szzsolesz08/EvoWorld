package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.ExploreGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.UnexploredArea;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/**
 * Offers to go and look at the parts of the world the agent knows least.
 *
 * <p>This used to pick a tile at random, which is wandering rather than
 * exploring - an agent could cross the same ground a dozen times and never
 * reach a corner. Now it offers the frontier: the regions with the lowest
 * familiarity, each valued at how much there is left to find out there and
 * discounted, like everything else, by how far away it is.
 *
 * <p>Nothing marks a region as unexplored. The frontier is simply the regions
 * with the smallest numbers, and because familiarity fades, a map that has been
 * swept becomes worth sweeping again. The agent's touring of the world is
 * emergent, not scheduled.
 *
 * <p>A random target is still offered as a last resort, for the case where the
 * agent is standing in the middle of the only place it does not know.
 */
public final class ExploreProposer implements ActionProposer {

    /** How sure an agent is that unseen ground is worth the walk. */
    public static final double SPECULATIVE = 0.5;

    private final double moveEnergyCost;

    public ExploreProposer(double moveEnergyCost) {
        this.moveEnergyCost = moveEnergyCost;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        List<ActionCandidate> candidates = new ArrayList<>();
        for (UnexploredArea area : perception.exploration().frontier()) {
            if (area.stepsAway() == 0 || area.novelty() <= 0.0) {
                continue;
            }
            candidates.add(head(
                    area.centre(),
                    area.stepsAway(),
                    area.novelty(),
                    "unexplored " + area.describe()));
        }
        if (candidates.isEmpty()) {
            wander(perception, random).ifPresent(candidates::add);
        }
        return List.copyOf(candidates);
    }

    /** Somewhere, anywhere - for when the frontier has nothing left to offer. */
    private java.util.Optional<ActionCandidate> wander(
            Perception perception, SimulationRandom random) {
        WorldBounds bounds = perception.bounds();
        Location target =
                new Location(random.nextInt(bounds.width()), random.nextInt(bounds.height()));
        if (target.equals(perception.location())) {
            return java.util.Optional.empty();
        }
        int steps = perception.location().manhattanDistanceTo(target);
        return java.util.Optional.of(
                head(target, steps, 1.0, "nowhere in particular, " + target));
    }

    private ActionCandidate head(Location target, int steps, double novelty, String why) {
        return ActionCandidate.heading(
                target,
                new MoveAction(target, moveEnergyCost),
                ExploreGoal.NAME,
                novelty,
                SPECULATIVE,
                steps,
                steps * moveEnergyCost,
                why);
    }
}
