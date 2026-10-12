package dev.evoworld.core.world;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceSnapshot;
import dev.evoworld.core.resource.ResourceType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The simulated space and everything in it: a bounded grid holding resource
 * nodes and agents.
 *
 * <p>A tile holds at most one resource node. That is a simplification, not a
 * law of nature - it keeps "what is here?" a single lookup, and nothing yet
 * needs a bush and a stream on the same square.
 *
 * <p>Resources are stored in a {@link LinkedHashMap} rather than a
 * {@link java.util.HashMap} on purpose: iteration order has to be insertion
 * order, or the order resources regenerate in would depend on hash codes and a
 * run would stop being reproducible.
 *
 * <p>Ground is held in {@link Chunk chunks}. A world given a {@link Terrain}
 * fills a chunk the moment an agent comes near enough to see into it, which is
 * what lets a world be endless without being generated. A world without one is
 * a fixed map: every chunk it will ever have is put there by hand or by
 * {@link WorldGenerator}.
 */
public final class World {

    private final WorldBounds bounds;
    private final Terrain terrain;
    private final Map<Chunk, ChunkState> chunks = new LinkedHashMap<>();
    private final List<Agent> agents = new ArrayList<>();

    /** How far around an agent a snapshot reaches: its own chunk, and any it can see into. */
    private static final int SNAPSHOT_REACH = 16;

    /**
     * The fires burning in the world, in the order they were lit.
     *
     * <p>Kept whole rather than by chunk: there are never many, they go out on
     * their own, and one that has gone out is forgotten the moment it does.
     */
    private final Map<Location, Fire> fires = new LinkedHashMap<>();

    /** One chunk's worth of ground, and when its regrowth was last brought up to date. */
    private static final class ChunkState {
        private final Map<Location, ResourceNode> nodes = new LinkedHashMap<>();
        private long regrownTo;
    }

    /** A fixed map: whatever is put in it is all it will ever hold. */
    public World(WorldBounds bounds) {
        this(bounds, null);
    }

