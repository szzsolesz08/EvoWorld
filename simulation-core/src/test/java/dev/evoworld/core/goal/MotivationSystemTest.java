package dev.evoworld.core.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.perception.ExplorationView;
import dev.evoworld.core.perception.Perception;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MotivationSystemTest {

    private static final double EPSILON = 1e-9;
    private static final SimulationTime NOW = new SimulationTime(1L, Duration.ofMinutes(10));
    private static final WorldBounds BOUNDS = WorldBounds.of(16, 16);

    private final MotivationSystem motivations = MotivationSystem.standard();

    /** An agent in a given bodily state, alone in an empty field. */
    private Perception perceiving(Needs needs) {
        AgentSnapshot self = new AgentSnapshot(
                AgentId.of("agent-1"),
                "Atlas",
                new Location(8, 8),
                needs,
                Inventory.empty(), Belongings.nothing(),
                0L,
                needs.isAlive());
        return new Perception(
                NOW,
                self,
                BOUNDS,
                6,
                List.of(),
                List.of(),
                List.of(),
                Knowledge.innocent(),
                ExplorationView.NOTHING_SEEN);
    }

    private Motivation assess(Needs needs) {
        return motivations.assess(perceiving(needs));
    }

    private static Needs needs(double hunger, double thirst, double energy) {
        return new Needs(hunger, thirst, energy, Needs.MAX);
    }

    private double priorityOf(String goal, Needs needs) {
        return assess(needs).priorityOf(goal);
    }

    // --- the shape of each want -------------------------------------------

    @Nested
    @DisplayName("priorities follow the state")
    class Priorities {

        @Test
        @DisplayName("a satisfied agent wants nothing but to look around")
        void aSatisfiedAgentOnlyWantsToExplore() {
            Motivation motivation = assess(Needs.healthy());

            assertEquals(ExploreGoal.NAME, motivation.dominant().name());
            assertEquals(0.0, motivation.priorityOf(SatisfyHungerGoal.NAME), EPSILON);
            assertEquals(0.0, motivation.priorityOf(SatisfyThirstGoal.NAME), EPSILON);
            assertEquals(0.0, motivation.priorityOf(RecoverEnergyGoal.NAME), EPSILON);
            assertEquals(0.0, motivation.priorityOf(RecoverHealthGoal.NAME), EPSILON);
        }

        @Test
        @DisplayName("very high thirst produces a very strong thirst goal")
        void thirstDrivesItsGoal() {
            double mild = priorityOf(SatisfyThirstGoal.NAME, needs(0, 20, 100));
            double severe = priorityOf(SatisfyThirstGoal.NAME, needs(0, 90, 100));

            assertTrue(severe > mild, severe + " should beat " + mild);
            assertEquals(1.0, priorityOf(SatisfyThirstGoal.NAME, needs(0, 100, 100)), EPSILON);
        }

        @Test
        @DisplayName("rising hunger raises the want for food")
        void hungerDrivesItsGoal() {
            double mild = priorityOf(SatisfyHungerGoal.NAME, needs(20, 0, 100));
            double severe = priorityOf(SatisfyHungerGoal.NAME, needs(90, 0, 100));

            assertTrue(severe > mild);
            assertEquals(0.0, priorityOf(SatisfyHungerGoal.NAME, needs(0, 0, 100)), EPSILON);
        }

        @Test
        @DisplayName("low energy raises the want to rest")
        void lowEnergyDrivesTheRestGoal() {
            double rested = priorityOf(RecoverEnergyGoal.NAME, needs(0, 0, 90));
            double weary = priorityOf(RecoverEnergyGoal.NAME, needs(0, 0, 20));

            assertTrue(weary > rested, weary + " should beat " + rested);
            assertEquals(1.0, priorityOf(RecoverEnergyGoal.NAME, needs(0, 0, 0)), EPSILON);
        }

        @Test
        @DisplayName("exploring fades as energy does, rather than as needs grow")
        void exploringFollowsEnergy() {
            double fresh = priorityOf(ExploreGoal.NAME, needs(0, 0, 100));
            double tired = priorityOf(ExploreGoal.NAME, needs(0, 0, 25));

            assertEquals(ExploreGoal.DEFAULT_CURIOSITY, fresh, EPSILON);
            assertTrue(tired < fresh);
            assertEquals(0.0, priorityOf(ExploreGoal.NAME, needs(0, 0, 0)), EPSILON);
        }

        @Test
        @DisplayName("injury raises the want to mend, on the same curve as tiredness")
        void injuryDrivesTheHealthGoal() {
            double scratched = priorityOf(RecoverHealthGoal.NAME, new Needs(0, 0, 100, 90));
            double halfDead = priorityOf(RecoverHealthGoal.NAME, new Needs(0, 0, 100, 50));

            assertEquals(0.01, scratched, EPSILON);
            assertEquals(0.25, halfDead, EPSILON);
            assertEquals(0.0, priorityOf(RecoverHealthGoal.NAME, needs(0, 0, 100)), EPSILON);
        }

        @Test
        @DisplayName("injury does not quiet curiosity: exploring is how an agent finds food")
        void injuryLeavesCuriosityAlone() {
            // Tried and measured: counting injury against curiosity starved
            // three agents in twenty that started hurt and did not yet know
            // where anything was.
            assertEquals(
                    priorityOf(ExploreGoal.NAME, needs(0, 0, 100)),
                    priorityOf(ExploreGoal.NAME, new Needs(0, 0, 100, 30)),
                    EPSILON);
        }

        @ParameterizedTest
        @ValueSource(doubles = {0, 10, 25, 50, 75, 90, 100})
        @DisplayName("every want rises with its need and never falls")
        void prioritiesAreMonotonic(double level) {
            double thirstHere = priorityOf(SatisfyThirstGoal.NAME, needs(0, level, 100));
            double thirstWorse = priorityOf(SatisfyThirstGoal.NAME, needs(0, level + 5, 100));
            double restHere = priorityOf(RecoverEnergyGoal.NAME, needs(0, 0, level));
            double restWorse = priorityOf(RecoverEnergyGoal.NAME, needs(0, 0, level - 5));

            assertTrue(thirstWorse >= thirstHere, "thirst at " + level);
            assertTrue(restWorse >= restHere, "energy at " + level);
            double healHere = priorityOf(RecoverHealthGoal.NAME, new Needs(0, 0, 100, level));
            double healWorse = priorityOf(RecoverHealthGoal.NAME, new Needs(0, 0, 100, level - 5));
            assertTrue(healWorse >= healHere, "health at " + level);
        }

        @Test
        @DisplayName("urgency accelerates: the second half of a need matters more than the first")
        void urgencyAccelerates() {
            double firstHalf = priorityOf(SatisfyThirstGoal.NAME, needs(0, 50, 100));
            double secondHalf = priorityOf(SatisfyThirstGoal.NAME, needs(0, 100, 100)) - firstHalf;

            // The appetite makes the first half count for something - a mild
            // thirst is worth a drink - but the urgency still dominates the end.
            assertTrue(
                    secondHalf > firstHalf,
                    "going from half to fully thirsty should matter more than the first half");
        }

        @Test
        @DisplayName("a mild need is wanted a little: appetite comes long before danger")
        void appetiteComesEarly() {
            double mildThirst = priorityOf(SatisfyThirstGoal.NAME, needs(0, 20, 100));
            double mildHunger = priorityOf(SatisfyHungerGoal.NAME, needs(20, 0, 100));

            assertTrue(mildThirst > 0.05, "thirst at 20 should be felt: " + mildThirst);
            assertTrue(mildHunger > 0.1, "hunger at 20 should be felt: " + mildHunger);
            assertEquals(1.0, priorityOf(SatisfyHungerGoal.NAME, needs(100, 0, 100)), EPSILON);
        }
    }

    // --- how wants compete ------------------------------------------------

    @Nested
    @DisplayName("ranking")
    class Ranking {

        @Test
        @DisplayName("the strongest want comes first")
        void goalsAreRankedByPriority() {
            Motivation motivation = assess(needs(30, 85, 60));

            List<String> order = motivation.goals().stream().map(GoalPriority::name).toList();
            assertEquals(SatisfyThirstGoal.NAME, order.get(0));
            assertTrue(
                    motivation.goals().get(0).priority() >= motivation.goals().get(1).priority());
            assertTrue(
                    motivation.goals().get(1).priority() >= motivation.goals().get(2).priority());
        }

        @Test
        @DisplayName("a desperate need outranks a mild one, whichever need it is")
        void desperationWins() {
            assertEquals(SatisfyThirstGoal.NAME, assess(needs(30, 95, 100)).dominant().name());
            assertEquals(SatisfyHungerGoal.NAME, assess(needs(95, 30, 100)).dominant().name());
            assertEquals(RecoverEnergyGoal.NAME, assess(needs(30, 30, 5)).dominant().name());
        }

        @Test
        @DisplayName("curiosity holds until a need grows past it, then loses for good")
        void exploringYieldsToRealNeeds() {
            assertEquals(ExploreGoal.NAME, assess(needs(10, 10, 100)).dominant().name());
            assertEquals(ExploreGoal.NAME, assess(needs(10, 55, 100)).dominant().name());
            assertEquals(SatisfyThirstGoal.NAME, assess(needs(10, 70, 100)).dominant().name());
        }

        @Test
        @DisplayName("the crossover is where the curves meet, not where a rule puts it")
        void curiosityIsOvertakenWhereTheCurvesMeet() {
            // Thirst a*t + (1-a)*t^2 (appetite share a, appetite spread over the
            // whole bar) meets curiosity c*(1 - t^2) at the positive root of
            // (1 - a + c) t^2 + a t - c = 0: the point is set by the curves,
            // not by a threshold anyone wrote.
            double c = ExploreGoal.DEFAULT_CURIOSITY;
            double a = Appetite.APPETITE_SHARE;
            double quadratic = 1.0 - a + c;
            double root = (-a + Math.sqrt(a * a + 4.0 * quadratic * c)) / (2.0 * quadratic);
            assertEquals(Needs.MAX, SatisfyThirstGoal.COMFORTABLE_UP_TO, EPSILON,
                    "the closed form assumes thirst's appetite spans the whole bar");
            double crossover = root * Needs.MAX;

            assertEquals(
                    ExploreGoal.NAME, assess(needs(0, crossover - 1, 100)).dominant().name());
            assertEquals(
                    SatisfyThirstGoal.NAME,
                    assess(needs(0, crossover + 1, 100)).dominant().name());
        }

        @Test
        @DisplayName("every want is weighed every time, not just the winner")
        void everyGoalIsWeighed() {
            Motivation motivation = assess(new Needs(40, 40, 40, 60));

            assertEquals(7, motivation.goals().size());
            assertEquals(6, motivation.wanted().size(),
                    "all but keeping warm, which a mild afternoon does not ask for");
            assertTrue(motivation.goals().contains(motivation.dominant()));
        }

        @Test
        @DisplayName("a goal at zero is still reported, just not wanted")
        void unwantedGoalsAreStillReported() {
            Motivation motivation = assess(Needs.healthy());

            assertEquals(7, motivation.goals().size());
            assertEquals(2, motivation.wanted().size(),
                    "a healthy agent wants to look around, and to have water with it");
            assertFalse(motivation.forGoal(SatisfyHungerGoal.NAME).orElseThrow().isWanted());
        }

        @Test
        @DisplayName("equal wants fall back on registration order, so survival beats comfort")
        void tiesResolveByRegistrationOrder() {
            // At the top of both bars both wants are total, so they tie, and
            // the tie goes to the goal registered first.
            Motivation motivation = assess(needs(100, 100, 100));

            assertEquals(
                    motivation.priorityOf(SatisfyHungerGoal.NAME),
                    motivation.priorityOf(SatisfyThirstGoal.NAME),
                    EPSILON);
            assertEquals(SatisfyThirstGoal.NAME, motivation.dominant().name());
        }
    }

    // --- guarantees -------------------------------------------------------

    @Nested
    @DisplayName("guarantees")
    class Guarantees {

        @Test
        @DisplayName("the same state always produces the same wants")
        void assessmentIsDeterministic() {
            Needs state = needs(37, 81, 19);

            assertEquals(assess(state), assess(state));
            assertEquals(assess(state), MotivationSystem.standard().assess(perceiving(state)));
        }

        @ParameterizedTest
        @ValueSource(doubles = {0, 1, 33, 67, 99, 100})
        @DisplayName("no want can escape the range, whatever the state")
        void prioritiesStayInRange(double level) {
            Motivation motivation = assess(needs(level, level, level));

            for (GoalPriority scored : motivation.goals()) {
                assertTrue(scored.priority() >= Goal.MIN_PRIORITY, scored.toString());
                assertTrue(scored.priority() <= Goal.MAX_PRIORITY, scored.toString());
            }
        }

        @Test
        @DisplayName("a goal that tries to shout louder than the scale is clamped")
        void runawayPrioritiesAreClamped() {
            Goal shouty = new Goal() {
                @Override
                public String name() {
                    return "SHOUTY";
                }

                @Override
                protected double computePriority(Perception perception) {
                    return 1000.0;
                }

                @Override
                protected String reasonFrom(Perception perception) {
                    return "wants everything";
                }
            };

            assertEquals(Goal.MAX_PRIORITY, shouty.assess(perceiving(Needs.healthy())).priority());
        }

        @Test
        @DisplayName("goals say what the agent wants, never how to get it")
        void goalsCarryNoPlan() {
            Motivation motivation = assess(needs(90, 90, 10));

            // Nothing in a motivation names a place, an action or a resource.
            String rendered = motivation.toString();
            assertFalse(rendered.contains("MOVE"), rendered);
            assertFalse(rendered.contains("berries"), rendered);
            assertFalse(rendered.contains("("  + 8 + ","), rendered);
        }

        @Test
        @DisplayName("the assessment reads clearly in a log")
        void readsClearlyInALog() {
            String rendered = assess(needs(0, 90, 100)).toString();

            assertTrue(rendered.startsWith("wants SATISFY_THIRST 0.85 (thirst at 90.0)"), rendered);
        }

        @Test
        void rejectsAnAgentWithNoGoals() {
            assertThrows(IllegalArgumentException.class, () -> new MotivationSystem(List.of()));
        }

        @Test
        void rejectsImpossiblePriorities() {
            Goal goal = new ExploreGoal();

            assertThrows(
                    IllegalArgumentException.class, () -> new GoalPriority(goal, 1.5, "too keen"));
            assertThrows(
                    IllegalArgumentException.class, () -> new GoalPriority(goal, -0.1, "too glum"));
            assertThrows(IllegalArgumentException.class, () -> new ExploreGoal(2.0));
        }
    }
}
