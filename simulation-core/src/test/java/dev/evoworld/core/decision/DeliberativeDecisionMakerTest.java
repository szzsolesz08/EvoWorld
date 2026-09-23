package dev.evoworld.core.decision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.exploration.Region;
import dev.evoworld.core.goal.Motivation;
import dev.evoworld.core.goal.MotivationSystem;
import dev.evoworld.core.goal.RecoverHealthGoal;
import dev.evoworld.core.knowledge.BeliefKind;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.perception.PerceptionSystem;
import dev.evoworld.core.random.SimulationRandom;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** What the agent chooses, in a world it can see and a body it has to live in. */
class DeliberativeDecisionMakerTest {

    private static final SimulationConfig CONFIG = SimulationConfig.defaults(1L);
    private static final SimulationTime NOW = new SimulationTime(1L, Duration.ofMinutes(10));
    private static final Location MIDDLE = new Location(8, 8);

    private final DeliberativeDecisionMaker decisions = DeliberativeDecisionMaker.from(CONFIG);
    private final MotivationSystem motivations = MotivationSystem.standard();
    private final PerceptionSystem senses = new PerceptionSystem(CONFIG.perceptionRadius());

    /**
     * A world builder that reads like the situation it describes.
     *
     * <p>Agents here start <em>experienced</em>: every situation teaches them
     * what berries and water do before asking what they would choose. Without
     * that they would be deciding on hunches, which is a different question -
     * see {@code LearningChangesBehaviourTest} for that one.
     */
    private final class Situation {
        private final World world = new World(CONFIG.bounds());
        private Agent agent = new Agent(AgentId.of("agent-1"), "Atlas", MIDDLE, Needs.healthy());

        Situation() {
            teach(agent);
        }

        /** Everything an agent would know after a week of eating and drinking. */
        private void teach(Agent subject) {
            Knowledge learned = subject.knowledge();
            for (ResourceType type : ResourceType.values()) {
                for (int i = 0; i < 20; i++) {
                    learned = learned
                            .learned(BeliefKind.NOURISHMENT, type, type.nourishmentPerUnit())
                            .learned(BeliefKind.HYDRATION, type, type.hydrationPerUnit())
                            .learned(BeliefKind.LOCATION_RELIABILITY, type, 1.0);
                }
            }
            subject.updateKnowledge(learned);
        }

        Situation body(double hunger, double thirst, double energy) {
            return body(hunger, thirst, energy, Needs.MAX);
        }

        Situation body(double hunger, double thirst, double energy, double health) {
            Agent replacement = new Agent(
                    AgentId.of("agent-1"),
                    "Atlas",
                    agent.location(),
                    new Needs(hunger, thirst, energy, health));
            replacement.updateKnowledge(agent.knowledge());
            agent = replacement;
            return this;
        }

        /**
         * Has just walked every part of the country around it: nothing left to
         * find out. Around it, not the whole world - the world has no edges
         * any more, and an agent that had to see all of it would never be
         * anything but restless.
         */
        Situation knowingTheWorld() {
            int window = agent.exploration().regionsInView(agent.location());
            for (Region region :
                    agent.exploration().leastFamiliar(agent.location(), NOW.tick(), window)) {
                agent.exploration().sight(CONFIG.bounds(), region.centre(), NOW);
            }
            return this;
        }

        Situation standingOn(ResourceType type) {
            world.addResource(ResourceNode.full(type, agent.location()));
            return this;
        }

        Situation with(ResourceType type, int x, int y) {
            world.addResource(ResourceNode.full(type, new Location(x, y)));
            return this;
        }

        Situation carrying(ResourceType type, double amount) {
            agent.store(type, amount);
            return this;
        }

        Deliberation decide() {
            Perception perception = senses.perceive(agent, world, NOW);
            Motivation motivation = motivations.assess(perception);
            return decisions.deliberate(perception, motivation, SimulationRandom.seededWith(1L));
        }

        String chosenAction() {
            return decide().chosen().action().name();
        }
    }

    private Situation situation() {
        return new Situation();
    }

    // --- the behaviours the milestone asks for ----------------------------

    @Nested
    @DisplayName("thirsty")
    class Thirsty {

