package dev.evoworld.core.decision;

import dev.evoworld.core.ActionSettings;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.decision.proposal.ApproachRememberedProposer;
import dev.evoworld.core.decision.proposal.ApproachResourceProposer;
import dev.evoworld.core.decision.proposal.DiscardProposer;
import dev.evoworld.core.decision.proposal.DrinkProposer;
import dev.evoworld.core.decision.proposal.EatProposer;
import dev.evoworld.core.decision.proposal.ExploreProposer;
import dev.evoworld.core.decision.proposal.GatherProposer;
import dev.evoworld.core.decision.proposal.InvestigateProposer;
import dev.evoworld.core.decision.proposal.RestProposer;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the decision pipeline: propose, evaluate, select.
 *
 * <p>Three collaborators and no logic of its own beyond the order it calls them
 * in. That is the point. There is no branch anywhere that says "if thirsty,
 * head for water" - there are proposers that offer what is possible, an
 * evaluator that scores everything against what the agent wants, and a selector
 * that takes the winner. Thirst beating curiosity is arithmetic, not a rule.
 *
 * <p>Adding a behaviour means adding a proposer to the list. Changing how an
 * agent thinks means swapping the {@link ActionEvaluator} or the
 * {@link ActionSelector}. Replacing deliberation entirely means implementing
 * {@link DecisionMaker}, and nothing here is in the way.
 */
public final class DeliberativeDecisionMaker implements DecisionMaker {

    private final List<ActionProposer> proposers;
    private final ActionEvaluator evaluator;
    private final ActionSelector selector;
    private final double fallbackRestGain;

    public DeliberativeDecisionMaker(
            List<ActionProposer> proposers,
            ActionEvaluator evaluator,
            ActionSelector selector,
            double fallbackRestGain) {
        Objects.requireNonNull(proposers, "proposers");
        if (proposers.isEmpty()) {
            throw new IllegalArgumentException("an agent needs at least one way to act");
        }
        this.proposers = List.copyOf(proposers);
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.fallbackRestGain = fallbackRestGain;
    }

    /** The MVP's agent: everything it can do, scored on utility. */
    public static DeliberativeDecisionMaker from(SimulationConfig config) {
        ActionSettings actions = config.actions();
        return new DeliberativeDecisionMaker(
                List.of(
                        new DrinkProposer(actions.drinkGulp()),
                        new EatProposer(actions.eatPortion()),
                        new GatherProposer(actions.gatherAmount(), actions.gatherEnergyCost()),
                        new ApproachResourceProposer(
                                actions.moveEnergyCost(),
                                actions.eatPortion(),
                                actions.drinkGulp()),
                        new ApproachRememberedProposer(
                                actions.moveEnergyCost(),
                                actions.eatPortion(),
                                actions.drinkGulp()),
                        new InvestigateProposer(actions.eatPortion()),
                        new DiscardProposer(actions.gatherAmount()),
                        new RestProposer(actions.restEnergyGain()),
                        new ExploreProposer(actions.moveEnergyCost())),
                new UtilityEvaluator(config.metabolism()),
                new HighestUtilitySelector(),
                actions.restEnergyGain());
    }

    @Override
    public Deliberation deliberate(
            Perception perception, Motivation motivation, SimulationRandom random) {
        List<ActionCandidate> candidates = propose(perception, random);
        if (candidates.isEmpty()) {
            return doNothingMuch();
        }

        List<Decision> scored = new ArrayList<>(candidates.size());
        for (ActionCandidate candidate : candidates) {
            scored.add(evaluator.evaluate(candidate, motivation, perception));
        }
        return new Deliberation(scored, selector.select(scored));
    }

    private List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        List<ActionCandidate> candidates = new ArrayList<>();
        for (ActionProposer proposer : proposers) {
            candidates.addAll(proposer.propose(perception, random));
        }
        return candidates;
    }

    /**
     * What an agent does when it can think of nothing at all - fully rested,
     * nothing in sight, standing on its own exploration target. Rare, but an
     * agent has to do something with its tick.
     */
    private Deliberation doNothingMuch() {
        Decision idle = new Decision(
                new RestAction(fallbackRestGain), 0.0, "nothing worth doing");
        return new Deliberation(List.of(idle), idle);
    }
}
