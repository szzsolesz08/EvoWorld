package dev.evoworld.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.SimulationTime;
import dev.evoworld.core.agent.AgentId;
import dev.evoworld.core.event.SimulationEvent;
import dev.evoworld.core.exploration.Discovery;
import dev.evoworld.core.exploration.Region;
import dev.evoworld.core.resource.ResourceType;
import dev.evoworld.core.skill.SkillLevel;
import dev.evoworld.core.status.AgentStatus;
import dev.evoworld.core.world.Location;
import dev.evoworld.persistence.SaveFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** What the console says, at each level, and the savers behind it. */
class ConsoleOutputTest {

    private static final SimulationTime NOON =
            new SimulationTime(72L, Duration.ofHours(12));

    private static Simulation running(long ticks) {
        Simulation simulation = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
        simulation.run(ticks);
        return simulation;
    }

    private static List<String> statusLines(Simulation simulation) {
        AgentStatus status = simulation.status().get(0);
        return StatusReport.lines(status, simulation.config().tickDuration());
    }

    private static String row(List<String> lines, String label) {
        return lines.stream()
                .filter(line -> line.trim().startsWith(label + " "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no '" + label + "' row in " + lines));
    }

    @Nested
    @DisplayName("the status report")
    class Status {

        @Test
        @DisplayName("covers time, status, place, goal, action, memories, knowledge and skills")
        void coversEverythingAskedFor() {
            List<String> lines = statusLines(running(600));

            assertTrue(lines.get(0).startsWith("-- day 5 04:00 -- Atlas"), lines.get(0));
            for (String label : List.of(
                    "status", "location", "needs", "carrying", "wants",
                    "remembers", "knows", "skills", "explored")) {
                row(lines, label);
            }
            assertTrue(row(lines, "knows").contains("berries: "), row(lines, "knows"));
            assertTrue(row(lines, "skills").contains("move "), row(lines, "skills"));
        }

        @Test
        @DisplayName("says 'doing' only while a job is under way, and 'last act' otherwise")
        void distinguishesDoingFromDone() {
            Simulation simulation = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
            boolean sawBusy = false;
            boolean sawDone = false;
            for (int tick = 0; tick < 900 && !(sawBusy && sawDone); tick++) {
                simulation.run(1);
                AgentStatus status = simulation.status().get(0);
                List<String> lines = StatusReport.lines(status, simulation.config().tickDuration());
                if (status.busyFor() > 0) {
                    sawBusy = true;
                    assertTrue(row(lines, "doing").contains("to go"), row(lines, "doing"));
                } else if (status.doing().isPresent()) {
                    sawDone = true;
                    row(lines, "last act");
                }
            }
            assertTrue(sawBusy && sawDone, "the run should both finish and be part-way through things");
        }

        @Test
        @DisplayName("says where the agent is heading while it has somewhere to be, and not otherwise")
        void showsTheIntent() {
            Simulation simulation = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
            boolean sawHeading = false;
            boolean sawNowhere = false;
            for (int tick = 0; tick < 900 && !(sawHeading && sawNowhere); tick++) {
                simulation.run(1);
                AgentStatus status = simulation.status().get(0);
                String intent = row(
                        StatusReport.lines(status, simulation.config().tickDuration()), "intent");
                if (status.body().intent().isPresent()) {
                    sawHeading = true;
                    assertTrue(intent.contains("heading for ") && intent.contains("steps to go"), intent);
                } else {
                    sawNowhere = true;
                    assertTrue(intent.contains("nowhere in particular"), intent);
                }
            }
            assertTrue(sawHeading && sawNowhere, "the run should both travel and stay put");
        }

        @Test
        @DisplayName("asking for a status changes nothing about the run")
        void reportingIsReadOnly() {
            Simulation watched = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
            Simulation unwatched = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
            for (int tick = 0; tick < 300; tick++) {
                watched.run(1);
                watched.status();
            }
            unwatched.run(300);

            assertEquals(unwatched.save(), watched.save());
        }
    }

    @Nested
    @DisplayName("log levels")
    class Levels {

        private final SimulationEvent firstFind = new SimulationEvent.Discovered(
                NOON, AgentId.of("a"), "Atlas",
                new Discovery(ResourceType.WATER, new Location(1, 1), new Region(0, 0), NOON, true));
        private final SimulationEvent anotherFind = new SimulationEvent.Discovered(
                NOON, AgentId.of("a"), "Atlas",
                new Discovery(ResourceType.WATER, new Location(9, 9), new Region(2, 2), NOON, false));
        private final SimulationEvent skill = new SimulationEvent.SkillImproved(
                NOON, AgentId.of("a"), "Atlas", "MOVE", SkillLevel.APPRENTICE);
        private final SimulationEvent death = new SimulationEvent.Died(
                NOON, AgentId.of("a"), "Atlas", "thirst", 72L);

        @Test
        @DisplayName("quiet keeps only what changes the story")
        void quietIsQuiet() {
            EventLog quiet = new EventLog(LogLevel.QUIET);

            assertTrue(quiet.shows(firstFind));
            assertTrue(quiet.shows(death));
            assertFalse(quiet.shows(anotherFind));
            assertFalse(quiet.shows(skill));
        }

        @Test
        @DisplayName("normal shows every event")
        void normalShowsEverything() {
            EventLog normal = new EventLog(LogLevel.NORMAL);

            assertTrue(normal.shows(anotherFind));
            assertTrue(normal.shows(skill));
        }

        @Test
        @DisplayName("each level is a superset of the one below")
        void levelsAreOrdered() {
            assertTrue(LogLevel.DEBUG.atLeast(LogLevel.VERBOSE));
            assertTrue(LogLevel.VERBOSE.atLeast(LogLevel.NORMAL));
            assertTrue(LogLevel.NORMAL.atLeast(LogLevel.QUIET));
            assertEquals(0L, LogLevel.QUIET.statusEvery(144));
            assertEquals(144L, LogLevel.NORMAL.statusEvery(144));
            assertEquals(36L, LogLevel.VERBOSE.statusEvery(144));
        }

        @Test
        void parsesLevelsCaseInsensitively() {
            assertEquals(LogLevel.VERBOSE, LogLevel.parse("Verbose"));
            assertThrows(IllegalArgumentException.class, () -> LogLevel.parse("loud"));
        }
    }

    @Nested
    @DisplayName("saving from the runner")
    class Saving {

        @TempDir
        Path directory;

        @Test
        @DisplayName("autosave writes on schedule and does not change the run")
        void autosaveIsInvisible(@TempDir Path elsewhere) {
            Path file = directory.resolve("auto.json");
            Simulation autosaved = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
            autosaved.addObserver(new Autosaver(autosaved, file, 50));
            autosaved.run(120);

            Simulation plain = EvoWorldRunner.buildSimulation(SimulationConfig.defaults(7L));
            plain.run(120);

            assertTrue(Files.exists(file));
            assertEquals(100L, SaveFile.read(file).tick(), "the last scheduled save was at tick 100");
            assertEquals(plain.save(), autosaved.save());
        }
    }

    @Nested
    @DisplayName("options")
    class Options {

        @Test
        void parsesSaveLoadAndLevel() {
            RunnerOptions options = RunnerOptions.parse(new String[] {
                    "--load", "in.json", "--save", "out.json",
                    "--autosave-every", "144", "--log-level", "verbose"});

            assertEquals(Path.of("in.json"), options.loadFrom().orElseThrow());
            assertEquals(Path.of("out.json"), options.saveTo().orElseThrow());
            assertEquals(144L, options.autosaveEvery());
            assertEquals(LogLevel.VERBOSE, options.logLevel());
        }

        @Test
        @DisplayName("--trace is debug by another name")
        void traceIsDebug() {
            assertEquals(LogLevel.DEBUG, RunnerOptions.parse(new String[] {"--trace"}).logLevel());
        }

        @Test
        @DisplayName("autosave needs somewhere to save to")
        void autosaveNeedsAFile() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RunnerOptions.parse(new String[] {"--autosave-every", "10"}));
        }

        @Test
        @DisplayName("a scripted run cannot be saved, because the script's place in its list is not saved")
        void scriptsAreNotSaved() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RunnerOptions.parse(new String[] {"--script", "--save", "x.json"}));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RunnerOptions.parse(new String[] {"--script", "--load", "x.json"}));
        }
    }
}
