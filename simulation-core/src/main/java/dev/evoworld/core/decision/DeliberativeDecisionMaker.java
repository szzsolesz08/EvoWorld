package dev.evoworld.core.decision;

import dev.evoworld.core.ActionSettings;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.decision.proposal.ApproachRememberedProposer;
import dev.evoworld.core.decision.proposal.ApproachResourceProposer;
import dev.evoworld.core.decision.proposal.DiscardProposer;
import dev.evoworld.core.decision.proposal.DrinkProposer;
import dev.evoworld.core.decision.proposal.EatProposer;
import dev.evoworld.core.decision.proposal.ExploreProposer;
import dev.evoworld.core.decision.proposal.CraftProposer;
import dev.evoworld.core.decision.proposal.FetchMaterialsProposer;
import dev.evoworld.core.decision.proposal.GatherProposer;
import dev.evoworld.core.decision.proposal.GoLookingProposer;
import dev.evoworld.core.decision.proposal.HeadForPlentyProposer;
import dev.evoworld.core.decision.proposal.InvestigateProposer;
import dev.evoworld.core.decision.proposal.RestProposer;
import dev.evoworld.core.decision.proposal.SleepProposer;
import dev.evoworld.core.decision.proposal.StockUpProposer;
import dev.evoworld.core.decision.proposal.TendFireProposer;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.time.Duration;
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

    /** The longest single sleep: a long night. */
    public static final Duration LONGEST_SLEEP = Duration.ofHours(10);

    public DeliberativeDecisionMaker(
            List<ActionProposer> proposers,
            ActionEvaluator evaluator,
            ActionSelector selector) {
        Objects.requireNonNull(proposers, "proposers");
        if (proposers.isEmpty()) {
            throw new IllegalArgumentException("an agent needs at least one way to act");
        }
        this.proposers = List.copyOf(proposers);
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    /** The standard agent: everything it can do, scored on utility. */
    public static DeliberativeDecisionMaker from(SimulationConfig config) {
        ActionSettings actions = config.actions();
        Duration tick = config.tickDuration();
        // Everything here works one tick at a time, so it is given one tick's
        // worth of the body's rates, and walking and work measured in ticks.
        MetabolismRates body = config.metabolism().perTick(tick);
        int steps = actions.stepsPerTick(tick);
        int nightTicks = (int) Math.max(1L, (LONGEST_SLEEP.getSeconds() + tick.getSeconds() - 1) / tick.getSeconds());
        return new DeliberativeDecisionMaker(
                List.of(
                        new DrinkProposer(actions.drinkGulp()),
                        new EatProposer(actions.eatPortion()),
                        new GatherProposer(
                                actions.gatherAmount(), actions.gatherEnergyCost(), actions.gatherTicks(tick)),
                        new ApproachResourceProposer(actions.moveEnergyCost(), steps, actions.gatherTicks(tick)),
                        new ApproachRememberedProposer(actions.moveEnergyCost(), steps, actions.gatherTicks(tick)),
                        new HeadForPlentyProposer(
                                actions.moveEnergyCost(), steps, actions.gatherTicks(tick)),
                        new InvestigateProposer(actions.eatPortion(), actions.moveEnergyCost(), steps),
                        new CraftProposer(tick),
                        new TendFireProposer(
                                actions.gatherEnergyCost(), actions.gatherTicks(tick)),
                        new FetchMaterialsProposer(
                                actions.gatherAmount(), actions.gatherEnergyCost(),
                                actions.gatherTicks(tick), actions.moveEnergyCost(), steps),
                        new StockUpProposer(
                                actions.gatherAmount(), actions.gatherEnergyCost(), actions.gatherTicks(tick)),
                        new DiscardProposer(actions.gatherAmount()),
                        new RestProposer(body),
                        new SleepProposer(body, nightTicks),
                        new ExploreProposer(actions.moveEnergyCost(), steps),
                        new GoLookingProposer(
                                actions.moveEnergyCost(), steps, actions.gatherTicks(tick))),
                new CommitmentEvaluator(new UtilityEvaluator(body)),
                new HighestUtilitySelector());
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
                new RestAction(), 0.0, "nothing worth doing");
        return new Deliberation(List.of(idle), idle);
    }
}
