package dev.evoworld.core.event;

import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.exploration.Discovery;
import dev.evoworld.core.knowledge.Belief;
import dev.evoworld.core.skill.SkillLevel;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import java.util.Locale;
import java.util.Objects;

/**
 * Something that happened in the simulation worth telling someone about.
 *
 * <p>The observer interface already reports every perception, every
 * deliberation and every candidate that lost - which is what you want when
 * debugging a decision and far too much when watching a life. Events are the
 * other end of that: the handful of moments a narrator would mention. A find, a
 * lesson, a close call, getting better at something, dying, and once a day a
 * summary of how things stand.
 *
 * <p>The set is sealed on purpose. Every kind of event is listed here, in one
 * place, so "what does the simulation consider worth reporting" has a single
 * answer, and a renderer switching over events is told by the compiler when a
 * new one appears.
 */
public sealed interface SimulationEvent {

    SimulationTime time();

    AgentId agentId();

    String agentName();

    /** One line, the way it would be read out. */
    String describe();

    default long tick() {
        return time().tick();
    }

    /** A new agent entered the world. */
    record Born(SimulationTime time, AgentId agentId, String agentName, Location where)
            implements SimulationEvent {

        public String describe() {
            return agentName + " came into the world at " + where;
        }
    }

    /** Something found for the first time, or somewhere new. */
    record Discovered(
            SimulationTime time, AgentId agentId, String agentName, Discovery discovery)
            implements SimulationEvent {

        public Discovered {
            Objects.requireNonNull(discovery, "discovery");
        }

        public String describe() {
            return agentName + " discovered " + discovery.describe();
        }
    }

    /**
     * A belief went from assumption to experience.
     *
     * <p>Only the first time: the ninth berry sharpens a belief the first berry
     * formed, and reporting it would drown the lesson in its repetitions.
     */
    record Learned(SimulationTime time, AgentId agentId, String agentName, Belief belief)
            implements SimulationEvent {

        public String describe() {
            return agentName + " learned " + belief.describe();
        }
    }

    /** A need has started to do damage. */
    record DangerBegan(
            SimulationTime time, AgentId agentId, String agentName, Danger danger, double health)
            implements SimulationEvent {

        public String describe() {
            return String.format(
                    Locale.ROOT,
                    "%s is %s, and it is starting to hurt (health %.1f)",
                    agentName,
                    danger.label(),
                    health);
        }
    }

    /** A need that was doing damage no longer is. */
    record DangerPassed(
            SimulationTime time, AgentId agentId, String agentName, Danger danger, double health)
            implements SimulationEvent {

        public String describe() {
            return String.format(
                    Locale.ROOT,
                    "%s is no longer %s (health %.1f)",
                    agentName,
                    danger.label(),
                    health);
        }
    }

    /** Practice paid off enough to deserve a new name. */
    record SkillImproved(
            SimulationTime time,
            AgentId agentId,
            String agentName,
            String skill,
            SkillLevel level)
            implements SimulationEvent {

        public String describe() {
            return agentName + "'s " + skill.toLowerCase(Locale.ROOT) + " skill is now "
                    + level.label();
        }
    }

    /** The end of an agent. */
    record Died(
            SimulationTime time, AgentId agentId, String agentName, String cause, long ticksAlive)
            implements SimulationEvent {

        public String describe() {
            return agentName + " died of " + cause + " after " + ticksAlive + " ticks";
        }
    }

    /**
     * Once per simulated day, how an agent is getting on.
     *
     * <p>The only periodic event, and the one that makes a long run readable:
     * between the rare moments above, this is what shows a life going on.
     */
    record DayPassed(
            SimulationTime time,
            AgentId agentId,
            String agentName,
            long day,
            AgentSnapshot state,
            int factsLearned,
            int regionsVisited,
            int regionCount,
            String skills)
            implements SimulationEvent {

        public String describe() {
            return String.format(
                    Locale.ROOT,
                    "day %d: %s at %s, %s, %s, knows %d fact(s), has seen %d of %d regions, %s",
                    day,
                    agentName,
                    state.location(),
                    state.needs(),
                    state.inventory(),
                    factsLearned,
                    regionsVisited,
                    regionCount,
                    skills);
        }
    }

    /** What is doing the damage when an agent is in danger. */
    enum Danger {
        STARVING("starving"),
        DEHYDRATED("dehydrated"),
        EXHAUSTED("exhausted");

        private final String label;

        Danger(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
