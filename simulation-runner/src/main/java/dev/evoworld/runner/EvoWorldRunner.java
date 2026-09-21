package dev.evoworld.runner;

import dev.evoworld.core.Simulation;
import dev.evoworld.core.SimulationConfig;
import dev.evoworld.core.agent.Agent;
import dev.evoworld.core.decision.DecisionMaker;
import dev.evoworld.core.decision.DeliberativeDecisionMaker;
import dev.evoworld.core.world.Location;
import dev.evoworld.core.world.World;
import dev.evoworld.core.world.WorldGenerator;
import dev.evoworld.core.world.WorldState;
import dev.evoworld.persistence.SaveFile;
import dev.evoworld.persistence.SaveFileException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Headless entry point: starts or resumes a world, runs it, and reports.
 *
 * <p>The runner owns everything about <em>this</em> run - the command line, what
 * is printed and how often, how fast it goes, where it is saved, the process
 * exit code. The core owns the simulation itself, and runs it without anyone
 * steering: once started, nothing here intervenes until it ends.
 */
public final class EvoWorldRunner {

    private static final Logger log = LoggerFactory.getLogger(EvoWorldRunner.class);

    /** The one agent the MVP runs, until there is a reason for more. */
    public static final String FIRST_AGENT_NAME = "Atlas";

    private EvoWorldRunner() {
    }

    public static void main(String[] args) {
        RunnerOptions options;
        try {
            options = RunnerOptions.parse(args);
        } catch (IllegalArgumentException e) {
            log.error("{}", e.getMessage());
            log.error("{}", RunnerOptions.usage());
            System.exit(2);
            return;
        }
        options.logLevel().applyToCore();

        try {
            Simulation simulation = startOrResume(options);
            attachObservers(simulation, options);

            if (options.logLevel().atLeast(LogLevel.NORMAL)) {
                printWorld("world at the start", simulation.snapshot());
            }
            long processed = simulation.run(options.ticks());
            options.saveTo().ifPresent(file -> {
                SaveFile.write(simulation, file);
                log.info("saved to {} at tick {}", file.toAbsolutePath(), simulation.currentTick());
            });
            if (options.logLevel().atLeast(LogLevel.NORMAL)) {
                printWorld("world at the end", simulation.snapshot());
            }
            log.info("run complete: seed={} ran {} tick(s), now at tick {} ({} simulated)",
                    simulation.config().seed(), processed, simulation.currentTick(),
                    simulation.time().elapsed());
        } catch (SaveFileException e) {
            log.error("{}", e.getMessage());
            System.exit(1);
        }
    }

    /** A fresh world from a seed, or a saved one carried on from where it stopped. */
    private static Simulation startOrResume(RunnerOptions options) {
        if (options.loadFrom().isPresent()) {
            Path file = options.loadFrom().get();
            Simulation resumed = SaveFile.load(file);
            if (options.seed() != RunnerOptions.DEFAULT_SEED
                    || !options.tickDuration().equals(RunnerOptions.DEFAULT_TICK_DURATION)) {
                log.warn("--seed and --tick-minutes are ignored with --load: the save decides them");
            }
            log.info("resuming {} at tick {} (seed {})",
                    file.toAbsolutePath(), resumed.currentTick(), resumed.config().seed());
            return resumed;
        }
        SimulationConfig config = SimulationConfig.defaults(options.seed())
                .withTickDuration(options.tickDuration());
        return buildSimulation(config, options.scripted());
    }

    /**
     * What gets shown, by level, and the savers and pacer.
     *
     * <p>The pacer goes on last, so it waits after everything else has had its
     * say about a tick.
     */
    private static void attachObservers(Simulation simulation, RunnerOptions options) {
        LogLevel level = options.logLevel();
        simulation.addObserver(new EventLog(level));

        long statusEvery = level.statusEvery(simulation.ticksPerDay());
        if (statusEvery > 0) {
            simulation.addObserver(new StatusReport(simulation, statusEvery));
        }
        if (level.atLeast(LogLevel.VERBOSE)) {
            simulation.addObserver(new ActionLog(simulation.config().tickDuration()));
        }
        if (level.atLeast(LogLevel.DEBUG)) {
            simulation.addObserver(new ConsoleSimulationObserver(
                    options.logEvery(), simulation.config().tickDuration()));
        }
        if (options.autosaveEvery() > 0) {
            simulation.addObserver(new Autosaver(
                    simulation, options.saveTo().orElseThrow(), options.autosaveEvery()));
        }
        if (options.isPaced()) {
            simulation.addObserver(new Pacer(options.speed()));
        }
    }

    /**
     * Assembles a simulation from configuration. Kept separate from {@code main}
     * so tests can build the identical world without going through the process.
     */
    public static Simulation buildSimulation(SimulationConfig config) {
        return buildSimulation(config, false);
    }

    /**
     * Assembles a simulation from configuration.
     *
     * <p>A scripted run replaces the decision layer with a fixed list of
     * actions, which is how the action system gets exercised end to end without
     * an AI to drive it.
     */
    public static Simulation buildSimulation(SimulationConfig config, boolean scripted) {
        World world = WorldGenerator.generate(config);
        Location start = config.bounds().center();
        world.addAgent(Agent.spawn("agent-1", FIRST_AGENT_NAME, start));

        DecisionMaker decisionMaker = scripted
                ? new ScriptedDecisionMaker(
                        ActionScripts.forage(world, start, config))
                : DeliberativeDecisionMaker.from(config);

        return new Simulation(world, decisionMaker, config);
    }

    private static void printWorld(String caption, WorldState state) {
        log.info("--- {} ---", caption);
        WorldMapRenderer.render(state).forEach(line -> log.info("{}", line));
    }
}
