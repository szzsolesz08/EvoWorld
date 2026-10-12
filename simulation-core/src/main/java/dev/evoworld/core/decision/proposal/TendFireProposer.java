package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.TendFireAction;
import dev.evoworld.core.agent.Exposure;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.KeepWarmGoal;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Fire;
import java.util.List;
import java.util.Locale;

/**
 * Offers to light a fire, or to feed the one the agent is standing at.
 *
 * <p>Worth what the warmth would be worth: the share of the cold it would take
 * away, here, now. A fire in the middle of a mild afternoon is worth nothing
 * and is not offered; the same fire at four in the morning is worth most of
 * what the agent is suffering.
 *
 * <p>Sure of it, too - unlike a flask, there is nothing to find out about a
 * fire's warmth: the agent can feel it from where it stands the moment the
 * thing is lit. What it has to find out is whether the wood was worth
 * spending, and that it learns the way it learns everything else.
 */
public final class TendFireProposer implements ActionProposer {

    private final double energyCost;
    private final int ticks;

    public TendFireProposer(double energyCost, int ticks) {
        this.energyCost = energyCost;
        this.ticks = ticks;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        // The cold out there, not the warmth of the tile: an agent at its own
        // fire is warm, and that is exactly when the next log is worth laying.
        if (KeepWarmGoal.belowMild(perception) <= 0.0) {
            return List.of();
        }

        // Wood can only go on a fire the agent is standing at; one burning a
        // couple of steps away is warming it, but is not within reach.
        boolean burning = perception.isAtTheFire();
        double roomForMore = burning
                ? perception.fire().map(Fire::roomForFuel).orElse(0.0)
                : Fire.MOST_IT_HOLDS;
        double carried = perception.inventory().quantityOf(ResourceType.WOOD);
        // What the night needs, not the whole armful. A fire will take
        // everything it is given, and an agent that gave it everything had
        // nothing left to make a flask out of - the two wants were spending
        // the same wood, and the fire always got there first.
        double forTheNight = KeepWarmGoal.A_NIGHT * Fire.WOOD_PER_HOUR;
        double wood = Math.min(Math.min(carried, roomForMore), forTheNight);
        if (wood <= 0.0 || (!burning && carried < Fire.KINDLING)) {
            return List.of();
        }

        return List.of(new ActionCandidate(
                new TendFireAction(wood, energyCost, ticks),
                KeepWarmGoal.NAME,
                worthOfWarmth(perception, wood),
                1.0,
                0,
                energyCost,
                String.format(
                        Locale.ROOT,
                        "%s a fire with %.1f wood, %.1f degrees out",
                        burning ? "feeding" : "lighting",
                        wood,
                        perception.sky().temperature())));
    }

    /**
     * What this wood would be worth on the fire, from 0 to 1.
     *
     * <p>Two things at once: how much of the cold a fire answers at all - all
     * of it on a spring night, less of it in a winter this world has not had
     * yet - and how much of the night the wood would add that the fire does
     * not already have. Wood on a well-fed fire is wood wasted.
     */
    private static double worthOfWarmth(Perception perception, double wood) {
        double answered = Math.min(
                1.0, Fire.WARMTH / Math.max(Fire.WARMTH, KeepWarmGoal.belowMild(perception)));
        double hoursAdded = wood / Fire.WOOD_PER_HOUR;
        double nightCovered = Math.min(
                1.0, hoursAdded / KeepWarmGoal.A_NIGHT) * (1.0 - KeepWarmGoal.sheltered(perception));
        return answered * nightCovered;
    }
}
