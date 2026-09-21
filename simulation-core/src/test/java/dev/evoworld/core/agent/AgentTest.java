package dev.evoworld.core.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AgentTest {

    private static final double EPSILON = 1e-9;
    private static final Location START = new Location(4, 4);
    private final MetabolismRates rates = MetabolismRates.defaults().perTick(Duration.ofMinutes(10));

    private Agent atlas() {
        return Agent.spawn("agent-1", "Atlas", START);
    }

    @Nested
    @DisplayName("identity")
    class Identity {

        @Test
        @DisplayName("a new agent has an id, a name, a place and a healthy body")
        void spawnsWithEverythingItNeeds() {
            Agent agent = atlas();

            assertEquals(AgentId.of("agent-1"), agent.id());
            assertEquals("Atlas", agent.name());
            assertEquals(START, agent.location());
            assertEquals(Needs.healthy(), agent.needs());
            assertTrue(agent.inventory().isEmpty());
            assertEquals(0L, agent.ticksAlive());
            assertTrue(agent.isAlive());
        }

        @Test
        @DisplayName("the name is for reading, the id is for identifying")
        void nameAndIdAreSeparate() {
            Agent first = Agent.spawn("agent-1", "Atlas", START);
            Agent second = Agent.spawn("agent-2", "Atlas", START);

            assertEquals(first.name(), second.name());
            assertFalse(first.id().equals(second.id()));
        }

        @Test
        void requiresANonBlankName() {
            assertThrows(IllegalArgumentException.class, () -> Agent.spawn("agent-1", "  ", START));
            assertThrows(NullPointerException.class, () -> Agent.spawn("agent-1", null, START));
        }
    }

    @Nested
    @DisplayName("position")
    class Position {

        @Test
        void movesToAGivenLocation() {
            Agent agent = atlas();

            agent.moveTo(new Location(7, 2));

            assertEquals(new Location(7, 2), agent.location());
        }

        @Test
        void rejectsAMissingDestination() {
            assertThrows(NullPointerException.class, () -> atlas().moveTo(null));
        }
    }

    @Nested
    @DisplayName("stat changes")
    class StatChanges {

        @Test
        void exertionSpendsEnergy() {
            Agent agent = atlas();

            agent.exert(30.0);

            assertEquals(Needs.MAX - 30.0, agent.needs().energy(), EPSILON);
        }

        @Test
        void restRecoversEnergy() {
            Agent agent = atlas();
            agent.exert(50.0);

            agent.rest(20.0);

            assertEquals(Needs.MAX - 30.0, agent.needs().energy(), EPSILON);
        }

        @Test
        void eatingAndDrinkingRelievePressures() {
            Agent agent = new Agent(
                    AgentId.of("agent-1"), "Atlas", START, new Needs(40.0, 40.0, 50.0, 100.0));

            agent.eat(15.0);
            agent.drink(25.0);

            assertEquals(25.0, agent.needs().hunger(), EPSILON);
            assertEquals(15.0, agent.needs().thirst(), EPSILON);
        }

        @Test
        @DisplayName("stat changes stay within range however often they are applied")
        void statsStayInRange() {
            Agent agent = atlas();

            for (int i = 0; i < 100; i++) {
                agent.exert(10.0);
                agent.rest(5.0);
                agent.eat(3.0);
            }

            assertTrue(agent.needs().energy() >= Needs.MIN);
            assertTrue(agent.needs().energy() <= Needs.MAX);
            assertEquals(Needs.MIN, agent.needs().hunger(), EPSILON);
        }
    }

    @Nested
    @DisplayName("inventory")
    class Carrying {

        @Test
        void storesWhatItIsGiven() {
            Agent agent = atlas();

            double stored = agent.store(ResourceType.BERRIES, 4.0);

            assertEquals(4.0, stored, EPSILON);
            assertEquals(4.0, agent.inventory().quantityOf(ResourceType.BERRIES), EPSILON);
        }

        @Test
        @DisplayName("an agent can only carry so much, and says how much it took")
        void storesOnlyWhatFits() {
            Agent agent = atlas();
            double capacity = agent.inventory().capacity();

            double first = agent.store(ResourceType.STONE, capacity - 1.0);
            double second = agent.store(ResourceType.BERRIES, 10.0);

            assertEquals(capacity - 1.0, first, EPSILON);
            assertEquals(1.0, second, EPSILON, "only the last unit of space was left");
            assertTrue(agent.inventory().isFull());
        }

        @Test
        void takesBackWhatItIsCarrying() {
            Agent agent = atlas();
            agent.store(ResourceType.WOOD, 6.0);

            double taken = agent.take(ResourceType.WOOD, 2.5);

            assertEquals(2.5, taken, EPSILON);
            assertEquals(3.5, agent.inventory().quantityOf(ResourceType.WOOD), EPSILON);
        }

        @Test
        @DisplayName("taking more than is carried yields what there is")
        void takingTooMuchYieldsWhatIsThere() {
            Agent agent = atlas();
            agent.store(ResourceType.WOOD, 2.0);

            assertEquals(2.0, agent.take(ResourceType.WOOD, 99.0), EPSILON);
            assertEquals(0.0, agent.take(ResourceType.WOOD, 99.0), EPSILON);
            assertTrue(agent.inventory().isEmpty());
        }
    }

    @Nested
    @DisplayName("living through a tick")
    class Living {

        @Test
        @DisplayName("a tick ages the agent and runs its metabolism")
        void aTickAgesAndDrains() {
            Agent agent = atlas();

            agent.live(rates, Exertion.of(ActivityLevel.ACTIVE));

            assertEquals(1L, agent.ticksAlive());
            assertEquals(rates.hunger(), agent.needs().hunger(), EPSILON);
            assertEquals(Needs.MAX - -rates.activeEnergy(), agent.needs().energy(), EPSILON);
        }

        @Test
        @DisplayName("age counts every tick lived")
        void ageAccumulates() {
            Agent agent = atlas();

            for (int i = 0; i < 25; i++) {
                agent.live(rates, Exertion.of(ActivityLevel.RESTING));
            }

            assertEquals(25L, agent.ticksAlive());
        }

        @Test
        @DisplayName("resting through a tick costs less energy than being active")
        void restingIsCheaper() {
            Agent active = atlas();
            Agent resting = atlas();

            active.live(rates, Exertion.of(ActivityLevel.ACTIVE));
            resting.live(rates, Exertion.of(ActivityLevel.RESTING));

            assertTrue(resting.needs().energy() > active.needs().energy());
        }

        @Test
        @DisplayName("an agent that never eats or drinks eventually dies")
        void neglectedAgentsDie() {
            Agent agent = atlas();

            long ticks = 0;
            while (agent.isAlive() && ticks < 10_000) {
                agent.live(rates, Exertion.of(ActivityLevel.ACTIVE));
                ticks++;
            }

            assertFalse(agent.isAlive());
            assertEquals(ticks, agent.ticksAlive(), "every tick lived should have been counted");
        }

        @Test
        @DisplayName("a dead agent stops ageing, so its age is the length of its life")
        void deadAgentsStopAgeing() {
            Agent agent = new Agent(
                    AgentId.of("agent-1"), "Atlas", START, new Needs(0.0, 0.0, 50.0, 0.0));

            agent.live(rates, Exertion.of(ActivityLevel.ACTIVE));
            agent.live(rates, Exertion.of(ActivityLevel.ACTIVE));

            assertFalse(agent.isAlive());
            assertEquals(0L, agent.ticksAlive());
        }

        @Test
        @DisplayName("living a tick is deterministic")
        void livingIsDeterministic() {
            Agent first = atlas();
            Agent second = atlas();

            for (int i = 0; i < 50; i++) {
                first.live(rates, Exertion.of(ActivityLevel.ACTIVE));
                second.live(rates, Exertion.of(ActivityLevel.ACTIVE));
            }

            assertEquals(first.needs(), second.needs());
            assertEquals(first.ticksAlive(), second.ticksAlive());
        }

        @Test
        void rejectsMissingRatesOrActivity() {
            Agent agent = atlas();

            assertThrows(NullPointerException.class, () -> agent.live(null, Exertion.of(ActivityLevel.ACTIVE)));
            assertThrows(NullPointerException.class, () -> agent.live(rates, null));
        }
    }
}
