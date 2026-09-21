package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.resource.ResourceType;

/**
 * How much good the agent <em>believes</em> a quantity of something would do.
 *
 * <p>The single most important line in this milestone is what is missing: none
 * of these methods consults {@link ResourceType}. What a berry is actually
 * worth is a fact about the world, known to {@link
 * dev.evoworld.core.action.EatAction} when it runs. What the agent expects it
 * to be worth comes from {@link Knowledge}, and the two can disagree - which is
 * exactly what it means for an agent to be wrong about something, and what
 * gives learning anything to do.
 *
 * <p>Relief is capped by the need itself: a feast is worth no more than the
 * hunger it answers.
 */
final class ExpectedRelief {

    private ExpectedRelief() {
    }

    /**
     * As many units as the need could take. A trip is worth the meal at the
     * end of it, not one mouthful: nobody walks to a bush for a single berry.
     * The relief is capped by the need itself, so this only says "as much as
     * I want", and a remembered place is still doubted through its confidence.
     */
    static final double FILL_UP = Double.MAX_VALUE;

    /** What the agent expects eating {@code units} of something to do for hunger. */
    static double fromEating(
            Knowledge knowledge, Needs needs, ResourceType type, double units) {
        double perUnit = Math.max(knowledge.estimate(BeliefKind.NOURISHMENT, type), 0.0);
        return perUnit <= 0.0 ? 0.0 : Math.min(Math.min(units, Needs.MAX) * perUnit, needs.hunger()) / Needs.MAX;
    }

    /** What the agent expects drinking {@code units} of something to do for thirst. */
    static double fromDrinking(
            Knowledge knowledge, Needs needs, ResourceType type, double units) {
        double perUnit = Math.max(knowledge.estimate(BeliefKind.HYDRATION, type), 0.0);
        return perUnit <= 0.0 ? 0.0 : Math.min(Math.min(units, Needs.MAX) * perUnit, needs.thirst()) / Needs.MAX;
    }

    /** What a tick of rest would do for energy. No belief required: bodies are known. */
    static double fromResting(Needs needs, double energyGain) {
        return Math.min(energyGain, Needs.MAX - needs.energy()) / Needs.MAX;
    }

    /**
     * What stopping for a tick would do for health: the healing a tick at
     * rest brings beyond what carrying on would have brought anyway.
     */
    static double fromHealing(Needs needs, double extraHealing) {
        return Math.min(Math.max(extraHealing, 0.0), Needs.MAX - needs.health()) / Needs.MAX;
    }

    /** Whether the agent thinks this is worth putting in its mouth at all. */
    static boolean thinksItIsFood(Knowledge knowledge, ResourceType type) {
        return knowledge.estimate(BeliefKind.NOURISHMENT, type) > 0.0;
    }

    /** Whether the agent thinks this is worth drinking at all. */
    static boolean thinksItIsADrink(Knowledge knowledge, ResourceType type) {
        return knowledge.estimate(BeliefKind.HYDRATION, type) > 0.0;
    }
}
