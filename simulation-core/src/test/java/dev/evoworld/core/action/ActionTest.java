package dev.evoworld.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Every action, succeeding and being blocked, on its own. */
class ActionTest {

    private static final double EPSILON = 1e-9;
    private static final Location MIDDLE = new Location(4, 4);

    private World world() {
        return new World(WorldBounds.of(8, 8));
    }

    private Agent agentAt(Location location, Needs needs) {
        return new Agent(AgentId.of("agent-1"), "Atlas", location, needs);
    }

    private Agent hungryAndThirsty() {
        return agentAt(MIDDLE, new Needs(50.0, 50.0, 80.0, 100.0));
    }

    // --- MOVE -------------------------------------------------------------

    @Nested
    @DisplayName("Move")
    class Move {

        @Test
        @DisplayName("at longer ticks one move covers several tiles, paying for each")
        void severalStepsInATick() {
            Agent agent = agentAt(new Location(0, 4), new Needs(0.0, 0.0, 80.0, 100.0));

            ActionOutcome outcome = new MoveAction(new Location(7, 4), 1.0, 3).execute(agent, world());

            assertTrue(outcome.succeeded());
            assertEquals(new Location(3, 4), agent.location());
            assertEquals(77.0, agent.needs().energy(), EPSILON);
        }

        @Test
        @DisplayName("and stops at the destination rather than walking past it")
        void stopsWhereItWasGoing() {
            Agent agent = agentAt(new Location(0, 4), new Needs(0.0, 0.0, 80.0, 100.0));

            new MoveAction(new Location(2, 4), 1.0, 6).execute(agent, world());

            assertEquals(new Location(2, 4), agent.location());
            assertEquals(78.0, agent.needs().energy(), EPSILON);
        }

        @Test
        @DisplayName("steps one tile towards the destination and spends energy")
        void movesAndSpendsEnergy() {
            Agent agent = hungryAndThirsty();
            World world = world();

            ActionOutcome outcome = new MoveAction(new Location(7, 4), 1.5).execute(agent, world);

            assertTrue(outcome.succeeded(), outcome.detail());
            assertEquals(new Location(5, 4), agent.location());
            assertEquals(78.5, agent.needs().energy(), EPSILON);
            assertEquals(-1.5, outcome.change().energyChange(), EPSILON);
            assertTrue(outcome.change().moved());
        }

        @Test
        @DisplayName("the cost is the same for every step, so a route costs its length")
        void movementCostIsDeterministic() {
            Agent agent = agentAt(new Location(0, 0), Needs.healthy());
            World world = world();
            MoveAction move = new MoveAction(new Location(5, 0), 1.5);

            for (int i = 0; i < 5; i++) {
                move.execute(agent, world);
            }

            assertEquals(new Location(5, 0), agent.location());
            assertEquals(Needs.MAX - 5 * 1.5, agent.needs().energy(), EPSILON);
        }

        @Test
        @DisplayName("blocked when already there")
        void blockedAtTheDestination() {
            Agent agent = hungryAndThirsty();

            ActionOutcome outcome = new MoveAction(MIDDLE, 1.5).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.detail().contains("already at"), outcome.detail());
            assertTrue(outcome.change().isNothing());
        }

        @Test
        @DisplayName("blocked when the destination is off the map")
        void blockedOutsideTheWorld() {
            Agent agent = hungryAndThirsty();

            ActionOutcome outcome = new MoveAction(new Location(99, 99), 1.5).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertEquals(MIDDLE, agent.location(), "an agent must never leave the world");
        }

