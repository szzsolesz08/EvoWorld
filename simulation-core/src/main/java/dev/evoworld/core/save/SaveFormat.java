package dev.evoworld.core.save;

import dev.evoworld.core.ActionSettings;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.action.Action;
import dev.evoworld.core.action.DiscardAction;
import dev.evoworld.core.action.DrinkAction;
import dev.evoworld.core.action.EatAction;
import dev.evoworld.core.action.GatherAction;
import dev.evoworld.core.action.MoveAction;
import dev.evoworld.core.action.RestAction;
import dev.evoworld.core.action.TendFireAction;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.agent.Exertion;
import dev.evoworld.core.agent.Inventory;
import dev.evoworld.core.agent.MetabolismRates;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.event.SimulationEvent.Danger;
import dev.evoworld.core.exploration.ExplorationMap;
import dev.evoworld.core.exploration.Region;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.item.Belongings;
import dev.evoworld.core.item.Item;
import dev.evoworld.core.knowledge.Belief;
import dev.evoworld.core.knowledge.Knowledge;
import dev.evoworld.core.memory.Memory;
import dev.evoworld.core.memory.MemoryStore;
import dev.evoworld.core.resource.ResourceNode;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.skill.Skills;
import dev.evoworld.core.world.ChunkMix;
import dev.evoworld.core.world.Chunk;
import dev.evoworld.core.world.ChunkedTerrain;
import dev.evoworld.core.world.Terrain;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldBounds;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Translates between the domain and the save format, in both directions.
 *
 * <p>The only class that knows both sides, and the only place in the codebase
 * that switches over every kind of action. That is the right place for it: a
 * serializer has to name every shape it writes, and keeping the knowledge here
 * means no domain class has to know it can be saved.
 *
 * <p>Every conversion is exact. Numbers go across unchanged, collections keep
 * their order, and durations travel as ISO-8601 text, which reads back to the
 * nanosecond.
 */
public final class SaveFormat {

    private SaveFormat() {
    }

    // --- configuration ----------------------------------------------------

    public static SavedConfig save(SimulationConfig config) {
        MetabolismRates body = config.metabolism();
        ActionSettings actions = config.actions();
        return new SavedConfig(
                config.seed(),
                config.tickDuration().toString(),
                config.bounds().minX(),
                config.bounds().minY(),
                config.bounds().maxX(),
                config.bounds().maxY(),
                saveMix(config.chunks()),
                config.perceptionRadius(),
                new SavedMetabolism(
                        body.hunger(),
                        body.thirst(),
                        body.activeEnergy(),
                        body.restingEnergy(),
                        body.sleepingEnergy(),
                        body.starvation(),
                        body.dehydration(),
                        body.exhaustion(),
                        body.activeHealing(),
                        body.restingHealing(),
                        body.sleepingHealing()),
                new SavedActionSettings(
                        actions.moveEnergyCost(),
                        actions.walkingStepsPerHour(),
                        actions.gatherAmount(),
                        actions.gatherEnergyCost(),
                        actions.gatherMinutes(),
                        actions.eatPortion(),
                        actions.drinkGulp()));
    }

    public static SimulationConfig load(SavedConfig saved) {
        SavedMetabolism body = saved.metabolism();
        SavedActionSettings actions = saved.actions();
        return new SimulationConfig(
                saved.seed(),
                Duration.parse(saved.tickDuration()),
                new WorldBounds(saved.minX(), saved.minY(), saved.maxX(), saved.maxY()),
                loadMix(saved.chunks()),
                saved.perceptionRadius(),
                new MetabolismRates(
                        body.hunger(),
                        body.thirst(),
                        body.activeEnergy(),
                        body.restingEnergy(),
                        body.sleepingEnergy(),
                        body.starvation(),
                        body.dehydration(),
                        body.exhaustion(),
                        body.activeHealing(),
                        body.restingHealing(),
                        body.sleepingHealing()),
                new ActionSettings(
                        actions.moveEnergyCost(),
                        actions.walkingStepsPerHour(),
                        actions.gatherAmount(),
                        actions.gatherEnergyCost(),
                        actions.gatherMinutes(),
                        actions.eatPortion(),
                        actions.drinkGulp()));
    }

