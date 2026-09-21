package dev.evoworld.core.perception;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.exploration.ExplorationMap;
import dev.evoworld.core.exploration.Region;
import dev.evoworld.core.memory.MemoryKind;
import dev.evoworld.core.memory.MemoryRecall;
import dev.evoworld.core.memory.RecallQuery;
import dev.evoworld.core.memory.Recollection;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Turns a world into what one agent can tell about it.
 *
 * <p>The only place allowed to look at the live world on an agent's behalf.
 * It reads the world and emits immutable values, so nothing downstream ever
 * holds a reference it could follow back.
 *
 * <p>It also asks the agent what it remembers. Recalled places arrive beside
 * sensed ones but as a different type, carrying a confidence below one: a
 * perception is where seeing and believing meet, and the two are never mixed
 * up. Somewhere the agent can see is never also offered as somewhere it
 * remembers.
 *
 * <p>Range is measured in <strong>steps</strong>, not line of sight: a radius
 * of six means six moves away. That makes a field of view a diamond rather than
 * a circle, which is odd as eyesight and exactly right as a decision input -
 * "four steps north" is directly comparable to what four steps will cost. There
 * is no terrain to block a view yet, so there is nothing to occlude.
 */
public final class PerceptionSystem {

    /** Roughly a third of the way across the default world. */
    public static final int DEFAULT_RADIUS = 6;

    /** How many unknown corners an agent weighs at once. */
    private static final int FRONTIER_SIZE = 4;

    private final int radius;

    public PerceptionSystem(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("perception radius must not be negative, was " + radius);
        }
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }

    /**
     * Everything {@code agent} can tell about {@code world} right now.
     *
     * <p>Results come back nearest first, ties broken by position, so a
     * perception built twice from the same world is the same perception.
     */
    public Perception perceive(Agent agent, World world, SimulationTime time) {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(time, "time");

        Location here = agent.location();

        List<PerceivedResource> resources = world.resources().stream()
                .filter(node -> isWithinRange(here, node.location()))
                .map(node -> PerceivedResource.seenFrom(here, node.snapshot()))
                .sorted(nearestFirst(
                        PerceivedResource::stepsAway, PerceivedResource::location))
                .toList();

        List<PerceivedAgent> others = world.agents().stream()
                .filter(other -> !other.id().equals(agent.id()))
                .filter(other -> isWithinRange(here, other.location()))
                .map(other -> PerceivedAgent.seenFrom(here, other.snapshot()))
                .sorted(nearestFirst(PerceivedAgent::stepsAway, PerceivedAgent::location))
                .toList();

        List<RememberedResource> remembered =
                recallPlacesOutOfSight(agent.memory(), here, time, resources);

        // Standing somewhere is how it becomes familiar, so coverage is
        // updated as part of looking rather than as part of moving.
        agent.exploration().sight(world.bounds(), here, time);

        return new Perception(
                time,
                agent.snapshot(),
                world.bounds(),
                radius,
                resources,
                others,
                remembered,
                agent.knowledge(),
                surveyOf(agent.exploration(), here, time));
    }

    /**
     * How much of the world the agent has covered, and where the gaps are.
     *
     * <p>Only a handful of the least familiar regions are offered. An agent
     * weighing every corner of the map at once would be planning, and this
     * layer does not plan - it points at the nearest few unknowns and lets the
     * decision layer decide whether any of them is worth the walk.
     */
    private ExplorationView surveyOf(ExplorationMap map, Location here, SimulationTime time) {
        List<UnexploredArea> frontier = new ArrayList<>();
        for (Region region : map.leastFamiliar(time.tick(), FRONTIER_SIZE)) {
            frontier.add(
                    UnexploredArea.seenFrom(here, region, map.familiarity(region, time.tick())));
        }
        return new ExplorationView(
                frontier,
                map.worldFamiliarity(time.tick()),
                map.regionsVisited(),
                map.regionCount());
    }

    /**
     * Places the agent believes in but cannot currently see.
     *
     * <p>Anything in sight is left out: a belief about something the agent is
     * looking straight at is noise, and worse, it would be a second candidate
     * for the same tile at a lower confidence.
     */
    private List<RememberedResource> recallPlacesOutOfSight(
            MemoryRecall memory,
            Location here,
            SimulationTime time,
            List<PerceivedResource> visible) {
        List<Location> inSight = visible.stream().map(PerceivedResource::location).toList();

        // Asked for enough to fill the list after what is in sight is left out.
        // Asking for the usual number and filtering afterwards let the stone and
        // wood underfoot crowd out the spring an agent was walking to, which
        // then vanished from its mind for as long as it stood near them.
        RecallQuery query = RecallQuery.from(time, here)
                .ofKind(MemoryKind.RESOURCE_LOCATION)
                .limitedTo(RecallQuery.DEFAULT_LIMIT + inSight.size());

        List<RememberedResource> places = new ArrayList<>();
        for (Recollection recalled : memory.recall(query)) {
            if (inSight.contains(recalled.location())) {
                continue;
            }
            if (places.size() == RecallQuery.DEFAULT_LIMIT) {
                break;
            }
            places.add(RememberedResource.recalledFrom(
                    here,
                    ResourceType.valueOf(recalled.subject()),
                    recalled.location(),
                    recalled.confidence(),
                    recalled.memory().ageAt(time.tick())));
        }
        return List.copyOf(places);
    }

    private boolean isWithinRange(Location viewer, Location target) {
        return viewer.manhattanDistanceTo(target) <= radius;
    }

    /** Nearest first, then by column and row so the order never wobbles. */
    private static <T> Comparator<T> nearestFirst(
            java.util.function.ToIntFunction<T> distance,
            java.util.function.Function<T, Location> location) {
        return Comparator.comparingInt(distance)
                .thenComparingInt(seen -> location.apply(seen).x())
                .thenComparingInt(seen -> location.apply(seen).y());
    }
}