    /**
     * A map that generates itself as it is walked into.
     *
     * @param terrain what an unvisited chunk holds, or null for a fixed map
     */
    public World(WorldBounds bounds, Terrain terrain) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.terrain = terrain;
    }

    /** Whether this world fills itself in as an agent explores it. */
    public boolean generatesItself() {
        return terrain != null;
    }

    public WorldBounds bounds() {
        return bounds;
    }

    // --- resources --------------------------------------------------------

    /**
     * Places a resource node.
     *
     * @throws IllegalArgumentException if it lies outside the world, or the
     *     tile is already occupied by another node
     */
    public void addResource(ResourceNode node) {
        Objects.requireNonNull(node, "node");
        if (!bounds.contains(node.location())) {
            throw new IllegalArgumentException(
                    "resource " + node.type().label() + " placed outside the world at "
                            + node.location());
        }
        ResourceNode existing = stateOf(Chunk.containing(node.location())).nodes
                .putIfAbsent(node.location(), node);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "tile " + node.location() + " already holds " + existing.type().label());
        }
    }

    /** The node on a tile, if there is one. */
    public Optional<ResourceNode> resourceAt(Location location) {
        ChunkState chunk = chunks.get(Chunk.containing(location));
        return chunk == null ? Optional.empty() : Optional.ofNullable(chunk.nodes.get(location));
    }

    /** The node on a tile, but only if it has something left to give. */
    public Optional<ResourceNode> availableResourceAt(Location location) {
        return resourceAt(location).filter(ResourceNode::isAvailable);
    }

    /** Every node the world has generated so far, chunk by chunk, in placement order. */
    public List<ResourceNode> resources() {
        List<ResourceNode> all = new ArrayList<>();
        chunks.values().forEach(chunk -> all.addAll(chunk.nodes.values()));
        return List.copyOf(all);
    }

    /**
     * The nodes within {@code radius} steps of a place - what an agent standing
     * there could see. Reads only the chunks that square touches, so looking
     * around costs the same in an endless world as in a small one.
     */
    public List<ResourceNode> resourcesNear(Location centre, int radius) {
        List<ResourceNode> near = new ArrayList<>();
        for (Chunk chunk : Chunk.around(centre, radius)) {
            ChunkState state = chunks.get(chunk);
            if (state == null) {
                continue;
            }
            for (ResourceNode node : state.nodes.values()) {
                if (node.location().manhattanDistanceTo(centre) <= radius) {
                    near.add(node);
                }
            }
        }
        return List.copyOf(near);
    }

    public List<ResourceNode> resourcesOfType(ResourceType type) {
        return resources().stream().filter(node -> node.type() == type).toList();
    }

    /** How many chunks this world has generated or been given. */
    public int chunkCount() {
        return chunks.size();
    }

    /** The chunks it holds, in the order they came into being. */
    public List<Chunk> chunks() {
        return List.copyOf(chunks.keySet());
    }

    /**
     * Makes sure everything within {@code radius} of a place exists.
     *
     * <p>Called before an agent looks around, so a chunk is always there a
     * moment before it could be seen. On a fixed map it does nothing.
     */
    public void ensureAround(Location centre, int radius, long tick) {
        if (terrain == null) {
            return;
        }
        for (Chunk chunk : Chunk.around(centre, radius)) {
            if (!chunks.containsKey(chunk) && withinBounds(chunk)) {
                generate(chunk, tick);
            }
        }
    }

    private boolean withinBounds(Chunk chunk) {
        Location origin = chunk.origin();
        return bounds.contains(origin)
                || bounds.contains(new Location(origin.x() + Chunk.SIZE - 1, origin.y() + Chunk.SIZE - 1));
    }

    private void generate(Chunk chunk, long tick) {
        ChunkState state = new ChunkState();
        state.regrownTo = tick;
        chunks.put(chunk, state);
        for (ResourceNode node : terrain.generate(chunk)) {
            if (bounds.contains(node.location()) && chunk.contains(node.location())) {
                state.nodes.putIfAbsent(node.location(), node);
            }
        }
    }

    private ChunkState stateOf(Chunk chunk) {
        return chunks.computeIfAbsent(chunk, key -> new ChunkState());
    }

    /** Applies one tick of regrowth to every node. */
    /**
     * Regrowth, for the chunks anyone is near.
     *
     * <p>A chunk far from every agent is left alone until someone comes back,
     * and then catches up in one step from when it was last brought up to date.
     * The result is the same as regrowing it every tick - a bush picked bare
     * and left for a week is full when you return - without the cost of walking
     * every node in a world that may be thousands of chunks wide.
     *
     * @param around where regrowth is worth doing tick by tick
     * @param radius how far around those places counts as nearby
     * @param now the tick that has just been reached
     */
    public void regenerateResources(Duration tick, List<Location> around, int radius, long now) {
        for (Location place : around) {
            for (Chunk chunk : Chunk.around(place, radius)) {
                ChunkState state = chunks.get(chunk);
                if (state != null) {
                    catchUp(state, tick, now);
                }
            }
        }
    }

    // --- fires -------------------------------------------------------------

    /** Lights a fire here, or feeds the one already burning; returns the wood it took. */
    public double tendFire(Location where, double wood) {
        Objects.requireNonNull(where, "where");
        Fire here = fires.get(where);
        if (here == null || here.isOut()) {
            Fire lit = Fire.lit(where, Math.min(wood, Fire.MOST_IT_HOLDS));
            fires.put(where, lit);
            return lit.fuel();
        }
        return here.feed(wood);
    }

    /** The fire burning here, if there is one. */
    public Optional<Fire> fireAt(Location where) {
        return Optional.ofNullable(fires.get(where)).filter(fire -> !fire.isOut());
    }

    /**
     * The fire doing the most to warm a place, if any is.
     *
     * <p>What an agent means by "my fire" is the one it can feel, not the one
     * it is standing exactly on. Judged the other way, an agent one step off
     * its own fire is warm and believes itself exposed, and spends the night
     * stepping on and off the same tile.
     */
    public Optional<Fire> warmestFireNear(Location where) {
        Objects.requireNonNull(where, "where");
        return fires.values().stream()
                .filter(fire -> fire.warmthAt(where) > 0.0)
                .max((first, second) -> Double.compare(first.warmthAt(where), second.warmthAt(where)));
    }

    /** Every fire still burning, in the order they were lit. */
    public List<Fire> fires() {
        return fires.values().stream().filter(fire -> !fire.isOut()).toList();
    }

    /** Puts a saved fire back exactly as it was. */
    public void restoreFire(Fire fire) {
        fires.put(Objects.requireNonNull(fire, "fire").location(), fire);
    }

    /**
     * How much warmer the fires make a place.
     *
     * <p>Fires do not add up: standing between two of them is as warm as the
     * better of the two, which spares the world a pile of bonfires that warm
     * an agent more than a summer day.
     */
    public double warmthAt(Location where) {
        double warmest = 0.0;
        for (Fire fire : fires.values()) {
            warmest = Math.max(warmest, fire.warmthAt(where));
        }
        return warmest;
    }

    /** Burns every fire for one tick, and forgets the ones that have gone out. */
    public void burnFires(Duration tick) {
        fires.values().forEach(fire -> fire.burn(tick, 1L));
        fires.values().removeIf(Fire::isOut);
    }

    /** One tick of regrowth for every chunk there is; for small fixed maps and tests. */
    public void regenerateResources(Duration tick) {
        chunks.values().forEach(state -> {
            state.nodes.values().forEach(node -> node.regenerate(tick));
            state.regrownTo++;
        });
    }

    private static void catchUp(ChunkState state, Duration tick, long now) {
        long missed = now - state.regrownTo;
        if (missed <= 0) {
            return;
        }
        state.nodes.values().forEach(node -> node.regenerate(tick, missed));
        state.regrownTo = now;
    }

    /**
     * The nodes in the chunks anyone is standing in or beside.
     *
     * <p>What a snapshot carries. An endless world cannot hand out a copy of
     * everything it has ever generated every tick - after a few weeks' walking
     * that is tens of thousands of nodes a tick, and nobody watching can use
     * ground a day's walk from any agent anyway.
     */
    private List<ResourceNode> aroundEveryone() {
        if (terrain == null) {
            return resources();
        }
        List<ResourceNode> near = new ArrayList<>();
        Set<Chunk> seen = new LinkedHashSet<>();
        for (Agent agent : agents) {
            for (Chunk chunk : Chunk.around(agent.location(), SNAPSHOT_REACH)) {
                ChunkState state = chunks.get(chunk);
                if (state != null && seen.add(chunk)) {
                    near.addAll(state.nodes.values());
                }
            }
        }
        return List.copyOf(near);
    }

    /** How far behind a chunk's regrowth is, for saving it. */
    public long regrownTo(Chunk chunk) {
        ChunkState state = chunks.get(chunk);
        return state == null ? 0L : state.regrownTo;
    }

    /** Restores a chunk from a save: its nodes, and how far its regrowth had got. */
    public void restoreChunk(Chunk chunk, List<ResourceNode> nodes, long regrownTo) {
        ChunkState state = stateOf(chunk);
        state.regrownTo = regrownTo;
        nodes.forEach(node -> state.nodes.put(node.location(), node));
    }

    // --- agents -----------------------------------------------------------

    public void addAgent(Agent agent) {
        if (!bounds.contains(agent.location())) {
            throw new IllegalArgumentException(
                    "agent " + agent.id() + " spawned outside the world at " + agent.location());
        }
        agents.add(agent);
    }

    /** Agents in insertion order, which keeps tick processing deterministic. */
    public List<Agent> agents() {
        return List.copyOf(agents);
    }

    public Optional<Agent> findAgent(AgentId id) {
        return agents.stream().filter(agent -> agent.id().equals(id)).findFirst();
    }

    // --- observation ------------------------------------------------------

    public WorldState snapshot(SimulationTime time) {
        List<AgentSnapshot> agentSnapshots = agents.stream().map(Agent::snapshot).toList();
        List<ResourceSnapshot> resourceSnapshots =
                aroundEveryone().stream().map(ResourceNode::snapshot).toList();
        return new WorldState(
                time,
                bounds,
                agentSnapshots,
                resourceSnapshots,
                fires().stream().map(FireSnapshot::of).toList());
    }
}
