package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.craft.Recipe;
import dev.evoworld.core.goal.StayProvisionedGoal;
import dev.evoworld.core.item.ItemKind;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.resource.ResourceType;

/**
 * What a thing the agent could make would be worth to it.
 *
 * <p>One rule, used both by the offer to make something and by the offer to go
 * and fetch what making it needs, so the two can never disagree about what the
 * agent is after.
 *
 * <p>Two halves, and both matter. What the thing would <em>do</em> is a fact
 * about the agent's situation: a flask when you have nothing to carry water in
 * answers the whole of that want, and a second flask answers much less of it.
 * Whether it would do it at all is a <em>belief</em>, which starts as a hunch
 * and is settled by owning one. Nothing here says an axe is good.
 */
final class RecipeWorth {

    private RecipeWorth() {
    }

    /** What making this would be worth, from 0 to 1. */
    static double benefit(Recipe recipe, Perception perception) {
        return answers(recipe.makes(), perception)
                * perception.knowledge().believe(BeliefKind.USEFULNESS, recipe.makes().name()).estimate();
    }

    /** How sure the agent is that such a thing is worth having at all. */
    static double confidence(Recipe recipe, Perception perception) {
        return Math.max(
                perception.knowledge()
                        .believe(BeliefKind.USEFULNESS, recipe.makes().name())
                        .confidence(),
                UntestedGuess.CONFIDENCE);
    }

    /**
     * How much of what the agent lacks this kind of thing would answer.
     *
     * <p>A container is worth the supply it would let the agent carry that it
     * cannot carry now, so the first one is everything and one beyond what a
     * day needs is nothing. A tool is worth the work it takes off you.
     */
    private static double answers(ItemKind kind, Perception perception) {
        if (kind.holds().isPresent()) {
            ResourceType carried = kind.holds().orElseThrow();
            double room = perception.belongings().roomFor(carried);
            double enough = StayProvisionedGoal.ENOUGH;
            double reachNow = Math.min(room, enough);
            double reachThen = Math.min(room + kind.holdsUpTo(), enough);
            return (reachThen - reachNow) / enough;
        }
        if (kind.worksOn().isPresent()) {
            // Only worth something if the agent does not already have one.
            if (perception.belongings().toolFor(kind.worksOn().orElseThrow()).isPresent()) {
                return 0.0;
            }
            // What the work would cost with it, against what it costs without:
            // less effort per swing and more in the hand for each one.
            return Math.max(0.0, 1.0 - kind.effortMultiplier() / kind.yieldMultiplier());
        }
        return 0.0;
    }
}