    static SavedChunkMix saveMix(ChunkMix mix) {
        Map<String, Integer> average = new LinkedHashMap<>();
        mix.average().forEach((type, count) -> average.put(type.name(), count));
        return new SavedChunkMix(average, mix.leanest(), mix.richest());
    }

    static ChunkMix loadMix(SavedChunkMix saved) {
        Map<ResourceType, Integer> average = new LinkedHashMap<>();
        saved.average().forEach((name, count) -> average.put(ResourceType.valueOf(name), count));
        return new ChunkMix(average, saved.leanest(), saved.richest());
    }

    // --- chunks -----------------------------------------------------------

    /**
     * The chunks a generated world has, each with only what has changed in it.
     *
     * <p>Everything else the terrain can build again from the seed, so a save
     * stays small however far an agent has walked.
     */
    public static List<SavedChunk> saveChunks(World world, SimulationConfig config) {
        Terrain terrain = new ChunkedTerrain(config.seed(), config.chunks());
        List<SavedChunk> saved = new ArrayList<>();
        for (Chunk chunk : world.chunks()) {
            Map<Location, ResourceNode> asGenerated = new LinkedHashMap<>();
            terrain.generate(chunk).forEach(node -> asGenerated.put(node.location(), node));

            List<SavedResource> changed = new ArrayList<>();
            for (ResourceNode node : world.resources()) {
                if (!chunk.contains(node.location())) {
                    continue;
                }
                ResourceNode generated = asGenerated.remove(node.location());
                if (generated == null || generated.quantity() != node.quantity()) {
                    changed.add(save(node));
                }
            }
            // Anything the terrain would put here that is no longer here at all
            // is saved as an empty node, so loading does not bring it back.
            asGenerated.values().forEach(missing ->
                    changed.add(save(ResourceNode.holding(missing.type(), missing.location(), 0.0))));
            saved.add(new SavedChunk(chunk.column(), chunk.row(), world.regrownTo(chunk), changed));
        }
        return List.copyOf(saved);
    }

    /** Rebuilds each saved chunk from the terrain, then applies what had changed. */
    public static void restoreChunks(World world, List<SavedChunk> chunks, SimulationConfig config) {
        Terrain terrain = new ChunkedTerrain(config.seed(), config.chunks());
        for (SavedChunk saved : chunks) {
            Chunk chunk = new Chunk(saved.column(), saved.row());
            Map<Location, ResourceNode> nodes = new LinkedHashMap<>();
            terrain.generate(chunk).forEach(node -> nodes.put(node.location(), node));
            saved.changed().forEach(resource -> {
                ResourceNode node = load(resource);
                nodes.put(node.location(), node);
            });
            world.restoreChunk(chunk, List.copyOf(nodes.values()), saved.regrownTo());
        }
    }

    // --- the world --------------------------------------------------------

    public static SavedResource save(ResourceNode node) {
        return new SavedResource(
                node.type(),
                node.location().x(),
                node.location().y(),
                node.capacity(),
                node.quantity());
    }

    public static ResourceNode load(SavedResource saved) {
        return new ResourceNode(
                saved.type(), new Location(saved.x(), saved.y()), saved.capacity(), saved.quantity());
    }

    // --- agents -----------------------------------------------------------

