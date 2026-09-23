package dev.evoworld.core.status;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.goal.GoalPriority;
import dev.evoworld.core.knowledge.Belief;
import dev.evoworld.core.memory.Recollection;
import dev.evoworld.core.perception.KnownPlenty;
import dev.evoworld.core.skill.Skills;
import dev.evoworld.core.world.AgentSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How one agent is doing, all of it, as values.
 *
 * <p>What a status display needs and a {@link AgentSnapshot} does not carry:
 * what the agent wants, what it is doing, what it remembers, what it has
 * worked out. Observers never see the live agent (ADR 2), so the simulation
 * assembles this for them - every field here is immutable, and nothing in it
 * leads back to anything that can change.
 *
 * @param wants the goal behind its latest decision, if it has made one
 * @param doing its latest decision, if it has made one
 * @param busyFor ticks left on a job it is part-way through, or zero
 * @param memories the handful of things it remembers that matter most right
 *     now, most relevant first
 * @param knowledge what it has actually tested, in the order it learned it
 * @param plenty where it remembers there being a lot of something, best first
 */
public record AgentStatus(
        SimulationTime time,
        AgentSnapshot body,
        Optional<GoalPriority> wants,
        Optional<Decision> doing,
        long busyFor,
        List<Recollection> memories,
        List<Belief> knowledge,
        Skills skills,
        int regionsVisited,
        int regionCount,
        List<KnownPlenty> plenty) {

    public AgentStatus {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(wants, "wants");
        Objects.requireNonNull(doing, "doing");
        Objects.requireNonNull(skills, "skills");
        memories = List.copyOf(memories);
        knowledge = List.copyOf(knowledge);
        plenty = List.copyOf(plenty);
    }
}
