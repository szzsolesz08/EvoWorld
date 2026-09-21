package dev.evoworld.runner;

import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.decision.DecisionMaker;
import dev.evoworld.core.decision.Deliberation;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.random.SimulationRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Plays a fixed list of actions in order, one per decision.
 *
 * <p>Not a decision maker in any real sense - it decides nothing. It exists so
 * the runner can drive hand-picked actions through the real simulation and
 * watch what they do, which is how you check an action system without an AI to
 * exercise it. It lives in the runner for the same reason: it is a test
 * harness, not part of the world.
 *
 * <p>Once the script runs out the agent rests, so a run can outlast its script
 * without the simulation having to care. It ignores the motivation it is handed
 * entirely - a script wants nothing.
 */
public final class ScriptedDecisionMaker implements DecisionMaker {

    private final Deque<Action> remaining;
    private final Action whenFinished;
    private final int scriptLength;

    public ScriptedDecisionMaker(List<Action> script, double restEnergyGain) {
        this.remaining = new ArrayDeque<>(script);
        this.whenFinished = new RestAction(restEnergyGain);
        this.scriptLength = script.size();
    }

    public int stepsRemaining() {
        return remaining.size();
    }

    @Override
    public Deliberation deliberate(
            Perception perception, Motivation motivation, SimulationRandom random) {
        boolean scripted = !remaining.isEmpty();
        Action next = scripted ? remaining.removeFirst() : whenFinished;
        String rationale = scripted
                ? "scripted step " + (scriptLength - remaining.size()) + " of " + scriptLength
                : "script finished, idling";

        // One candidate, because a script considers nothing.
        Decision only = new Decision(next, 1.0, rationale);
        return new Deliberation(List.of(only), only);
    }
}