    /**
     * An agent, with what the simulation was holding on its behalf.
     *
     * @param underway the action it is part-way through, or null
     */
    public static SavedAgent save(
            Agent agent,
            Action underway,
            long underwayTicksRemaining,
            Exertion lastActivity,
            List<Danger> dangers) {
        Needs needs = agent.needs();
        return new SavedAgent(
                agent.id().value(),
                agent.name(),
                agent.location().x(),
                agent.location().y(),
                needs.hunger(),
                needs.thirst(),
                needs.energy(),
                needs.health(),
                agent.ticksAlive(),
                agent.inventory().capacity(),
                agent.inventory().contents().entrySet().stream()
                        .map(entry -> new SavedAmount(entry.getKey(), entry.getValue()))
                        .toList(),
                agent.belongings().items().stream()
                        .map(item -> new SavedItem(item.kind(), item.usesLeft()))
                        .toList(),
                agent.knowledge().beliefs().stream()
                        .map(belief -> new SavedBelief(
                                belief.kind(),
                                belief.subject(),
                                belief.estimate(),
                                belief.observations()))
                        .toList(),
                agent.skills().practice().entrySet().stream()
                        .map(entry -> new SavedSkill(entry.getKey(), entry.getValue()))
                        .toList(),
                agent.memory().capacity(),
                agent.memory().contents().stream().map(SaveFormat::save).toList(),
                agent.exploration().knownBounds().isPresent(),
                agent.exploration().records().stream()
                        .map(record -> new SavedRegion(
                                record.region().column(),
                                record.region().row(),
                                record.lastSeenTick(),
                                record.found(),
                                record.plenty().entrySet().stream()
                                        .map(seen -> new SavedRegion.SavedPlenty(
                                                seen.getKey(),
                                                seen.getValue().count(),
                                                seen.getValue().tick()))
                                        .toList()))
                        .toList(),
                underway == null ? null : save(underway),
                underwayTicksRemaining,
                lastActivity.level(),
                lastActivity.comfort(),
                dangers,
                agent.intent()
                        .map(intent -> new SavedIntent(
                                intent.goalName(),
                                intent.target().x(),
                                intent.target().y(),
                                intent.formedAtTick(),
                                intent.startingDistance()))
                        .orElse(null));
    }

    /** What a saved region remembers of how much was there, by kind. */
    private static Map<ResourceType, ExplorationMap.Seen> plentyOf(SavedRegion region) {
        Map<ResourceType, ExplorationMap.Seen> plenty = new EnumMap<>(ResourceType.class);
        region.plenty().forEach(seen ->
                plenty.put(seen.type(), new ExplorationMap.Seen(seen.count(), seen.tick())));
        return plenty;
    }

    /** The agent itself; what the simulation held for it is read by the simulation. */
    public static Agent load(SavedAgent saved, WorldBounds worldBounds) {
        Map<ResourceType, Double> carried = new EnumMap<>(ResourceType.class);
        saved.inventory().forEach(amount -> carried.put(amount.type(), amount.amount()));

        Knowledge knowledge = Knowledge.of(saved.beliefs().stream()
                .map(belief -> new Belief(
                        belief.kind(), belief.subject(), belief.estimate(), belief.observations()))
                .toList());

        Map<String, Integer> practice = new LinkedHashMap<>();
        saved.skills().forEach(skill -> practice.put(skill.skill(), skill.practice()));

        // Remembered oldest first, so the store's order - which decides what is
        // forgotten next - comes back exactly as it was.
        MemoryStore memory = new MemoryStore(saved.memoryCapacity());
        saved.memories().forEach(remembered -> memory.remember(load(remembered)));

        ExplorationMap exploration = ExplorationMap.restored(
                saved.knowsWorldBounds() ? worldBounds : null,
                saved.explored().stream()
                        .map(region -> new ExplorationMap.RegionRecord(
                                new Region(region.column(), region.row()),
                                region.lastSeenTick(),
                                region.found(),
                                plentyOf(region)))
                        .toList());

        return Agent.restored(
                AgentId.of(saved.id()),
                saved.name(),
                new Location(saved.x(), saved.y()),
                new Needs(saved.hunger(), saved.thirst(), saved.energy(), saved.health()),
                new Inventory(carried, saved.inventoryCapacity()),
                saved.ticksAlive(),
                knowledge,
                Skills.of(practice),
                memory,
                exploration,
                Optional.ofNullable(saved.intent())
                        .map(intent -> new Intent(
                                intent.goalName(),
                                new Location(intent.x(), intent.y()),
                                intent.formedAtTick(),
                                intent.startingDistance())),
                new Belongings(saved.belongings().stream()
                        .map(item -> new Item(item.kind(), item.usesLeft()))
                        .toList()));
    }

