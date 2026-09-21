package dev.evoworld.core.decision;

import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import java.util.Locale;
import java.util.Objects;

/**
 * Scores a candidate by multiplying what it is worth by what it costs.
 *
 * <p>Six factors, each a fraction between zero and one, multiplied together:
 *
 * <pre>
 *   utility = want x benefit x confidence x timeliness x affordability x (1 - risk)
 * </pre>
 *
 * <ul>
 *   <li><strong>want</strong> - how badly the agent wants the goal this serves.
 *       The only thing the goal layer contributes, and it contributes it
 *       without knowing an action exists.
 *   <li><strong>benefit</strong> - how much of that goal this would satisfy.
 *   <li><strong>confidence</strong> - how sure the agent is, which is where
 *       knowledge enters. Full for something it can see, half for a guess.
 *   <li><strong>timeliness</strong> - a close mediocre option beating a distant
 *       perfect one. Hyperbolic, so the first few steps cost the most.
 *   <li><strong>affordability</strong> - what the energy would cost out of what
 *       is left. A trip that costs half an agent's remaining energy is worth
 *       half as much.
 *   <li><strong>risk</strong> - how much health the undertaking would cost at
 *       the current rate of decline. An agent with something already killing it
 *       will not walk fifteen steps for a snack.
 * </ul>
 *
 * <p>Multiplying rather than adding means any factor that hits zero kills the
 * candidate outright, which is the right semantics: no want, no point; no
 * energy, no chance. Nothing here inspects what kind of action it is scoring -
 * everything it needs is on the candidate - so a new action needs no case here.
 */
public final class UtilityEvaluator implements ActionEvaluator {

    /** Steps at which timeliness halves. Small, so distance bites early. */
    public static final double PATIENCE = 4.0;

    private final MetabolismRates metabolism;
    private final double patience;

    public UtilityEvaluator(MetabolismRates metabolism) {
        this(metabolism, PATIENCE);
    }

    public UtilityEvaluator(MetabolismRates metabolism, double patience) {
        this.metabolism = Objects.requireNonNull(metabolism, "metabolism");
        if (patience <= 0.0) {
            throw new IllegalArgumentException("patience must be positive, was " + patience);
        }
        this.patience = patience;
    }

    @Override
    public Decision evaluate(ActionCandidate candidate, Motivation motivation, Perception perception) {
        double want = motivation.priorityOf(candidate.goalName());
        double benefit = candidate.expectedBenefit();
        double confidence = candidate.confidence();
        double timeliness = timeliness(candidate);
        double affordability = affordability(candidate, perception.needs());
        double risk = risk(candidate, perception.needs());

        double utility = want * benefit * confidence * timeliness * affordability * (1.0 - risk);

        return Decision.of(candidate, utility, explain(candidate, want, benefit, risk));
    }

    /** Worth less the longer the payoff takes to arrive. */
    private double timeliness(ActionCandidate candidate) {
        return 1.0 / (1.0 + candidate.ticksToBenefit() / patience);
    }

    /** Worth less the larger a share of what is left it would burn. */
    private double affordability(ActionCandidate candidate, Needs needs) {
        double cost = candidate.expectedEnergyCost();
        if (cost <= 0.0) {
            return 1.0;
        }
        if (needs.energy() <= Needs.MIN) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - cost / needs.energy());
    }

    /**
     * How much of the agent's health this would cost, given what is already
     * going wrong and how long the undertaking takes.
     *
     * <p>Projects the needs forward to when the benefit would land and charges
     * whatever damage they would be doing by then. An agent that is fine takes
     * no risk walking anywhere; an agent that is dying takes a great deal.
     */
    private double risk(ActionCandidate candidate, Needs needs) {
        int ticks = candidate.ticksToBenefit();
        Needs projected = new Needs(
                needs.hunger() + metabolism.hungerPerTick() * ticks,
                needs.thirst() + metabolism.thirstPerTick() * ticks,
                needs.energy() - candidate.expectedEnergyCost(),
                needs.health());

        double damagePerTick = 0.0;
        if (projected.isStarving()) {
            damagePerTick += metabolism.starvationDamage();
        }
        if (projected.isDehydrated()) {
            damagePerTick += metabolism.dehydrationDamage();
        }
        if (projected.isExhausted()) {
            damagePerTick += metabolism.exhaustionDamage();
        }
        if (damagePerTick <= 0.0 || needs.health() <= Needs.MIN) {
            return damagePerTick > 0.0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, damagePerTick * ticks / needs.health());
    }

    private String explain(ActionCandidate candidate, double want, double benefit, double risk) {
        String risky = risk > 0.0
                ? String.format(Locale.ROOT, ", risk %.2f", risk)
                : "";
        return String.format(
                Locale.ROOT,
                "%s (%s) want %.2f, gain %.2f, %d tick(s)%s",
                candidate.goalName().toLowerCase(Locale.ROOT).replace('_', ' '),
                candidate.rationale(),
                want,
                benefit,
                candidate.ticksToBenefit(),
                risky);
    }
}
