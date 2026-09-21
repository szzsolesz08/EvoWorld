package dev.evoworld.core.save;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.event.SimulationEvent.Danger;
import java.util.List;
import java.util.Objects;

/**
 * One agent: its body, its mind, and what it was in the middle of.
 *
 * @param inventory what it carries, in declaration order
 * @param beliefs what it has worked out, in the order it worked it out
 * @param memories what it remembers, oldest first - the order decides what is
 *     forgotten first, so it is part of the state
 * @param knowsWorldBounds whether it has looked at anything yet; an agent that
 *     has not does not know how big the world is
 * @param explored its coverage of the world, in the order it first went there
 * @param underway the action it is part-way through, or null
 * @param underwayTicksRemaining how much of that action is left
 * @param lastActivity how hard it worked last tick, which is what the next
 *     tick will charge it for
 * @param lastComfort how comfortably it recovered doing it
 * @param dangers which needs were already doing damage, so the next tick does
 *     not report them as starting again
 * @param intent where it had set out for, or null
 */
public record SavedAgent(
        String id,
        String name,
        int x,
        int y,
        double hunger,
        double thirst,
        double energy,
        double health,
        long ticksAlive,
        double inventoryCapacity,
        List<SavedAmount> inventory,
        List<SavedBelief> beliefs,
        List<SavedSkill> skills,
        int memoryCapacity,
        List<SavedMemory> memories,
        boolean knowsWorldBounds,
        List<SavedRegion> explored,
        SavedAction underway,
        long underwayTicksRemaining,
        ActivityLevel lastActivity,
        double lastComfort,
        List<Danger> dangers,
        SavedIntent intent) {

    public SavedAgent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lastActivity, "lastActivity");
        inventory = List.copyOf(inventory);
        beliefs = List.copyOf(beliefs);
        skills = List.copyOf(skills);
        memories = List.copyOf(memories);
        explored = List.copyOf(explored);
        dangers = List.copyOf(dangers);
    }
}