        @Test
        @DisplayName("with water underfoot, it drinks")
        void drinksWhenStandingOnWater() {
            assertEquals(
                    DrinkAction.NAME,
                    situation().body(0, 70, 100).standingOn(ResourceType.WATER).chosenAction());
        }

        @Test
        @DisplayName("with water in sight, it heads for it")
        void movesTowardsVisibleWater() {
            Deliberation deliberation =
                    situation().body(0, 70, 100).with(ResourceType.WATER, 4, 8).decide();

            assertEquals(MoveAction.NAME, deliberation.chosen().action().name());
            assertEquals(
                    new Location(4, 8),
                    ((MoveAction) deliberation.chosen().action()).destination());
        }

        @Test
        @DisplayName("drinking beats walking to a second spring, because it is here")
        void drinkingBeatsTravelling() {
            assertEquals(
                    DrinkAction.NAME,
                    situation()
                            .body(0, 70, 100)
                            .standingOn(ResourceType.WATER)
                            .with(ResourceType.WATER, 5, 8)
                            .chosenAction());
        }

        @Test
        @DisplayName("mild thirst is not worth interrupting anything for")
        void mildThirstDoesNotWin() {
            // Below the point where a need overtakes curiosity, an agent keeps
            // wandering - even standing in the water. Greedy scoring has no way
            // to notice that a free top-up now saves a walk later; that is what
            // planning would be for.
            assertFalse(
                    DrinkAction.NAME.equals(
                            situation()
                                    .body(0, 25, 100)
                                    .standingOn(ResourceType.WATER)
                                    .chosenAction()));
        }

        @Test
        @DisplayName("real thirst does send it travelling, mild thirst does not")
        void onlyRealThirstTravels() {
            String parched =
                    situation().body(0, 85, 100).with(ResourceType.WATER, 5, 8).chosenAction();
            Deliberation mild =
                    situation().body(0, 25, 100).with(ResourceType.WATER, 5, 8).decide();

            assertEquals(MoveAction.NAME, parched);
            assertFalse(
                    mild.chosen().rationale().contains("satisfy thirst"),
                    mild.chosen().rationale());
        }

        @Test
        @DisplayName("a satisfied agent does not drink, even standing in a spring")
        void doesNotDrinkWhenNotThirsty() {
            assertFalse(
                    DrinkAction.NAME.equals(
                            situation()
                                    .body(0, 0, 100)
                                    .standingOn(ResourceType.WATER)
                                    .chosenAction()));
        }
    }

    @Nested
    @DisplayName("hungry")
    class Hungry {

        @Test
        @DisplayName("with food in hand, it eats")
        void eatsWhatItCarries() {
            assertEquals(
                    EatAction.NAME,
                    situation()
                            .body(70, 0, 100)
                            .carrying(ResourceType.BERRIES, 10.0)
                            .chosenAction());
        }

        @Test
        @DisplayName("empty-handed on a bush, it gathers")
        void gathersWhenEmptyHanded() {
            assertEquals(
                    GatherAction.NAME,
                    situation().body(70, 0, 100).standingOn(ResourceType.BERRIES).chosenAction());
        }

        @Test
        @DisplayName("eating beats gathering once there is something to eat")
        void eatingBeatsGatheringWhenFed() {
            assertEquals(
                    EatAction.NAME,
                    situation()
                            .body(70, 0, 100)
                            .standingOn(ResourceType.BERRIES)
                            .carrying(ResourceType.BERRIES, 20.0)
                            .chosenAction());
        }

        @Test
        @DisplayName("with berries in sight, it heads for them")
        void movesTowardsVisibleFood() {
            assertEquals(
                    MoveAction.NAME,
                    situation().body(80, 0, 100).with(ResourceType.BERRIES, 6, 8).chosenAction());
        }

        @Test
        @DisplayName("it will not eat what is not food")
        void ignoresInedibleThings() {
            String chosen = situation()
                    .body(80, 0, 100)
                    .standingOn(ResourceType.STONE)
                    .carrying(ResourceType.STONE, 10.0)
                    .chosenAction();

            assertFalse(EatAction.NAME.equals(chosen));
            assertFalse(GatherAction.NAME.equals(chosen));
        }
    }

