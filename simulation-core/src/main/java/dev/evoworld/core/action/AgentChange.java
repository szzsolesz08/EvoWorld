package dev.evoworld.core.action;

import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import java.util.Objects;

/**
 * What an action did to an agent, as a before and after pair.
 *
 * <p>Captured by {@link Action#execute} rather than reported by each action, so
 * the record of what changed cannot disagree with what actually changed. This
 * is the raw material the experience and memory milestones will read.
 */
public record AgentChange(AgentSnapshot before, AgentSnapshot after) {

    public AgentChange {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    /** A change that has not happened yet, and may never. */
    public static AgentChange nothing(Agent agent) {
        AgentSnapshot snapshot = agent.snapshot();
        return new AgentChange(snapshot, snapshot);
    }

    public static AgentChange from(AgentSnapshot before, Agent agent) {
        return new AgentChange(before, agent.snapshot());
    }

    public double hungerChange() {
        return after.needs().hunger() - before.needs().hunger();
    }

    public double thirstChange() {
        return after.needs().thirst() - before.needs().thirst();
    }

    public double energyChange() {
        return after.needs().energy() - before.needs().energy();
    }

    public double healthChange() {
        return after.needs().health() - before.needs().health();
    }

    /** How much more, or less, the agent is carrying. */
    public double carriedChange() {
        return after.inventory().total() - before.inventory().total();
    }

    public boolean moved() {
        return !before.location().equals(after.location());
    }

    public Location from() {
        return before.location();
    }

    public Location to() {
        return after.location();
    }

    /** Whether the agent came through this completely untouched. */
    public boolean isNothing() {
        return before.equals(after);
    }
}
