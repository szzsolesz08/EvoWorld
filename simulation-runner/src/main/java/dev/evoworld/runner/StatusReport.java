package dev.evoworld.runner;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.action.SleepAction;
import dev.evoworld.core.agent.Needs;
import dev.evoworld.core.decision.Decision;
import dev.evoworld.core.goal.GoalPriority;
import dev.evoworld.core.intent.Intent;
import dev.evoworld.core.knowledge.Belief;
import dev.evoworld.core.memory.Memory;
import dev.evoworld.core.memory.Recollection;
import dev.evoworld.core.perception.KnownPlenty;
import dev.evoworld.core.observer.SimulationObserver;
import dev.evoworld.core.skill.Skills;
import dev.evoworld.core.status.AgentStatus;
import dev.evoworld.core.world.AgentSnapshot;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.WorldState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A periodic block describing how each agent is doing.
 *
 * <p>Where the event log says what happened, this says how things stand:
 * where the agent is, what it wants, what it is doing about it, what it
 * remembers and what it has worked out. Printed on a schedule of simulated
 * time, and once more when the run ends.
 *
 * <pre>
 * -- day 3 14:20 -- Atlas ------------------------------------------------
 *    status     alive, age 3d 14h, health 100.0
 *    location   (5,12)
 *    needs      hunger 12.0, thirst 30.0, energy 82.0
 *    carrying   berries 5.0
 *    wants      satisfy thirst (0.09)
 *    last act   MOVE to (2,8), for satisfy thirst (water 3 steps north)
 *    intent     heading for (2,8) to satisfy thirst, 3 steps to go, set out 4 ticks ago
 *    remembers  water at (2,8); found stone at (13,1); close call with thirst at (7,3)
 *    knows      berries: food, 8.0 a unit (98%); stone: not food, not a drink
 *    skills     move skilled (49% less effort), gather competent (35% less effort)
 *    plenty     berries x9 at region -3,0, 12 steps; water x4 at region 1,2, 9 steps
 *    explored   17 of 81 regions nearby
 * </pre>
 *
 * <p>It asks the simulation for {@link AgentStatus} values rather than looking
 * at the agent itself, so it can print as often as it likes without being able
 * to change anything.
 */
public final class StatusReport implements SimulationObserver {

    private static final Logger log = LoggerFactory.getLogger("evoworld.status");
    private static final int WIDTH = 72;

    /** How many remembered rich places are worth listing. */
    private static final int PLENTY_SHOWN = 3;

    private final Simulation simulation;
    private final long every;

    /** @param every ticks between reports; the run's end is always reported */
    public StatusReport(Simulation simulation, long every) {
        this.simulation = simulation;
        if (every < 1) {
            throw new IllegalArgumentException("a status report needs a positive interval");
        }
        this.every = every;
    }

    @Override
    public void onTickCompleted(WorldState state) {
        if (state.tick() % every == 0) {
            report();
        }
    }

    @Override
    public void onSimulationStopped(WorldState state, String reason) {
        if (state.tick() % every != 0) {
            report();
        }
    }

    private void report() {
        Duration tick = simulation.config().tickDuration();
        for (AgentStatus status : simulation.status()) {
            lines(status, tick).forEach(line -> log.info("{}", line));
        }
    }

    /** The block for one agent, as lines. Public so it can be tested without a logger. */
    public static List<String> lines(AgentStatus status, Duration tickDuration) {
        AgentSnapshot body = status.body();
        Needs needs = body.needs();
        List<String> lines = new ArrayList<>();

        String heading = "-- " + EventLog.when(status.time()) + " -- " + body.name() + " ";
        lines.add(heading + "-".repeat(Math.max(3, WIDTH - heading.length())));
        row(lines, "status", String.format(
                Locale.ROOT,
                "%s, age %s, health %.1f",
                body.alive() ? "alive" : "dead",
                age(tickDuration.multipliedBy(body.ticksAlive())),
                needs.health()));
        row(lines, "location", body.location().toString());
        row(lines, "needs", String.format(
                Locale.ROOT,
                "hunger %.1f, thirst %.1f, energy %.1f",
                needs.hunger(),
                needs.thirst(),
                needs.energy()));
        row(lines, "carrying", body.inventory().isEmpty()
                ? "nothing"
                : body.inventory().toString().replaceFirst("^carrying ", ""));
        if (body.alive()) {
            row(lines, "wants", status.wants().map(StatusReport::describe).orElse("(not decided yet)"));
            // "Doing" only while a job is actually under way. Otherwise the
            // last decision has already happened - it was carried out this
            // tick, and saying the agent is still doing it would be wrong.
            row(lines, status.busyFor() > 0 ? "doing" : "last act", status.doing()
                    .map(decision -> describe(decision, status.busyFor(), tickDuration))
                    .orElse("(not decided yet)"));
            row(lines, "intent", body.intent()
                    .map(intent -> describe(intent, body.location(), status.time().tick()))
                    .orElse("nowhere in particular"));
        }
        row(lines, "remembers", status.memories().isEmpty()
                ? "nothing worth mentioning"
                : status.memories().stream()
                        .map(StatusReport::describe)
                        .collect(Collectors.joining("; ")));
        row(lines, "knows", status.knowledge().isEmpty() ? "nothing yet" : describe(status.knowledge()));
        row(lines, "skills", describe(status.skills()));
        if (!status.plenty().isEmpty()) {
            row(lines, "plenty", status.plenty().stream()
                    .limit(PLENTY_SHOWN)
                    .map(StatusReport::describe)
                    .collect(Collectors.joining("; ")));
        }
        // "nearby", because the world has no edges: 81 is the window the agent
        // measures itself against, not a count of the world.
        row(lines, "explored",
                status.regionsVisited() + " of " + status.regionCount() + " regions nearby");
        return lines;
    }

