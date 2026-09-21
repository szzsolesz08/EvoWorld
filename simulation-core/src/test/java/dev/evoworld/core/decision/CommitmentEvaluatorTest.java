package dev.evoworld.core.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.goal.MotivationSystem;
import dev.evoworld.core.goal.SatisfyHungerGoal;
import dev.evoworld.core.goal.SatisfyThirstGoal;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Carrying on is worth a little more than starting over - and only a little. */
class CommitmentEvaluatorTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final String THIRST = SatisfyThirstGoal.NAME;
    private static final Location HERE = new Location(8, 8);
    private static final Location SPRING = new Location(2, 8);
    private static final Location OTHER_SPRING = new Location(14, 8);

    /** Scores every candidate the same, so only commitment tells them apart. */
    private static final ActionEvaluator FLAT = (candidate, motivation, perception) ->
            Decision.of(candidate, 0.5, "flat");

    private final CommitmentEvaluator committed = new CommitmentEvaluator(FLAT);

    private static Perception perceptionOf(Agent agent) {
        return new PerceptionSystem(CONFIG.perceptionRadius())
                .perceive(agent, new World(CONFIG.bounds()), new SimulationTime(1L, Duration.ofMinutes(10)));
    }

    private static Agent headingFor(Location target, Needs needs) {
        Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", HERE, needs);
        agent.commitTo(Intent.formed(THIRST, target, HERE, 0L));
        return agent;
    }

    private static ActionCandidate walkTo(Location target, String goal) {
        int steps = HERE.manhattanDistanceTo(target);
        return ActionCandidate.heading(
                target, new MoveAction(target, 1.0), goal, 0.5, 1.0, steps, steps, "walk");
    }

    private Decision score(ActionCandidate candidate, Agent agent) {
        Perception perception = perceptionOf(agent);
        Motivation motivation = MotivationSystem.standard().assess(perception);
        return committed.evaluate(candidate, motivation, perception);
    }

    @Test
    @DisplayName("an option that carries on the intent scores exactly (1 + commitment) times as much")
    void rewardsCarryingOn() {
        Decision decision = score(walkTo(SPRING, THIRST), headingFor(SPRING, Needs.healthy()));

        assertEquals(0.5 * (1.0 + CommitmentEvaluator.COMMITMENT), decision.utility(), 1e-12);
        assertTrue(decision.rationale().endsWith("(keeping on)"), decision.rationale());
    }

    @Test
    @DisplayName("somewhere else, another goal, or an action going nowhere is scored as it was")
    void leavesEverythingElseAlone() {
        Agent agent = headingFor(SPRING, Needs.healthy());

        assertEquals(0.5, score(walkTo(OTHER_SPRING, THIRST), agent).utility());
        assertEquals(0.5, score(walkTo(SPRING, SatisfyHungerGoal.NAME), agent).utility());
        assertEquals(0.5, score(
                ActionCandidate.here(new RestAction(1.0), THIRST, 0.5, 0.0, "rest"),
                agent).utility());
    }

    @Test
    @DisplayName("an agent that has not set out anywhere gets no bonus at all")
    void noIntentNoBonus() {
        Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", HERE, Needs.healthy());

        assertEquals(0.5, score(walkTo(SPRING, THIRST), agent).utility());
    }

    @Test
    @DisplayName("loyal, not stubborn: a trip that has become pointless still loses to a real need")
    void neverRescuesAWorthlessOption() {
        // Set out for water, but no longer thirsty - and starving.
        Agent agent = headingFor(SPRING, new Needs(90.0, 0.0, 100.0, Needs.MAX));
        Perception perception = perceptionOf(agent);
        Motivation motivation = MotivationSystem.standard().assess(perception);
        CommitmentEvaluator real =
                new CommitmentEvaluator(new UtilityEvaluator(CONFIG.metabolism()));

        Decision water = real.evaluate(walkTo(SPRING, THIRST), motivation, perception);
        Decision food = real.evaluate(
                walkTo(OTHER_SPRING, SatisfyHungerGoal.NAME), motivation, perception);

        assertEquals(0.0, water.utility(), "no want times any bonus is still nothing");
        assertTrue(food.utility() > water.utility());
    }

    @Test
    @DisplayName("refuses a negative commitment, which would be a bias against finishing things")
    void refusesNegativeCommitment() {
        assertThrows(IllegalArgumentException.class, () -> new CommitmentEvaluator(FLAT, -0.1));
    }
}
