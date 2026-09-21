package dev.evoworld.core.action;

import dev.evoworld.core.agent.ActivityLevel;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.agent.Exertion;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.world.World;
import java.util.Optional;

/**
 * Something an agent can do.
 *
 * <p>An action is a command object: chosen first, applied later, and never
 * anything else. Every action is built from three parts, and the split is what
 * makes the whole system safe to extend:
 *
 * <ul>
 *   <li>a <strong>precondition</strong> - what has to be true first, checked
 *       without touching anything;
 *   <li>an <strong>effect</strong> - what changes, run only once the
 *       precondition holds;
 *   <li>a <strong>duration</strong> - how many ticks it occupies the agent.
 * </ul>
 *
 * <p>{@link #execute} is final and is the only way in. A subclass cannot skip
 * its own precondition, forget to report what happened, or invent a status for
 * itself, so "a blocked action changes nothing" is a property of the base class
 * rather than a rule every action has to remember.
 */
public abstract class Action {

    /** Short stable name used in logs and, later, in memories. */
    public abstract String name();

    /**
     * How many ticks this action occupies the agent. Most things take a single
     * tick; work that should cost real time says so.
     */
    public int durationTicks() {
        return 1;
    }

    /**
     * How much effort this represents, which decides what each tick costs the
     * agent in energy. Most things take effort, so that is the default.
     */
    public ActivityLevel activityLevel() {
        return ActivityLevel.ACTIVE;
    }

    /**
     * What each tick of this asks of the body: its activity level, and how
     * comfortably it recovers. Only something that brings its own comfort - a
     * sleep in a bed - needs to say more than its level.
     */
    public Exertion exertion() {
        return Exertion.of(activityLevel());
    }

    /**
     * How many ticks until this starts doing any good.
     *
     * <p>Usually when it is finished: a gather is worth nothing half done. An
     * action whose benefit accrues as it goes - a night's sleep - says it
     * starts at once, so a long sleep is not valued as if its reward only
     * arrived in the morning.
     */
    public int ticksUntilBenefit() {
        return durationTicks();
    }

    /**
     * How many tiles this covers in a tick, for an action that travels; one
     * otherwise. Walking speed is set in simulated time, so at longer ticks a
     * single move covers more ground.
     */
    public int stepsPerTick() {
        return 1;
    }

    /**
     * Why this cannot be done right now, or empty if it can.
     *
     * <p>Must not change anything: it is called to ask, not to do, and it is
     * called again when a multi-tick action finishes in case the world moved on
     * in the meantime.
     */
    protected abstract Optional<Refusal> precondition(Agent agent, World world);

    /**
     * Whether doing this well is something an agent can get better at.
     *
     * <p>Walking and gathering are; eating and resting are not. A trainable
     * action costs a practised agent less effort, and every success counts as
     * practice.
     */
    public boolean trainable() {
        return false;
    }

    /**
     * The resource this action is about, if it is about one.
     *
     * <p>Lets an outcome be interpreted without anyone having to guess from the
     * text what it concerned, which is what makes learning from experience a
     * lookup rather than a parse.
     */
    public Optional<ResourceType> concerns() {
        return Optional.empty();
    }

    /**
     * Carries out the action, returning a human-readable account of what
     * happened. Only ever called when {@link #precondition} is satisfied.
     */
    protected abstract String effect(Agent agent, World world);

    /**
     * Checks the precondition and, if it holds, applies the effect.
     *
     * <p>The before and after state of the agent is captured either way, so the
     * outcome carries what actually changed rather than what was intended.
     */
    public final ActionOutcome execute(Agent agent, World world) {
        AgentChange unchanged = AgentChange.nothing(agent);
        Optional<Refusal> blocked = precondition(agent, world);
        if (blocked.isPresent()) {
            return ActionOutcome.blocked(name(), blocked.get(), unchanged);
        }
        String detail = effect(agent, world);
        return ActionOutcome.succeeded(
                name(), detail, AgentChange.from(unchanged.before(), agent));
    }

    /** Whether this action could be carried out right now. */
    public final boolean isPossibleFor(Agent agent, World world) {
        return precondition(agent, world).isEmpty();
    }

    @Override
    public String toString() {
        return name();
    }
}