    @Nested
    @DisplayName("tired")
    class Tired {

        @Test
        @DisplayName("it sleeps")
        void sleepsWhenTired() {
            assertEquals(SleepAction.NAME, situation().body(0, 0, 15).chosenAction());
        }

        @Test
        @DisplayName("merely a little tired, it carries on: sleep is for the end of the day")
        void doesNotNapWhenALittleTired() {
            assertFalse(SleepAction.NAME.equals(situation().body(0, 0, 60).chosenAction()));
        }

        @Test
        @DisplayName("a rested agent does not sit around")
        void doesNotRestWhenFresh() {
            assertFalse(RestAction.NAME.equals(situation().body(0, 0, 100).chosenAction()));
        }

        @Test
        @DisplayName("too tired to walk anywhere, it sleeps rather than starting a trek")
        void exhaustionBeatsTravelling() {
            assertEquals(
                    SleepAction.NAME,
                    situation().body(60, 60, 4).with(ResourceType.WATER, 2, 8).chosenAction());
        }
    }

    @Nested
    @DisplayName("hurt")
    class Hurt {

        private List<String> restsFor(Situation situation) {
            return situation.decide().considered().stream()
                    .filter(decision -> decision.action().name().equals(RestAction.NAME))
                    .map(decision -> decision.goalName().orElse(""))
                    .toList();
        }

        @Test
        @DisplayName("badly hurt with nothing new to find, it lies down to mend rather than wander")
        void bedRestWhenIdle() {
            Deliberation hurt = situation().body(0, 0, 100, 10).knowingTheWorld().decide();
            Deliberation whole = situation().body(0, 0, 100, 100).knowingTheWorld().decide();

            assertEquals(SleepAction.NAME, hurt.chosen().action().name());
            assertEquals(RecoverHealthGoal.NAME, hurt.chosen().goalName().orElseThrow());
            assertEquals(MoveAction.NAME, whole.chosen().action().name(), "a whole agent wanders");
        }

        @Test
        @DisplayName("hurt in a world it has barely seen, it still goes looking - that is how it finds food")
        void curiosityStillWinsInANewWorld() {
            assertEquals(MoveAction.NAME, situation().body(0, 0, 100, 30).chosenAction());
        }

        @Test
        @DisplayName("resting to mend is only offered when resting would mend")
        void noHealingRestWhileNeglected() {
            assertTrue(restsFor(situation().body(0, 0, 100, 60)).contains(RecoverHealthGoal.NAME));
            assertFalse(restsFor(situation().body(70, 0, 100, 60)).contains(RecoverHealthGoal.NAME),
                    "a hungry agent heals nothing by sitting down");
            assertEquals(List.of(), restsFor(situation().body(0, 0, 100, 100)),
                    "whole and rested, there is nothing to rest for");
        }

        @Test
        @DisplayName("hurt and hungry, it goes for food first - that is what lets it heal")
        void foodBeforeRest() {
            Deliberation deliberation =
                    situation().body(80, 0, 100, 40).with(ResourceType.BERRIES, 8, 5).decide();

            assertTrue(deliberation.chosen().rationale().contains("hunger"),
                    deliberation.chosen().rationale());
        }
    }

    @Nested
    @DisplayName("nothing pressing")
    class Idle {

        @Test
        @DisplayName("a satisfied agent goes exploring")
        void exploresWhenSatisfied() {
            Deliberation deliberation = situation().body(0, 0, 100).decide();

            assertEquals(MoveAction.NAME, deliberation.chosen().action().name());
            assertTrue(deliberation.chosen().rationale().contains("explore"),
                    deliberation.chosen().rationale());
        }
    }

    // --- how the competition works ----------------------------------------

    @Nested
    @DisplayName("competition")
    class Competition {

        @Test
        @DisplayName("the more urgent need wins when both are available")
        void theStrongerNeedWins() {
            String chosen = situation()
                    .body(20, 90, 100)
                    .standingOn(ResourceType.WATER)
                    .carrying(ResourceType.BERRIES, 10.0)
                    .chosenAction();

            assertEquals(DrinkAction.NAME, chosen, "desperate thirst should beat mild hunger");
        }