    // --- memories ---------------------------------------------------------

    public static SavedMemory save(Memory memory) {
        return new SavedMemory(
                memory.kind(),
                memory.formed().tick(),
                memory.formed().elapsed().toString(),
                memory.location().x(),
                memory.location().y(),
                memory.subject(),
                memory.confidence(),
                memory.importance(),
                memory.detail());
    }

    public static Memory load(SavedMemory saved) {
        return new Memory(
                saved.kind(),
                new SimulationTime(saved.tick(), Duration.parse(saved.elapsed())),
                new Location(saved.x(), saved.y()),
                saved.subject(),
                saved.confidence(),
                saved.importance(),
                saved.detail());
    }

    // --- actions ----------------------------------------------------------

    public static SavedAction save(Action action) {
        return switch (action) {
            case MoveAction move -> new SavedAction(
                    MoveAction.NAME,
                    null,
                    move.destination().x(),
                    move.destination().y(),
                    move.stepsPerTick(),
                    move.energyCostPerStep(),
                    0);
            case RestAction rest -> new SavedAction(RestAction.NAME, null, null, null, 0.0, 0.0, 0);
            case SleepAction sleep -> new SavedAction(
                    SleepAction.NAME, null, null, null, sleep.comfort(), 0.0, sleep.durationTicks());
            case GatherAction gather -> new SavedAction(
                    GatherAction.NAME, gather.target(), null, null, gather.amount(), gather.energyCost(),
                    gather.durationTicks());
            case EatAction eat -> new SavedAction(
                    EatAction.NAME, eat.food(), null, null, eat.portion(), 0.0, 0,
                    eat.isFromGround());
            case DrinkAction drink -> new SavedAction(
                    DrinkAction.NAME, drink.source(), null, null, drink.gulp(), 0.0, 0,
                    drink.isFromPack());
            case TendFireAction fire -> new SavedAction(
                    TendFireAction.NAME, ResourceType.WOOD, null, null, fire.wood(),
                    fire.energyCost(), fire.durationTicks());
            case DiscardAction discard -> new SavedAction(
                    DiscardAction.NAME, discard.what(), null, null, discard.amount(), 0.0, 0);
            default -> throw new IllegalArgumentException(
                    "no save format for " + action.getClass().getSimpleName()
                            + "; add it here before saving a simulation that uses it");
        };
    }

    public static Action load(SavedAction saved) {
        return switch (saved.kind()) {
            case MoveAction.NAME -> new MoveAction(
                    new Location(saved.x(), saved.y()), saved.cost(), (int) saved.amount());
            case RestAction.NAME -> new RestAction();
            case SleepAction.NAME -> new SleepAction(saved.ticks(), saved.amount());
            case GatherAction.NAME -> new GatherAction(
                    saved.resource(), saved.amount(), saved.cost(), saved.ticks());
            case EatAction.NAME -> new EatAction(
                    saved.resource(), saved.amount(), saved.fromOwnHands());
            case DrinkAction.NAME -> new DrinkAction(
                    saved.resource(), saved.amount(), saved.fromOwnHands());
            case TendFireAction.NAME -> new TendFireAction(
                    saved.amount(), saved.cost(), saved.ticks());
            case DiscardAction.NAME -> new DiscardAction(saved.resource(), saved.amount());
            default -> throw new IllegalArgumentException("unknown action in save: " + saved.kind());
        };
    }
}
