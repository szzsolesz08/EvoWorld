package dev.evoworld.core.decision.proposal;

import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.ActionCandidate;
import dev.evoworld.core.decision.ActionProposer;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.UnexploredArea;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offers to go and look for something to eat or drink when there is none here.
 *
 * <p>Searching is not curiosity. Curiosity is what an agent does when nothing
 * is the matter, and it is silenced - rightly - by a need that is. But a
 * hungry agent standing where there is nothing to eat, nothing in its pack and
 * nothing it remembers nearby has exactly one thing it can do about its hunger,
 * and that is to go and look. Before this, such an agent shuttled between a
 * spring and a patch of empty ground - the only candidates on the table - until
 * it starved with its thirst quenched.
 *
 * <p>So the journey is offered under the goal of the need itself, not under
 * exploring. It heads for the same frontier {@link ExploreProposer} uses,
 * because unknown ground is where unfound food is; what differs is why, and
 * therefore how it is valued: at what the need could take if the search comes
 * off, tempered by how likely that is. Hence {@link ExploreProposer#SPECULATIVE}
 * for confidence - a hunch, the same one curiosity runs on.
 */
public final class GoLookingProposer implements ActionProposer {

    private final double moveEnergyCost;
    private final int stepsPerTick;
    private final int gatherTicks;

    public GoLookingProposer(double moveEnergyCost, int stepsPerTick, int gatherTicks) {
        this.moveEnergyCost = moveEnergyCost;
        this.stepsPerTick = stepsPerTick;
        this.gatherTicks = gatherTicks;
    }

    @Override
    public List<ActionCandidate> propose(Perception perception, SimulationRandom random) {
        List<ActionCandidate> candidates = new ArrayList<>();
        if (perception.needs().thirst() >= Needs.NOTICEABLE
                && nothingFor(perception, BeliefKind.HYDRATION)) {
            search(perception, SatisfyThirstGoal.NAME, perception.needs().thirst(), 0, candidates);
        }
        if (perception.needs().hunger() >= Needs.NOTICEABLE
                && nothingFor(perception, BeliefKind.NOURISHMENT)) {
            search(perception,
                    SatisfyHungerGoal.NAME,
                    perception.needs().hunger(),
                    gatherTicks + 1,
                    candidates);
        }
        return List.copyOf(candidates);
    }

    /**
     * Whether there is nothing at all the agent could turn to for a need:
     * nothing of the kind in view, none in the pack, and none remembered
     * anywhere near. Belief, not fact - what it has never tasted does not
     * count as food to it, and finding out is curiosity's job, not this one's.
     */
    private boolean nothingFor(Perception perception, BeliefKind kind) {
        Knowledge knowledge = perception.knowledge();
        for (ResourceType type : ResourceType.values()) {
            if (knowledge.estimate(kind, type) <= 0.0) {
                continue;
            }
            if (perception.inventory().quantityOf(type) > 0.0
                    || !perception.resourcesOfType(type).isEmpty()
                    || perception.bestRemembered(type).isPresent()) {
                return false;
            }
        }
        return true;
    }

    private void search(
            Perception perception,
            String goal,
            double pressure,
            int workAtTarget,
            List<ActionCandidate> candidates) {
        for (UnexploredArea area : perception.exploration().frontier()) {
            if (area.stepsAway() == 0) {
                continue;
            }
            candidates.add(ActionCandidate.heading(
                    area.centre(),
                    new MoveAction(area.centre(), moveEnergyCost, stepsPerTick),
                    goal,
                    // Worth the whole of the need: if the search finds anything
                    // at all, that is what it is worth. What makes it a gamble
                    // is the confidence, not a shaved-down benefit.
                    pressure / Needs.MAX,
                    ExploreProposer.SPECULATIVE,
                    area.stepsAway(),
                    area.stepsAway() * moveEnergyCost,
                    String.format(
                            Locale.ROOT,
                            "nothing here, looking in %s",
                            area.describe()),
                    workAtTarget));
        }
    }
}