        @Test
        @DisplayName("and the other way round, with no rule saying which")
        void andTheOtherWayRound() {
            String chosen = situation()
                    .body(90, 20, 100)
                    .standingOn(ResourceType.WATER)
                    .carrying(ResourceType.BERRIES, 10.0)
                    .chosenAction();

            assertEquals(EatAction.NAME, chosen, "desperate hunger should beat mild thirst");
        }

        @Test
        @DisplayName("a near option beats an equally good far one")
        void nearnessWins() {
            Deliberation deliberation = situation()
                    .body(0, 80, 100)
                    .with(ResourceType.WATER, 6, 8)
                    .with(ResourceType.WATER, 10, 8)
                    .decide();

            assertEquals(
                    new Location(6, 8),
                    ((MoveAction) deliberation.chosen().action()).destination(),
                    "two steps away should beat four");
        }

        @Test
        @DisplayName("every option is scored, not just the winner")
        void everyCandidateIsScored() {
            Deliberation deliberation = situation()
                    .body(50, 50, 20)
                    .standingOn(ResourceType.WATER)
                    .with(ResourceType.BERRIES, 6, 8)
                    .carrying(ResourceType.BERRIES, 10.0)
                    .decide();

            List<String> considered =
                    deliberation.considered().stream().map(d -> d.action().name()).toList();
            assertTrue(considered.contains(DrinkAction.NAME), considered.toString());
            assertTrue(considered.contains(EatAction.NAME), considered.toString());
            assertTrue(considered.contains(SleepAction.NAME), considered.toString());
            assertTrue(considered.contains(MoveAction.NAME), considered.toString());
            assertTrue(deliberation.considered().contains(deliberation.chosen()));
        }

        @Test
        @DisplayName("the winner is the highest-scoring option, and never negative")
        void theWinnerIsTheHighestScore() {
            Deliberation deliberation = situation().body(40, 60, 70).decide();

            double best = deliberation.considered().stream()
                    .mapToDouble(Decision::utility)
                    .max()
                    .orElseThrow();
            assertEquals(best, deliberation.chosen().utility());
            assertTrue(deliberation.considered().stream().allMatch(d -> d.utility() >= 0.0));
        }

        @Test
        @DisplayName("an agent with nothing it can usefully do still does something")
        void thereIsAlwaysSomethingToDo() {
            Deliberation deliberation = situation().body(0, 0, 100).decide();

            assertFalse(deliberation.considered().isEmpty());
        }
    }

    @Nested
    @DisplayName("guarantees")
    class Guarantees {

        @Test
        @DisplayName("the same state and the same seed give the same decision")
        void decisionsAreDeterministic() {
            assertEquals(
                    situation().body(45, 55, 65).with(ResourceType.WATER, 5, 8).decide(),
                    situation().body(45, 55, 65).with(ResourceType.WATER, 5, 8).decide());
        }

        @Test
        @DisplayName("an agent only ever chooses from what it can actually see")
        void choicesAreBoundedByPerception() {
            Location unseen = new Location(
                    MIDDLE.x() + CONFIG.perceptionRadius() + 4, MIDDLE.y());
            Deliberation deliberation = situation()
                    .body(0, 90, 100)
                    .with(ResourceType.WATER, unseen.x(), unseen.y())
                    .decide();

            // A thirsty agent with nothing in reach may well set off looking -
            // that is what thirst is for. What it must not do is walk to a
            // spring it has no way of knowing about.
            assertTrue(
                    deliberation.considered().stream()
                            .map(Decision::action)
                            .filter(action -> action instanceof MoveAction)
                            .noneMatch(action -> ((MoveAction) action).destination().equals(unseen)),
                    "unseen water must not be a destination: " + deliberation.considered());
        }

        @Test
        @DisplayName("the reason for a choice explains it, rather than restating it")
        void rationalesExplainTheChoice() {
            Decision chosen = situation()
                    .body(0, 80, 100)
                    .standingOn(ResourceType.WATER)
                    .decide()
                    .chosen();

            assertTrue(chosen.rationale().contains("satisfy thirst"), chosen.rationale());
            assertTrue(chosen.rationale().contains("want"), chosen.rationale());
            assertTrue(chosen.rationale().contains("gain"), chosen.rationale());
        }
    }
}