        @Test
        @DisplayName("blocked when there is not enough energy for a step")
        void blockedWhenTooTired() {
            Agent agent = agentAt(MIDDLE, new Needs(0.0, 0.0, 1.0, 100.0));

            ActionOutcome outcome = new MoveAction(new Location(7, 4), 1.5).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.detail().contains("too tired"), outcome.detail());
            assertEquals(MIDDLE, agent.location());
            assertEquals(1.0, agent.needs().energy(), EPSILON);
        }

        @Test
        void movingIsActiveWork() {
            assertEquals(ActivityLevel.ACTIVE, new MoveAction(MIDDLE, 1.0).activityLevel());
            assertEquals(1, new MoveAction(MIDDLE, 1.0).durationTicks());
        }
    }

    // --- REST -------------------------------------------------------------

    @Nested
    @DisplayName("Sleep")
    class Sleep {

        @Test
        @DisplayName("lasts as long as it was asked to, and counts from its first tick")
        void lastsAndCountsAtOnce() {
            SleepAction sleep = new SleepAction(45);

            assertEquals(45, sleep.durationTicks());
            assertEquals(1, sleep.ticksUntilBenefit());
            assertEquals(ActivityLevel.SLEEPING, sleep.exertion().level());
            assertEquals(1.0, sleep.exertion().comfort());
        }

        @Test
        @DisplayName("a bed is a sleep with more comfort")
        void carriesItsComfort() {
            assertEquals(1.5, new SleepAction(45, 1.5).exertion().comfort());
        }

        @Test
        @DisplayName("is never refused: whether to sleep is the mind's question, not the world's")
        void neverRefused() {
            Agent rested = agentAt(MIDDLE, Needs.healthy());

            assertTrue(new SleepAction(6).execute(rested, world()).succeeded());
        }
    }

    @Nested
    @DisplayName("Rest")
    class Rest {

        @Test
        @DisplayName("a breather changes nothing by itself: the body does the recovering, tick by tick")
        void restIsABreather() {
            Agent agent = agentAt(MIDDLE, new Needs(0.0, 0.0, 50.0, 100.0));

            ActionOutcome outcome = new RestAction().execute(agent, world());

            assertTrue(outcome.succeeded());
            assertTrue(outcome.change().isNothing(), "the metabolism charges a rest, the action does not");
            assertEquals(ActivityLevel.RESTING, new RestAction().activityLevel());
        }

        @Test
        @DisplayName("blocked when there is nothing to recover")
        void blockedWhenFullyRested() {
            Agent agent = agentAt(MIDDLE, Needs.healthy());

            ActionOutcome outcome = new RestAction().execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.change().isNothing());
        }

        @Test
        @DisplayName("a wounded agent may rest even when it is not tired")
        void allowedWhenHurtButRested() {
            Agent agent = agentAt(MIDDLE, new Needs(0.0, 0.0, 100.0, 60.0));

            ActionOutcome outcome = new RestAction().execute(agent, world());

            assertTrue(outcome.succeeded());
        }

        @Test
        @DisplayName("resting is the one action that is not work")
        void restingIsNotWork() {
            assertEquals(ActivityLevel.RESTING, new RestAction().activityLevel());
        }
    }

    // --- GATHER -----------------------------------------------------------

    @Nested
    @DisplayName("Gather")
    class Gather {

        private World worldWithBerries(double quantity) {
            World world = world();
            world.addResource(ResourceNode.holding(ResourceType.BERRIES, MIDDLE, quantity));
            return world;
        }

        @Test
        @DisplayName("moves resources from the node into the inventory")
        void gathersIntoTheInventory() {
            Agent agent = hungryAndThirsty();
            World world = worldWithBerries(12.0);

            ActionOutcome outcome = new GatherAction(ResourceType.BERRIES, 5.0, 1.0).execute(agent, world);

            assertTrue(outcome.succeeded(), outcome.detail());
            assertEquals(5.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
            assertEquals(7.0, world.resourceAt(MIDDLE).orElseThrow().quantity(), EPSILON);
            assertEquals(5.0, outcome.change().carriedChange(), EPSILON);
        }

        @Test
        @DisplayName("nothing is created or destroyed in the move")
        void conservesTheResource() {
            Agent agent = hungryAndThirsty();
            World world = worldWithBerries(12.0);
            double totalBefore = world.resourceAt(MIDDLE).orElseThrow().quantity();

            new GatherAction(ResourceType.BERRIES, 5.0, 1.0).execute(agent, world);

            double totalAfter = world.resourceAt(MIDDLE).orElseThrow().quantity()
                    + agent.inventory().total();
            assertEquals(totalBefore, totalAfter, EPSILON);
        }

        @Test
        @DisplayName("takes only what is left when the node is nearly empty")
        void limitedByTheNode() {
            Agent agent = hungryAndThirsty();
            World world = worldWithBerries(2.0);

            new GatherAction(ResourceType.BERRIES, 5.0, 1.0).execute(agent, world);

            assertEquals(2.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
            assertTrue(world.resourceAt(MIDDLE).orElseThrow().isDepleted());
        }

        @Test
        @DisplayName("takes only what fits, leaving the rest in the ground")
        void limitedByTheInventory() {
            Agent agent = hungryAndThirsty();
            World world = worldWithBerries(12.0);
            double capacity = agent.inventory().capacity();
            agent.store(ResourceType.STONE, capacity - 2.0);

            new GatherAction(ResourceType.BERRIES, 8.0, 1.0).execute(agent, world);

            assertEquals(2.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
            assertEquals(
                    10.0,
                    world.resourceAt(MIDDLE).orElseThrow().quantity(),
                    EPSILON,
                    "what did not fit must stay in the world, not vanish");
        }

        @Test
        @DisplayName("blocked on an empty tile")
        void blockedWithNothingToGather() {
            Agent agent = hungryAndThirsty();

            ActionOutcome outcome = new GatherAction(ResourceType.BERRIES, 5.0, 1.0).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.detail().contains("no berries left"), outcome.detail());
            assertTrue(outcome.change().isNothing());
        }

        @Test
        @DisplayName("blocked at a node that has been picked clean")
        void blockedAtADepletedNode() {
            Agent agent = hungryAndThirsty();

            ActionOutcome outcome = new GatherAction(ResourceType.BERRIES, 5.0, 1.0).execute(agent, worldWithBerries(0.0));

            assertTrue(outcome.wasBlocked());
            assertTrue(agent.inventory().isEmpty());
        }

        @Test
        @DisplayName("blocked with nowhere to put it")
        void blockedWhenFull() {
            Agent agent = hungryAndThirsty();
            World world = worldWithBerries(12.0);
            agent.store(ResourceType.STONE, agent.inventory().capacity());

            ActionOutcome outcome = new GatherAction(ResourceType.BERRIES, 5.0, 1.0).execute(agent, world);

            assertTrue(outcome.wasBlocked());
            assertEquals(12.0, world.resourceAt(MIDDLE).orElseThrow().quantity(), EPSILON);
        }

        @Test
        @DisplayName("gathering is work that takes more than a tick")
        void gatheringTakesTime() {
            assertEquals(2, new GatherAction(ResourceType.BERRIES, 5.0, 1.0).durationTicks());
        }
    }

    // --- EAT --------------------------------------------------------------

    @Nested
    @DisplayName("Eat")
    class Eat {

        @Test
        @DisplayName("consumes food from the inventory and relieves hunger")
        void eatsAndRelievesHunger() {
            Agent agent = hungryAndThirsty();
            agent.store(ResourceType.BERRIES, 6.0);

            ActionOutcome outcome = new EatAction(ResourceType.BERRIES, 4.0).execute(agent, world());

            assertTrue(outcome.succeeded(), outcome.detail());
            assertEquals(2.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
            assertEquals(
                    50.0 - 4.0 * ResourceType.BERRIES.nourishmentPerUnit(),
                    agent.needs().hunger(),
                    EPSILON);
            assertEquals(-4.0, outcome.change().carriedChange(), EPSILON);
            assertTrue(outcome.change().hungerChange() < 0);
        }

        @Test
        @DisplayName("blocked with an empty inventory")
        void blockedWithoutFood() {
            Agent agent = hungryAndThirsty();

            ActionOutcome outcome = new EatAction(ResourceType.BERRIES, 4.0).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.detail().contains("no berries"), outcome.detail());
            assertTrue(outcome.change().isNothing());
        }

        @Test
        @DisplayName("blocked when carrying some food but not a whole portion")
        void blockedWithTooLittleFood() {
            Agent agent = hungryAndThirsty();
            agent.store(ResourceType.BERRIES, 1.0);

            ActionOutcome outcome = new EatAction(ResourceType.BERRIES, 4.0).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertEquals(1.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
            assertEquals(50.0, agent.needs().hunger(), EPSILON);
        }

        @Test
        @DisplayName("blocked when the agent is not hungry, so food is not wasted")
        void blockedWhenNotHungry() {
            Agent agent = agentAt(MIDDLE, Needs.healthy());
            agent.store(ResourceType.BERRIES, 6.0);

            ActionOutcome outcome = new EatAction(ResourceType.BERRIES, 4.0).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertEquals(6.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
        }

        @Test
        @DisplayName("being told something is not food is a permanent refusal, not a passing one")
        void inedibilityIsPermanent() {
            Agent agent = hungryAndThirsty();
            agent.store(ResourceType.STONE, 6.0);

            ActionOutcome refused =
                    new EatAction(ResourceType.STONE, 4.0).execute(agent, world());
            ActionOutcome notHungry = new EatAction(ResourceType.BERRIES, 4.0)
                    .execute(agentAt(MIDDLE, Needs.healthy()), world());

            assertTrue(refused.wasImpossible(), "stone will never be food");
            assertFalse(notHungry.wasImpossible(), "but not being hungry passes");
        }

        @Test
        @DisplayName("blocked when the thing carried is not food")
        void blockedOnInedibleThings() {
            Agent agent = hungryAndThirsty();
            agent.store(ResourceType.STONE, 6.0);

            ActionOutcome outcome = new EatAction(ResourceType.STONE, 4.0).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.detail().contains("not food"), outcome.detail());
            assertEquals(6.0, agent.inventory().quantityOf(ResourceType.STONE), EPSILON);
        }
    }

    // --- DRINK ------------------------------------------------------------

    @Nested
    @DisplayName("Drink")
    class Drink {

        private World worldWithWaterAt(Location location, double quantity) {
            World world = world();
            world.addResource(ResourceNode.holding(ResourceType.WATER, location, quantity));
            return world;
        }

        @Test
        @DisplayName("drinks from the source underfoot and relieves thirst")
        void drinksAndRelievesThirst() {
            Agent agent = hungryAndThirsty();
            World world = worldWithWaterAt(MIDDLE, 100.0);

            ActionOutcome outcome = new DrinkAction(ResourceType.WATER, 4.0).execute(agent, world);

            assertTrue(outcome.succeeded(), outcome.detail());
            assertEquals(
                    50.0 - 4.0 * ResourceType.WATER.hydrationPerUnit(),
                    agent.needs().thirst(),
                    EPSILON);
            assertEquals(96.0, world.resourceAt(MIDDLE).orElseThrow().quantity(), EPSILON);
            assertTrue(outcome.change().thirstChange() < 0);
        }

        @Test
        @DisplayName("drinking takes nothing into the inventory")
        void drinkingDoesNotFillTheInventory() {
            Agent agent = hungryAndThirsty();

            new DrinkAction(ResourceType.WATER, 4.0).execute(agent, worldWithWaterAt(MIDDLE, 100.0));

            assertTrue(agent.inventory().isEmpty());
        }

        @Test
        @DisplayName("blocked away from water")
        void blockedWithoutWater() {
            Agent agent = hungryAndThirsty();

            ActionOutcome outcome = new DrinkAction(ResourceType.WATER, 4.0).execute(agent, world());

            assertTrue(outcome.wasBlocked());
            assertTrue(outcome.detail().contains("no water"), outcome.detail());
            assertTrue(outcome.change().isNothing());
        }

        @Test
        @DisplayName("blocked standing next to water rather than on it")
        void blockedOneTileAway() {
            Agent agent = hungryAndThirsty();
            World world = worldWithWaterAt(new Location(5, 4), 100.0);

            ActionOutcome outcome = new DrinkAction(ResourceType.WATER, 4.0).execute(agent, world);

            assertTrue(outcome.wasBlocked());
            assertEquals(50.0, agent.needs().thirst(), EPSILON);
        }

        @Test
        @DisplayName("blocked on a resource that is not drinkable")
        void blockedOnUndrinkableResources() {
            Agent agent = hungryAndThirsty();
            World world = world();
            world.addResource(ResourceNode.full(ResourceType.STONE, MIDDLE));

            assertTrue(new DrinkAction(ResourceType.WATER, 4.0).execute(agent, world).wasBlocked());
        }

        @Test
        @DisplayName("blocked when the agent is not thirsty")
        void blockedWhenNotThirsty() {
            Agent agent = agentAt(MIDDLE, Needs.healthy());
            World world = worldWithWaterAt(MIDDLE, 100.0);

            ActionOutcome outcome = new DrinkAction(ResourceType.WATER, 4.0).execute(agent, world);

            assertTrue(outcome.wasBlocked());
            assertEquals(100.0, world.resourceAt(MIDDLE).orElseThrow().quantity(), EPSILON);
        }

        @Test
        @DisplayName("a nearly dry spring gives what it has")
        void limitedByTheSource() {
            Agent agent = hungryAndThirsty();
            World world = worldWithWaterAt(MIDDLE, 1.0);

            new DrinkAction(ResourceType.WATER, 4.0).execute(agent, world);

            assertEquals(
                    50.0 - 1.0 * ResourceType.WATER.hydrationPerUnit(),
                    agent.needs().thirst(),
                    EPSILON);
            assertTrue(world.resourceAt(MIDDLE).orElseThrow().isDepleted());
        }
    }

    // --- the abstraction itself -------------------------------------------

    @Nested
    @DisplayName("the action contract")
    class Contract {

        @Test
        @DisplayName("a blocked action leaves the agent exactly as it was")
        void blockedActionsChangeNothing() {
            Agent agent = agentAt(MIDDLE, Needs.healthy());
            agent.store(ResourceType.STONE, 3.0);
            AgentSnapshot before = agent.snapshot();
            World world = world();

            for (Action action : java.util.List.of(
                    new MoveAction(MIDDLE, 1.5),
                    new RestAction(),
                    new GatherAction(ResourceType.BERRIES, 5.0, 1.0),
                    new EatAction(ResourceType.BERRIES, 4.0),
                    new DrinkAction(ResourceType.WATER, 4.0))) {
                ActionOutcome outcome = action.execute(agent, world);
                assertTrue(outcome.wasBlocked(), action.name() + " should be blocked here");
                assertEquals(before, agent.snapshot(), action.name() + " changed the agent");
            }
        }

        @Test
        @DisplayName("asking whether an action is possible does not perform it")
        void checkingIsNotDoing() {
            Agent agent = hungryAndThirsty();
            agent.store(ResourceType.BERRIES, 6.0);
            AgentSnapshot before = agent.snapshot();
            EatAction eat = new EatAction(ResourceType.BERRIES, 4.0);

            assertTrue(eat.isPossibleFor(agent, world()));
            assertTrue(eat.isPossibleFor(agent, world()));

            assertEquals(before, agent.snapshot());
        }

        @Test
        @DisplayName("an outcome reports what actually changed, not what was intended")
        void outcomesReportRealChange() {
            Agent agent = hungryAndThirsty();
            World world = world();
            world.addResource(ResourceNode.holding(ResourceType.BERRIES, MIDDLE, 1.0));

            ActionOutcome outcome = new GatherAction(ResourceType.BERRIES, 9.0, 1.0).execute(agent, world);

            assertEquals(1.0, outcome.change().carriedChange(), EPSILON, "asked for 9, got 1");
        }

        @Test
        @DisplayName("an outcome claiming to be blocked cannot report a change")
        void blockedOutcomesCannotCarryChanges() {
            Agent agent = hungryAndThirsty();
            AgentSnapshot before = agent.snapshot();
            agent.exert(5.0);
            AgentChange real = AgentChange.from(before, agent);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ActionOutcome("FAKE", ActionStatus.BLOCKED, "nope", real, false));
        }

        @Test
        @DisplayName("actions with the same parameters are the same action")
        void actionsHaveValueEquality() {
            assertEquals(new MoveAction(MIDDLE, 1.5), new MoveAction(MIDDLE, 1.5));
            assertEquals(new RestAction(), new RestAction());
            assertEquals(new GatherAction(ResourceType.BERRIES, 5.0, 1.0), new GatherAction(ResourceType.BERRIES, 5.0, 1.0));
            assertEquals(
                    new EatAction(ResourceType.BERRIES, 4.0),
                    new EatAction(ResourceType.BERRIES, 4.0));
            assertEquals(new DrinkAction(ResourceType.WATER, 4.0), new DrinkAction(ResourceType.WATER, 4.0));
            assertFalse(new DrinkAction(ResourceType.WATER, 4.0).equals(new DrinkAction(ResourceType.WATER, 5.0)));
        }

        @Test
        @DisplayName("nonsensical actions are rejected at construction")
        void rejectsImpossibleActions() {
            assertThrows(IllegalArgumentException.class, () -> new MoveAction(MIDDLE, -1.0));
            assertThrows(IllegalArgumentException.class, () -> new SleepAction(0));
            assertThrows(IllegalArgumentException.class, () -> new SleepAction(10, 0.0));
            assertThrows(IllegalArgumentException.class, () -> new GatherAction(ResourceType.BERRIES, 0.0, 1.0));
            assertThrows(
                    IllegalArgumentException.class, () -> new EatAction(ResourceType.BERRIES, 0.0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DrinkAction(ResourceType.WATER, -1.0));
        }
    }
}