    /** "berries x9 at region -3,0, 12 steps away". */
    private static String describe(KnownPlenty known) {
        return String.format(
                Locale.ROOT,
                "%s x%d at %s, %d steps",
                known.type().label(),
                known.seen(),
                known.region(),
                known.stepsAway());
    }

    private static String describe(Intent intent, Location from, long now) {
        return String.format(
                Locale.ROOT,
                "%s, %d steps to go, set out %d ticks ago",
                intent.describe(),
                from.manhattanDistanceTo(intent.target()),
                intent.ticksUnderway(now));
    }

    private static void row(List<String> lines, String label, String value) {
        lines.add(String.format(Locale.ROOT, "   %-10s %s", label, value));
    }

    private static String describe(GoalPriority want) {
        return String.format(
                Locale.ROOT,
                "%s (%.2f)",
                want.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                want.priority());
    }

    /** The action, and the reason without the arithmetic. */
    private static String describe(Decision decision, long busyFor, Duration tickDuration) {
        if (busyFor > 0 && decision.action().name().equals(SleepAction.NAME)) {
            return "sleeping, " + span(tickDuration.multipliedBy(busyFor)) + " left";
        }
        String reason = decision.rationale();
        int numbers = reason.indexOf(" want ");
        String why = numbers < 0 ? reason : reason.substring(0, numbers);
        String busy = busyFor > 0 ? " (" + busyFor + " tick(s) to go)" : "";
        return decision.action() + ", for " + why + busy;
    }

    /** "5h 10m", "40m". */
    static String span(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours == 0) {
            return minutes + "m";
        }
        return minutes == 0 ? hours + "h" : hours + "h " + minutes + "m";
    }

    private static String describe(Recollection recalled) {
        Memory memory = recalled.memory();
        String what = memory.subject().toLowerCase(Locale.ROOT);
        String where = memory.location().toString();
        return switch (memory.kind()) {
            case RESOURCE_LOCATION -> what + " at " + where;
            case DISCOVERY -> "found " + what + " at " + where;
            case DANGER -> "close call with " + what + " at " + where;
            case FAILED_ACTION -> what.toUpperCase(Locale.ROOT) + " failed at " + where;
            default -> memory.detail() + " at " + where;
        };
    }

    /** What the agent has worked out, grouped by the thing it is about. */
    private static String describe(List<Belief> beliefs) {
        Map<String, List<String>> bySubject = new LinkedHashMap<>();
        for (Belief belief : beliefs) {
            String fact = switch (belief.kind()) {
                case NOURISHMENT -> belief.estimate() > 0.0
                        ? String.format(Locale.ROOT, "food, %.1f a unit (%.0f%%)",
                                belief.estimate(), belief.confidence() * 100)
                        : "not food";
                case HYDRATION -> belief.estimate() > 0.0
                        ? String.format(Locale.ROOT, "drink, %.1f a unit (%.0f%%)",
                                belief.estimate(), belief.confidence() * 100)
                        : "not a drink";
                case LOCATION_RELIABILITY -> null;
            };
            if (fact != null) {
                bySubject.computeIfAbsent(belief.subject().toLowerCase(Locale.ROOT), s -> new ArrayList<>())
                        .add(fact);
            }
        }
        return bySubject.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + String.join(", ", entry.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String describe(Skills skills) {
        if (skills.isEmpty()) {
            return "none yet";
        }
        return skills.practice().keySet().stream()
                .map(skill -> String.format(
                        Locale.ROOT,
                        "%s %s (%.0f%% less effort)",
                        skill.toLowerCase(Locale.ROOT),
                        skills.level(skill).label(),
                        (1.0 - skills.effortMultiplier(skill)) * 100))
                .collect(Collectors.joining(", "));
    }

    private static String age(Duration lived) {
        long days = lived.toDays();
        return days > 0
                ? String.format(Locale.ROOT, "%dd %dh", days, lived.toHoursPart())
                : String.format(Locale.ROOT, "%dh %dm", lived.toHoursPart(), lived.toMinutesPart());
    }
}
