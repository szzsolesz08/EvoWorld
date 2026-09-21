package dev.evoworld.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Direction;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PerceptionSystemTest {

    private static final SimulationTime NOW = new SimulationTime(1L, Duration.ofMinutes(10));
    private static final Location MIDDLE = new Location(10, 10);
    private static final int RADIUS = 5;

    private final PerceptionSystem senses = new PerceptionSystem(RADIUS);

    private World world() {
        return new World(WorldBounds.of(21, 21));
    }

    private Agent agentAt(Location location) {
        return new Agent(AgentId.of("agent-1"), "Atlas", location, Needs.healthy());
    }

    private Perception perceive(World world, Agent agent) {
        return senses.perceive(agent, world, NOW);
    }

    private static void place(World world, ResourceType type, int x, int y) {
        world.addResource(ResourceNode.full(type, new Location(x, y)));
    }

    // --- what is and is not perceived -------------------------------------

    @Nested
    @DisplayName("range")
    class Range {

        @Test
        @DisplayName("nearby resources are perceived")
        void nearbyResourcesArePerceived() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 6);
            place(world, ResourceType.WATER, 13, 10);
            Agent agent = agentAt(MIDDLE);

            Perception perception = perceive(world, agent);

            assertEquals(2, perception.resources().size());
            assertTrue(perception.nearest(ResourceType.BERRIES).isPresent());
            assertTrue(perception.nearest(ResourceType.WATER).isPresent());
        }

        @Test
        @DisplayName("distant resources are not perceived at all")
        void distantResourcesAreNotPerceived() {
            World world = world();
            place(world, ResourceType.BERRIES, 20, 20);
            place(world, ResourceType.WOOD, 0, 0);
            Agent agent = agentAt(MIDDLE);

            Perception perception = perceive(world, agent);

            assertTrue(perception.resources().isEmpty(), perception.toString());
            assertFalse(perception.seesAnything());
            assertTrue(perception.nearest(ResourceType.BERRIES).isEmpty());
        }

        @Test
        @DisplayName("the radius is an inclusive edge, measured in steps")
        void theEdgeOfTheRadiusIsVisible() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 5); // exactly 5 steps north
            place(world, ResourceType.WOOD, 10, 4); // 6 steps north, one too far
            Agent agent = agentAt(MIDDLE);

            Perception perception = perceive(world, agent);

            assertEquals(1, perception.resources().size());
            assertEquals(ResourceType.BERRIES, perception.resources().get(0).type());
            assertEquals(5, perception.resources().get(0).stepsAway());
        }

        @Test
        @DisplayName("distance is walking distance, so the field of view is a diamond")
        void rangeIsMeasuredInSteps() {
            World world = world();
            // Three steps each way is six steps of travel: out of a radius of five.
            place(world, ResourceType.BERRIES, 13, 13);
            // Five steps along one axis is five steps of travel: just inside.
            place(world, ResourceType.WATER, 15, 10);
            Agent agent = agentAt(MIDDLE);

            Perception perception = perceive(world, agent);

            assertEquals(1, perception.resources().size());
            assertEquals(ResourceType.WATER, perception.resources().get(0).type());
        }

        @Test
        @DisplayName("a radius of zero sees only the tile underfoot")
        void zeroRadiusSeesOnlyItsOwnTile() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 10);
            place(world, ResourceType.WATER, 10, 11);

            Perception perception =
                    new PerceptionSystem(0).perceive(agentAt(MIDDLE), world, NOW);

            assertEquals(1, perception.resources().size());
            assertTrue(perception.underfoot().isPresent());
        }

        @Test
        void rejectsANegativeRadius() {
            assertThrows(IllegalArgumentException.class, () -> new PerceptionSystem(-1));
        }
    }

    // --- position --------------------------------------------------------

    @Nested
    @DisplayName("position")
    class Position {

        @Test
        @DisplayName("the same world looks different from a different tile")
        void positionChangesWhatIsPerceived() {
            World world = world();
            place(world, ResourceType.BERRIES, 2, 10);
            place(world, ResourceType.WATER, 18, 10);

            Perception fromWest = perceive(world, agentAt(new Location(4, 10)));
            Perception fromEast = perceive(world, agentAt(new Location(16, 10)));

            assertEquals(
                    List.of(ResourceType.BERRIES),
                    fromWest.resources().stream().map(PerceivedResource::type).toList());
            assertEquals(
                    List.of(ResourceType.WATER),
                    fromEast.resources().stream().map(PerceivedResource::type).toList());
        }

        @Test
        @DisplayName("walking towards something brings it into range")
        void movingChangesWhatComesIntoView() {
            World world = world();
            place(world, ResourceType.BERRIES, 18, 10);
            Agent agent = agentAt(MIDDLE);

            assertTrue(perceive(world, agent).resources().isEmpty());

            agent.moveTo(new Location(14, 10));

            assertEquals(1, perceive(world, agent).resources().size());
        }

        @Test
        @DisplayName("distance and bearing are measured from where the agent is")
        void distanceAndBearingAreRelative() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 6);

            PerceivedResource fromMiddle = perceive(world, agentAt(MIDDLE)).resources().get(0);
            PerceivedResource fromNorth =
                    perceive(world, agentAt(new Location(10, 4))).resources().get(0);

            assertEquals(4, fromMiddle.stepsAway());
            assertEquals(Direction.NORTH, fromMiddle.direction());
            assertEquals(2, fromNorth.stepsAway());
            assertEquals(Direction.SOUTH, fromNorth.direction());
        }

        @Test
        @DisplayName("a resource underfoot is zero steps away")
        void resourceUnderfootIsZeroSteps() {
            World world = world();
            place(world, ResourceType.WATER, 10, 10);

            Perception perception = perceive(world, agentAt(MIDDLE));

            PerceivedResource here = perception.underfoot().orElseThrow();
            assertEquals(0, here.stepsAway());
            assertEquals(Direction.HERE, here.direction());
            assertTrue(here.isUnderfoot());
        }
    }

    // --- the boundary ----------------------------------------------------

    @Nested
    @DisplayName("the boundary")
    class Boundary {

        @Test
        @DisplayName("perception is a snapshot: later changes to the world do not show up")
        void perceptionDoesNotFollowTheWorld() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 8);
            Agent agent = agentAt(MIDDLE);

            Perception perception = perceive(world, agent);
            double seen = perception.resources().get(0).quantity();

            world.resourceAt(new Location(10, 8)).orElseThrow().harvestAll();
            agent.moveTo(new Location(0, 0));
            agent.exert(50.0);

            assertEquals(seen, perception.resources().get(0).quantity(), 1e-9);
            assertTrue(perception.resources().get(0).isAvailable());
            assertEquals(MIDDLE, perception.location());
            assertEquals(Needs.MAX, perception.needs().energy(), 1e-9);
        }

        @Test
        @DisplayName("what it hands over cannot be edited")
        void perceivedListsAreImmutable() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 8);
            Perception perception = perceive(world, agentAt(MIDDLE));

            assertThrows(
                    UnsupportedOperationException.class,
                    () -> perception.resources().add(perception.resources().get(0)));
            assertThrows(UnsupportedOperationException.class, () -> perception.others().clear());
        }

        @Test
        @DisplayName("perceiving changes nothing at all")
        void perceivingIsReadOnly() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 8);
            Agent agent = agentAt(MIDDLE);
            var worldBefore = world.snapshot(NOW);

            perceive(world, agent);
            perceive(world, agent);

            assertEquals(worldBefore, world.snapshot(NOW));
        }

        @Test
        @DisplayName("an agent is told its own state in full, and only its own")
        void selfIsFullyKnown() {
            World world = world();
            Agent agent = agentAt(MIDDLE);
            agent.store(ResourceType.BERRIES, 3.0);

            Perception perception = perceive(world, agent);

            assertEquals(agent.id(), perception.agentId());
            assertEquals(MIDDLE, perception.location());
            assertEquals(agent.needs(), perception.needs());
            assertEquals(3.0, perception.inventory().quantityOf(ResourceType.BERRIES), 1e-9);
        }

        @Test
        @DisplayName("an agent does not perceive itself as somebody else")
        void selfIsNotAmongTheOthers() {
            World world = world();
            Agent agent = agentAt(MIDDLE);
            world.addAgent(agent);

            assertTrue(perceive(world, agent).others().isEmpty());
        }

        @Test
        @DisplayName("standing facts about the world come through as known, not sensed")
        void knownInformationIsSeparateFromSensing() {
            World world = world();

            Perception perception = perceive(world, agentAt(MIDDLE));

            assertEquals(WorldBounds.of(21, 21), perception.bounds());
            assertEquals(RADIUS, perception.radius());
            assertFalse(perception.seesAnything(), "knowing the map is not seeing anything");
        }
    }

    // --- other agents ----------------------------------------------------

    @Nested
    @DisplayName("other agents")
    class Others {

        @Test
        void nearbyAgentsArePerceivedAndDistantOnesAreNot() {
            World world = world();
            Agent atlas = agentAt(MIDDLE);
            Agent near = new Agent(AgentId.of("agent-2"), "Bo", new Location(12, 10), Needs.healthy());
            Agent far = new Agent(AgentId.of("agent-3"), "Cyd", new Location(20, 20), Needs.healthy());
            world.addAgent(atlas);
            world.addAgent(near);
            world.addAgent(far);

            Perception perception = perceive(world, atlas);

            assertEquals(1, perception.others().size());
            assertEquals("Bo", perception.others().get(0).name());
            assertEquals(2, perception.others().get(0).stepsAway());
            assertEquals(Direction.EAST, perception.others().get(0).direction());
        }
    }

    // --- ordering and determinism ----------------------------------------

    @Nested
    @DisplayName("ordering")
    class Ordering {

        @Test
        @DisplayName("what is perceived comes back nearest first")
        void resourcesAreSortedByDistance() {
            World world = world();
            place(world, ResourceType.WOOD, 10, 6);
            place(world, ResourceType.BERRIES, 10, 9);
            place(world, ResourceType.STONE, 12, 10);
            Perception perception = perceive(world, agentAt(MIDDLE));

            assertEquals(
                    List.of(1, 2, 4),
                    perception.resources().stream().map(PerceivedResource::stepsAway).toList());
        }

        @Test
        @DisplayName("nearest picks the closest one with something left in it")
        void nearestSkipsDepletedNodes() {
            World world = world();
            world.addResource(
                    ResourceNode.holding(ResourceType.BERRIES, new Location(10, 9), 0.0));
            place(world, ResourceType.BERRIES, 10, 7);

            Perception perception = perceive(world, agentAt(MIDDLE));

            assertEquals(2, perception.resources().size(), "an empty bush is still visible");
            assertEquals(
                    new Location(10, 7),
                    perception.nearest(ResourceType.BERRIES).orElseThrow().location());
        }

        @Test
        @DisplayName("perceiving the same world twice gives the same perception")
        void perceptionIsDeterministic() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 8);
            place(world, ResourceType.WATER, 8, 10);
            Agent agent = agentAt(MIDDLE);

            assertEquals(perceive(world, agent), perceive(world, agent));
        }

        @Test
        @DisplayName("two agents standing apart perceive different things")
        void differentViewersDisagree() {
            World world = world();
            place(world, ResourceType.BERRIES, 10, 8);

            assertNotEquals(
                    perceive(world, agentAt(MIDDLE)),
                    perceive(world, agentAt(new Location(9, 10))));
        }
    }

    @Test
    @DisplayName("a perception reads out the way you would say it aloud")
    void describesWhatItSees() {
        World world = world();
        place(world, ResourceType.BERRIES, 10, 6);
        place(world, ResourceType.WATER, 14, 10);

        Perception perception = perceive(world, agentAt(MIDDLE));

        assertEquals(
                List.of("berries 4 steps north (12.0)", "water 4 steps east (100.0)"),
                perception.describe());
    }
}
